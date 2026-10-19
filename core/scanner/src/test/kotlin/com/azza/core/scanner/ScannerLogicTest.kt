package com.azza.core.scanner

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Test

class ScannerLogicTest {
    /** Any symbology, drawn by ZXing's own writer and laid in a white frame. */
    private fun frameOf(code: String, format: BarcodeFormat, width: Int = 800, height: Int = 800): ByteArray {
        val size = if (format == BarcodeFormat.QR_CODE) 400 else 600
        val bars = MultiFormatWriter().encode(code, format, size, if (format == BarcodeFormat.QR_CODE) 400 else 200)
        val y = ByteArray(width * height) { 0xFF.toByte() }
        for (r in 0 until bars.height) for (c in 0 until bars.width) {
            if (bars[c, r]) y[((height - bars.height) / 2 + r) * width + (width - bars.width) / 2 + c] = 0
        }
        return y
    }

    @Test
    fun `reads every register format, named as the server names them`() {
        val decoder = BarcodeDecoder()
        assertThat(decoder.decode(frameOf("96385074", BarcodeFormat.EAN_8), 800, 800)).isEqualTo(ScannedCode("96385074", "EAN8"))
        assertThat(decoder.decode(frameOf("SHELF-12", BarcodeFormat.CODE_128), 800, 800)).isEqualTo(ScannedCode("SHELF-12", "CODE128"))
        assertThat(decoder.decode(frameOf("https://azza.ir/q/abc", BarcodeFormat.QR_CODE), 800, 800)).isEqualTo(ScannedCode("https://azza.ir/q/abc", "QR"))
        assertThat(decoder.decode(frameOf("4006381333931", BarcodeFormat.EAN_13), 800, 800)).isEqualTo(ScannedCode("4006381333931", "EAN13"))
    }

    @Test
    fun `a UPC-E label reads as the UPC-A it stands for`() {
        // 0425261-4 is the UPC-E of UPC-A 042100005264.
        assertThat(UpcE.toUpcA("04252614")).isEqualTo("042100005264")
        assertThat(UpcE.toUpcA("04252615")).isNull() // wrong check digit
        assertThat(UpcE.toUpcA("24252614")).isNull() // number system 2 is not UPC-E
        val read = BarcodeDecoder().decode(frameOf("04252614", BarcodeFormat.UPC_E), 800, 800)
        assertThat(read).isEqualTo(ScannedCode("042100005264", "UPC_A"))
    }

    @Test
    fun `the same value is ignored for 1_5 s, a new one passes at once`() {
        var now = 0L
        val dedupe = ScanDeduper(1_500) { now }
        assertThat(dedupe.accept("A")).isTrue()
        now = 1_000
        assertThat(dedupe.accept("A")).isFalse()
        assertThat(dedupe.accept("B")).isTrue()
        now = 1_200
        assertThat(dedupe.accept("A")).isTrue()
        now = 2_600
        assertThat(dedupe.accept("A")).isFalse()
        now = 4_400
        assertThat(dedupe.accept("A")).isTrue()
    }

    @Test
    fun `a keyboard-wedge scanner is a run of keys ended by Enter`() {
        val wedge = WedgeBuffer()
        "4006381333931".forEach { assertThat(wedge.key(it, enter = false)).isNull() }
        assertThat(wedge.key(null, enter = true)).isEqualTo("4006381333931")
        // A stray Enter, or one or two keys, is not a code.
        assertThat(wedge.key(null, enter = true)).isNull()
        wedge.key('1', false)
        wedge.key('2', false)
        assertThat(wedge.key(null, enter = true)).isNull()
    }

    @Test
    fun `labels are drawn in the code's own symbology, and Code 128 falls back to QR for non-ASCII`() {
        assertThat(BarcodeImages.formatOf("EAN13", "2000000012346")).isEqualTo(BarcodeFormat.EAN_13)
        assertThat(BarcodeImages.formatOf("EAN13", "2000000012343")).isNull()
        assertThat(BarcodeImages.formatOf("EAN8", "96385074")).isEqualTo(BarcodeFormat.EAN_8)
        assertThat(BarcodeImages.formatOf("CODE128", "SHELF-12")).isEqualTo(BarcodeFormat.CODE_128)
        assertThat(BarcodeImages.formatOf("CODE128", "قفسه")).isEqualTo(BarcodeFormat.QR_CODE)
        val bars = BarcodeImages.matrix("2000000012346", BarcodeFormat.EAN_13, 300, 80)!!
        // What is drawn reads back as the same code.
        val y = ByteArray(800 * 800) { 0xFF.toByte() }
        for (r in 0 until bars.height) for (c in 0 until bars.width) if (bars[c, r]) y[(300 + r) * 800 + 200 + c] = 0
        assertThat(BarcodeDecoder().decode(y, 800, 800)?.text).isEqualTo("2000000012346")
    }
}
