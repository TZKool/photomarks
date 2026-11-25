import SwiftUI
import UIKit

struct PickedPhoto: Identifiable, @unchecked Sendable {
    let id = UUID()
    let data: Data
    let meta: PhotoMeta
    let preview: UIImage
}

#if DEBUG
extension PickedPhoto {
    /// Used by the simulator UI-preview launch argument; opens the editor without the photo picker.
    static var debugSample: PickedPhoto {
        let image = SamplePhoto.image(width: 900, height: 1200)
        return PickedPhoto(
            data: image.jpegData(compressionQuality: 0.9) ?? Data(),
            meta: PhotoMeta(latitude: nil, longitude: nil, altitude: nil),
            preview: image
        )
    }
}
#endif

struct AccentPreset: Identifiable {
    let name: String
    let argb: Int
    var id: Int { argb }
}

@MainActor
final class TransformState: ObservableObject {
    @Published private(set) var value = WatermarkTransform()
    private var gestureBase: WatermarkTransform?

    func beginGesture() {
        if gestureBase == nil { gestureBase = value }
    }

    func drag(translation: CGSize, canvas: CGSize) {
        guard canvas.width > 0, canvas.height > 0 else { return }
        beginGesture()
        guard let base = gestureBase else { return }
        value.offsetX = clamp(base.offsetX + translation.width / canvas.width, -1.2, 1.2)
        value.offsetY = clamp(base.offsetY + translation.height / canvas.height, -1.2, 1.2)
    }

    func magnify(_ factor: CGFloat) {
        beginGesture()
        guard let base = gestureBase else { return }
        value.scale = clamp(base.scale * factor, 0.4, 2.5)
    }

    func rotate(degrees: CGFloat) {
        beginGesture()
        guard let base = gestureBase else { return }
        value.rotation = normalize(base.rotation + degrees)
    }

    func endGesture() {
        gestureBase = nil
    }

    func setScale(_ scale: CGFloat) {
        value.scale = clamp(scale, 0.4, 2.5)
    }

    func reset() {
        value = .identity
        gestureBase = nil
    }

    private func clamp(_ value: CGFloat, _ lower: CGFloat, _ upper: CGFloat) -> CGFloat {
        min(max(value, lower), upper)
    }

    private func normalize(_ degrees: CGFloat) -> CGFloat {
        var value = degrees.truncatingRemainder(dividingBy: 360)
        if value > 180 { value -= 360 }
        if value < -180 { value += 360 }
        return value
    }
}

@MainActor
final class EditorModel: ObservableObject {
    let photo: PickedPhoto
    let transform = TransformState()

    private let settingsStore: SettingsStore
    private let templateStore: TemplateStore

    @Published private(set) var templates: [WatermarkTemplate]
    @Published var template: WatermarkTemplate
    @Published var source: WatermarkSource
    @Published private(set) var formatted: FormattedWatermark?

    @Published private(set) var addressLoading = false
    @Published private(set) var addressError: String?
    @Published private(set) var candidates: [GeoPoi] = []
    @Published private(set) var providerName = ""
    @Published private(set) var precision: GeoPrecision?

    @Published private(set) var exporting = false
    @Published var exported: ExportPayload?
    @Published private(set) var toast: ToastMessage?
    @Published var comparing = false

    private var geocodeTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?

    static let accentPresets: [AccentPreset] = [
        AccentPreset(name: "金", argb: 0xFFE9B949),
        AccentPreset(name: "白", argb: 0xFFFFFFFF),
        AccentPreset(name: "蓝", argb: 0xFF2F9EFF),
        AccentPreset(name: "红", argb: 0xFFB42128),
        AccentPreset(name: "橙", argb: 0xFFFF8A3D),
        AccentPreset(name: "绿", argb: 0xFF37C978),
        AccentPreset(name: "青", argb: 0xFF2BD9C4),
        AccentPreset(name: "紫", argb: 0xFF9B6BFF),
    ]

    init(photo: PickedPhoto, settingsStore: SettingsStore, templateStore: TemplateStore) {
        self.photo = photo
        self.settingsStore = settingsStore
        self.templateStore = templateStore

        let initial = templateStore.template(id: settingsStore.value.defaultTemplateId)
        self.template = initial
        self.templates = templateStore.all
        self.source = WatermarkSource(
            date: photo.meta.date,
            latitude: photo.meta.latitude,
            longitude: photo.meta.longitude,
            altitude: photo.meta.altitude,
            address: "",
            weather: "",
            note: settingsStore.value.note,
            brand: "迹印相机",
            device: photo.meta.deviceText,
            code: ""
        )
        rebuild()

        if source.hasGPS {
            refreshAddress()
            Task { await loadWeather(force: false) }
        }
    }

    var canExport: Bool {
        formatted != nil && !exporting
    }

    var coordSystem: CoordinateSystem {
        settingsStore.value.coordSystem
    }

    var settings: AppSettings {
        settingsStore.value
    }

    // MARK: - Rebuild

    func rebuild() {
        formatted = WatermarkFormatter.format(
            source: source,
            template: template,
            coordSystem: settingsStore.value.coordSystem
        )
    }

    // MARK: - Template

    func selectTemplate(_ value: WatermarkTemplate) {
        template = value
        rebuild()
    }

    func updateFields(_ mutate: (inout FieldConfig) -> Void) {
        mutate(&template.fields)
        rebuild()
    }

    func toggleField(_ keyPath: WritableKeyPath<FieldConfig, Bool>) {
        template.fields[keyPath: keyPath].toggle()
        rebuild()
    }

    func updateStyle(_ mutate: (inout TemplateStyle) -> Void) {
        mutate(&template.style)
        rebuild()
    }

    func setAnchor(_ anchor: Anchor) {
        updateStyle { $0.anchor = anchor }
    }

    func setBackground(_ background: BackdropStyle) {
        updateStyle { $0.background = background }
    }

    func setAccent(_ argb: Int) {
        updateStyle { $0.accentColor = argb }
    }

    func setOpacity(_ value: Double) {
        updateStyle { $0.opacity = CGFloat(min(max(value, 0.2), 1)) }
    }

    func saveTemplate(named name: String) {
        let saved = templateStore.addCopy(of: template, name: name)
        templates = templateStore.all
        template = saved
        rebuild()
        showToast(.success("已保存模板「\(saved.name)」"))
    }

    // MARK: - Source

    func setAddress(_ value: String) {
        source.address = value
        rebuild()
    }

    func setNote(_ value: String) {
        source.note = value
        rebuild()
    }

    func setDate(_ value: Date) {
        source.date = value
        rebuild()
        Task { await loadWeather(force: true) }
    }

    func useCurrentTime() {
        setDate(Date())
    }

    func choosePoi(_ poi: GeoPoi) {
        let display = [poi.name.trimmed, poi.address?.trimmed ?? ""]
            .filter { !$0.isEmpty }
            .reduce(into: [String]()) { result, item in
                if !result.contains(item) { result.append(item) }
            }
            .joined(separator: "，")
        guard !display.isEmpty else { return }
        source.address = display
        rebuild()
    }

    func setCoordSystem(_ system: CoordinateSystem) {
        guard settingsStore.value.coordSystem != system else { return }
        settingsStore.value.coordSystem = system
        rebuild()
    }

    func refreshAddress(provider: String? = nil) {
        guard let lat = source.latitude, let lon = source.longitude else {
            showToast(.failure("这张照片没有 GPS 信息，无法解析地址"))
            return
        }
        if let provider {
            settingsStore.value.geoProvider = provider
        }
        geocodeTask?.cancel()
        addressLoading = true
        addressError = nil
        let settings = settingsStore.value

        geocodeTask = Task {
            let outcome = await GeoRepository.shared.reverse(lat: lat, lon: lon, settings: settings)
            guard !Task.isCancelled else { return }
            addressLoading = false
            guard let result = outcome.result else {
                candidates = []
                precision = nil
                providerName = ""
                addressError = outcome.errors.first
                    ?? "没有解析到地址，请检查网络或在设置中填写 Key"
                return
            }
            providerName = result.providerName
            precision = result.precision
            candidates = result.pois
            source.address = result.address
            source.addressSecondary = result.addressShort
            rebuild()
            await loadWeather(force: false)
        }
    }

    func refreshWeather() {
        Task { await loadWeather(force: true) }
    }

    private func loadWeather(force: Bool) async {
        guard let lat = source.latitude, let lon = source.longitude else { return }
        guard force || settingsStore.value.autoWeather else { return }
        if !force, !source.weather.isBlank { return }
        guard let info = await WeatherRepository.shared.weather(lat: lat, lon: lon, date: source.date) else {
            return
        }
        let text = [info.text, info.temperatureC.map { "\($0)°C" }]
            .compactMap { $0 }
            .filter { !$0.isBlank }
            .joined(separator: " ")
        guard !text.isBlank else { return }
        source.weather = text
        rebuild()
    }

    // MARK: - Transform

    func resetTransform() {
        transform.reset()
    }

    // MARK: - Export

    func export() {
        guard !exporting else { return }
        guard let text = formatted else {
            showToast(.failure("水印还在准备中，请稍后再试"))
            return
        }
        exporting = true
        let template = template
        let transformValue = transform.value
        let settings = settingsStore.value
        let data = photo.data
        let comment = buildExifComment()

        Task {
            do {
                let payload = try await Task.detached(priority: .userInitiated) {
                    try PhotoExporter.render(
                        data: data,
                        template: template,
                        text: text,
                        transform: transformValue,
                        maxSide: settings.exportMaxSide,
                        quality: settings.jpegQualityValue,
                        keepExif: settings.keepExif,
                        comment: comment
                    )
                }.value
                exporting = false
                exported = payload
                do {
                    try await PhotoSaver.saveToAlbum(data: payload.data, fileName: payload.fileName)
                    showToast(.success("已保存到相册 TraceMark 相簿"))
                } catch {
                    showToast(.failure("图片已生成，但保存相册失败：\(error.localizedDescription)"))
                }
            } catch {
                exporting = false
                showToast(.failure("导出失败：\(error.localizedDescription)"))
            }
        }
    }

    private func buildExifComment() -> String {
        let stamp = Self.stampFormatter.string(from: source.date)
        var parts = ["迹印相机"]
        if let formatted {
            if !formatted.latValue.isBlank { parts.append("纬度 \(formatted.latValue)") }
            if !formatted.lngValue.isBlank { parts.append("经度 \(formatted.lngValue)") }
        }
        if !source.address.isBlank { parts.append(source.address) }
        if !stamp.isBlank { parts.append(stamp) }
        if let code = formatted?.code, !code.isBlank { parts.append("防伪码 \(code)") }
        return parts.joined(separator: " | ")
    }

    // MARK: - Toast

    func showToast(_ message: ToastMessage) {
        toast = message
        toastTask?.cancel()
        toastTask = Task {
            try? await Task.sleep(nanoseconds: 2_600_000_000)
            guard !Task.isCancelled else { return }
            if toast == message { toast = nil }
        }
    }

    private static let stampFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return f
    }()
}
