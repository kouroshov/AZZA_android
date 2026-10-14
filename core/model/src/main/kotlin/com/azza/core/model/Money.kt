package com.azza.core.model

/**
 * Money in integer minor units + ISO-4217 currency — the same rule as the
 * database (no floating-point money, ever). apps/api stores 100 minor units
 * per major unit for every currency, IRR included.
 */
data class Money(val amountMinor: Long, val currency: String) {
    val major: Long get() = amountMinor / MINOR_PER_MAJOR

    companion object {
        const val MINOR_PER_MAJOR = 100L

        /**
         * The discovery feed sends `price` already converted to major units as
         * a JSON number. Rounded back to minor units once, here, and never
         * used for arithmetic beyond display.
         */
        fun fromMajor(major: Double, currency: String): Money =
            Money(Math.round(major * MINOR_PER_MAJOR), currency)
    }
}
