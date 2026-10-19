package com.azza.core.common

import com.azza.core.common.ProductCodes.Invalid
import com.azza.core.common.ProductCodes.Normalized
import com.azza.core.common.ProductCodes.Symbology
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The SAME vectors as apps/api's `store-inventory/product-code.util.spec.ts`
 * (web repo), case for case, so the phone and the server agree on every code.
 */
class ProductCodesTest {
    @Test
    fun `computes GS1 check digits`() {
        assertThat(ProductCodes.checkDigit("400638133393")).isEqualTo(1) // EAN-13 4006381333931
        assertThat(ProductCodes.checkDigit("9638507")).isEqualTo(4) // EAN-8 96385074
        assertThat(ProductCodes.checkDigit("03600029145")).isEqualTo(2) // UPC-A 036000291452
        assertThat(ProductCodes.hasValidCheckDigit("4006381333931")).isTrue()
        assertThat(ProductCodes.hasValidCheckDigit("4006381333932")).isFalse()
    }

    @Test
    fun `accepts a valid EAN-13 as it is, trimmed`() {
        assertThat(ProductCodes.normalize("  4006381333931 ")).isEqualTo(Normalized.Ok("4006381333931", Symbology.EAN13))
    }

    @Test
    fun `stores UPC-A as EAN-13 with a leading 0`() {
        assertThat(ProductCodes.normalize("036000291452")).isEqualTo(Normalized.Ok("0036000291452", Symbology.EAN13))
    }

    @Test
    fun `keeps EAN-8, and folds a GTIN-14 with a leading 0 into its EAN-13`() {
        assertThat(ProductCodes.normalize("96385074")).isEqualTo(Normalized.Ok("96385074", Symbology.EAN8))
        assertThat(ProductCodes.normalize("04006381333931")).isEqualTo(Normalized.Ok("4006381333931", Symbology.EAN13))
    }

    @Test
    fun `rejects a GTIN whose check digit is wrong`() {
        for (bad in listOf("4006381333932", "036000291453", "96385075", "04006381333932")) {
            assertThat(ProductCodes.normalize(bad)).isEqualTo(Normalized.Bad(Invalid.BAD_CHECK_DIGIT))
        }
    }

    @Test
    fun `treats other codes as Code 128 or QR, and refuses empty or unprintable ones`() {
        assertThat(ProductCodes.normalize("SHELF-12")).isEqualTo(Normalized.Ok("SHELF-12", Symbology.CODE128))
        assertThat(ProductCodes.normalize("12345")).isEqualTo(Normalized.Ok("12345", Symbology.CODE128))
        assertThat(ProductCodes.normalize("https://azza.ir/q/abc")).isEqualTo(Normalized.Ok("https://azza.ir/q/abc", Symbology.QR))
        assertThat(ProductCodes.normalize("   ")).isEqualTo(Normalized.Bad(Invalid.EMPTY))
        assertThat(ProductCodes.normalize("a\u0007b")).isEqualTo(Normalized.Bad(Invalid.UNPRINTABLE))
        assertThat(ProductCodes.normalize("x".repeat(129))).isEqualTo(Normalized.Bad(Invalid.TOO_LONG))
    }

    @Test
    fun `recognises the internal in-store format (prefix 2, valid check digit)`() {
        assertThat(ProductCodes.isInternal("2000000012346")).isTrue()
        assertThat(ProductCodes.isInternal("2000000012343")).isFalse()
        assertThat(ProductCodes.isInternal("4006381333931")).isFalse()
    }
}
