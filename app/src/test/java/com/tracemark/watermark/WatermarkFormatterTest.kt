package com.tracemark.watermark

import com.tracemark.watermark.model.BuiltInTemplates
import com.tracemark.watermark.model.CoordinateSystem
import com.tracemark.watermark.model.WatermarkSource
import com.tracemark.watermark.util.CodeGenerator
import com.tracemark.watermark.util.WatermarkFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WatermarkFormatterTest {

    private val source = WatermarkSource(
        dateTime = LocalDateTime.of(2025, 6, 22, 19, 16, 43),
        latitude = 39.908735,
        longitude = 116.397477,
        altitude = 52.4,
        accuracyMeters = 8.4f,
        address = "北京市东城区东长安街 12 号",
        weather = "晴 21°C",
        note = "现场记录"
    )

    @Test
    fun formatsTimeAndAddress() {
        val text = WatermarkFormatter.format(source, BuiltInTemplates.coordinateList, CoordinateSystem.WGS84)
        assertEquals("19:16", text.time)
        assertEquals("2025-06-22", text.date)
        assertEquals("星期日", text.week)
        assertEquals("北京市东城区东长安街 12 号", text.address)
        assertEquals("纬度", text.latLabel)
        assertEquals("39.908735", text.latValue)
        assertEquals("116.397477", text.lngValue)
        assertEquals("海拔 52 米", text.altitudeText)
        assertTrue(text.brand.isNotBlank())
    }

    @Test
    fun respectsCoordinateSystemAndDecimals() {
        val template = BuiltInTemplates.heroTime.copy(
            fields = BuiltInTemplates.heroTime.fields.copy(coordDecimals = 4)
        )
        val gcj = WatermarkFormatter.format(source, template, CoordinateSystem.GCJ02)
        assertTrue(gcj.latValue.matches(Regex("""\d+\.\d{4}""")))
        val bd = WatermarkFormatter.format(source, template, CoordinateSystem.BD09)
        assertTrue(bd.lngValue.matches(Regex("""\d+\.\d{4}""")))
    }

    @Test
    fun supportsSecondsAndTwelveHourClock() {
        val template = BuiltInTemplates.coordinateList.copy(
            fields = BuiltInTemplates.coordinateList.fields.copy(timeWithSeconds = true)
        )
        val text = WatermarkFormatter.format(source, template, CoordinateSystem.WGS84)
        assertEquals("19:16:43", text.time)

        val twelve = WatermarkFormatter.format(
            source,
            template.copy(fields = template.fields.copy(time24h = false, timeWithSeconds = false)),
            CoordinateSystem.WGS84
        )
        assertTrue(twelve.time.contains("07:16"))
    }

    @Test
    fun fallsBackWhenAddressMissing() {
        val text = WatermarkFormatter.format(
            source.copy(address = ""),
            BuiltInTemplates.brandBadge,
            CoordinateSystem.WGS84
        )
        assertEquals("未获取到地址", text.address)
    }

    @Test
    fun codeIsDeterministicAndStable() {
        val first = CodeGenerator.make(39.9, 116.3, "2025-06-22T19:16:43")
        val second = CodeGenerator.make(39.9, 116.3, "2025-06-22T19:16:43")
        assertEquals(first, second)
        assertEquals(12, first.length)
        assertTrue(first.all { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" })
    }
}
