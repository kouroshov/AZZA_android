package com.azza.feature.inventory

import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.pos.CodeOwner
import com.azza.core.model.PosCode
import com.azza.core.model.PosItem
import com.azza.core.model.PosStock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelsTest {
    private val repo = FakeInventoryRepository()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun item(
        id: String,
        name: String = "Tea",
        visibility: String = "PRIVATE",
        storeQty: Int = 1,
        onlineQty: Int = 0,
        review: Boolean = false,
        codes: List<String> = emptyList(),
    ) = PosItem(
        id = id, storeId = "s", name = name, priceMinor = 10_000, salePriceMinor = 10_000, currency = "IRR",
        visibility = visibility, status = "DRAFT", imageUrl = null,
        codes = codes.mapIndexed { i, c -> PosCode("c$i-$id", c, "EAN13") },
        stock = PosStock(storeQty, onlineQty, review),
    )

    // ---------------------------------------------------------------- scan to create

    @Test
    fun `an unknown code opens the sheet with the normalized code, and saving makes a private item`() = runTest {
        val vm = ScanCreateViewModel("s", "b1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.onScanned("036000291452", "UPC_A")
        assertThat(vm.state.value.sheet?.code).isEqualTo("0036000291452")

        vm.edit { copy(name = "  Green tea ", price = "۱۲,۵۰۰", storeQty = "4") }
        vm.save()

        val made = repo.created.single()
        assertThat(made.code).isEqualTo("0036000291452")
        assertThat(made.name).isEqualTo("  Green tea ")
        assertThat(made.priceMinor).isEqualTo(1_250_000)
        assertThat(made.storeQty).isEqualTo(4)
        assertThat(made.onlineQty).isNull()
        assertThat(made.generateCode).isFalse()
        // Straight back to scanning, the next product.
        assertThat(vm.state.value.sheet).isNull()
        assertThat(vm.state.value.notice).isEqualTo(ScanNotice.Saved("Green tea"))
    }

    @Test
    fun `no barcode asks the server to make a code`() = runTest {
        val vm = ScanCreateViewModel("s", "b1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.startWithoutCode()
        vm.edit { copy(name = "Bread", price = "5000") }
        vm.save()
        assertThat(repo.created.single().code).isNull()
        assertThat(repo.created.single().generateCode).isTrue()
    }

    @Test
    fun `name and price are required, and nothing is sent without them`() = runTest {
        val vm = ScanCreateViewModel("s", "b1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.onScanned("SHELF-1", null)
        vm.edit { copy(price = "12.345") }
        vm.save()
        val sheet = vm.state.value.sheet!!
        assertThat(sheet.nameMissing).isTrue()
        assertThat(sheet.priceInvalid).isTrue()
        assertThat(repo.created).isEmpty()
    }

    @Test
    fun `a code another item has is shown with that item`() = runTest {
        repo.createResult = Outcome.Failure(
            AppError.Api(409, "CODE_ALREADY_ASSIGNED", "taken", null, details = """{"productId":"p9","name":"Black tea"}"""),
        )
        val vm = ScanCreateViewModel("s", "b1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.onScanned("4006381333931", "EAN13")
        vm.edit { copy(name = "Tea", price = "100") }
        vm.save()
        val sheet = vm.state.value.sheet!!
        assertThat(sheet.conflict).isEqualTo(CodeOwner("p9", "Black tea"))
        assertThat(sheet.error).isNull()
        assertThat(sheet.saving).isFalse()
    }

    @Test
    fun `offline the sheet says connect, and nothing is sent`() = runTest {
        repo.online.value = false
        val vm = ScanCreateViewModel("s", "b1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.onScanned("SHELF-1", null)
        vm.edit { copy(name = "Tea", price = "100") }
        vm.save()
        assertThat(vm.state.value.sheet?.error).isEqualTo(AppError.Network)
        assertThat(repo.created).isEmpty()
    }

    @Test
    fun `a known code opens its item, a post QR and a bad code only explain`() = runTest {
        repo.known["4006381333931"] = item("p1")
        val vm = ScanCreateViewModel("s", "b1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val opened = backgroundScope.launch { assertThat(vm.events.first()).isEqualTo(ScanEvent.OpenItem("p1")) }
        vm.onScanned("4006381333931", "EAN13")
        opened.join()
        assertThat(vm.state.value.sheet).isNull()

        vm.onScanned("https://azza.example/q/x", "QR")
        assertThat(vm.state.value.notice).isEqualTo(ScanNotice.PostLink)
        vm.onScanned("4006381333932", "EAN13")
        assertThat(vm.state.value.notice).isEqualTo(ScanNotice.InvalidCode("BAD_CHECK_DIGIT"))
    }

    @Test
    fun `while the sheet is open, scans are ignored`() = runTest {
        val vm = ScanCreateViewModel("s", "b1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.onScanned("AAA-1", null)
        vm.onScanned("BBB-2", null)
        assertThat(vm.state.value.sheet?.code).isEqualTo("AAA-1")
    }

    // ---------------------------------------------------------------- item detail

    @Test
    fun `making public lists what is missing`() = runTest {
        repo.all.value = listOf(item("p1"))
        repo.nextResult = Outcome.Failure(
            AppError.Api(400, "PRODUCT_NOT_PUBLISHABLE", "x", null, details = """{"missing":["description","categoryId"]}"""),
        )
        val vm = ItemDetailViewModel("s", "b1", "p1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.setPublic(true)
        assertThat(vm.state.value.missing).containsExactly("description", "categoryId").inOrder()
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `offline an edit is refused before it starts`() = runTest {
        repo.all.value = listOf(item("p1"))
        repo.online.value = false
        val vm = ItemDetailViewModel("s", "b1", "p1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.generateCode()
        assertThat(vm.state.value.error).isEqualTo(AppError.Network)
        assertThat(repo.calls).isEmpty()
    }

    @Test
    fun `codes, transfers and edits go to the repository normalized`() = runTest {
        repo.all.value = listOf(item("p1", name = "Tea"))
        val vm = ItemDetailViewModel("s", "b1", "p1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.addCode(" 036000291452 ", "UPC_A")
        vm.openDialog(ItemDialog.Transfer(fromStore = true))
        vm.editTransfer("0")
        vm.transfer()
        assertThat((vm.state.value.dialog as ItemDialog.Transfer).invalid).isTrue()
        vm.editTransfer("3")
        vm.transfer()
        vm.startEdit()
        vm.editPrice("250")
        vm.saveEdit()

        assertThat(repo.calls).containsExactly("attach:0036000291452", "transfer:true:3", "rename:Tea:25000").inOrder()
        assertThat(vm.state.value.dialog).isNull()
        assertThat(vm.state.value.edit).isNull()
    }

    @Test
    fun `a code conflict on an item names its owner`() = runTest {
        repo.all.value = listOf(item("p1"))
        repo.nextResult = Outcome.Failure(AppError.Api(409, "CODE_ALREADY_ASSIGNED", "x", null, details = """{"productId":"p2","name":"Milk"}"""))
        val vm = ItemDetailViewModel("s", "b1", "p1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.addCode("SHELF-9", null)
        assertThat(vm.state.value.conflict).isEqualTo(CodeOwner("p2", "Milk"))
    }

    // ---------------------------------------------------------------- list

    @Test
    fun `the list opens the remembered branch and filters by name, code and state`() = runTest {
        repo.remembered = "b2"
        repo.all.value = listOf(
            item("p1", name = "Green tea", visibility = "PUBLIC", codes = listOf("0036000291452")),
            item("p2", name = "Bread", storeQty = 0, onlineQty = 0),
            item("p3", name = "Milk", review = true, storeQty = -2),
        )
        val vm = InventoryListViewModel("s", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertThat(vm.state.value.branch?.name).isEqualTo("Bazaar")
        assertThat(repo.calls).contains("open:b2")

        vm.setQuery("tea")
        assertThat(vm.state.value.items.map { it.id }).containsExactly("p1")
        // A scanned UPC-A finds the item stored under its EAN-13.
        vm.setQuery("036000291452")
        assertThat(vm.state.value.items.map { it.id }).containsExactly("p1")
        vm.setQuery("")
        vm.setFilter(InventoryFilter.OUT_OF_STOCK)
        assertThat(vm.state.value.items.map { it.id }).containsExactly("p2", "p3")
        vm.setFilter(InventoryFilter.NEEDS_REVIEW)
        assertThat(vm.state.value.items.map { it.id }).containsExactly("p3")
        vm.setFilter(InventoryFilter.NEEDS_REVIEW) // again: off
        assertThat(vm.state.value.items).hasSize(3)

        vm.selectBranch("b1")
        assertThat(repo.remembered).isEqualTo("b1")
    }

    @Test
    fun `prices are read in major units, Persian digits and separators allowed`() {
        assertThat(parsePriceMinor("125000")).isEqualTo(12_500_000)
        assertThat(parsePriceMinor("۱۲۵,۰۰۰")).isEqualTo(12_500_000)
        assertThat(parsePriceMinor("12.5")).isEqualTo(1_250)
        assertThat(parsePriceMinor("12.345")).isNull()
        assertThat(parsePriceMinor("")).isNull()
        assertThat(parsePriceMinor("-5")).isNull()
        assertThat(formatMajor(1_250)).isEqualTo("12.5")
        assertThat(formatMajor(12_500_000)).isEqualTo("125000")
    }
}
