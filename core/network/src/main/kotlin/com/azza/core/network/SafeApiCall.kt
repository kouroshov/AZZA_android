package com.azza.core.network

import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.network.model.ApiErrorEnvelope
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Runs one apps/api call and turns every way it can end into an [Outcome].
 * The error envelope's `code` is surfaced as-is so callers can branch on it.
 */
suspend fun <T> safeApiCall(json: Json, block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    val body = e.response()?.errorBody()?.string()
    val parsed = body?.let { runCatching { json.decodeFromString<ApiErrorEnvelope>(it) }.getOrNull() }
    if (e.code() == 401 && parsed == null) {
        Outcome.Failure(AppError.Unauthenticated)
    } else {
        Outcome.Failure(
            AppError.Api(
                status = e.code(),
                code = parsed?.error?.code ?: "HTTP_${e.code()}",
                message = parsed?.error?.message ?: e.message(),
                requestId = parsed?.error?.requestId,
                details = parsed?.error?.details?.toString(),
            ),
        )
    }
} catch (e: IOException) {
    Outcome.Failure(AppError.Network)
} catch (e: Exception) {
    Outcome.Failure(AppError.Unexpected(e))
}
