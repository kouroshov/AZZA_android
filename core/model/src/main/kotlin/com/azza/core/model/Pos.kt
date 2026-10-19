package com.azza.core.model

// The sales register (POS-03): what screens get from the register's
// repositories. Money is minor units, as everywhere.

enum class PaymentMethod { CASH, CARD_TERMINAL }

/** One item of a store, from the register's local copy of the catalog. */
data class PosItem(
    val id: String,
    val storeId: String,
    val name: String,
    val priceMinor: Long,
    /** After the item's own discount: what the register charges. */
    val salePriceMinor: Long,
    val currency: String,
    /** PRIVATE (inventory only) or PUBLIC (also a post). */
    val visibility: String,
    val status: String,
    val imageUrl: String?,
    val codes: List<String>,
    /** At the branch asked for; null when the item has no stock row there yet. */
    val stock: PosStock?,
)

data class PosStock(
    /** The shop floor, with sales still in the queue already taken off. May be negative. */
    val storeQty: Int,
    val onlineQty: Int,
    val needsReview: Boolean,
)

/** What a scan (or a typed code) turned out to be. */
sealed interface CodeLookup {
    /** A code of an item of this store. */
    data class Product(val item: PosItem) : CodeLookup

    /** A valid code this store has no item for (yet) — offer to create one. */
    data class UnknownCode(val code: String, val symbology: String) : CodeLookup

    /**
     * An AZZA web address (a post's QR): `/store/<productId>`, or a `/q/<…>`
     * short link. Opened as a post, not looked up as a code.
     */
    data class PostLink(val url: String, val productId: String?) : CodeLookup

    /** Not a code that can exist (bad check digit, empty, unprintable). */
    data class Invalid(val reason: String) : CodeLookup
}

data class SaleLine(
    val productId: String,
    val name: String,
    val quantity: Int,
    /** The price this sale used, minor units. */
    val unitPriceMinor: Long,
)

/** A sale on the phone that has not been recorded by the server (or was refused by it). */
data class QueuedSale(
    val id: String,
    val storeId: String,
    val branchId: String,
    val paymentMethod: PaymentMethod,
    val totalMinor: Long,
    val soldAtMillis: Long,
    val lines: List<SaleLine>,
    /** Set for a REJECTED sale: the server's reason code (e.g. PRODUCT_NOT_IN_STORE). */
    val rejectReason: String?,
    /** Why the last attempt to send it got no answer; null when none failed. */
    val lastError: String?,
)

/** What signing out did. */
sealed interface PosSignOut {
    data object Done : PosSignOut

    /** Refused: [pendingSales] sales have not reached the server and would be lost. */
    data class Blocked(val pendingSales: Int) : PosSignOut
}
