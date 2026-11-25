import Foundation

enum BuiltInTemplates {
    static let coordinateList = WatermarkTemplate(
        id: "builtin_coordinate",
        name: "经纬现场",
        description: "经纬度 + 地址 + 拍摄时间，元道经纬风格",
        builtIn: true,
        style: TemplateStyle(
            layout: .coordinateList,
            accentColor: 0xFFE9B949,
            textColor: 0xFFFFFFFF,
            subTextColor: 0xF2FFFFFF,
            letterSpacing: 0.01,
            shadow: true,
            background: .none,
            anchor: .bottomLeft,
            marginRatio: 0.05,
            brandLabel: "迹印经纬"
        ),
        fields: {
            var f = FieldConfig()
            f.showHeader = true
            f.showAddress = true
            f.showCoords = true
            f.showTime = true
            f.showDate = false
            f.showWeek = false
            f.showWeather = false
            f.showNote = true
            f.showDevice = false
            f.showLogo = true
            f.showCode = true
            return f
        }()
    )

    static let heroTime = WatermarkTemplate(
        id: "builtin_hero",
        name: "极简大字",
        description: "超大时间 + 日期地址，干净通透",
        builtIn: true,
        style: TemplateStyle(
            layout: .heroTime,
            accentColor: 0xFFFFC93C,
            textColor: 0xFFFFFFFF,
            subTextColor: 0xE6FFFFFF,
            shadow: true,
            background: .none,
            anchor: .bottomLeft,
            marginRatio: 0.045,
            brandLabel: "迹印相机"
        ),
        fields: {
            var f = FieldConfig()
            f.showHeader = false
            f.showCoords = false
            f.showAddress = true
            f.showTime = true
            f.showDate = true
            f.showWeek = true
            f.showWeather = true
            f.showNote = false
            f.showLogo = true
            f.showCode = true
            return f
        }()
    )

    static let brandBadge = WatermarkTemplate(
        id: "builtin_brand",
        name: "红标品牌",
        description: "品牌色块 + 时间 + 天气，门店打卡常用",
        builtIn: true,
        style: TemplateStyle(
            layout: .brandBadge,
            accentColor: 0xFFB42128,
            textColor: 0xFFFFFFFF,
            subTextColor: 0xF0FFFFFF,
            background: .none,
            anchor: .bottomLeft,
            marginRatio: 0.045,
            brandLabel: "门店打卡"
        ),
        fields: {
            var f = FieldConfig()
            f.showHeader = true
            f.showCoords = false
            f.showAddress = true
            f.showTime = true
            f.showDate = true
            f.showWeek = true
            f.showWeather = true
            f.showNote = false
            f.showLogo = true
            f.showCode = false
            return f
        }()
    )

    static let checkinCard = WatermarkTemplate(
        id: "builtin_checkin",
        name: "蓝色打卡",
        description: "打卡记录卡片，时间醒目不遮挡画面",
        builtIn: true,
        style: TemplateStyle(
            layout: .checkinCard,
            accentColor: 0xFF2F9EFF,
            textColor: 0xFFFFFFFF,
            subTextColor: 0xD9FFFFFF,
            cardColor: 0xFFFFFFFF,
            cardTextColor: 0xFF101820,
            cornerRatio: 0.02,
            background: .card,
            anchor: .topLeft,
            marginRatio: 0.045
        ),
        fields: {
            var f = FieldConfig()
            f.showHeader = true
            f.showCoords = false
            f.showAddress = true
            f.showTime = true
            f.showDate = true
            f.showWeek = true
            f.showWeather = false
            f.showNote = false
            f.showSeal = true
            f.showLogo = true
            f.showCode = true
            f.showBrand = true
            return f
        }()
    )

    static let goldDivider = WatermarkTemplate(
        id: "builtin_gold",
        name: "金色竖线",
        description: "金属质感时间配金色分隔线，质感高级",
        builtIn: true,
        style: TemplateStyle(
            layout: .goldDivider,
            accentColor: 0xFFE7C46A,
            textColor: 0xFFFFFFFF,
            subTextColor: 0xE6FFFFFF,
            background: .none,
            anchor: .bottomLeft,
            marginRatio: 0.05,
            brandLabel: "迹印相机"
        ),
        fields: {
            var f = FieldConfig()
            f.showHeader = false
            f.showCoords = false
            f.showAddress = true
            f.showTime = true
            f.showDate = true
            f.showWeek = true
            f.showWeather = true
            f.showNote = false
            f.showLogo = true
            f.showCode = true
            return f
        }()
    )

    static let bottomPill = WatermarkTemplate(
        id: "builtin_pill",
        name: "底部胶囊",
        description: "胶囊打卡 + 地址日期，信息量充足",
        builtIn: true,
        style: TemplateStyle(
            layout: .bottomPill,
            accentColor: 0xFFF5B53C,
            textColor: 0xFFFFFFFF,
            subTextColor: 0xE0FFFFFF,
            cardColor: 0xFFFFFFFF,
            cardTextColor: 0xFF14181F,
            background: .scrim,
            anchor: .bottomLeft,
            marginRatio: 0.04,
            brandLabel: "迹印相机"
        ),
        fields: {
            var f = FieldConfig()
            f.showHeader = true
            f.showCoords = false
            f.showAddress = true
            f.showTime = true
            f.showDate = true
            f.showWeek = true
            f.showWeather = false
            f.showNote = false
            f.showSeal = true
            f.showLogo = true
            f.showCode = true
            return f
        }()
    )

    static let all: [WatermarkTemplate] = [
        coordinateList, heroTime, brandBadge, checkinCard, goldDivider, bottomPill,
    ]

    static func template(id: String?) -> WatermarkTemplate? {
        guard let id else { return nil }
        return all.first { $0.id == id }
    }
}

final class TemplateStore: ObservableObject {
    @Published private(set) var custom: [WatermarkTemplate] = [] {
        didSet { persist() }
    }

    private let storageKey = "TraceMark.CustomTemplates.v1"

    init() {
        load()
    }

    var all: [WatermarkTemplate] {
        BuiltInTemplates.all + custom
    }

    func template(id: String?) -> WatermarkTemplate {
        if let id, let match = all.first(where: { $0.id == id }) {
            return match
        }
        return BuiltInTemplates.coordinateList
    }

    @discardableResult
    func addCopy(of template: WatermarkTemplate, name: String) -> WatermarkTemplate {
        var copy = template
        copy.id = "custom_\(UUID().uuidString)"
        copy.builtIn = false
        copy.name = name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "自定义模板"
            : name.trimmingCharacters(in: .whitespacesAndNewlines)
        copy.description = "在「\(template.name)」基础上自定义"
        custom.append(copy)
        return copy
    }

    func delete(id: String) {
        custom.removeAll { $0.id == id }
    }

    private func load() {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([WatermarkTemplate].self, from: data)
        else { return }
        custom = decoded
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(custom) else { return }
        UserDefaults.standard.set(data, forKey: storageKey)
    }
}
