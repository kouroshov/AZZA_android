package com.azza.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A phone photo as an upload apps/api accepts: JPEG, at most 5 MB
 * (MAX_UPLOAD_BYTES in product-media.controller.ts). Camera photos are
 * often 4000+ px and larger than that, so the long side is brought down to
 * [MAX_SIDE] and the quality stepped down until it fits. The EXIF rotation
 * is applied first — the stored file has no EXIF to carry it.
 */
@Singleton
class PhotoCompressor @Inject constructor(@ApplicationContext private val context: Context) {

    fun toJpeg(uri: Uri): ByteArray? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val bitmap = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        // The first quality that fits under the limit; a 2048 px JPEG almost always fits at the first.
        QUALITIES.asSequence()
            .map { quality -> ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray() }
            .firstOrNull { it.size <= MAX_BYTES }
    }.getOrNull()

    private companion object {
        const val MAX_SIDE = 2048
        const val MAX_BYTES = 5 * 1024 * 1024
        val QUALITIES = listOf(88, 76, 64, 52, 40)
    }
}
