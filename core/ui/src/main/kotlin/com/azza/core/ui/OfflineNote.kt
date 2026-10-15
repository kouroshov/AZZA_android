package com.azza.core.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azza.core.designsystem.theme.AzzaTheme

/** "Offline — saved 2 hours ago" above a list showing its saved copy (OfflineCache). */
@Composable
fun OfflineNote(savedAt: Long, modifier: Modifier = Modifier) {
    val now = System.currentTimeMillis()
    val ago = if (now - savedAt < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.offline_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(savedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    Row(
        modifier.fillMaxWidth().background(AzzaTheme.colors.surfaceRaised).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = AzzaTheme.colors.textSecondary)
        Text(stringResource(R.string.offline_saved_copy, ago), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
    }
}

fun LazyListScope.offlineNote(savedAt: Long?) {
    if (savedAt != null) item(key = "offline-note") { OfflineNote(savedAt) }
}
