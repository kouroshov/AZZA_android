package com.azza.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.common.Outcome
import com.azza.core.data.CursorPager
import com.azza.core.data.FeedRepository
import com.azza.core.data.GeographyRepository
import com.azza.core.data.SearchRepository
import com.azza.core.model.Area
import com.azza.core.model.Page
import com.azza.core.model.ProductCard
import com.azza.core.model.SearchProduct
import com.azza.core.model.SearchSort
import com.azza.core.model.SearchStore
import com.azza.core.model.SearchUser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchTab { PRODUCTS, USERS, STORES }

/** The web's Explore scopes: everywhere, or one city. */
enum class ExploreScope { GLOBAL, CITY }

/** apps/api refuses a one-letter query (VALIDATION_FAILED "q must be at least 2 characters"). */
const val MIN_QUERY_LENGTH = 2

/** The city picker: provinces first, then the chosen province's cities. */
data class CityPickerState(
    val open: Boolean = false,
    val provinces: List<Area> = emptyList(),
    val province: Area? = null,
    val cities: List<Area> = emptyList(),
    val loading: Boolean = false,
)

/**
 * The Explore tab, as on the web: one page that is a feed (Global or a
 * City) until something is typed, and search results once it is.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
    private val feed: FeedRepository,
    private val geography: GeographyRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tab = MutableStateFlow(SearchTab.PRODUCTS)
    val tab: StateFlow<SearchTab> = _tab.asStateFlow()

    private val _sort = MutableStateFlow(SearchSort.RELEVANCE)
    val sort: StateFlow<SearchSort> = _sort.asStateFlow()

    /** The query the lists are showing results for (debounced, trimmed, long enough). */
    private val _active = MutableStateFlow("")
    val activeQuery: StateFlow<String> = _active.asStateFlow()

    private val _scope = MutableStateFlow(ExploreScope.GLOBAL)
    val scope: StateFlow<ExploreScope> = _scope.asStateFlow()

    val city: StateFlow<Area?> = geography.chosenCity.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _picker = MutableStateFlow(CityPickerState())
    val picker: StateFlow<CityPickerState> = _picker.asStateFlow()

    val products = CursorPager<SearchProduct>(viewModelScope, { it.id }) { search.products(_active.value, _sort.value, it) }
    val users = CursorPager<SearchUser>(viewModelScope, { it.id }) { search.users(_active.value, it) }
    val stores = CursorPager<SearchStore>(viewModelScope, { it.id }) { search.stores(_active.value, it) }

    val globalFeed = CursorPager<ProductCard>(viewModelScope, { it.id }) { feed.explore(it) }
    val cityFeed = CursorPager<ProductCard>(viewModelScope, { it.id }) { cursor ->
        val chosen = city.value ?: return@CursorPager Outcome.Success(Page(emptyList(), null, false))
        feed.cities(listOf(chosen.id), cursor)
    }

    init {
        globalFeed.refresh()
        viewModelScope.launch {
            // One request per pause in typing, not per keystroke.
            combine(_query.debounce(300), _tab, _sort) { q, tab, sort -> Triple(q.trim(), tab, sort) }
                .distinctUntilChanged()
                .collect { (q, tab, _) ->
                    _active.value = q
                    if (q.length >= MIN_QUERY_LENGTH) pagerFor(tab).refresh()
                }
        }
        viewModelScope.launch {
            city.collect { if (it != null) cityFeed.refresh() }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onTab(tab: SearchTab) {
        _tab.value = tab
    }

    fun onSort(sort: SearchSort) {
        _sort.value = sort
    }

    fun onScope(scope: ExploreScope) {
        _scope.value = scope
        if (scope == ExploreScope.CITY && city.value == null) openPicker()
    }

    fun openPicker() {
        _picker.value = CityPickerState(open = true, loading = true)
        viewModelScope.launch {
            val provinces = (geography.provinces() as? Outcome.Success)?.value.orEmpty()
            _picker.value = _picker.value.copy(provinces = provinces, loading = false)
        }
    }

    fun pickProvince(province: Area) {
        _picker.value = _picker.value.copy(province = province, cities = emptyList(), loading = true)
        viewModelScope.launch {
            val cities = (geography.cities(province.id) as? Outcome.Success)?.value.orEmpty()
            _picker.value = _picker.value.copy(cities = cities, loading = false)
        }
    }

    fun backToProvinces() {
        _picker.value = _picker.value.copy(province = null, cities = emptyList())
    }

    fun pickCity(city: Area) = viewModelScope.launch {
        geography.chooseCity(city)
        _scope.value = ExploreScope.CITY
        _picker.value = CityPickerState()
    }

    fun closePicker() {
        _picker.value = CityPickerState()
        if (city.value == null) _scope.value = ExploreScope.GLOBAL
    }

    private fun pagerFor(tab: SearchTab): CursorPager<*> = when (tab) {
        SearchTab.PRODUCTS -> products
        SearchTab.USERS -> users
        SearchTab.STORES -> stores
    }
}
