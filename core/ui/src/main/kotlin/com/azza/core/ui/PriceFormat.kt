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
 * Same output as the web card today: `$` + the major amount grouped for the
 * current locale (`toLocaleString()`), which gives Persian digits under fa.
 * When the web picks a real currency label, change both together.
 */
fun Money.format(locale: Locale = Locale.getDefault()): String {
    val whole = amountMinor % Money.MINOR_PER_MAJOR == 0L
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = if (whole) 0 else 2
        maximumFractionDigits = if (whole) 0 else 2
    }
    val value = if (whole) amountMinor / Money.MINOR_PER_MAJOR else amountMinor
    val text = if (whole) formatter.format(value) else formatter.format(value / Money.MINOR_PER_MAJOR.toDouble())
    return "$$text"
}
