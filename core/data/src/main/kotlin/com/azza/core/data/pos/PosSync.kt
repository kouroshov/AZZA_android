package com.azza.core.data.pos

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.azza.core.common.AppError
import com.azza.core.common.ApplicationScope
import com.azza.core.common.Outcome
import com.azza.core.data.AuthRepository
import com.azza.core.data.StoresRepository
import com.azza.core.database.pos.PosDao
import com.azza.core.database.pos.SyncStateEntity
import com.azza.core.datastore.AppPreferences
import com.azza.core.model.PosSignOut
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The register's WorkManager jobs (POS-03):
 * - [SaleUploadWorker]: one at a time ("pos-sale-upload"), only on a network,
 *   exponential backoff. A new sale APPENDS a run, so a sale recorded while an
 *   upload is finishing is never left behind.
 * - [CatalogSyncWorker]: on demand ("pos-catalog-sync") and every 30 minutes.
 */
@Singleton
class PosSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val work get() = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun enqueueUpload() {
        val request = OneTimeWorkRequestBuilder<SaleUploadWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        work.enqueueUniqueWork(UPLOAD, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun enqueueCatalogSync() {
        val request = OneTimeWorkRequestBuilder<CatalogSyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        work.enqueueUniqueWork(CATALOG, ExistingWorkPolicy.KEEP, request)
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(30, TimeUnit.MINUTES).setConstraints(online).build()
        work.enqueueUniquePeriodicWork(CATALOG_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Signed out: no more catalog refreshes. The upload stays — queued sales wait for their account. */
    fun stopCatalog() {
        work.cancelUniqueWork(CATALOG)
        work.cancelUniqueWork(CATALOG_PERIODIC)
    }

    companion object {
        const val UPLOAD = "pos-sale-upload"
        const val CATALOG = "pos-catalog-sync"
        const val CATALOG_PERIODIC = "pos-catalog-sync-periodic"
    }
}

@HiltWorker
class SaleUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploader: SaleUploader,
    private val scheduler: PosSyncScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val run = uploader.uploadAll()
        // The server's counts moved with every recorded sale: refresh them.
        val touched = when (run) {
            is UploadRun.Done -> run.touched
            is UploadRun.Retry -> run.touched
        }
        if (touched.isNotEmpty()) scheduler.enqueueCatalogSync()
        return if (run is UploadRun.Retry) Result.retry() else Result.success()
    }
}

@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val catalog: PosCatalogRepository,
    private val auth: AuthRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!auth.isSignedIn.first()) return Result.success()
        return when (val r = catalog.syncAll()) {
            is Outcome.Success -> Result.success()
            is Outcome.Failure -> {
                val error = r.error
                val transient = error is AppError.Network || (error is AppError.Api && error.status >= 500)
                if (transient) Result.retry() else Result.success()
            }
        }
    }
}

/**
 * The register and the account (POS-03):
 * - signing in on a phone restores everything from the server — each own
 *   store's main branch gets a catalog (other branches when opened);
 * - signing out is REFUSED while sales are still queued (they would be
 *   lost); with an empty queue the register's tables are cleared;
 * - the catalog refreshes when the app comes to the foreground and when the
 *   network comes back (plus the periodic job and after each upload).
 */
@Singleton
class PosSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PosDao,
    private val auth: AuthRepository,
    private val stores: StoresRepository,
    private val scheduler: PosSyncScheduler,
    private val preferences: AppPreferences,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Called once, from Application.onCreate (main thread). */
    fun start() {
        scope.launch {
            auth.isSignedIn.distinctUntilChanged().collect { signedIn ->
                if (signedIn) {
                    scheduler.schedulePeriodic()
                    scheduler.enqueueUpload()
                    restore()
                } else {
                    scheduler.stopCatalog()
                    preferences.setPosStores(emptyList())
                    // Signed out some other way (session expired, password changed):
                    // keep the register's data only if sales are still waiting.
                    if (dao.pendingCount() == 0) dao.clearAll()
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = refresh()
            },
        )
        context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
            },
        )
    }

    private fun refresh() {
        scope.launch {
            if (!auth.isSignedIn.first()) return@launch
            scheduler.enqueueUpload()
            scheduler.enqueueCatalogSync()
        }
    }

    /** Every own store's main branch becomes a synced catalog. */
    private suspend fun restore() {
        val mine = stores.mine()
        if (mine is Outcome.Success) {
            // Who may see the Register tab and its shortcut (POS-05): stores with a branch.
            preferences.setPosStores(mine.value.mapNotNull { st -> st.primaryBranchId?.let { Triple(st.id, st.name, it) } })
            for (store in mine.value) {
                val branchId = store.primaryBranchId ?: continue
                if (dao.syncState(store.id, branchId) == null) {
                    dao.saveSyncState(SyncStateEntity(store.id, branchId, cursor = null, fullSyncDone = false, lastSyncedAt = null))
                }
            }
        }
        scheduler.enqueueCatalogSync()
    }

    /** Sign out — unless sales have not reached the server yet. */
    suspend fun signOut(): PosSignOut {
        val pending = dao.pendingCount()
        if (pending > 0) {
            scheduler.enqueueUpload()
            return PosSignOut.Blocked(pending)
        }
        dao.clearAll()
        auth.logout()
        return PosSignOut.Done
    }
}
