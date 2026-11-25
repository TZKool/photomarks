package com.tracemark.watermark.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tracemark.watermark.geo.CoordTransform
import com.tracemark.watermark.model.AddressLevel
import com.tracemark.watermark.model.CoordinateSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppSettings(
    val geoProvider: String = "auto",
    val addressLevel: AddressLevel = AddressLevel.POI,
    val coordSystem: CoordinateSystem = CoordinateSystem.WGS84,
    val amapKey: String = "",
    val baiduKey: String = "",
    val tencentKey: String = "",
    val tiandituKey: String = "",
    val mapboxToken: String = "",
    val geoapifyKey: String = "",
    val autoWeather: Boolean = true,
    val defaultTemplateId: String = "builtin_coordinate",
    val exportMaxSide: Int = 6000,
    val jpegQuality: Int = 95,
    val keepExif: Boolean = true,
    val darkTheme: Boolean = true,
    val watermarkOpacity: Float = 1f
) {
    fun keyFor(providerId: String): String = when (providerId) {
        "amap" -> amapKey
        "baidu" -> baiduKey
        "tencent" -> tencentKey
        "tianditu" -> tiandituKey
        "mapbox" -> mapboxToken
        "geoapify" -> geoapifyKey
        else -> ""
    }

    fun withKey(providerId: String, value: String): AppSettings = when (providerId) {
        "amap" -> copy(amapKey = value)
        "baidu" -> copy(baiduKey = value)
        "tencent" -> copy(tencentKey = value)
        "tianditu" -> copy(tiandituKey = value)
        "mapbox" -> copy(mapboxToken = value)
        "geoapify" -> copy(geoapifyKey = value)
        else -> this
    }

    fun keysConfigured(): Int = listOf(amapKey, baiduKey, tencentKey, tiandituKey, mapboxToken, geoapifyKey)
        .count { it.isNotBlank() }

    fun displayCoords(lat: Double, lng: Double): Pair<Double, Double> =
        CoordTransform.convert(lat, lng, coordSystem)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tracemark_settings")

class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val settingsKey = stringPreferencesKey("settings_json")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        prefs[settingsKey]?.let { decode(it) } ?: AppSettings()
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[settingsKey]?.let { decode(it) } ?: AppSettings()
            prefs[settingsKey] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    private fun decode(raw: String): AppSettings = runCatching {
        json.decodeFromString(AppSettings.serializer(), raw)
    }.getOrDefault(AppSettings())
}
