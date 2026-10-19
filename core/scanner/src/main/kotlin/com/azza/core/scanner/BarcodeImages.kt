package com.azza.core.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * A code drawn on the phone (POS-04) — no server call, so a label can be
 * shown, shared and printed offline. The item's name above, the bars, and
 * the code in digits below (a QR code carries no digits line).
 */
object BarcodeImages {
    fun formatOf(symbology: String, code: String): BarcodeFormat? = when (symbology) {
        "EAN13" -> BarcodeFormat.EAN_13.takeIf { Ean13.isValid(code) }
        "EAN8" -> BarcodeFormat.EAN_8.takeIf { code.length == 8 }
        "UPC_A" -> BarcodeFormat.UPC_A.takeIf { code.length == 12 }
        "QR" -> BarcodeFormat.QR_CODE
        // Code 128 carries printable ASCII only; anything else is drawn as QR.
        "CODE128" -> if (code.all { it.code in 0x20..0x7e }) BarcodeFormat.CODE_128 else BarcodeFormat.QR_CODE
        else -> null
    }

    /** The bars alone, [width] × [height] modules-scaled, or null when [code] cannot be drawn so. */
    fun matrix(code: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix? = runCatching {
        MultiFormatWriter().encode(code, format, width, height, mapOf(EncodeHintType.MARGIN to if (format == BarcodeFormat.QR_CODE) 1 else 10))
    }.getOrNull()

    /** A printable label; null when the code cannot be drawn in its symbology. */
    fun label(code: String, symbology: String, title: String, scale: Int = 3): Bitmap? {
        val format = formatOf(symbology, code) ?: return null
        val qr = format == BarcodeFormat.QR_CODE
        val barsWidth = if (qr) 160 * scale else 300 * scale
        val barsHeight = if (qr) barsWidth else 80 * scale
        val bars = matrix(code, format, barsWidth, barsHeight) ?: return null
        val titleSize = 12f * scale
        val digitSize = 13f * scale
        val pad = 6 * scale
        val top = (titleSize * 1.8f).toInt()
        val bottom = if (qr) pad else (digitSize * 1.8f).toInt()
        val bitmap = Bitmap.createBitmap(bars.width + 2 * pad, top + bars.height + bottom, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val ink = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        for (y in 0 until bars.height) {
            var x = 0
            while (x < bars.width) {
                if (!bars[x, y]) { x++; continue }
                val start = x
                while (x < bars.width && bars[x, y]) x++
                canvas.drawRect((pad + start).toFloat(), (top + y).toFloat(), (pad + x).toFloat(), (top + y + 1).toFloat(), ink)
            }
        }
        val titlePaint = Paint().apply { color = Color.BLACK; isAntiAlias = true; textSize = titleSize; textAlign = Paint.Align.CENTER }
        canvas.drawText(ellipsize(title, titlePaint, bitmap.width - 2f * pad), bitmap.width / 2f, titleSize * 1.25f, titlePaint)
        if (!qr) {
            val digits = Paint().apply { color = Color.BLACK; isAntiAlias = true; typeface = Typeface.MONOSPACE; textSize = digitSize; textAlign = Paint.Align.CENTER }
            canvas.drawText(ellipsize(code, digits, bitmap.width - 2f * pad), bitmap.width / 2f, top + bars.height + digitSize * 1.25f, digits)
        }
        return bitmap
    }

    private fun ellipsize(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
}
