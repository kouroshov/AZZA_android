package com.azza.core.scanner

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.EAN13Writer
import org.junit.Test

class BarcodeDecoderTest {
    // A code shaped like the database's (GS1 prefix 2, sequence payload, check digit).
    private val code = "2000000012346"

    /** The label as a camera would see it: dark bars on white, in a frame [width] × [height]. */
    private fun frame(width: Int, height: Int, sideways: Boolean, rowStride: Int = width): ByteArray {
        val bars = EAN13Writer().encode(code, BarcodeFormat.EAN_13, 600, 200)
        val y = ByteArray(rowStride * height) { 0xFF.toByte() }
        for (r in 0 until bars.height) for (c in 0 until bars.width) {
            if (!bars[c, r]) continue
            // Sideways: a quarter turn, as a label held level looks to an upright phone's sensor.
            val (x, yy) = if (sideways) {
                ((width - bars.height) / 2 + bars.height - 1 - r) to ((height - bars.width) / 2 + c)
            } else {
                ((width - bars.width) / 2 + c) to ((height - bars.height) / 2 + r)
            }
            y[yy * rowStride + x] = 0
        }
        return y
    }

    @Test
    fun `checks the GS1 check digit`() {
        assertThat(Ean13.isValid(code)).isTrue()
        assertThat(Ean13.isValid("2000000012343")).isFalse()
        assertThat(Ean13.isValid("200000001234")).isFalse()
        assertThat(Ean13.isValid("20000000123a6")).isFalse()
    }

    @Test
    fun `reads a level label`() {
        assertThat(BarcodeDecoder().decode(frame(800, 800, sideways = false), 800, 800)?.text).isEqualTo(code)
    }

    @Test
    fun `reads a label that lies sideways in the frame`() {
        assertThat(BarcodeDecoder().decode(frame(800, 800, sideways = true), 800, 800)?.text).isEqualTo(code)
    }

    @Test
    fun `honours a row stride wider than the image`() {
        assertThat(BarcodeDecoder().decode(frame(800, 600, sideways = false, rowStride = 832), 800, 600, rowStride = 832)?.text).isEqualTo(code)
    }

    @Test
    fun `finds nothing in a blank frame`() {
        assertThat(BarcodeDecoder().decode(ByteArray(640 * 480) { 0xFF.toByte() }, 640, 480)).isNull()
    }
}
