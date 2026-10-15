package com.azza.feature.notifications

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
    onOpenProduct: (String) -> Unit,
    onOpenPlacedOrder: (String) -> Unit,
    onOpenReceivedOrder: (String) -> Unit,
) {
    composable<NotificationsRoute> { NotificationsScreen(onOpenProduct, onOpenPlacedOrder, onOpenReceivedOrder) }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val systemNotifications: SystemNotifications,
) : ViewModel() {
    val pager = CursorPager<AppNotification>(viewModelScope, { it.id }) { repository.page(it) }

    init {
        pager.refresh()
        // What the phone showed in its notification shade is now on screen.
        systemNotifications.clearAll()
        // Opening the list is reading it: the badge clears, rows keep their "unread" mark until the next visit.
        viewModelScope.launch { repository.markAllRead() }
    }
}

@Composable
fun NotificationsScreen(
    onOpenProduct: (String) -> Unit,
    onOpenPlacedOrder: (String) -> Unit,
    onOpenReceivedOrder: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.pager.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, state.items.size, viewModel.pager::loadMore)

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
                    items(state.items, key = { it.id }) { n ->
                        // The seller's copy of an order event opens the received order; the buyer's, the placed one.
                        val open: (() -> Unit)? = when {
                            n.sellerOrderId != null -> { { onOpenReceivedOrder(n.sellerOrderId!!) } }
                            n.orderId != null -> { { onOpenPlacedOrder(n.orderId!!) } }
                            n.productId != null -> { { onOpenProduct(n.productId!!) } }
                            else -> null
                        }
                        NotificationRow(n, open)
                        HorizontalDivider(color = AzzaTheme.colors.navDivider)
                    }
                    loadingMoreFooter(state.loadingMore)
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
