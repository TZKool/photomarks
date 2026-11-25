import Foundation
import UIKit

enum TemplateLayout: String, Codable, CaseIterable, Identifiable {
    case coordinateList = "COORDINATE_LIST"
    case heroTime = "HERO_TIME"
    case brandBadge = "BRAND_BADGE"
    case checkinCard = "CHECKIN_CARD"
    case goldDivider = "GOLD_DIVIDER"
    case bottomPill = "BOTTOM_PILL"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .coordinateList: return "经纬现场"
        case .heroTime: return "极简大字"
        case .brandBadge: return "红标品牌"
        case .checkinCard: return "蓝色打卡"
        case .goldDivider: return "金色竖线"
        case .bottomPill: return "底部胶囊"
        }
    }
}

enum Anchor: String, Codable, CaseIterable, Identifiable {
    case topLeft = "TOP_LEFT"
    case topCenter = "TOP_CENTER"
    case topRight = "TOP_RIGHT"
    case centerLeft = "CENTER_LEFT"
    case center = "CENTER"
    case centerRight = "CENTER_RIGHT"
    case bottomLeft = "BOTTOM_LEFT"
    case bottomCenter = "BOTTOM_CENTER"
    case bottomRight = "BOTTOM_RIGHT"

    var id: String { rawValue }
}

enum BackdropStyle: String, Codable, CaseIterable, Identifiable {
    case none = "NONE"
    case card = "CARD"
    case scrim = "SCRIM"
    case pill = "PILL"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .none: return "无"
        case .card: return "卡片"
        case .scrim: return "渐变"
        case .pill: return "胶囊"
        }
    }
}

enum AddressLevel: String, Codable, CaseIterable, Identifiable {
    case street = "STREET"
    case door = "DOOR"
    case poi = "POI"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .street: return "道路级"
        case .door: return "门牌级"
        case .poi: return "兴趣点"
        }
    }
}

enum CoordinateSystem: String, Codable, CaseIterable, Identifiable {
    case wgs84 = "WGS84"
    case gcj02 = "GCJ02"
    case bd09 = "BD09"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .wgs84: return "WGS84"
        case .gcj02: return "GCJ-02"
        case .bd09: return "BD-09"
        }
    }
}

enum GeoPrecision: String, Codable, CaseIterable {
    case province
    case city
    case district
    case town
    case street
    case door
    case poi

    var title: String {
        switch self {
        case .province: return "省级"
        case .city: return "城市"
        case .district: return "区县"
        case .town: return "乡镇/街道"
        case .street: return "道路"
        case .door: return "门牌号"
        case .poi: return "兴趣点"
        }
    }

    var rank: Int {
        switch self {
        case .province: return 0
        case .city: return 1
        case .district: return 2
        case .town: return 3
        case .street: return 4
        case .door: return 5
        case .poi: return 6
        }
    }
}

struct TemplateStyle: Codable, Hashable {
    var layout: TemplateLayout = .coordinateList
    var accentColor: Int = 0xFFF2B33D
    var textColor: Int = 0xFFFFFFFF
    var subTextColor: Int = 0xCCFFFFFF
    var cardColor: Int = 0xFFFFFFFF
    var cardTextColor: Int = 0xFF161B22
    var fontScale: CGFloat = 1
    var cornerRatio: CGFloat = 0.018
    var letterSpacing: CGFloat = 0
    var shadow: Bool = true
    var background: BackdropStyle = .none
    var anchor: Anchor = .bottomLeft
    var marginRatio: CGFloat = 0.048
    var opacity: CGFloat = 1
    var brandLabel: String = "迹印相机"
    var condensedTime: Bool = true
}

struct FieldConfig: Codable, Hashable {
    var showHeader: Bool = false
    var showAddress: Bool = true
    var showCoords: Bool = true
    var showLatitude: Bool = true
    var showLongitude: Bool = true
    var showTime: Bool = true
    var showDate: Bool = true
    var showWeek: Bool = true
    var showWeather: Bool = true
    var showNote: Bool = true
    var showDevice: Bool = false
    var showAltitude: Bool = false
    var showAccuracy: Bool = false
    var showBrand: Bool = true
    var showLogo: Bool = true
    var showSeal: Bool = true
    var showCode: Bool = true
    var time24h: Bool = true
    var timeWithSeconds: Bool = false
    var datePattern: String = "yyyy-MM-dd"
    var coordDecimals: Int = 6
}

struct WatermarkTemplate: Codable, Hashable, Identifiable {
    var id: String
    var name: String
    var description: String = ""
    var builtIn: Bool = false
    var style: TemplateStyle
    var fields: FieldConfig = FieldConfig()

    var layoutTitle: String { style.layout.title }
}

struct WatermarkTransform: Equatable {
    var offsetX: CGFloat = 0
    var offsetY: CGFloat = 0
    var scale: CGFloat = 1
    var rotation: CGFloat = 0

    static let identity = WatermarkTransform()

    mutating func reset() {
        self = .identity
    }
}

struct WatermarkSource {
    var date: Date = Date()
    var latitude: Double?
    var longitude: Double?
    var altitude: Double?
    var accuracyMeters: Double?
    var address: String = ""
    var addressSecondary: String = ""
    var weather: String = ""
    var note: String = ""
    var brand: String = ""
    var device: String = ""
    var code: String = ""

    var hasGPS: Bool {
        latitude != nil && longitude != nil
    }
}

struct FormattedWatermark {
    var time: String
    var date: String
    var week: String
    var weather: String
    var address: String
    var latLabel: String
    var lngLabel: String
    var latValue: String
    var lngValue: String
    var timeLabel: String
    var noteLabel: String
    var altitudeText: String
    var accuracyText: String
    var note: String
    var brand: String
    var device: String
    var code: String
    var coordSystemLabel: String
}

extension UIColor {
    convenience init(argb: Int) {
        let a = CGFloat((argb >> 24) & 0xFF) / 255
        let r = CGFloat((argb >> 16) & 0xFF) / 255
        let g = CGFloat((argb >> 8) & 0xFF) / 255
        let b = CGFloat(argb & 0xFF) / 255
        self.init(red: r, green: g, blue: b, alpha: a)
    }

    func withAlphaByte(_ alpha: Int) -> UIColor {
        var r: CGFloat = 0
        var g: CGFloat = 0
        var b: CGFloat = 0
        var a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        return UIColor(red: r, green: g, blue: b, alpha: CGFloat(max(0, min(255, alpha))) / 255)
    }
}
