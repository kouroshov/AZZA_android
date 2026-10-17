package com.azza.feature.notifications

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import com.azza.core.common.Outcome
import com.azza.core.data.StoresRepository
import com.azza.core.model.SellingContexts
import com.azza.core.model.ContextFilter
import com.azza.core.ui.ContextChips
import com.azza.core.ui.offlineNote
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import com.azza.core.data.CursorPager
import com.azza.core.data.NotificationsRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.AppNotification
import com.azza.core.ui.EmptyState
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadMoreEffect
import com.azza.core.ui.LoadingState
import com.azza.core.ui.formatInstantDate
import com.azza.core.ui.loadingMoreFooter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object NotificationsRoute

fun NavGraphBuilder.notificationsScreen(
    nav: NotificationNav,
) {
    composable<NotificationsRoute> { NotificationsScreen(nav) }
}

/** Where the screens a notification can open live (the app wires them). */
class NotificationNav(
    val openProduct: (String) -> Unit,
    val openPlacedOrder: (String) -> Unit,
    val openReceivedOrder: (String) -> Unit,
    val openUser: (String) -> Unit,
    val openStore: (String) -> Unit,
    val openAppointments: () -> Unit,
    /** A ticket's thread, or (null) the list. */
    val openTicket: (String?) -> Unit,
    /** A page that only exists on the website (the admin panel), opened in the browser. */
    val openWebPage: (String) -> Unit,
) {
    /** The web's notificationHref, as a tap: always a page the recipient may see; null = nothing to open. */
    fun target(n: AppNotification): (() -> Unit)? = when (n.type) {
        "USER_FOLLOWED", "STORE_FOLLOWED" -> n.actorUsername?.let { u -> { openUser(u) } }
        "ORDER_RECEIVED", "ORDER_PAID" -> n.sellerOrderId?.let { id -> { openReceivedOrder(id) } }
        "ORDER_SHIPPED" -> n.orderId?.let { id -> { openPlacedOrder(id) } }
        "PRODUCT_REVIEWED", "LISTING_TAKEN_DOWN", "LISTING_RESTORED" -> n.productId?.let { id -> { openProduct(id) } }
        // The timetable it belongs to — the listing would only offer to book the hour again.
        "APPOINTMENT_BOOKED", "APPOINTMENT_CANCELLED" -> openAppointments
        "STORE_SUSPENDED", "STORE_RESTORED" -> n.storeSlug?.let { slug -> { openStore(slug) } }
        "APPEAL_DECIDED" -> n.productId?.let { id -> { openProduct(id) } } ?: n.storeSlug?.let { slug -> { openStore(slug) } }
        "ORDER_CANCELLED", "ORDER_REFUNDED" -> n.sellerOrderId?.let { id -> { openReceivedOrder(id) } } ?: n.orderId?.let { id -> { openPlacedOrder(id) } }
        "TICKET_REPLIED" -> { { openTicket(n.ticketId) } }
        // The admin panel is the website's.
        "ADMIN_NEW_SIGN_IN" -> { { openWebPage("/admin/security") } }
        "SYSTEM_ALERT" -> { { openWebPage("/admin/system") } }
        "APPROVAL_REQUESTED", "APPROVAL_DECIDED" -> { { openWebPage("/admin/approvals") } }
        else -> n.sellerOrderId?.let { id -> { openReceivedOrder(id) } }
            ?: n.orderId?.let { id -> { openPlacedOrder(id) } }
            ?: n.productId?.let { id -> { openProduct(id) } }
    }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val systemNotifications: SystemNotifications,
    private val stores: StoresRepository,
) : ViewModel() {
    private val _filter = MutableStateFlow(ContextFilter.ALL)
    val filter: StateFlow<ContextFilter> = _filter.asStateFlow()
    private val _contexts = MutableStateFlow(SellingContexts())
    /** The personal page and each store the person runs (chips only when there is a store). */
    val contexts: StateFlow<SellingContexts> = _contexts.asStateFlow()
    val pager = CursorPager<AppNotification>(viewModelScope, { it.id }) { repository.page(it, _filter.value) }

    fun select(filter: ContextFilter) {
        if (filter == _filter.value) return
        _filter.value = filter
        pager.refresh()
    }

    init {
        pager.refresh()
        viewModelScope.launch {
            (stores.mine() as? Outcome.Success)?.let { r -> _contexts.value = SellingContexts(true, r.value.map { it.id to it.name }) }
        }
        // What the phone showed in its notification shade is now on screen.
        systemNotifications.clearAll()
        // Opening the list is reading it: the badge clears, rows keep their "unread" mark until the next visit.
        viewModelScope.launch { repository.markAllRead() }
    }
}

@Composable
fun NotificationsScreen(
    nav: NotificationNav,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.pager.state.collectAsStateWithLifecycle()
    val contexts by viewModel.contexts.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, state.items.size, viewModel.pager::loadMore)

    Column(Modifier.fillMaxSize()) {
    ContextChips(contexts, filter, viewModel::select)
    when {
        state.loadingFirst -> LoadingState()
        state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = { viewModel.pager.refresh() })
        else -> PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.pager.refresh(showSpinner = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.items.isEmpty()) {
                EmptyState(stringResource(R.string.notifications_empty))
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    offlineNote(state.savedAt)
                    items(state.items, key = { it.id }) { n ->
                        // The seller's copy of an order event opens the received order; the buyer's, the placed one.
                        val open: (() -> Unit)? = nav.target(n)
                        NotificationRow(n, open)
                        HorizontalDivider(color = AzzaTheme.colors.navDivider)
                    }
                    loadingMoreFooter(state.loadingMore)
                }
            }
        }
    }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onOpen: (() -> Unit)?) {
    val colors = AzzaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .background(if (n.read) colors.bg else colors.primary.copy(alpha = 0.06f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceRaised), contentAlignment = Alignment.Center) {
            if (n.productImageUrl != null) {
                AsyncImage(n.productImageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            } else {
                Icon(Icons.Outlined.Notifications, null, tint = colors.textMuted)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title(n), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
            Text(body(n), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Text(formatInstantDate(n.createdAt), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        if (!n.read) Box(Modifier.size(8.dp).clip(CircleShape).background(colors.primary))
    }
}

@Composable
private fun title(n: AppNotification): String {
    LocalConfiguration.current // re-read on a language change, as stringResource does
    return notificationTitle(LocalContext.current.resources, n)
}

@Composable
private fun body(n: AppNotification): String {
    LocalConfiguration.current
    return notificationBody(LocalContext.current.resources, n)
}
