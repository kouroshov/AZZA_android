package com.azza.feature.register

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.azza.core.common.AppError
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Money
import com.azza.core.model.PaymentMethod
import com.azza.core.model.QueuedSale
import com.azza.core.model.ServerSale
import com.azza.core.scanner.CodeScanner
import com.azza.core.scanner.ScanSource
import com.azza.core.ui.errorMessage
import com.azza.core.ui.format
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/** The register: a tab, and the app shortcut's target. Opens where it was last used. */
@Serializable data object RegisterRoute

@Serializable data class RegisterTodayRoute(val storeId: String, val branchId: String, val title: String)

class RegisterNavigation(val back: () -> Unit, val open: (Any) -> Unit)

fun NavGraphBuilder.registerScreens(nav: RegisterNavigation) {
    composable<RegisterRoute> { RegisterScreen(nav) }
    composable<RegisterTodayRoute> { TodayScreen(nav) }
}

private fun money(minor: Long, currency: String) = Money(minor, currency).format()

/** The server's UTC instant as the phone's own hour and minute. */
private fun localTime(iso: String): String = runCatching {
    val utc = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(utc.parse(iso.take(19))!!)
}.getOrDefault("")

@Composable
private fun problem(error: AppError): String =
    if (error is AppError.Network) stringResource(R.string.reg_offline_needed) else errorMessage(error)

// ---------------------------------------------------------------- register

@Composable
fun RegisterScreen(nav: RegisterNavigation, viewModel: RegisterViewModel = hiltViewModel(), scanSource: ScanSource? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var placeMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }

    // A notice says its piece and goes; the camera never waits for it.
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(3_500)
            viewModel.dismissNotice()
        }
    }

    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.reg_title), style = MaterialTheme.typography.titleMedium)
                        state.place?.let { place ->
                            Box {
                                Row(
                                    Modifier.clickable(enabled = state.places.size > 1) { placeMenu = true }.testTag("reg_place"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(place.label, style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (state.places.size > 1) Icon(Icons.Outlined.ArrowDropDown, stringResource(R.string.reg_switch_place), Modifier.size(18.dp))
                                }
                                DropdownMenu(expanded = placeMenu, onDismissRequest = { placeMenu = false }) {
                                    state.places.forEach { p ->
                                        DropdownMenuItem(text = { Text(p.label) }, onClick = { placeMenu = false; viewModel.selectPlace(p) })
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    val waiting = state.pending.size + state.rejected.size
                    IconButton(onClick = { queueOpen = true }, modifier = Modifier.testTag("reg_queue")) {
                        BadgedBox(badge = {
                            if (waiting > 0) {
                                Badge(containerColor = if (state.rejected.isNotEmpty()) AzzaTheme.colors.error else AzzaTheme.colors.primary) {
                                    Text("$waiting", modifier = Modifier.testTag("reg_pending_count"))
                                }
                            }
                        }) { Icon(Icons.Outlined.CloudUpload, stringResource(R.string.reg_queue)) }
                    }
                    state.place?.let { place ->
                        Box {
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.reg_more)) }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reg_today)) },
                                    onClick = { moreMenu = false; nav.open(RegisterTodayRoute(place.storeId, place.branchId, place.label)) },
                                )
                                if (state.cart.isNotEmpty()) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.reg_clear_cart)) }, onClick = { moreMenu = false; viewModel.clearCart() })
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
    ) { padding ->
        if (state.noStore) {
            Text(stringResource(R.string.reg_no_store), color = AzzaTheme.colors.textSecondary, modifier = Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Top: the camera, live from the start — no "start scanning" button.
            Box(Modifier.padding(horizontal = 12.dp)) {
                CodeScanner(
                    onCode = { viewModel.onScanned(it.text, it.symbology) },
                    active = state.scanning,
                    source = scanSource,
                )
            }
            Box(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                state.notice?.let { NoticeLine(it) }
            }
            HorizontalDivider(color = AzzaTheme.colors.navDivider)
            // Bottom: the cart, the total, Pay.
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("reg_cart")) {
                if (state.cart.isEmpty()) {
                    item { Text(stringResource(R.string.reg_cart_empty), color = AzzaTheme.colors.textSecondary, modifier = Modifier.padding(16.dp)) }
                }
                items(state.cart, key = { it.productId }) { line ->
                    CartRow(line, onMore = { viewModel.increase(line.productId) }, onLess = { viewModel.decrease(line.productId) }, onRemove = { viewModel.remove(line.productId) })
                }
            }
            Row(
                Modifier.fillMaxWidth().background(AzzaTheme.colors.surface).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reg_items, state.itemCount), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
                    Text(money(state.totalMinor, state.currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("reg_total"))
                }
                Button(
                    onClick = viewModel::startPay,
                    enabled = state.cart.isNotEmpty(),
                    modifier = Modifier.height(56.dp).testTag("reg_pay"),
                    colors = ButtonDefaults.buttonColors(containerColor = AzzaTheme.colors.primary, contentColor = AzzaTheme.colors.textOnPrimary),
                ) { Text(stringResource(R.string.reg_pay), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }

    if (state.paying) PaySheet(state.totalMinor, state.currency, onPay = viewModel::pay, onDismiss = viewModel::cancelPay)
    state.unknown?.let { UnknownItemSheet(it, viewModel) }
    if (queueOpen) QueueSheet(state.pending, state.rejected, onDismissRejected = viewModel::dismissRejected, onDismiss = { queueOpen = false })
}

@Composable
private fun NoticeLine(notice: RegisterNotice) {
    val (text, good) = when (notice) {
        is RegisterNotice.Added -> stringResource(R.string.reg_added, notice.name, notice.quantity) to true
        RegisterNotice.PostLink -> stringResource(R.string.reg_post_link) to false
        RegisterNotice.InvalidCode -> stringResource(R.string.reg_invalid_code) to false
        is RegisterNotice.UnknownOffline -> stringResource(R.string.reg_unknown_offline, notice.code) to false
        is RegisterNotice.Sold -> stringResource(R.string.reg_sold, money(notice.totalMinor, notice.currency)) to true
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (good) AzzaTheme.colors.primaryText else AzzaTheme.colors.error,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("reg_notice"),
    )
}

@Composable
private fun CartRow(line: CartLine, onMore: () -> Unit, onLess: () -> Unit, onRemove: () -> Unit) {
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipe.currentValue) { if (swipe.currentValue != SwipeToDismissBoxValue.Settled) onRemove() }
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(AzzaTheme.colors.error.copy(alpha = 0.15f)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Text(stringResource(R.string.reg_remove), color = AzzaTheme.colors.error)
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().background(AzzaTheme.colors.bg).padding(horizontal = 16.dp, vertical = 8.dp).testTag("reg_line_${line.productId}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(money(line.unitPriceMinor, line.currency), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
            }
            IconButton(onClick = onLess) { Icon(Icons.Outlined.Remove, stringResource(R.string.reg_less)) }
            Text("${line.quantity}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("reg_qty_${line.productId}"))
            IconButton(onClick = onMore) { Icon(Icons.Outlined.Add, stringResource(R.string.reg_more_one)) }
            Text(money(line.totalMinor, line.currency), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Pay: Cash or Card — one tap records the sale. The amount received is optional, only to show change. */
@Composable
private fun PaySheet(totalMinor: Long, currency: String, onPay: (PaymentMethod) -> Unit, onDismiss: () -> Unit) {
    var received by rememberSaveable { mutableStateOf("") }
    val receivedMinor = parseAmountMinor(received)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.reg_to_pay, money(totalMinor, currency)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = received,
                onValueChange = { received = it.take(16) },
                label = { Text(stringResource(R.string.reg_received_optional)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (receivedMinor != null) {
                val change = receivedMinor - totalMinor
                Text(
                    if (change >= 0) stringResource(R.string.reg_change, money(change, currency)) else stringResource(R.string.reg_short, money(-change, currency)),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (change >= 0) AzzaTheme.colors.primaryText else AzzaTheme.colors.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onPay(PaymentMethod.CASH) },
                    modifier = Modifier.weight(1f).height(64.dp).testTag("reg_cash"),
                    colors = ButtonDefaults.buttonColors(containerColor = AzzaTheme.colors.primary, contentColor = AzzaTheme.colors.textOnPrimary),
                ) {
                    Icon(Icons.Outlined.Payments, null)
                    Text(" " + stringResource(R.string.reg_cash), style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = { onPay(PaymentMethod.CARD_TERMINAL) },
                    modifier = Modifier.weight(1f).height(64.dp).testTag("reg_card"),
                    colors = ButtonDefaults.buttonColors(containerColor = AzzaTheme.colors.primary, contentColor = AzzaTheme.colors.textOnPrimary),
                ) {
                    Icon(Icons.Outlined.CreditCard, null)
                    Text(" " + stringResource(R.string.reg_card), style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(stringResource(R.string.reg_card_hint), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun UnknownItemSheet(sheet: UnknownSheet, viewModel: RegisterViewModel) {
    ModalBottomSheet(onDismissRequest = viewModel::dismissUnknown, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.reg_new_item), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.reg_new_item_hint, sheet.code), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary, modifier = Modifier.testTag("reg_unknown_code"))
            OutlinedTextField(
                sheet.name,
                { v -> viewModel.editUnknown { copy(name = v.take(200)) } },
                label = { Text(stringResource(R.string.reg_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("reg_unknown_name"),
            )
            OutlinedTextField(
                sheet.price,
                { v -> viewModel.editUnknown { copy(price = v.take(20)) } },
                label = { Text(stringResource(R.string.reg_price)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("reg_unknown_price"),
            )
            if (sheet.invalid) Text(stringResource(R.string.reg_name_price_needed), color = AzzaTheme.colors.error)
            sheet.conflict?.let { Text(stringResource(R.string.reg_code_taken, it.name), color = AzzaTheme.colors.error) }
            sheet.error?.let { Text(problem(it), color = AzzaTheme.colors.error) }
            Button(onClick = viewModel::saveUnknown, enabled = !sheet.saving, modifier = Modifier.fillMaxWidth().testTag("reg_unknown_save")) {
                Text(stringResource(if (sheet.saving) R.string.reg_saving else R.string.reg_save_and_add))
            }
        }
    }
}

@Composable
private fun QueueSheet(pending: List<QueuedSale>, rejected: List<QueuedSale>, onDismissRejected: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reg_queue), style = MaterialTheme.typography.titleMedium)
            if (pending.isEmpty() && rejected.isEmpty()) Text(stringResource(R.string.reg_queue_empty), color = AzzaTheme.colors.textSecondary)
            if (pending.isNotEmpty()) {
                Text(stringResource(R.string.reg_waiting, pending.size), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.reg_waiting_hint), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
                pending.forEach { QueuedRow(it) }
            }
            if (rejected.isNotEmpty()) {
                Text(stringResource(R.string.reg_rejected, rejected.size), style = MaterialTheme.typography.titleSmall, color = AzzaTheme.colors.error)
                rejected.forEach { sale ->
                    QueuedRow(sale)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rejectReason(sale.rejectReason), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDismissRejected(sale.id) }) { Text(stringResource(R.string.reg_dismiss)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuedRow(sale: QueuedSale) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(sale.lines.joinToString { "${it.name} ×${it.quantity}" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (sale.paymentMethod == PaymentMethod.CASH) R.string.reg_cash else R.string.reg_card),
                style = MaterialTheme.typography.bodySmall,
                color = AzzaTheme.colors.textSecondary,
            )
        }
        Text(money(sale.totalMinor, "IRR"), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun rejectReason(code: String?): String = when (code) {
    "PRODUCT_NOT_IN_STORE" -> stringResource(R.string.reg_reason_product)
    "BRANCH_NOT_IN_STORE" -> stringResource(R.string.reg_reason_branch)
    "INVALID_SALE" -> stringResource(R.string.reg_reason_invalid)
    else -> stringResource(R.string.reg_reason_other, code.orEmpty())
}

// ---------------------------------------------------------------- today

@Composable
fun TodayScreen(nav: RegisterNavigation, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reg_today), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reg_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Section(stringResource(R.string.reg_on_server)) {
                val summary = state.summary
                when {
                    summary != null -> {
                        Text(stringResource(R.string.reg_sum_count, summary.count), style = MaterialTheme.typography.bodyLarge)
                        Text(money(summary.totalMinor, summary.currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.reg_sum_cash, money(summary.cashMinor, summary.currency)))
                        Text(stringResource(R.string.reg_sum_card, money(summary.cardMinor, summary.currency)))
                        if (summary.flagged > 0) Text(stringResource(R.string.reg_sum_flagged, summary.flagged), color = AzzaTheme.colors.error)
                        if (summary.voided > 0) Text(stringResource(R.string.reg_sum_voided, summary.voided), color = AzzaTheme.colors.textSecondary)
                    }
                    !state.online || state.error is AppError.Network -> Text(stringResource(R.string.reg_summary_offline), color = AzzaTheme.colors.textSecondary)
                    state.error != null -> Text(problem(state.error!!), color = AzzaTheme.colors.error)
                }
                OutlinedButton(onClick = viewModel::refresh) { Text(stringResource(R.string.reg_refresh)) }
            }
            if (state.waiting.isNotEmpty() || state.rejected.isNotEmpty()) {
                Section(stringResource(R.string.reg_on_phone)) {
                    if (state.waiting.isNotEmpty()) {
                        Text(stringResource(R.string.reg_waiting, state.waiting.size))
                        Text(money(state.waiting.sumOf { it.totalMinor }, "IRR"), fontWeight = FontWeight.SemiBold)
                    }
                    if (state.rejected.isNotEmpty()) Text(stringResource(R.string.reg_rejected, state.rejected.size), color = AzzaTheme.colors.error)
                }
            }
            if (state.sales.isNotEmpty()) {
                Section(stringResource(R.string.reg_sales_today)) {
                    state.sales.forEach { sale -> SaleRow(sale, onVoid = { viewModel.askVoid(sale) }) }
                }
            }
        }
    }
    state.confirmVoid?.let { sale ->
        AlertDialog(
            onDismissRequest = { viewModel.askVoid(null) },
            title = { Text(stringResource(R.string.reg_void_title)) },
            text = { Text(stringResource(R.string.reg_void_body, money(sale.totalMinor, sale.currency))) },
            confirmButton = { TextButton(onClick = viewModel::void, enabled = !state.voiding) { Text(stringResource(R.string.reg_void)) } },
            dismissButton = { TextButton(onClick = { viewModel.askVoid(null) }) { Text(stringResource(R.string.reg_cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AzzaTheme.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun SaleRow(sale: ServerSale, onVoid: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                sale.lines.joinToString { "${it.name} ×${it.quantity}" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (sale.voided) AzzaTheme.colors.textMuted else AzzaTheme.colors.textPrimary,
            )
            Text(
                listOfNotNull(
                    stringResource(if (sale.paymentMethod == PaymentMethod.CASH) R.string.reg_cash else R.string.reg_card),
                    localTime(sale.soldAtIso),
                    if (sale.voided) stringResource(R.string.reg_voided) else null,
                    if (sale.flagged) stringResource(R.string.reg_flagged) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (sale.flagged) AzzaTheme.colors.error else AzzaTheme.colors.textSecondary,
            )
        }
        Text(money(sale.totalMinor, sale.currency))
        if (!sale.voided) TextButton(onClick = onVoid) { Text(stringResource(R.string.reg_void)) }
    }
}
