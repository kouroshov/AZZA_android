package com.azza.feature.basket

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
import androidx.hilt.navigation.compose.hiltViewModel
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
                ShippingVisibility.VISIBLE -> order.shipping?.let { ShippingBlock(it) }
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
