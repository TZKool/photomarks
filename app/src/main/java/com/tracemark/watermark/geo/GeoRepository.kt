package com.tracemark.watermark.geo

import com.tracemark.watermark.data.AppSettings
import com.tracemark.watermark.model.AddressLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class GeoRepository {

    private data class Entry(val result: GeocodeResult, val at: Long)

    private val cache = object : LinkedHashMap<String, Entry>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 80
    }

    private val autoOrder = listOf(
        "amap", "baidu", "tencent", "tianditu", "mapbox", "geoapify", "photon", "nominatim", "bigdatacloud"
    )

    fun providerOrder(settings: AppSettings): List<ReverseGeocoder> {
        val ids = if (settings.geoProvider == "auto") autoOrder else listOf(settings.geoProvider)
        val chosen = ids.mapNotNull { GeoProviderRegistry.byId(it) }
            .filter { !it.info.requiresKey || settings.keyFor(it.info.id).isNotBlank() }
        if (chosen.isEmpty()) {
            return listOfNotNull(
                GeoProviderRegistry.byId("photon"),
                GeoProviderRegistry.byId("nominatim"),
                GeoProviderRegistry.byId("bigdatacloud")
            )
        }
        return chosen
    }

    suspend fun reverse(lat: Double, lon: Double, settings: AppSettings): GeoOutcome =
        withContext(Dispatchers.IO) {
            val level = settings.addressLevel
            val providers = providerOrder(settings)
            val errors = mutableListOf<String>()
            var best: GeocodeResult? = null
            val tried = mutableListOf<String>()

            for (provider in providers) {
                val info = provider.info
                val key = settings.keyFor(info.id)
                val cacheKey = cacheKey(info.id, lat, lon, level)
                val cached = cache[cacheKey]
                val result: GeocodeResult? = try {
                    if (cached != null && System.currentTimeMillis() - cached.at < 10 * 60 * 1000L) {
                        cached.result
                    } else {
                        provider.reverse(lat, lon, level, key).also { cache[cacheKey] = Entry(it, System.currentTimeMillis()) }
                    }
                } catch (t: Throwable) {
                    errors += "${info.name}：${t.message ?: "请求失败"}"
                    null
                }
                tried += info.name
                if (result != null) {
                    if (best == null || result.precision.ordinal > best.precision.ordinal) best = result
                    if (achieved(result, level)) return@withContext GeoOutcome(result, tried, errors)
                    if (settings.geoProvider != "auto") return@withContext GeoOutcome(result, tried, errors)
                }
            }
            GeoOutcome(best, tried, errors)
        }

    private fun achieved(result: GeocodeResult, level: AddressLevel): Boolean = when (level) {
        AddressLevel.STREET -> result.precision.ordinal >= GeoPrecision.STREET.ordinal
        AddressLevel.DOOR -> result.precision.ordinal >= GeoPrecision.DOOR.ordinal
        AddressLevel.POI -> result.precision == GeoPrecision.POI ||
            (result.precision.ordinal >= GeoPrecision.DOOR.ordinal && result.poiName.isNotBlank())
    }

    private fun cacheKey(providerId: String, lat: Double, lon: Double, level: AddressLevel): String =
        String.format(Locale.US, "%s|%.5f|%.5f|%s", providerId, lat, lon, level.name)

    fun clearCache() = cache.clear()
}
