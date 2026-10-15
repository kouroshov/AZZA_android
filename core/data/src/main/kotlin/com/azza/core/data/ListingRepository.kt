package com.azza.core.data

import android.net.Uri
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.model.AttributeDataType
import com.azza.core.model.CategoryHit
import com.azza.core.model.CategoryNode
import com.azza.core.model.CategorySchema
import com.azza.core.model.EditableListing
import com.azza.core.model.ExistingPhoto
import com.azza.core.model.ItemCondition
import com.azza.core.model.Condition
import com.azza.core.model.ListingDraft
import com.azza.core.model.ListingSubmitResult
import com.azza.core.model.LocalizedText
import com.azza.core.model.OfferingKind
import com.azza.core.model.OwnStore
import com.azza.core.model.SchemaField
import com.azza.core.model.SchemaOption
import com.azza.core.network.api.CategoriesApi
import com.azza.core.network.api.ListingsApi
import com.azza.core.network.model.CategoryNodeDto
import com.azza.core.network.model.NullableLocalizedDto
import com.azza.core.network.model.ProductStatusRequest
import com.azza.core.network.model.SchemaFieldDto
import com.azza.core.network.model.SetQuantityRequest
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Posting a listing, the web's create flow (Dashboard/page.tsx) on apps/api:
 *  - no photos  → created and published in one step;
 *  - with photos → created HIDDEN, the photos upload in order (the first is
 *    the cover), then it is published. A post never reaches a feed without
 *    the photos its author attached. If a photo fails, nothing is live and
 *    submitting again CONTINUES the same hidden listing.
 *  - a store post's stock is placed at the store's main branch (the oldest
 *    ACTIVE one — where checkout reserves first), since apps/api's create
 *    ignores `quantity` for a store product.
 */
@Singleton
class ListingRepository @Inject constructor(
    private val categoriesApi: CategoriesApi,
    private val api: ListingsApi,
    private val photos: PhotoCompressor,
    private val products: com.azza.core.network.api.ProductsApi,
    private val mediaUrls: com.azza.core.network.MediaUrlResolver,
    private val json: Json,
) {
    private val treeLock = Mutex()
    private var tree: List<CategoryNode>? = null

    suspend fun categoryTree(): Outcome<List<CategoryNode>> = treeLock.withLock {
        tree?.let { return Outcome.Success(it) }
        safeApiCall(json) { categoriesApi.tree().data }.map { roots -> roots.map { it.toModel() } }
            .also { if (it is Outcome.Success) tree = it.value }
    }

    suspend fun searchCategories(query: String): Outcome<List<CategoryHit>> =
        safeApiCall(json) { categoriesApi.search(query.trim()).data }.map { hits ->
            hits.map { h -> CategoryHit(h.id, LocalizedText(h.name.fa, h.name.en), h.path.map { LocalizedText(it.name.fa, it.name.en) }) }
        }

    suspend fun schema(categoryId: String, kind: OfferingKind?): Outcome<CategorySchema> =
        safeApiCall(json) { categoriesApi.schema(categoryId, kind?.name).data }.map { s ->
            CategorySchema(
                categoryId = s.categoryId,
                offeringKind = kindOf(s.offeringKind),
                allowedKinds = s.allowedKinds.map(::kindOf),
                fields = s.fields.map { it.toModel() },
            )
        }

    /** A listing loaded into the edit form: its fields as the form holds them, and its photos. */
    suspend fun loadForEdit(productId: String): Outcome<EditableListing> =
        safeApiCall(json) { products.byId(productId).data }.map { p ->
            EditableListing(
                id = p.id,
                storeId = p.storeId,
                offeringKind = kindOf(p.offeringKind),
                categoryId = p.categoryId ?: p.category?.id.orEmpty(),
                categoryName = LocalizedText(p.category?.nameFa?.takeIf { it.isNotBlank() } ?: p.category?.nameEn.orEmpty(), p.category?.nameEn.orEmpty()),
                title = p.title,
                description = p.description.orEmpty(),
                price = majorText(p.priceAmountMinor),
                discountPercentage = p.discountPercentage?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }.orEmpty(),
                condition = runCatching { ItemCondition.valueOf(p.condition ?: "USED") }.getOrDefault(ItemCondition.USED),
                attributes = p.attributeValues.mapNotNull { v -> v.toFormValue()?.let { v.categoryAttribute.slug to it } }.toMap(),
                photos = p.media.filter { it.mediaType == "IMAGE" }.mapNotNull { m -> mediaUrls.resolve(m.url ?: m.storageKey)?.let { ExistingPhoto(m.id, it) } },
            )
        }

    /** Removes a saved photo at once, like the web's media manager. */
    suspend fun deletePhoto(productId: String, mediaId: String): Outcome<Unit> =
        safeApiCall(json) { api.deletePhoto(productId, mediaId) }

    /** A soft delete: apps/api's DELETED status (allowed from any state, even a moderation hold). */
    suspend fun delete(productId: String): Outcome<Unit> =
        safeApiCall(json) { api.setStatus(productId, ProductStatusRequest("DELETED")) }.map { }

    /**
     * Saves an edit: the fields, then any new photos. A listing's store and
     * stock are not changed here (stock is per branch, managed by the store).
     */
    suspend fun saveEdit(productId: String, draft: ListingDraft): Outcome<List<String>> {
        val r = safeApiCall(json) { api.update(productId, fieldsOf(draft)) }
        if (r is Outcome.Failure) return r
        return Outcome.Success(uploadPhotos(productId, draft.photos))
    }

    suspend fun myStores(): Outcome<List<OwnStore>> =
        safeApiCall(json) { api.myStores().data }.map { list -> list.map { OwnStore(it.id, it.name) } }

    /**
     * [continueId]: a hidden listing an earlier attempt created (photos
     * failed) — its fields are updated and only the photos not yet uploaded
     * are sent, instead of creating a second listing.
     */
    suspend fun submit(draft: ListingDraft, continueId: String?, alreadyUploaded: Set<String>): Outcome<ListingSubmitResult> {
        val hasPhotos = draft.photos.isNotEmpty()
        val fields = fieldsOf(draft)
        val productId: String = if (continueId != null) {
            when (val r = safeApiCall(json) { api.update(continueId, fields) }) {
                is Outcome.Failure -> return r
                is Outcome.Success -> continueId
            }
        } else {
            val body = JsonObject(
                fields + buildJsonObject {
                    draft.storeId?.let { put("storeId", it) }
                    // A personal listing is one item: no quantity is sent, so nothing can disagree with apps/api.
                    if (draft.storeId != null) draft.quantity.toLatinNumber()?.let { put("quantity", it.toInt()) }
                    put("publish", !hasPhotos)
                },
            )
            when (val r = safeApiCall(json) { api.create(body).data }) {
                is Outcome.Failure -> return r
                is Outcome.Success -> r.value.id
            }
        }

        var stockNotPlaced = false
        if (continueId == null && draft.storeId != null) {
            val quantity = draft.quantity.toLatinNumber()?.toInt()
            if (quantity != null) stockNotPlaced = !placeAtMainBranch(draft.storeId!!, productId, quantity)
        }

        val failed = uploadPhotos(productId, draft.photos.filterNot { it in alreadyUploaded })
        if (failed.isNotEmpty()) return Outcome.Success(ListingSubmitResult(productId, published = false, failed, stockNotPlaced))

        // Created hidden (or continuing a hidden one): publish now that every photo is in.
        if (hasPhotos || continueId != null) {
            val r = safeApiCall(json) { api.setStatus(productId, ProductStatusRequest("ACTIVE")) }
            if (r is Outcome.Failure) return r
        }
        return Outcome.Success(ListingSubmitResult(productId, published = true, emptyList(), stockNotPlaced))
    }

    /** Uploads in order; returns the photos that failed. */
    private suspend fun uploadPhotos(productId: String, uris: List<String>): List<String> {
        val failed = mutableListOf<String>()
        for (uri in uris) {
            val bytes = withContext(Dispatchers.IO) { photos.toJpeg(Uri.parse(uri)) }
            if (bytes == null) {
                failed += uri
                continue
            }
            val part = MultipartBody.Part.createFormData("file", "photo.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            if (safeApiCall(json) { api.uploadPhoto(productId, part) } is Outcome.Failure) failed += uri
        }
        return failed
    }

    private fun fieldsOf(draft: ListingDraft): JsonObject = buildJsonObject {
        put("title", draft.title.trim())
        put("description", draft.description.trim())
        put("categoryId", draft.categoryId)
        put("price", draft.price.toLatinNumber() ?: 0.0)
        put("condition", draft.condition.name)
        put("offeringKind", draft.offeringKind.name)
        // Always sent, blank = 0: on an edit a missing field is left as it was, so clearing it would not clear it.
        put("discountPercentage", draft.discountPercentage.toLatinNumber() ?: 0.0)
        put("attributeValues", JsonObject(draft.attributeValues.mapValues { (_, v) -> v.toJson() }))
    }

    private suspend fun placeAtMainBranch(storeId: String, productId: String, quantity: Int): Boolean {
        val branches = (safeApiCall(json) { api.branches(storeId).data } as? Outcome.Success)?.value ?: return false
        val main = branches.filter { it.status == "ACTIVE" }.minByOrNull { it.createdAt } ?: return false
        return safeApiCall(json) { api.setBranchQuantity(storeId, main.id, productId, SetQuantityRequest(quantity)) } is Outcome.Success
    }

    private fun CategoryNodeDto.toModel(): CategoryNode = CategoryNode(
        id = id,
        name = LocalizedText(name.fa, name.en),
        allowedKinds = allowedOfferingKinds.map(::kindOf),
        defaultKind = defaultOfferingKind?.let(::kindOf),
        children = children.map { it.toModel() },
    )

    private fun NullableLocalizedDto.toModel() = LocalizedText(fa?.takeIf { it.isNotBlank() } ?: en, en)

    private fun SchemaFieldDto.toModel() = SchemaField(
        slug = slug,
        label = label.toModel(),
        help = help?.toModel(),
        dataType = runCatching { AttributeDataType.valueOf(dataType) }.getOrDefault(AttributeDataType.UNKNOWN),
        unit = unit,
        required = required,
        min = min,
        max = max,
        maxLength = maxLength,
        pattern = pattern,
        options = options?.map { SchemaOption(it.value, it.label.toModel()) },
        visibleWhen = visibleWhen.toCondition(),
        requiredWhen = requiredWhen.toCondition(),
        group = group,
    )
}

private fun kindOf(raw: String): OfferingKind = runCatching { OfferingKind.valueOf(raw) }.getOrDefault(OfferingKind.UNKNOWN)

/** The schema's condition AST from its JSON (`{all}`, `{any}`, `{not}`, `{field, op, value}`). */
internal fun JsonElement?.toCondition(): Condition? {
    val obj = this as? JsonObject ?: return null
    obj["all"]?.let { all -> return Condition.All((all as JsonArray).mapNotNull { it.toCondition() }) }
    obj["any"]?.let { any -> return Condition.AnyOf((any as JsonArray).mapNotNull { it.toCondition() }) }
    obj["not"]?.let { not -> return not.toCondition()?.let { Condition.Not(it) } }
    val field = obj["field"]?.jsonPrimitive?.content ?: return null
    val op = obj["op"]?.jsonPrimitive?.content ?: return null
    return Condition.Leaf(field, op, obj["value"]?.toPlain())
}

private fun JsonElement.toPlain(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> if (isString) content else booleanOrNull ?: doubleOrNull ?: content
    is JsonArray -> map { it.toPlain() }
    is JsonObject -> null
}

private fun Any.toJson(): JsonElement = when (this) {
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(if (toDouble() == Math.floor(toDouble())) toDouble().toLong() else toDouble())
    is List<*> -> JsonArray(mapNotNull { it?.toJson() })
    else -> JsonPrimitive(toString())
}

/** A number typed with Persian or Arabic digits and separators, as the rules accept it; null when blank or not a number. */
private fun String.toLatinNumber(): Double? {
    val cleaned = com.azza.core.model.SchemaRules.toLatinDigits(trim()).replace(Regex("[,٬]"), "").replace('٫', '.')
    return cleaned.toDoubleOrNull()
}

/** Minor units as the price field shows them: "350", "349.5". */
private fun majorText(minor: Long): String =
    if (minor % 100 == 0L) (minor / 100).toString() else (minor / 100.0).toString()

/** A stored attribute value as the form holds it (String, Boolean or List<String>). */
private fun com.azza.core.network.model.AttributeValueDto.toFormValue(): Any? = when {
    valueJson is JsonArray -> (valueJson as JsonArray).mapNotNull { (it as? JsonPrimitive)?.content }
    valueBoolean != null -> valueBoolean
    valueNumber != null -> valueNumber!!.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }
    valueDate != null -> valueDate!!.take(10)
    valueText != null -> valueText
    else -> null
}
