package com.azza.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.print.PrintHelper
import com.azza.core.common.Ean13Symbol

/**
 * A listing's barcode as a label: bars (guards taller), the 13 digits in
 * the EAN groups, and the title above — the web's BarcodeLabel. Drawn on a
 * bitmap so the same picture is shown and printed.
 */
fun barcodeLabel(code: String, title: String, moduleWidth: Int = 4): Bitmap? {
    val modules = Ean13Symbol.modules(code) ?: return null
    val quiet = 11 * moduleWidth
    val barHeight = 70 * moduleWidth / 4 * 3
    val width = modules.size * moduleWidth + 2 * quiet
    val titleSize = 9f * moduleWidth
    val digitSize = 10f * moduleWidth
    val height = (titleSize * 1.8f + barHeight + digitSize * 1.6f).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val ink = Paint().apply { color = android.graphics.Color.BLACK; isAntiAlias = false }
    val text = Paint().apply { color = android.graphics.Color.BLACK; isAntiAlias = true; typeface = Typeface.MONOSPACE; textSize = digitSize }
    val titlePaint = Paint().apply { color = android.graphics.Color.BLACK; isAntiAlias = true; textSize = titleSize; textAlign = Paint.Align.CENTER }
    val shortTitle = if (titlePaint.measureText(title) > width - 2 * quiet) {
        var t = title
        while (t.isNotEmpty() && titlePaint.measureText("$t…") > width - 2 * quiet) t = t.dropLast(1)
        "$t…"
    } else title
    canvas.drawText(shortTitle, width / 2f, titleSize * 1.2f, titlePaint)
    val top = titleSize * 1.8f
    modules.forEachIndexed { i, dark ->
        if (!dark) return@forEachIndexed
        val x = (quiet + i * moduleWidth).toFloat()
        val bottom = top + barHeight + if (Ean13Symbol.isGuard(i)) digitSize * 0.6f else 0f
        canvas.drawRect(x, top, x + moduleWidth, bottom, ink)
    }
    val baseline = top + barHeight + digitSize * 1.1f
    canvas.drawText(code.substring(0, 1), (quiet - digitSize * 0.8f).coerceAtLeast(0f), baseline, text)
    text.textAlign = Paint.Align.CENTER
    canvas.drawText(code.substring(1, 7), (quiet + 24 * moduleWidth).toFloat(), baseline, text)
    canvas.drawText(code.substring(7), (quiet + 70 * moduleWidth).toFloat(), baseline, text)
    return bitmap
}

/** Sends the label to the phone's printing service (a printer, or "Save as PDF"). */
fun printBarcode(context: Context, code: String, title: String) {
    val bitmap = barcodeLabel(code, title, moduleWidth = 8) ?: return
    PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }.printBitmap("AZZA $code", bitmap)
}

/** The owner's barcode dialog: the label, its digits, and Print. */
@Composable
fun BarcodeDialog(code: String?, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(code, title) { code?.let { barcodeLabel(it, title) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.barcode_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bitmap == null) {
                    Text(stringResource(R.string.barcode_missing))
                } else {
                    Image(bitmap.asImageBitmap(), contentDescription = code, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.barcode_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (code != null && bitmap != null) TextButton(onClick = { printBarcode(context, code, title) }) { Text(stringResource(R.string.barcode_print)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.barcode_close)) } },
    )
}
