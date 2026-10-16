package com.azza.feature.basket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.OrderLine
import com.azza.core.model.OrderStatus
import com.azza.core.model.SellingContext
import com.azza.core.model.ShippingDetails
import com.azza.core.ui.format

@Composable
internal fun statusLabel(status: OrderStatus): String = stringResource(
    when (status) {
        OrderStatus.PENDING -> R.string.order_status_pending
        OrderStatus.PAID -> R.string.order_status_paid
        OrderStatus.PROCESSING -> R.string.order_status_processing
        OrderStatus.SHIPPED -> R.string.order_status_shipped
        OrderStatus.DELIVERED -> R.string.order_status_delivered
        OrderStatus.COMPLETED -> R.string.order_status_completed
        OrderStatus.PARTIALLY_FULFILLED -> R.string.order_status_partially_fulfilled
        OrderStatus.CANCELLED -> R.string.order_status_cancelled
        OrderStatus.REFUNDED -> R.string.order_status_refunded
        OrderStatus.FAILED -> R.string.order_status_failed
        OrderStatus.UNKNOWN -> R.string.order_status_unknown
    },
)

@Composable
internal fun StatusChip(status: OrderStatus) {
    val colors = AzzaTheme.colors
    val tint: Color = when (status) {
        OrderStatus.PENDING -> colors.warning
        OrderStatus.CANCELLED, OrderStatus.FAILED, OrderStatus.REFUNDED -> colors.error
        OrderStatus.UNKNOWN -> colors.textMuted
        else -> colors.success
    }
    Text(
        statusLabel(status),
        color = tint,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
internal fun contextLabel(context: SellingContext): String =
    if (context.isStore && context.storeName != null) stringResource(R.string.order_context_store, context.storeName!!)
    else stringResource(R.string.order_context_personal)

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AzzaTheme.colors.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = AzzaTheme.colors.textSecondary)
        content()
    }
}

@Composable
internal fun LineRow(line: OrderLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(line.title, style = MaterialTheme.typography.bodyMedium)
            if (line.options.isNotEmpty()) {
                Text(line.options.joinToString(" · "), color = AzzaTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.order_quantity_times_price, line.quantity, line.unitPrice.format()),
                color = AzzaTheme.colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(line.lineTotal.format(), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun AmountRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (strong) AzzaTheme.colors.textPrimary else AzzaTheme.colors.textSecondary)
        Text(value, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
internal fun ShippingBlock(shipping: ShippingDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(shipping.recipientName, fontWeight = FontWeight.SemiBold)
        Text(shipping.line1)
        shipping.line2?.let { Text(it) }
        Text(listOfNotNull(shipping.city.ifBlank { null }, shipping.region, shipping.countryCode.ifBlank { null }).joinToString(", "))
        shipping.postalCode?.let { Text(stringResource(R.string.order_postal_code) + ": " + it, color = AzzaTheme.colors.textSecondary) }
        shipping.phone?.let { Text(stringResource(R.string.order_phone) + ": " + it, color = AzzaTheme.colors.textSecondary) }
    }
}
