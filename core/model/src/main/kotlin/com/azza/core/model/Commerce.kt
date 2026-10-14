package com.azza.core.model

/** What a listing is (apps/api `OfferingKind`). Only a GOOD goes through the basket. */
enum class OfferingKind { GOOD, SERVICE, BOOKABLE, RENTAL, DIGITAL, ACCESS, UNKNOWN }

data class CartLine(
    val productId: String,
    val title: String,
    val quantity: Int,
    /** Price per unit before the product's own discount. */
    val unitPrice: Money,
    val discountPercentage: Double?,
    val imageUrl: String?,
    /** False when the listing was unpublished or deleted since it was added. */
    val available: Boolean,
)

data class Cart(val lines: List<CartLine>) {
    val itemCount: Int get() = lines.sumOf { it.quantity }
    val isEmpty: Boolean get() = lines.isEmpty()
}

enum class OrderStatus {
    PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, COMPLETED, PARTIALLY_FULFILLED, CANCELLED, REFUNDED, FAILED, UNKNOWN;

    companion object {
        fun of(raw: String): OrderStatus = runCatching { valueOf(raw) }.getOrDefault(UNKNOWN)
    }
}

data class ShippingAddress(
    val recipientName: String,
    val phone: String?,
    val line1: String,
    val postalCode: String?,
)

data class CheckoutResult(val orderId: String, val reference: String, val total: Money, val status: OrderStatus)

data class OrderLine(
    val productId: String,
    val title: String,
    val quantity: Int,
    val unitPrice: Money,
    val lineTotal: Money,
)

data class PlacedOrderSummary(
    val orderId: String,
    val reference: String,
    val placedAt: String,
    val status: OrderStatus,
    val itemCount: Int,
    val sellerCount: Int,
    val firstItemTitle: String,
    val total: Money,
)

data class OrderSeller(val isStore: Boolean, val name: String, val storeSlug: String?)

data class PlacedOrderGroup(val status: OrderStatus, val seller: OrderSeller, val subtotal: Money, val lines: List<OrderLine>)

data class PlacedOrderDetail(
    val orderId: String,
    val reference: String,
    val placedAt: String,
    val status: OrderStatus,
    val subtotal: Money,
    val discount: Money,
    val shippingCost: Money,
    val total: Money,
    val shipping: ShippingDetails?,
    val groups: List<PlacedOrderGroup>,
)

data class ShippingDetails(
    val recipientName: String,
    val phone: String?,
    val line1: String,
    val city: String,
    val postalCode: String?,
)

/** "Personal profile" or one of the viewer's stores — the selling context of a received order. */
data class SellingContext(val isStore: Boolean, val storeName: String?)

data class ReceivedOrderSummary(
    val sellerOrderId: String,
    val reference: String,
    val placedAt: String,
    val status: OrderStatus,
    val context: SellingContext,
    val itemCount: Int,
    val firstItemTitle: String,
    val sellerAmount: Money,
)

enum class ShippingVisibility { VISIBLE, AWAITING_PAYMENT, NOT_RECORDED, NOT_APPLICABLE }

data class ReceivedOrderDetail(
    val sellerOrderId: String,
    val reference: String,
    val placedAt: String,
    val status: OrderStatus,
    val context: SellingContext,
    val lines: List<OrderLine>,
    val subtotal: Money,
    val platformFee: Money,
    val sellerAmount: Money,
    val shippingVisibility: ShippingVisibility,
    val shipping: ShippingDetails?,
    val canMarkShipped: Boolean,
)
