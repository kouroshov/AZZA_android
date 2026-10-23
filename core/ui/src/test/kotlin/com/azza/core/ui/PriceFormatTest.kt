package com.azza.core.ui

import com.azza.core.model.Money
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class PriceFormatTest {

    @Test
    fun `no discount leaves the price unchanged`() {
        val price = Money(1_250_000_00, "IRR")
        assertThat(price.discounted(null)).isEqualTo(price)
        assertThat(price.discounted(0.0)).isEqualTo(price)
    }

    @Test
    fun `discount is applied in integer minor units`() {
        // 12.5% of 1,000.00 = 125.00
        assertThat(Money(1_000_00, "IRR").discounted(12.5).amountMinor).isEqualTo(875_00)
    }

    @Test
    fun `prints toman, the rial amount over ten, grouped`() {
        // 1,250,000 rial = 125,000 toman (2026-10-23: toman everywhere, as on the web).
        assertThat(Money(1_250_000_00, "IRR").format(Locale.US)).isEqualTo("125,000 toman")
    }

    @Test
    fun `rounds to the nearest toman`() {
        assertThat(Money(1_234_56, "IRR").format(Locale.US)).isEqualTo("123 toman")
    }

    @Test
    fun `a negotiable listing says so instead of a price`() {
        val price = Money(0, "IRR")
        assertThat(price.priceLabel(null, negotiable = true, Locale("fa"))).isEqualTo("توافقی")
        assertThat(price.priceLabel(null, negotiable = true, Locale.US)).isEqualTo("Negotiable")
        assertThat(Money(1_000_00, "IRR").priceLabel(10.0, negotiable = false, Locale.US)).isEqualTo("90 toman")
    }

    @Test
    fun `feed major-unit prices round back to exact minor units`() {
        assertThat(Money.fromMajor(19.99, "USD").amountMinor).isEqualTo(1999)
    }
}
