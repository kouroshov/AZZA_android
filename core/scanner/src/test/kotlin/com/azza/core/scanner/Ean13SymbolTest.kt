package com.azza.core.scanner

import com.azza.core.common.Ean13Symbol
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The label the app prints must scan: its own encoder's bars, read back by the scanner's decoder. */
class Ean13SymbolTest {
    private fun frameOf(modules: List<Boolean>, scale: Int = 4, height: Int = 200): Pair<ByteArray, Int> {
        val quiet = 12 * scale
        val width = modules.size * scale + 2 * quiet
        val y = ByteArray(width * height) { 0xFF.toByte() }
        modules.forEachIndexed { i, dark ->
            if (!dark) return@forEachIndexed
            for (row in 40 until height - 40) for (dx in 0 until scale) y[row * width + quiet + i * scale + dx] = 0
        }
        return y to width
    }

    @Test
    fun `every printed code reads back as itself`() {
        for (code in listOf("2000000012346", "2000000046198", "2000001234563", "4006381333931")) {
            val modules = Ean13Symbol.modules(code)!!
            assertThat(modules).hasSize(95)
            val (frame, width) = frameOf(modules)
            assertThat(BarcodeDecoder().decode(frame, width, 200)?.text).isEqualTo(code)
        }
    }

    @Test
    fun `refuses what is not an EAN-13`() {
        assertThat(Ean13Symbol.modules("2000000012343")).isNull()
        assertThat(Ean13Symbol.modules("12345")).isNull()
    }
}
