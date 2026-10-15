package com.azza.feature.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.azza.core.common.AppError
import com.azza.core.common.ApplicationScope
import com.azza.core.common.Outcome
import com.azza.core.data.AccountRepository
import com.azza.core.data.AuthRepository
import com.azza.core.data.NotificationsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Notifications reach the phone by polling, not push (product decision,
 * 2026-10-15): FCM is unreliable in Iran and an Iranian push provider needs
 * an account and backend work. WorkManager runs [NotificationPollWorker]
 * about every 15 minutes (Android's minimum for periodic work), only on a
 * network, and only while someone is signed in and has not switched it off.
 * Swapping in real push later replaces this class; the shade code stays.
 */
@Singleton
class NotificationPolling @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val systemNotifications: SystemNotifications,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            combine(auth.isSignedIn, account.phoneNotifications) { signedIn, on -> signedIn && on }
                .distinctUntilChanged()
                .collect { run -> if (run) schedule() else stop() }
        }
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun stop() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        // Signed out, or switched off: nothing of this account stays in the shade.
        systemNotifications.clearAll()
    }

    private companion object {
        const val WORK_NAME = "notification-poll"
    }
}

@HiltWorker
class NotificationPollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifications: NotificationsRepository,
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val systemNotifications: SystemNotifications,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!auth.isSignedIn.first() || !account.phoneNotifications.first()) return Result.success()
        return when (val r = notifications.unseenSinceLastPoll()) {
            is Outcome.Success -> {
                systemNotifications.post(r.value)
                Result.success()
            }
            // Offline or a server hiccup: WorkManager tries again with backoff.
            is Outcome.Failure -> if (r.error is AppError.Network || (r.error as? AppError.Api)?.status?.let { it >= 500 } == true) Result.retry() else Result.success()
        }
    }
}
