import Foundation

@MainActor
final class GeoRepository {
    static let shared = GeoRepository()

    private struct Entry {
        let result: GeocodeResult
        let at: Date
    }

    private var cache: [String: Entry] = [:]
    private let autoOrder = [
        "amap", "baidu", "tencent", "tianditu", "mapbox",
        "geoapify", "photon", "nominatim", "bigdatacloud",
    ]

    func providerOrder(settings: AppSettings) -> [any ReverseGeocoder] {
        let ids = settings.geoProvider == "auto" ? autoOrder : [settings.geoProvider]
        var chosen = ids.compactMap { GeoProviderRegistry.byId($0) }
            .filter { !$0.info.requiresKey || !settings.key(for: $0.info.id).isBlank }
        if chosen.isEmpty {
            chosen = ["photon", "nominatim", "bigdatacloud"].compactMap { GeoProviderRegistry.byId($0) }
        }
        return chosen
    }

    func reverse(lat: Double, lon: Double, settings: AppSettings) async -> GeoOutcome {
        let level = settings.addressLevel
        let providers = providerOrder(settings: settings)
        var errors: [String] = []
        var best: GeocodeResult?
        var tried: [String] = []

        for provider in providers {
            let info = provider.info
            let key = settings.key(for: info.id)
            let cacheKey = cacheKey(providerId: info.id, lat: lat, lon: lon, level: level)
            var result: GeocodeResult?
            if let cached = cache[cacheKey], Date().timeIntervalSince(cached.at) < 600 {
                result = cached.result
            } else {
                do {
                    let fresh = try await provider.reverse(lat: lat, lon: lon, level: level, key: key)
                    result = fresh
                    cache[cacheKey] = Entry(result: fresh, at: Date())
                } catch {
                    errors.append("\(info.name)：\(error.localizedDescription)")
                    result = nil
                }
            }
            tried.append(info.name)
            if let result {
                if best == nil || result.precision.rank > (best?.precision.rank ?? -1) {
                    best = result
                }
                if achieved(result, level: level) {
                    return GeoOutcome(result: result, tried: tried, errors: errors)
                }
                if settings.geoProvider != "auto" {
                    return GeoOutcome(result: result, tried: tried, errors: errors)
                }
            }
        }
        return GeoOutcome(result: best, tried: tried, errors: errors)
    }

    func clearCache() {
        cache.removeAll()
    }

    private func achieved(_ result: GeocodeResult, level: AddressLevel) -> Bool {
        switch level {
        case .street:
            return result.precision.rank >= GeoPrecision.street.rank
        case .door:
            return result.precision.rank >= GeoPrecision.door.rank
        case .poi:
            return result.precision == .poi ||
                (result.precision.rank >= GeoPrecision.door.rank && !result.poiName.isBlank)
        }
    }

    private func cacheKey(providerId: String, lat: Double, lon: Double, level: AddressLevel) -> String {
        String(format: "%@|%.5f|%.5f|%@", providerId, lat, lon, level.rawValue)
    }
}
