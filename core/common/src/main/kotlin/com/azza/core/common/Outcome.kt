package com.azza.core.common

/**
 * The result of anything that talks to apps/api. A failure carries the API's
 * machine-readable `code` (e.g. `INVALID_CREDENTIALS`) — UI branches on the
 * code, never on the message, exactly as the web client does.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

sealed interface AppError {
    /** apps/api answered with its error envelope. */
    data class Api(val status: Int, val code: String, val message: String, val requestId: String?) : AppError

    /** No answer at all: offline, DNS, timeout, refused connection. */
    data object Network : AppError

    /** The session could not be refreshed; the user has to sign in again. */
    data object Unauthenticated : AppError

    data class Unexpected(val cause: Throwable) : AppError
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}
