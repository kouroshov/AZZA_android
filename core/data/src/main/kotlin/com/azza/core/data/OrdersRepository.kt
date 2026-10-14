package com.azza.core.data

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
) {
    /**
     * Places an order from the server cart. apps/api prices every line,
     * applies the code, reserves stock and empties the cart in one
     * transaction; the app sends no prices at all.
     */
    suspend fun checkout(address: ShippingAddress, discountCode: String?): Outcome<CheckoutResult> {
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
                ),
            ).data
        }.map { CheckoutResult(it.id, it.reference, Money(it.totalAmountMinor, it.currency), OrderStatus.of(it.status)) }
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
        safeApiCall(json) { orders.placed(cursor).data }.map { Page(it.items.map { o -> o.toModel() }, it.nextCursor, it.hasMore) }

    suspend fun placedDetail(orderId: String): Outcome<PlacedOrderDetail> =
        safeApiCall(json) { orders.placedDetail(orderId).data }.map { it.toModel() }

    suspend fun received(cursor: String?): Outcome<Page<ReceivedOrderSummary>> =
        safeApiCall(json) { orders.received(cursor).data }.map { Page(it.items.map { o -> o.toModel() }, it.nextCursor, it.hasMore) }

    suspend fun receivedDetail(sellerOrderId: String): Outcome<ReceivedOrderDetail> =
        safeApiCall(json) { orders.receivedDetail(sellerOrderId).data }.map { it.toModel() }

    suspend fun markShipped(sellerOrderId: String): Outcome<Unit> =
        safeApiCall(json) { orders.markShipped(sellerOrderId) }.map { }
}
