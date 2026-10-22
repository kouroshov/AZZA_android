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
         * ✅ (2026-10-23) Money is stored in rial and shown and typed in toman,
         * as on Divar: 1 toman = 10 rial. The web does the same (src/lib/money.ts).
         */
        const val RIAL_PER_TOMAN = 10L

        /** Minor rial units in one toman (100 × 10). */
        const val MINOR_PER_TOMAN = MINOR_PER_MAJOR * RIAL_PER_TOMAN

        /** A typed toman amount → the rial (major units) apps/api takes. */
        fun tomanToRial(toman: Double): Double = toman * RIAL_PER_TOMAN

        /**
         * The discovery feed sends `price` already converted to major units as
         * a JSON number. Rounded back to minor units once, here, and never
         * used for arithmetic beyond display.
         */
        fun fromMajor(major: Double, currency: String): Money =
            Money(Math.round(major * MINOR_PER_MAJOR), currency)
    }
}
