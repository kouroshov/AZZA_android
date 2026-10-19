package com.azza.core.database.pos

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// The sales register's own records (POS-03). Unlike the response cache in
// AzzaDatabase these are NOT disposable: a queued sale is money that changed
// hands. They live in their own database (PosDatabase) with real migrations.

/** One item of a store, as the server's catalog sync last described it. */
@Entity(tableName = "catalog_products", indices = [Index("storeId")])
data class CatalogProductEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val name: String,
    /** List price, minor units. */
    val priceAmountMinor: Long,
    /** After the item's own discount — what the register charges. */
    val salePriceAmountMinor: Long,
    val currency: String,
    val visibility: String,
    val status: String,
    val imageUrl: String?,
    val updatedAt: String?,
)

/** A code of an item. Looked up by (storeId, code) — never across stores. */
@Entity(tableName = "catalog_codes", indices = [Index("storeId", "code"), Index("productId")])
data class CatalogCodeEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val productId: String,
    val code: String,
    val symbology: String,
)

/**
 * An item's stock at one branch. [serverStoreQty] is what the server last
 * said; [storeQty] is what the register shows: the server's figure less the
 * sales still waiting in the queue (re-applied after every refresh).
 */
@Entity(tableName = "branch_stock", primaryKeys = ["productId", "branchId"], indices = [Index("branchId")])
data class BranchStockEntity(
    val productId: String,
    val branchId: String,
    val storeId: String,
    val storeQty: Int,
    val serverStoreQty: Int,
    val onlineQty: Int,
    val reserved: Int,
    val needsReview: Boolean,
)

/** Where the catalog sync of one store/branch stands. */
@Entity(tableName = "sync_state", primaryKeys = ["storeId", "branchId"])
data class SyncStateEntity(
    val storeId: String,
    val branchId: String,
    /** The server's opaque cursor; null until the first page arrived. */
    val cursor: String?,
    /** True once a full sync reached its last page. */
    val fullSyncDone: Boolean,
    val lastSyncedAt: Long?,
)

/**
 * A completed sale waiting for the server. [id] is generated here and is the
 * server's idempotency key, so sending it twice records it once.
 * [status] is PENDING (to send) or REJECTED (the server refused it — kept,
 * with its reason, for the person to see).
 */
@Entity(tableName = "pending_sales", indices = [Index("status"), Index("storeId", "branchId")])
data class PendingSaleEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val branchId: String,
    val deviceId: String,
    val paymentMethod: String,
    val totalAmountMinor: Long,
    /** Epoch millis, device clock. */
    val soldAt: Long,
    val status: String,
    val rejectReason: String?,
    /** The server's `details` (JSON), for the screen to explain the refusal. */
    val rejectDetails: String?,
    /** Why the last upload did not get an answer (network, server error); cleared on success. */
    val lastError: String?,
    val attempts: Int,
) {
    companion object {
        const val PENDING = "PENDING"
        const val REJECTED = "REJECTED"
    }
}

@Entity(
    tableName = "pending_sale_items",
    foreignKeys = [
        ForeignKey(
            entity = PendingSaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("saleId"), Index("productId")],
)
data class PendingSaleItemEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val saleId: String,
    val productId: String,
    /** The name when it was sold, for the queue screen. */
    val name: String,
    val quantity: Int,
    /** The price it was sold at, minor units. */
    val unitPriceAmountMinor: Long,
)

data class PendingSaleWithItems(
    @Embedded val sale: PendingSaleEntity,
    @Relation(parentColumn = "id", entityColumn = "saleId") val items: List<PendingSaleItemEntity>,
)

data class CatalogProductWithStock(
    @Embedded val product: CatalogProductEntity,
    @Relation(parentColumn = "id", entityColumn = "productId") val codes: List<CatalogCodeEntity>,
    @Relation(parentColumn = "id", entityColumn = "productId") val stock: List<BranchStockEntity>,
)
