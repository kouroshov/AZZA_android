@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.azza.feature.stores

import androidx.compose.material3.Checkbox
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import com.azza.core.ui.BarcodeDialog
import com.azza.core.ui.OwnerPostsBar
import com.azza.core.ui.PostActionsSheet
import com.azza.core.data.OwnerPostsActions
import com.azza.core.data.ListingRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil.compose.AsyncImage
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.BookingRepository
import com.azza.core.data.CalendarWeek
import com.azza.core.data.CursorPager
import com.azza.core.data.ProfilesRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Appointment
import com.azza.core.model.AppointmentStatus
import com.azza.core.model.PostTile
import com.azza.core.ui.EmptyState
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadMoreEffect
import com.azza.core.ui.LoadingState
import com.azza.core.ui.addDays
import com.azza.core.ui.dayKey
import com.azza.core.ui.discounted
import com.azza.core.ui.format
import com.azza.core.ui.formatClock
import com.azza.core.ui.formatDayChip
import com.azza.core.ui.loadingMoreFooter
import com.azza.core.ui.today
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- a store's listings, every state (the web's store "Products" tab)

@OptIn(FlowPreview::class)
@HiltViewModel
class StoreProductsViewModel @Inject constructor(savedStateHandle: SavedStateHandle, profiles: ProfilesRepository, listings: ListingRepository) : ViewModel() {
    val storeId: String = savedStateHandle.toRoute<StoreProductsRoute>().storeId
    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()
    val pager = CursorPager<PostTile>(viewModelScope, { it.id }) { profiles.myStorePosts(storeId, it, _search.value) }
    val owner = OwnerPostsActions(viewModelScope, listings) { pager.refresh(showSpinner = true) }

    init {
        pager.refresh()
        viewModelScope.launch { _search.drop(1).debounce(300).distinctUntilChanged().collect { pager.refresh() } }
    }

    fun onSearch(value: String) {
        _search.value = value
    }
}

@Composable
fun StoreProductsScreen(nav: StoreNavigation, viewModel: StoreProductsViewModel = hiltViewModel()) {
    val state by viewModel.pager.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val owner by viewModel.owner.state.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<PostTile?>(null) }
    var barcodeFor by remember { mutableStateOf<PostTile?>(null) }
    menuFor?.let { p ->
        PostActionsSheet(
            title = p.title,
            showBarcode = true,
            onEdit = { nav.editListing(p.id) },
            onBarcode = { barcodeFor = p },
            onDelete = { viewModel.owner.deleteOne(p.id) { } },
            onSelect = { viewModel.owner.start(p.id) },
            onDismiss = { menuFor = null },
        )
    }
    barcodeFor?.let { p -> BarcodeDialog(p.barcode, p.title) { barcodeFor = null } }
    BackHandler(enabled = owner.selecting) { viewModel.owner.stop() }
    // A listing edited and back: show it as it is now.
    LifecycleResumeEffect(viewModel) {
        if (!state.loadingFirst) viewModel.pager.refresh(showSpinner = true)
        onPauseOrDispose { }
    }
    LoadMoreEffect(listState, state.items.size, viewModel.pager::loadMore)
    StorePage(stringResource(R.string.stores_products), nav.back, actions = {
        IconButton(onClick = { nav.postFromStore(viewModel.storeId) }) { Icon(Icons.Outlined.Add, stringResource(R.string.stores_products_add)) }
    }) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = search,
                onValueChange = viewModel::onSearch,
                placeholder = { Text(stringResource(R.string.stores_products_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            when {
                state.loadingFirst -> LoadingState()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = { viewModel.pager.refresh() })
                state.items.isEmpty() -> EmptyState(stringResource(R.string.stores_products_empty))
                else -> LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(state.items, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = { if (owner.selecting) viewModel.owner.toggle(p.id) else nav.openListing(p.id) },
                                    onLongClick = { menuFor = p },
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (owner.selecting) Checkbox(checked = p.id in owner.selected, onCheckedChange = { viewModel.owner.toggle(p.id) })
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(AzzaTheme.colors.surface)) {
                                p.imageUrl?.let { AsyncImage(it, p.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(p.price.discounted(p.discountPercentage).format(), style = MaterialTheme.typography.titleSmall)
                            }
                            postStatusLabel(p.status)?.let { label ->
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (p.status == "REJECTED") AzzaTheme.colors.error else AzzaTheme.colors.textSecondary,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AzzaTheme.colors.surface).padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = AzzaTheme.colors.navDivider)
                    }
                    loadingMoreFooter(state.loadingMore)
                }
            }
            OwnerPostsBar(owner, viewModel.owner::applyDiscount, viewModel.owner::deleteSelected, viewModel.owner::stop)
        }
    }
}

/** A listing's state when it is not simply live; null for ACTIVE. */
@Composable
internal fun postStatusLabel(status: String): String? = when (status) {
    "ACTIVE" -> null
    "DRAFT" -> stringResource(R.string.stores_status_draft)
    "SOLD" -> stringResource(R.string.stores_status_sold)
    "REJECTED" -> stringResource(R.string.stores_status_held)
    else -> stringResource(R.string.stores_status_inactive)
}

// ---------------------------------------------------------------- a calendar's week (the web's StoreCalendarPanel)

data class CalendarWeekState(
    val from: String = today(),
    val week: CalendarWeek? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val busyId: String? = null,
)

@HiltViewModel
class CalendarWeekViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val booking: BookingRepository) : ViewModel() {
    private val storeId: String? = savedStateHandle.toRoute<CalendarWeekRoute>().storeId
    private val _state = MutableStateFlow(CalendarWeekState())
    val state: StateFlow<CalendarWeekState> = _state.asStateFlow()

    init {
        load()
    }

    fun load(from: String = _state.value.from) = viewModelScope.launch {
        _state.update { it.copy(from = from, loading = true, error = null) }
        when (val r = booking.calendarWeek(storeId, from, addDays(from, 6))) {
            is Outcome.Success -> _state.update { it.copy(week = r.value, loading = false) }
            is Outcome.Failure -> _state.update { it.copy(loading = false, error = r.error) }
        }
    }

    fun shift(days: Int) = load(addDays(_state.value.from, days))
    fun thisWeek() = load(today(_state.value.week?.timezone ?: "Asia/Tehran"))

    fun cancel(a: Appointment) = act(a) { booking.cancel(a.id) }
    fun complete(a: Appointment) = act(a) { booking.complete(a.id) }
    fun noShow(a: Appointment) = act(a) { booking.noShow(a.id) }

    private fun act(a: Appointment, call: suspend () -> Outcome<Appointment>) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = a.id, error = null) }
        viewModelScope.launch {
            when (val r = call()) {
                is Outcome.Success -> _state.update { s ->
                    s.copy(busyId = null, week = s.week?.copy(appointments = s.week.appointments.map { if (it.id == a.id) r.value else it }))
                }
                is Outcome.Failure -> _state.update { it.copy(busyId = null, error = r.error) }
            }
        }
    }
}

@Composable
fun CalendarWeekScreen(nav: StoreNavigation, viewModel: CalendarWeekViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var cancelling by remember { mutableStateOf<Appointment?>(null) }
    cancelling?.let { a ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            text = { Text(stringResource(R.string.stores_appt_cancel_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.cancel(a); cancelling = null }) { Text(stringResource(R.string.stores_appt_cancel), color = AzzaTheme.colors.error) } },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text(stringResource(R.string.stores_keep)) } },
        )
    }
    StorePage(stringResource(R.string.stores_appointments_week), nav.back) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.shift(-7) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.stores_week_prev)) }
                Text(
                    "${formatDayChip(state.from)} – ${formatDayChip(addDays(state.from, 6))}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::thisWeek) { Text(stringResource(R.string.stores_week_today)) }
                IconButton(onClick = { viewModel.shift(7) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.stores_week_next)) }
            }
            state.error?.let { Text(storeError(it), color = AzzaTheme.colors.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            val week = state.week
            when {
                state.loading && week == null -> LoadingState()
                week == null -> Unit
                else -> {
                    val byDay = week.appointments.groupBy { dayKey(it.startsAt, week.timezone) }
                    LazyColumn(Modifier.fillMaxSize()) {
                        (0..6).map { addDays(state.from, it) }.forEach { day ->
                            item(key = "day-$day") {
                                Text(
                                    formatDayChip(day),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (day == today(week.timezone)) AzzaTheme.colors.primary else AzzaTheme.colors.textSecondary,
                                    modifier = Modifier.fillMaxWidth().background(AzzaTheme.colors.surface).padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                            val list = byDay[day].orEmpty()
                            if (list.isEmpty()) {
                                item(key = "none-$day") {
                                    Text(stringResource(R.string.stores_week_none), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                            }
                            items(list, key = { it.id }) { a ->
                                WeekAppointment(a, week.timezone, busy = state.busyId != null, onOpen = { nav.openListing(a.productId) }, onCancel = { cancelling = a }, onComplete = { viewModel.complete(a) }, onNoShow = { viewModel.noShow(a) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekAppointment(a: Appointment, zone: String, busy: Boolean, onOpen: () -> Unit, onCancel: () -> Unit, onComplete: () -> Unit, onNoShow: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${formatClock(a.startsAt, zone)}–${formatClock(a.endsAt, zone)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(apptStatus(a.status), style = MaterialTheme.typography.labelSmall, color = AzzaTheme.colors.textMuted)
        }
        Text(a.productTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        listOfNotNull(a.contactName ?: a.counterpartyName, a.contactEmail, a.contactAddress).takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
        }
        a.note?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary) }
        if (a.status == AppointmentStatus.CONFIRMED || a.status == AppointmentStatus.HELD) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (a.status == AppointmentStatus.CONFIRMED) {
                    TextButton(onClick = onComplete, enabled = !busy) { Text(stringResource(R.string.stores_appt_complete)) }
                    TextButton(onClick = onNoShow, enabled = !busy) { Text(stringResource(R.string.stores_appt_no_show)) }
                }
                TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.stores_appt_cancel), color = AzzaTheme.colors.error) }
            }
        }
    }
    HorizontalDivider(color = AzzaTheme.colors.navDivider)
}

@Composable
private fun apptStatus(status: AppointmentStatus): String = stringResource(
    when (status) {
        AppointmentStatus.HELD -> R.string.stores_appt_held
        AppointmentStatus.CONFIRMED -> R.string.stores_appt_confirmed
        AppointmentStatus.COMPLETED -> R.string.stores_appt_completed
        AppointmentStatus.CANCELLED -> R.string.stores_appt_cancelled
        AppointmentStatus.NO_SHOW -> R.string.stores_appt_noshow
        AppointmentStatus.EXPIRED, AppointmentStatus.UNKNOWN -> R.string.stores_appt_expired
    },
)
