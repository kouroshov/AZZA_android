package com.azza.feature.stores

import com.azza.core.ui.clock
import com.azza.core.ui.dayName
import com.azza.core.ui.weekOrder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azza.core.data.BookingSettings
import com.azza.core.data.DayOff
import com.azza.core.data.TimeWindow
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadingState
import com.azza.core.ui.formatInstantDate

/**
 * The store's timetable — ONE calendar for every timed service it offers
 * (an hour booked for one is gone for all, apps/api's exclusion
 * constraint). The working week is a list of windows per day; a day off
 * blocks a whole day. What differs per service is on that service.
 */
@Composable
fun TimetableScreen(nav: StoreNavigation, viewModel: TimetableViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        if (state.draft == null) viewModel.load()
        onPauseOrDispose { }
    }
    var picking by remember { mutableStateOf<Pair<TimeWindow, Boolean>?>(null) }
    var addingDayOff by remember { mutableStateOf(false) }
    StorePage(stringResource(R.string.stores_timetable), nav.back) {
        val draft = state.draft
        when {
            draft == null && state.timetable is Load.Failed -> ErrorState((state.timetable as Load.Failed).error, onRetry = { viewModel.load() })
            draft == null -> LoadingState()
            else -> Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.stores_timetable_intro, draft.timezone), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
                if (draft.services.isEmpty()) {
                    Text(stringResource(R.string.stores_timetable_no_services), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
                } else {
                    Text(stringResource(R.string.stores_timetable_services), style = MaterialTheme.typography.titleSmall)
                    draft.services.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.stores_service_line, s.title, s.durationMinutes), modifier = Modifier.weight(1f))
                            TextButton(onClick = { nav.open(BookingSettingsRoute(s.productId)) }) { Text(stringResource(R.string.stores_service_settings)) }
                        }
                    }
                }
                HorizontalDivider(color = AzzaTheme.colors.navDivider)
                Text(stringResource(R.string.stores_working_week), style = MaterialTheme.typography.titleSmall)
                weekOrder().forEach { d ->
                    val windows = draft.windows.filter { it.dayOfWeek == d }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(dayName(d), modifier = Modifier.width(96.dp).padding(top = 12.dp))
                        Column(Modifier.weight(1f)) {
                            if (windows.isEmpty()) Text(stringResource(R.string.stores_no_hours), color = AzzaTheme.colors.textMuted, modifier = Modifier.padding(top = 12.dp))
                            windows.forEach { w ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { picking = w to true }) { Text(clock(w.start)) }
                                    Text("–")
                                    TextButton(onClick = { picking = w to false }) { Text(clock(w.end)) }
                                    IconButton(onClick = { viewModel.removeWindow(w) }) { Icon(Icons.Outlined.Close, stringResource(R.string.stores_remove)) }
                                }
                            }
                            TextButton(onClick = { viewModel.addWindow(d) }) { Text(stringResource(R.string.stores_add_hours)) }
                        }
                    }
                }
                HorizontalDivider(color = AzzaTheme.colors.navDivider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.stores_days_off), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addingDayOff = true }) { Text(stringResource(R.string.stores_add_day_off)) }
                }
                val daysOff = draft.daysOff.filter { it.kind == "CLOSED" }
                if (daysOff.isEmpty()) Text(stringResource(R.string.stores_no_days_off), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                daysOff.forEach { off ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(listOfNotNull(formatInstantDate(off.startsAt), off.reason?.takeIf { it.isNotBlank() }).joinToString(" · "), modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeDayOff(off) }) { Icon(Icons.Outlined.Close, stringResource(R.string.stores_remove)) }
                    }
                }
                state.error?.let { Text(storeError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
                if (state.saved) Text(stringResource(R.string.stores_saved), color = AzzaTheme.colors.primary, style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stores_save)) }
            }
        }
    }
    picking?.let { (w, start) ->
        TimeDialog(if (start) w.start else w.end, onDismiss = { picking = null }) { m ->
            // 00:00 as an end means midnight at the end of the day (apps/api allows up to 1440).
            viewModel.setWindow(w, if (start) w.copy(start = m) else w.copy(end = if (m == 0) 1440 else m))
            picking = null
        }
    }
    if (addingDayOff) {
        DateDialog(onDismiss = { addingDayOff = false }) { date ->
            // A whole day in the calendar's own zone: apps/api compares instants, so midnight to midnight there.
            val zone = state.draft?.timezone ?: "Asia/Tehran"
            viewModel.addDayOff(DayOff(startOfDay(date, zone, 0), startOfDay(date, zone, 1), null))
            addingDayOff = false
        }
    }
}

/** yyyy-MM-dd's midnight in [zone] (plus [plusDays]), as an ISO instant. */
internal fun startOfDay(date: String, zone: String, plusDays: Int): String {
    val (y, m, d) = date.split('-').map { it.toInt() }
    val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
        clear()
        set(y, m - 1, d, 0, 0, 0)
        add(java.util.Calendar.DAY_OF_MONTH, plusDays)
    }
    val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    return iso.format(c.time)
}

// ---------------------------------------------------------------- one service

@Composable
fun BookingSettingsScreen(nav: StoreNavigation, viewModel: BookingSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StorePage(stringResource(R.string.stores_service_settings), nav.back) {
        if (state.loading) {
            LoadingState()
            return@StorePage
        }
        val s = state.settings
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.stores_service_intro), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
            NumberField(s.durationMinutes, 5..1440, R.string.stores_duration) { v -> viewModel.edit { copy(durationMinutes = v) } }
            NumberField(s.slotIntervalMinutes, 5..1440, R.string.stores_interval) { v -> viewModel.edit { copy(slotIntervalMinutes = v) } }
            NumberField(s.bufferBeforeMinutes, 0..720, R.string.stores_buffer_before) { v -> viewModel.edit { copy(bufferBeforeMinutes = v) } }
            NumberField(s.bufferAfterMinutes, 0..720, R.string.stores_buffer_after) { v -> viewModel.edit { copy(bufferAfterMinutes = v) } }
            NumberField(s.minNoticeMinutes, 0..43200, R.string.stores_notice) { v -> viewModel.edit { copy(minNoticeMinutes = v) } }
            NumberField(s.maxAdvanceDays, 1..365, R.string.stores_advance) { v -> viewModel.edit { copy(maxAdvanceDays = v) } }
            NumberField(s.depositPercent, 0..100, R.string.stores_deposit) { v -> viewModel.edit { copy(depositPercent = v) } }
            state.error?.let { Text(storeError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
            if (state.saved) Text(stringResource(R.string.stores_saved), color = AzzaTheme.colors.primary, style = MaterialTheme.typography.bodySmall)
            Button(onClick = viewModel::save, enabled = !state.saving && s.valid(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stores_save)) }
        }
    }
}

private fun BookingSettings.valid() = durationMinutes in 5..1440 && slotIntervalMinutes in 5..1440 && bufferBeforeMinutes in 0..720 &&
    bufferAfterMinutes in 0..720 && minNoticeMinutes in 0..43200 && maxAdvanceDays in 1..365 && depositPercent in 0..100

/** A whole-number field within apps/api's limits; out of range shows them. */
@Composable
private fun NumberField(value: Int, range: IntRange, label: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val number = text.toIntOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter(Char::isDigit).take(5)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(stringResource(label)) },
        isError = number == null || number !in range,
        supportingText = { Text("${range.first} – ${range.last}") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
