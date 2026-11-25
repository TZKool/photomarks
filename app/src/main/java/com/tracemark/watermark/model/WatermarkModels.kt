package com.tracemark.watermark.model

import kotlinx.serialization.Serializable

@Serializable
enum class TemplateLayout {
    COORDINATE_LIST,
    HERO_TIME,
    BRAND_BADGE,
    CHECKIN_CARD,
    GOLD_DIVIDER,
    BOTTOM_PILL
}

@Serializable
enum class Anchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

@Serializable
enum class BackdropStyle {
    NONE, CARD, SCRIM, PILL
}

@Serializable
enum class AddressLevel {
    STREET, DOOR, POI
}

@Serializable
enum class CoordinateSystem {
    WGS84, GCJ02, BD09
}

@Serializable
data class TemplateStyle(
    val layout: TemplateLayout,
    val accentColor: Int = 0xFFF2B33D.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val subTextColor: Int = 0xCCFFFFFF.toInt(),
    val cardColor: Int = 0xFFFFFFFF.toInt(),
    val cardTextColor: Int = 0xFF161B22.toInt(),
    val fontScale: Float = 1f,
    val cornerRatio: Float = 0.018f,
    val letterSpacing: Float = 0f,
    val shadow: Boolean = true,
    val background: BackdropStyle = BackdropStyle.NONE,
    val anchor: Anchor = Anchor.BOTTOM_LEFT,
    val marginRatio: Float = 0.048f,
    val opacity: Float = 1f,
    val brandLabel: String = "迹印相机",
    val condensedTime: Boolean = true
)

@Serializable
data class FieldConfig(
    val showHeader: Boolean = false,
    val showAddress: Boolean = true,
    val showCoords: Boolean = true,
    val showLatitude: Boolean = true,
    val showLongitude: Boolean = true,
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val showWeek: Boolean = true,
    val showWeather: Boolean = true,
    val showNote: Boolean = true,
    val showDevice: Boolean = false,
    val showAltitude: Boolean = false,
    val showAccuracy: Boolean = false,
    val showBrand: Boolean = true,
    val showLogo: Boolean = true,
    val showSeal: Boolean = true,
    val showCode: Boolean = true,
    val time24h: Boolean = true,
    val timeWithSeconds: Boolean = false,
    val datePattern: String = "yyyy-MM-dd",
    val coordDecimals: Int = 6
)

@Serializable
data class WatermarkTemplate(
    val id: String,
    val name: String,
    val description: String = "",
    val builtIn: Boolean = false,
    val style: TemplateStyle,
    val fields: FieldConfig = FieldConfig()
) {
    fun withStyle(style: TemplateStyle) = copy(style = style)
    fun withFields(fields: FieldConfig) = copy(fields = fields)
}

/** Runtime transform of the watermark inside the photo, expressed as fractions of the image size. */
data class WatermarkTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f
) {
    fun reset() = WatermarkTransform()
}

data class GeoPoint(val lat: Double, val lon: Double, val altitude: Double? = null)

data class WatermarkSource(
    val dateTime: java.time.LocalDateTime? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val accuracyMeters: Float? = null,
    val address: String = "",
    val addressSecondary: String = "",
    val weather: String = "",
    val note: String = "",
    val brand: String = "",
    val device: String = "",
    val code: String = ""
)

data class FormattedWatermark(
    val time: String,
    val date: String,
    val week: String,
    val weather: String,
    val address: String,
    val latLabel: String,
    val lngLabel: String,
    val latValue: String,
    val lngValue: String,
    val timeLabel: String,
    val noteLabel: String,
    val altitudeText: String,
    val accuracyText: String,
    val note: String,
    val brand: String,
    val device: String,
    val code: String,
    val coordSystemLabel: String
)
