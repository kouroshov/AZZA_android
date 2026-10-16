package com.azza.feature.stores

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.Appeal
import com.azza.core.data.BookingSettings
import com.azza.core.data.Branch
import com.azza.core.data.BranchLocation
import com.azza.core.data.Closure
import com.azza.core.data.CursorPager
import com.azza.core.data.DayHours
import com.azza.core.data.DayOff
import com.azza.core.data.FollowEntry
import com.azza.core.data.GeographyRepository
import com.azza.core.data.Occupation
import com.azza.core.data.ManagedStore
import com.azza.core.data.StockLine
import com.azza.core.data.StoreDraft
import com.azza.core.data.StoresRepository
import com.azza.core.data.Timetable
import com.azza.core.data.TimeWindow
import com.azza.core.model.Area
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loading / failed / ready for one thing, the shape every screen here starts from. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val error: AppError) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

private fun <T> Outcome<T>.toLoad(): Load<T> = when (this) {
    is Outcome.Success -> Load.Ready(value)
    is Outcome.Failure -> Load.Failed(error)
}

// ---------------------------------------------------------------- my stores

data class MyStoresState(val stores: Load<List<ManagedStore>> = Load.Loading, val canCreate: Boolean? = null)

@HiltViewModel
class MyStoresViewModel @Inject constructor(private val repo: StoresRepository) : ViewModel() {
    private val _state = MutableStateFlow(MyStoresState())
    val state: StateFlow<MyStoresState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        val stores = repo.mine().toLoad()
        val eligible = (repo.canCreate() as? Outcome.Success)?.value
        _state.value = MyStoresState(stores, eligible)
    }
}

// ---------------------------------------------------------------- create / edit

data class StoreFormState(
    val loading: Boolean = false,
    val draft: StoreDraft = StoreDraft(),
    val originalHandle: String = "",
    val occupationQuery: String = "",
    val occupationResults: List<Occupation> = emptyList(),
    val saving: Boolean = false,
    val error: AppError? = null,
    /** Create: the new store's id; edit: the edited one's. */
    val savedId: String? = null,
)

/** One form for both: [StoreEditRoute] edits, [CreateStoreRoute] creates. */
@HiltViewModel
class StoreFormViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: StoresRepository) : ViewModel() {
    private val storeId: String? = savedStateHandle.get<String>("storeId")
    val isNew = storeId == null
    private val _state = MutableStateFlow(StoreFormState(loading = storeId != null))
    val state: StateFlow<StoreFormState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        if (storeId != null) viewModelScope.launch {
            when (val r = repo.store(storeId)) {
                is Outcome.Success -> r.value.let { s ->
                    _state.update {
                        it.copy(
                            loading = false,
                            originalHandle = s.slug,
                            draft = StoreDraft(s.name, s.slug, s.phone.orEmpty(), s.description.orEmpty(), s.occupation),
                        )
                    }
                }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun edit(transform: StoreDraft.() -> StoreDraft) = _state.update { it.copy(draft = it.draft.transform(), error = null) }

    fun searchOccupation(q: String) {
        _state.update { it.copy(occupationQuery = q) }
        searchJob?.cancel()
        if (q.trim().length < 2) return _state.update { it.copy(occupationResults = emptyList()) }
        searchJob = viewModelScope.launch {
            delay(300)
            (repo.searchOccupations(q) as? Outcome.Success)?.let { r -> _state.update { it.copy(occupationResults = r.value) } }
        }
    }

    fun chooseOccupation(o: Occupation?) = _state.update { it.copy(draft = it.draft.copy(occupation = o), occupationQuery = "", occupationResults = emptyList()) }

    fun save() {
        val s = _state.value
        if (s.saving || s.draft.name.isBlank()) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val r: Outcome<String> = if (storeId == null) {
                when (val c = repo.create(s.draft)) {
                    is Outcome.Success -> Outcome.Success(c.value.id)
                    is Outcome.Failure -> c
                }
            } else {
                when (val u = repo.update(storeId, s.draft, handleChanged = s.draft.handle.trim().lowercase() != s.originalHandle)) {
                    is Outcome.Success -> Outcome.Success(storeId)
                    is Outcome.Failure -> u
                }
            }
            _state.update {
                when (r) {
                    is Outcome.Success -> it.copy(saving = false, savedId = r.value)
                    is Outcome.Failure -> it.copy(saving = false, error = r.error)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- one store

data class StoreHomeState(
    val store: Load<ManagedStore> = Load.Loading,
    val branches: List<Branch> = emptyList(),
    val photoBusy: Boolean = false,
    val actionError: AppError? = null,
    val appeals: List<Appeal> = emptyList(),
    val appealSending: Boolean = false,
    val appealError: AppError? = null,
    val newBranchSaving: Boolean = false,
)

@HiltViewModel
class StoreHomeViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: StoresRepository) : ViewModel() {
    val storeId: String = savedStateHandle.toRoute<StoreHomeRoute>().storeId
    private val _state = MutableStateFlow(StoreHomeState())
    val state: StateFlow<StoreHomeState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        val store = repo.store(storeId).toLoad()
        val branches = (repo.branches(storeId) as? Outcome.Success)?.value.orEmpty()
        val appeals = if ((store as? Load.Ready)?.value?.hold?.appealable == true) (repo.appeals(storeId) as? Outcome.Success)?.value.orEmpty() else emptyList()
        _state.update { it.copy(store = store, branches = branches, appeals = appeals) }
    }

    fun setPhoto(uri: String) = photo { repo.uploadAvatar(storeId, uri) }

    fun removePhoto() = photo { repo.removeAvatar(storeId) }

    private fun photo(call: suspend () -> Outcome<Unit>) {
        if (_state.value.photoBusy) return
        _state.update { it.copy(photoBusy = true, actionError = null) }
        viewModelScope.launch {
            val r = call()
            _state.update { it.copy(photoBusy = false, actionError = (r as? Outcome.Failure)?.error) }
            if (r is Outcome.Success) load()
        }
    }

    fun addBranch(name: String, phone: String, onCreated: (String) -> Unit) {
        if (_state.value.newBranchSaving || name.isBlank()) return
        _state.update { it.copy(newBranchSaving = true, actionError = null) }
        viewModelScope.launch {
            when (val r = repo.createBranch(storeId, name, phone)) {
                is Outcome.Success -> {
                    _state.update { it.copy(newBranchSaving = false) }
                    load()
                    onCreated(r.value.id)
                }
                is Outcome.Failure -> _state.update { it.copy(newBranchSaving = false, actionError = r.error) }
            }
        }
    }

    fun appeal(message: String) {
        if (_state.value.appealSending) return
        _state.update { it.copy(appealSending = true, appealError = null) }
        viewModelScope.launch {
            when (val r = repo.appealSuspension(storeId, message)) {
                is Outcome.Success -> _state.update { it.copy(appealSending = false, appeals = listOf(r.value) + it.appeals) }
                is Outcome.Failure -> _state.update { it.copy(appealSending = false, appealError = r.error) }
            }
        }
    }
}

@HiltViewModel
class StoreFollowersViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repo: StoresRepository) : ViewModel() {
    private val storeId = savedStateHandle.toRoute<StoreFollowersRoute>().storeId
    val pager = CursorPager<FollowEntry>(viewModelScope, { it.id }) { repo.followers(storeId, it) }

    init {
        pager.refresh()
    }
}

// ---------------------------------------------------------------- a branch

data class BranchState(
    val branch: Load<Branch> = Load.Loading,
    val name: String = "",
    val phone: String = "",
    val active: Boolean = true,
    val week: List<DayHours> = emptyList(),
    val closures: List<Closure> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class BranchViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: StoresRepository) : ViewModel() {
    private val route = savedStateHandle.toRoute<BranchRoute>()
    val storeId = route.storeId
    val branchId = route.branchId
    private val _state = MutableStateFlow(BranchState())
    val state: StateFlow<BranchState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        when (val r = repo.branch(storeId, branchId)) {
            is Outcome.Success -> r.value.let { b ->
                _state.update { it.copy(branch = Load.Ready(b), name = b.name, phone = b.phone.orEmpty(), active = b.status == "ACTIVE", week = b.week, closures = b.closures) }
            }
            is Outcome.Failure -> _state.update { it.copy(branch = Load.Failed(r.error)) }
        }
    }

    fun edit(transform: BranchState.() -> BranchState) = _state.update { it.transform().copy(saved = false, error = null) }

    fun setDay(day: DayHours) = edit { copy(week = week.map { if (it.dayOfWeek == day.dayOfWeek) day else it }) }

    fun addClosure(date: String, reason: String) = edit { copy(closures = (closures.filterNot { it.date == date } + Closure(date, reason)).sortedBy { it.date }) }

    fun removeClosure(date: String) = edit { copy(closures = closures.filterNot { it.date == date }) }

    /** Details, week and closures are three apps/api writes; they are saved together, in that order. */
    fun save() {
        val s = _state.value
        if (s.saving || s.name.isBlank()) return
        if (s.week.any { !it.closed && it.closes <= it.opens }) return _state.update { it.copy(error = AppError.Api(400, "HOURS_ORDER", "", null)) }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val failure = listOf(
                suspend { repo.updateBranch(storeId, branchId, s.name, s.phone, s.active) },
                suspend { repo.setHours(storeId, branchId, s.week) },
                suspend { repo.setClosures(storeId, branchId, s.closures) },
            ).firstNotNullOfOrNull { (it() as? Outcome.Failure)?.error }
            _state.update { it.copy(saving = false, saved = failure == null, error = failure) }
        }
    }
}

// ---------------------------------------------------------------- where a branch is

data class LocationState(
    val provinces: List<Area> = emptyList(),
    val cities: List<Area> = emptyList(),
    val province: Area? = null,
    val city: Area? = null,
    val address: String = "",
    val start: BranchLocation? = null,
    val saving: Boolean = false,
    val done: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class LocationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: StoresRepository,
    private val geography: GeographyRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<BranchLocationRoute>()
    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val provinces = (geography.provinces() as? Outcome.Success)?.value.orEmpty()
            val current = (repo.branch(route.storeId, route.branchId) as? Outcome.Success)?.value?.location
            _state.update { it.copy(provinces = provinces, start = current, address = current?.address.orEmpty()) }
        }
    }

    fun chooseProvince(p: Area) {
        _state.update { it.copy(province = p, city = null, cities = emptyList()) }
        viewModelScope.launch {
            (geography.cities(p.id) as? Outcome.Success)?.let { r -> _state.update { it.copy(cities = r.value) } }
        }
    }

    fun chooseCity(c: Area) = _state.update { it.copy(city = c, error = null) }

    fun editAddress(text: String) = _state.update { it.copy(address = text.take(300), error = null) }

    /** apps/api checks the point against the city (boundaryVerified tells whether it really could). */
    fun save(latitude: Double, longitude: Double) {
        val s = _state.value
        val city = s.city ?: return
        if (s.saving || s.address.isBlank()) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.setLocation(route.storeId, route.branchId, BranchLocation(latitude, longitude, s.address, city.id))) {
                is Outcome.Success -> _state.update { it.copy(saving = false, done = true) }
                is Outcome.Failure -> _state.update { it.copy(saving = false, error = r.error) }
            }
        }
    }
}

// ---------------------------------------------------------------- stock at a branch

data class StockState(val lines: Load<List<StockLine>> = Load.Loading, val savingId: String? = null, val error: AppError? = null)

@HiltViewModel
class StockViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: StoresRepository) : ViewModel() {
    private val route = savedStateHandle.toRoute<BranchStockRoute>()
    private val _state = MutableStateFlow(StockState())
    val state: StateFlow<StockState> = _state.asStateFlow()

    fun load() = viewModelScope.launch { _state.update { it.copy(lines = repo.stock(route.storeId, route.branchId).toLoad()) } }

    fun setQuantity(productId: String, quantity: Int) {
        if (_state.value.savingId != null) return
        _state.update { it.copy(savingId = productId, error = null) }
        viewModelScope.launch {
            when (val r = repo.setQuantity(route.storeId, route.branchId, productId, quantity)) {
                is Outcome.Success -> _state.update { s ->
                    val lines = (s.lines as? Load.Ready)?.value?.map { if (it.productId == productId) it.copy(quantity = quantity) else it }
                    s.copy(savingId = null, lines = lines?.let { Load.Ready(it) } ?: s.lines)
                }
                is Outcome.Failure -> _state.update { it.copy(savingId = null, error = r.error) }
            }
        }
    }
}

// ---------------------------------------------------------------- the timetable

data class TimetableState(val timetable: Load<Timetable> = Load.Loading, val draft: Timetable? = null, val saving: Boolean = false, val saved: Boolean = false, val error: AppError? = null)

@HiltViewModel
class TimetableViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: StoresRepository) : ViewModel() {
    private val storeId = savedStateHandle.toRoute<TimetableRoute>().storeId
    private val _state = MutableStateFlow(TimetableState())
    val state: StateFlow<TimetableState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        val r = repo.timetable(storeId)
        _state.update { it.copy(timetable = r.toLoad(), draft = (r as? Outcome.Success)?.value) }
    }

    private fun edit(transform: Timetable.() -> Timetable) = _state.update { s -> s.copy(draft = s.draft?.transform(), saved = false, error = null) }

    fun addWindow(day: Int) = edit { copy(windows = (windows + TimeWindow(day, 9 * 60, 17 * 60)).sortedWith(compareBy({ it.dayOfWeek }, { it.start }))) }

    fun setWindow(old: TimeWindow, new: TimeWindow) = edit { copy(windows = windows.map { if (it == old) new else it }.sortedWith(compareBy({ it.dayOfWeek }, { it.start }))) }

    fun removeWindow(w: TimeWindow) = edit { copy(windows = windows - w) }

    fun addDayOff(off: DayOff) = edit { copy(daysOff = (daysOff + off).sortedBy { it.startsAt }) }

    fun removeDayOff(off: DayOff) = edit { copy(daysOff = daysOff - off) }

    fun save() {
        val draft = _state.value.draft ?: return
        if (_state.value.saving) return
        if (draft.windows.any { it.end <= it.start }) return _state.update { it.copy(error = AppError.Api(400, "HOURS_ORDER", "", null)) }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.saveTimetable(storeId, draft)) {
                is Outcome.Success -> _state.update { it.copy(saving = false, saved = true, draft = r.value, timetable = Load.Ready(r.value)) }
                is Outcome.Failure -> _state.update { it.copy(saving = false, error = r.error) }
            }
        }
    }
}

// ---------------------------------------------------------------- one service's booking settings

data class BookingSettingsState(val loading: Boolean = true, val settings: BookingSettings = BookingSettings(), val saving: Boolean = false, val saved: Boolean = false, val error: AppError? = null)

@HiltViewModel
class BookingSettingsViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: StoresRepository) : ViewModel() {
    private val productId = savedStateHandle.toRoute<BookingSettingsRoute>().productId
    private val _state = MutableStateFlow(BookingSettingsState())
    val state: StateFlow<BookingSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = repo.bookingSettings(productId)) {
                is Outcome.Success -> _state.update { it.copy(loading = false, settings = r.value) }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun edit(transform: BookingSettings.() -> BookingSettings) = _state.update { it.copy(settings = it.settings.transform(), saved = false, error = null) }

    fun save() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val r = repo.saveBookingSettings(productId, _state.value.settings)
            _state.update { it.copy(saving = false, saved = r is Outcome.Success, error = (r as? Outcome.Failure)?.error) }
        }
    }
}
