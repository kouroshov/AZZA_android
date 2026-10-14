package com.azza.core.network

import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Android twin of the web's `resolveMediaUrl` (src/lib/productImage.ts).
 * apps/api hands out media as a storage KEY (or, for a future CDN, a full
 * URL); the bytes are served by `GET /api/v1/media/<key>`. The web routes
 * that through its own same-origin proxy; the app has no such need and
 * reads apps/api directly.
 */
@Singleton
class MediaUrlResolver @Inject constructor(@ApiBaseUrl private val baseUrl: String) {

    private val origin: String = baseUrl.substringBefore("/api/")

    fun resolve(urlOrKey: String?): String? {
        val value = urlOrKey?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            return value
        }
        // A path apps/api generated relative to its own origin, e.g. /api/v1/media/…
        if (value.startsWith("/")) return origin + value
        val encodedKey = value.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        return "${baseUrl.trimEnd('/')}/media/$encodedKey"
    }
}
