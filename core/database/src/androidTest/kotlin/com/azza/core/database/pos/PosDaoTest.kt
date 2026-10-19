package com.azza.core.database.pos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosDaoTest {
    private lateinit var db: PosDatabase
    private lateinit var dao: PosDao

    private val store = "store-1"
    private val branch = "branch-1"

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PosDatabase::class.java).build()
        dao = db.pos()
    }

    @After
    fun close() = db.close()

    private fun product(id: String) = CatalogProductEntity(
        id = id, storeId = store, name = "Item $id", priceAmountMinor = 1000, salePriceAmountMinor = 1000,
        currency = "IRR", visibility = "PRIVATE", status = "DRAFT", imageUrl = null, updatedAt = null,
    )

    private fun stock(productId: String, server: Int) = BranchStockEntity(
        productId = productId, branchId = branch, storeId = store, storeQty = server, serverStoreQty = server,
        onlineQty = 0, reserved = 0, needsReview = false,
    )

    private fun page(
        products: List<String> = emptyList(),
        serverQty: Map<String, Int> = emptyMap(),
        codes: List<CatalogCodeEntity> = emptyList(),
        removedProducts: List<String> = emptyList(),
        removedCodes: List<String> = emptyList(),
    ) = CatalogPageWrite(
        storeId = store,
        branchId = branch,
        products = products.map(::product),
        codes = codes,
        stock = products.map { stock(it, serverQty[it] ?: 0) },
        removedProductIds = removedProducts,
        removedCodeIds = removedCodes,
        state = SyncStateEntity(store, branch, cursor = "c", fullSyncDone = true, lastSyncedAt = 1),
    )

    private fun sale(id: String) = PendingSaleEntity(
        id = id, storeId = store, branchId = branch, deviceId = "d", paymentMethod = "CASH", totalAmountMinor = 3000,
        soldAt = 1, status = PendingSaleEntity.PENDING, rejectReason = null, rejectDetails = null, lastError = null, attempts = 0,
    )

    private fun line(saleId: String, productId: String, qty: Int, rowId: Long = 0) =
        PendingSaleItemEntity(rowId = rowId, saleId = saleId, productId = productId, name = "x", quantity = qty, unitPriceAmountMinor = 1000)

    @Test
    fun recordSale_writes_the_sale_its_lines_and_the_stock_together() = runTest {
        dao.applyCatalogPage(page(listOf("p1"), mapOf("p1" to 10)))
        dao.recordSale(sale("s1"), listOf(line("s1", "p1", 2), line("s1", "p1", 1)))

        assertThat(dao.stock("p1", branch)!!.storeQty).isEqualTo(7)
        assertThat(dao.pendingCount()).isEqualTo(1)
        assertThat(dao.pendingBatch(store, branch, 100).single().items).hasSize(2)
    }

    @Test
    fun recordSale_is_all_or_nothing() = runTest {
        dao.applyCatalogPage(page(listOf("p1"), mapOf("p1" to 10)))
        dao.recordSale(sale("s1"), listOf(line("s1", "p1", 1, rowId = 42)))

        // The second sale's line collides on its primary key AFTER the sale row
        // was inserted: the whole transaction must roll back.
        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { dao.recordSale(sale("s2"), listOf(line("s2", "p1", 5, rowId = 42))) }
        }
        assertThat(dao.pendingCount()).isEqualTo(1)
        assertThat(dao.pendingBatch(store, branch, 100).map { it.sale.id }).containsExactly("s1")
        assertThat(dao.stock("p1", branch)!!.storeQty).isEqualTo(9)
    }

    @Test
    fun recordSale_creates_a_stock_row_for_an_item_without_one() = runTest {
        dao.recordSale(sale("s1"), listOf(line("s1", "unsynced", 2)))
        assertThat(dao.stock("unsynced", branch)!!.storeQty).isEqualTo(-2)
    }

    @Test
    fun tombstones_remove_items_and_codes() = runTest {
        dao.applyCatalogPage(
            page(
                listOf("p1", "p2"),
                codes = listOf(
                    CatalogCodeEntity("c1", store, "p1", "4006381333931", "EAN13"),
                    CatalogCodeEntity("c2", store, "p2", "96385074", "EAN8"),
                    CatalogCodeEntity("c3", store, "p2", "SHELF-1", "CODE128"),
                ),
            ),
        )
        assertThat(dao.productByCode(store, "4006381333931")!!.id).isEqualTo("p1")

        dao.applyCatalogPage(page(removedProducts = listOf("p1"), removedCodes = listOf("c3")))

        assertThat(dao.productByCode(store, "4006381333931")).isNull()
        assertThat(dao.productWithStock("p1")).isNull()
        assertThat(dao.stock("p1", branch)).isNull()
        assertThat(dao.productByCode(store, "SHELF-1")).isNull()
        assertThat(dao.productByCode(store, "96385074")!!.id).isEqualTo("p2")
    }

    @Test
    fun a_code_is_never_found_in_another_store() = runTest {
        dao.applyCatalogPage(page(listOf("p1"), codes = listOf(CatalogCodeEntity("c1", store, "p1", "123", "CODE128"))))
        assertThat(dao.productByCode("another-store", "123")).isNull()
    }

    @Test
    fun queued_sales_stay_taken_off_after_a_catalog_refresh() = runTest {
        dao.applyCatalogPage(page(listOf("p1"), mapOf("p1" to 10)))
        dao.recordSale(sale("s1"), listOf(line("s1", "p1", 3)))
        assertThat(dao.stock("p1", branch)!!.storeQty).isEqualTo(7)

        // The server has not seen s1 yet and still says 10.
        dao.applyCatalogPage(page(listOf("p1"), mapOf("p1" to 10)))
        assertThat(dao.stock("p1", branch)!!.storeQty).isEqualTo(7)

        // Another phone sold 2: the server says 8, s1 still waits here.
        dao.applyCatalogPage(page(listOf("p1"), mapOf("p1" to 8)))
        assertThat(dao.stock("p1", branch)!!.storeQty).isEqualTo(5)

        // s1 reached the server (8 - 3 = 5) and left the queue.
        dao.deleteSales(listOf("s1"))
        dao.applyCatalogPage(page(listOf("p1"), mapOf("p1" to 5)))
        assertThat(dao.stock("p1", branch)!!.storeQty).isEqualTo(5)

        // A refused sale is not re-applied: the server never took that stock.
        dao.recordSale(sale("s2"), listOf(line("s2", "p1", 1)))
        dao.markRejected("s2", "PRODUCT_NOT_IN_STORE", null)
        dao.applyCatalogPage(page(listOf("p1"), mapOf("p1" to 5)))
        assertThat(dao.stock("p1", branch)!!.storeQty).isEqualTo(5)
        assertThat(dao.observeRejected().first().single().sale.rejectReason).isEqualTo("PRODUCT_NOT_IN_STORE")
    }

    @Test
    fun a_full_sync_forgets_what_the_server_no_longer_has() = runTest {
        dao.applyCatalogPage(page(listOf("p1", "p2", "p3")))
        dao.removeProductsOfStoreExcept(store, listOf("p2"))
        assertThat(dao.productCount(store)).isEqualTo(1)
        assertThat(dao.productWithStock("p2")).isNotNull()
    }

    @Test
    fun clearAll_empties_the_register() = runTest {
        dao.applyCatalogPage(page(listOf("p1"), mapOf("p1" to 1)))
        dao.recordSale(sale("s1"), listOf(line("s1", "p1", 1)))
        dao.clearAll()
        assertThat(dao.pendingCount()).isEqualTo(0)
        assertThat(dao.productCount(store)).isEqualTo(0)
        assertThat(dao.allSyncStates()).isEmpty()
    }
}
