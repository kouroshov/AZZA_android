package com.azza.core.network.model

import kotlinx.serialization.Serializable

/** Every 2xx body from apps/api: `{ "success": true, "data": … }` (TransformInterceptor). */
@Serializable
data class ApiEnvelope<T>(val success: Boolean, val data: T)

/** Every error body: `{ "success": false, "error": { code, message, details?, requestId } }`. */
@Serializable
data class ApiErrorEnvelope(val success: Boolean = false, val error: ApiErrorBody)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String = "",
    val requestId: String? = null,
    val details: kotlinx.serialization.json.JsonElement? = null,
)
