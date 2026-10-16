package com.azza.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azza.core.data.OwnerPostsState
import com.azza.core.designsystem.theme.AzzaTheme

/** The "⋯" menu of one of the owner's posts (the web's OwnerPosts menu), opened by a long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostActionsSheet(
    title: String,
    showBarcode: Boolean,
    onEdit: () -> Unit,
    onBarcode: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            text = { Text(stringResource(R.string.owner_delete_confirm)) },
            confirmButton = { TextButton(onClick = { confirming = false; onDelete(); onDismiss() }) { Text(stringResource(R.string.owner_delete), color = AzzaTheme.colors.error) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.owner_keep)) } },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SheetRow(Icons.Outlined.Edit, stringResource(R.string.owner_edit)) { onDismiss(); onEdit() }
            if (showBarcode) SheetRow(Icons.Outlined.QrCode2, stringResource(R.string.owner_barcode)) { onDismiss(); onBarcode() }
            SheetRow(Icons.Outlined.CheckCircle, stringResource(R.string.owner_select)) { onDismiss(); onSelect() }
            SheetRow(Icons.Outlined.Delete, stringResource(R.string.owner_delete), danger = true) { confirming = true }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) AzzaTheme.colors.error else AzzaTheme.colors.textPrimary
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color)
        Text(label, color = color)
    }
}

/** The bar at the bottom while selecting: how many, one discount for all, delete all, stop. */
@Composable
fun OwnerPostsBar(state: OwnerPostsState, onDiscount: (Int) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    if (!state.selecting) return
    var discount by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    val value = latinDigits(discount.trim()).toIntOrNull()
    val invalid = discount.isNotBlank() && (value == null || value !in 0..100)
    val count = state.selected.size
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            text = { Text(pluralStringResource(R.plurals.owner_delete_selected_confirm, count, count)) },
            confirmButton = { TextButton(onClick = { confirming = false; onDelete() }) { Text(stringResource(R.string.owner_delete), color = AzzaTheme.colors.error) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.owner_keep)) } },
        )
    }
    Column(
        Modifier.fillMaxWidth().background(AzzaTheme.colors.surface).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.owner_selected_count, count, count), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, stringResource(R.string.owner_stop_selecting)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = discount,
                onValueChange = { discount = it.take(3) },
                label = { Text(stringResource(R.string.owner_discount_label)) },
                suffix = { Text("%") },
                isError = invalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(130.dp),
            )
            TextButton(onClick = { value?.let(onDiscount) }, enabled = count > 0 && value != null && !invalid && state.busy == null) {
                Text(stringResource(if (state.busy == "discount") R.string.owner_working else R.string.owner_apply_discount))
            }
            TextButton(onClick = { confirming = true }, enabled = count > 0 && state.busy == null) {
                Text(stringResource(if (state.busy == "delete") R.string.owner_working else R.string.owner_delete_selected), color = AzzaTheme.colors.error)
            }
        }
        if (invalid) Text(stringResource(R.string.owner_discount_invalid), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall)
        state.ok?.let { Text(stringResource(R.string.owner_bulk_result, it, state.failed), style = MaterialTheme.typography.bodySmall, color = if (state.failed > 0) AzzaTheme.colors.error else AzzaTheme.colors.textSecondary) }
    }
}
