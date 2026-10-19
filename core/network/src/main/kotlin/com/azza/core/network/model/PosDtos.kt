package com.azza.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// apps/api POS-02. Money is a digit string of minor units both ways.

@Serializable
data class CatalogCodeDto(val id: String, val code: String, val symbology: String)

@Serializable
data class CatalogItemDto(
    val id: String,
    val name: String,
    @Serializable(with = FlexibleLongSerializer::class) val priceAmountMinor: Long,
    @Serializable(with = FlexibleLongSerializer::class) val salePriceAmountMinor: Long,
    val currency: String = "IRR",
    val visibility: String,
    val status: String,
    val imageUrl: String? = null,
    val codes: List<CatalogCodeDto> = emptyList(),
    val storeQty: Int = 0,
    val onlineQty: Int = 0,
    val reserved: Int = 0,
    val needsReview: Boolean = false,
    val updatedAt: String? = null,
)

/** `type` is PRODUCT or CODE; for a CODE, [productId] is the item it belonged to. */
@Serializable
data class CatalogTombstoneDto(val type: String, val id: String, val productId: String)

@Serializable
data class CatalogPageDto(
    val items: List<CatalogItemDto> = emptyList(),
    val tombstones: List<CatalogTombstoneDto> = emptyList(),
    val nextCursor: String,
    val hasMore: Boolean,
)

@Serializable
data class SyncSaleItemDto(val productId: String, val quantity: Int, val unitPriceAmountMinor: String)

@Serializable
data class SyncSaleDto(
    val id: String,
    val deviceId: String,
    val paymentMethod: String,
    val totalAmountMinor: String,
    /** ISO 8601, device time. */
    val soldAt: String,
    val items: List<SyncSaleItemDto>,
)

@Serializable
data class SyncSalesRequest(val sales: List<SyncSaleDto>)

/** `result`: ACCEPTED | DUPLICATE | REJECTED (+ `reason`). */
@Serializable
data class SyncSaleResultDto(
    val id: String? = null,
    val index: Int,
    val result: String,
    val reason: String? = null,
    val details: JsonObject? = null,
    val flags: List<String> = emptyList(),
)

@Serializable
data class SyncSalesResponseDto(val results: List<SyncSaleResultDto>)
