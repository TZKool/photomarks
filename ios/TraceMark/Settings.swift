import Foundation

struct AppSettings: Codable, Equatable {
    var geoProvider: String = "auto"
    var addressLevel: AddressLevel = .poi
    var coordSystem: CoordinateSystem = .wgs84
    var amapKey: String = ""
    var baiduKey: String = ""
    var tencentKey: String = ""
    var tiandituKey: String = ""
    var mapboxToken: String = ""
    var geoapifyKey: String = ""
    var autoWeather: Bool = true
    var defaultTemplateId: String = "builtin_coordinate"
    var exportMaxSide: Int = 4096
    var jpegQuality: Int = 95
    var keepExif: Bool = true
    var note: String = ""

    func key(for providerId: String) -> String {
        switch providerId {
        case "amap": return amapKey
        case "baidu": return baiduKey
        case "tencent": return tencentKey
        case "tianditu": return tiandituKey
        case "mapbox": return mapboxToken
        case "geoapify": return geoapifyKey
        default: return ""
        }
    }

    mutating func setKey(_ value: String, for providerId: String) {
        switch providerId {
        case "amap": amapKey = value
        case "baidu": baiduKey = value
        case "tencent": tencentKey = value
        case "tianditu": tiandituKey = value
        case "mapbox": mapboxToken = value
        case "geoapify": geoapifyKey = value
        default: break
        }
    }

    var configuredKeyCount: Int {
        [amapKey, baiduKey, tencentKey, tiandituKey, mapboxToken, geoapifyKey]
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .count
    }

    var jpegQualityValue: CGFloat {
        CGFloat(max(60, min(100, jpegQuality))) / 100
    }
}

final class SettingsStore: ObservableObject {
    @Published var value: AppSettings {
        didSet { persist() }
    }

    private let storageKey = "TraceMark.Settings.v1"

    init() {
        if let data = UserDefaults.standard.data(forKey: storageKey),
           let decoded = try? JSONDecoder().decode(AppSettings.self, from: data) {
            value = decoded
        } else {
            value = AppSettings()
        }
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(value) else { return }
        UserDefaults.standard.set(data, forKey: storageKey)
    }
}
