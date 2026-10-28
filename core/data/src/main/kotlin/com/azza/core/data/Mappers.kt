package com.azza.core.data

import com.azza.core.model.Spec
import com.azza.core.model.ProductOption
import com.azza.core.model.ListingHold
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
    usernameChangedAt = usernameChangedAt,
    phoneNumber = profile?.phoneNumber,
    province = profile?.province,
    city = profile?.city,
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
    priceNegotiable = priceNegotiable,
)

internal fun ProductDto.toModel(media: MediaUrlResolver) = ProductDetail(
    // A store's post is the store's; anything else, the account that posted it (as on the cards).
    author = store?.let { PostAuthor(storeId ?: it.slug, it.slug, media.resolve(it.avatarMediaKey), isStore = true, storeSlug = it.slug) }
        ?: seller?.let { PostAuthor(sellerId, it.username, media.resolve(it.profile?.avatarMediaKey), isStore = false, storeSlug = null) },
    authorName = store?.name?.ifBlank { null }
        ?: seller?.let { s -> listOfNotNull(s.profile?.firstName, s.profile?.lastName).joinToString(" ").trim().ifBlank { s.username } },
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
    status = status,
    hold = moderatedAt?.let { at -> moderationCause?.let { cause -> ListingHold(cause, moderationReason, at) } },
    options = options.map { ProductOption(it.id, it.name, Money(it.extraPriceAmountMinor, it.currency), it.maxQuantity.coerceIn(1, 10)) },
    specs = attributeValues.mapNotNull { it.toSpec() },
    // Free-form features: attributesCache less any key that is one of the category's own fields.
    features = attributeValues.map { it.categoryAttribute.slug }.toSet().let { typed ->
        (attributesCache as? kotlinx.serialization.json.JsonObject).orEmpty().mapNotNull { (k, v) ->
            if (k in typed) null else (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { k to it }
        }
    },
    barcode = barcode,
    stockQuantity = inventory?.quantity,
    stockReserved = inventory?.reservedQuantity,
    listingTypeName = listingType?.let { t ->
        com.azza.core.model.LocalizedText(t.nameFa?.takeIf { it.isNotBlank() } ?: t.nameEn, t.nameEn, t.nameCkb)
    },
    purchasable = purchasable,
    contactAvailable = contactAvailable,
    priceNegotiable = priceNegotiable,
    expiresAt = expiresAt,
    bumpedAt = bumpedAt,
    storeVerified = store?.verificationStatus == "VERIFIED",
    stockState = availability?.state,
    stockLeft = availability?.left,
    variants = variants.map {
        com.azza.core.model.ProductVariant(
            id = it.id,
            label = it.label,
            price = Money(it.priceAmountMinor, it.currency),
            discountPercentage = it.discountPercentage,
            inStock = it.availability != "OUT_OF_STOCK",
            current = it.current,
        )
    },
    variantLabel = variantLabel,
    dispatch = deliveryEstimate?.let { com.azza.core.model.DispatchEstimate(it.minDays, it.maxDays, it.from, it.to) },
    place = parcel?.let { p ->
        val lat = p.latitude ?: return@let null
        val lng = p.longitude ?: return@let null
        com.azza.core.model.ProductPlace(
            isBoundary = p.kind == "POLYGON",
            areaM2 = p.areaM2,
            perimeterM = p.perimeterM,
            west = p.minLng ?: lng,
            south = p.minLat ?: lat,
            east = p.maxLng ?: lng,
            north = p.maxLat ?: lat,
        )
    },
)

/** A typed value in words: an option's label, a number without a needless ".0", yes/no left to the UI. */
private fun com.azza.core.network.model.AttributeValueDto.toSpec(): Spec? {
    val a = categoryAttribute
    val labels: Map<String, Pair<String, String>> = (a.options as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { o ->
        val obj = o as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val value = (obj["value"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
        val label = obj["label"] as? kotlinx.serialization.json.JsonObject
        val en = (label?.get("en") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: value
        val fa = (label?.get("fa") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: en
        value to (en to fa)
    }.toMap()
    fun word(v: String) = labels[v] ?: (v to v)
    val (en, fa, yes) = when (a.dataType) {
        "BOOLEAN" -> Triple("", "", valueBoolean ?: return null)
        // ✅ (2026-10-23) an amount, stored in minor units: shown in major, grouped.
        "MONEY" -> {
            val minor = valueNumber ?: return null
            val money = com.azza.core.model.Money(Math.round(minor), "IRR")
            // In toman, with the word: the unit column would repeat it.
            return Spec(a.name.ifBlank { a.slug }, a.nameFa?.takeIf { it.isNotBlank() } ?: a.name, tomanLabel(money, "en"), tomanLabel(money, "fa"), null, null)
        }
        "NUMBER", "INTEGER", "DECIMAL" -> {
            val n = valueNumber ?: return null
            val text = if (n == Math.floor(n)) n.toLong().toString() else n.toString()
            Triple(text, text, null)
        }
        "MULTI_ENUM" -> {
            val values = (valueJson as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            if (values.isEmpty()) return null
            Triple(values.joinToString(", ") { word(it).first }, values.joinToString("، ") { word(it).second }, null)
        }
        "ENUM" -> {
            val v = valueText ?: return null
            Triple(word(v).first, word(v).second, null)
        }
        "DATE" -> {
            val d = valueDate?.take(10) ?: return null
            Triple(d, d, null)
        }
        else -> {
            val t = valueText?.takeIf { it.isNotBlank() } ?: return null
            Triple(t, t, null)
        }
    }
    val number = valueNumber.takeIf { a.dataType == "NUMBER" || a.dataType == "INTEGER" || a.dataType == "DECIMAL" }
    return Spec(a.name.ifBlank { a.slug }, a.nameFa?.takeIf { it.isNotBlank() } ?: a.name, en, fa, a.unit, yes, number)
}

/** Toman for a spec cell, in that language's digits (core:data cannot reach core:ui's formatter). */
private fun tomanLabel(money: com.azza.core.model.Money, language: String): String {
    val toman = (money.amountMinor + com.azza.core.model.Money.MINOR_PER_TOMAN / 2) / com.azza.core.model.Money.MINOR_PER_TOMAN
    val locale = if (language == "fa") java.util.Locale.forLanguageTag("fa-IR") else java.util.Locale.US
    return java.text.NumberFormat.getIntegerInstance(locale).format(toman) + if (language == "fa") " تومان" else " toman"
}
