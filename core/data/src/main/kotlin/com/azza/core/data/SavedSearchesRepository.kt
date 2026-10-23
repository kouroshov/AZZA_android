package com.azza.core.data

import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.model.SavedSearch
import com.azza.core.network.api.SavedSearchesApi
import com.azza.core.network.model.CreateSavedSearchRequest
import com.azza.core.network.model.SavedSearchDto
import com.azza.core.network.model.SavedSearchNotifyRequest
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * ✅ (2026-10-23) The web's /saved-searches: Explore's filters kept under a
 * name, with an in-app alert when a new listing matches. apps/api keeps
 * only the keys it knows (saved-search-match.ts) and at most 20 per person
 * (409 SAVED_SEARCH_LIMIT).
 */
@Singleton
class SavedSearchesRepository @Inject constructor(
    private val api: SavedSearchesApi,
    private val json: Json,
) {
    suspend fun list(): Outcome<List<SavedSearch>> = safeApiCall(json) { api.list().data }.map { list -> list.map { it.toModel() } }

    /** Prices are rial major units; [attrsJson] is the category-field filter object, or null. */
    suspend fun save(
        label: String,
        query: String?,
        categoryId: String?,
        listingType: String?,
        cityId: String?,
        minPrice: Long?,
        maxPrice: Long?,
        condition: String?,
        attrsJson: String?,
    ): Outcome<SavedSearch> {
        val filters = buildMap {
            query?.takeIf { it.isNotBlank() }?.let { put("q", JsonPrimitive(it)) }
            categoryId?.let { put("categoryId", JsonPrimitive(it)) }
            listingType?.let { put("listingType", JsonPrimitive(it)) }
            cityId?.let { put("cityId", JsonPrimitive(it)) }
            minPrice?.let { put("minPrice", JsonPrimitive(it)) }
            maxPrice?.let { put("maxPrice", JsonPrimitive(it)) }
            condition?.let { put("condition", JsonPrimitive(it)) }
            attrsJson?.let { text ->
                runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { put("attrs", it) }
            }
        }
        return safeApiCall(json) { api.create(CreateSavedSearchRequest(label.take(120), JsonObject(filters))).data }.map { it.toModel() }
    }

    suspend fun setNotify(id: String, notify: Boolean): Outcome<Unit> =
        safeApiCall(json) { api.setNotify(id, SavedSearchNotifyRequest(notify)) }.map { }

    suspend fun remove(id: String): Outcome<Unit> = safeApiCall(json) { api.remove(id) }.map { }

    private fun SavedSearchDto.toModel(): SavedSearch {
        fun str(key: String) = (filters[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        fun num(key: String) = (filters[key] as? JsonPrimitive)?.takeIf { !it.isString && it.booleanOrNull == null }?.longOrNull
        return SavedSearch(
            id = id,
            label = label,
            query = str("q"),
            categoryId = str("categoryId"),
            listingType = str("listingType"),
            cityId = str("cityId"),
            minPrice = num("minPrice"),
            maxPrice = num("maxPrice"),
            condition = str("condition"),
            attrsJson = (filters["attrs"] as? JsonObject)?.toString(),
            notify = notify,
            createdAt = createdAt,
        )
    }
}
