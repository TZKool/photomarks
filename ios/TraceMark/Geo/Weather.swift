import Foundation

struct WeatherInfo {
    var text: String
    var temperatureC: Int?
}

/// Open-Meteo, no API key. Uses the archive endpoint for past shoot dates.
@MainActor
final class WeatherRepository {
    static let shared = WeatherRepository()

    private var cache: [String: WeatherInfo] = [:]

    func weather(lat: Double, lon: Double, date: Date) async -> WeatherInfo? {
        let day = Calendar.current.startOfDay(for: date)
        let key = String(format: "%.3f|%.3f|%lld", lat, lon, Int64(day.timeIntervalSince1970))
        if let cached = cache[key] { return cached }

        let today = Calendar.current.startOfDay(for: Date())
        let archived = day < today
        let base = archived
            ? "https://archive-api.open-meteo.com/v1/archive"
            : "https://api.open-meteo.com/v1/forecast"
        let dayText = dayFormatter.string(from: day)

        guard let url = try? HTTP.url(base, [
            ("latitude", String(format: "%.4f", lat)),
            ("longitude", String(format: "%.4f", lon)),
            ("start_date", dayText),
            ("end_date", dayText),
            ("daily", "weather_code,temperature_2m_max,temperature_2m_min"),
            ("timezone", "auto"),
        ]) else { return nil }

        guard let root = try? await HTTP.getJSON(url),
              let daily = root.optDict("daily"),
              let codes = daily["weather_code"] as? [Any],
              let codeNumber = codes.first as? NSNumber,
              let text = describe(codeNumber.intValue)
        else { return nil }

        var temperature: Int?
        if let maxes = daily["temperature_2m_max"] as? [Any],
           let first = maxes.first as? NSNumber {
            temperature = Int(round(first.doubleValue))
        }
        let info = WeatherInfo(text: text, temperatureC: temperature)
        cache[key] = info
        return info
    }

    private var dayFormatter: DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone.current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }

    private func describe(_ code: Int) -> String? {
        switch code {
        case 0: return "晴"
        case 1: return "少云"
        case 2: return "多云"
        case 3: return "阴"
        case 45, 48: return "雾"
        case 51, 53, 55: return "毛毛雨"
        case 56, 57: return "冻雨"
        case 61: return "小雨"
        case 63: return "中雨"
        case 65: return "大雨"
        case 66, 67: return "冻雨"
        case 71: return "小雪"
        case 73: return "中雪"
        case 75: return "大雪"
        case 77: return "米雪"
        case 80: return "阵雨"
        case 81: return "强阵雨"
        case 82: return "暴雨"
        case 85: return "阵雪"
        case 86: return "强阵雪"
        case 95: return "雷阵雨"
        case 96, 99: return "雷暴冰雹"
        default: return nil
        }
    }
}
