package com.tracemark.watermark.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.tracemark.watermark.data.PhotoRepository
import com.tracemark.watermark.model.FormattedWatermark
import com.tracemark.watermark.model.WatermarkTemplate
import com.tracemark.watermark.model.WatermarkTransform
import com.tracemark.watermark.render.WatermarkRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ExportRequest(
    val sourceUri: Uri,
    val template: WatermarkTemplate,
    val text: FormattedWatermark,
    val transform: WatermarkTransform,
    val maxSide: Int = 6000,
    val jpegQuality: Int = 95,
    val keepExif: Boolean = true,
    val exifComment: String = "",
    val fileNameHint: String = ""
)

data class ExportResult(
    val uri: Uri,
    val width: Int,
    val height: Int,
    val fileName: String
)

class PhotoExporter(
    private val context: Context,
    private val photos: PhotoRepository
) {
    private val resolver get() = context.contentResolver

    suspend fun export(request: ExportRequest, onProgress: (Float) -> Unit = {}): ExportResult =
        withContext(Dispatchers.IO) {
            val caps = buildList {
                add(request.maxSide)
                listOf(4096, 3200, 2400).filterTo(this) { it < request.maxSide }
            }
            var lastError: Throwable? = null
            for (cap in caps) {
                try {
                    onProgress(0.08f)
                    val decoded = photos.loadForExport(request.sourceUri, cap)
                        ?: error("无法解码这张照片")
                    onProgress(0.4f)

                    val target = if (decoded.isMutable) decoded else {
                        val copy = decoded.copy(Bitmap.Config.ARGB_8888, true)
                        decoded.recycle()
                        copy ?: error("内存不足，无法创建画布")
                    }
                    try {
                        val canvas = Canvas(target)
                        WatermarkRenderer.draw(
                            canvas,
                            target.width.toFloat(),
                            target.height.toFloat(),
                            request.template,
                            request.text,
                            request.transform
                        )
                        onProgress(0.62f)

                        val result = write(target, request)
                        onProgress(1f)
                        return@withContext result
                    } finally {
                        target.recycle()
                    }
                } catch (oom: OutOfMemoryError) {
                    lastError = oom
                    Log.w(TAG, "export OOM at cap=$cap, retrying lower", oom)
                }
            }
            throw lastError ?: IllegalStateException("导出失败，请尝试降低导出尺寸")
        }

    private fun buildFileName(request: ExportRequest): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val hint = request.fileNameHint
            .substringBeforeLast('.', request.fileNameHint)
            .replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
            .takeLast(28)
            .trim('_')
        return buildString {
            append("迹印_")
            if (hint.isNotEmpty()) append(hint).append('_')
            append(stamp).append(".jpg")
        }
    }

    private fun write(bitmap: Bitmap, request: ExportRequest): ExportResult {
        val fileName = buildFileName(request)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToMediaStore(bitmap, request, fileName)
        } else {
            writeToPublicDir(bitmap, request, fileName)
        }.let { uri ->
            ExportResult(uri, bitmap.width, bitmap.height, fileName)
        }
    }

    private fun writeToMediaStore(bitmap: Bitmap, request: ExportRequest, fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TraceMark")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法写入相册")
        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, request.jpegQuality, out)) {
                    error("JPEG 编码失败")
                }
            } ?: error("无法打开输出流")
            if (request.keepExif) {
                runCatching {
                    resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        copyMetadata(request, exif)
                    }
                }.onFailure { Log.w(TAG, "copy exif failed", it) }
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun writeToPublicDir(bitmap: Bitmap, request: ExportRequest, fileName: String): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "TraceMark"
        )
        if (!dir.exists() && !dir.mkdirs()) error("无法创建导出目录")
        val file = File(dir, fileName)
        try {
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, request.jpegQuality, out)) {
                    error("JPEG 编码失败")
                }
            }
            if (request.keepExif) {
                runCatching {
                    copyMetadata(request, ExifInterface(file.absolutePath))
                }.onFailure { Log.w(TAG, "copy exif failed", it) }
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.TITLE, fileName.substringBeforeLast('.'))
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) return uri
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
        return Uri.fromFile(file)
    }

    private fun copyMetadata(request: ExportRequest, target: ExifInterface) {
        request.sourceUri.let { source ->
            runCatching {
                resolver.openInputStream(source)?.use { input ->
                    val src = ExifInterface(input)
                    COPY_TAGS.forEach { tag ->
                        src.getAttribute(tag)?.takeIf { it.isNotBlank() }
                            ?.let { target.setAttribute(tag, it) }
                    }
                }
            }.onFailure { Log.w(TAG, "read source exif failed", it) }
        }
        // Pixels are already rotated by the loader, so the output is always upright.
        target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        target.setAttribute(ExifInterface.TAG_SOFTWARE, "TraceMark 迹印相机")
        target.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "迹印相机 · 经纬地址水印")
        if (request.exifComment.isNotBlank()) {
            target.setAttribute(ExifInterface.TAG_USER_COMMENT, request.exifComment)
        }
        target.saveAttributes()
    }

    companion object {
        private const val TAG = "TraceMarkExport"

        private val COPY_TAGS = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD
        )

        fun shareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
