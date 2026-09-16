package com.xbertz.onsite.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Where the date/time band is drawn on a stamped photo. */
enum class TimestampPosition { TOP, BOTTOM }

/**
 * Draws the capture date/time (and, when a logo file is set, the logo in the bottom-right
 * corner) onto a photo and saves the result to the device gallery.
 *
 * Sizes are relative to the image width so the stamp looks the same on any camera resolution.
 */
object PhotoStamper {

    /** Longest side kept when decoding; camera output is far bigger than needed and would risk OOM. */
    private const val MAX_SIDE = 2560

    // Stamp geometry as fractions of the image width, shared with the live camera overlay so the
    // viewfinder shows exactly what will be saved.
    const val TEXT_SIZE_FRACTION = 1f / 26f
    const val PADDING_FRACTION = TEXT_SIZE_FRACTION * 0.6f
    const val LOGO_WIDTH_FRACTION = 0.18f
    const val BAND_ALPHA = 150

    /** Decodes [source] (honouring EXIF rotation) and returns a new bitmap with the stamp drawn on it. */
    @Throws(IOException::class)
    fun stamp(context: Context, source: Uri, timestamp: String, position: TimestampPosition, logoPath: String?): Bitmap {
        val photo = decodeUpright(context, source)
        val canvas = Canvas(photo)
        val width = photo.width.toFloat()
        val height = photo.height.toFloat()

        val textSize = width * TEXT_SIZE_FRACTION
        val padding = width * PADDING_FRACTION
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(textSize / 10f, 0f, textSize / 20f, Color.argb(160, 0, 0, 0))
        }
        val bandPaint = Paint().apply { color = Color.argb(BAND_ALPHA, 0, 0, 0) }

        val bandHeight = textSize + padding * 2
        val bandTop = if (position == TimestampPosition.TOP) 0f else height - bandHeight
        canvas.drawRect(0f, bandTop, width, bandTop + bandHeight, bandPaint)
        val metrics = textPaint.fontMetrics
        val baseline = bandTop + bandHeight / 2 - (metrics.ascent + metrics.descent) / 2
        canvas.drawText(timestamp, padding, baseline, textPaint)

        val logo = logoPath?.let { BitmapFactory.decodeFile(it) }
        if (logo != null) {
            val logoWidth = width * LOGO_WIDTH_FRACTION
            val logoHeight = logoWidth * logo.height / logo.width
            // Sit the logo above the band when the band is at the bottom so the two never overlap.
            val bottom = if (position == TimestampPosition.BOTTOM) bandTop - padding else height - padding
            val left = width - logoWidth - padding
            val scaled = Bitmap.createScaledBitmap(logo, logoWidth.toInt().coerceAtLeast(1), logoHeight.toInt().coerceAtLeast(1), true)
            canvas.drawBitmap(scaled, left, bottom - logoHeight, Paint(Paint.FILTER_BITMAP_FLAG))
            scaled.recycle()
            logo.recycle()
        }
        return photo
    }

    /**
     * Saves [bitmap] as a JPEG in the gallery under Pictures/OnSite and returns its content Uri.
     * Below Android 10 this needs WRITE_EXTERNAL_STORAGE, which the screen requests first.
     */
    @Throws(IOException::class)
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OnSite")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "OnSite")
                dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(dir, displayName).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw IOException("JPEG compression failed")
            } ?: throw IOException("Cannot open output stream")
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        return uri
    }

    /** Decodes at most [MAX_SIDE] on the longest side, rotated per EXIF, as a mutable bitmap. */
    private fun decodeUpright(context: Context, source: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(source) ?: throw IOException("Cannot open $source")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) } // returns null by design with inJustDecodeBounds
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image: $source")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Cannot decode $source")

        val orientation = resolver.openInputStream(source)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return decoded
        }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return if (rotated.isMutable) rotated else rotated.copy(Bitmap.Config.ARGB_8888, true).also { rotated.recycle() }
    }
}
