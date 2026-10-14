package com.azza.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.FeedRepository
import com.azza.core.model.ProductCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val items: List<ProductCard> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = true,
    val loadingFirstPage: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    /** Only shown full-screen when there is nothing on screen yet. */
    val error: AppError? = null,
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feed: FeedRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadFirstPage()
    }

    fun loadFirstPage(isRefresh: Boolean = false) {
        loadJob?.cancel()
        _state.update {
            if (isRefresh) it.copy(refreshing = true, error = null) else FeedUiState(loadingFirstPage = true)
        }
        loadJob = viewModelScope.launch {
            when (val result = feed.explore(cursor = null)) {
                is Outcome.Success -> _state.value = FeedUiState(
                    items = result.value.items,
                    nextCursor = result.value.nextCursor,
                    hasMore = result.value.hasMore,
                    loadingFirstPage = false,
                )
                is Outcome.Failure -> _state.update {
                    it.copy(loadingFirstPage = false, refreshing = false, error = result.error)
                }
            }
        }
    }

    /** Called as the list nears its end. A cursor page, never an offset: rows added meanwhile don't shift it. */
    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.loadingFirstPage || !current.hasMore || current.nextCursor == null) return
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch {
            when (val result = feed.explore(cursor = current.nextCursor)) {
                is Outcome.Success -> _state.update {
                    val seen = it.items.mapTo(HashSet()) { card -> card.id }
                    it.copy(
                        items = it.items + result.value.items.filterNot { card -> card.id in seen },
                        nextCursor = result.value.nextCursor,
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                    )
                }
                // Keep what is on screen; the next scroll retries.
                is Outcome.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}
