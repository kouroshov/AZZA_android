package com.azza.core.data.pos

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import com.azza.core.common.AppError
import com.azza.core.common.ApplicationScope
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.data.PhotoCompressor
import com.azza.core.data.StoresRepository
import com.azza.core.database.pos.PosDao
import com.azza.core.datastore.AppPreferences
import com.azza.core.model.CodeLookup
import com.azza.core.model.PosItem
import com.azza.core.network.api.ListingsApi
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.PosApi
import com.azza.core.network.model.AttachCodeRequest
import com.azza.core.network.model.CreateInventoryItemRequest
import com.azza.core.network.model.SetVisibilityRequest
import com.azza.core.network.model.StockTransferRequest
import com.azza.core.network.safeApiCall
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** A store's branch, as the inventory and the register show it. */
data class PosBranch(val id: String, val name: String)

/** What a new item is made of (scan-to-create). */
data class NewItem(
    val name: String,
    val priceMinor: Long,
    val storeQty: Int? = null,
    val onlineQty: Int? = null,
    /** The scanned / typed code, normalized; null for "no barcode". */
    val code: String? = null,
    /** "No barcode": the server generates an internal EAN-13 for it. */
    val generateCode: Boolean = false,
    val photoUri: String? = null,
)

/**
 * The store's private inventory (POS-04). Reads come from the register's
 * local catalog (Room), so browsing works offline; every change goes to
 * apps/api and is followed by a catalog sync, so the list shows the server's
 * answer. A change offline fails with [AppError.Network] — the screen says
 * "connect to edit".
 */
interface InventoryRepository {
    val online: StateFlow<Boolean>

    fun items(storeId: String, branchId: String): Flow<List<PosItem>>
    fun item(productId: String, branchId: String): Flow<PosItem?>

    /** The store's branches — from the server when online, the last copy otherwise. */
    suspend fun branches(storeId: String): List<PosBranch>
    fun lastBranch(storeId: String): Flow<String?>
    suspend fun rememberBranch(storeId: String, branchId: String)

    /** Starts (or continues) keeping this branch's catalog on the phone. */
    suspend fun open(storeId: String, branchId: String): Outcome<Unit>
    suspend fun lookup(storeId: String, raw: String, branchId: String): CodeLookup

    /** Creates a PRIVATE item; returns its id. */
    suspend fun create(storeId: String, branchId: String, item: NewItem): Outcome<String>
    suspend fun rename(storeId: String, branchId: String, productId: String, name: String, priceMinor: Long): Outcome<Unit>
    suspend fun setPhoto(storeId: String, branchId: String, productId: String, uri: String): Outcome<Unit>
    suspend fun attachCode(storeId: String, branchId: String, productId: String, code: String, symbology: String?): Outcome<Unit>
    suspend fun generateCode(storeId: String, branchId: String, productId: String): Outcome<Unit>
    suspend fun removeCode(storeId: String, branchId: String, productId: String, codeId: String): Outcome<Unit>
    suspend fun transfer(storeId: String, branchId: String, productId: String, fromStore: Boolean, qty: Int): Outcome<Unit>
    suspend fun setPublic(storeId: String, branchId: String, productId: String, public: Boolean): Outcome<Unit>
}

/** 409 CODE_ALREADY_ASSIGNED: the item of this store that already has the code. */
data class CodeOwner(val productId: String, val name: String)

fun AppError.codeOwner(json: Json = Json): CodeOwner? {
    if (this !is AppError.Api || code != "CODE_ALREADY_ASSIGNED") return null
    val details = details?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return null
    val id = details["productId"]?.jsonPrimitive?.content ?: return null
    return CodeOwner(id, details["name"]?.jsonPrimitive?.content.orEmpty())
}

/** 400 PRODUCT_NOT_PUBLISHABLE: what the item lacks (title, description, categoryId, price). */
fun AppError.missingForPublic(json: Json = Json): List<String>? {
    if (this !is AppError.Api || code != "PRODUCT_NOT_PUBLISHABLE") return null
    val details = details?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return emptyList()
    return details["missing"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
}

@Singleton
class DefaultInventoryRepository @Inject constructor(
    private val dao: PosDao,
    private val api: PosApi,
    private val listings: ListingsApi,
    private val catalog: PosCatalogRepository,
    private val stores: StoresRepository,
    private val preferences: AppPreferences,
    private val photos: PhotoCompressor,
    private val json: Json,
    private val media: MediaUrlResolver,
    network: NetworkMonitor,
) : InventoryRepository {
    override val online: StateFlow<Boolean> = network.online

    override fun items(storeId: String, branchId: String): Flow<List<PosItem>> =
        dao.observeStore(storeId).map { rows -> rows.map { it.toModel(branchId).withImage() } }

    override fun item(productId: String, branchId: String): Flow<PosItem?> =
        dao.observeItem(productId).map { it?.toModel(branchId)?.withImage() }

    /** The catalog sends apps/api's own path (/api/v1/media/…): made absolute for the image loader. */
    private fun PosItem.withImage() = copy(imageUrl = media.resolve(imageUrl))

    override suspend fun branches(storeId: String): List<PosBranch> {
        when (val r = stores.branches(storeId)) {
            is Outcome.Success -> {
                val live = r.value.filter { it.status == "ACTIVE" }.map { PosBranch(it.id, it.name) }
                preferences.setPosBranches(storeId, live.map { it.id to it.name })
                return live
            }
            is Outcome.Failure -> return preferences.posBranches(storeId).first().map { PosBranch(it.first, it.second) }
        }
    }

    override fun lastBranch(storeId: String): Flow<String?> = preferences.posLastBranch(storeId)

    override suspend fun rememberBranch(storeId: String, branchId: String) = preferences.setPosLastBranch(storeId, branchId)

    override suspend fun open(storeId: String, branchId: String): Outcome<Unit> = catalog.open(storeId, branchId)

    override suspend fun lookup(storeId: String, raw: String, branchId: String): CodeLookup = catalog.lookup(storeId, raw, branchId)

    override suspend fun create(storeId: String, branchId: String, item: NewItem): Outcome<String> {
        val created = safeApiCall(json) {
            api.createItem(
                storeId,
                CreateInventoryItemRequest(
                    name = item.name.trim(),
                    price = item.priceMinor / 100.0,
                    branchId = branchId,
                    storeQty = item.storeQty,
                    onlineQty = item.onlineQty,
                    code = item.code,
                ),
            ).data
        }
        val id = when (created) {
            is Outcome.Success -> created.value.id
            is Outcome.Failure -> return created
        }
        // The item exists from here on: a failed extra (code, photo) is the
        // detail screen's to retry, never a reason to report the create failed.
        if (item.generateCode) safeApiCall(json) { api.generateCode(storeId, id) }
        item.photoUri?.let { upload(id, it) }
        catalog.sync(storeId, branchId)
        return Outcome.Success(id)
    }

    override suspend fun rename(storeId: String, branchId: String, productId: String, name: String, priceMinor: Long): Outcome<Unit> =
        afterwards(storeId, branchId) {
            listings.update(productId, buildJsonObject {
                put("title", name.trim())
                put("price", priceMinor / 100.0)
            })
        }

    override suspend fun setPhoto(storeId: String, branchId: String, productId: String, uri: String): Outcome<Unit> {
        val result = upload(productId, uri)
        // A photo does not touch the product row the catalog tracks: an edit
        // of the item brings it to other phones; this one is refreshed now.
        if (result is Outcome.Success) catalog.sync(storeId, branchId)
        return result
    }

    override suspend fun attachCode(storeId: String, branchId: String, productId: String, code: String, symbology: String?) =
        afterwards(storeId, branchId) { api.attachCode(storeId, productId, AttachCodeRequest(code, symbology)) }

    override suspend fun generateCode(storeId: String, branchId: String, productId: String) =
        afterwards(storeId, branchId) { api.generateCode(storeId, productId) }

    override suspend fun removeCode(storeId: String, branchId: String, productId: String, codeId: String) =
        afterwards(storeId, branchId) { api.removeCode(storeId, productId, codeId) }

    override suspend fun transfer(storeId: String, branchId: String, productId: String, fromStore: Boolean, qty: Int) =
        afterwards(storeId, branchId) {
            api.transfer(storeId, branchId, StockTransferRequest(productId, if (fromStore) "STORE" else "ONLINE", qty))
        }

    override suspend fun setPublic(storeId: String, branchId: String, productId: String, public: Boolean) =
        afterwards(storeId, branchId) {
            api.setVisibility(storeId, productId, SetVisibilityRequest(if (public) "PUBLIC" else "PRIVATE"))
        }

    /** The call, then the catalog refreshed so the screen shows the server's answer. */
    private suspend fun afterwards(storeId: String, branchId: String, call: suspend () -> Any?): Outcome<Unit> {
        val result = safeApiCall(json) { call() }.map { }
        if (result is Outcome.Success) catalog.sync(storeId, branchId)
        return result
    }

    private suspend fun upload(productId: String, uri: String): Outcome<Unit> {
        val bytes = withContext(Dispatchers.IO) { photos.toJpeg(Uri.parse(uri)) }
            ?: return Outcome.Failure(AppError.Unexpected(IllegalArgumentException("Unreadable photo")))
        val part = MultipartBody.Part.createFormData("file", "photo.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        return safeApiCall(json) { listings.uploadPhoto(productId, part) }.map { }
    }
}

/** Whether the phone has a working network — for "connect to edit". */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    val online: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isOnline())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(isOnline())
        manager?.registerDefaultNetworkCallback(callback)
        awaitClose { manager?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, isOnline())

    private fun isOnline(): Boolean {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InventoryModule {
    @Binds
    abstract fun inventory(impl: DefaultInventoryRepository): InventoryRepository
}
