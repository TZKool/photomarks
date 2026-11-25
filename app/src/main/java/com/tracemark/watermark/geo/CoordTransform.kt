package com.tracemark.watermark.geo

import com.tracemark.watermark.model.CoordinateSystem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Conversions between the three coordinate systems used in mainland China.
 * EXIF GPS is WGS84; AMap/Tencent expect GCJ-02; Baidu expects BD-09.
 */
object CoordTransform {

    private const val PI = Math.PI
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val X_PI = PI * 3000.0 / 180.0

    fun outOfChina(lat: Double, lon: Double): Boolean =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return lat to lon
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return (lat + dLat) to (lon + dLon)
    }

    fun gcj02ToWgs84(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return lat to lon
        // Iterative inverse keeps error well below 1 metre.
        var wgsLat = lat
        var wgsLon = lon
        repeat(4) {
            val (gcjLat, gcjLon) = wgs84ToGcj02(wgsLat, wgsLon)
            wgsLat += lat - gcjLat
            wgsLon += lon - gcjLon
        }
        return wgsLat to wgsLon
    }

    fun gcj02ToBd09(lat: Double, lon: Double): Pair<Double, Double> {
        val z = sqrt(lon * lon + lat * lat) + 0.00002 * sin(lat * X_PI)
        val theta = kotlin.math.atan2(lat, lon) + 0.000003 * cos(lon * X_PI)
        return (z * sin(theta) + 0.006) to (z * cos(theta) + 0.0065)
    }

    fun bd09ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        val x = lon - 0.0065
        val y = lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = kotlin.math.atan2(y, x) - 0.000003 * cos(x * X_PI)
        return (z * sin(theta)) to (z * cos(theta))
    }

    fun wgs84ToBd09(lat: Double, lon: Double): Pair<Double, Double> {
        val (gcjLat, gcjLon) = wgs84ToGcj02(lat, lon)
        return gcj02ToBd09(gcjLat, gcjLon)
    }

    /** Returns coordinate pair expressed in [system], given a WGS84 input. */
    fun convert(wgs84Lat: Double, wgs84Lon: Double, system: CoordinateSystem): Pair<Double, Double> =
        when (system) {
            CoordinateSystem.WGS84 -> wgs84Lat to wgs84Lon
            CoordinateSystem.GCJ02 -> wgs84ToGcj02(wgs84Lat, wgs84Lon)
            CoordinateSystem.BD09 -> wgs84ToBd09(wgs84Lat, wgs84Lon)
        }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}

object GeoMath {
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * kotlin.math.asin(sqrt(a))
    }
}
