package com.azza.feature.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/** Every AZZA label is EAN-13 (the web's `src/lib/ean13.ts`, GS1 prefix 2). */
object Ean13 {
    /** 13 digits whose last one is the GS1 check digit of the first twelve. */
    fun isValid(code: String): Boolean {
        if (code.length != 13 || code.any { it !in '0'..'9' }) return false
        val sum = code.take(12).mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10 == code[12] - '0'
    }
}

/**
 * Reads an EAN-13 from a camera frame's luminance (the Y plane). Bars read
 * across, so a label held level on an upright phone lies sideways in the
 * sensor's frame: when the frame as it is has none, it is read again turned
 * a quarter. Not thread-safe — one per analyzer.
 */
class BarcodeDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    /** [y] is [width] × [height] luminance, [rowStride] bytes per row. */
    fun decode(y: ByteArray, width: Int, height: Int, rowStride: Int = width): String? {
        read(PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false))?.let { return it }
        val turned = rotate(y, width, height, rowStride)
        return read(PlanarYUVLuminanceSource(turned, height, width, 0, 0, height, width, false))
    }

    private fun read(source: PlanarYUVLuminanceSource): String? = try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text.takeIf(Ean13::isValid)
    } catch (_: NotFoundException) {
        null
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }

    /** A quarter turn clockwise: the result is [height] wide and [width] tall. */
    private fun rotate(y: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val out = ByteArray(width * height)
        for (row in 0 until height) {
            val from = row * rowStride
            val column = height - 1 - row
            for (x in 0 until width) out[x * height + column] = y[from + x]
        }
        return out
    }
}
