package com.tracemark.watermark.data

import com.tracemark.watermark.geo.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

data class WeatherInfo(val text: String, val temperatureC: Int?)

/** 使用 Open-Meteo 免费接口，按拍摄坐标与日期取天气，无需 Key。 */
class WeatherRepository {

    private val cache = mutableMapOf<String, WeatherInfo?>()

    suspend fun weatherAt(lat: Double, lon: Double, dateTime: LocalDateTime): WeatherInfo? =
        withContext(Dispatchers.IO) {
            val date = dateTime.toLocalDate()
            val key = String.format(Locale.US, "%.3f|%.3f|%s", lat, lon, date)
            if (cache.containsKey(key)) return@withContext cache[key]
            val info = runCatching { request(lat, lon, date) }.getOrNull()
            cache[key] = info
            info
        }

    private suspend fun request(lat: Double, lon: Double, date: LocalDate): WeatherInfo? {
        val today = LocalDate.now()
        val archived = date.isBefore(today)
        val base = if (archived) {
            "https://archive-api.open-meteo.com/v1/archive"
        } else {
            "https://api.open-meteo.com/v1/forecast"
        }
        val url = Http.url(base) {
            addQueryParameter("latitude", String.format(Locale.US, "%.4f", lat))
            addQueryParameter("longitude", String.format(Locale.US, "%.4f", lon))
            addQueryParameter("start_date", date.toString())
            addQueryParameter("end_date", date.toString())
            addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min")
            addQueryParameter("timezone", "auto")
        }
        val root = JSONObject(Http.get(url))
        val daily = root.optJSONObject("daily") ?: return null
        val code = daily.optJSONArray("weather_code")?.let { if (it.length() > 0) it.optInt(0, -1) else -1 } ?: -1
        val max = daily.optJSONArray("temperature_2m_max")?.let {
            if (it.length() > 0 && !it.isNull(0)) it.optDouble(0) else null
        }
        val text = describe(code) ?: return null
        val temp = max?.let { Math.round(it).toInt() }
        return WeatherInfo(text, temp)
    }

    private fun describe(code: Int): String? = when (code) {
        0 -> "晴"
        1 -> "少云"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "冻雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        66, 67 -> "冻雨"
        71 -> "小雪"
        73 -> "中雪"
        75 -> "大雪"
        77 -> "米雪"
        80 -> "阵雨"
        81 -> "强阵雨"
        82 -> "暴雨"
        85 -> "阵雪"
        86 -> "强阵雪"
        95 -> "雷阵雨"
        96, 99 -> "雷暴冰雹"
        else -> null
    }
}
