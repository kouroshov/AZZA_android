package com.azza.feature.product

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.AuthRepository
import com.azza.core.data.BookingRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Appointment
import com.azza.core.model.AppointmentStatus
import com.azza.core.model.BookingWeek
import com.azza.core.model.BookingSetup
import com.azza.core.model.Slot
import com.azza.core.ui.errorMessage
import com.azza.core.ui.formatClock
import com.azza.core.ui.formatDayChip
import com.azza.core.ui.formatWhen
import com.azza.core.ui.format
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingUiState(
    val setup: BookingSetup? = null,
    /** BOOKING_NOT_AVAILABLE and similar: the panel says so instead of offering times. */
    val unavailable: Boolean = false,
    val week: BookingWeek? = null,
    val loadingWeek: Boolean = false,
    val dayIndex: Int = 0,
    val slot: Slot? = null,
    val note: String = "",
    val booking: Boolean = false,
    val booked: Appointment? = null,
    val error: AppError? = null,
    val signInRequired: Boolean = false,
)

@HiltViewModel
class BookingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val booking: BookingRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val productId = savedStateHandle.toRoute<ProductRoute>().productId

    private val _state = MutableStateFlow(BookingUiState())
    val state: StateFlow<BookingUiState> = _state.asStateFlow()

    /** The first day the server offered: the earliest the week can go back to. */
    private var firstDay: String? = null

    init {
        viewModelScope.launch {
            when (val r = booking.setup(productId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(setup = r.value) }
                    loadWeek(today(r.value.timezone))
                }
                is Outcome.Failure -> _state.update { it.copy(unavailable = true) }
            }
        }
    }

    fun previousWeek() {
        val from = _state.value.week?.from ?: return
        val target = shift(from, -7)
        loadWeek(if (firstDay != null && target < firstDay!!) firstDay!! else target)
    }

    fun nextWeek() {
        val from = _state.value.week?.from ?: return
        loadWeek(shift(from, 7))
    }

    fun pickDay(index: Int) = _state.update { it.copy(dayIndex = index, slot = null) }

    fun pickSlot(slot: Slot) = _state.update { it.copy(slot = slot, error = null) }

    fun editNote(note: String) = _state.update { it.copy(note = note.take(500)) }

    fun book() = viewModelScope.launch {
        val s = _state.value
        val slot = s.slot ?: return@launch
        if (!auth.isSignedIn.first()) return@launch _state.update { it.copy(signInRequired = true) }
        _state.update { it.copy(booking = true, error = null) }
        when (val r = booking.book(productId, slot.startsAt, s.note)) {
            is Outcome.Success -> {
                _state.update { it.copy(booking = false, booked = r.value, slot = null, note = "") }
                // The hour just taken leaves the list.
                s.week?.let { loadWeek(it.from) }
            }
            is Outcome.Failure -> {
                _state.update { it.copy(booking = false, error = r.error, slot = null) }
                // SLOT_TAKEN / SLOT_UNAVAILABLE: show the times still free.
                s.week?.let { loadWeek(it.from) }
            }
        }
    }

    fun signInHandled() = _state.update { it.copy(signInRequired = false) }

    private fun loadWeek(from: String) {
        _state.update { it.copy(loadingWeek = true) }
        viewModelScope.launch {
            when (val r = booking.availability(productId, from)) {
                is Outcome.Success -> _state.update { cur ->
                    if (firstDay == null) firstDay = r.value.from
                    // Open on the first day that has a free time.
                    val firstFree = r.value.days.indexOfFirst { it.slots.isNotEmpty() }.coerceAtLeast(0)
                    cur.copy(week = r.value, loadingWeek = false, dayIndex = firstFree, slot = null)
                }
                is Outcome.Failure -> _state.update { it.copy(loadingWeek = false, error = r.error) }
            }
        }
    }

    private fun today(timeZone: String): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = TimeZone.getTimeZone(timeZone) }.format(java.util.Date())

    private fun shift(day: String, days: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = fmt.parse(day)!!; add(Calendar.DAY_OF_MONTH, days) }
        return fmt.format(cal.time)
    }
}

/**
 * The web's BookingPicker: a week of days, each with its free times in the
 * provider's timezone, an optional note, then Book (or Hold, when a
 * deposit keeps the time).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookingPanel(
    onRequireSignIn: () -> Unit,
    onOpenAppointments: () -> Unit,
    viewModel: BookingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AzzaTheme.colors
    LaunchedEffect(state.signInRequired) {
        if (state.signInRequired) {
            viewModel.signInHandled()
            onRequireSignIn()
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.booking_title), style = MaterialTheme.typography.titleMedium)
        if (state.unavailable) {
            Text(stringResource(R.string.booking_not_open), color = colors.textMuted)
            return@Column
        }
        val setup = state.setup ?: return@Column CircularProgressIndicator()
        Text(stringResource(R.string.booking_session_length, setup.durationMinutes), color = colors.textSecondary)

        state.booked?.let { appointment ->
            val whenText = formatWhen(appointment.startsAt, appointment.timezone)
            if (appointment.status == AppointmentStatus.HELD) {
                Text(stringResource(R.string.booking_held, whenText), color = colors.warning)
                Button(onClick = onOpenAppointments) { Text(stringResource(R.string.booking_go_to_basket)) }
            } else {
                Text(stringResource(R.string.booking_booked, whenText), color = colors.success)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = viewModel::previousWeek, enabled = !state.loadingWeek) { Text(stringResource(R.string.booking_earlier)) }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.week?.days?.forEachIndexed { index, day ->
                    FilterChip(
                        selected = index == state.dayIndex,
                        onClick = { viewModel.pickDay(index) },
                        enabled = day.slots.isNotEmpty(),
                        label = { Text(formatDayChip(day.date)) },
                    )
                }
            }
            TextButton(onClick = viewModel::nextWeek, enabled = !state.loadingWeek) { Text(stringResource(R.string.booking_later)) }
        }

        val week = state.week
        when {
            state.loadingWeek -> CircularProgressIndicator()
            week == null -> Unit
            week.days.all { it.slots.isEmpty() } -> Text(stringResource(R.string.booking_none_this_week), color = colors.textMuted)
            else -> {
                val day = week.days.getOrNull(state.dayIndex)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    day?.slots?.forEach { slot ->
                        FilterChip(
                            selected = slot == state.slot,
                            onClick = { viewModel.pickSlot(slot) },
                            label = { Text(formatClock(slot.startsAt, week.timezone)) },
                        )
                    }
                }
                Text(stringResource(R.string.booking_timezone_note, week.timezone), color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (state.slot != null) {
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::editNote,
                label = { Text(stringResource(R.string.booking_note_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            if (setup.requiresDeposit) stringResource(R.string.booking_deposit_required, setup.depositPercent, setup.deposit.format())
            else stringResource(R.string.booking_pay_on_site),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        state.error?.let { Text(bookingErrorText(it), color = colors.error) }
        Button(
            onClick = { viewModel.book() },
            enabled = state.slot != null && !state.booking,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(if (setup.requiresDeposit) R.string.booking_hold_action else R.string.booking_book_action))
        }
        if (state.booked != null) OutlinedButton(onClick = onOpenAppointments, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.booking_my_appointments))
        }
    }
}

@Composable
private fun bookingErrorText(error: AppError): String = when ((error as? AppError.Api)?.code) {
    "SLOT_TAKEN" -> stringResource(R.string.booking_error_slot_taken)
    "SLOT_UNAVAILABLE" -> stringResource(R.string.booking_error_not_available)
    "BOOKING_SELF_NOT_ALLOWED" -> stringResource(R.string.booking_error_self)
    else -> errorMessage(error)
}
