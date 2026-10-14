package com.azza.feature.basket

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.AuthRepository
import com.azza.core.data.CartRepository
import com.azza.core.data.CursorPager
import com.azza.core.data.OrdersRepository
import com.azza.core.model.Cart
import com.azza.core.model.CheckoutResult
import com.azza.core.model.Money
import com.azza.core.model.PlacedOrderDetail
import com.azza.core.model.PlacedOrderSummary
import com.azza.core.model.ReceivedOrderDetail
import com.azza.core.model.ReceivedOrderSummary
import com.azza.core.model.ShippingAddress
import com.azza.core.ui.discounted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the buyer pays per line before any promo code: the product's own discount applied. */
fun Cart.subtotal(): Money? {
    val lines = lines.filter { it.available }
    if (lines.isEmpty()) return null
    val currency = lines.first().unitPrice.currency
    return Money(lines.sumOf { it.unitPrice.discounted(it.discountPercentage).amountMinor * it.quantity }, currency)
}

@HiltViewModel
class BasketViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    orders: OrdersRepository,
    auth: AuthRepository,
) : ViewModel() {

    val signedIn: StateFlow<Boolean?> = auth.isSignedIn.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val cart: StateFlow<Cart?> = cartRepository.cart

    private val _busyLine = MutableStateFlow<String?>(null)
    val busyLine: StateFlow<String?> = _busyLine.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    val placed = CursorPager<PlacedOrderSummary>(viewModelScope, { it.orderId }) { orders.placed(it) }
    val received = CursorPager<ReceivedOrderSummary>(viewModelScope, { it.sellerOrderId }) { orders.received(it) }

    fun refreshCart() = viewModelScope.launch { cartRepository.refresh() }

    fun setQuantity(productId: String, quantity: Int) = mutate(productId) { cartRepository.setQuantity(productId, quantity) }

    fun remove(productId: String) = mutate(productId) { cartRepository.remove(productId) }

    fun clearError() = _error.update { null }

    private fun mutate(productId: String, block: suspend () -> Outcome<Cart>) {
        if (_busyLine.value != null) return
        _busyLine.value = productId
        viewModelScope.launch {
            val result = block()
            if (result is Outcome.Failure) _error.value = result.error
            _busyLine.value = null
        }
    }
}

data class CheckoutUiState(
    val fullName: String = "",
    val phone: String = "",
    val address: String = "",
    val postalCode: String = "",
    val promoInput: String = "",
    /** A code the server has accepted for this subtotal, with what it takes off. */
    val appliedCode: String? = null,
    val discount: Money? = null,
    val checkingCode: Boolean = false,
    val codeRejected: Boolean = false,
    val placing: Boolean = false,
    val error: AppError? = null,
    val placed: CheckoutResult? = null,
    val paying: Boolean = false,
    val paid: Boolean = false,
    val payError: AppError? = null,
) {
    val canPlace: Boolean get() = fullName.isNotBlank() && address.isNotBlank() && !placing
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    cartRepository: CartRepository,
    private val orders: OrdersRepository,
) : ViewModel() {

    val cart: StateFlow<Cart?> = cartRepository.cart

    private val _state = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    fun edit(transform: CheckoutUiState.() -> CheckoutUiState) = _state.update { it.transform().copy(error = null) }

    /** One code per order, as on the web; applying another replaces it. */
    fun applyCode() {
        val code = _state.value.promoInput.trim()
        val subtotal = cart.value?.subtotal() ?: return
        if (code.isEmpty() || _state.value.checkingCode) return
        _state.update { it.copy(checkingCode = true, codeRejected = false) }
        viewModelScope.launch {
            when (val result = orders.previewDiscount(code, subtotal)) {
                is Outcome.Success -> _state.update {
                    it.copy(checkingCode = false, appliedCode = code.uppercase(), discount = result.value, promoInput = "")
                }
                is Outcome.Failure -> _state.update { it.copy(checkingCode = false, codeRejected = true) }
            }
        }
    }

    fun removeCode() = _state.update { it.copy(appliedCode = null, discount = null) }

    fun placeOrder() {
        val s = _state.value
        if (!s.canPlace || s.placed != null) return
        _state.update { it.copy(placing = true, error = null) }
        viewModelScope.launch {
            val result = orders.checkout(
                ShippingAddress(s.fullName, s.phone, s.address, s.postalCode),
                s.appliedCode,
            )
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(placing = false, placed = result.value)
                    is Outcome.Failure -> it.copy(placing = false, error = result.error)
                }
            }
        }
    }

    fun payNow() {
        val order = _state.value.placed ?: return
        if (_state.value.paying || _state.value.paid) return
        _state.update { it.copy(paying = true, payError = null) }
        viewModelScope.launch {
            val result = orders.payWithTestPayment(order.orderId)
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(paying = false, paid = true)
                    is Outcome.Failure -> it.copy(paying = false, payError = result.error)
                }
            }
        }
    }
}

sealed interface DetailState<out T> {
    data object Loading : DetailState<Nothing>
    data class Ready<T>(val value: T) : DetailState<T>
    data class Failed(val error: AppError) : DetailState<Nothing>
}

@HiltViewModel
class PlacedOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orders: OrdersRepository,
) : ViewModel() {
    private val orderId = savedStateHandle.toRoute<PlacedOrderRoute>().orderId

    private val _state = MutableStateFlow<DetailState<PlacedOrderDetail>>(DetailState.Loading)
    val state: StateFlow<DetailState<PlacedOrderDetail>> = _state.asStateFlow()

    private val _paying = MutableStateFlow(false)
    val paying: StateFlow<Boolean> = _paying.asStateFlow()

    private val _payError = MutableStateFlow<AppError?>(null)
    val payError: StateFlow<AppError?> = _payError.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.value = when (val r = orders.placedDetail(orderId)) {
            is Outcome.Success -> DetailState.Ready(r.value)
            is Outcome.Failure -> DetailState.Failed(r.error)
        }
    }

    fun payNow() {
        if (_paying.value) return
        _paying.value = true
        _payError.value = null
        viewModelScope.launch {
            val r = orders.payWithTestPayment(orderId)
            if (r is Outcome.Failure) _payError.value = r.error else load()
            _paying.value = false
        }
    }
}

@HiltViewModel
class ReceivedOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orders: OrdersRepository,
) : ViewModel() {
    private val sellerOrderId = savedStateHandle.toRoute<ReceivedOrderRoute>().sellerOrderId

    private val _state = MutableStateFlow<DetailState<ReceivedOrderDetail>>(DetailState.Loading)
    val state: StateFlow<DetailState<ReceivedOrderDetail>> = _state.asStateFlow()

    private val _shipping = MutableStateFlow(false)
    val shipping: StateFlow<Boolean> = _shipping.asStateFlow()

    private val _shipError = MutableStateFlow<AppError?>(null)
    val shipError: StateFlow<AppError?> = _shipError.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.value = when (val r = orders.receivedDetail(sellerOrderId)) {
            is Outcome.Success -> DetailState.Ready(r.value)
            is Outcome.Failure -> DetailState.Failed(r.error)
        }
    }

    fun markShipped() {
        if (_shipping.value) return
        _shipping.value = true
        _shipError.value = null
        viewModelScope.launch {
            val r = orders.markShipped(sellerOrderId)
            if (r is Outcome.Failure) _shipError.value = r.error else load()
            _shipping.value = false
        }
    }
}
