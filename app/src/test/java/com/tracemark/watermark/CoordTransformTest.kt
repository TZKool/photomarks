package com.tracemark.watermark

import com.tracemark.watermark.geo.CoordTransform
import com.tracemark.watermark.geo.GeoMath
import com.tracemark.watermark.model.CoordinateSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordTransformTest {

    private val beijing = 39.908735 to 116.397477

    @Test
    fun wgs84IsIdentity() {
        val (lat, lon) = CoordTransform.convert(beijing.first, beijing.second, CoordinateSystem.WGS84)
        assertEquals(beijing.first, lat, 1e-12)
        assertEquals(beijing.second, lon, 1e-12)
    }

    @Test
    fun gcjRoundTrip() {
        val (gcjLat, gcjLon) = CoordTransform.wgs84ToGcj02(beijing.first, beijing.second)
        val (backLat, backLon) = CoordTransform.gcj02ToWgs84(gcjLat, gcjLon)
        assertEquals(beijing.first, backLat, 1e-5)
        assertEquals(beijing.second, backLon, 1e-5)
        assertTrue(GeoMath.distanceMeters(beijing.first, beijing.second, gcjLat, gcjLon) > 100.0)
    }

    @Test
    fun bd09RoundTrip() {
        val (gcjLat, gcjLon) = CoordTransform.wgs84ToGcj02(beijing.first, beijing.second)
        val (bdLat, bdLon) = CoordTransform.gcj02ToBd09(gcjLat, gcjLon)
        val (backLat, backLon) = CoordTransform.bd09ToGcj02(bdLat, bdLon)
        assertEquals(gcjLat, backLat, 1e-5)
        assertEquals(gcjLon, backLon, 1e-5)
    }

    @Test
    fun outsideChinaIsUntouched() {
        assertTrue(CoordTransform.outOfChina(48.8566, 2.3522))
        assertFalse(CoordTransform.outOfChina(39.9087, 116.3975))
        val (lat, lon) = CoordTransform.wgs84ToGcj02(48.8566, 2.3522)
        assertEquals(48.8566, lat, 1e-9)
        assertEquals(2.3522, lon, 1e-9)
    }

    @Test
    fun distanceMatchesKnownCitySpan() {
        val meters = GeoMath.distanceMeters(39.9087, 116.3975, 31.2304, 121.4737)
        assertTrue("Beijing-Shanghai should be ~1068km but was $meters", meters in 1_050_000.0..1_090_000.0)
    }
}
