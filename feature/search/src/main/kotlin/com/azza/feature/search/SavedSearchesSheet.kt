package com.azza.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.formatInstantDate

/**
 * ✅ (2026-10-23) The web's /saved-searches, as a sheet on Explore: open one
 * (its query and filters), switch its alerts on or off, remove it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedSearchesSheet(state: SearchViewModel.SavedUi, viewModel: SearchViewModel) {
    val colors = AzzaTheme.colors
    ModalBottomSheet(onDismissRequest = viewModel::closeSaved, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.saved_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.saved_intro), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            when {
                state.loading -> Text(stringResource(R.string.saved_loading), color = colors.textMuted)
                state.failed -> Text(stringResource(R.string.saved_failed), color = colors.error)
                state.items.isEmpty() -> Text(stringResource(R.string.saved_empty), color = colors.textMuted, modifier = Modifier.padding(vertical = 16.dp))
                else -> LazyColumn {
                    items(state.items, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.applySaved(item) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(R.string.saved_on, formatInstantDate(item.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Switch(checked = item.notify, onCheckedChange = { on -> viewModel.setSavedNotify(item.id, on) })
                                Text(stringResource(R.string.saved_alerts), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            }
                            IconButton(onClick = { viewModel.removeSaved(item.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.saved_remove, item.label))
                            }
                        }
                        HorizontalDivider(color = colors.border)
                    }
                }
            }
        }
    }
}
