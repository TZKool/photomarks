package com.tracemark.watermark.geo

import com.tracemark.watermark.model.CoordinateSystem

enum class GeoPrecision(val label: String) {
    PROVINCE("省级"),
    CITY("城市"),
    DISTRICT("区县"),
    TOWN("乡镇/街道"),
    STREET("道路"),
    DOOR("门牌号"),
    POI("兴趣点")
}

data class GeoPoi(
    val name: String,
    val distanceMeters: Double? = null,
    val address: String? = null,
    val category: String? = null
)

data class GeocodeResult(
    val providerId: String,
    val providerName: String,
    val address: String,
    val addressShort: String,
    val province: String = "",
    val city: String = "",
    val district: String = "",
    val town: String = "",
    val street: String = "",
    val number: String = "",
    val poiName: String = "",
    val pois: List<GeoPoi> = emptyList(),
    val precision: GeoPrecision = GeoPrecision.CITY,
    val coordSystem: CoordinateSystem = CoordinateSystem.WGS84,
    val extra: String = ""
)

data class ProviderInfo(
    val id: String,
    val name: String,
    val requiresKey: Boolean,
    val keyLabel: String,
    val keyHint: String,
    val keyUrl: String,
    val note: String
)

data class GeoOutcome(
    val result: GeocodeResult?,
    val tried: List<String>,
    val errors: List<String>
)

fun cnJoin(vararg parts: String?): String = parts
    .filterNotNull()
    .map { it.trim() }
    .filter { it.isNotEmpty() && it != "[]" }
    .distinct()
    .joinToString("")
