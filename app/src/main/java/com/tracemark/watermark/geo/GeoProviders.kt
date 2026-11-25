package com.tracemark.watermark.geo

import com.tracemark.watermark.model.AddressLevel
import com.tracemark.watermark.model.CoordinateSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

interface ReverseGeocoder {
    val info: ProviderInfo
    suspend fun reverse(lat: Double, lon: Double, level: AddressLevel, key: String): GeocodeResult
}

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    fun url(base: String, build: HttpUrl.Builder.() -> Unit): HttpUrl =
        (base.toHttpUrlOrNull() ?: throw IOException("无效地址")).newBuilder().apply(build).build()

    suspend fun get(url: HttpUrl, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "TraceMark/1.0 (Android watermark camera)")
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                body
            }
        }
}

private fun JSONObject.str(key: String): String {
    val value = opt(key) ?: return ""
    return when (value) {
        is String -> if (value == "[]" || value == "null") "" else value
        is JSONArray -> ""
        is JSONObject -> ""
        else -> value.toString()
    }
}

private fun doubleOrNull(obj: JSONObject, key: String): Double? = when (val v = obj.opt(key)) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

private fun precisionOf(
    number: String,
    street: String,
    town: String,
    district: String,
    city: String
): GeoPrecision = when {
    number.isNotBlank() -> GeoPrecision.DOOR
    street.isNotBlank() -> GeoPrecision.STREET
    town.isNotBlank() -> GeoPrecision.TOWN
    district.isNotBlank() -> GeoPrecision.DISTRICT
    city.isNotBlank() -> GeoPrecision.CITY
    else -> GeoPrecision.PROVINCE
}

private fun coordinate(lat: Double, lon: Double): String =
    String.format(Locale.US, "%.6f,%.6f", lon, lat)

/** 高德 Web 服务逆地理编码，国内门牌级精度最佳。 */
class AmapProvider : ReverseGeocoder {
    override val info = ProviderInfo(
        id = "amap",
        name = "高德地图",
        requiresKey = true,
        keyLabel = "Web服务 Key",
        keyHint = "在高德开放平台创建「Web服务」类型的 Key",
        keyUrl = "https://console.amap.com/dev/key/app",
        note = "门牌 + AOI + 最近 POI，国内精度最高，推荐首选"
    )

    override suspend fun reverse(lat: Double, lon: Double, level: AddressLevel, key: String): GeocodeResult {
        val (glat, glon) = CoordTransform.wgs84ToGcj02(lat, lon)
        val url = Http.url("https://restapi.amap.com/v3/geocode/regeo") {
            addQueryParameter("key", key)
            addQueryParameter("location", coordinate(glat, glon))
            addQueryParameter("extensions", "all")
            addQueryParameter("radius", "300")
            addQueryParameter("roadlevel", "0")
        }
        val root = JSONObject(Http.get(url))
        if (root.str("status") != "1") throw IOException("高德返回错误：${root.str("info")}")

        val rg = root.optJSONObject("regeocode") ?: throw IOException("高德无结果")
        val comp = rg.optJSONObject("addressComponent") ?: JSONObject()
        val streetNumber = comp.optJSONObject("streetNumber") ?: JSONObject()
        val province = comp.str("province")
        val city = comp.str("city").ifBlank { province }
        val district = comp.str("district")
        val town = comp.str("township")
        val street = streetNumber.str("street")
        val number = streetNumber.str("number")

        val pois = mutableListOf<GeoPoi>()
        rg.optJSONArray("pois")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.str("name")
                if (name.isNotBlank()) {
                    pois += GeoPoi(
                        name = name,
                        distanceMeters = doubleOrNull(o, "distance"),
                        address = o.str("address"),
                        category = o.str("type")
                    )
                }
            }
        }
        rg.optJSONArray("aois")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.str("name")
                if (name.isNotBlank() && pois.none { it.name == name }) {
                    pois += GeoPoi(
                        name = name,
                        distanceMeters = doubleOrNull(o, "distance"),
                        address = o.str("address"),
                        category = "AOI"
                    )
                }
            }
        }
        pois.sortBy { it.distanceMeters ?: Double.MAX_VALUE }

        val formatted = rg.str("formatted_address")
        val base = when (level) {
            AddressLevel.STREET -> cnJoin(province, city, district, town, street)
            AddressLevel.DOOR -> cnJoin(province, city, district, town, street, number)
            AddressLevel.POI -> formatted.ifBlank { cnJoin(province, city, district, town, street, number) }
        }
        val nearest = pois.firstOrNull()
        val poiSuffix = if (level == AddressLevel.POI && nearest != null &&
            (nearest.distanceMeters ?: 999.0) <= 120 && !base.contains(nearest.name)
        ) " · ${nearest.name}" else ""

        return GeocodeResult(
            providerId = info.id,
            providerName = info.name,
            address = (base + poiSuffix).ifBlank { cnJoin(province, city, district) },
            addressShort = cnJoin(district, town, street, number).ifBlank { base },
            province = province,
            city = city,
            district = district,
            town = town,
            street = street,
            number = number,
            poiName = nearest?.name.orEmpty(),
            pois = pois.take(8),
            precision = precisionOf(number, street, town, district, city),
            coordSystem = CoordinateSystem.GCJ02,
            extra = formatted
        )
    }
}

/** 百度地图逆地理编码，支持 wgs84ll 直接输入，无需本地纠偏。 */
class BaiduProvider : ReverseGeocoder {
    override val info = ProviderInfo(
        id = "baidu",
        name = "百度地图",
        requiresKey = true,
        keyLabel = "浏览器端 AK",
        keyHint = "在百度地图开放平台创建「服务端」或「浏览器端」AK，需开启逆地理编码",
        keyUrl = "https://lbsyun.baidu.com/apiconsole/key",
        note = "国内门牌级，支持语义化描述（附近地标）"
    )

    override suspend fun reverse(lat: Double, lon: Double, level: AddressLevel, key: String): GeocodeResult {
        val url = Http.url("https://api.map.baidu.com/reverse_geocoding/v3/") {
            addQueryParameter("ak", key)
            addQueryParameter("output", "json")
            addQueryParameter("coordtype", "wgs84ll")
            addQueryParameter("location", String.format(Locale.US, "%.6f,%.6f", lat, lon))
            addQueryParameter("extensions_poi", "1")
            addQueryParameter("radius", "300")
            addQueryParameter("latest_admin", "1")
        }
        val root = JSONObject(Http.get(url))
        if (root.optInt("status", -1) != 0) throw IOException("百度返回错误：${root.str("message")}")
        val result = root.optJSONObject("result") ?: throw IOException("百度无结果")
        val comp = result.optJSONObject("addressComponent") ?: JSONObject()
        val province = comp.str("province")
        val city = comp.str("city")
        val district = comp.str("district")
        val town = comp.str("town")
        val street = comp.str("street")
        val number = comp.str("street_number")

        val pois = mutableListOf<GeoPoi>()
        result.optJSONArray("pois")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.str("name")
                if (name.isNotBlank()) {
                    pois += GeoPoi(
                        name = name,
                        distanceMeters = doubleOrNull(o, "distance"),
                        address = o.str("addr"),
                        category = o.str("poi_types")
                    )
                }
            }
        }
        pois.sortBy { it.distanceMeters ?: Double.MAX_VALUE }

        val formatted = result.str("formatted_address")
        val base = when (level) {
            AddressLevel.STREET -> cnJoin(province, city, district, town, street)
            AddressLevel.DOOR -> cnJoin(province, city, district, town, street, number)
            AddressLevel.POI -> formatted.ifBlank { cnJoin(province, city, district, town, street, number) }
        }
        val nearest = pois.firstOrNull()
        val poiSuffix = if (level == AddressLevel.POI && nearest != null &&
            (nearest.distanceMeters ?: 999.0) <= 120 && !base.contains(nearest.name)
        ) " · ${nearest.name}" else ""
        val semantic = result.str("sematic_description")

        return GeocodeResult(
            providerId = info.id,
            providerName = info.name,
            address = (base + poiSuffix).ifBlank { formatted },
            addressShort = cnJoin(district, town, street, number).ifBlank { base },
            province = province,
            city = city,
            district = district,
            town = town,
            street = street,
            number = number,
            poiName = nearest?.name.orEmpty(),
            pois = pois.take(8),
            precision = precisionOf(number, street, town, district, city),
            coordSystem = CoordinateSystem.WGS84,
            extra = semantic
        )
    }
}

/** 腾讯位置服务，GCJ-02 输入。 */
class TencentProvider : ReverseGeocoder {
    override val info = ProviderInfo(
        id = "tencent",
        name = "腾讯地图",
        requiresKey = true,
        keyLabel = "WebService Key",
        keyHint = "在腾讯位置服务控制台创建 Key，勾选「逆地址解析」",
        keyUrl = "https://lbs.qq.com/dev/console/application/mine",
        note = "门牌级，返回推荐地址与周边 POI"
    )

    override suspend fun reverse(lat: Double, lon: Double, level: AddressLevel, key: String): GeocodeResult {
        val (glat, glon) = CoordTransform.wgs84ToGcj02(lat, lon)
        val url = Http.url("https://apis.map.qq.com/ws/geocoder/v1/") {
            addQueryParameter("key", key)
            addQueryParameter("location", String.format(Locale.US, "%.6f,%.6f", glat, glon))
            addQueryParameter("get_poi", "1")
            addQueryParameter("poi_options", "radius=300;page_size=8")
        }
        val root = JSONObject(Http.get(url))
        if (root.optInt("status", -1) != 0) throw IOException("腾讯返回错误：${root.str("message")}")
        val result = root.optJSONObject("result") ?: throw IOException("腾讯无结果")
        val comp = result.optJSONObject("address_component") ?: JSONObject()
        val province = comp.str("province")
        val city = comp.str("city")
        val district = comp.str("district")
        val street = comp.str("street")
        val number = comp.str("street_number")
        val town = comp.optJSONObject("address_reference")?.optJSONObject("town")?.str("title").orEmpty()

        val pois = mutableListOf<GeoPoi>()
        result.optJSONArray("pois")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.str("title")
                if (name.isNotBlank()) {
                    pois += GeoPoi(
                        name = name,
                        distanceMeters = doubleOrNull(o, "distance"),
                        address = o.str("address"),
                        category = o.str("category")
                    )
                }
            }
        }
        pois.sortBy { it.distanceMeters ?: Double.MAX_VALUE }

        val recommended = result.optJSONObject("formatted_addresses")?.str("recommend").orEmpty()
        val base = when (level) {
            AddressLevel.STREET -> cnJoin(province, city, district, town, street)
            AddressLevel.DOOR -> cnJoin(province, city, district, town, street, number)
            AddressLevel.POI -> result.str("address").ifBlank {
                recommended.ifBlank { cnJoin(province, city, district, town, street, number) }
            }
        }
        val nearest = pois.firstOrNull()
        val poiSuffix = if (level == AddressLevel.POI && nearest != null &&
            (nearest.distanceMeters ?: 999.0) <= 120 && !base.contains(nearest.name)
        ) " · ${nearest.name}" else ""

        return GeocodeResult(
            providerId = info.id,
            providerName = info.name,
            address = (base + poiSuffix).ifBlank { recommended },
            addressShort = cnJoin(district, town, street, number).ifBlank { base },
            province = province,
            city = city,
            district = district,
            town = town,
            street = street,
            number = number,
            poiName = nearest?.name.orEmpty(),
            pois = pois.take(8),
            precision = precisionOf(number, street, town, district, city),
            coordSystem = CoordinateSystem.GCJ02,
            extra = recommended
        )
    }
}

/** 天地图（国家地理信息公共服务平台），CGCS2000≈WGS84。 */
class TiandituProvider : ReverseGeocoder {
    override val info = ProviderInfo(
        id = "tianditu",
        name = "天地图",
        requiresKey = true,
        keyLabel = "服务端 tk",
        keyHint = "在国家地理信息公共服务平台申请「服务端」类型 tk",
        keyUrl = "https://console.tianditu.gov.cn/api/key",
        note = "国家平台，地址规范，区县/乡镇精度稳定"
    )

    override suspend fun reverse(lat: Double, lon: Double, level: AddressLevel, key: String): GeocodeResult {
        val postStr = JSONObject().apply {
            put("lon", lon)
            put("lat", lat)
            put("ver", 1)
        }.toString()
        val url = Http.url("https://api.tianditu.gov.cn/geocoder") {
            addQueryParameter("postStr", postStr)
            addQueryParameter("type", "geocode")
            addQueryParameter("tk", key)
        }
        val root = JSONObject(Http.get(url))
        val result = root.optJSONObject("result") ?: throw IOException("天地图返回错误：${root.str("msg")}")
        val comp = result.optJSONObject("addressComponent") ?: JSONObject()
        val province = comp.str("province")
        val city = comp.str("city")
        val district = comp.str("county")
        val town = comp.str("town")
        val street = comp.str("street")
        val detail = comp.str("address")
        val number = if (detail.contains("号")) detail else ""
        val formatted = result.str("formatted_address")

        val pois = mutableListOf<GeoPoi>()
        result.optJSONArray("pois")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.str("name")
                if (name.isNotBlank()) {
                    pois += GeoPoi(name = name, address = o.str("address"), distanceMeters = doubleOrNull(o, "distance"))
                }
            }
        }

        val base = when (level) {
            AddressLevel.STREET -> cnJoin(province, city, district, town, street)
            AddressLevel.DOOR -> cnJoin(province, city, district, town, street, detail)
            AddressLevel.POI -> formatted.ifBlank { cnJoin(province, city, district, town, street, detail) }
        }

        return GeocodeResult(
            providerId = info.id,
            providerName = info.name,
            address = base.ifBlank { formatted },
            addressShort = cnJoin(district, town, street, detail).ifBlank { base },
            province = province,
            city = city,
            district = district,
            town = town,
            street = street,
            number = number,
            poiName = pois.firstOrNull()?.name.orEmpty(),
            pois = pois.take(8),
            precision = precisionOf(number, street, town, district, city),
            coordSystem = CoordinateSystem.WGS84,
            extra = formatted
        )
    }
}

/** Mapbox 逆地理编码，全球可用，中国细节略弱于高德。 */
class MapboxProvider : ReverseGeocoder {
    override val info = ProviderInfo(
        id = "mapbox",
        name = "Mapbox",
        requiresKey = true,
        keyLabel = "Access Token",
        keyHint = "在 Mapbox 控制台创建默认公共 Token",
        keyUrl = "https://account.mapbox.com/access-tokens/",
        note = "全球覆盖，海外照片推荐，门牌级精度"
    )

    override suspend fun reverse(lat: Double, lon: Double, level: AddressLevel, key: String): GeocodeResult {
        val url = Http.url(
            "https://api.mapbox.com/geocoding/v5/mapbox.places/" +
                String.format(Locale.US, "%.6f,%.6f", lon, lat) + ".json"
        ) {
            addQueryParameter("access_token", key)
            addQueryParameter("language", "zh")
            addQueryParameter("limit", "3")
            addQueryParameter("types", "address,poi,place,locality,neighborhood,district,region")
        }
        val root = JSONObject(Http.get(url))
        val features = root.optJSONArray("features") ?: throw IOException("Mapbox 无结果")
        if (features.length() == 0) throw IOException("Mapbox 无结果")
        val first = features.getJSONObject(0)
        val placeName = first.str("place_name")
        val text = first.str("text")
        val addressNumber = first.str("address")
        val placeTypes = first.optJSONArray("place_type")

        var street = ""
        var district = ""
        var city = ""
        var province = ""
        var country = ""
        val pois = mutableListOf<GeoPoi>()
        first.optJSONArray("context")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.str("id")
                val txt = o.str("text")
                when {
                    id.startsWith("region") -> province = txt
                    id.startsWith("district") -> district = txt
                    id.startsWith("place") -> city = txt
                    id.startsWith("country") -> country = txt
                    id.startsWith("neighborhood") -> if (district.isBlank()) district = txt
                }
            }
        }
        if (placeTypes != null && (0 until placeTypes.length()).any {
                placeTypes.optString(it) == "poi"
            }
        ) {
            pois += GeoPoi(name = text, category = "POI")
        }
        if (addressNumber.isBlank()) street = text
        val composed = if (country.contains("中国") || country.equals("China", true)) {
            cnJoin(province, city, district, if (street.isNotBlank() && street != text) street else "", addressNumber)
                .ifBlank { placeName }
        } else {
            placeName
        }
        val precision = when {
            addressNumber.isNotBlank() -> GeoPrecision.DOOR
            placeTypes != null && (0 until placeTypes.length()).any { placeTypes.optString(it) == "poi" } -> GeoPrecision.POI
            city.isNotBlank() -> GeoPrecision.CITY
            province.isNotBlank() -> GeoPrecision.PROVINCE
            else -> GeoPrecision.STREET
        }
        return GeocodeResult(
            providerId = info.id,
            providerName = info.name,
            address = composed,
            addressShort = cnJoin(district, city, street, addressNumber).ifBlank { composed },
            province = province,
            city = city,
            district = district,
            street = street,
            number = addressNumber,
            poiName = text,
            pois = pois,
            precision = precision,
            coordSystem = CoordinateSystem.WGS84,
            extra = placeName
        )
    }
}

/** Geoapify 逆地理编码。 */
class GeoapifyProvider : ReverseGeocoder {
    override val info = ProviderInfo(
        id = "geoapify",
        name = "Geoapify",
        requiresKey = true,
        keyLabel = "API Key",
        keyHint = "在 Geoapify 控制台创建免费 API Key",
        keyUrl = "https://myprojects.geoapify.com/",
        note = "全球门牌级，支持中文输出，免费额度充足"
    )

    override suspend fun reverse(lat: Double, lon: Double, level: AddressLevel, key: String): GeocodeResult {
        val url = Http.url("https://api.geoapify.com/v1/geocode/reverse") {
            addQueryParameter("lat", String.format(Locale.US, "%.6f", lat))
            addQueryParameter("lon", String.format(Locale.US, "%.6f", lon))
            addQueryParameter("lang", "zh")
            addQueryParameter("limit", "3")
            addQueryParameter("apiKey", key)
        }
        val root = JSONObject(Http.get(url))
        val features = root.optJSONArray("features") ?: throw IOException("Geoapify 无结果")
        if (features.length() == 0) throw IOException("Geoapify 无结果")
        val props = features.getJSONObject(0).optJSONObject("properties") ?: JSONObject()
        val formatted = props.str("formatted").ifBlank { props.str("address_line1") }
        val houseNumber = props.str("housenumber")
        val street = props.str("street")
        val district = props.str("district")
        val city = props.str("city").ifBlank { props.str("county") }
        val state = props.str("state")
        val poiName = props.str("name")
        val pois = if (poiName.isNotBlank()) listOf(GeoPoi(poiName)) else emptyList()
        val composed = formatted.ifBlank { cnJoin(state, city, district, street, houseNumber) }
        val precision = when {
            houseNumber.isNotBlank() -> GeoPrecision.DOOR
            street.isNotBlank() -> GeoPrecision.STREET
            district.isNotBlank() -> GeoPrecision.DISTRICT
            city.isNotBlank() -> GeoPrecision.CITY
            state.isNotBlank() -> GeoPrecision.PROVINCE
            else -> GeoPrecision.CITY
        }
        return GeocodeResult(
            providerId = info.id,
            providerName = info.name,
            address = composed,
            addressShort = cnJoin(district, street, houseNumber).ifBlank { composed },
            province = state,
            city = city,
            district = district,
            street = street,
            number = houseNumber,
            poiName = poiName,
            pois = pois,
            precision = precision,
            coordSystem = CoordinateSystem.WGS84,
            extra = props.str("address_line2")
        )
    }
}

/** OpenStreetMap Nominatim，无需 Key，注意使用频率。 */
class NominatimProvider : ReverseGeocoder {
    override val info = ProviderInfo(
        id = "nominatim",
        name = "OpenStreetMap",
        requiresKey = false,
        keyLabel = "",
        keyHint = "",
        keyUrl = "https://nominatim.openstreetmap.org/",
        note = "免费无需 Key，全球覆盖；国内细节一般，适合作为兜底"
    )

    override suspend fun reverse(lat: Double, lon: Double, level: AddressLevel, key: String): GeocodeResult {
        val url = Http.url("https://nominatim.openstreetmap.org/reverse") {
            addQueryParameter("format", "jsonv2")
            addQueryParameter("lat", String.format(Locale.US, "%.6f", lat))
            addQueryParameter("lon", String.format(Locale.US, "%.6f", lon))
            addQueryParameter("zoom", "18")
            addQueryParameter("addressdetails", "1")
            addQueryParameter("namedetails", "1")
            addQueryParameter("accept-language", "zh-CN")
        }
        val root = JSONObject(Http.get(url))
        val address = root.optJSONObject("address") ?: JSONObject()
        val province = address.str("state").ifBlank { address.str("region") }
        val city = address.str("city").ifBlank { address.str("town") }
            .ifBlank { address.str("village") }.ifBlank { address.str("county") }
        val district = address.str("district").ifBlank { address.str("city_district") }
            .ifBlank { address.str("suburb") }
        val town = address.str("town").ifBlank { address.str("suburb") }
            .ifBlank { address.str("neighbourhood") }
        val street = address.str("road").ifBlank { address.str("pedestrian") }
        val number = address.str("house_number")
        val country = address.str("country")
        val poiName = listOf("amenity", "shop", "tourism", "leisure", "building")
            .map { address.str(it) }
            .firstOrNull { it.isNotBlank() }
            ?: root.str("name")
        val formatted = root.str("display_name")
        val isChina = country.contains("中国") || country.equals("China", true)
        val composed = if (isChina || country.isBlank()) {
            cnJoin(province, city, district, street, number).ifBlank { formatted }
        } else {
            listOfNotNull(poiName.ifBlank { null }, street, number, district, city, province, country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }
        val precision = precisionOf(number, street, town, district, city)
        return GeocodeResult(
            providerId = info.id,
            providerName = info.name,
            address = composed,
            addressShort = cnJoin(district, street, number).ifBlank { composed },
            province = province,
            city = city,
            district = district,
            town = town,
            street = street,
            number = number,
            poiName = poiName,
            pois = if (poiName.isNotBlank()) listOf(GeoPoi(poiName)) else emptyList(),
            precision = precision,
            coordSystem = CoordinateSystem.WGS84,
            extra = formatted
        )
    }
}

/** Photon（Komoot），免 Key，响应快。 */
class PhotonProvider : ReverseGeocoder {
    override val info = ProviderInfo(
        id = "photon",
        name = "Photon",
        requiresKey = false,
        keyLabel = "",
        keyHint = "",
        keyUrl = "https://photon.komoot.io/",
        note = "免费无需 Key，响应快，海外表现好"
    )

    override suspend fun reverse(lat: Double, lon: Double, level: AddressLevel, key: String): GeocodeResult {
        val url = Http.url("https://photon.komoot.io/reverse") {
            addQueryParameter("lat", String.format(Locale.US, "%.6f", lat))
            addQueryParameter("lon", String.format(Locale.US, "%.6f", lon))
            addQueryParameter("lang", "zh")
            addQueryParameter("limit", "3")
        }
        val root = JSONObject(Http.get(url))
        val features = root.optJSONArray("features") ?: throw IOException("Photon 无结果")
        if (features.length() == 0) throw IOException("Photon 无结果")
        val props = features.getJSONObject(0).optJSONObject("properties") ?: JSONObject()
        val name = props.str("name")
        val street = props.str("street")
        val number = props.str("housenumber")
        val city = props.str("city").ifBlank { props.str("locality") }
        val district = props.str("district").ifBlank { props.str("locality") }
        val province = props.str("state")
        val country = props.str("country")
        val isChina = country.contains("中国") || country.equals("China", true)
        val composed = if (isChina || country.isBlank()) {
            cnJoin(province, city, district, street, number).ifBlank {
                listOfNotNull(name, street, city).joinToString("")
            }
        } else {
            listOfNotNull(name, street, number, city, province, country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }
        val precision = when {
            number.isNotBlank() -> GeoPrecision.DOOR
            street.isNotBlank() -> GeoPrecision.STREET
            name.isNotBlank() && street.isBlank() -> GeoPrecision.POI
            district.isNotBlank() -> GeoPrecision.DISTRICT
            city.isNotBlank() -> GeoPrecision.CITY
            else -> GeoPrecision.PROVINCE
        }
        return GeocodeResult(
            providerId = info.id,
            providerName = info.name,
            address = composed,
            addressShort = cnJoin(district, street, number).ifBlank { composed },
            province = province,
            city = city,
            district = district,
            street = street,
            number = number,
            poiName = name,
            pois = if (name.isNotBlank()) listOf(GeoPoi(name)) else emptyList(),
            precision = precision,
            coordSystem = CoordinateSystem.WGS84,
            extra = name
        )
    }
}

/** BigDataCloud，免 Key，精度较粗，作为最后的兜底。 */
class BigDataCloudProvider : ReverseGeocoder {
    override val info = ProviderInfo(
        id = "bigdatacloud",
        name = "BigDataCloud",
        requiresKey = false,
        keyLabel = "",
        keyHint = "",
        keyUrl = "https://www.bigdatacloud.com/",
        note = "免费无需 Key，仅到区县/街区级别，应急兜底"
    )

    override suspend fun reverse(lat: Double, lon: Double, level: AddressLevel, key: String): GeocodeResult {
        val url = Http.url("https://api.bigdatacloud.net/data/reverse-geocode-client") {
            addQueryParameter("latitude", String.format(Locale.US, "%.6f", lat))
            addQueryParameter("longitude", String.format(Locale.US, "%.6f", lon))
            addQueryParameter("localityLanguage", "zh")
        }
        val root = JSONObject(Http.get(url))
        val province = root.str("principalSubdivision")
        val city = root.str("city")
        val locality = root.str("locality")
        val country = root.str("countryName")
        val isChina = country.contains("中国") || country.equals("China", true)
        val composed = if (isChina || country.isBlank()) {
            cnJoin(province, city, locality)
        } else {
            listOfNotNull(locality, city, province, country).filter { it.isNotBlank() }.joinToString(", ")
        }
        if (composed.isBlank()) throw IOException("BigDataCloud 无结果")
        return GeocodeResult(
            providerId = info.id,
            providerName = info.name,
            address = composed,
            addressShort = cnJoin(city, locality).ifBlank { composed },
            province = province,
            city = city,
            district = locality,
            precision = if (locality.isNotBlank()) GeoPrecision.TOWN else GeoPrecision.CITY,
            coordSystem = CoordinateSystem.WGS84,
            extra = composed
        )
    }
}

object GeoProviderRegistry {
    val all: List<ReverseGeocoder> = listOf(
        AmapProvider(),
        BaiduProvider(),
        TencentProvider(),
        TiandituProvider(),
        MapboxProvider(),
        GeoapifyProvider(),
        PhotonProvider(),
        NominatimProvider(),
        BigDataCloudProvider()
    )

    fun byId(id: String): ReverseGeocoder? = all.firstOrNull { it.info.id == id }

    fun info(id: String): ProviderInfo? = byId(id)?.info
}
