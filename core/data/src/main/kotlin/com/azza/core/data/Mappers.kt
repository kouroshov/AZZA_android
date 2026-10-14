package com.azza.core.data

import com.azza.core.model.Availability
import com.azza.core.model.LocalizedText
import com.azza.core.model.Money
import com.azza.core.model.OfferingKind
import com.azza.core.model.PostAuthor
import com.azza.core.model.ProductCard
import com.azza.core.model.ProductDetail
import com.azza.core.model.User
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.model.FeedCardDto
import com.azza.core.network.model.MeDto
import com.azza.core.network.model.ProductDto

internal fun MeDto.toModel(media: MediaUrlResolver) = User(
    id = id,
    email = email,
    username = username,
    firstName = profile?.firstName.orEmpty(),
    lastName = profile?.lastName.orEmpty(),
    avatarUrl = media.resolve(profile?.avatarMediaKey),
    bio = profile?.bio,
)

/** Same rule as the web card: a store's post shows the store, anything else the account that posted it. */
internal fun FeedCardDto.toModel(media: MediaUrlResolver) = ProductCard(
    id = id,
    title = title,
    price = Money.fromMajor(price, currency),
    discountPercentage = discountPercentage,
    imageUrl = media.resolve(primaryImage),
    categoryName = LocalizedText(category.name.fa, category.name.en),
    author = store?.let {
        PostAuthor(id = it.id, handle = it.name, avatarUrl = media.resolve(it.avatarKey), isStore = true, storeSlug = it.slug)
    } ?: PostAuthor(
        id = postedBy.id,
        handle = postedBy.username,
        avatarUrl = media.resolve(postedBy.avatarKey),
        isStore = false,
        storeSlug = null,
    ),
    availability = runCatching { Availability.valueOf(availability) }.getOrDefault(Availability.UNKNOWN),
    favoriteCount = favoriteCount,
    viewCount = viewCount,
)

internal fun ProductDto.toModel(media: MediaUrlResolver) = ProductDetail(
    id = id,
    title = title,
    description = description,
    price = Money(priceAmountMinor, currency),
    discountPercentage = discountPercentage,
    imageUrls = this.media.filter { it.mediaType == "IMAGE" }.mapNotNull { media.resolve(it.url ?: it.storageKey) },
    sellerId = sellerId,
    storeId = storeId,
    // A store product's stock lives per branch (InventoryLocation), not here.
    quantityAvailable = inventory?.let { (it.quantity - it.reservedQuantity).coerceAtLeast(0) },
    offeringKind = runCatching { OfferingKind.valueOf(offeringKind) }.getOrDefault(OfferingKind.UNKNOWN),
    favoriteCount = favoriteCount,
)
