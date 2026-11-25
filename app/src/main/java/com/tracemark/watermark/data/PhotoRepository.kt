package com.tracemark.watermark.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class PhotoMeta(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val dateFromExif: Boolean = false,
    val make: String = "",
    val model: String = "",
    val lens: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int = ExifInterface.ORIENTATION_NORMAL,
    val displayName: String = ""
)

class PhotoRepository(private val context: Context) {

    private val exifDateFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm")
    )

    suspend fun readMeta(uri: Uri): PhotoMeta = withContext(Dispatchers.IO) {
        val exif = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        }.getOrNull()

        val dateTime = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?.takeIf { it.isNotBlank() }
            ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)?.takeIf { it.isNotBlank() }
        val parsedDate = dateTime?.let { raw ->
            exifDateFormats.firstNotNullOfOrNull { fmt ->
                runCatching { LocalDateTime.parse(raw.trim(), fmt) }.getOrNull()
            }
        }

        var lat: Double? = null
        var lon: Double? = null
        if (exif != null) {
            val latValue = exif.getAttributeDouble(ExifInterface.TAG_GPS_LATITUDE, Double.NaN)
            val lonValue = exif.getAttributeDouble(ExifInterface.TAG_GPS_LONGITUDE, Double.NaN)
            if (!latValue.isNaN() && !lonValue.isNaN()) {
                val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
                val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
                lat = if (latRef.equals("S", true)) -latValue else latValue
                lon = if (lonRef.equals("W", true)) -lonValue else lonValue
                if (lat == 0.0 && lon == 0.0) {
                    lat = null
                    lon = null
                }
            }
        }

        val altitude = exif?.let {
            val value = it.getAttributeDouble(ExifInterface.TAG_GPS_ALTITUDE, Double.NaN)
            if (value.isNaN()) null else {
                val below = it.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF) == "1"
                if (below) -value else value
            }
        }

        val make = exif?.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
        val model = exif?.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
        val lens = exif?.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim().orEmpty()
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL
        val exifWidth = exif?.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0) ?: 0
        val exifHeight = exif?.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0) ?: 0

        PhotoMeta(
            latitude = lat,
            longitude = lon,
            altitude = altitude,
            dateTime = parsedDate ?: LocalDateTime.now(),
            dateFromExif = parsedDate != null,
            make = make,
            model = model,
            lens = lens,
            width = exifWidth,
            height = exifHeight,
            orientation = orientation,
            displayName = queryName(uri)
        )
    }

    private fun queryName(uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty()
    }

    suspend fun loadPreview(uri: Uri, maxSide: Int = 1600): Bitmap? = load(uri, maxSide)

    suspend fun loadForExport(uri: Uri, maxSide: Int): Bitmap? = load(uri, maxSide)

    private suspend fun load(uri: Uri, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@withContext null

        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        var result = applyOrientation(decoded, orientation)
        val longSide = max(result.width, result.height)
        if (longSide > maxSide) {
            val ratio = maxSide.toFloat() / longSide
            val scaled = Bitmap.createScaledBitmap(
                result,
                (result.width * ratio).toInt().coerceAtLeast(1),
                (result.height * ratio).toInt().coerceAtLeast(1),
                true
            )
            if (scaled != result) result.recycle()
            result = scaled
        }
        result
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it != bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }
}
