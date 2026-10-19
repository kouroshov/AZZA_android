package com.azza.core.scanner

import com.azza.core.common.ProductCodes
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
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
 * What was read, and as what. [symbology] uses the server's names
 * (EAN13, EAN8, UPC_A, CODE128, QR) so it can be sent as the hint; null
 * for something typed or from a hardware scanner.
 */
data class ScannedCode(val text: String, val symbology: String?)

/**
 * The formats the register reads (POS-04): EAN-13, EAN-8, UPC-A, UPC-E,
 * Code 128, QR. Pure Java (ZXing) rather than ML Kit: many phones in Iran
 * have no Google Play Services (docs/ROADMAP.md, phase 4b).
 */
internal val SCAN_FORMATS = listOf(
    BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8,
    BarcodeFormat.UPC_A,
    BarcodeFormat.UPC_E,
    BarcodeFormat.CODE_128,
    BarcodeFormat.QR_CODE,
)

/**
 * Reads one code from a camera frame's luminance (the Y plane). Bars read
 * across, so a label held level on an upright phone lies sideways in the
 * sensor's frame: when the frame as it is has none, it is read again turned
 * a quarter. Not thread-safe — one per analyzer.
 */
class BarcodeDecoder(formats: List<BarcodeFormat> = SCAN_FORMATS) {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to formats, DecodeHintType.TRY_HARDER to true))
    }

    /** [y] is [width] × [height] luminance, [rowStride] bytes per row. */
    fun decode(y: ByteArray, width: Int, height: Int, rowStride: Int = width): ScannedCode? {
        read(PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false))?.let { return it }
        val turned = rotate(y, width, height, rowStride)
        return read(PlanarYUVLuminanceSource(turned, height, width, 0, 0, height, width, false))
    }

    private fun read(source: PlanarYUVLuminanceSource): ScannedCode? = try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).toScanned()
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

internal fun Result.toScanned(): ScannedCode? {
    val value = text?.takeIf { it.isNotEmpty() } ?: return null
    return when (barcodeFormat) {
        BarcodeFormat.EAN_13 -> ScannedCode(value, "EAN13").takeIf { Ean13.isValid(value) }
        BarcodeFormat.EAN_8 -> ScannedCode(value, "EAN8")
        BarcodeFormat.UPC_A -> ScannedCode(value, "UPC_A")
        // UPC-E is a zero-suppressed UPC-A: stored (and looked up) as the number it stands for.
        BarcodeFormat.UPC_E -> UpcE.toUpcA(value)?.let { ScannedCode(it, "UPC_A") }
        BarcodeFormat.CODE_128 -> ScannedCode(value, "CODE128")
        BarcodeFormat.QR_CODE -> ScannedCode(value, "QR")
        else -> null
    }
}

/** UPC-E → UPC-A expansion (GS1 General Specifications, the zero-suppression table). */
object UpcE {
    /** [code]: 8 digits (number system 0/1, six data digits, check digit). Null if it is not one. */
    fun toUpcA(code: String): String? {
        if (code.length != 8 || code.any { it !in '0'..'9' } || code[0] !in "01") return null
        val d = code.substring(1, 7)
        val body = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        val upcA = "${code[0]}$body${code[7]}"
        // The check digit is the UPC-A's: a wrong one means a misread.
        return upcA.takeIf { ProductCodes.hasValidCheckDigit(it) }
    }
}
