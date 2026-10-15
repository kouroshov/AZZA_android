package com.azza.feature.profile

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
) : ViewModel() {

    /** The web's profile shows the owner's favourites; so does this page. */
    val favorites = CursorPager<FavoriteItem>(viewModelScope, { it.productId }) { favoritesRepository.page(it) }

    private var myId: String? = null

    /** The listings on my personal page (not my stores'), newest first. */
    val posts = CursorPager<PostTile>(viewModelScope, { it.id }) { cursor ->
        profiles.personalPosts(myId ?: return@CursorPager Outcome.Failure(AppError.Unauthenticated), cursor)
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
            }
            is Outcome.Failure -> ProfileUiState.Failed(result.error)
        }
    }

    fun logout() = viewModelScope.launch { auth.logout() }
}
