package com.azza.core.data

import com.azza.core.model.SellingContexts
import com.azza.core.model.ContextFilter
import com.azza.core.network.model.CheckoutLineDto
import com.azza.core.network.model.CheckoutLineOptionDto
import com.azza.core.network.model.OrdersPageDto
import com.azza.core.network.model.PlacedOrderSummaryDto
import com.azza.core.network.model.ReceivedOrderSummaryDto
import kotlinx.serialization.KSerializer
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.model.CheckoutResult
import com.azza.core.model.Money
import com.azza.core.model.OrderStatus
import com.azza.core.model.Page
import com.azza.core.model.PlacedOrderDetail
import com.azza.core.model.PlacedOrderSummary
import com.azza.core.model.ReceivedOrderDetail
import com.azza.core.model.ReceivedOrderSummary
import com.azza.core.model.ShippingAddress
import com.azza.core.network.api.DiscountsApi
import com.azza.core.network.api.OrdersApi
import com.azza.core.network.api.PaymentsApi
import com.azza.core.network.model.CheckoutAddressDto
import com.azza.core.network.model.CheckoutRequest
import com.azza.core.network.model.PaymentRequest
import com.azza.core.network.model.ValidateDiscountRequest
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class OrdersRepository @Inject constructor(
    private val orders: OrdersApi,
    private val payments: PaymentsApi,
    private val discounts: DiscountsApi,
    private val cart: CartRepository,
    private val json: Json,
    private val offline: OfflineCache,
    private val basketOptions: BasketOptions,
) {
    /**
     * Places an order from the server cart. apps/api prices every line,
     * applies the code, reserves stock and empties the cart in one
     * transaction; the app sends no prices at all.
     */
    suspend fun checkout(address: ShippingAddress, discountCode: String?): Outcome<CheckoutResult> {
        // Options chosen on the phone (BasketOptions) can only reach apps/api on explicit lines;
        // without any, the server cart is used as before.
        // The basket as the server has it now — not the copy on screen, which may be stale.
        cart.refresh()
        val lines = cart.cart.value?.lines.orEmpty().filter { it.available }
        val chosen = basketOptions.chosen.value
        val items = if (lines.any { chosen[it.productId].orEmpty().isNotEmpty() }) {
            lines.map { line ->
                CheckoutLineDto(
                    productId = line.productId,
                    quantity = line.quantity,
                    options = chosen[line.productId]?.map { CheckoutLineOptionDto(it.optionId, it.perItem * line.quantity) }?.ifEmpty { null },
                )
            }
        } else {
            null
        }
        val result = safeApiCall(json) {
            orders.checkout(
                CheckoutRequest(
                    shippingAddress = CheckoutAddressDto(
                        recipientName = address.recipientName.trim(),
                        phone = address.phone?.trim()?.ifEmpty { null },
                        line1 = address.line1.trim(),
                        postalCode = address.postalCode?.trim()?.ifEmpty { null },
                    ),
                    discountCode = discountCode?.trim()?.uppercase()?.ifEmpty { null },
                    items = items,
                ),
            ).data
        }.map { CheckoutResult(it.id, it.reference, Money(it.totalAmountMinor, it.currency), OrderStatus.of(it.status)) }
        if (result is Outcome.Success) {
            // Explicit lines leave the server cart alone: empty it here, as a cart checkout would have.
            if (items != null) lines.forEach { cart.remove(it.productId) }
            basketOptions.clear()
        }
        // Success consumed the cart on the server; a failure may have changed nothing — re-read either way.
        cart.refresh()
        return result
    }

    /** How much a code takes off this subtotal — a preview; checkout decides for real. */
    suspend fun previewDiscount(code: String, subtotal: Money): Outcome<Money> =
        safeApiCall(json) {
            discounts.validate(ValidateDiscountRequest(code.trim().uppercase(), subtotal.amountMinor / Money.MINOR_PER_MAJOR.toDouble())).data
        }.map { Money.fromMajor(it.discountAmount, subtotal.currency) }

    /** The web's "Pay now (test)". Only works while apps/api has test payments switched on. */
    suspend fun payWithTestPayment(orderId: String): Outcome<Unit> =
        safeApiCall(json) { payments.simulate(PaymentRequest(orderId)) }.map { }

    suspend fun placed(cursor: String?): Outcome<Page<PlacedOrderSummary>> =
        firstPageSaved("orders-placed", cursor, OrdersPageDto.serializer(PlacedOrderSummaryDto.serializer())) { safeApiCall(json) { orders.placed(cursor).data } }
            .map { (p, savedAt) -> Page(p.items.map { o -> o.toModel() }, p.nextCursor, p.hasMore, savedAt) }

    suspend fun placedDetail(orderId: String): Outcome<PlacedOrderDetail> =
        safeApiCall(json) { orders.placedDetail(orderId).data }.map { it.toModel() }

    suspend fun received(cursor: String?, filter: ContextFilter = ContextFilter.ALL): Outcome<Page<ReceivedOrderSummary>> {
        val fetch = suspend { safeApiCall(json) { orders.received(cursor, context = filter.context.takeIf { it != "all" }, storeId = filter.storeId).data } }
        // Only the unfiltered list is kept for offline use.
        val result = if (filter == ContextFilter.ALL) firstPageSaved("orders-received", cursor, OrdersPageDto.serializer(ReceivedOrderSummaryDto.serializer()), fetch) else fetch().map { Fresh(it) }
        return result.map { (p, savedAt) -> Page(p.items.map { o -> o.toModel() }, p.nextCursor, p.hasMore, savedAt) }
    }

    suspend fun receivedContexts(): Outcome<SellingContexts> = safeApiCall(json) { orders.receivedContexts().data }.map { c ->
        SellingContexts(c.hasPersonalOrders, c.stores.map { it.storeId to it.storeName })
    }

    private suspend fun <T> firstPageSaved(key: String, cursor: String?, serializer: KSerializer<T>, fetch: suspend () -> Outcome<T>): Outcome<Fresh<T>> =
        if (cursor == null) offline.load(key, personal = true, serializer, fetch = fetch) else fetch().map { Fresh(it) }

    suspend fun receivedDetail(sellerOrderId: String): Outcome<ReceivedOrderDetail> =
        safeApiCall(json) { orders.receivedDetail(sellerOrderId).data }.map { it.toModel() }

    suspend fun markShipped(sellerOrderId: String): Outcome<Unit> =
        safeApiCall(json) { orders.markShipped(sellerOrderId) }.map { }
}
