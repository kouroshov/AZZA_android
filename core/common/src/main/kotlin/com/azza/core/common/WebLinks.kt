package com.azza.core.common

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** A website address the app can open on its own screen. */
sealed interface WebDestination {
    data class Product(val id: String) : WebDestination
    data class User(val username: String) : WebDestination
    data class Store(val slug: String) : WebDestination
    /** A store's branch page (/stores/<store>/branches/<branch>). */
    data class Branch(val storeSlug: String, val branchSlug: String) : WebDestination
    /** The emailed "choose a new password" link. */
    data class ResetPassword(val token: String) : WebDestination
    /** The emailed "confirm your new address" link. */
    data class ConfirmEmailChange(val token: String) : WebDestination
}

/**
 * The website's addresses, both ways: what the app shares, and which links
 * it opens itself. The web's paths (Next.js `src/app/[locale]/…`):
 * `/store/<productId>` a listing, `/u/<username>` a person, `/stores/<slug>`
 * a store — each optionally behind a locale (`/fa/…`, `/ckb/…`; English
 * has none, next-intl's "as-needed").
 */
class WebLinks(baseUrl: String) {
    private val base = baseUrl.trimEnd('/')
    private val origin = URI(base)

    fun product(id: String) = "$base/store/${segment(id)}"
    fun user(username: String) = "$base/u/${segment(username)}"
    fun store(slug: String) = "$base/stores/${segment(slug)}"
    /** Any page of the website by its path ("/admin/system"). */
    fun page(path: String) = base + "/" + path.trimStart('/')
    fun branch(storeSlug: String, branchSlug: String) = "$base/stores/${segment(storeSlug)}/branches/${segment(branchSlug)}"

    /** Null for anything that is not one of the pages above on this website. */
    fun parse(link: String): WebDestination? {
        val uri = runCatching { URI(link) }.getOrNull() ?: return null
        if (!uri.host.equals(origin.host, ignoreCase = true) || uri.port != origin.port) return null
        if (uri.scheme != "https" && uri.scheme != origin.scheme) return null
        var parts = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }
        if (parts.firstOrNull() in LOCALES) parts = parts.drop(1)
        // The two emailed links carry a one-time token in the query.
        if (parts.size == 1 && parts[0] in TOKEN_PAGES) {
            val token = (uri.rawQuery ?: "").split('&').firstNotNullOfOrNull { kv ->
                kv.split('=', limit = 2).takeIf { it.size == 2 && it[0] == "token" }?.let { runCatching { URLDecoder.decode(it[1], "UTF-8") }.getOrNull() }
            }?.takeIf { it.isNotBlank() } ?: return null
            return if (parts[0] == "reset-password") WebDestination.ResetPassword(token) else WebDestination.ConfirmEmailChange(token)
        }
        if (parts.size == 4 && parts[0] == "stores" && parts[2] == "branches") {
            val store = runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
            val branch = runCatching { URLDecoder.decode(parts[3], "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
            return WebDestination.Branch(store, branch)
        }
        if (parts.size != 2) return null
        val value = runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return when (parts[0]) {
            "store" -> WebDestination.Product(value)
            "u" -> WebDestination.User(value)
            "stores" -> WebDestination.Store(value)
            else -> null
        }
    }

    private fun segment(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        val LOCALES = setOf("en", "fa", "ckb")
        val TOKEN_PAGES = setOf("reset-password", "confirm-email-change")
    }
}
