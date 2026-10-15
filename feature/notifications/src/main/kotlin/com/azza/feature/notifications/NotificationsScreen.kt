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
) : ViewModel() {
    val pager = CursorPager<AppNotification>(viewModelScope, { it.id }) { repository.page(it) }

    init {
        pager.refresh()
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
private fun title(n: AppNotification): String = stringResource(
    when (n.type) {
        "USER_FOLLOWED" -> R.string.n_type_user_followed
        "STORE_FOLLOWED" -> R.string.n_type_store_followed
        "ORDER_RECEIVED" -> R.string.n_type_order_received
        "ORDER_PAID" -> R.string.n_type_order_paid
        "ORDER_SHIPPED" -> R.string.n_type_order_shipped
        "PRODUCT_REVIEWED" -> R.string.n_type_product_reviewed
        "APPOINTMENT_BOOKED" -> R.string.n_type_appointment_booked
        "APPOINTMENT_CANCELLED" -> R.string.n_type_appointment_cancelled
        "REPORT_REVIEWED" -> R.string.n_type_report_reviewed
        "LISTING_TAKEN_DOWN" -> R.string.n_type_listing_taken_down
        "LISTING_RESTORED" -> R.string.n_type_listing_restored
        "STORE_SUSPENDED" -> R.string.n_type_store_suspended
        "STORE_RESTORED" -> R.string.n_type_store_restored
        "CONTENT_HIDDEN" -> R.string.n_type_content_hidden
        "APPEAL_DECIDED" -> R.string.n_type_appeal_decided
        "TICKET_REPLIED" -> R.string.n_type_ticket_replied
        "ACCOUNT_SECURITY_NOTICE" -> R.string.n_type_account_security
        "ORDER_CANCELLED" -> R.string.n_type_order_cancelled
        "ORDER_REFUNDED" -> R.string.n_type_order_refunded
        "PAYOUT_SENT" -> R.string.n_type_payout_sent
        else -> R.string.n_type_generic
    },
)

/** The web's sentences (the web's `notifications.text…` messages); `reference` is the subject title for order events. */
@Composable
private fun body(n: AppNotification): String {
    val actor = n.actorName ?: stringResource(R.string.n_someone)
    val subject = n.subjectTitle.orEmpty()
    return when (n.type) {
        "USER_FOLLOWED" -> stringResource(R.string.n_text_user_followed, actor)
        "STORE_FOLLOWED" -> stringResource(R.string.n_text_store_followed, actor, subject)
        "ORDER_RECEIVED" -> stringResource(R.string.n_text_order_received, actor, subject)
        "ORDER_PAID" -> stringResource(R.string.n_text_order_paid, subject)
        "ORDER_SHIPPED" -> stringResource(R.string.n_text_order_shipped, subject)
        "PRODUCT_REVIEWED" -> stringResource(R.string.n_text_product_reviewed, actor, subject)
        "APPOINTMENT_BOOKED" -> stringResource(R.string.n_text_appointment_booked, actor, subject)
        "APPOINTMENT_CANCELLED" -> stringResource(R.string.n_text_appointment_cancelled, actor, subject)
        "REPORT_REVIEWED" ->
            if (subject.isNotEmpty()) stringResource(R.string.n_text_report_reviewed_subject, subject) else stringResource(R.string.n_text_report_reviewed)
        "LISTING_TAKEN_DOWN" -> stringResource(R.string.n_text_listing_taken_down, subject)
        "LISTING_RESTORED" -> stringResource(R.string.n_text_listing_restored, subject)
        "STORE_SUSPENDED" -> stringResource(R.string.n_text_store_suspended, subject)
        "STORE_RESTORED" -> stringResource(R.string.n_text_store_restored, subject)
        "CONTENT_HIDDEN" -> stringResource(R.string.n_text_content_hidden, subject)
        "APPEAL_DECIDED" -> stringResource(R.string.n_text_appeal_decided, subject)
        "TICKET_REPLIED" -> stringResource(R.string.n_text_ticket_replied, subject)
        "ACCOUNT_SECURITY_NOTICE" -> stringResource(
            when (subject) {
                // subjectTitle is a CODE here, not text (CLAUDE.md, phase 3 of the admin plan).
                "SIGNED_OUT" -> R.string.n_text_security_signed_out
                "PASSWORD_RESET" -> R.string.n_text_security_password_reset
                "EMAIL_VERIFIED" -> R.string.n_text_security_email_verified
                else -> R.string.n_text_security_generic
            },
        )
        "ORDER_CANCELLED" ->
            stringResource(if (n.sellerOrderId != null) R.string.n_text_order_cancelled_seller else R.string.n_text_order_cancelled, subject)
        "ORDER_REFUNDED" ->
            stringResource(if (n.sellerOrderId != null) R.string.n_text_order_refunded_seller else R.string.n_text_order_refunded, subject)
        "PAYOUT_SENT" -> stringResource(R.string.n_text_payout_sent)
        else -> subject
    }
}
