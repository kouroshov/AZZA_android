package com.azza.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.azza.core.common.AppError
import com.azza.core.data.Appeal
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.ProductDetail
import com.azza.core.ui.errorMessage
import com.azza.core.ui.formatInstantDate

/** apps/api's CreateAppealDto: 20–1000 characters. */
const val APPEAL_MIN = 20
const val APPEAL_MAX = 1000

/** The appeal's side of the owner's view: what is on file, and whether a form can be sent. */
data class AppealUi(
    val loading: Boolean = false,
    val appeals: List<Appeal> = emptyList(),
    val sending: Boolean = false,
    val error: AppError? = null,
)

/**
 * Above the photos, for the OWNER of a listing that is not live: a draft,
 * or one taken down (the web's held-listing notice and AppealPanel). Only
 * a decision about this listing itself can be appealed — one that went
 * down with the account or the store has nothing of its own to appeal.
 */
@Composable
internal fun OwnerStatus(product: ProductDetail, appeal: AppealUi, onAppeal: (String) -> Unit) {
    val hold = product.hold
    val title = when {
        hold?.cause == "ADMIN_UNPUBLISHED" -> stringResource(R.string.owner_held_moderation)
        hold?.cause == "SELLER_SUSPENDED" -> stringResource(R.string.owner_held_account)
        hold?.cause == "STORE_SUSPENDED" -> stringResource(R.string.owner_held_store)
        hold != null -> stringResource(R.string.owner_held_moderation)
        product.status == "DRAFT" -> stringResource(R.string.owner_draft)
        product.status == "SOLD" -> stringResource(R.string.owner_sold)
        product.status == "ACTIVE" -> return
        else -> stringResource(R.string.owner_not_live)
    }
    Column(
        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp))
            .background(if (hold != null) AzzaTheme.colors.error.copy(alpha = 0.10f) else AzzaTheme.colors.surfaceRaised)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (hold != null) AzzaTheme.colors.error else AzzaTheme.colors.textPrimary)
        Text(
            stringResource(if (product.status == "DRAFT" && hold == null) R.string.owner_draft_note else R.string.owner_held_note),
            style = MaterialTheme.typography.bodySmall,
            color = AzzaTheme.colors.textSecondary,
        )
        hold?.reason?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.owner_held_reason, it), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
        }
        if (hold != null && hold.appealable) AppealSection(hold.at, appeal, onAppeal)
    }
}

@Composable
private fun AppealSection(holdAt: String, ui: AppealUi, onAppeal: (String) -> Unit) {
    // One appeal per decision: the one about THIS hold, if any.
    val current = ui.appeals.firstOrNull { it.holdAt == holdAt }
    when {
        ui.loading -> Unit
        current?.status == "OPEN" -> Text(stringResource(R.string.appeal_waiting, formatInstantDate(current.createdAt)), style = MaterialTheme.typography.bodySmall)
        current?.status == "REJECTED" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.appeal_rejected, formatInstantDate(current.decidedAt ?: holdAt)), style = MaterialTheme.typography.bodySmall)
            current.decisionNote?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.appeal_answer, it), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
            }
        }
        // ACCEPTED lifts the hold, so the listing would not be here.
        current != null -> Unit
        else -> AppealForm(ui, onAppeal)
    }
}

@Composable
private fun AppealForm(ui: AppealUi, onAppeal: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    if (!open) {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.appeal_button)) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.appeal_one_chance), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
        val length = text.trim().length
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(APPEAL_MAX) },
            label = { Text(stringResource(R.string.appeal_message_label)) },
            placeholder = { Text(stringResource(R.string.appeal_message_placeholder)) },
            supportingText = {
                Text(if (length in 1 until APPEAL_MIN) stringResource(R.string.appeal_too_short, APPEAL_MIN) else "$length / $APPEAL_MAX")
            },
            minLines = 4,
            textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            modifier = Modifier.fillMaxWidth(),
        )
        ui.error?.let { Text(appealError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { onAppeal(text) }, enabled = length >= APPEAL_MIN && !ui.sending) {
            Text(stringResource(if (ui.sending) R.string.appeal_sending else R.string.appeal_send))
        }
    }
}

@Composable
private fun appealError(e: AppError): String = when ((e as? AppError.Api)?.code) {
    "APPEAL_DUPLICATE" -> stringResource(R.string.appeal_error_duplicate)
    "APPEAL_NOT_AVAILABLE" -> stringResource(R.string.appeal_error_not_available)
    else -> errorMessage(e)
}
