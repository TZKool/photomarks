import Foundation

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
    var isBlank: Bool { trimmed.isEmpty }
    func orIfBlank(_ fallback: String) -> String { isBlank ? fallback : self }
}

enum WatermarkFormatter {
    static func format(
        source: WatermarkSource,
        template: WatermarkTemplate,
        coordSystem: CoordinateSystem
    ) -> FormattedWatermark {
        let fields = template.fields
        let date = source.date

        let timePattern: String
        if !fields.time24h {
            timePattern = fields.timeWithSeconds ? "a hh:mm:ss" : "a hh:mm"
        } else {
            timePattern = fields.timeWithSeconds ? "HH:mm:ss" : "HH:mm"
        }
        let timeText = formatter(timePattern).string(from: date)
        let dateText = formatter(fields.datePattern.isBlank ? "yyyy-MM-dd" : fields.datePattern)
            .string(from: date)
        let weekText = formatter("EEEE").string(from: date)

        var displayLat: Double?
        var displayLon: Double?
        if let lat = source.latitude, let lon = source.longitude {
            let converted = CoordTransform.convert(lat: lat, lon: lon, to: coordSystem)
            displayLat = converted.lat
            displayLon = converted.lon
        }
        let decimals = max(3, min(8, fields.coordDecimals))
        let format = "%.\(decimals)f"
        let latValue = displayLat.map { String(format: format, $0) } ?? ""
        let lngValue = displayLon.map { String(format: format, $0) } ?? ""

        return FormattedWatermark(
            time: timeText,
            date: dateText,
            week: weekText,
            weather: source.weather,
            address: source.address.orIfBlank("未获取到地址"),
            latLabel: "纬度",
            lngLabel: "经度",
            latValue: latValue,
            lngValue: lngValue,
            timeLabel: "时间",
            noteLabel: "备注",
            altitudeText: source.altitude.map { "海拔 \(Int(round($0))) 米" } ?? "",
            accuracyText: source.accuracyMeters.map { "定位精度 ±\(Int(round($0))) 米" } ?? "",
            note: source.note,
            brand: template.style.brandLabel.orIfBlank(source.brand.orIfBlank("迹印相机")),
            device: source.device,
            code: source.code.orIfBlank(
                CodeGenerator.make([
                    displayLat.map { String($0) },
                    displayLon.map { String($0) },
                    ISO8601DateFormatter().string(from: date),
                ])
            ),
            coordSystemLabel: coordSystem.title
        )
    }

    private static var formatterCache: [String: DateFormatter] = [:]

    private static func formatter(_ pattern: String) -> DateFormatter {
        if let cached = formatterCache[pattern] { return cached }
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.timeZone = TimeZone.current
        f.dateFormat = pattern
        formatterCache[pattern] = f
        return f
    }
}

enum CodeGenerator {
    private static let alphabet = Array("23456789ABCDEFGHJKLMNPQRSTUVWXYZ")

    static func make(_ parts: [String?]) -> String {
        let joined = parts.map { $0 ?? "" }.joined(separator: "|")
        var hash: UInt32 = 0
        for unit in joined.utf16 {
            hash = 31 &* hash &+ UInt32(unit)
        }
        var state = UInt64(hash) ^ 0x5DEECE66D
        func next(_ bits: Int) -> UInt32 {
            state = (state &* 0x5DEECE66D &+ 0xB) & 0xFFFF_FFFF_FFFF
            return UInt32(state >> (48 - bits))
        }
        var result = ""
        for _ in 0..<12 {
            result.append(alphabet[Int(next(31) % UInt32(alphabet.count))])
        }
        return result
    }
}
