package com.azza.feature.basket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.BookingRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Appointment
import com.azza.core.model.AppointmentStatus
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadingState
import com.azza.core.ui.errorMessage
import com.azza.core.ui.format
import com.azza.core.ui.formatWhen
import com.azza.core.ui.minutesUntil
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppointmentsUiState(
    val received: Boolean = false,
    val loading: Boolean = true,
    val items: List<Appointment> = emptyList(),
    val error: AppError? = null,
    /** The hold whose contact form is open, and what has been typed. */
    val payingId: String? = null,
    val name: String = "",
    val email: String = "",
    val address: String = "",
    val busyId: String? = null,
    val actionError: AppError? = null,
    /** A deposit order was created: open it (and pay it there). */
    val openOrderId: String? = null,
)

@HiltViewModel
class AppointmentsViewModel @Inject constructor(private val booking: BookingRepository) : ViewModel() {
    private val _state = MutableStateFlow(AppointmentsUiState())
    val state: StateFlow<AppointmentsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun showReceived(received: Boolean) {
        if (received == _state.value.received) return
        _state.update { AppointmentsUiState(received = received) }
        load()
    }

    fun load() = viewModelScope.launch {
        // Only the first load shows a spinner; a re-read keeps the list on screen.
        _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
        val r = if (_state.value.received) booking.received() else booking.mine()
        _state.update {
            when (r) {
                is Outcome.Success -> it.copy(loading = false, items = r.value)
                is Outcome.Failure -> it.copy(loading = false, error = r.error)
            }
        }
    }

    fun openPayment(a: Appointment) = _state.update {
        it.copy(payingId = a.id, name = a.contactName.orEmpty(), email = a.contactEmail.orEmpty(), address = a.contactAddress.orEmpty(), actionError = null)
    }

    fun edit(transform: AppointmentsUiState.() -> AppointmentsUiState) = _state.update { it.transform().copy(actionError = null) }

    fun payDeposit() {
        val s = _state.value
        val id = s.payingId ?: return
        if (s.name.trim().length < 2 || !s.email.contains('@')) return _state.update { it.copy(actionError = AppError.Api(400, "CONTACT_INVALID", "", null)) }
        _state.update { it.copy(busyId = id) }
        viewModelScope.launch {
            when (val r = booking.startDepositPayment(id, s.name, s.email, s.address)) {
                is Outcome.Success -> _state.update { it.copy(busyId = null, payingId = null, openOrderId = r.value) }
                is Outcome.Failure -> {
                    _state.update { it.copy(busyId = null, actionError = r.error) }
                    load()
                }
            }
        }
    }

    fun orderOpened() = _state.update { it.copy(openOrderId = null) }

    fun act(a: Appointment, action: suspend (String) -> Outcome<Appointment>) {
        _state.update { it.copy(busyId = a.id, actionError = null) }
        viewModelScope.launch {
            val r = action(a.id)
            _state.update { it.copy(busyId = null, actionError = (r as? Outcome.Failure)?.error) }
            load()
        }
    }

    fun cancel(a: Appointment) = act(a, booking::cancel)
    fun complete(a: Appointment) = act(a, booking::complete)
    fun noShow(a: Appointment) = act(a, booking::noShow)
}

/**
 * The basket's fourth panel (the web's AppointmentsBasketPanel), plus the
 * provider's side ("My calendar"): one list, grouped — deposits to pay,
 * upcoming, past.
 */
@Composable
internal fun AppointmentsTab(onOpenOrder: (String) -> Unit, onOpenProduct: (String) -> Unit, viewModel: AppointmentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.openOrderId) {
        state.openOrderId?.let {
            viewModel.orderOpened()
            onOpenOrder(it)
        }
    }
    // Back from paying a deposit (or from anywhere): re-read, so a paid hold shows as confirmed.
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose { }
    }
    // The hold countdown ticks once a minute.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!state.received, { viewModel.showReceived(false) }, { Text(stringResource(R.string.appt_tab_mine)) })
            FilterChip(state.received, { viewModel.showReceived(true) }, { Text(stringResource(R.string.appt_tab_received)) })
        }
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(state.error!!, onRetry = { viewModel.load() })
            state.items.isEmpty() -> com.azza.core.ui.EmptyState(
                stringResource(if (state.received) R.string.appt_empty_received else R.string.appt_empty_mine),
            )
            else -> {
                val nowMs = now
                val live = setOf(AppointmentStatus.HELD, AppointmentStatus.CONFIRMED)
                val unpaid = state.items.filter { !state.received && it.status == AppointmentStatus.HELD && it.deposit.amountMinor > 0 }
                val upcoming = state.items.filter { it !in unpaid && it.status in live && (minutesUntil(it.endsAt, nowMs) ?: 0) > 0 }
                val past = state.items.filter { it !in unpaid && it !in upcoming }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.actionError?.let { e ->
                        item { Text(contactOrError(e), color = AzzaTheme.colors.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                    }
                    if (unpaid.isNotEmpty()) {
                        item { GroupTitle(R.string.appt_group_unpaid, R.string.appt_group_unpaid_hint) }
                        items(unpaid, key = { it.id }) { a -> UnpaidCard(a, state, viewModel, nowMs) }
                    }
                    if (upcoming.isNotEmpty()) {
                        item { GroupTitle(R.string.appt_group_upcoming) }
                        items(upcoming, key = { it.id }) { a -> AppointmentCard(a, state, viewModel, onOpenProduct) }
                    }
                    if (past.isNotEmpty()) {
                        item { GroupTitle(R.string.appt_group_past) }
                        items(past, key = { it.id }) { a -> AppointmentCard(a, state, viewModel, onOpenProduct) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(title: Int, hint: Int? = null) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        hint?.let { Text(stringResource(it), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)).background(AzzaTheme.colors.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun UnpaidCard(a: Appointment, state: AppointmentsUiState, viewModel: AppointmentsViewModel, now: Long) {
    CardBox {
        Text(a.productTitle, style = MaterialTheme.typography.titleSmall)
        Text(formatWhen(a.startsAt, a.timezone), color = AzzaTheme.colors.textSecondary)
        val left = minutesUntil(a.holdExpiresAt, now)
        Text(
            if (left == null || left > 0) stringResource(R.string.appt_hold_expires_in, left ?: 0) else stringResource(R.string.appt_hold_expired),
            color = AzzaTheme.colors.warning,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(R.string.appt_deposit_amount, a.deposit.format()))
        if (state.payingId == a.id) {
            Text(stringResource(R.string.appt_contact_hint), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(state.name, { v -> viewModel.edit { copy(name = v) } }, label = { Text(stringResource(R.string.appt_contact_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.email, { v -> viewModel.edit { copy(email = v) } }, label = { Text(stringResource(R.string.appt_contact_email)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.address, { v -> viewModel.edit { copy(address = v) } }, label = { Text(stringResource(R.string.appt_contact_address)) },
                modifier = Modifier.fillMaxWidth())
            Button(onClick = viewModel::payDeposit, enabled = state.busyId == null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.appt_pay_deposit, a.deposit.format()))
            }
        } else if (left != 0) {
            Button(onClick = { viewModel.openPayment(a) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.appt_pay_deposit, a.deposit.format()))
            }
        }
        TextButton(onClick = { viewModel.cancel(a) }, enabled = state.busyId == null) { Text(stringResource(R.string.appt_cancel)) }
    }
}

@Composable
private fun AppointmentCard(a: Appointment, state: AppointmentsUiState, viewModel: AppointmentsViewModel, onOpenProduct: (String) -> Unit) {
    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onOpenProduct(a.productId) }) { Text(a.productTitle, style = MaterialTheme.typography.titleSmall) }
            Text(statusText(a.status), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text(formatWhen(a.startsAt, a.timezone), color = AzzaTheme.colors.textSecondary)
        a.counterpartyName?.let { Text(stringResource(R.string.appt_with, it), style = MaterialTheme.typography.bodySmall) }
        if (state.received) {
            // The provider reads the contact details: that is who they were collected for.
            listOfNotNull(a.contactName, a.contactEmail, a.contactAddress).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
            }
            a.note?.let { Text(stringResource(R.string.appt_note, it), style = MaterialTheme.typography.bodySmall) }
        }
        if (a.deposit.amountMinor > 0 && a.status != AppointmentStatus.HELD) {
            Text(stringResource(R.string.appt_deposit_paid, a.deposit.format()), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.success)
        }
        if (a.status == AppointmentStatus.CONFIRMED || a.status == AppointmentStatus.HELD) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.received && a.status == AppointmentStatus.CONFIRMED) {
                    TextButton(onClick = { viewModel.complete(a) }, enabled = state.busyId == null) { Text(stringResource(R.string.appt_complete)) }
                    TextButton(onClick = { viewModel.noShow(a) }, enabled = state.busyId == null) { Text(stringResource(R.string.appt_no_show)) }
                }
                TextButton(onClick = { viewModel.cancel(a) }, enabled = state.busyId == null) {
                    Text(stringResource(R.string.appt_cancel), color = AzzaTheme.colors.error)
                }
            }
        }
    }
}

@Composable
private fun statusText(status: AppointmentStatus): String = stringResource(
    when (status) {
        AppointmentStatus.HELD -> R.string.appt_status_held
        AppointmentStatus.CONFIRMED -> R.string.appt_status_confirmed
        AppointmentStatus.COMPLETED -> R.string.appt_status_completed
        AppointmentStatus.CANCELLED -> R.string.appt_status_cancelled
        AppointmentStatus.NO_SHOW -> R.string.appt_status_no_show
        AppointmentStatus.EXPIRED, AppointmentStatus.UNKNOWN -> R.string.appt_status_expired
    },
)

@Composable
private fun contactOrError(e: AppError): String =
    if ((e as? AppError.Api)?.code == "CONTACT_INVALID" || (e as? AppError.Api)?.status == 400) stringResource(R.string.appt_contact_invalid)
    else errorMessage(e)
