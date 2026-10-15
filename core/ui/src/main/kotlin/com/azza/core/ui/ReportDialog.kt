package com.azza.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.ReportReason
import com.azza.core.data.ReportTarget
import com.azza.core.designsystem.theme.AzzaTheme
import kotlinx.coroutines.launch

/**
 * The web's report dialog: a reason, optional details, then a thank-you.
 * The person reported is never told who reported them; apps/api takes the
 * reporter from the token, so [submit] carries nothing about who is asking.
 */
@Composable
fun ReportDialog(
    target: ReportTarget,
    onDismiss: () -> Unit,
    submit: suspend (ReportReason, String) -> Outcome<Unit>,
) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<AppError?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = {
            Text(
                stringResource(
                    when (target) {
                        ReportTarget.PRODUCT -> R.string.report_title_product
                        ReportTarget.USER -> R.string.report_title_user
                        ReportTarget.STORE -> R.string.report_title_store
                    },
                ),
            )
        },
        text = {
            if (sent) {
                Text(stringResource(R.string.report_sent))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.report_intro), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
                    Column(Modifier.selectableGroup()) {
                        ReportReason.entries.forEach { r ->
                            Row(
                                Modifier.fillMaxWidth().clickable { reason = r; error = null },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = reason == r, onClick = { reason = r; error = null })
                                Text(reasonLabel(r))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it.take(1000) },
                        label = { Text(stringResource(R.string.report_details)) },
                        minLines = 2,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(reportError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.report_close)) }
            } else {
                TextButton(
                    enabled = reason != null && !sending,
                    onClick = {
                        val chosen = reason ?: return@TextButton
                        sending = true
                        scope.launch {
                            when (val r = submit(chosen, details)) {
                                is Outcome.Success -> sent = true
                                is Outcome.Failure -> error = r.error
                            }
                            sending = false
                        }
                    },
                ) { Text(stringResource(if (sending) R.string.report_sending else R.string.report_send)) }
            }
        },
        dismissButton = { if (!sent) TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(R.string.report_cancel)) } },
    )
}

@Composable
private fun reasonLabel(r: ReportReason): String = stringResource(
    when (r) {
        ReportReason.SPAM -> R.string.report_reason_spam
        ReportReason.FRAUD -> R.string.report_reason_fraud
        ReportReason.COUNTERFEIT -> R.string.report_reason_counterfeit
        ReportReason.PROHIBITED -> R.string.report_reason_prohibited
        ReportReason.INAPPROPRIATE -> R.string.report_reason_inappropriate
        ReportReason.HARASSMENT -> R.string.report_reason_harassment
        ReportReason.OTHER -> R.string.report_reason_other
    },
)

@Composable
private fun reportError(e: AppError): String = when ((e as? AppError.Api)?.code) {
    "REPORT_DUPLICATE" -> stringResource(R.string.report_error_duplicate)
    "REPORT_SELF_NOT_ALLOWED" -> stringResource(R.string.report_error_self)
    else -> errorMessage(e)
}
