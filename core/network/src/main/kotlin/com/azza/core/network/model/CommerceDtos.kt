package com.azza.core.network.model

import kotlinx.serialization.Serializable

// ---------- cart (apps/api/src/cart) — a Prisma Cart row with items + product ----------

@Serializable
data class CartDto(val id: String, val items: List<CartItemDto> = emptyList())

@Serializable
data class CartItemDto(val productId: String, val quantity: Int, val product: CartProductDto)

@Serializable
data class CartProductDto(
    val id: String,
    val title: String,
    @Serializable(with = FlexibleLongSerializer::class)
    val priceAmountMinor: Long,
    val currency: String = "IRR",
    @Serializable(with = FlexibleDoubleSerializer::class)
    val discountPercentage: Double? = null,
    val status: String = "ACTIVE",
    val deletedAt: String? = null,
    val media: List<ProductMediaDto> = emptyList(),
)

@Serializable
data class AddCartItemRequest(val productId: String, val quantity: Int)

@Serializable
data class UpdateCartItemRequest(val quantity: Int)

// ---------- checkout (POST /orders/checkout) ----------

/** No `items`: apps/api then takes the lines from the caller's server cart and consumes it. */
@Serializable
data class CheckoutRequest(
    val shippingAddress: CheckoutAddressDto,
    val discountCode: String? = null,
    /** Present = these lines, not the server cart (which apps/api then leaves untouched). */
    val items: List<CheckoutLineDto>? = null,
)

@Serializable
data class CheckoutLineDto(val productId: String, val quantity: Int, val options: List<CheckoutLineOptionDto>? = null)

/** [quantity] is the TOTAL for the line, not per item (apps/api's CheckoutLineOptionDto). */
@Serializable
data class CheckoutLineOptionDto(val optionId: String, val quantity: Int)

@Serializable
data class CheckoutAddressDto(
    val recipientName: String,
    val phone: String? = null,
    val line1: String,
    val postalCode: String? = null,
)

@Serializable
data class CheckoutResultDto(
    val id: String,
    val reference: String,
    val status: String,
    val currency: String = "IRR",
    @Serializable(with = FlexibleLongSerializer::class)
    val totalAmountMinor: Long,
)

@Serializable
data class ValidateDiscountRequest(val code: String, /** Major units. */ val subtotal: Double)

@Serializable
data class ValidateDiscountResponse(/** Major units. */ val discountAmount: Double)

@Serializable
data class PaymentRequest(val orderId: String)

// ---------- orders (GET /orders/placed|received) — explicit DTOs, money as digit strings ----------

@Serializable
data class OrdersPageDto<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class OrderLineDto(
    val productId: String,
    val title: String,
    val quantity: Int,
    @Serializable(with = FlexibleLongSerializer::class)
    val unitPriceAmountMinor: Long,
    @Serializable(with = FlexibleLongSerializer::class)
    val lineTotalAmountMinor: Long,
)

@Serializable
data class ShippingDetailsDto(
    val recipientName: String,
    val phone: String? = null,
    val line1: String,
    val city: String = "",
    val postalCode: String? = null,
)

@Serializable
data class PlacedOrderSummaryDto(
    val orderId: String,
    val reference: String,
    val placedAt: String,
    val status: String,
    val currency: String = "IRR",
    val itemCount: Int = 0,
    val sellerCount: Int = 0,
    val firstItemTitle: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val totalAmountMinor: Long,
)

@Serializable
data class OrderSellerDto(
    val type: String,
    val username: String? = null,
    val displayName: String? = null,
    val storeName: String? = null,
    val storeSlug: String? = null,
)

@Serializable
data class PlacedOrderGroupDto(
    val status: String,
    val seller: OrderSellerDto,
    @Serializable(with = FlexibleLongSerializer::class)
    val subtotalAmountMinor: Long,
    val items: List<OrderLineDto> = emptyList(),
)

@Serializable
data class PlacedOrderDetailDto(
    val orderId: String,
    val reference: String,
    val placedAt: String,
    val status: String,
    val currency: String = "IRR",
    @Serializable(with = FlexibleLongSerializer::class) val subtotalAmountMinor: Long,
    @Serializable(with = FlexibleLongSerializer::class) val discountAmountMinor: Long,
    @Serializable(with = FlexibleLongSerializer::class) val shippingAmountMinor: Long,
    @Serializable(with = FlexibleLongSerializer::class) val totalAmountMinor: Long,
    val shipping: ShippingDetailsDto? = null,
    val groups: List<PlacedOrderGroupDto> = emptyList(),
)

@Serializable
data class OrderContextDto(val type: String, val storeName: String? = null)

@Serializable
data class ReceivedOrderSummaryDto(
    val sellerOrderId: String,
    val reference: String,
    val placedAt: String,
    val status: String,
    val context: OrderContextDto,
    val currency: String = "IRR",
    val itemCount: Int = 0,
    val firstItemTitle: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val sellerAmountMinor: Long,
)

@Serializable
data class ReceivedOrderDetailDto(
    val sellerOrderId: String,
    val reference: String,
    val placedAt: String,
    val status: String,
    val context: OrderContextDto,
    val currency: String = "IRR",
    val items: List<OrderLineDto> = emptyList(),
    @Serializable(with = FlexibleLongSerializer::class) val subtotalAmountMinor: Long,
    @Serializable(with = FlexibleLongSerializer::class) val platformFeeAmountMinor: Long,
    @Serializable(with = FlexibleLongSerializer::class) val sellerAmountMinor: Long,
    val shippingStatus: String = "NOT_RECORDED",
    val shipping: ShippingDetailsDto? = null,
    val canMarkShipped: Boolean = false,
)
