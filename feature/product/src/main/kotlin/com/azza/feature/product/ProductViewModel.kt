package com.azza.feature.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.AuthRepository
import com.azza.core.data.CartRepository
import com.azza.core.data.FavoritesRepository
import com.azza.core.data.ProductRepository
import com.azza.core.data.UserRepository
import com.azza.core.model.ProductDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProductUiState {
    data object Loading : ProductUiState
    data class Ready(val product: ProductDetail) : ProductUiState
    data class Failed(val error: AppError) : ProductUiState
}

sealed interface ProductEvent {
    data object SignInRequired : ProductEvent
    data object AddedToBasket : ProductEvent
    data class Failed(val error: AppError) : ProductEvent
}

@HiltViewModel
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val products: ProductRepository,
    private val cart: CartRepository,
    private val favorites: FavoritesRepository,
    private val auth: AuthRepository,
    users: UserRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ProductRoute>()

    private val _state = MutableStateFlow<ProductUiState>(ProductUiState.Loading)
    val state: StateFlow<ProductUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = favorites.favoriteIds
        .map { route.productId in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Your own listing: no buying it (checkout refuses SELF_PURCHASE_NOT_ALLOWED). */
    val isMine: StateFlow<Boolean> = combine(_state, users.currentUser) { s, me ->
        val product = (s as? ProductUiState.Ready)?.product
        product != null && me != null && product.sellerId == me.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()

    private val _events = Channel<ProductEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ProductUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = products.product(route.productId, route.source)) {
                is Outcome.Success -> ProductUiState.Ready(result.value)
                is Outcome.Failure -> ProductUiState.Failed(result.error)
            }
        }
    }

    /** Re-reads without a spinner — coming back from editing it, the page must show the new version. */
    fun refreshQuietly() = viewModelScope.launch {
        if (_state.value !is ProductUiState.Ready) return@launch
        (products.product(route.productId) as? Outcome.Success)?.let { _state.value = ProductUiState.Ready(it.value) }
    }

    fun toggleFavorite() = viewModelScope.launch {
        if (!auth.isSignedIn.first()) return@launch _events.send(ProductEvent.SignInRequired)
        val result = favorites.setFavorite(route.productId, favorite = !isFavorite.value)
        if (result is Outcome.Failure) _events.send(ProductEvent.Failed(result.error))
    }

    fun addToBasket() = viewModelScope.launch {
        if (_adding.value) return@launch
        if (!auth.isSignedIn.first()) return@launch _events.send(ProductEvent.SignInRequired)
        _adding.value = true
        when (val result = cart.add(route.productId)) {
            is Outcome.Success -> _events.send(ProductEvent.AddedToBasket)
            is Outcome.Failure -> _events.send(ProductEvent.Failed(result.error))
        }
        _adding.value = false
    }
}
