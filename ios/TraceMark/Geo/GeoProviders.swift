import Foundation

protocol ReverseGeocoder {
    var info: ProviderInfo { get }
    func reverse(lat: Double, lon: Double, level: AddressLevel, key: String) async throws -> GeocodeResult
}

enum HTTP {
    static func url(_ base: String, _ items: [(String, String)]) throws -> URL {
        guard var components = URLComponents(string: base) else { throw GeoError.badURL }
        components.queryItems = items.map { URLQueryItem(name: $0.0, value: $0.1) }
        guard let url = components.url else { throw GeoError.badURL }
        return url
    }

    static func getJSON(_ url: URL, headers: [String: String] = [:]) async throws -> [String: Any] {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("TraceMark/1.0 (iOS watermark camera)", forHTTPHeaderField: "User-Agent")
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? -1
        guard (200..<300).contains(status) else { throw GeoError.requestFailed(status) }
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw GeoError.invalidResponse
        }
        return object
    }
}

private func coordinate(_ lat: Double, _ lon: Double) -> String {
    String(format: "%.6f,%.6f", lon, lat)
}

struct AmapProvider: ReverseGeocoder {
    let info = ProviderInfo(
        id: "amap",
        name: "高德地图",
        requiresKey: true,
        keyLabel: "Web服务 Key",
        keyHint: "在高德开放平台创建「Web服务」类型的 Key",
        keyUrl: "https://console.amap.com/dev/key/app",
        note: "门牌 + AOI + 最近 POI，国内精度最高，推荐首选"
    )

    func reverse(lat: Double, lon: Double, level: AddressLevel, key: String) async throws -> GeocodeResult {
        let gcj = CoordTransform.wgs84ToGcj02(lat: lat, lon: lon)
        let url = try HTTP.url("https://restapi.amap.com/v3/geocode/regeo", [
            ("key", key),
            ("location", coordinate(gcj.lat, gcj.lon)),
            ("extensions", "all"),
            ("radius", "300"),
            ("roadlevel", "0"),
        ])
        let root = try await HTTP.getJSON(url)
        guard root.str("status") == "1" else {
            throw GeoError.message("高德返回错误：\(root.str("info"))")
        }
        guard let rg = root.optDict("regeocode") else { throw GeoError.noResult("高德") }
        let comp = rg.dict("addressComponent")
        let streetNumber = comp.dict("streetNumber")
        let province = comp.str("province")
        let city = comp.str("city").orIfBlank(province)
        let district = comp.str("district")
        let town = comp.str("township")
        let street = streetNumber.str("street")
        let number = streetNumber.str("number")

        var pois: [GeoPoi] = []
        for poi in rg.array("pois") {
            let name = poi.str("name")
            guard !name.isEmpty else { continue }
            pois.append(GeoPoi(
                name: name,
                distanceMeters: poi.dbl("distance"),
                address: poi.str("address"),
                category: poi.str("type")
            ))
        }
        for aoi in rg.array("aois") {
            let name = aoi.str("name")
            guard !name.isEmpty, !pois.contains(where: { $0.name == name }) else { continue }
            pois.append(GeoPoi(
                name: name,
                distanceMeters: aoi.dbl("distance"),
                address: aoi.str("address"),
                category: "AOI"
            ))
        }
        pois.sort { ($0.distanceMeters ?? .greatestFiniteMagnitude) < ($1.distanceMeters ?? .greatestFiniteMagnitude) }

        let formatted = rg.str("formatted_address")
        let base: String
        switch level {
        case .street: base = cnJoin(province, city, district, town, street)
        case .door: base = cnJoin(province, city, district, town, street, number)
        case .poi: base = formatted.orIfBlank(cnJoin(province, city, district, town, street, number))
        }
        let nearest = pois.first
        let poiSuffix: String
        if level == .poi, let nearest, (nearest.distanceMeters ?? 999) <= 120, !base.contains(nearest.name) {
            poiSuffix = " · \(nearest.name)"
        } else {
            poiSuffix = ""
        }

        return GeocodeResult(
            providerId: info.id,
            providerName: info.name,
            address: (base + poiSuffix).orIfBlank(cnJoin(province, city, district)),
            addressShort: cnJoin(district, town, street, number).orIfBlank(base),
            province: province,
            city: city,
            district: district,
            town: town,
            street: street,
            number: number,
            poiName: nearest?.name ?? "",
            pois: Array(pois.prefix(8)),
            precision: precisionOf(number: number, street: street, town: town, district: district, city: city),
            coordSystem: .gcj02,
            extra: formatted
        )
    }
}

struct BaiduProvider: ReverseGeocoder {
    let info = ProviderInfo(
        id: "baidu",
        name: "百度地图",
        requiresKey: true,
        keyLabel: "浏览器端 AK",
        keyHint: "在百度地图开放平台创建「服务端」或「浏览器端」AK，需开启逆地理编码",
        keyUrl: "https://lbsyun.baidu.com/apiconsole/key",
        note: "国内门牌级，支持语义化描述（附近地标）"
    )

    func reverse(lat: Double, lon: Double, level: AddressLevel, key: String) async throws -> GeocodeResult {
        let url = try HTTP.url("https://api.map.baidu.com/reverse_geocoding/v3/", [
            ("ak", key),
            ("output", "json"),
            ("coordtype", "wgs84ll"),
            ("location", String(format: "%.6f,%.6f", lat, lon)),
            ("extensions_poi", "1"),
            ("radius", "300"),
            ("latest_admin", "1"),
        ])
        let root = try await HTTP.getJSON(url)
        guard root.intValue("status") == 0 else {
            throw GeoError.message("百度返回错误：\(root.str("message"))")
        }
        guard let result = root.optDict("result") else { throw GeoError.noResult("百度") }
        let comp = result.dict("addressComponent")
        let province = comp.str("province")
        let city = comp.str("city")
        let district = comp.str("district")
        let town = comp.str("town")
        let street = comp.str("street")
        let number = comp.str("street_number")

        var pois: [GeoPoi] = []
        for poi in result.array("pois") {
            let name = poi.str("name")
            guard !name.isEmpty else { continue }
            pois.append(GeoPoi(
                name: name,
                distanceMeters: poi.dbl("distance"),
                address: poi.str("addr"),
                category: poi.str("poi_types")
            ))
        }
        pois.sort { ($0.distanceMeters ?? .greatestFiniteMagnitude) < ($1.distanceMeters ?? .greatestFiniteMagnitude) }

        let formatted = result.str("formatted_address")
        let base: String
        switch level {
        case .street: base = cnJoin(province, city, district, town, street)
        case .door: base = cnJoin(province, city, district, town, street, number)
        case .poi: base = formatted.orIfBlank(cnJoin(province, city, district, town, street, number))
        }
        let nearest = pois.first
        let poiSuffix: String
        if level == .poi, let nearest, (nearest.distanceMeters ?? 999) <= 120, !base.contains(nearest.name) {
            poiSuffix = " · \(nearest.name)"
        } else {
            poiSuffix = ""
        }

        return GeocodeResult(
            providerId: info.id,
            providerName: info.name,
            address: (base + poiSuffix).orIfBlank(formatted),
            addressShort: cnJoin(district, town, street, number).orIfBlank(base),
            province: province,
            city: city,
            district: district,
            town: town,
            street: street,
            number: number,
            poiName: nearest?.name ?? "",
            pois: Array(pois.prefix(8)),
            precision: precisionOf(number: number, street: street, town: town, district: district, city: city),
            coordSystem: .wgs84,
            extra: result.str("sematic_description")
        )
    }
}

struct TencentProvider: ReverseGeocoder {
    let info = ProviderInfo(
        id: "tencent",
        name: "腾讯地图",
        requiresKey: true,
        keyLabel: "WebService Key",
        keyHint: "在腾讯位置服务控制台创建 Key，勾选「逆地址解析」",
        keyUrl: "https://lbs.qq.com/dev/console/application/mine",
        note: "门牌级，返回推荐地址与周边 POI"
    )

    func reverse(lat: Double, lon: Double, level: AddressLevel, key: String) async throws -> GeocodeResult {
        let gcj = CoordTransform.wgs84ToGcj02(lat: lat, lon: lon)
        let url = try HTTP.url("https://apis.map.qq.com/ws/geocoder/v1/", [
            ("key", key),
            ("location", String(format: "%.6f,%.6f", gcj.lat, gcj.lon)),
            ("get_poi", "1"),
            ("poi_options", "radius=300;page_size=8"),
        ])
        let root = try await HTTP.getJSON(url)
        guard root.intValue("status") == 0 else {
            throw GeoError.message("腾讯返回错误：\(root.str("message"))")
        }
        guard let result = root.optDict("result") else { throw GeoError.noResult("腾讯") }
        let comp = result.dict("address_component")
        let province = comp.str("province")
        let city = comp.str("city")
        let district = comp.str("district")
        let street = comp.str("street")
        let number = comp.str("street_number")
        let town = result.dict("address_reference").dict("town").str("title")

        var pois: [GeoPoi] = []
        for poi in result.array("pois") {
            let name = poi.str("title")
            guard !name.isEmpty else { continue }
            pois.append(GeoPoi(
                name: name,
                distanceMeters: poi.dbl("distance"),
                address: poi.str("address"),
                category: poi.str("category")
            ))
        }
        pois.sort { ($0.distanceMeters ?? .greatestFiniteMagnitude) < ($1.distanceMeters ?? .greatestFiniteMagnitude) }

        let recommended = result.dict("formatted_addresses").str("recommend")
        let base: String
        switch level {
        case .street: base = cnJoin(province, city, district, town, street)
        case .door: base = cnJoin(province, city, district, town, street, number)
        case .poi: base = result.str("address").orIfBlank(
            recommended.orIfBlank(cnJoin(province, city, district, town, street, number))
        )
        }
        let nearest = pois.first
        let poiSuffix: String
        if level == .poi, let nearest, (nearest.distanceMeters ?? 999) <= 120, !base.contains(nearest.name) {
            poiSuffix = " · \(nearest.name)"
        } else {
            poiSuffix = ""
        }

        return GeocodeResult(
            providerId: info.id,
            providerName: info.name,
            address: (base + poiSuffix).orIfBlank(recommended),
            addressShort: cnJoin(district, town, street, number).orIfBlank(base),
            province: province,
            city: city,
            district: district,
            town: town,
            street: street,
            number: number,
            poiName: nearest?.name ?? "",
            pois: Array(pois.prefix(8)),
            precision: precisionOf(number: number, street: street, town: town, district: district, city: city),
            coordSystem: .gcj02,
            extra: recommended
        )
    }
}

struct TiandituProvider: ReverseGeocoder {
    let info = ProviderInfo(
        id: "tianditu",
        name: "天地图",
        requiresKey: true,
        keyLabel: "服务端 tk",
        keyHint: "在国家地理信息公共服务平台申请「服务端」类型 tk",
        keyUrl: "https://console.tianditu.gov.cn/api/key",
        note: "国家平台，地址规范，区县/乡镇精度稳定"
    )

    func reverse(lat: Double, lon: Double, level: AddressLevel, key: String) async throws -> GeocodeResult {
        let post: [String: Any] = ["lon": lon, "lat": lat, "ver": 1]
        let data = try JSONSerialization.data(withJSONObject: post)
        let postStr = String(data: data, encoding: .utf8) ?? "{}"
        let url = try HTTP.url("https://api.tianditu.gov.cn/geocoder", [
            ("postStr", postStr),
            ("type", "geocode"),
            ("tk", key),
        ])
        let root = try await HTTP.getJSON(url)
        guard let result = root.optDict("result") else {
            throw GeoError.message("天地图返回错误：\(root.str("msg"))")
        }
        let comp = result.dict("addressComponent")
        let province = comp.str("province")
        let city = comp.str("city")
        let district = comp.str("county")
        let town = comp.str("town")
        let street = comp.str("street")
        let detail = comp.str("address")
        let number = detail.contains("号") ? detail : ""
        let formatted = result.str("formatted_address")

        var pois: [GeoPoi] = []
        for poi in result.array("pois") {
            let name = poi.str("name")
            guard !name.isEmpty else { continue }
            pois.append(GeoPoi(name: name, distanceMeters: poi.dbl("distance"), address: poi.str("address")))
        }

        let base: String
        switch level {
        case .street: base = cnJoin(province, city, district, town, street)
        case .door: base = cnJoin(province, city, district, town, street, detail)
        case .poi: base = formatted.orIfBlank(cnJoin(province, city, district, town, street, detail))
        }

        return GeocodeResult(
            providerId: info.id,
            providerName: info.name,
            address: base.orIfBlank(formatted),
            addressShort: cnJoin(district, town, street, detail).orIfBlank(base),
            province: province,
            city: city,
            district: district,
            town: town,
            street: street,
            number: number,
            poiName: pois.first?.name ?? "",
            pois: Array(pois.prefix(8)),
            precision: precisionOf(number: number, street: street, town: town, district: district, city: city),
            coordSystem: .wgs84,
            extra: formatted
        )
    }
}

struct MapboxProvider: ReverseGeocoder {
    let info = ProviderInfo(
        id: "mapbox",
        name: "Mapbox",
        requiresKey: true,
        keyLabel: "Access Token",
        keyHint: "在 Mapbox 控制台创建默认公共 Token",
        keyUrl: "https://account.mapbox.com/access-tokens/",
        note: "全球覆盖，海外照片推荐，门牌级精度"
    )

    func reverse(lat: Double, lon: Double, level: AddressLevel, key: String) async throws -> GeocodeResult {
        let path = String(format: "%.6f,%.6f", lon, lat)
        let url = try HTTP.url("https://api.mapbox.com/geocoding/v5/mapbox.places/\(path).json", [
            ("access_token", key),
            ("language", "zh"),
            ("limit", "3"),
            ("types", "address,poi,place,locality,neighborhood,district,region"),
        ])
        let root = try await HTTP.getJSON(url)
        let features = root.array("features")
        guard let first = features.first else { throw GeoError.noResult("Mapbox") }
        let placeName = first.str("place_name")
        let text = first.str("text")
        let addressNumber = first.str("address")
        let placeTypes = first.stringArray("place_type")

        var street = ""
        var district = ""
        var city = ""
        var province = ""
        var country = ""
        var pois: [GeoPoi] = []
        for context in first.array("context") {
            let id = context.str("id")
            let txt = context.str("text")
            if id.hasPrefix("region") {
                province = txt
            } else if id.hasPrefix("district") {
                district = txt
            } else if id.hasPrefix("place") {
                city = txt
            } else if id.hasPrefix("country") {
                country = txt
            } else if id.hasPrefix("neighborhood"), district.isEmpty {
                district = txt
            }
        }
        if placeTypes.contains("poi") {
            pois.append(GeoPoi(name: text, category: "POI"))
        }
        if addressNumber.isEmpty { street = text }
        let isChina = country.contains("中国") || country.caseInsensitiveCompare("China") == .orderedSame
        let composed: String
        if isChina {
            composed = cnJoin(
                province,
                city,
                district,
                street != text ? street : "",
                addressNumber
            ).orIfBlank(placeName)
        } else {
            composed = placeName
        }
        let precision: GeoPrecision
        if !addressNumber.isEmpty {
            precision = .door
        } else if placeTypes.contains("poi") {
            precision = .poi
        } else if !city.isEmpty {
            precision = .city
        } else if !province.isEmpty {
            precision = .province
        } else {
            precision = .street
        }
        return GeocodeResult(
            providerId: info.id,
            providerName: info.name,
            address: composed,
            addressShort: cnJoin(district, city, street, addressNumber).orIfBlank(composed),
            province: province,
            city: city,
            district: district,
            street: street,
            number: addressNumber,
            poiName: text,
            pois: pois,
            precision: precision,
            coordSystem: .wgs84,
            extra: placeName
        )
    }
}

struct GeoapifyProvider: ReverseGeocoder {
    let info = ProviderInfo(
        id: "geoapify",
        name: "Geoapify",
        requiresKey: true,
        keyLabel: "API Key",
        keyHint: "在 Geoapify 控制台创建免费 API Key",
        keyUrl: "https://myprojects.geoapify.com/",
        note: "全球门牌级，支持中文输出，免费额度充足"
    )

    func reverse(lat: Double, lon: Double, level: AddressLevel, key: String) async throws -> GeocodeResult {
        let url = try HTTP.url("https://api.geoapify.com/v1/geocode/reverse", [
            ("lat", String(format: "%.6f", lat)),
            ("lon", String(format: "%.6f", lon)),
            ("lang", "zh"),
            ("limit", "3"),
            ("apiKey", key),
        ])
        let root = try await HTTP.getJSON(url)
        guard let first = root.array("features").first else { throw GeoError.noResult("Geoapify") }
        let props = first.dict("properties")
        let formatted = props.str("formatted").orIfBlank(props.str("address_line1"))
        let houseNumber = props.str("housenumber")
        let street = props.str("street")
        let district = props.str("district")
        let city = props.str("city").orIfBlank(props.str("county"))
        let state = props.str("state")
        let poiName = props.str("name")
        let pois = poiName.isEmpty ? [] : [GeoPoi(name: poiName)]
        let composed = formatted.orIfBlank(cnJoin(state, city, district, street, houseNumber))
        let precision: GeoPrecision
        if !houseNumber.isEmpty {
            precision = .door
        } else if !street.isEmpty {
            precision = .street
        } else if !district.isEmpty {
            precision = .district
        } else if !city.isEmpty {
            precision = .city
        } else if !state.isEmpty {
            precision = .province
        } else {
            precision = .city
        }
        return GeocodeResult(
            providerId: info.id,
            providerName: info.name,
            address: composed,
            addressShort: cnJoin(district, street, houseNumber).orIfBlank(composed),
            province: state,
            city: city,
            district: district,
            street: street,
            number: houseNumber,
            poiName: poiName,
            pois: pois,
            precision: precision,
            coordSystem: .wgs84,
            extra: props.str("address_line2")
        )
    }
}

struct NominatimProvider: ReverseGeocoder {
    let info = ProviderInfo(
        id: "nominatim",
        name: "OpenStreetMap",
        requiresKey: false,
        keyLabel: "",
        keyHint: "",
        keyUrl: "https://nominatim.openstreetmap.org/",
        note: "免费无需 Key，全球覆盖；国内细节一般，适合作为兜底"
    )

    func reverse(lat: Double, lon: Double, level: AddressLevel, key: String) async throws -> GeocodeResult {
        let url = try HTTP.url("https://nominatim.openstreetmap.org/reverse", [
            ("format", "jsonv2"),
            ("lat", String(format: "%.6f", lat)),
            ("lon", String(format: "%.6f", lon)),
            ("zoom", "18"),
            ("addressdetails", "1"),
            ("namedetails", "1"),
            ("accept-language", "zh-CN"),
        ])
        let root = try await HTTP.getJSON(url)
        let address = root.dict("address")
        let province = address.str("state").orIfBlank(address.str("region"))
        let city = address.str("city").orIfBlank(address.str("town"))
            .orIfBlank(address.str("village")).orIfBlank(address.str("county"))
        let district = address.str("district").orIfBlank(address.str("city_district"))
            .orIfBlank(address.str("suburb"))
        let town = address.str("town").orIfBlank(address.str("suburb"))
            .orIfBlank(address.str("neighbourhood"))
        let street = address.str("road").orIfBlank(address.str("pedestrian"))
        let number = address.str("house_number")
        let country = address.str("country")
        let poiName = ["amenity", "shop", "tourism", "leisure", "building"]
            .map { address.str($0) }
            .first { !$0.isEmpty } ?? root.str("name")
        let formatted = root.str("display_name")
        let isChina = country.contains("中国") || country.caseInsensitiveCompare("China") == .orderedSame
        let composed: String
        if isChina || country.isEmpty {
            composed = cnJoin(province, city, district, street, number).orIfBlank(formatted)
        } else {
            composed = [poiName, street, number, district, city, province, country]
                .map { $0.trimmed }
                .filter { !$0.isEmpty }
                .joined(separator: ", ")
        }
        return GeocodeResult(
            providerId: info.id,
            providerName: info.name,
            address: composed,
            addressShort: cnJoin(district, street, number).orIfBlank(composed),
            province: province,
            city: city,
            district: district,
            town: town,
            street: street,
            number: number,
            poiName: poiName,
            pois: poiName.isEmpty ? [] : [GeoPoi(name: poiName)],
            precision: precisionOf(number: number, street: street, town: town, district: district, city: city),
            coordSystem: .wgs84,
            extra: formatted
        )
    }
}

struct PhotonProvider: ReverseGeocoder {
    let info = ProviderInfo(
        id: "photon",
        name: "Photon",
        requiresKey: false,
        keyLabel: "",
        keyHint: "",
        keyUrl: "https://photon.komoot.io/",
        note: "免费无需 Key，响应快，海外表现好"
    )

    func reverse(lat: Double, lon: Double, level: AddressLevel, key: String) async throws -> GeocodeResult {
        let url = try HTTP.url("https://photon.komoot.io/reverse", [
            ("lat", String(format: "%.6f", lat)),
            ("lon", String(format: "%.6f", lon)),
            ("lang", "zh"),
            ("limit", "3"),
        ])
        let root = try await HTTP.getJSON(url)
        guard let first = root.array("features").first else { throw GeoError.noResult("Photon") }
        let props = first.dict("properties")
        let name = props.str("name")
        let street = props.str("street")
        let number = props.str("housenumber")
        let city = props.str("city").orIfBlank(props.str("locality"))
        let district = props.str("district").orIfBlank(props.str("locality"))
        let province = props.str("state")
        let country = props.str("country")
        let isChina = country.contains("中国") || country.caseInsensitiveCompare("China") == .orderedSame
        let composed: String
        if isChina || country.isEmpty {
            composed = cnJoin(province, city, district, street, number).orIfBlank(
                [name, street, city].filter { !$0.isEmpty }.joined()
            )
        } else {
            composed = [name, street, number, city, province, country]
                .map { $0.trimmed }
                .filter { !$0.isEmpty }
                .joined(separator: ", ")
        }
        let precision: GeoPrecision
        if !number.isEmpty {
            precision = .door
        } else if !street.isEmpty {
            precision = .street
        } else if !name.isEmpty {
            precision = .poi
        } else if !district.isEmpty {
            precision = .district
        } else if !city.isEmpty {
            precision = .city
        } else {
            precision = .province
        }
        return GeocodeResult(
            providerId: info.id,
            providerName: info.name,
            address: composed,
            addressShort: cnJoin(district, street, number).orIfBlank(composed),
            province: province,
            city: city,
            district: district,
            street: street,
            number: number,
            poiName: name,
            pois: name.isEmpty ? [] : [GeoPoi(name: name)],
            precision: precision,
            coordSystem: .wgs84,
            extra: name
        )
    }
}

struct BigDataCloudProvider: ReverseGeocoder {
    let info = ProviderInfo(
        id: "bigdatacloud",
        name: "BigDataCloud",
        requiresKey: false,
        keyLabel: "",
        keyHint: "",
        keyUrl: "https://www.bigdatacloud.com/",
        note: "免费无需 Key，仅到区县/街区级别，应急兜底"
    )

    func reverse(lat: Double, lon: Double, level: AddressLevel, key: String) async throws -> GeocodeResult {
        let url = try HTTP.url("https://api.bigdatacloud.net/data/reverse-geocode-client", [
            ("latitude", String(format: "%.6f", lat)),
            ("longitude", String(format: "%.6f", lon)),
            ("localityLanguage", "zh"),
        ])
        let root = try await HTTP.getJSON(url)
        let province = root.str("principalSubdivision")
        let city = root.str("city")
        let locality = root.str("locality")
        let country = root.str("countryName")
        let isChina = country.contains("中国") || country.caseInsensitiveCompare("China") == .orderedSame
        let composed: String
        if isChina || country.isEmpty {
            composed = cnJoin(province, city, locality)
        } else {
            composed = [locality, city, province, country]
                .map { $0.trimmed }
                .filter { !$0.isEmpty }
                .joined(separator: ", ")
        }
        guard !composed.isEmpty else { throw GeoError.noResult("BigDataCloud") }
        return GeocodeResult(
            providerId: info.id,
            providerName: info.name,
            address: composed,
            addressShort: cnJoin(city, locality).orIfBlank(composed),
            province: province,
            city: city,
            district: locality,
            precision: locality.isEmpty ? .city : .town,
            coordSystem: .wgs84,
            extra: composed
        )
    }
}

enum GeoProviderRegistry {
    static let all: [any ReverseGeocoder] = [
        AmapProvider(),
        BaiduProvider(),
        TencentProvider(),
        TiandituProvider(),
        MapboxProvider(),
        GeoapifyProvider(),
        PhotonProvider(),
        NominatimProvider(),
        BigDataCloudProvider(),
    ]

    static func byId(_ id: String) -> (any ReverseGeocoder)? {
        all.first { $0.info.id == id }
    }

    static func info(_ id: String) -> ProviderInfo? {
        byId(id)?.info
    }
}
