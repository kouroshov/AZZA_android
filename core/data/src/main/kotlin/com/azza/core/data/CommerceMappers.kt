package com.azza.core.data

import com.azza.core.model.AppNotification
import com.azza.core.model.Availability
import com.azza.core.model.Cart
import com.azza.core.model.CartLine
import com.azza.core.model.FavoriteItem
import com.azza.core.model.FavoriteUnavailable
import com.azza.core.model.Money
import com.azza.core.model.OrderLine
import com.azza.core.model.OrderSeller
import com.azza.core.model.OrderStatus
import com.azza.core.model.PlacedOrderDetail
import com.azza.core.model.PlacedOrderGroup
import com.azza.core.model.PlacedOrderSummary
import com.azza.core.model.ReceivedOrderDetail
import com.azza.core.model.ReceivedOrderSummary
import com.azza.core.model.SearchProduct
import com.azza.core.model.SearchStore
import com.azza.core.model.SearchUser
import com.azza.core.model.SellingContext
import com.azza.core.model.ShippingDetails
import com.azza.core.model.ShippingVisibility
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.model.CartDto
import com.azza.core.network.model.FavoriteListItemDto
import com.azza.core.network.model.NotificationDto
import com.azza.core.network.model.OrderContextDto
import com.azza.core.network.model.OrderLineDto
import com.azza.core.network.model.PlacedOrderDetailDto
import com.azza.core.network.model.PlacedOrderSummaryDto
import com.azza.core.network.model.ReceivedOrderDetailDto
import com.azza.core.network.model.ReceivedOrderSummaryDto
import com.azza.core.network.model.SearchProductDto
import com.azza.core.network.model.SearchStoreDto
import com.azza.core.network.model.SearchUserDto
import com.azza.core.network.model.ShippingDetailsDto

internal fun CartDto.toModel(media: MediaUrlResolver) = Cart(
    lines = items.map { item ->
        val p = item.product
        CartLine(
            productId = item.productId,
            title = p.title,
            quantity = item.quantity,
            unitPrice = Money(p.priceAmountMinor, p.currency),
            discountPercentage = p.discountPercentage,
            imageUrl = p.media.firstOrNull()?.let { media.resolve(it.url ?: it.storageKey) },
            available = p.status == "ACTIVE" && p.deletedAt == null,
        )
    },
)

private fun OrderLineDto.toModel(currency: String) = OrderLine(
    productId = productId,
    title = title,
    quantity = quantity,
    unitPrice = Money(unitPriceAmountMinor, currency),
    lineTotal = Money(lineTotalAmountMinor, currency),
)

private fun ShippingDetailsDto.toModel() = ShippingDetails(recipientName, phone, line1, city, postalCode)

private fun OrderContextDto.toModel() = SellingContext(isStore = type == "STORE", storeName = storeName)

internal fun PlacedOrderSummaryDto.toModel() = PlacedOrderSummary(
    orderId = orderId,
    reference = reference,
    placedAt = placedAt,
    status = OrderStatus.of(status),
    itemCount = itemCount,
    sellerCount = sellerCount,
    firstItemTitle = firstItemTitle,
    total = Money(totalAmountMinor, currency),
)

internal fun PlacedOrderDetailDto.toModel() = PlacedOrderDetail(
    orderId = orderId,
    reference = reference,
    placedAt = placedAt,
    status = OrderStatus.of(status),
    subtotal = Money(subtotalAmountMinor, currency),
    discount = Money(discountAmountMinor, currency),
    shippingCost = Money(shippingAmountMinor, currency),
    total = Money(totalAmountMinor, currency),
    shipping = shipping?.toModel(),
    groups = groups.map { group ->
        PlacedOrderGroup(
            status = OrderStatus.of(group.status),
            seller = OrderSeller(
                isStore = group.seller.type == "STORE",
                name = group.seller.storeName ?: group.seller.displayName ?: group.seller.username.orEmpty(),
                storeSlug = group.seller.storeSlug,
            ),
            subtotal = Money(group.subtotalAmountMinor, currency),
            lines = group.items.map { it.toModel(currency) },
        )
    },
)

internal fun ReceivedOrderSummaryDto.toModel() = ReceivedOrderSummary(
    sellerOrderId = sellerOrderId,
    reference = reference,
    placedAt = placedAt,
    status = OrderStatus.of(status),
    context = context.toModel(),
    itemCount = itemCount,
    firstItemTitle = firstItemTitle,
    sellerAmount = Money(sellerAmountMinor, currency),
)

internal fun ReceivedOrderDetailDto.toModel() = ReceivedOrderDetail(
    sellerOrderId = sellerOrderId,
    reference = reference,
    placedAt = placedAt,
    status = OrderStatus.of(status),
    context = context.toModel(),
    lines = items.map { it.toModel(currency) },
    subtotal = Money(subtotalAmountMinor, currency),
    platformFee = Money(platformFeeAmountMinor, currency),
    sellerAmount = Money(sellerAmountMinor, currency),
    shippingVisibility = runCatching { ShippingVisibility.valueOf(shippingStatus) }.getOrDefault(ShippingVisibility.NOT_RECORDED),
    shipping = shipping?.toModel(),
    canMarkShipped = canMarkShipped,
)

internal fun SearchProductDto.toModel(media: MediaUrlResolver) = SearchProduct(
    id = id,
    title = title,
    price = Money.fromMajor(price, currency),
    discountPercentage = discountPercentage,
    imageUrl = media.resolve(primaryImage),
    categoryName = categoryName,
    sellerName = sellerName,
    storeName = storeName,
    cityName = cityName,
    availability = runCatching { Availability.valueOf(availability) }.getOrDefault(Availability.UNKNOWN),
)

internal fun SearchUserDto.toModel(media: MediaUrlResolver) =
    SearchUser(id, username, displayName.ifBlank { username }, media.resolve(avatarUrl), isSeller, followerCount)

internal fun SearchStoreDto.toModel(media: MediaUrlResolver) = SearchStore(
    id = id,
    slug = slug,
    name = name,
    avatarUrl = media.resolve(avatarUrl),
    verified = verificationStatus == "VERIFIED",
    branchCount = branchCount,
    followerCount = followerCount,
)

internal fun FavoriteListItemDto.toModel(media: MediaUrlResolver) = FavoriteItem(
    productId = product.id,
    title = product.title,
    price = Money.fromMajor(product.price, product.currency),
    imageUrl = media.resolve(product.primaryImage),
    unavailable = product.unavailableReason?.let { runCatching { FavoriteUnavailable.valueOf(it) }.getOrNull() },
)

internal fun NotificationDto.toModel(media: MediaUrlResolver) = AppNotification(
    id = id,
    type = type,
    createdAt = createdAt,
    read = read,
    actorName = actorName,
    actorUsername = actorUsername,
    subjectTitle = subjectTitle,
    storeName = context?.storeName,
    productId = productId,
    productImageUrl = media.resolve(productImage),
    orderId = orderId,
    sellerOrderId = sellerOrderId,
)
