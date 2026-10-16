package com.azza.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.common.Outcome
import com.azza.core.data.CursorPager
import com.azza.core.data.FeedRepository
import com.azza.core.data.GeographyRepository
import com.azza.core.data.ListingRepository
import com.azza.core.data.SearchRepository
import com.azza.core.model.Area
import com.azza.core.model.Availability
import com.azza.core.model.CategoryHit
import com.azza.core.model.CategoryNode
import com.azza.core.model.ItemCondition
import com.azza.core.model.LocalizedText
import com.azza.core.model.Page
import com.azza.core.model.PlaceHit
import com.azza.core.model.ProductCard
import com.azza.core.model.SearchFilters
import com.azza.core.model.SearchProduct
import com.azza.core.model.SearchSort
import com.azza.core.model.SearchStore
import com.azza.core.model.SearchUser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchTab { PRODUCTS, USERS, STORES }

/** The web's Explore scopes: everywhere, or the chosen cities. */
enum class ExploreScope { GLOBAL, CITY }

/** apps/api refuses a one-letter query (VALIDATION_FAILED "q must be at least 2 characters"). */
const val MIN_QUERY_LENGTH = 2

/** The web's "All Iran" entry: not an area, it means "no city filter". */
const val ALL_IRAN_ID = "__all_iran__"

/**
 * The city picker (the web's CitySelector): a staged selection that only
 * Confirm commits, browsed by province or found by name, with the last
 * confirmed picks offered again.
 */
data class CityPickerState(
    val open: Boolean = false,
    val staged: List<Area> = emptyList(),
    val recent: List<Area> = emptyList(),
    val provinces: List<Area> = emptyList(),
    val province: Area? = null,
    val cities: List<Area> = emptyList(),
    val query: String = "",
    val hits: List<PlaceHit> = emptyList(),
    val loading: Boolean = false,
)

/** The category chooser used by the filters and by "Browse categories". */
data class CategoryBrowserState(
    val open: Boolean = false,
    val roots: List<CategoryNode> = emptyList(),
    val trail: List<CategoryNode> = emptyList(),
    val query: String = "",
    val hits: List<CategoryHit> = emptyList(),
    val loading: Boolean = false,
)

/** What the result lists are showing: the debounced query and the filters it ran with. */
private data class SearchInput(val query: String, val tab: SearchTab, val sort: SearchSort, val filters: SearchFilters)

/**
 * The Explore tab, as on the web: one page that is a feed (Global or the
 * chosen cities) until something is typed or a filter is set, and search
 * results once it is. Filters without a query are the web's marketplace
 * (browse by category, price, condition).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
    private val feed: FeedRepository,
    private val geography: GeographyRepository,
    private val listings: ListingRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tab = MutableStateFlow(SearchTab.PRODUCTS)
    val tab: StateFlow<SearchTab> = _tab.asStateFlow()

    private val _sort = MutableStateFlow(SearchSort.RELEVANCE)
    val sort: StateFlow<SearchSort> = _sort.asStateFlow()

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    /** The query the lists are showing results for (debounced, trimmed). */
    private val _active = MutableStateFlow("")
    val activeQuery: StateFlow<String> = _active.asStateFlow()

    private val _scope = MutableStateFlow(ExploreScope.GLOBAL)
    val scope: StateFlow<ExploreScope> = _scope.asStateFlow()

    val cities: StateFlow<List<Area>> = geography.chosenCities.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _picker = MutableStateFlow(CityPickerState())
    val picker: StateFlow<CityPickerState> = _picker.asStateFlow()

    private val _categories = MutableStateFlow(CategoryBrowserState())
    val categories: StateFlow<CategoryBrowserState> = _categories.asStateFlow()

    val products = CursorPager<SearchProduct>(viewModelScope, { it.id }) {
        search.products(_active.value.takeIf { q -> q.length >= MIN_QUERY_LENGTH }.orEmpty(), _sort.value, it, _filters.value)
    }
    val users = CursorPager<SearchUser>(viewModelScope, { it.id }) { search.users(_active.value, it) }
    val stores = CursorPager<SearchStore>(viewModelScope, { it.id }) { search.stores(_active.value, it) }

    val globalFeed = CursorPager<ProductCard>(viewModelScope, { it.id }) { feed.explore(it) }
    val cityFeed = CursorPager<ProductCard>(viewModelScope, { it.id }) { cursor ->
        val chosen = cities.value.takeIf { it.isNotEmpty() } ?: return@CursorPager Outcome.Success(Page(emptyList(), null, false))
        feed.cities(chosen.map { it.id }, cursor)
    }

    private var placeJob: Job? = null
    private var categoryJob: Job? = null

    init {
        globalFeed.refresh()
        viewModelScope.launch {
            // One request per pause in typing, not per keystroke.
            combine(_query.debounce(300), _tab, _sort, _filters) { q, tab, sort, filters -> SearchInput(q.trim(), tab, sort, filters) }
                .distinctUntilChanged()
                .collect { input ->
                    _active.value = input.query
                    when {
                        input.query.length >= MIN_QUERY_LENGTH -> pagerFor(input.tab).refresh()
                        !input.filters.isEmpty -> products.refresh()
                    }
                }
        }
        viewModelScope.launch {
            // The stored choice arrives after the empty initial value.
            cities.drop(1).collect { if (it.isNotEmpty()) cityFeed.refresh() }
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

    fun applyFilters(filters: SearchFilters) {
        _filters.value = filters
        _tab.value = SearchTab.PRODUCTS
    }

    fun clearFilters() {
        _filters.value = SearchFilters()
    }

    fun setCondition(condition: ItemCondition?) = applyFilters(_filters.value.copy(condition = condition))
    fun setAvailability(availability: Availability?) = applyFilters(_filters.value.copy(availability = availability))

    // ---------- Explore scope and the city picker ----------

    fun onScope(scope: ExploreScope) {
        if (scope == ExploreScope.CITY && cities.value.isEmpty()) return openPicker()
        _scope.value = scope
    }

    fun openPicker() {
        _picker.value = CityPickerState(open = true, staged = cities.value, loading = true)
        viewModelScope.launch {
            val recent = geography.recentCities.first()
            val provinces = (geography.provinces() as? Outcome.Success)?.value.orEmpty()
            _picker.update { it.copy(provinces = provinces, recent = recent, loading = false) }
        }
    }

    fun openProvince(province: Area) {
        _picker.update { it.copy(province = province, cities = emptyList(), query = "", hits = emptyList(), loading = true) }
        viewModelScope.launch {
            val cities = (geography.cities(province.id) as? Outcome.Success)?.value.orEmpty()
            _picker.update { it.copy(cities = cities, loading = false) }
        }
    }

    fun backToProvinces() {
        _picker.update { it.copy(province = null, cities = emptyList()) }
    }

    fun toggleCity(city: Area) = _picker.update { s ->
        val staged = if (s.staged.any { it.id == city.id }) s.staged.filterNot { it.id == city.id } else s.staged + city
        // "All Iran" and a city cannot both hold: picking one clears the other.
        val cleaned = if (city.id == ALL_IRAN_ID) staged.filter { it.id == ALL_IRAN_ID } else staged.filterNot { it.id == ALL_IRAN_ID }
        s.copy(staged = cleaned)
    }

    fun clearStaged() = _picker.update { it.copy(staged = emptyList()) }

    fun searchPlaces(query: String) {
        _picker.update { it.copy(query = query) }
        placeJob?.cancel()
        if (query.trim().length < 2) return _picker.update { it.copy(hits = emptyList()) }
        placeJob = viewModelScope.launch {
            delay(300)
            val hits = (geography.search(query.trim()) as? Outcome.Success)?.value.orEmpty()
            _picker.update { it.copy(hits = hits) }
        }
    }

    fun confirmCities() = viewModelScope.launch {
        val picked = _picker.value.staged.filterNot { it.id == ALL_IRAN_ID }
        geography.chooseCities(picked)
        _scope.value = if (picked.isEmpty()) ExploreScope.GLOBAL else ExploreScope.CITY
        _picker.value = CityPickerState()
        if (picked.isNotEmpty()) cityFeed.refresh()
    }

    fun closePicker() {
        _picker.value = CityPickerState()
    }

    // ---------- the category browser ----------

    fun openCategories() {
        _categories.value = CategoryBrowserState(open = true, loading = true)
        viewModelScope.launch {
            val roots = (listings.categoryTree() as? Outcome.Success)?.value.orEmpty()
            _categories.update { it.copy(roots = roots, loading = false) }
        }
    }

    fun closeCategories() {
        _categories.value = CategoryBrowserState()
    }

    fun openCategory(node: CategoryNode) = _categories.update { it.copy(trail = it.trail + node) }

    fun categoryUp() = _categories.update { it.copy(trail = it.trail.dropLast(1)) }

    fun searchCategories(query: String) {
        _categories.update { it.copy(query = query) }
        categoryJob?.cancel()
        if (query.trim().length < 2) return _categories.update { it.copy(hits = emptyList()) }
        categoryJob = viewModelScope.launch {
            delay(250)
            val hits = (listings.searchCategories(query) as? Outcome.Success)?.value.orEmpty()
            _categories.update { it.copy(hits = hits) }
        }
    }

    /** Null = every category. */
    fun chooseCategory(id: String?, name: LocalizedText?) {
        _categories.value = CategoryBrowserState()
        applyFilters(_filters.value.copy(categoryId = id, categoryName = name))
    }

    private fun pagerFor(tab: SearchTab): CursorPager<*> = when (tab) {
        SearchTab.PRODUCTS -> products
        SearchTab.USERS -> users
        SearchTab.STORES -> stores
    }
}
