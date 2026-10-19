package com.azza.feature.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import com.azza.core.common.AppError
import com.azza.core.data.pos.CodeOwner
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Money
import com.azza.core.model.PosItem
import com.azza.core.scanner.BarcodeImages
import com.azza.core.scanner.CodeScanner
import com.azza.core.scanner.ScanSource
import com.azza.core.ui.errorMessage
import com.azza.core.ui.format
import kotlinx.serialization.Serializable

@Serializable data class InventoryRoute(val storeId: String)
@Serializable data class InventoryScanRoute(val storeId: String, val branchId: String)
@Serializable data class InventoryItemRoute(val storeId: String, val branchId: String, val productId: String)

/** Where the inventory leads outside this module (the app wires them). */
class InventoryNavigation(
    val back: () -> Unit,
    val open: (Any) -> Unit,
    /** The full listing editor (description, category…), for what publishing needs. */
    val editListing: (String) -> Unit,
    /** A post's QR read by the scanner: the post. */
    val openPost: (String) -> Unit = {},
)

fun NavGraphBuilder.inventoryScreens(nav: InventoryNavigation) {
    composable<InventoryRoute> { InventoryListScreen(nav) }
    composable<InventoryScanRoute> { ScanCreateScreen(nav) }
    composable<InventoryItemRoute> { ItemDetailScreen(nav) }
}

// ---------------------------------------------------------------- shared bits

@Composable
private fun Page(title: String, onBack: () -> Unit, subtitle: (@Composable () -> Unit)? = null, floating: @Composable () -> Unit = {}, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.invoke()
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.inv_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
        floatingActionButton = floating,
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
internal fun OfflineBanner() {
    Row(
        Modifier.fillMaxWidth().background(AzzaTheme.colors.surface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = AzzaTheme.colors.textSecondary)
        Text(stringResource(R.string.inv_offline), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
    }
}

@Composable
private fun VisibilityBadge(visibility: String) {
    val public = visibility == "PUBLIC"
    Text(
        stringResource(if (public) R.string.inv_badge_public else R.string.inv_badge_private),
        style = MaterialTheme.typography.labelSmall,
        color = if (public) AzzaTheme.colors.primaryText else AzzaTheme.colors.textSecondary,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AzzaTheme.colors.surface).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun money(minor: Long, currency: String) = Money(minor, currency).format()

/** An error as a sentence; offline is always "connect to edit". */
@Composable
private fun problem(error: AppError): String =
    if (error is AppError.Network) stringResource(R.string.inv_connect_to_edit) else errorMessage(error)

// ---------------------------------------------------------------- list

@Composable
fun InventoryListScreen(nav: InventoryNavigation, viewModel: InventoryListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var branchMenu by remember { mutableStateOf(false) }
    Page(
        title = stringResource(R.string.inv_title),
        onBack = nav.back,
        subtitle = {
            state.branch?.let { branch ->
                Box {
                    Row(
                        Modifier.clickable(enabled = state.branches.size > 1) { branchMenu = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(branch.name, style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
                        if (state.branches.size > 1) Icon(Icons.Outlined.ArrowDropDown, stringResource(R.string.inv_switch_branch), Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                        state.branches.forEach { b ->
                            DropdownMenuItem(text = { Text(b.name) }, onClick = { branchMenu = false; viewModel.selectBranch(b.id) })
                        }
                    }
                }
            }
        },
        floating = {
            state.branch?.let { branch ->
                ExtendedFloatingActionButton(
                    onClick = { nav.open(InventoryScanRoute(viewModel.storeId, branch.id)) },
                    icon = { Icon(Icons.Outlined.QrCodeScanner, null) },
                    text = { Text(stringResource(R.string.inv_scan_to_add)) },
                    containerColor = AzzaTheme.colors.primary,
                    contentColor = AzzaTheme.colors.textOnPrimary,
                )
            }
        },
    ) { modifier ->
        Column(modifier.fillMaxSize()) {
            if (!state.online) OfflineBanner()
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.inv_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InventoryFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = {
                            Text(
                                stringResource(
                                    when (f) {
                                        InventoryFilter.PRIVATE -> R.string.inv_filter_private
                                        InventoryFilter.PUBLIC -> R.string.inv_filter_public
                                        InventoryFilter.NEEDS_REVIEW -> R.string.inv_filter_review
                                        InventoryFilter.OUT_OF_STOCK -> R.string.inv_filter_out
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            state.syncError?.let { Text(problem(it), color = AzzaTheme.colors.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            if (state.items.isEmpty() && !state.loading) {
                Text(
                    stringResource(if (state.query.isBlank() && state.filter == null) R.string.inv_empty else R.string.inv_no_match),
                    color = AzzaTheme.colors.textSecondary,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { item ->
                    ItemRow(item) { state.branch?.let { b -> nav.open(InventoryItemRoute(viewModel.storeId, b.id, item.id)) } }
                    HorizontalDivider(color = AzzaTheme.colors.navDivider)
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun ItemRow(item: PosItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumbnail(item.imageUrl, 52)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                VisibilityBadge(item.visibility)
                if (item.stock?.needsReview == true) Icon(Icons.Outlined.WarningAmber, stringResource(R.string.inv_needs_review), tint = AzzaTheme.colors.error, modifier = Modifier.size(18.dp))
            }
            Text(money(item.salePriceMinor, item.currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.inv_counts, item.stock?.storeQty ?: 0, item.stock?.onlineQty ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = AzzaTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun Thumbnail(url: String?, sizeDp: Int) {
    Box(Modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp)).background(AzzaTheme.colors.surface), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.Image, null, tint = AzzaTheme.colors.textSecondary)
        }
    }
}

// ---------------------------------------------------------------- scan to create

@Composable
fun ScanCreateScreen(nav: InventoryNavigation, viewModel: ScanCreateViewModel = hiltViewModel(), scanSource: ScanSource? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanEvent.OpenItem -> nav.open(InventoryItemRoute(viewModel.storeId, viewModel.branchId, event.productId))
            }
        }
    }
    Page(stringResource(R.string.inv_add_title), nav.back) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.online) OfflineBanner()
            CodeScanner(
                onCode = { viewModel.onScanned(it.text, it.symbology) },
                active = state.sheet == null,
                source = scanSource,
            )
            state.notice?.let { notice ->
                Text(
                    when (notice) {
                        is ScanNotice.Saved -> stringResource(R.string.inv_saved_next, notice.name)
                        ScanNotice.PostLink -> stringResource(R.string.inv_post_link)
                        is ScanNotice.InvalidCode -> stringResource(R.string.inv_invalid_code)
                    },
                    color = if (notice is ScanNotice.Saved) AzzaTheme.colors.primaryText else AzzaTheme.colors.error,
                    modifier = Modifier.testTag("scan_notice"),
                )
            }
            OutlinedButton(onClick = viewModel::startWithoutCode, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.inv_no_barcode))
            }
        }
    }
    state.sheet?.let { sheet ->
        CreateItemSheet(
            sheet = sheet,
            online = state.online,
            onChange = viewModel::edit,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissSheet,
            onOpenOwner = { owner -> viewModel.dismissSheet(); nav.open(InventoryItemRoute(viewModel.storeId, viewModel.branchId, owner.productId)) },
        )
    }
}

@Composable
private fun CreateItemSheet(
    sheet: CreateSheet,
    online: Boolean,
    onChange: (CreateSheet.() -> CreateSheet) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onOpenOwner: (CodeOwner) -> Unit,
) {
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onChange { copy(photoUri = it.toString()) } } }
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = cameraTarget
        if (ok && target != null) onChange { copy(photoUri = target) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.inv_new_item), style = MaterialTheme.typography.titleMedium)
            Text(
                sheet.code?.let { stringResource(R.string.inv_code_is, it) } ?: stringResource(R.string.inv_code_will_be_made),
                style = MaterialTheme.typography.bodySmall,
                color = AzzaTheme.colors.textSecondary,
                modifier = Modifier.testTag("sheet_code"),
            )
            OutlinedTextField(
                value = sheet.name,
                onValueChange = { v -> onChange { copy(name = v.take(200)) } },
                label = { Text(stringResource(R.string.inv_name)) },
                isError = sheet.nameMissing,
                supportingText = { if (sheet.nameMissing) Text(stringResource(R.string.inv_name_required)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("sheet_name"),
            )
            OutlinedTextField(
                value = sheet.price,
                onValueChange = { v -> onChange { copy(price = v.take(20)) } },
                label = { Text(stringResource(R.string.inv_price)) },
                isError = sheet.priceInvalid,
                supportingText = { if (sheet.priceInvalid) Text(stringResource(R.string.inv_price_invalid)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("sheet_price"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sheet.storeQty,
                    onValueChange = { v -> onChange { copy(storeQty = v.take(7)) } },
                    label = { Text(stringResource(R.string.inv_store_qty)) },
                    isError = sheet.qtyInvalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = sheet.onlineQty,
                    onValueChange = { v -> onChange { copy(onlineQty = v.take(7)) } },
                    label = { Text(stringResource(R.string.inv_online_qty)) },
                    isError = sheet.qtyInvalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Thumbnail(sheet.photoUri, 56)
                TextButton(onClick = {
                    val uri = LabelFiles.newPhotoUri(context)
                    cameraTarget = uri.toString()
                    camera.launch(uri)
                }) { Text(stringResource(R.string.inv_take_photo)) }
                TextButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(stringResource(R.string.inv_choose_photo))
                }
            }
            sheet.conflict?.let { owner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.inv_code_taken, owner.name), color = AzzaTheme.colors.error, modifier = Modifier.weight(1f).testTag("sheet_conflict"))
                    TextButton(onClick = { onOpenOwner(owner) }) { Text(stringResource(R.string.inv_open)) }
                }
            }
            sheet.error?.let { Text(problem(it), color = AzzaTheme.colors.error) }
            if (!online) Text(stringResource(R.string.inv_connect_to_edit), color = AzzaTheme.colors.textSecondary)
            Button(onClick = onSave, enabled = !sheet.saving, modifier = Modifier.fillMaxWidth().testTag("sheet_save")) {
                Text(stringResource(if (sheet.saving) R.string.inv_saving else R.string.inv_save_private))
            }
        }
    }
}

// ---------------------------------------------------------------- item detail

@Composable
fun ItemDetailScreen(nav: InventoryNavigation, viewModel: ItemDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.setPhoto(it.toString()) } }
    Page(item?.name ?: stringResource(R.string.inv_item), nav.back) { modifier ->
        if (item == null) {
            Text(
                stringResource(if (state.loaded) R.string.inv_item_gone else R.string.inv_loading),
                color = AzzaTheme.colors.textSecondary,
                modifier = modifier.padding(24.dp),
            )
            return@Page
        }
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.online) OfflineBanner()
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Messages(state, onEditFields = viewModel::startEdit, onEditListing = { nav.editListing(item.id) }, onOpenOwner = { owner ->
                viewModel.dismissMessages()
                nav.open(InventoryItemRoute(viewModel.storeId, viewModel.branchId, owner.productId))
            })

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Thumbnail(item.imageUrl, 88) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VisibilityBadge(item.visibility)
                    Text(money(item.salePriceMinor, item.currency), style = MaterialTheme.typography.titleMedium)
                    if (item.salePriceMinor != item.priceMinor) {
                        Text(stringResource(R.string.inv_list_price, money(item.priceMinor, item.currency)), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
                    }
                }
            }

            val edit = state.edit
            if (edit == null) {
                OutlinedButton(onClick = viewModel::startEdit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.inv_edit_name_price)) }
            } else {
                Section(stringResource(R.string.inv_edit_name_price)) {
                    OutlinedTextField(edit.name, viewModel::editName, label = { Text(stringResource(R.string.inv_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        edit.price,
                        viewModel::editPrice,
                        label = { Text(stringResource(R.string.inv_price)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.editInvalid) Text(stringResource(R.string.inv_name_price_invalid), color = AzzaTheme.colors.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::saveEdit, enabled = !state.busy) { Text(stringResource(R.string.inv_save)) }
                        TextButton(onClick = viewModel::cancelEdit) { Text(stringResource(R.string.inv_cancel)) }
                    }
                }
            }

            Section(stringResource(R.string.inv_stock)) {
                Text(stringResource(R.string.inv_counts, item.stock?.storeQty ?: 0, item.stock?.onlineQty ?: 0), style = MaterialTheme.typography.bodyLarge)
                if (item.stock?.needsReview == true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = AzzaTheme.colors.error, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.inv_needs_review_hint), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.error)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.openDialog(ItemDialog.Transfer(fromStore = true)) }) { Text(stringResource(R.string.inv_store_to_online)) }
                    OutlinedButton(onClick = { viewModel.openDialog(ItemDialog.Transfer(fromStore = false)) }) { Text(stringResource(R.string.inv_online_to_store)) }
                }
            }

            Section(stringResource(R.string.inv_codes)) {
                if (item.codes.isEmpty()) Text(stringResource(R.string.inv_no_codes), color = AzzaTheme.colors.textSecondary)
                item.codes.forEach { code ->
                    Row(Modifier.fillMaxWidth().clickable { viewModel.openDialog(ItemDialog.ShowCode(code.id)) }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(code.code, style = MaterialTheme.typography.bodyLarge)
                            Text(code.symbology, style = MaterialTheme.typography.labelSmall, color = AzzaTheme.colors.textSecondary)
                        }
                        IconButton(onClick = { viewModel.openDialog(ItemDialog.RemoveCode(code.id)) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.inv_remove_code)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.openDialog(ItemDialog.AddCode) }) {
                        Icon(Icons.Outlined.Add, null)
                        Text(stringResource(R.string.inv_add_code))
                    }
                    OutlinedButton(onClick = viewModel::generateCode, enabled = !state.busy) { Text(stringResource(R.string.inv_generate_code)) }
                }
            }

            Section(stringResource(R.string.inv_visibility)) {
                Text(
                    stringResource(if (item.visibility == "PUBLIC") R.string.inv_is_public else R.string.inv_is_private),
                    style = MaterialTheme.typography.bodySmall,
                    color = AzzaTheme.colors.textSecondary,
                )
                if (item.visibility == "PUBLIC") {
                    OutlinedButton(onClick = { viewModel.setPublic(false) }, enabled = !state.busy) { Text(stringResource(R.string.inv_make_private)) }
                } else {
                    Button(onClick = { viewModel.setPublic(true) }, enabled = !state.busy) { Text(stringResource(R.string.inv_make_public)) }
                }
            }
        }
    }

    if (item != null) ItemDialogs(state, item, viewModel)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AzzaTheme.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Messages(state: ItemDetailState, onEditFields: () -> Unit, onEditListing: () -> Unit, onOpenOwner: (CodeOwner) -> Unit) {
    state.error?.let { Text(problem(it), color = AzzaTheme.colors.error) }
    state.conflict?.let { owner ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.inv_code_taken, owner.name), color = AzzaTheme.colors.error, modifier = Modifier.weight(1f))
            TextButton(onClick = { onOpenOwner(owner) }) { Text(stringResource(R.string.inv_open)) }
        }
    }
    state.missing?.let { missing ->
        Section(stringResource(R.string.inv_missing_title)) {
            missing.forEach { field ->
                val (label, inEditor) = when (field) {
                    "title" -> stringResource(R.string.inv_missing_title_field) to false
                    "price" -> stringResource(R.string.inv_missing_price) to false
                    "description" -> stringResource(R.string.inv_missing_description) to true
                    "categoryId" -> stringResource(R.string.inv_missing_category) to true
                    else -> field to true
                }
                AssistChip(onClick = if (inEditor) onEditListing else onEditFields, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun ItemDialogs(state: ItemDetailState, item: PosItem, viewModel: ItemDetailViewModel) {
    val context = LocalContext.current
    when (val dialog = state.dialog) {
        null -> Unit
        ItemDialog.AddCode -> AlertDialog(
            onDismissRequest = { viewModel.openDialog(null) },
            title = { Text(stringResource(R.string.inv_add_code)) },
            text = { CodeScanner(onCode = { viewModel.addCode(it.text, it.symbology) }, active = !state.busy) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { viewModel.openDialog(null) }) { Text(stringResource(R.string.inv_cancel)) } },
        )
        is ItemDialog.Transfer -> AlertDialog(
            onDismissRequest = { viewModel.openDialog(null) },
            title = { Text(stringResource(if (dialog.fromStore) R.string.inv_store_to_online else R.string.inv_online_to_store)) },
            text = {
                OutlinedTextField(
                    dialog.qty,
                    viewModel::editTransfer,
                    label = { Text(stringResource(R.string.inv_units)) },
                    isError = dialog.invalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::transfer, enabled = !state.busy) { Text(stringResource(R.string.inv_move)) } },
            dismissButton = { TextButton(onClick = { viewModel.openDialog(null) }) { Text(stringResource(R.string.inv_cancel)) } },
        )
        is ItemDialog.RemoveCode -> AlertDialog(
            onDismissRequest = { viewModel.openDialog(null) },
            title = { Text(stringResource(R.string.inv_remove_code)) },
            text = { Text(item.codes.firstOrNull { it.id == dialog.codeId }?.code.orEmpty()) },
            confirmButton = { TextButton(onClick = { viewModel.removeCode(dialog.codeId) }, enabled = !state.busy) { Text(stringResource(R.string.inv_remove)) } },
            dismissButton = { TextButton(onClick = { viewModel.openDialog(null) }) { Text(stringResource(R.string.inv_cancel)) } },
        )
        is ItemDialog.ShowCode -> {
            val code = item.codes.firstOrNull { it.id == dialog.codeId }
            val bitmap = remember(code) { code?.let { BarcodeImages.label(it.code, it.symbology, item.name) } }
            var saved by remember { mutableStateOf<Boolean?>(null) }
            AlertDialog(
                onDismissRequest = { viewModel.openDialog(null) },
                title = { Text(code?.code.orEmpty()) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (bitmap != null) {
                            Image(bitmap.asImageBitmap(), code?.code, modifier = Modifier.fillMaxWidth())
                        } else {
                            Text(stringResource(R.string.inv_cannot_draw))
                        }
                        saved?.let { Text(stringResource(if (it) R.string.inv_saved_to_pictures else R.string.inv_not_saved), style = MaterialTheme.typography.bodySmall) }
                        if (bitmap != null && code != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { LabelFiles.share(context, bitmap, code.code, item.name) }) { Text(stringResource(R.string.inv_share)) }
                                if (LabelFiles.canSave()) TextButton(onClick = { saved = LabelFiles.save(context, bitmap, code.code) }) { Text(stringResource(R.string.inv_save_image)) }
                                TextButton(onClick = { LabelFiles.print(context, bitmap, code.code) }) { Text(stringResource(R.string.inv_print)) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { viewModel.openDialog(null) }) { Text(stringResource(R.string.inv_close)) } },
            )
        }
    }
}
