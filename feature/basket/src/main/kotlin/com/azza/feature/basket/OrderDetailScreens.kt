package com.azza.feature.basket

import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import com.azza.core.model.ShippingDetails
import com.azza.core.ui.htmlEscape
import com.azza.core.ui.printHtml
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azza.core.common.AppError
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.OrderStatus
import com.azza.core.model.PlacedOrderDetail
import com.azza.core.model.ReceivedOrderDetail
import com.azza.core.model.ShippingVisibility
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadingState
import com.azza.core.ui.format
import com.azza.core.ui.formatInstantDate

@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.order_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }
}

@Composable
private fun orderLoadError(error: AppError, retry: () -> Unit) {
    // 404 ORDER_NOT_FOUND covers "not yours" too — apps/api never says which.
    if ((error as? AppError.Api)?.status == 404) {
        com.azza.core.ui.EmptyState(stringResource(R.string.order_not_found))
    } else {
        ErrorState(error, onRetry = retry)
    }
}

@Composable
fun PlacedOrderScreen(onBack: () -> Unit, viewModel: PlacedOrderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val paying by viewModel.paying.collectAsStateWithLifecycle()
    val payError by viewModel.payError.collectAsStateWithLifecycle()
    val title = (state as? DetailState.Ready)?.value?.reference?.let { stringResource(R.string.order_heading, it) } ?: ""
    DetailScaffold(title, onBack) {
        when (val s = state) {
            DetailState.Loading -> LoadingState()
            is DetailState.Failed -> orderLoadError(s.error) { viewModel.load() }
            is DetailState.Ready -> PlacedBody(s.value, paying, payError, viewModel::payNow)
        }
    }
}

@Composable
private fun PlacedBody(order: PlacedOrderDetail, paying: Boolean, payError: AppError?, onPay: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusChip(order.status)
        Text(stringResource(R.string.order_placed_on, formatInstantDate(order.placedAt)), color = AzzaTheme.colors.textMuted)

        if (order.status == OrderStatus.PENDING) {
            Section(stringResource(R.string.order_payment_heading)) {
                Text(stringResource(R.string.order_test_payment_notice), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onPay, enabled = !paying, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(if (paying) R.string.order_test_paying else R.string.order_test_pay))
                }
                payError?.let { Text(payErrorText(it), color = AzzaTheme.colors.error) }
            }
        }

        order.groups.forEach { group ->
            Section(stringResource(R.string.order_sold_by) + ": " + group.seller.name) {
                StatusChip(group.status)
                group.lines.forEach { LineRow(it) }
            }
        }

        Section(stringResource(R.string.order_total)) {
            AmountRow(stringResource(R.string.order_subtotal), order.subtotal.format())
            if (order.discount.amountMinor > 0) AmountRow(stringResource(R.string.order_discount), "−" + order.discount.format())
            AmountRow(
                stringResource(R.string.order_shipping),
                if (order.shippingCost.amountMinor == 0L) stringResource(R.string.order_free) else order.shippingCost.format(),
            )
            AmountRow(stringResource(R.string.order_total), order.total.format(), strong = true)
        }

        Section(stringResource(R.string.order_your_shipping_address)) {
            order.shipping?.let { ShippingBlock(it) } ?: Text(stringResource(R.string.order_no_shipping_address), color = AzzaTheme.colors.textMuted)
        }
        if (order.status != OrderStatus.PENDING) {
            Text(stringResource(R.string.order_payment_unverified), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ReceivedOrderScreen(onBack: () -> Unit, viewModel: ReceivedOrderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shipping by viewModel.shipping.collectAsStateWithLifecycle()
    val shipError by viewModel.shipError.collectAsStateWithLifecycle()
    val title = (state as? DetailState.Ready)?.value?.reference?.let { stringResource(R.string.order_heading, it) } ?: ""
    DetailScaffold(title, onBack) {
        when (val s = state) {
            DetailState.Loading -> LoadingState()
            is DetailState.Failed -> orderLoadError(s.error) { viewModel.load() }
            is DetailState.Ready -> ReceivedBody(s.value, shipping, shipError, viewModel::markShipped)
        }
    }
}

@Composable
private fun ReceivedBody(order: ReceivedOrderDetail, shipping: Boolean, shipError: AppError?, onShip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusChip(order.status)
        Text(
            stringResource(R.string.order_placed_on, formatInstantDate(order.placedAt)) + " · " + contextLabel(order.context),
            color = AzzaTheme.colors.textMuted,
        )
        Section(stringResource(R.string.order_items)) { order.lines.forEach { LineRow(it) } }
        Section(stringResource(R.string.order_your_earnings)) {
            AmountRow(stringResource(R.string.order_subtotal), order.subtotal.format())
            AmountRow(stringResource(R.string.order_platform_fee), "−" + order.platformFee.format())
            AmountRow(stringResource(R.string.order_your_earnings), order.sellerAmount.format(), strong = true)
        }
        Section(stringResource(R.string.order_ship_to)) {
            when (order.shippingVisibility) {
                ShippingVisibility.VISIBLE -> order.shipping?.let {
                    ShippingBlock(it)
                    val context = LocalContext.current
                    val label = labelStrings()
                    OutlinedButton(onClick = { printHtml(context, "AZZA ${order.reference}", shippingLabelHtml(order, it, label)) }) {
                        Icon(Icons.Outlined.Print, null)
                        Text(stringResource(R.string.order_print_label), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                ShippingVisibility.AWAITING_PAYMENT -> Text(stringResource(R.string.order_shipping_awaiting_payment), color = AzzaTheme.colors.textMuted)
                ShippingVisibility.NOT_APPLICABLE -> Text(stringResource(R.string.order_shipping_not_applicable), color = AzzaTheme.colors.textMuted)
                ShippingVisibility.NOT_RECORDED -> Text(stringResource(R.string.order_no_shipping_address), color = AzzaTheme.colors.textMuted)
            }
        }
        if (order.canMarkShipped) {
            Button(onClick = onShip, enabled = !shipping, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(if (shipping) R.string.order_marking_shipped else R.string.order_mark_shipped))
            }
        }
        shipError?.let {
            val conflict = (it as? AppError.Api)?.status == 409
            Text(stringResource(if (conflict) R.string.order_mark_shipped_conflict else R.string.order_mark_shipped_error), color = AzzaTheme.colors.error)
        }
    }
}

/** The words on the label, in the app's language. */
internal data class LabelStrings(val order: String, val from: String, val to: String, val items: String, val postal: String, val phone: String, val unpaid: String, val placed: String)

@Composable
private fun labelStrings() = LabelStrings(
    order = stringResource(R.string.order_label_order),
    from = stringResource(R.string.order_label_from),
    to = stringResource(R.string.order_label_to),
    items = stringResource(R.string.order_items),
    postal = stringResource(R.string.order_postal_code),
    phone = stringResource(R.string.order_phone),
    unpaid = stringResource(R.string.order_label_unpaid),
    placed = stringResource(R.string.order_placed_on, "%s"),
)

/** The web's ShippingLabelView as a printable page: order, sender, recipient, items. */
internal fun shippingLabelHtml(order: ReceivedOrderDetail, to: ShippingDetails, s: LabelStrings): String {
    fun e(t: String) = htmlEscape(t)
    val items = order.lines.joinToString("") { l ->
        val opts = if (l.options.isEmpty()) "" else "<div class=muted>${e(l.options.joinToString(" · "))}</div>"
        "<tr><td dir=auto>${e(l.title)}$opts</td><td class=q>×${l.quantity}</td></tr>"
    }
    val place = listOfNotNull(to.city.ifBlank { null }, to.region).joinToString(", ")
    return """<!doctype html><html><head><meta charset=utf-8><style>
body{font-family:sans-serif;margin:12px;color:#000}
.box{border:2px solid #000;border-radius:8px;padding:12px}
.cap{font-size:10px;text-transform:uppercase;color:#555;margin-top:10px}
.big{font-size:18px;font-weight:bold}
.muted{font-size:11px;color:#555}
.warn{border:2px dashed #000;padding:4px 8px;font-weight:bold;display:inline-block;margin-top:6px}
table{width:100%;border-collapse:collapse;font-size:13px}td{padding:3px 0;vertical-align:top}.q{text-align:end;white-space:nowrap}
</style></head><body><div class=box>
<div class=cap>${e(s.order)}</div><div class=big dir=ltr>${e(order.reference)}</div>
<div class=muted>${e(s.placed.replace("%s", formatInstantDate(order.placedAt)))}</div>
${if (order.status != OrderStatus.PAID) "<div class=warn>${e(s.unpaid)}</div>" else ""}
${order.context.storeName?.takeIf { order.context.isStore }?.let { "<div class=cap>${e(s.from)}</div><div dir=auto>${e(it)}</div>" } ?: ""}
<div class=cap>${e(s.to)}</div>
<div class=big dir=auto>${e(to.recipientName)}</div>
<div dir=auto>${e(to.line1)}</div>
${to.line2?.let { "<div dir=auto>${e(it)}</div>" } ?: ""}
${if (place.isNotEmpty()) "<div dir=auto>${e(place)}</div>" else ""}
${to.postalCode?.let { "<div>${e(s.postal)}: <span dir=ltr>${e(it)}</span></div>" } ?: ""}
${if (to.countryCode.isNotBlank()) "<div>${e(to.countryCode)}</div>" else ""}
${to.phone?.let { "<div>${e(s.phone)}: <span dir=ltr>${e(it)}</span></div>" } ?: ""}
<div class=cap>${e(s.items)}</div><table>$items</table>
</div></body></html>"""
}
