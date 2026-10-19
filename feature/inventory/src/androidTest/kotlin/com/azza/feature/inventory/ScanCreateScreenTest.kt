package com.azza.feature.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.scanner.ScanSource
import com.azza.core.scanner.ScannedCode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scan-to-create end to end on the screen, with a fake scanner in place of
 * the camera: a scan opens the sheet with the code filled in, two fields
 * and Save make a private item, and the screen is ready for the next scan.
 */
@RunWith(AndroidJUnit4::class)
class ScanCreateScreenTest {
    @get:Rule val compose = createComposeRule()

    private val scans = MutableSharedFlow<ScannedCode>(extraBufferCapacity = 8)
    private val fakeScanner = ScanSource { scans }
    private val repo = FakeInventoryRepository()
    private val opened = mutableListOf<Any>()

    private fun show(): ScanCreateViewModel {
        val vm = ScanCreateViewModel("store-1", "branch-1", repo)
        compose.setContent {
            AzzaTheme {
                ScanCreateScreen(
                    nav = InventoryNavigation(back = {}, open = { opened += it }, editListing = {}),
                    viewModel = vm,
                    scanSource = fakeScanner,
                )
            }
        }
        return vm
    }

    @Test
    fun scanning_an_unknown_code_creates_a_private_item_and_returns_to_the_scanner() {
        show()
        compose.waitForIdle()
        scans.tryEmit(ScannedCode("036000291452", "UPC_A"))

        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("sheet_code") }
        compose.onNodeWithTag("sheet_code").assertTextContains("0036000291452", substring = true)
        compose.onNodeWithTag("sheet_name").performTextInput("Green tea")
        compose.onNodeWithTag("sheet_price").performTextInput("125000")
        compose.onNodeWithTag("sheet_save").performClick()

        compose.waitUntil(5_000) { repo.created.isNotEmpty() && !compose.onAllNodesWithTagExists("sheet_code") }
        val made = repo.created.single()
        assertThat(made.code).isEqualTo("0036000291452")
        assertThat(made.name).isEqualTo("Green tea")
        assertThat(made.priceMinor).isEqualTo(12_500_000)
        compose.onNodeWithTag("scan_notice").assertIsDisplayed().assertTextContains("Green tea", substring = true)

        // Ready for the next product at once.
        scans.tryEmit(ScannedCode("96385074", "EAN8"))
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("sheet_code") }
        compose.onNodeWithTag("sheet_code").assertTextContains("96385074", substring = true)
    }

    @Test
    fun a_known_code_opens_its_item() {
        repo.known["4006381333931"] = com.azza.core.model.PosItem(
            id = "p1", storeId = "store-1", name = "Tea", priceMinor = 1, salePriceMinor = 1, currency = "IRR",
            visibility = "PRIVATE", status = "DRAFT", imageUrl = null, codes = emptyList(), stock = null,
        )
        show()
        compose.waitForIdle()
        scans.tryEmit(ScannedCode("4006381333931", "EAN13"))
        compose.waitUntil(5_000) { opened.isNotEmpty() }
        assertThat(opened.single()).isEqualTo(InventoryItemRoute("store-1", "branch-1", "p1"))
    }

    @Test
    fun the_same_scan_twice_in_a_row_opens_one_sheet() {
        show()
        compose.waitForIdle()
        scans.tryEmit(ScannedCode("SHELF-1", "CODE128"))
        scans.tryEmit(ScannedCode("SHELF-1", "CODE128"))
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("sheet_code") }
        compose.onNodeWithTag("sheet_code").assertTextContains("SHELF-1", substring = true)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagExists(tag: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}
