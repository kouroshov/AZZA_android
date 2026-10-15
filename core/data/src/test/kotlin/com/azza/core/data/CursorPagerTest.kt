package com.azza.core.data

import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.model.Page
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CursorPagerTest {

    @Test
    fun `pages append in order and a repeated row is dropped`() = runTest(UnconfinedTestDispatcher()) {
        val pages = mapOf(
            null to Page(listOf("a", "b"), nextCursor = "c1", hasMore = true),
            "c1" to Page(listOf("b", "c"), nextCursor = null, hasMore = false),
        )
        val pager = CursorPager<String>(backgroundScope, { it }) { cursor -> Outcome.Success(pages.getValue(cursor)) }

        pager.refresh()
        pager.loadMore()

        assertThat(pager.state.value.items).containsExactly("a", "b", "c").inOrder()
        assertThat(pager.state.value.hasMore).isFalse()
    }

    @Test
    fun `a failed later page keeps what is on screen`() = runTest(UnconfinedTestDispatcher()) {
        val pager = CursorPager<String>(backgroundScope, { it }) { cursor ->
            if (cursor == null) Outcome.Success(Page(listOf("a"), "c1", true)) else Outcome.Failure(AppError.Network)
        }

        pager.refresh()
        pager.loadMore()

        assertThat(pager.state.value.items).containsExactly("a")
        assertThat(pager.state.value.error).isNull()
        assertThat(pager.state.value.loadingMore).isFalse()
    }

    @Test
    fun `a failed first page is reported`() = runTest(UnconfinedTestDispatcher()) {
        val pager = CursorPager<String>(backgroundScope, { it }) { Outcome.Failure(AppError.Network) }

        pager.refresh()

        assertThat(pager.state.value.loadingFirst).isFalse()
        assertThat(pager.state.value.error).isEqualTo(AppError.Network)
    }
}
