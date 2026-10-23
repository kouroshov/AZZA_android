package com.azza.feature.product

import com.azza.core.data.BasketOptions
import com.azza.core.data.ChosenOption
import com.azza.core.data.AppealsRepository
import kotlinx.coroutines.flow.update
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.AuthRepository
import com.azza.core.data.ReportReason
import com.azza.core.data.ReportTarget
import com.azza.core.data.ReportsRepository
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
    /** ✅ (2026-10-23) «تمدید» / «نردبان» went through. */
    data object Renewed : ProductEvent
    data object Bumped : ProductEvent
}

@HiltViewModel
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val products: ProductRepository,
    private val cart: CartRepository,
    private val favorites: FavoritesRepository,
    private val auth: AuthRepository,
    private val reports: ReportsRepository,
    private val appealsRepository: AppealsRepository,
    private val basketOptions: BasketOptions,
    private val stores: com.azza.core.data.StoresRepository,
    private val discussionRepository: com.azza.core.data.DiscussionRepository,
    users: UserRepository,
) : ViewModel() {

    /** ✅ (2026-10-23) the viewer's id: their own comments get a delete button. */
    val myId: StateFlow<String?> = users.currentUser.map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    /** Reporting needs an account; a guest is sent to sign in, as for the basket. */
    suspend fun canReport(): Boolean {
        if (auth.isSignedIn.first()) return true
        _events.send(ProductEvent.SignInRequired)
        return false
    }

    /** ✅ (2026-10-23) «اطلاعات تماس»: null while not asked; signed-out callers are sent to sign in. */
    private val _contact = MutableStateFlow<com.azza.core.model.ContactDetails?>(null)
    val contact: StateFlow<com.azza.core.model.ContactDetails?> = _contact.asStateFlow()
    private val _contactLoading = MutableStateFlow(false)
    val contactLoading: StateFlow<Boolean> = _contactLoading.asStateFlow()

    fun revealContact() = viewModelScope.launch {
        if (!auth.isSignedIn.first()) return@launch _events.send(ProductEvent.SignInRequired)
        _contactLoading.value = true
        when (val r = products.contact(route.productId)) {
            is Outcome.Success -> _contact.value = r.value
            is Outcome.Failure -> _events.send(ProductEvent.Failed(r.error))
        }
        _contactLoading.value = false
    }

    fun closeContact() { _contact.value = null }

    suspend fun report(reason: ReportReason, details: String) =
        reports.report(ReportTarget.PRODUCT, route.productId, reason, details)

    fun load() {
        _state.value = ProductUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = fetch(route.source)) {
                is Outcome.Success -> ProductUiState.Ready(result.value).also {
                    loadAppeals(it.product)
                    loadSimilar(it.product)
                    loadDiscussion(it.product)
                }
                is Outcome.Failure -> ProductUiState.Failed(result.error)
            }
        }
    }

    /** Re-reads without a spinner — coming back from editing it, the page must show the new version. */
    fun refreshQuietly() = viewModelScope.launch {
        if (_state.value !is ProductUiState.Ready) return@launch
        (fetch(null) as? Outcome.Success)?.let { _state.value = ProductUiState.Ready(it.value) }
    }

    /**
     * The public page first. A listing taken down answers 404 to everyone —
     * its owner included — so a signed-in caller then asks for the owner's
     * view, which answers only if the listing is theirs (with why it is down).
     */
    private suspend fun fetch(source: String?): Outcome<ProductDetail> {
        val result = products.product(route.productId, source)
        val notFound = (result as? Outcome.Failure)?.error.let { it is AppError.Api && it.status == 404 }
        if (notFound && auth.isSignedIn.first()) {
            (products.ownProduct(route.productId) as? Outcome.Success)?.let { return it }
        }
        return result
    }

    // ---- ✅ (2026-10-23) reviews and comments ----

    private val _discussion = MutableStateFlow(DiscussionUi())
    val discussion: StateFlow<DiscussionUi> = _discussion.asStateFlow()

    private fun loadDiscussion(product: ProductDetail) {
        if (product.status != "ACTIVE" && product.status != "RESERVED" && product.status != "SOLD") return
        viewModelScope.launch {
            (discussionRepository.load(product.id) as? Outcome.Success)?.let { r -> _discussion.update { it.copy(discussion = r.value) } }
            if (auth.isSignedIn.first() && myId.value != product.sellerId) {
                (discussionRepository.reviewableOrderItem(product.id) as? Outcome.Success)?.let { r ->
                    _discussion.update { it.copy(reviewableOrderItem = r.value) }
                }
            }
        }
    }

    private fun reloadDiscussion() = viewModelScope.launch {
        (discussionRepository.load(route.productId) as? Outcome.Success)?.let { r -> _discussion.update { it.copy(discussion = r.value) } }
    }

    fun comment(body: String, parentId: String?) = viewModelScope.launch {
        if (!auth.isSignedIn.first()) return@launch _events.send(ProductEvent.SignInRequired)
        _discussion.update { it.copy(sending = true) }
        when (val r = discussionRepository.addComment(route.productId, body, parentId)) {
            is Outcome.Success -> reloadDiscussion()
            is Outcome.Failure -> _events.send(ProductEvent.Failed(r.error))
        }
        _discussion.update { it.copy(sending = false) }
    }

    fun deleteComment(id: String) = viewModelScope.launch {
        when (val r = discussionRepository.deleteComment(id)) {
            is Outcome.Success -> reloadDiscussion()
            is Outcome.Failure -> _events.send(ProductEvent.Failed(r.error))
        }
    }

    fun review(rating: Int, title: String, body: String) = viewModelScope.launch {
        val item = _discussion.value.reviewableOrderItem ?: return@launch
        _discussion.update { it.copy(sending = true) }
        when (val r = discussionRepository.addReview(route.productId, item, rating, title, body)) {
            is Outcome.Success -> {
                _discussion.update { it.copy(reviewSent = true, reviewableOrderItem = null) }
                reloadDiscussion()
            }
            is Outcome.Failure -> _events.send(ProductEvent.Failed(r.error))
        }
        _discussion.update { it.copy(sending = false) }
    }

    /** ✅ (2026-10-23) «آگهی‌های مشابه» — only under a live listing. */
    private val _similar = MutableStateFlow<List<ProductDetail>>(emptyList())
    val similar: StateFlow<List<ProductDetail>> = _similar.asStateFlow()

    private fun loadSimilar(product: ProductDetail) {
        if (product.status != "ACTIVE") return
        viewModelScope.launch { (products.similar(product.id) as? Outcome.Success)?.let { _similar.value = it.value } }
    }

    /** ✅ (2026-10-23) The owner's «تمدید» / «نردبان»; the page then shows the listing as it now is. */
    private val _lifecycleBusy = MutableStateFlow(false)
    val lifecycleBusy: StateFlow<Boolean> = _lifecycleBusy.asStateFlow()

    fun renew() = lifecycleAction(ProductEvent.Renewed) { products.renew(route.productId) }

    fun bump() = lifecycleAction(ProductEvent.Bumped) { products.bump(route.productId) }

    private fun lifecycleAction(done: ProductEvent, run: suspend () -> Outcome<ProductDetail>) = viewModelScope.launch {
        if (_lifecycleBusy.value) return@launch
        _lifecycleBusy.value = true
        when (val r = run()) {
            is Outcome.Success -> {
                (fetch(null) as? Outcome.Success)?.let { _state.value = ProductUiState.Ready(it.value) }
                _events.send(done)
            }
            is Outcome.Failure -> _events.send(ProductEvent.Failed(r.error))
        }
        _lifecycleBusy.value = false
    }

    private val _appeal = MutableStateFlow(AppealUi())
    val appeal: StateFlow<AppealUi> = _appeal.asStateFlow()

    private fun loadAppeals(product: ProductDetail) {
        if (product.hold?.appealable != true) return
        _appeal.value = AppealUi(loading = true)
        viewModelScope.launch {
            val appeals = (appealsRepository.forProduct(product.id) as? Outcome.Success)?.value.orEmpty()
            _appeal.value = AppealUi(appeals = appeals)
        }
    }

    fun appeal(message: String) {
        if (_appeal.value.sending || message.trim().length < APPEAL_MIN) return
        _appeal.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            when (val r = appealsRepository.appealProduct(route.productId, message)) {
                is Outcome.Success -> _appeal.update { it.copy(sending = false, appeals = listOf(r.value) + it.appeals) }
                is Outcome.Failure -> _appeal.update { it.copy(sending = false, error = r.error) }
            }
        }
    }

    fun toggleFavorite() = viewModelScope.launch {
        if (!auth.isSignedIn.first()) return@launch _events.send(ProductEvent.SignInRequired)
        val result = favorites.setFavorite(route.productId, favorite = !isFavorite.value)
        if (result is Outcome.Failure) _events.send(ProductEvent.Failed(result.error))
    }

    /** How many of each option ONE item takes (optionId → count); 0 = not chosen. */
    private val _options = MutableStateFlow<Map<String, Int>>(emptyMap())
    val options: StateFlow<Map<String, Int>> = _options.asStateFlow()

    fun setOption(optionId: String, count: Int) = _options.update { it + (optionId to count) }

    /** How many to add to the basket (the web's quantity card), capped by what is available. */
    private val _quantity = MutableStateFlow(1)
    val quantity: StateFlow<Int> = _quantity.asStateFlow()

    fun setQuantity(n: Int) {
        val max = (_state.value as? ProductUiState.Ready)?.product?.quantityAvailable ?: 99
        _quantity.value = n.coerceIn(1, max.coerceIn(1, 99))
    }

    /** The owner's view of a store listing's stock: each branch, held and reserved. */
    private val _branchStock = MutableStateFlow<List<Pair<com.azza.core.data.Branch, com.azza.core.data.StockLine>>?>(null)
    val branchStock: StateFlow<List<Pair<com.azza.core.data.Branch, com.azza.core.data.StockLine>>?> = _branchStock.asStateFlow()

    fun loadOwnerStock(product: ProductDetail) {
        val storeId = product.storeId ?: return
        if (_branchStock.value != null) return
        viewModelScope.launch { (stores.stockByBranch(storeId, product.id) as? Outcome.Success)?.let { _branchStock.value = it.value } }
    }

    fun addToBasket() = viewModelScope.launch {
        if (_adding.value) return@launch
        if (!auth.isSignedIn.first()) return@launch _events.send(ProductEvent.SignInRequired)
        _adding.value = true
        when (val result = cart.add(route.productId, _quantity.value)) {
            is Outcome.Success -> {
                val product = (_state.value as? ProductUiState.Ready)?.product
                val chosen = product?.options.orEmpty().mapNotNull { o ->
                    _options.value[o.id]?.takeIf { it > 0 }?.let { ChosenOption(o.id, o.name, it, o.extra) }
                }
                basketOptions.set(route.productId, chosen)
                _events.send(ProductEvent.AddedToBasket)
            }
            is Outcome.Failure -> _events.send(ProductEvent.Failed(result.error))
        }
        _adding.value = false
    }
}
