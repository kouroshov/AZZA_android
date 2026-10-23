package com.azza.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.ProductDetail
import com.azza.core.ui.formatInstantDate
import java.time.Duration
import java.time.Instant

/** The rules apps/api applies (listing-checks.ts); the buttons only mirror them. */
private const val RENEW_WINDOW_DAYS = 7L
private const val BUMP_INTERVAL_DAYS = 7L

/**
 * ✅ (2026-10-23, the audit: "a listing never ends and can never be moved
 * up"). The owner's view of a live, waiting or expired listing, as on the
 * web's OwnerListingPanel: waiting for review, days left, «تمدید» and
 * «نردبان».
 */
@Composable
internal fun OwnerLifecycle(product: ProductDetail, busy: Boolean, onRenew: () -> Unit, onBump: () -> Unit) {
    val colors = AzzaTheme.colors
    val now = remember(product.id, product.expiresAt, product.bumpedAt) { Instant.now() }
    val box = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))

    if (product.status == "PENDING_REVIEW" && product.hold == null) {
        Column(box.background(colors.warning.copy(alpha = 0.12f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.owner_pending_title), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(stringResource(R.string.owner_pending_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        return
    }
    if (product.hold != null || (product.status != "ACTIVE" && product.status != "EXPIRED")) return

    val expired = product.status == "EXPIRED"
    val end = product.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val daysLeft = end?.let { Duration.between(now, it).toHours().let { h -> ((h + 23) / 24).coerceAtLeast(0) } }
    val canRenew = expired || (daysLeft != null && daysLeft <= RENEW_WINDOW_DAYS)
    val nextBump = product.bumpedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }?.plus(Duration.ofDays(BUMP_INTERVAL_DAYS))
    val canBump = !expired && (nextBump == null || !nextBump.isAfter(now))

    Column(box.background(colors.surfaceRaised).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when {
                expired -> stringResource(R.string.owner_expired)
                daysLeft == null -> stringResource(R.string.owner_live)
                daysLeft == 0L -> stringResource(R.string.owner_ends_today)
                else -> pluralStringResource(R.plurals.owner_days_left, daysLeft.toInt(), daysLeft.toInt())
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canRenew) {
                Button(onClick = onRenew, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.owner_renew)) }
            }
            if (!expired) {
                OutlinedButton(onClick = onBump, enabled = !busy && canBump, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.owner_bump))
                }
            }
        }
        if (!expired) {
            Text(
                if (canBump || nextBump == null) stringResource(R.string.owner_bump_hint)
                else stringResource(R.string.owner_next_bump, formatInstantDate(nextBump.toString())),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
