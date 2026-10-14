package com.azza.feature.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.ProductRepository
import com.azza.core.model.ProductDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProductUiState {
    data object Loading : ProductUiState
    data class Ready(val product: ProductDetail) : ProductUiState
    data class Failed(val error: AppError) : ProductUiState
}

@HiltViewModel
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val products: ProductRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ProductRoute>()

    private val _state = MutableStateFlow<ProductUiState>(ProductUiState.Loading)
    val state: StateFlow<ProductUiState> = _state.asStateFlow()

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
}
