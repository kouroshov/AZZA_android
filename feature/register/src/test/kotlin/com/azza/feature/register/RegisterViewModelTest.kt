package com.azza.feature.register

import androidx.lifecycle.SavedStateHandle
import com.azza.core.common.AppError
import com.azza.core.data.pos.PosBranch
import com.azza.core.model.PaymentMethod
import com.azza.core.model.PosItem
import com.azza.core.model.SaleLine
import com.azza.core.model.ServerSale
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class RegisterViewModelTest {
    private val inventory = FakeInventoryRepository()
    private val register = FakeRegisterRepository()
    private val sales = FakeSaleRecorder()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun item(id: String, name: String, price: Long) = PosItem(
        id = id, storeId = "s1", name = name, priceMinor = price, salePriceMinor = price, currency = "IRR",
        visibility = "PRIVATE", status = "DRAFT", imageUrl = null, codes = emptyList(), stock = null,
    )

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = RegisterViewModel(saved, inventory, register, sales)

    init {
        inventory.branchList = listOf(PosBranch("b1", "Main"), PosBranch("b2", "Bazaar"))
        inventory.known["TEA"] = item("p1", "Tea", 10_000)
        inventory.known["MILK"] = item("p2", "Milk", 4_500)
    }

    @Test
    fun `the same item twice is one line with quantity 2`() {
        val lines = emptyList<CartLine>().add("p1", "Tea", 100, "IRR").add("p2", "Milk", 50, "IRR").add("p1", "Tea", 100, "IRR")
        assertThat(lines.map { it.productId to it.quantity }).containsExactly("p1" to 2, "p2" to 1).inOrder()
    }

    @Test
    fun `scans fill the cart, Pay Cash records the sale and empties it`() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertThat(vm.state.value.place?.branchId).isEqualTo("b1")

        vm.onScanned("TEA", "CODE128")
        vm.onScanned("MILK", "CODE128")
        vm.onScanned("TEA", "CODE128")
        assertThat(vm.state.value.cart.map { it.productId to it.quantity }).containsExactly("p1" to 2, "p2" to 1)
        assertThat(vm.state.value.totalMinor).isEqualTo(24_500)

        vm.startPay()
        assertThat(vm.state.value.paying).isTrue()
        assertThat(vm.state.value.scanning).isFalse()
        vm.pay(PaymentMethod.CASH)

        val sale = sales.pending.first().single()
        assertThat(sale.paymentMethod).isEqualTo(PaymentMethod.CASH)
        assertThat(sale.lines).containsExactly(SaleLine("p1", "Tea", 2, 10_000), SaleLine("p2", "Milk", 1, 4_500))
        assertThat(vm.state.value.cart).isEmpty()
        assertThat(vm.state.value.paying).isFalse()
        assertThat(vm.state.value.notice).isEqualTo(RegisterNotice.Sold(24_500, "IRR"))
        assertThat(vm.state.value.scanning).isTrue()
    }

    @Test
    fun `an unknown code online is created with its code and lands in the cart`() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.onScanned("036000291452", "UPC_A")
        assertThat(vm.state.value.unknown?.code).isEqualTo("0036000291452")
        assertThat(vm.state.value.scanning).isFalse()

        vm.editUnknown { copy(name = "Biscuits", price = "75") }
        vm.saveUnknown()

        val made = inventory.created.single()
        assertThat(made.code).isEqualTo("0036000291452")
        assertThat(made.priceMinor).isEqualTo(7_500)
        assertThat(vm.state.value.unknown).isNull()
        assertThat(vm.state.value.cart.single()).isEqualTo(CartLine("new-1", "Biscuits", 7_500, 1))
    }

    @Test
    fun `an unknown code offline only explains, and the sale goes on`() = runTest {
        inventory.online.value = false
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.onScanned("TEA", null)
        vm.onScanned("NEW-1", null)
        assertThat(vm.state.value.unknown).isNull()
        assertThat(vm.state.value.notice).isEqualTo(RegisterNotice.UnknownOffline("NEW-1"))
        assertThat(inventory.created).isEmpty()
        assertThat(vm.state.value.cart.map { it.productId }).containsExactly("p1")

        // Offline the sale is still recorded on the phone.
        vm.startPay()
        vm.pay(PaymentMethod.CARD_TERMINAL)
        assertThat(sales.pending.first().single().paymentMethod).isEqualTo(PaymentMethod.CARD_TERMINAL)
    }

    @Test
    fun `a post link and a bad code only explain`() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.onScanned("https://azza.example/q/abc", "QR")
        assertThat(vm.state.value.notice).isEqualTo(RegisterNotice.PostLink)
        vm.onScanned("4006381333932", "EAN13")
        assertThat(vm.state.value.notice).isEqualTo(RegisterNotice.InvalidCode)
        assertThat(vm.state.value.cart).isEmpty()
        assertThat(vm.state.value.unknown).isNull()
    }

    @Test
    fun `the cart survives the process being killed, and is not a sale until paid`() = runTest {
        val saved = SavedStateHandle()
        val first = vm(saved)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { first.state.collect {} }
        first.onScanned("TEA", null)
        first.onScanned("MILK", null)

        // A new view model over the restored saved state: what Android gives back after process death.
        val restored = vm(SavedStateHandle(mapOf("register_cart" to saved.get<String>("register_cart"))))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.state.collect {} }
        assertThat(restored.state.value.cart.map { it.productId }).containsExactly("p1", "p2")
        assertThat(sales.pending.first()).isEmpty()
    }

    @Test
    fun `steppers and removal`() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.onScanned("TEA", null)
        vm.increase("p1")
        vm.increase("p1")
        vm.decrease("p1")
        assertThat(vm.state.value.cart.single().quantity).isEqualTo(2)
        vm.decrease("p1")
        vm.decrease("p1")
        assertThat(vm.state.value.cart).isEmpty()
        vm.onScanned("MILK", null)
        vm.remove("p2")
        assertThat(vm.state.value.cart).isEmpty()
    }

    @Test
    fun `it opens where it was last used, and another store starts an empty cart`() = runTest {
        register.stores.value = register.stores.value + com.azza.core.model.PosStoreRef("s2", "Kiosk", "k1")
        register.lastPlace.value = "s1" to "b2"
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertThat(vm.state.value.place).isEqualTo(Place("s1", "Corner shop", "b2", "Bazaar"))

        vm.onScanned("TEA", null)
        vm.selectPlace(vm.state.value.places.first { it.branchId == "b1" })
        assertThat(vm.state.value.cart).hasSize(1) // same store: the cart stays
        vm.selectPlace(vm.state.value.places.first { it.storeId == "s2" })
        assertThat(vm.state.value.cart).isEmpty()
        assertThat(register.lastPlace.value?.first).isEqualTo("s2")
    }

    @Test
    fun `no store, no register`() = runTest {
        register.stores.value = emptyList()
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertThat(vm.state.value.noStore).isTrue()
    }

    @Test
    fun `today shows the server's day, and a void refreshes it`() = runTest {
        register.serverSales += ServerSale("x1", PaymentMethod.CASH, 20_000, "IRR", "2026-10-20T08:00:00.000Z", voided = false, flagged = false, lines = emptyList())
        val today = TodayViewModel("s1", "b1", register, sales, MutableStateFlow(true))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { today.state.collect {} }
        assertThat(today.state.value.summary?.totalMinor).isEqualTo(30_000)
        today.askVoid(today.state.value.sales.single())
        today.void()
        assertThat(register.voided).containsExactly("x1")
        assertThat(today.state.value.sales.single().voided).isTrue()
        assertThat(today.state.value.confirmVoid).isNull()
    }

    @Test
    fun `offline the day view says so`() = runTest {
        register.summary = com.azza.core.common.Outcome.Failure(AppError.Network)
        val today = TodayViewModel("s1", "b1", register, sales, MutableStateFlow(false))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { today.state.collect {} }
        assertThat(today.state.value.summary).isNull()
        assertThat(today.state.value.error).isEqualTo(AppError.Network)
    }

    @Test
    fun `amounts are read in major units`() {
        assertThat(parseAmountMinor("۵۰,۰۰۰")).isEqualTo(5_000_000)
        assertThat(parseAmountMinor("12.5")).isEqualTo(1_250)
        assertThat(parseAmountMinor("abc")).isNull()
    }
}
