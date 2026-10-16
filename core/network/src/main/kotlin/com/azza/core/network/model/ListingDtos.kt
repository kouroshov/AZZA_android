package com.azza.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------- categories (GET /categories, /categories/search) ----------

@Serializable
data class CategoryNodeDto(
    val id: String,
    val slug: String,
    val name: LocalizedNameDto,
    val parentId: String? = null,
    val allowedOfferingKinds: List<String> = emptyList(),
    val defaultOfferingKind: String? = null,
    val children: List<CategoryNodeDto> = emptyList(),
)

@Serializable
data class CategoryHitDto(
    val id: String,
    val slug: String,
    val name: LocalizedNameDto,
    val path: List<CategoryPathDto> = emptyList(),
)

@Serializable
data class CategoryPathDto(val id: String, val name: LocalizedNameDto)

// ---------- occupations (taxonomy): "what do you do?" and the categories it suggests ----------

@Serializable
data class OccupationDto(val id: String, val code: String = "", val name: LocalizedNameDto)

@Serializable
data class SuggestedCategoryDto(val id: String, val name: LocalizedNameDto, val path: List<CategoryPathDto> = emptyList())

@Serializable
data class OccupationSuggestionDto(val category: SuggestedCategoryDto, val offeringKind: String = "GOOD", val weight: Double = 0.0)

// ---------- schema (GET /taxonomy/categories/:id/schema) ----------

@Serializable
data class CategorySchemaDto(
    val categoryId: String,
    val offeringKind: String,
    val allowedKinds: List<String> = emptyList(),
    val defaultKind: String? = null,
    val fields: List<SchemaFieldDto> = emptyList(),
)

/** `fa` may be null on a field label — the web falls back to `en`. */
@Serializable
data class NullableLocalizedDto(val en: String = "", val fa: String? = null)

@Serializable
data class SchemaOptionDto(val value: String, val label: NullableLocalizedDto)

@Serializable
data class SchemaFieldDto(
    val slug: String,
    val label: NullableLocalizedDto,
    val help: NullableLocalizedDto? = null,
    val dataType: String,
    val unit: String? = null,
    val required: Boolean = false,
    val min: Double? = null,
    val max: Double? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val options: List<SchemaOptionDto>? = null,
    /** The condition AST, parsed by the data layer (see SchemaRules). */
    val visibleWhen: JsonElement? = null,
    val requiredWhen: JsonElement? = null,
    val group: String? = null,
)

// ---------- publishing (PATCH /products/:id/status) ----------

@Serializable
data class ProductStatusRequest(val status: String)

// ---------- stores and branches ----------

@Serializable
data class BranchDto(val id: String, val name: String = "", val status: String = "ACTIVE", val createdAt: String = "")

@Serializable
data class SetQuantityRequest(val quantity: Int)
