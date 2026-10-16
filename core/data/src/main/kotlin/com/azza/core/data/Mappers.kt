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
    status = status,
    hold = moderatedAt?.let { at -> moderationCause?.let { cause -> ListingHold(cause, moderationReason, at) } },
    options = options.map { ProductOption(it.id, it.name, Money(it.extraPriceAmountMinor, it.currency), it.maxQuantity.coerceIn(1, 10)) },
    specs = attributeValues.mapNotNull { it.toSpec() },
    features = if (attributesSchemaHash == null) {
        (attributesCache as? kotlinx.serialization.json.JsonObject).orEmpty().mapNotNull { (k, v) ->
            (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { k to it }
        }
    } else {
        emptyList()
    },
    barcode = barcode,
    stockQuantity = inventory?.quantity,
    stockReserved = inventory?.reservedQuantity,
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
    return Spec(a.name.ifBlank { a.slug }, a.nameFa?.takeIf { it.isNotBlank() } ?: a.name, en, fa, a.unit, yes)
}
