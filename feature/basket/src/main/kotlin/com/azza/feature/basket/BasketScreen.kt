package com.azza.feature.basket

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.azza.core.data.CursorPager
import com.azza.core.designsystem.theme.AzzaTextStyles
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Cart
import com.azza.core.model.CartLine
import com.azza.core.model.PlacedOrderSummary
import com.azza.core.model.ReceivedOrderSummary
import com.azza.core.ui.EmptyState
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadMoreEffect
import com.azza.core.ui.LoadingState
import com.azza.core.ui.discounted
import com.azza.core.ui.errorMessage
import com.azza.core.ui.format
import com.azza.core.ui.formatInstantDate
import com.azza.core.ui.loadingMoreFooter

/** The web's /Basket: one surface, four views (CLAUDE.md "The basket has four panels"). */
enum class BasketTab { BASKET, PLACED, RECEIVED, APPOINTMENTS }

@Composable
fun BasketScreen(
    initialTab: BasketTab,
    onSignIn: () -> Unit,
    onBrowse: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onCheckout: () -> Unit,
    onOpenPlaced: (String) -> Unit,
    onOpenReceived: (String) -> Unit,
    viewModel: BasketViewModel = hiltViewModel(),
) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    when (signedIn) {
        null -> return LoadingState()
        false -> return SignInPrompt(onSignIn)
        true -> Unit
    }
    var tab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }

    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = AzzaTheme.colors.bg, edgePadding = 8.dp) {
            BasketTab.entries.forEach { t ->
                Tab(selected = tab == t.ordinal, onClick = { tab = t.ordinal }, text = { Text(stringResource(t.label())) })
            }
        }
        when (BasketTab.entries[tab]) {
            BasketTab.BASKET -> BasketTabContent(viewModel, onBrowse, onOpenProduct, onCheckout)
            BasketTab.PLACED -> OrdersTab(viewModel.placed, R.string.basket_placed_empty, { it.orderId }, onOpenPlaced) { PlacedRow(it) }
            BasketTab.RECEIVED -> OrdersTab(viewModel.received, R.string.basket_received_empty, { it.sellerOrderId }, onOpenReceived) { ReceivedRow(it) }
            BasketTab.APPOINTMENTS -> AppointmentsTab(onOpenOrder = onOpenPlaced, onOpenProduct = onOpenProduct)
        }
    }
}

private fun BasketTab.label() = when (this) {
    BasketTab.BASKET -> R.string.basket_tab_basket
    BasketTab.PLACED -> R.string.basket_tab_placed
    BasketTab.RECEIVED -> R.string.basket_tab_received
    BasketTab.APPOINTMENTS -> R.string.basket_tab_appointments
}

@Composable
private fun BasketTabContent(
    viewModel: BasketViewModel,
    onBrowse: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onCheckout: () -> Unit,
) {
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val busyLine by viewModel.busyLine.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshCart() }

    val current: Cart = cart ?: return LoadingState()
    if (current.isEmpty) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.basket_empty_heading), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.basket_empty_body), color = AzzaTheme.colors.textSecondary)
            Button(onClick = onBrowse) { Text(stringResource(R.string.basket_browse)) }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            items(current.lines, key = { it.productId }) { line ->
                CartLineRow(
                    line = line,
                    busy = busyLine == line.productId,
                    onOpen = { onOpenProduct(line.productId) },
                    onQuantity = { viewModel.setQuantity(line.productId, it) },
                    onRemove = { viewModel.remove(line.productId) },
                )
                HorizontalDivider(color = AzzaTheme.colors.navDivider)
            }
        }
        Column(
            Modifier.fillMaxWidth().background(AzzaTheme.colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { Text(errorMessage(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(pluralStringResource(R.plurals.basket_item_count, current.itemCount, current.itemCount), color = AzzaTheme.colors.textSecondary)
                current.subtotal()?.let { Text(it.format(), style = AzzaTextStyles.productPrice) }
            }
            Button(
                onClick = onCheckout,
                enabled = current.lines.any { it.available },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.basket_checkout_now)) }
        }
    }
}

@Composable
private fun CartLineRow(line: CartLine, busy: Boolean, onOpen: () -> Unit, onQuantity: (Int) -> Unit, onRemove: () -> Unit) {
    val colors = AzzaTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface)) {
            line.imageUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.title, style = AzzaTextStyles.productTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!line.available) {
                Text(stringResource(R.string.basket_line_unavailable), color = colors.error, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(line.unitPrice.discounted(line.discountPercentage).format(), style = MaterialTheme.typography.bodyMedium)
                    if ((line.discountPercentage ?: 0.0) > 0) {
                        Text(line.unitPrice.format(), color = colors.textMuted, style = AzzaTextStyles.productMeta, textDecoration = TextDecoration.LineThrough)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onQuantity(line.quantity - 1) }, enabled = !busy && line.quantity > 1) {
                    Icon(Icons.Outlined.Remove, stringResource(R.string.basket_decrease))
                }
                Text(line.quantity.toString(), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onQuantity(line.quantity + 1) }, enabled = !busy && line.available && line.quantity < 99) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.basket_increase))
                }
            }
        }
        IconButton(onClick = onRemove, enabled = !busy) {
            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.basket_remove), tint = colors.textMuted)
        }
    }
}

@Composable
private fun <T> OrdersTab(
    pager: CursorPager<T>,
    emptyText: Int,
    key: (T) -> String,
    onOpen: (String) -> Unit,
    row: @Composable (T) -> Unit,
) {
    val state by pager.state.collectAsStateWithLifecycle()
    // On first show, and again on returning (an order just paid or shipped elsewhere).
    LifecycleResumeEffect(pager) {
        pager.refresh(showSpinner = true)
        onPauseOrDispose { }
    }
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, state.items.size, pager::loadMore)
    when {
        state.loadingFirst -> LoadingState()
        state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = { pager.refresh() })
        state.items.isEmpty() -> EmptyState(stringResource(emptyText))
        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(state.items, key = key) { item ->
                Box(Modifier.fillMaxWidth().clickable { onOpen(key(item)) }.padding(16.dp)) { row(item) }
                HorizontalDivider(color = AzzaTheme.colors.navDivider)
            }
            loadingMoreFooter(state.loadingMore)
        }
    }
}

@Composable
private fun PlacedRow(order: PlacedOrderSummary) = OrderRow(
    reference = order.reference,
    status = { StatusChip(order.status) },
    title = andMore(order.firstItemTitle, order.itemCount),
    date = formatInstantDate(order.placedAt),
    amount = order.total.format(),
)

@Composable
private fun ReceivedRow(order: ReceivedOrderSummary) = OrderRow(
    reference = order.reference,
    status = { StatusChip(order.status) },
    title = andMore(order.firstItemTitle, order.itemCount),
    date = formatInstantDate(order.placedAt) + " · " + contextLabel(order.context),
    amount = order.sellerAmount.format(),
)

@Composable
private fun andMore(first: String, count: Int): String =
    if (count > 1) stringResource(R.string.basket_and_more, first, count - 1) else first

@Composable
private fun OrderRow(reference: String, status: @Composable () -> Unit, title: String, date: String, amount: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(reference, style = MaterialTheme.typography.titleSmall)
            status()
        }
        Text(title, color = AzzaTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(date, color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
            Text(amount, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun SignInPrompt(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.basket_sign_in_prompt), color = AzzaTheme.colors.textSecondary)
        Button(onClick = onSignIn) { Text(stringResource(R.string.basket_sign_in)) }
    }
}

@Composable
internal fun TextLink(text: String, onClick: () -> Unit) = TextButton(onClick = onClick) { Text(text) }
