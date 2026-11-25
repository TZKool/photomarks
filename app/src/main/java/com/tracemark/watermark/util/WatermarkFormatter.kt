package com.tracemark.watermark.util

import com.tracemark.watermark.model.CoordinateSystem
import com.tracemark.watermark.model.FormattedWatermark
import com.tracemark.watermark.model.WatermarkSource
import com.tracemark.watermark.model.WatermarkTemplate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object WatermarkFormatter {

    fun format(
        source: WatermarkSource,
        template: WatermarkTemplate,
        coordSystem: CoordinateSystem
    ): FormattedWatermark {
        val fields = template.fields
        val dateTime = source.dateTime ?: LocalDateTime.now()

        val timeFormatter = DateTimeFormatter.ofPattern(
            when {
                !fields.time24h -> if (fields.timeWithSeconds) "a hh:mm:ss" else "a hh:mm"
                fields.timeWithSeconds -> "HH:mm:ss"
                else -> "HH:mm"
            },
            Locale.CHINA
        )
        val dateFormatter = runCatching {
            DateTimeFormatter.ofPattern(fields.datePattern.ifBlank { "yyyy-MM-dd" }, Locale.CHINA)
        }.getOrElse { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA) }
        val weekFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)

        val lat = source.latitude
        val lng = source.longitude
        val (displayLat, displayLng) = if (lat != null && lng != null) {
            com.tracemark.watermark.geo.CoordTransform.convert(lat, lng, coordSystem)
        } else null to null

        val decimals = fields.coordDecimals.coerceIn(3, 8)

        return FormattedWatermark(
            time = dateTime.format(timeFormatter),
            date = dateTime.format(dateFormatter),
            week = dateTime.format(weekFormatter),
            weather = source.weather,
            address = source.address.ifBlank { "未获取到地址" },
            latLabel = "纬度",
            lngLabel = "经度",
            latValue = displayLat?.let { String.format(Locale.US, "%.${decimals}f", it) }.orEmpty(),
            lngValue = displayLng?.let { String.format(Locale.US, "%.${decimals}f", it) }.orEmpty(),
            timeLabel = "时间",
            noteLabel = "备注",
            altitudeText = source.altitude?.let { "海拔 ${Math.round(it)} 米" }.orEmpty(),
            accuracyText = source.accuracyMeters?.let { "定位精度 ±${Math.round(it)} 米" }.orEmpty(),
            note = source.note,
            brand = template.style.brandLabel.ifBlank {
                source.brand.ifBlank { "迹印相机" }
            },
            device = source.device,
            code = source.code.ifBlank {
                CodeGenerator.make(lat, lng, dateTime.toString())
            },
            coordSystemLabel = when (coordSystem) {
                CoordinateSystem.WGS84 -> "WGS84"
                CoordinateSystem.GCJ02 -> "GCJ-02"
                CoordinateSystem.BD09 -> "BD-09"
            }
        )
    }
}

object CodeGenerator {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun make(vararg parts: Any?): String {
        val seed = parts.joinToString("|") { it?.toString().orEmpty() }.hashCode().toLong() and 0xFFFFFFFFL
        val random = java.util.Random(seed)
        return buildString {
            repeat(12) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
    }
}
