package com.azza.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.data.AuthRepository
import com.azza.core.data.CursorPager
import com.azza.core.data.FeedRepository
import com.azza.core.data.FollowRepository
import com.azza.core.model.ProductCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class)
/** The Following tab: posts from the people and stores the signed-in person follows. */
@HiltViewModel
class FollowingFeedViewModel @Inject constructor(
    feed: FeedRepository,
    auth: AuthRepository,
    follow: FollowRepository,
) : ViewModel() {

    val pager = CursorPager<ProductCard>(viewModelScope, { it.id }) { feed.following(it) }

    /** Null until known. Signing in (or out) reloads the feed. */
    val signedIn: StateFlow<Boolean?> = auth.isSignedIn
        .distinctUntilChanged()
        .onEach { if (it) pager.refresh() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Following or unfollowing someone anywhere in the app changes this feed.
        combine(follow.followedUsers, follow.followedStores) { users, stores -> users to stores }
            .distinctUntilChanged()
            .drop(1)
            // The follow toggle is optimistic: wait for its request to land before re-reading the feed.
            .debounce(1_000)
            .onEach { if (signedIn.value == true) pager.refresh() }
            .launchIn(viewModelScope)
    }
}
