package com.azza.core.data

import com.azza.core.common.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OwnerPostsState(
    val selecting: Boolean = false,
    val selected: Set<String> = emptySet(),
    /** "discount" or "delete" while a batch runs. */
    val busy: String? = null,
    /** The last batch: how many went through and how many did not. */
    val ok: Int? = null,
    val failed: Int = 0,
)

/**
 * The web's OwnerPosts tools for the owner's own grid: select posts, then
 * give them all one discount or delete them. There is no bulk endpoint, so
 * it is one request per post, in order, and the outcome is counted — never
 * claimed as all-or-nothing. apps/api re-checks ownership on every request.
 */
class OwnerPostsActions(
    private val scope: CoroutineScope,
    private val listings: ListingRepository,
    /** After a batch or a single delete: re-read the list. */
    private val onChanged: () -> Unit,
) {
    private val _state = MutableStateFlow(OwnerPostsState())
    val state: StateFlow<OwnerPostsState> = _state.asStateFlow()

    fun start(firstId: String? = null) = _state.update { OwnerPostsState(selecting = true, selected = setOfNotNull(firstId)) }

    fun stop() = _state.update { OwnerPostsState() }

    fun toggle(id: String) = _state.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id, ok = null) }

    fun applyDiscount(percent: Int) = run("discount") { listings.setDiscount(it, percent) }

    fun deleteSelected() = run("delete") { listings.delete(it) }

    fun deleteOne(id: String, onResult: (Boolean) -> Unit) = scope.launch {
        val ok = listings.delete(id) is Outcome.Success
        if (ok) onChanged()
        onResult(ok)
    }

    private fun run(kind: String, call: suspend (String) -> Outcome<Unit>) {
        val s = _state.value
        if (s.busy != null || s.selected.isEmpty()) return
        _state.update { it.copy(busy = kind, ok = null, failed = 0) }
        scope.launch {
            var ok = 0
            var failed = 0
            for (id in s.selected) if (call(id) is Outcome.Success) ok++ else failed++
            onChanged()
            // All went through: selection ends, as on the web; otherwise it stays, with the count.
            _state.update { if (failed == 0) OwnerPostsState(ok = ok) else it.copy(busy = null, ok = ok, failed = failed) }
        }
    }
}
