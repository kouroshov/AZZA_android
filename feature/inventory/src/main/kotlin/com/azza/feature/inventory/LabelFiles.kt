package com.azza.feature.inventory

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.print.PrintHelper
import java.io.File

/**
 * A code label leaving the app (POS-04): shared as a PNG, saved to the
 * phone's pictures, or printed. The label is drawn on the phone
 * (core:scanner's BarcodeImages), so all three work offline.
 */
internal object LabelFiles {
    private fun authority(context: Context) = "${context.packageName}.inventory.files"

    private fun safeName(code: String) = code.filter { it.isLetterOrDigit() }.take(40).ifEmpty { "code" }

    fun share(context: Context, bitmap: Bitmap, code: String, title: String) {
        val dir = File(context.cacheDir, "labels").apply { mkdirs() }
        val file = File(dir, "azza-${safeName(code)}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "$title — $code")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Pictures/AZZA on Android 10+ (no permission needed there); false where it cannot. */
    fun canSave(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun save(context: Context, bitmap: Bitmap, code: String): Boolean {
        if (!canSave()) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "azza-${safeName(code)}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AZZA")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching { resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true }.getOrDefault(false)
    }

    fun print(context: Context, bitmap: Bitmap, code: String) {
        PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }.printBitmap("AZZA $code", bitmap)
    }

    /** Where the camera writes a new item's photo (shared with it through the FileProvider). */
    fun newPhotoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "item-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, authority(context), file)
    }
}
