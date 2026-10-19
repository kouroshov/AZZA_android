package com.azza.core.data.pos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.azza.core.common.Outcome
import com.azza.core.common.WebLinks
import com.azza.core.database.pos.BranchStockEntity
import com.azza.core.database.pos.CatalogPageWrite
import com.azza.core.database.pos.CatalogProductEntity
import com.azza.core.database.pos.PosDao
import com.azza.core.database.pos.PosDatabase
import com.azza.core.database.pos.SyncStateEntity
import com.azza.core.datastore.AppPreferences
import com.azza.core.model.CodeLookup
import com.azza.core.model.PaymentMethod
import com.azza.core.model.SaleLine
import com.azza.core.network.model.CatalogCodeDto
import com.azza.core.network.model.CatalogItemDto
import com.azza.core.network.model.CatalogPageDto
import com.azza.core.network.model.CatalogTombstoneDto
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosSyncTest {
    private lateinit var context: Context
    private lateinit var db: PosDatabase
    private lateinit var dao: PosDao
    private lateinit var server: FakePosServer
    private lateinit var queue: SaleQueue
    private lateinit var scheduler: PosSyncScheduler
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val store = "store-1"
    private val branch = "branch-1"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java).build()
        dao = db.pos()
        server = FakePosServer()
        scheduler = PosSyncScheduler(context)
        queue = SaleQueue(dao, AppPreferences(context), scheduler)
    }

    @After
    fun tearDown() = db.close()

    /** A fresh uploader: what the app has after its process was killed and restarted. */
    private fun uploader() = SaleUploader(dao, server, json)

    private suspend fun stockItem(id: String, qty: Int) = dao.applyCatalogPage(
        CatalogPageWrite(
            storeId = store,
            branchId = branch,
            products = listOf(CatalogProductEntity(id, store, "Item", 1000, 1000, "IRR", "PRIVATE", "DRAFT", null, null)),
            codes = emptyList(),
            stock = listOf(BranchStockEntity(id, branch, store, qty, qty, 0, 0, false)),
            removedProductIds = emptyList(),
            removedCodeIds = emptyList(),
            state = SyncStateEntity(store, branch, "c", true, 1),
        ),
    )

    private suspend fun sell(productId: String, qty: Int = 1) =
        queue.recordSale(store, branch, PaymentMethod.CASH, listOf(SaleLine(productId, "Item", qty, 1000)))

    private fun worker(): SaleUploadWorker =
        TestListenableWorkerBuilder<SaleUploadWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                        SaleUploadWorker(appContext, workerParameters, uploader(), scheduler)
                },
            )
            .build()

    // ---------------------------------------------------------------- queue

    @Test
    fun the_queue_drains_in_batches_of_100_when_the_server_accepts() = runTest {
        stockItem("p1", 500)
        repeat(150) { sell("p1") }
        assertThat(queue.pendingCount.first()).isEqualTo(150)

        assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())

        assertThat(queue.pendingCount.first()).isEqualTo(0)
        assertThat(server.recorded).hasSize(150)
        assertThat(server.batchSizes).containsExactly(100, 50).inOrder()
    }

    @Test
    fun a_rejected_sale_stays_with_its_reason_and_the_rest_go() = runTest {
        stockItem("p1", 10)
        stockItem("gone", 10)
        server.foreignProducts += "gone"
        val good = sell("p1")
        val bad = sell("gone")

        assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())

        assertThat(queue.pendingCount.first()).isEqualTo(0)
        assertThat(server.recorded).containsExactly(good)
        val rejected = queue.rejected.first()
        assertThat(rejected.map { it.id }).containsExactly(bad)
        assertThat(rejected.single().rejectReason).isEqualTo("PRODUCT_NOT_IN_STORE")

        queue.dismissRejected(bad)
        assertThat(queue.rejected.first()).isEmpty()
    }

    @Test
    fun no_network_means_retry_and_nothing_is_lost() = runTest {
        stockItem("p1", 10)
        sell("p1", 2)
        server.offlineCalls = 1

        assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(queue.pendingCount.first()).isEqualTo(1)
        assertThat(dao.pendingBatch(store, branch, 10).single().sale.lastError).isEqualTo("NETWORK")

        assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(queue.pendingCount.first()).isEqualTo(0)
        assertThat(server.recorded).hasSize(1)
    }

    @Test
    fun a_lost_answer_and_a_restarted_process_never_record_a_sale_twice() = runTest {
        stockItem("p1", 10)
        repeat(3) { sell("p1") }
        // The server records the batch, but the phone never hears back (the
        // process dies, the connection drops): the sales are still queued.
        server.loseNextAnswer = true
        assertThat(uploader().uploadAll()).isInstanceOf(UploadRun.Retry::class.java)
        assertThat(queue.pendingCount.first()).isEqualTo(3)
        assertThat(server.recorded).hasSize(3)

        // A new process sends them again: DUPLICATE, removed, recorded once.
        assertThat(uploader().uploadAll()).isInstanceOf(UploadRun.Done::class.java)
        assertThat(queue.pendingCount.first()).isEqualTo(0)
        assertThat(server.recorded).hasSize(3)
        // The local count took the sales off once.
        assertThat(dao.stock("p1", branch)!!.storeQty).isEqualTo(7)
    }

    @Test
    fun a_sale_survives_until_sent_and_its_stock_shows_at_once() = runTest {
        stockItem("p1", 4)
        val id = sell("p1", 3)
        assertThat(dao.stock("p1", branch)!!.storeQty).isEqualTo(1)
        // A "restart": the sale is read back from the database, not from memory.
        assertThat(dao.pendingBatch(store, branch, 10).single().sale.id).isEqualTo(id)
        assertThat(dao.pendingBatch(store, branch, 10).single().items.single().quantity).isEqualTo(3)
    }

    // ---------------------------------------------------------------- catalog

    private fun item(id: String, price: Long = 1000, storeQty: Int = 5, codes: List<CatalogCodeDto> = emptyList()) = CatalogItemDto(
        id = id, name = "Item $id", priceAmountMinor = price, salePriceAmountMinor = price, visibility = "PRIVATE", status = "DRAFT",
        codes = codes, storeQty = storeQty,
    )

    private fun catalogRepository() = PosCatalogRepository(dao, server, json, WebLinks("https://azza.example"))

    @Test
    fun full_sync_then_delta_with_tombstones() = runTest {
        server.catalogPages[null] = CatalogPageDto(
            items = listOf(item("p1", codes = listOf(CatalogCodeDto("c1", "4006381333931", "EAN13"))), item("p2")),
            nextCursor = "page2", hasMore = true,
        )
        server.catalogPages["page2"] = CatalogPageDto(items = listOf(item("p3")), nextCursor = "end1", hasMore = false)
        val catalog = catalogRepository()

        assertThat(catalog.open(store, branch)).isEqualTo(Outcome.Success(Unit))
        assertThat(dao.productCount(store)).isEqualTo(3)
        assertThat(dao.syncState(store, branch)!!.cursor).isEqualTo("end1")

        server.catalogPages["end1"] = CatalogPageDto(
            items = listOf(item("p1", price = 2500, codes = emptyList())),
            tombstones = listOf(CatalogTombstoneDto("CODE", "c1", "p1"), CatalogTombstoneDto("PRODUCT", "p2", "p2")),
            nextCursor = "end2", hasMore = false,
        )
        assertThat(catalog.sync(store, branch)).isEqualTo(Outcome.Success(Unit))
        assertThat(server.catalogCursorsAsked).containsExactly(null, "page2", "end1").inOrder()
        assertThat(dao.productCount(store)).isEqualTo(2)
        assertThat(catalog.item("p1", branch)!!.priceMinor).isEqualTo(2500)
        assertThat(catalog.lookup(store, "4006381333931")).isInstanceOf(CodeLookup.UnknownCode::class.java)
    }

    @Test
    fun a_refused_cursor_starts_over_from_scratch() = runTest {
        server.catalogPages[null] = CatalogPageDto(items = listOf(item("p1")), nextCursor = "end1", hasMore = false)
        val catalog = catalogRepository()
        catalog.open(store, branch)
        // Something the server no longer has, then a cursor it no longer
        // accepts (stockItem leaves the state's cursor at "c").
        stockItem("stale", 1)
        server.invalidCursors += "c"

        assertThat(catalog.sync(store, branch)).isEqualTo(Outcome.Success(Unit))
        assertThat(dao.productCount(store)).isEqualTo(1)
        assertThat(catalog.item("stale", branch)).isNull()
    }

    @Test
    fun lookup_tells_a_product_an_unknown_code_a_post_link_and_garbage_apart() = runTest {
        server.catalogPages[null] = CatalogPageDto(
            items = listOf(item("p1", storeQty = 7, codes = listOf(CatalogCodeDto("c1", "0036000291452", "EAN13")))),
            nextCursor = "end", hasMore = false,
        )
        val catalog = catalogRepository()
        catalog.open(store, branch)

        // A UPC-A scan finds the item stored under its EAN-13 form.
        val found = catalog.lookup(store, "036000291452", branch)
        assertThat(found).isInstanceOf(CodeLookup.Product::class.java)
        assertThat((found as CodeLookup.Product).item.stock!!.storeQty).isEqualTo(7)

        assertThat(catalog.lookup("another-store", "036000291452")).isInstanceOf(CodeLookup.UnknownCode::class.java)
        assertThat(catalog.lookup(store, "96385074")).isEqualTo(CodeLookup.UnknownCode("96385074", "EAN8"))
        assertThat(catalog.lookup(store, "4006381333932")).isEqualTo(CodeLookup.Invalid("BAD_CHECK_DIGIT"))
        assertThat(catalog.lookup(store, "https://azza.example/fa/store/abc")).isEqualTo(CodeLookup.PostLink("https://azza.example/fa/store/abc", "abc"))
        assertThat(catalog.lookup(store, "https://azza.example/q/xyz")).isEqualTo(CodeLookup.PostLink("https://azza.example/q/xyz", null))
        // Someone else's web address is just a QR code, not a post.
        assertThat(catalog.lookup(store, "https://example.org/store/abc")).isInstanceOf(CodeLookup.UnknownCode::class.java)
    }
}
