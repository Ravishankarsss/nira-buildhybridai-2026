package com.mos.nira.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.mos.nira.features.artwork.domain.ArtworkMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Image Processor handling memory-conscious decoding, dimension probing,
 * and thumbnail caching for Android artwork assets.
 */
object ArtworkImageProcessor {

    private const val MAX_DISPLAY_DIMENSION = 1200
    private const val THUMBNAIL_DIMENSION = 200

    /**
     * Inspects image metadata without decoding full bitmap into heap memory.
     */
    suspend fun probeMetadata(context: Context, uri: Uri): ArtworkMetadata = withContext(Dispatchers.IO) {
        var fileName = "Artwork"
        var fileSize = 0L
        var mimeType = "image/*"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: "Artwork"
                if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
            }
        }

        mimeType = context.contentResolver.getType(uri) ?: "image/*"

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        ArtworkMetadata(
            fileName = fileName,
            width = max(1, options.outWidth),
            height = max(1, options.outHeight),
            fileSizeBytes = fileSize,
            mimeType = mimeType
        )
    }

    /**
     * Safely decodes an image with subsampling to prevent OutOfMemoryError.
     */
    suspend fun decodeBitmapSafely(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_DISPLAY_DIMENSION
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // First pass: inspect bounds
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return@withContext null
            }

            // Calculate inSampleSize
            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                maxDimension,
                maxDimension
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates and saves a small thumbnail to app-private cache/files storage.
     */
    suspend fun saveThumbnail(
        context: Context,
        bitmap: Bitmap,
        analysisId: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val scale = min(
                THUMBNAIL_DIMENSION.toFloat() / bitmap.width,
                THUMBNAIL_DIMENSION.toFloat() / bitmap.height
            )
            val thumbW = max(1, (bitmap.width * scale).toInt())
            val thumbH = max(1, (bitmap.height * scale).toInt())

            val thumbBitmap = Bitmap.createScaledBitmap(bitmap, thumbW, thumbH, true)
            val file = java.io.File(context.filesDir, "thumb_$analysisId.webp")
            file.outputStream().use { out ->
                thumbBitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
            }
            if (thumbBitmap != bitmap) {
                thumbBitmap.recycle()
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }
}
