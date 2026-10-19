package com.azza.core.database.pos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** What one catalog page asks the database to do, in one transaction. */
data class CatalogPageWrite(
    val storeId: String,
    val branchId: String,
    val products: List<CatalogProductEntity>,
    /** The complete live code list of each product in [products]. */
    val codes: List<CatalogCodeEntity>,
    /** Server stock at this branch; storeQty is recomputed here. */
    val stock: List<BranchStockEntity>,
    val removedProductIds: List<String>,
    val removedCodeIds: List<String>,
    val state: SyncStateEntity,
)

@Dao
abstract class PosDao {

    // ---------------------------------------------------------------- catalog

    @Query(
        "SELECT p.* FROM catalog_products p JOIN catalog_codes c ON c.productId = p.id " +
            "WHERE c.storeId = :storeId AND c.code = :code AND p.storeId = :storeId LIMIT 1",
    )
    abstract suspend fun productByCode(storeId: String, code: String): CatalogProductEntity?

    @Transaction
    @Query("SELECT * FROM catalog_products WHERE id = :id")
    abstract suspend fun productWithStock(id: String): CatalogProductWithStock?

    @Query("SELECT * FROM branch_stock WHERE productId = :productId AND branchId = :branchId")
    abstract suspend fun stock(productId: String, branchId: String): BranchStockEntity?

    @Query("SELECT * FROM branch_stock WHERE productId = :productId AND branchId = :branchId")
    abstract fun observeStock(productId: String, branchId: String): Flow<BranchStockEntity?>

    @Query("SELECT COUNT(*) FROM catalog_products WHERE storeId = :storeId")
    abstract suspend fun productCount(storeId: String): Int

    @Query("SELECT * FROM sync_state WHERE storeId = :storeId AND branchId = :branchId")
    abstract suspend fun syncState(storeId: String, branchId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state")
    abstract suspend fun allSyncStates(): List<SyncStateEntity>

    @Upsert
    protected abstract suspend fun upsertSyncState(state: SyncStateEntity)

    @Upsert
    protected abstract suspend fun upsertProducts(rows: List<CatalogProductEntity>)

    @Upsert
    protected abstract suspend fun upsertCodes(rows: List<CatalogCodeEntity>)

    @Upsert
    protected abstract suspend fun upsertStock(rows: List<BranchStockEntity>)

    @Query("DELETE FROM catalog_codes WHERE productId IN (:productIds)")
    protected abstract suspend fun deleteCodesOf(productIds: List<String>)

    @Query("DELETE FROM catalog_codes WHERE id IN (:ids)")
    protected abstract suspend fun deleteCodes(ids: List<String>)

    @Query("DELETE FROM branch_stock WHERE productId IN (:productIds)")
    protected abstract suspend fun deleteStockOf(productIds: List<String>)

    @Query("DELETE FROM catalog_products WHERE id IN (:ids)")
    protected abstract suspend fun deleteProducts(ids: List<String>)

    /** Units of [productId] at [branchId] in sales still waiting to be sent. */
    @Query(
        "SELECT COALESCE(SUM(i.quantity), 0) FROM pending_sale_items i JOIN pending_sales s ON s.id = i.saleId " +
            "WHERE s.status = 'PENDING' AND s.branchId = :branchId AND i.productId = :productId",
    )
    abstract suspend fun queuedQuantity(productId: String, branchId: String): Int

    /**
     * One catalog page, all or nothing. Server values replace local ones —
     * except that the shop-floor count of an item with sales still in the
     * queue is the server's figure less those sales (the server has not
     * seen them yet). Deletions are applied last, so a tombstone always wins.
     */
    @Transaction
    open suspend fun applyCatalogPage(write: CatalogPageWrite) {
        if (write.products.isNotEmpty()) {
            upsertProducts(write.products)
            deleteCodesOf(write.products.map { it.id })
        }
        if (write.codes.isNotEmpty()) upsertCodes(write.codes)
        if (write.stock.isNotEmpty()) {
            upsertStock(
                write.stock.map { row ->
                    row.copy(storeQty = row.serverStoreQty - queuedQuantity(row.productId, row.branchId))
                },
            )
        }
        if (write.removedCodeIds.isNotEmpty()) deleteCodes(write.removedCodeIds)
        if (write.removedProductIds.isNotEmpty()) {
            deleteCodesOf(write.removedProductIds)
            deleteStockOf(write.removedProductIds)
            deleteProducts(write.removedProductIds)
        }
        upsertSyncState(write.state)
    }

    @Upsert
    abstract suspend fun saveSyncState(state: SyncStateEntity)

    @Query("SELECT id FROM catalog_products WHERE storeId = :storeId")
    protected abstract suspend fun productIdsOf(storeId: String): List<String>

    /**
     * After a FULL sync (which sends no tombstones): every local item of
     * [storeId] the server did not send is gone. Deleted in chunks, under
     * SQLite's bound-parameter limit.
     */
    @Transaction
    open suspend fun removeProductsOfStoreExcept(storeId: String, keep: List<String>) {
        val kept = keep.toHashSet()
        productIdsOf(storeId).filterNot { it in kept }.chunked(500).forEach { stale ->
            deleteCodesOf(stale)
            deleteStockOf(stale)
            deleteProducts(stale)
        }
    }

    // ---------------------------------------------------------------- sales

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertSale(sale: PendingSaleEntity)

    @Insert
    protected abstract suspend fun insertItems(items: List<PendingSaleItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertStockIfMissing(row: BranchStockEntity)

    @Query("UPDATE branch_stock SET storeQty = storeQty - :quantity WHERE productId = :productId AND branchId = :branchId")
    protected abstract suspend fun takeFromShopFloor(productId: String, branchId: String, quantity: Int)

    /**
     * The sale, its lines and the local shop-floor counts, in ONE
     * transaction: either the sale is recorded and the stock shows it, or
     * neither happened. An item the catalog has no stock row for (not yet
     * synced at this branch) gets one, at zero, before it is taken from.
     */
    @Transaction
    open suspend fun recordSale(sale: PendingSaleEntity, items: List<PendingSaleItemEntity>) {
        insertSale(sale)
        insertItems(items)
        for ((productId, lines) in items.groupBy { it.productId }) {
            insertStockIfMissing(
                BranchStockEntity(
                    productId = productId,
                    branchId = sale.branchId,
                    storeId = sale.storeId,
                    storeQty = 0,
                    serverStoreQty = 0,
                    onlineQty = 0,
                    reserved = 0,
                    needsReview = false,
                ),
            )
            takeFromShopFloor(productId, sale.branchId, lines.sumOf { it.quantity })
        }
    }

    /** The (store, branch) pairs that have sales to send. */
    @Query("SELECT DISTINCT storeId, branchId FROM pending_sales WHERE status = 'PENDING'")
    abstract suspend fun pendingDestinations(): List<SaleDestination>

    @Transaction
    @Query(
        "SELECT * FROM pending_sales WHERE status = 'PENDING' AND storeId = :storeId AND branchId = :branchId " +
            "ORDER BY soldAt, id LIMIT :limit",
    )
    abstract suspend fun pendingBatch(storeId: String, branchId: String, limit: Int): List<PendingSaleWithItems>

    /** Sent and recorded by the server (ACCEPTED or DUPLICATE): the lines go with the sale. */
    @Query("DELETE FROM pending_sales WHERE id IN (:ids)")
    abstract suspend fun deleteSales(ids: List<String>)

    @Query(
        "UPDATE pending_sales SET status = 'REJECTED', rejectReason = :reason, rejectDetails = :details, " +
            "lastError = NULL, attempts = attempts + 1 WHERE id = :id",
    )
    abstract suspend fun markRejected(id: String, reason: String, details: String?)

    @Query("UPDATE pending_sales SET lastError = :error, attempts = attempts + 1 WHERE id IN (:ids)")
    abstract suspend fun markAttemptFailed(ids: List<String>, error: String)

    @Query("SELECT COUNT(*) FROM pending_sales WHERE status = 'PENDING'")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_sales WHERE status = 'PENDING'")
    abstract suspend fun pendingCount(): Int

    @Transaction
    @Query("SELECT * FROM pending_sales WHERE status = 'REJECTED' ORDER BY soldAt DESC")
    abstract fun observeRejected(): Flow<List<PendingSaleWithItems>>

    /** Removes a rejected sale the person has dealt with. */
    @Query("DELETE FROM pending_sales WHERE id = :id AND status = 'REJECTED'")
    abstract suspend fun dismissRejected(id: String)

    // ---------------------------------------------------------------- session

    @Query("DELETE FROM catalog_codes")
    protected abstract suspend fun clearCodes()

    @Query("DELETE FROM branch_stock")
    protected abstract suspend fun clearStock()

    @Query("DELETE FROM catalog_products")
    protected abstract suspend fun clearProducts()

    @Query("DELETE FROM sync_state")
    protected abstract suspend fun clearSyncState()

    @Query("DELETE FROM pending_sales")
    protected abstract suspend fun clearSales()

    /** Everything the register holds — only ever called with an empty queue. */
    @Transaction
    open suspend fun clearAll() {
        clearCodes()
        clearStock()
        clearProducts()
        clearSyncState()
        clearSales()
    }
}

data class SaleDestination(val storeId: String, val branchId: String)
