package com.azza.core.data

import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagedState<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = true,
    /** True until the first page has answered (either way). */
    val loadingFirst: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    /** The first page's failure; a later page's failure keeps what is shown. */
    val error: AppError? = null,
    /** Offline: the first page is the saved copy from this time (epoch ms); no further pages. */
    val savedAt: Long? = null,
)

/**
 * One keyset-paged list, the shape every apps/api list shares
 * (`items` + `nextCursor` + `hasMore`). A ViewModel owns one per list and
 * passes its viewModelScope.
 */
class CursorPager<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> String,
    private val load: suspend (cursor: String?) -> Outcome<Page<T>>,
) {
    private val _state = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = _state.asStateFlow()
    private var job: Job? = null

    fun refresh(showSpinner: Boolean = false) {
        job?.cancel()
        _state.update {
            if (showSpinner && it.items.isNotEmpty()) it.copy(refreshing = true, error = null) else PagedState()
        }
        job = scope.launch {
            when (val result = load(null)) {
                is Outcome.Success -> _state.value = PagedState(
                    items = result.value.items,
                    nextCursor = result.value.nextCursor,
                    // A saved copy cannot be continued: the next page needs the network.
                    hasMore = result.value.hasMore && result.value.savedAt == null,
                    loadingFirst = false,
                    savedAt = result.value.savedAt,
                )
                is Outcome.Failure -> _state.update { it.copy(loadingFirst = false, refreshing = false, error = result.error) }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingFirst || current.loadingMore || !current.hasMore || current.nextCursor == null) return
        _state.update { it.copy(loadingMore = true) }
        job = scope.launch {
            when (val result = load(current.nextCursor)) {
                is Outcome.Success -> _state.update { s ->
                    val seen = s.items.mapTo(HashSet(), key)
                    s.copy(
                        items = s.items + result.value.items.filterNot { key(it) in seen },
                        nextCursor = result.value.nextCursor,
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                    )
                }
                is Outcome.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** Local edit after a successful mutation (e.g. an item removed) without a reload. */
    fun edit(transform: (List<T>) -> List<T>) = _state.update { it.copy(items = transform(it.items)) }
}
