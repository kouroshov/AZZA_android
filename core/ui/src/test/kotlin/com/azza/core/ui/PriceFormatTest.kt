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
    fun `whole amounts print without decimals, grouped like the web`() {
        assertThat(Money(1_250_000_00, "IRR").format(Locale.US)).isEqualTo("$1,250,000")
    }

    @Test
    fun `fractional amounts keep two decimals`() {
        assertThat(Money(1_234_56, "USD").format(Locale.US)).isEqualTo("$1,234.56")
    }

    @Test
    fun `feed major-unit prices round back to exact minor units`() {
        assertThat(Money.fromMajor(19.99, "USD").amountMinor).isEqualTo(1999)
    }
}
