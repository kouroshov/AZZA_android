package com.azza.feature.register

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.PaymentMethod
import com.azza.core.model.PosItem
import com.azza.core.scanner.ScanSource
import com.azza.core.scanner.ScannedCode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The register on screen with a fake scanner in place of the camera:
 * three scans (one of them an item already in the cart), Pay, Cash —
 * the cart empties, the sale waits in the queue, and scanning goes on.
 */
@RunWith(AndroidJUnit4::class)
class RegisterScreenTest {
    @get:Rule val compose = createComposeRule()

    private val scans = MutableSharedFlow<ScannedCode>(extraBufferCapacity = 8)
    private val inventory = FakeInventoryRepository()
    private val register = FakeRegisterRepository()
    private val sales = FakeSaleRecorder()

    private fun item(id: String, name: String, price: Long) = PosItem(
        id = id, storeId = "s1", name = name, priceMinor = price, salePriceMinor = price, currency = "IRR",
        visibility = "PRIVATE", status = "DRAFT", imageUrl = null, codes = emptyList(), stock = null,
    )

    private fun hasNode(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun three_scans_then_pay_cash_records_one_sale_and_empties_the_cart() {
        inventory.known["4006381333931"] = item("tea", "Tea", 10_000)
        inventory.known["96385074"] = item("milk", "Milk", 4_500)
        val vm = RegisterViewModel(SavedStateHandle(), inventory, register, sales)
        compose.setContent {
            AzzaTheme { RegisterScreen(RegisterNavigation(back = {}, open = {}), viewModel = vm, scanSource = ScanSource { scans }) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("reg_pay").assertIsNotEnabled()

        // The camera is live at once: three scans, the third the same item as the first.
        scans.tryEmit(ScannedCode("4006381333931", "EAN13"))
        compose.waitUntil(5_000) { hasNode("reg_line_tea") }
        scans.tryEmit(ScannedCode("96385074", "EAN8"))
        compose.waitUntil(5_000) { hasNode("reg_line_milk") }
        scans.tryEmit(ScannedCode("4006381333931", "EAN13"))
        compose.waitUntil(5_000) { vm.state.value.cart.firstOrNull { it.productId == "tea" }?.quantity == 2 }
        compose.onNodeWithTag("reg_qty_tea").assertTextEquals("2")
        compose.onNodeWithTag("reg_qty_milk").assertTextEquals("1")

        // Two taps to a recorded sale: Pay, then Cash.
        compose.onNodeWithTag("reg_pay").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { hasNode("reg_cash") }
        compose.onNodeWithTag("reg_cash").performClick()

        compose.waitUntil(5_000) { vm.state.value.cart.isEmpty() }
        val recorded = runBlocking { sales.pending.first() }
        assertThat(recorded).hasSize(1)
        assertThat(recorded.single().paymentMethod).isEqualTo(PaymentMethod.CASH)
        assertThat(recorded.single().totalMinor).isEqualTo(24_500)
        // The badge sits inside the queue button, which merges its children: read the unmerged tree.
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("reg_pending_count"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reg_pending_count", useUnmergedTree = true).assertTextEquals("1")

        // Ready for the next customer without a tap.
        scans.tryEmit(ScannedCode("96385074", "EAN8"))
        compose.waitUntil(5_000) { vm.state.value.cart.isNotEmpty() }
    }
}
