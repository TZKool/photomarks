import AVFoundation
import SwiftUI

// MARK: - Containers

struct SectionLabel: View {
    let title: String
    var detail: String? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(TMColor.textSecondary)
                .tracking(0.6)
            Spacer()
            if let detail {
                Text(detail)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(TMColor.textTertiary)
            }
        }
    }
}

struct GlassCard<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(TMColor.panel.opacity(0.82))
                    .overlay(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .strokeBorder(TMColor.hairline, lineWidth: 1)
                    )
            )
    }
}

extension View {
    func glassCard(padding: CGFloat = 16) -> some View {
        GlassCard(padding: padding) { self }
    }
}

// MARK: - Buttons

struct ChipButton: View {
    let title: String
    var selected = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(selected ? Color.black : TMColor.textSecondary)
                .padding(.horizontal, 13)
                .padding(.vertical, 8)
                .background(
                    Capsule(style: .continuous)
                        .fill(selected ? TMColor.gold : TMColor.chip)
                )
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(selected ? Color.clear : TMColor.hairline, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

struct PrimaryActionButton: View {
    let title: String
    var systemImage: String? = nil
    var enabled = true
    var showsProgress = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if showsProgress {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.black)
                } else if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 15, weight: .bold))
                }
                Text(title)
                    .font(.system(size: 16, weight: .bold))
            }
            .foregroundColor(.black)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(GoldGradient())
            )
            .opacity(enabled ? 1 : 0.4)
            .shadow(color: TMColor.gold.opacity(enabled ? 0.35 : 0), radius: 16, y: 8)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct IconCircleButton: View {
    let systemName: String
    var size: CGFloat = 38
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: size * 0.4, weight: .semibold))
                .foregroundColor(TMColor.textPrimary)
                .frame(width: size, height: size)
                .background(Circle().fill(TMColor.chip.opacity(0.9)))
                .overlay(Circle().strokeBorder(TMColor.hairline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Tags

struct InfoTag: View {
    var icon: String? = nil
    let text: String
    var tint: Color = TMColor.textSecondary

    var body: some View {
        HStack(spacing: 4) {
            if let icon {
                Image(systemName: icon)
                    .font(.system(size: 10, weight: .semibold))
            }
            Text(text)
                .font(.system(size: 11, weight: .medium))
                .lineLimit(1)
        }
        .foregroundColor(tint)
        .padding(.horizontal, 9)
        .padding(.vertical, 5)
        .background(Capsule().fill(TMColor.chip))
    }
}

// MARK: - Anchor picker

struct AnchorPicker: View {
    @Binding var selection: Anchor

    private let rows: [[Anchor]] = [
        [.topLeft, .topCenter, .topRight],
        [.centerLeft, .center, .centerRight],
        [.bottomLeft, .bottomCenter, .bottomRight],
    ]

    var body: some View {
        VStack(spacing: 6) {
            ForEach(0..<rows.count, id: \.self) { row in
                HStack(spacing: 6) {
                    ForEach(rows[row]) { anchor in
                        Button {
                            selection = anchor
                        } label: {
                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .fill(selection == anchor ? TMColor.gold : TMColor.chip)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                                        .strokeBorder(
                                            selection == anchor ? Color.clear : TMColor.hairline,
                                            lineWidth: 1
                                        )
                                )
                                .frame(width: 34, height: 26)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

// MARK: - Overlays

enum ToastKind: Equatable {
    case info, success, error

    var icon: String {
        switch self {
        case .info: return "info.circle.fill"
        case .success: return "checkmark.circle.fill"
        case .error: return "exclamationmark.triangle.fill"
        }
    }

    var tint: Color {
        switch self {
        case .info: return TMColor.gold
        case .success: return TMColor.success
        case .error: return TMColor.danger
        }
    }
}

struct ToastMessage: Identifiable, Equatable {
    let id = UUID()
    let text: String
    let kind: ToastKind

    static func info(_ text: String) -> ToastMessage { ToastMessage(text: text, kind: .info) }
    static func success(_ text: String) -> ToastMessage { ToastMessage(text: text, kind: .success) }
    static func failure(_ text: String) -> ToastMessage { ToastMessage(text: text, kind: .error) }
}

struct ToastView: View {
    let message: ToastMessage

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: message.kind.icon)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(message.kind.tint)
            Text(message.text)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(TMColor.textPrimary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(
            Capsule(style: .continuous)
                .fill(TMColor.panelStrong)
                .overlay(Capsule(style: .continuous).strokeBorder(TMColor.hairline, lineWidth: 1))
        )
        .shadow(color: .black.opacity(0.4), radius: 18, y: 8)
        .padding(.horizontal, 24)
    }
}

struct LoadingOverlay: View {
    var text = "处理中…"

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea()
            VStack(spacing: 14) {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(TMColor.gold)
                    .scaleEffect(1.2)
                Text(text)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(TMColor.textSecondary)
            }
            .padding(.horizontal, 30)
            .padding(.vertical, 24)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(TMColor.panelStrong.opacity(0.96))
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .strokeBorder(TMColor.hairline, lineWidth: 1)
                    )
            )
        }
        .transition(.opacity)
    }
}

// MARK: - Share sheet

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

// MARK: - Sample imagery

enum SamplePhoto {
    static let source: WatermarkSource = {
        var components = DateComponents()
        components.year = 2025
        components.month = 6
        components.day = 22
        components.hour = 19
        components.minute = 16
        let date = Calendar.current.date(from: components) ?? Date()
        return WatermarkSource(
            date: date,
            latitude: 31.230416,
            longitude: 121.473701,
            altitude: 12,
            accuracyMeters: 8,
            address: "上海市黄浦区中山东一路 18 号 外滩观景平台",
            addressSecondary: "外滩观景平台",
            weather: "晴 26°C",
            note: "江风与晚霞",
            brand: "迹印相机",
            device: "iPhone 15 Pro · 24mm",
            code: ""
        )
    }()

    private static let cache = NSCache<NSString, UIImage>()

    static func image(width: CGFloat = 540, height: CGFloat = 720) -> UIImage {
        let key = "\(Int(width))x\(Int(height))" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        let size = CGSize(width: width, height: height)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            let ctx = context.cgContext
            let colors = [
                UIColor(red: 0.06, green: 0.13, blue: 0.24, alpha: 1).cgColor,
                UIColor(red: 0.22, green: 0.34, blue: 0.48, alpha: 1).cgColor,
                UIColor(red: 0.93, green: 0.65, blue: 0.42, alpha: 1).cgColor,
            ] as CFArray
            if let gradient = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: colors,
                locations: [0, 0.58, 1]
            ) {
                ctx.drawLinearGradient(
                    gradient,
                    start: CGPoint(x: 0, y: 0),
                    end: CGPoint(x: 0, y: height * 0.78),
                    options: []
                )
            }

            // Sun glow.
            let sunCenter = CGPoint(x: width * 0.68, y: height * 0.5)
            let sunRadius = width * 0.07
            let glow = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: [
                    UIColor(red: 1, green: 0.86, blue: 0.6, alpha: 0.85).cgColor,
                    UIColor(red: 1, green: 0.72, blue: 0.45, alpha: 0).cgColor,
                ] as CFArray,
                locations: [0, 1]
            )
            if let glow {
                ctx.drawRadialGradient(
                    glow,
                    startCenter: sunCenter,
                    startRadius: sunRadius * 0.2,
                    endCenter: sunCenter,
                    endRadius: sunRadius * 2.6,
                    options: []
                )
            }
            ctx.setFillColor(UIColor(red: 1, green: 0.93, blue: 0.74, alpha: 0.95).cgColor)
            ctx.fillEllipse(in: CGRect(
                x: sunCenter.x - sunRadius,
                y: sunCenter.y - sunRadius,
                width: sunRadius * 2,
                height: sunRadius * 2
            ))

            // Distant ridge.
            let far = CGMutablePath()
            far.move(to: CGPoint(x: 0, y: height * 0.62))
            far.addLine(to: CGPoint(x: width * 0.18, y: height * 0.5))
            far.addLine(to: CGPoint(x: width * 0.34, y: height * 0.58))
            far.addLine(to: CGPoint(x: width * 0.55, y: height * 0.46))
            far.addLine(to: CGPoint(x: width * 0.76, y: height * 0.58))
            far.addLine(to: CGPoint(x: width, y: height * 0.52))
            far.addLine(to: CGPoint(x: width, y: height))
            far.addLine(to: CGPoint(x: 0, y: height))
            far.closeSubpath()
            ctx.setFillColor(UIColor(red: 0.12, green: 0.2, blue: 0.3, alpha: 0.9).cgColor)
            ctx.addPath(far)
            ctx.fillPath()

            // Water.
            let water = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: [
                    UIColor(red: 0.15, green: 0.27, blue: 0.38, alpha: 0.95).cgColor,
                    UIColor(red: 0.05, green: 0.09, blue: 0.15, alpha: 1).cgColor,
                ] as CFArray,
                locations: [0, 1]
            )
            if let water {
                ctx.saveGState()
                ctx.clip(to: CGRect(x: 0, y: height * 0.72, width: width, height: height * 0.28))
                ctx.drawLinearGradient(
                    water,
                    start: CGPoint(x: 0, y: height * 0.72),
                    end: CGPoint(x: 0, y: height),
                    options: []
                )
                ctx.setFillColor(UIColor(red: 1, green: 0.82, blue: 0.55, alpha: 0.22).cgColor)
                var y = height * 0.74
                var lineWidth = width * 0.1
                while y < height {
                    let rect = CGRect(
                        x: width * 0.68 - lineWidth / 2,
                        y: y,
                        width: lineWidth,
                        height: max(1.2, height * 0.0035)
                    )
                    ctx.fill(rect)
                    y += height * 0.03
                    lineWidth *= 0.82
                }
                ctx.restoreGState()
            }

            // Foreground bank.
            let bank = CGMutablePath()
            bank.move(to: CGPoint(x: 0, y: height * 0.88))
            bank.addQuadCurve(
                to: CGPoint(x: width, y: height * 0.8),
                control: CGPoint(x: width * 0.5, y: height * 0.97)
            )
            bank.addLine(to: CGPoint(x: width, y: height))
            bank.addLine(to: CGPoint(x: 0, y: height))
            bank.closeSubpath()
            ctx.setFillColor(UIColor(red: 0.03, green: 0.05, blue: 0.08, alpha: 1).cgColor)
            ctx.addPath(bank)
            ctx.fillPath()
        }
        cache.setObject(image, forKey: key)
        return image
    }
}

// MARK: - Template thumbnails

enum TemplateThumbnailCache {
    private static let cache = NSCache<NSString, UIImage>()

    static func image(
        for template: WatermarkTemplate,
        size: CGSize = CGSize(width: 300, height: 400)
    ) -> UIImage {
        let key = "\(template.id)_\(Int(size.width))x\(Int(size.height))" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        let sample = SamplePhoto.image(width: size.width, height: size.height)
        let text = WatermarkFormatter.format(
            source: SamplePhoto.source,
            template: template,
            coordSystem: .wgs84
        )
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            sample.draw(in: CGRect(origin: .zero, size: size))
            WatermarkRenderer.draw(
                in: context.cgContext,
                canvasSize: size,
                template: template,
                text: text,
                transform: .identity
            )
        }
        cache.setObject(image, forKey: key)
        return image
    }
}

struct TemplateThumb: View {
    let template: WatermarkTemplate
    var selected = false
    var width: CGFloat = 104

    var body: some View {
        VStack(spacing: 6) {
            Image(uiImage: TemplateThumbnailCache.image(for: template))
                .resizable()
                .aspectRatio(3.0 / 4.0, contentMode: .fit)
                .frame(width: width, height: width * 4 / 3)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(
                            selected ? TMColor.gold : TMColor.hairline,
                            lineWidth: selected ? 2 : 1
                        )
                )
                .shadow(color: .black.opacity(0.35), radius: 8, y: 4)
            Text(template.name)
                .font(.system(size: 11, weight: selected ? .semibold : .regular))
                .foregroundColor(selected ? TMColor.gold : TMColor.textSecondary)
                .lineLimit(1)
        }
        .frame(width: width)
    }
}

// MARK: - Canvas

final class WatermarkCanvasView: UIView {
    var image: UIImage? {
        didSet { setNeedsDisplay() }
    }
    var template: WatermarkTemplate? {
        didSet { setNeedsDisplay() }
    }
    var text: FormattedWatermark? {
        didSet { setNeedsDisplay() }
    }
    var transform: WatermarkTransform = .identity {
        didSet { setNeedsDisplay() }
    }
    var showsWatermark = true {
        didSet { setNeedsDisplay() }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        isOpaque = true
        clipsToBounds = true
        contentMode = .redraw
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext(), let image else { return }
        let canvasRect = AVMakeRect(aspectRatio: image.size, insideRect: bounds)
        image.draw(in: canvasRect)
        guard showsWatermark, let template, let text else { return }
        ctx.saveGState()
        ctx.clip(to: canvasRect)
        ctx.translateBy(x: canvasRect.minX, y: canvasRect.minY)
        WatermarkRenderer.draw(
            in: ctx,
            canvasSize: canvasRect.size,
            template: template,
            text: text,
            transform: transform
        )
        ctx.restoreGState()
    }
}

struct WatermarkPreview: UIViewRepresentable {
    let image: UIImage
    let template: WatermarkTemplate
    let text: FormattedWatermark?
    let transform: WatermarkTransform
    var showsWatermark = true

    func makeUIView(context: Context) -> WatermarkCanvasView {
        let view = WatermarkCanvasView(frame: .zero)
        view.image = image
        view.template = template
        view.text = text
        view.transform = transform
        view.showsWatermark = showsWatermark
        return view
    }

    func updateUIView(_ view: WatermarkCanvasView, context: Context) {
        view.image = image
        view.template = template
        view.text = text
        view.transform = transform
        view.showsWatermark = showsWatermark
    }
}

