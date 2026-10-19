package com.azza.core.data.pos

import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.common.ProductCodes
import com.azza.core.common.WebDestination
import com.azza.core.common.WebLinks
import com.azza.core.database.pos.BranchStockEntity
import com.azza.core.database.pos.CatalogCodeEntity
import com.azza.core.database.pos.CatalogPageWrite
import com.azza.core.database.pos.CatalogProductEntity
import com.azza.core.database.pos.CatalogProductWithStock
import com.azza.core.database.pos.PosDao
import com.azza.core.database.pos.SyncStateEntity
import com.azza.core.model.CodeLookup
import com.azza.core.model.PosCode
import com.azza.core.model.PosItem
import com.azza.core.model.PosStock
import com.azza.core.network.api.PosApi
import com.azza.core.network.model.CatalogPageDto
import com.azza.core.network.safeApiCall
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The register's local copy of a store's catalog (POS-03), kept current by
 * apps/api's catalog sync: a full sync the first time a store/branch is
 * opened, then deltas with the server's cursor, tombstones applied. The
 * server is the source of truth: its values replace local ones, except
 * that sales still in the queue stay taken off the shop-floor count
 * ([PosDao.applyCatalogPage]).
 *
 * Lookups read only the local copy, so they work offline.
 */
@Singleton
class PosCatalogRepository @Inject constructor(
    private val dao: PosDao,
    private val api: PosApi,
    private val json: Json,
    private val webLinks: WebLinks,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Makes [storeId]/[branchId] one of the places this phone keeps a catalog
     * for, and brings it up to date. Opening it again is cheap: a delta.
     */
    suspend fun open(storeId: String, branchId: String): Outcome<Unit> {
        if (dao.syncState(storeId, branchId) == null) {
            dao.saveSyncState(SyncStateEntity(storeId, branchId, cursor = null, fullSyncDone = false, lastSyncedAt = null))
        }
        return sync(storeId, branchId)
    }

    /** Every store/branch this phone has opened. Stops at the first one the network fails for. */
    suspend fun syncAll(): Outcome<Unit> {
        for (state in dao.allSyncStates()) {
            val result = sync(state.storeId, state.branchId)
            if (result is Outcome.Failure && result.error is AppError.Network) return result
        }
        return Outcome.Success(Unit)
    }

    /**
     * Pages until the server says there is no more. One sync per
     * store/branch at a time; a second caller waits and then finds little
     * left to do. A cursor the server no longer accepts starts over from
     * scratch, once.
     */
    suspend fun sync(storeId: String, branchId: String): Outcome<Unit> =
        locks.getOrPut("$storeId/$branchId") { Mutex() }.withLock {
            when (val first = syncLocked(storeId, branchId)) {
                is Outcome.Failure ->
                    if ((first.error as? AppError.Api)?.code == "SYNC_CURSOR_INVALID") {
                        dao.saveSyncState(SyncStateEntity(storeId, branchId, null, fullSyncDone = false, lastSyncedAt = null))
                        syncLocked(storeId, branchId)
                    } else {
                        first
                    }
                is Outcome.Success -> first
            }
        }

    private suspend fun syncLocked(storeId: String, branchId: String): Outcome<Unit> {
        var state = dao.syncState(storeId, branchId)
            ?: SyncStateEntity(storeId, branchId, null, fullSyncDone = false, lastSyncedAt = null)
        // A full sync sends no tombstones: what it did not send is gone.
        val fullFromScratch = !state.fullSyncDone && state.cursor == null
        val seen = mutableSetOf<String>()
        while (true) {
            val page = when (val r = safeApiCall(json) { api.catalog(storeId, branchId, state.cursor, PAGE_SIZE).data }) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> return r
            }
            seen += page.items.map { it.id }
            val done = !page.hasMore
            state = SyncStateEntity(
                storeId = storeId,
                branchId = branchId,
                cursor = page.nextCursor,
                fullSyncDone = state.fullSyncDone || done,
                lastSyncedAt = if (done) System.currentTimeMillis() else state.lastSyncedAt,
            )
            dao.applyCatalogPage(page.toWrite(storeId, branchId, state))
            if (done) break
        }
        if (fullFromScratch) dao.removeProductsOfStoreExcept(storeId, seen.toList())
        return Outcome.Success(Unit)
    }

    /** One item as the register sees it at [branchId]; null when this phone does not have it. */
    suspend fun item(productId: String, branchId: String): PosItem? =
        dao.productWithStock(productId)?.toModel(branchId)

    /**
     * What [raw] (a scan, or something typed) is, for [storeId] — read from
     * the local catalog only, so it works offline:
     * - an AZZA post address → [CodeLookup.PostLink], never looked up as a code;
     * - not a possible code → [CodeLookup.Invalid];
     * - a code of an item of THIS store → [CodeLookup.Product];
     * - any other valid code → [CodeLookup.UnknownCode] (normalized).
     */
    suspend fun lookup(storeId: String, raw: String, branchId: String? = null): CodeLookup {
        postLink(raw.trim())?.let { return it }
        return when (val normalized = ProductCodes.normalize(raw)) {
            is ProductCodes.Normalized.Bad -> CodeLookup.Invalid(normalized.reason.name)
            is ProductCodes.Normalized.Ok -> {
                val product = dao.productByCode(storeId, normalized.code)
                if (product != null) {
                    CodeLookup.Product(dao.productWithStock(product.id)!!.toModel(branchId))
                } else {
                    CodeLookup.UnknownCode(normalized.code, normalized.symbology.name)
                }
            }
        }
    }

    private fun postLink(text: String): CodeLookup.PostLink? {
        if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) return null
        when (val destination = webLinks.parse(text)) {
            is WebDestination.Product -> return CodeLookup.PostLink(text, destination.id)
            null -> Unit
            else -> return null
        }
        // A `/q/<…>` short link on the website (optionally behind a locale).
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val site = runCatching { URI(webLinks.page("")) }.getOrNull() ?: return null
        if (!uri.host.equals(site.host, ignoreCase = true)) return null
        val parts = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }.let { if (it.firstOrNull() in LOCALES) it.drop(1) else it }
        return if (parts.size == 2 && parts[0] == "q") CodeLookup.PostLink(text, productId = null) else null
    }

    private companion object {
        const val PAGE_SIZE = 200
        val LOCALES = setOf("fa", "ckb", "en")
    }
}

internal fun CatalogPageDto.toWrite(storeId: String, branchId: String, state: SyncStateEntity) = CatalogPageWrite(
    storeId = storeId,
    branchId = branchId,
    products = items.map {
        CatalogProductEntity(
            id = it.id,
            storeId = storeId,
            name = it.name,
            priceAmountMinor = it.priceAmountMinor,
            salePriceAmountMinor = it.salePriceAmountMinor,
            currency = it.currency,
            visibility = it.visibility,
            status = it.status,
            imageUrl = it.imageUrl,
            updatedAt = it.updatedAt,
        )
    },
    codes = items.flatMap { item ->
        item.codes.map { CatalogCodeEntity(id = it.id, storeId = storeId, productId = item.id, code = it.code, symbology = it.symbology) }
    },
    stock = items.map {
        BranchStockEntity(
            productId = it.id,
            branchId = branchId,
            storeId = storeId,
            storeQty = it.storeQty,
            serverStoreQty = it.storeQty,
            onlineQty = it.onlineQty,
            reserved = it.reserved,
            needsReview = it.needsReview,
        )
    },
    removedProductIds = tombstones.filter { it.type == "PRODUCT" }.map { it.id },
    removedCodeIds = tombstones.filter { it.type == "CODE" }.map { it.id },
    state = state,
)

internal fun CatalogProductWithStock.toModel(branchId: String?) = PosItem(
    id = product.id,
    storeId = product.storeId,
    name = product.name,
    priceMinor = product.priceAmountMinor,
    salePriceMinor = product.salePriceAmountMinor,
    currency = product.currency,
    visibility = product.visibility,
    status = product.status,
    imageUrl = product.imageUrl,
    codes = codes.map { PosCode(it.id, it.code, it.symbology) },
    stock = stock.firstOrNull { it.branchId == branchId }?.let { PosStock(it.storeQty, it.onlineQty, it.needsReview) },
)
