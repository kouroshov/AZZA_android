package com.azza.feature.profile

import com.azza.core.data.OwnerPostsActions
import com.azza.core.data.ListingRepository
import com.azza.core.data.FollowRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.AuthRepository
import com.azza.core.data.CursorPager
import com.azza.core.data.FavoritesRepository
import com.azza.core.data.ProfilesRepository
import com.azza.core.model.PostTile
import com.azza.core.model.FavoriteItem
import com.azza.core.data.UserRepository
import com.azza.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data object SignedOut : ProfileUiState
    data class Ready(val user: User) : ProfileUiState
    data class Failed(val error: AppError) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val favoritesRepository: FavoritesRepository,
    private val profiles: ProfilesRepository,
    follow: FollowRepository,
    listings: ListingRepository,
) : ViewModel() {

    /** The signed-in person as last read — an edit elsewhere (photo, name, username) shows here at once. */
    val me: StateFlow<User?> = users.currentUser

    /** Whom I follow: the in-memory set the Follow buttons already use. */
    val followingCount: StateFlow<Int> = combine(follow.followedUsers, follow.followedStores) { u, s -> u.size + s.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _followerCount = MutableStateFlow<Int?>(null)
    val followerCount: StateFlow<Int?> = _followerCount.asStateFlow()

    /** Live posts on the personal page, as every visitor counts them. */
    private val _postCount = MutableStateFlow<Int?>(null)
    val postCount: StateFlow<Int?> = _postCount.asStateFlow()

    /** Select / discount / delete on the grid (the web's OwnerPosts). */
    val owner = OwnerPostsActions(viewModelScope, listings) { refreshLists() }

    /** The web's profile shows the owner's favourites; so does this page. */
    val favorites = CursorPager<FavoriteItem>(viewModelScope, { it.productId }) { favoritesRepository.page(it) }

    private var myId: String? = null

    /**
     * The listings on my personal page (not my stores'), newest first — in
     * EVERY state: drafts, sold, and ones taken down are mine to see and
     * open (GET /products/mine), as the web's own profile shows them.
     */
    val posts = CursorPager<PostTile>(viewModelScope, { it.id }) { cursor ->
        if (myId == null) return@CursorPager Outcome.Failure(AppError.Unauthenticated)
        profiles.myPersonalPosts(cursor, _postSearch.value)
    }

    /** Words to find among my own posts (GET /products/mine?search=). */
    private val _postSearch = MutableStateFlow("")
    val postSearch: StateFlow<String> = _postSearch.asStateFlow()
    private var searchJob: kotlinx.coroutines.Job? = null

    fun searchPosts(text: String) {
        _postSearch.value = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            posts.refresh(showSpinner = true)
        }
    }

    /** Unfavourite from the list: the row goes once apps/api agrees. */
    fun unfavorite(productId: String) = viewModelScope.launch {
        if (favoritesRepository.setFavorite(productId, favorite = false) is Outcome.Success) {
            favorites.edit { items -> items.filterNot { it.productId == productId } }
        }
    }

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        // Follows the session: signing in or out anywhere re-renders this tab.
        viewModelScope.launch {
            auth.isSignedIn.collectLatest { signedIn -> if (signedIn) load() else _state.value = ProfileUiState.SignedOut }
        }
    }

    fun retry() = viewModelScope.launch { load() }

    private suspend fun load() {
        _state.value = ProfileUiState.Loading
        _state.value = when (val result = users.me()) {
            is Outcome.Success -> ProfileUiState.Ready(result.value).also {
                myId = result.value.id
                posts.refresh()
                favorites.refresh()
                loadFollowerCount(result.value.username)
            }
            is Outcome.Failure -> ProfileUiState.Failed(result.error)
        }
    }

    fun refreshLists() {
        if (_state.value !is ProfileUiState.Ready) return
        posts.refresh(showSpinner = true)
        favorites.refresh(showSpinner = true)
        me.value?.username?.let(::loadFollowerCount)
    }

    /** The public count, as every visitor sees it. */
    private fun loadFollowerCount(username: String) = viewModelScope.launch {
        (profiles.user(username) as? Outcome.Success)?.let {
            _followerCount.value = it.value.followerCount
            _postCount.value = it.value.postCount
        }
    }

    fun logout() = viewModelScope.launch { auth.logout() }
}
