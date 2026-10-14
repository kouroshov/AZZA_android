package com.azza.core.data

import com.azza.core.common.ApplicationScope
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.datastore.SessionStore
import com.azza.core.model.Cart
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.CartApi
import com.azza.core.network.model.AddCartItemRequest
import com.azza.core.network.model.CartDto
import com.azza.core.network.model.UpdateCartItemRequest
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * The basket is apps/api's server cart (`/cart`), not a copy on the phone:
 * it follows the account to any device and survives a reinstall, and
 * checkout consumes it server-side. (The web still keeps its basket in
 * localStorage — moving it to `/cart` too would make the two share one
 * basket; see docs/ROADMAP.md.)
 *
 * The cart stores product + quantity only, never a price: every read
 * re-prices from the product's current price.
 */
@Singleton
class CartRepository @Inject constructor(
    private val api: CartApi,
    private val media: MediaUrlResolver,
    private val json: Json,
    sessionStore: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _cart = MutableStateFlow<Cart?>(null)

    /** Null while signed out or not loaded yet. Drives the basket badge. */
    val cart: StateFlow<Cart?> = _cart.asStateFlow()

    init {
        scope.launch {
            sessionStore.isSignedIn.collect { signedIn -> if (signedIn) refresh() else _cart.value = null }
        }
    }

    suspend fun refresh(): Outcome<Cart> = publish { api.get().data }

    suspend fun add(productId: String, quantity: Int = 1): Outcome<Cart> =
        publish { api.add(AddCartItemRequest(productId, quantity)).data }

    /** 0 removes the line. */
    suspend fun setQuantity(productId: String, quantity: Int): Outcome<Cart> =
        publish { api.update(productId, UpdateCartItemRequest(quantity.coerceAtLeast(0))).data }

    suspend fun remove(productId: String): Outcome<Cart> = publish { api.remove(productId).data }

    private suspend fun publish(call: suspend () -> CartDto): Outcome<Cart> =
        safeApiCall(json, call).map { it.toModel(media) }.also { if (it is Outcome.Success) _cart.value = it.value }
}
