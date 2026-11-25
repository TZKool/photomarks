import XCTest
import UIKit
@testable import TraceMark

final class WatermarkSnapshotTests: XCTestCase {
    private var source: WatermarkSource = {
        var value = WatermarkSource()
        value.date = Date(timeIntervalSince1970: 1_750_000_000)
        value.latitude = 31.239700
        value.longitude = 121.490500
        value.altitude = 12
        value.accuracyMeters = 8
        value.address = "上海市黄浦区中山东一路外滩街道南京东路100号和平饭店"
        value.addressSecondary = "外滩街道"
        value.weather = "晴 26°C"
        value.note = "装修现场巡检记录，注意成品保护"
        value.brand = "迹印相机"
        value.device = "Apple iPhone 16 Pro · 24 mm f/1.78"
        value.code = "AB12CD34EF56"
        return value
    }()

    @MainActor
    func testRenderBuiltInTemplates() {
        for template in BuiltInTemplates.all {
            render(template: template, size: CGSize(width: 900, height: 1200), suffix: "900x1200")
        }
    }

    @MainActor
    func testRenderCoordinateListStress() {
        var template = BuiltInTemplates.coordinateList
        template.fields.showDate = true
        template.fields.showWeek = true
        template.fields.showWeather = true
        template.fields.showDevice = true
        template.fields.showAltitude = true
        template.fields.showAccuracy = true
        render(template: template, size: CGSize(width: 900, height: 1200), suffix: "stress-900x1200")
        render(template: template, size: CGSize(width: 300, height: 400), suffix: "stress-300x400")
        render(template: template, size: CGSize(width: 1200, height: 900), suffix: "stress-landscape")
    }

    @MainActor
    private func render(template: WatermarkTemplate, size: CGSize, suffix: String) {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        let text = WatermarkFormatter.format(source: source, template: template, coordSystem: .wgs84)
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            let cg = context.cgContext
            let colors = [UIColor(red: 0.36, green: 0.42, blue: 0.48, alpha: 1).cgColor,
                          UIColor(red: 0.16, green: 0.20, blue: 0.26, alpha: 1).cgColor] as CFArray
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
                cg.drawLinearGradient(
                    gradient,
                    start: CGPoint(x: 0, y: 0),
                    end: CGPoint(x: size.width, y: size.height),
                    options: []
                )
            }
            WatermarkRenderer.draw(
                in: cg,
                canvasSize: size,
                template: template,
                text: text,
                transform: .identity
            )
        }
        let attachment = XCTAttachment(image: image)
        attachment.name = "\(template.id)-\(suffix)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
