package com.azza.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.data.CursorPager
import com.azza.core.data.SearchRepository
import com.azza.core.model.SearchProduct
import com.azza.core.model.SearchSort
import com.azza.core.model.SearchStore
import com.azza.core.model.SearchUser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class SearchTab { PRODUCTS, USERS, STORES }

/** apps/api refuses a one-letter query (VALIDATION_FAILED "q must be at least 2 characters"). */
const val MIN_QUERY_LENGTH = 2

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
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

    val products = CursorPager<SearchProduct>(viewModelScope, { it.id }) { search.products(_active.value, _sort.value, it) }
    val users = CursorPager<SearchUser>(viewModelScope, { it.id }) { search.users(_active.value, it) }
    val stores = CursorPager<SearchStore>(viewModelScope, { it.id }) { search.stores(_active.value, it) }

    init {
        viewModelScope.launch {
            // One request per pause in typing, not per keystroke.
            combine(_query.debounce(300), _tab, _sort) { q, tab, sort -> Triple(q.trim(), tab, sort) }
                .distinctUntilChanged()
                .collect { (q, tab, _) ->
                    _active.value = q
                    if (q.length >= MIN_QUERY_LENGTH) pagerFor(tab).refresh()
                }
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

    private fun pagerFor(tab: SearchTab): CursorPager<*> = when (tab) {
        SearchTab.PRODUCTS -> products
        SearchTab.USERS -> users
        SearchTab.STORES -> stores
    }
}
