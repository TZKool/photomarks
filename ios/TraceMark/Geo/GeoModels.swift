import Foundation

struct GeoPoi: Identifiable, Hashable {
    var name: String
    var distanceMeters: Double?
    var address: String?
    var category: String?

    var id: String {
        "\(name)|\(distanceMeters.map { String(format: "%.1f", $0) } ?? "-")"
    }

    var distanceText: String? {
        guard let distanceMeters else { return nil }
        if distanceMeters >= 1000 {
            return String(format: "%.1f km", distanceMeters / 1000)
        }
        return "\(Int(round(distanceMeters))) m"
    }
}

struct GeocodeResult {
    var providerId: String
    var providerName: String
    var address: String
    var addressShort: String
    var province: String = ""
    var city: String = ""
    var district: String = ""
    var town: String = ""
    var street: String = ""
    var number: String = ""
    var poiName: String = ""
    var pois: [GeoPoi] = []
    var precision: GeoPrecision = .city
    var coordSystem: CoordinateSystem = .wgs84
    var extra: String = ""
}

struct ProviderInfo: Identifiable, Hashable {
    var id: String
    var name: String
    var requiresKey: Bool
    var keyLabel: String
    var keyHint: String
    var keyUrl: String
    var note: String
}

struct GeoOutcome {
    var result: GeocodeResult?
    var tried: [String]
    var errors: [String]
}

func cnJoin(_ parts: String?...) -> String {
    var seen = Set<String>()
    return parts
        .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty && $0 != "[]" && seen.insert($0).inserted }
        .joined()
}

func precisionOf(number: String, street: String, town: String, district: String, city: String) -> GeoPrecision {
    if !number.isEmpty { return .door }
    if !street.isEmpty { return .street }
    if !town.isEmpty { return .town }
    if !district.isEmpty { return .district }
    if !city.isEmpty { return .city }
    return .province
}

enum GeoError: LocalizedError {
    case message(String)
    case badURL
    case requestFailed(Int)
    case invalidResponse
    case noResult(String)

    var errorDescription: String? {
        switch self {
        case .message(let text): return text
        case .badURL: return "无效的请求地址"
        case .requestFailed(let code): return "HTTP \(code)"
        case .invalidResponse: return "服务返回了无法识别的数据"
        case .noResult(let name): return "\(name)无结果"
        }
    }
}

extension Dictionary where Key == String, Value == Any {
    func str(_ key: String) -> String {
        guard let value = self[key] else { return "" }
        if let text = value as? String {
            return text == "[]" || text == "null" ? "" : text
        }
        if value is [Any] || value is [String: Any] { return "" }
        if let number = value as? NSNumber { return number.stringValue }
        return ""
    }

    func dict(_ key: String) -> [String: Any] {
        self[key] as? [String: Any] ?? [:]
    }

    func optDict(_ key: String) -> [String: Any]? {
        self[key] as? [String: Any]
    }

    func array(_ key: String) -> [[String: Any]] {
        (self[key] as? [Any])?.compactMap { $0 as? [String: Any] } ?? []
    }

    func stringArray(_ key: String) -> [String] {
        (self[key] as? [Any])?.compactMap { $0 as? String } ?? []
    }

    func dbl(_ key: String) -> Double? {
        if let number = self[key] as? NSNumber { return number.doubleValue }
        if let text = self[key] as? String { return Double(text) }
        return nil
    }

    func intValue(_ key: String) -> Int? {
        if let number = self[key] as? NSNumber { return number.intValue }
        if let text = self[key] as? String { return Int(text) }
        return nil
    }
}
