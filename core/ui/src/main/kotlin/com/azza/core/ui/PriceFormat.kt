package com.azza.core.ui

import com.azza.core.model.Money
import java.text.NumberFormat
import java.util.Locale

/**
 * The price the buyer pays, discount applied — the web card's rule
 * (src/lib/pricing.ts `getDiscountedPrice`), done in integer minor units
 * with the percentage as basis points, the way apps/api's checkout does.
 */
fun Money.discounted(discountPercentage: Double?): Money {
    if (discountPercentage == null || discountPercentage <= 0.0) return this
    val basisPoints = Math.round(discountPercentage * 100)
    val off = (amountMinor * basisPoints + 5_000) / 10_000
    return copy(amountMinor = amountMinor - off)
}

/**
 * ✏️ (2026-10-23, the audit's first finding) Toman, as on the web
 * (src/lib/money.ts) and on Divar: the rial amount ÷ 10, grouped in the
 * app language's digits, with the currency word — never "$".
 */
fun Money.format(locale: Locale = Locale.getDefault()): String {
    val half = Money.MINOR_PER_TOMAN / 2
    val toman = if (amountMinor >= 0) (amountMinor + half) / Money.MINOR_PER_TOMAN else -((-amountMinor + half) / Money.MINOR_PER_TOMAN)
    val text = NumberFormat.getIntegerInstance(locale).format(toman)
    return "$text ${tomanWord(locale)}"
}

/** The amount alone, for an input to start from: rial minor units → toman, Latin digits. */
fun Money.tomanInput(): String = (amountMinor / Money.MINOR_PER_TOMAN).toString()

private fun tomanWord(locale: Locale): String = when (locale.language) {
    "fa" -> "تومان"
    "ckb" -> "تمەن"
    else -> "toman"
}

/**
 * A handle as "@name", isolated as left-to-right text (U+2066 … U+2069) so a
 * right-to-left page does not move the "@" to the end — the web's `<Bdi>`.
 */
fun handle(username: String): String = "\u2066@$username\u2069"
