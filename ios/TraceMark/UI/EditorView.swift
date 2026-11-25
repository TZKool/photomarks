import SwiftUI
import UIKit

private struct EditorHeaderTopKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

enum EditorTab: String, CaseIterable, Identifiable {
    case template = "模板"
    case style = "样式"
    case info = "信息"

    var id: String { rawValue }
}

@MainActor
struct EditorView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model: EditorModel

    @State private var tab: EditorTab = .template
    @State private var showSaveDialog = false
    @State private var templateName = ""
    @State private var headerTopCorrection: CGFloat = 0

    init(photo: PickedPhoto, settingsStore: SettingsStore, templateStore: TemplateStore) {
        _model = StateObject(wrappedValue: EditorModel(
            photo: photo,
            settingsStore: settingsStore,
            templateStore: templateStore
        ))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 10) {
                header
                EditorPreviewSection(model: model)
                    .frame(maxHeight: .infinity)
                    .padding(.horizontal, 14)
                controlPanel
                    .frame(height: 330)
                exportBar
            }

            if model.exporting {
                LoadingOverlay(text: "正在导出高清原图…")
            }
        }
        .overlay(alignment: .top) {
            if let toast = model.toast {
                ToastView(message: toast)
                    .padding(.top, 64)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.86), value: model.toast)
        .preferredColorScheme(.dark)
        .sheet(item: $model.exported) { payload in
            ExportSuccessSheet(payload: payload) {
                model.exported = nil
            }
        }
        .alert("另存为模板", isPresented: $showSaveDialog) {
            TextField("模板名称", text: $templateName)
                .textInputAutocapitalization(.never)
            Button("保存") {
                let name = templateName.trimmingCharacters(in: .whitespacesAndNewlines)
                model.saveTemplate(named: name.isEmpty ? "自定义模板" : name)
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("当前样式和字段开关会一起保存，之后可在模板列表中选择。")
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            IconCircleButton(systemName: "chevron.left") { dismiss() }
            VStack(alignment: .leading, spacing: 2) {
                Text("迹印编辑")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(TMColor.textPrimary)
                Text(model.template.name)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(TMColor.gold)
            }
            Spacer()
            InfoTag(icon: "arrow.counterclockwise", text: "重置", tint: TMColor.textSecondary)
                .contentShape(Rectangle())
                .onTapGesture {
                    withAnimation(.easeOut(duration: 0.2)) { model.resetTransform() }
                }
        }
        .padding(.horizontal, 16)
        .padding(.top, 4 + headerTopCorrection)
        .background(
            GeometryReader { proxy in
                Color.clear.preference(
                    key: EditorHeaderTopKey.self,
                    value: proxy.frame(in: .global).minY
                )
            }
        )
        .onPreferenceChange(EditorHeaderTopKey.self) { top in
            let target = windowTopSafeInset + 4
            guard top < target - 0.5 else { return }
            headerTopCorrection += target - top
        }
    }

    /// Full-screen covers on newer iOS releases can lay their content out edge-to-edge,
    /// which puts a custom header under the status bar where taps never reach it.
    private var windowTopSafeInset: CGFloat {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?
            .safeAreaInsets.top ?? 0
    }

    private var controlPanel: some View {
        VStack(spacing: 0) {
            EditorTabBar(selection: $tab)
                .padding(.horizontal, 14)
                .padding(.top, 12)
                .padding(.bottom, 8)

            ScrollView(showsIndicators: false) {
                Group {
                    switch tab {
                    case .template:
                        TemplatePanel(model: model, showSaveDialog: $showSaveDialog, templateName: $templateName)
                    case .style:
                        StylePanel(model: model)
                    case .info:
                        InfoPanel(model: model)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 18)
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(TMColor.bgTop.opacity(0.97))
                .overlay(
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .strokeBorder(TMColor.hairline, lineWidth: 1)
                )
                .ignoresSafeArea(edges: .bottom)
        )
    }

    private var exportBar: some View {
        HStack(spacing: 10) {
            PrimaryActionButton(
                title: model.exporting ? "导出中…" : "保存到相册",
                systemImage: "square.and.arrow.down.on.square",
                enabled: model.canExport,
                showsProgress: model.exporting
            ) {
                model.export()
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

// MARK: - Preview

@MainActor
private struct EditorPreviewSection: View {
    @ObservedObject var model: EditorModel

    var body: some View {
        GeometryReader { geo in
            ZStack {
                PreviewCanvas(
                    transform: model.transform,
                    image: model.photo.preview,
                    template: model.template,
                    text: model.formatted,
                    comparing: model.comparing,
                    size: geo.size
                )
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                VStack {
                    HStack(alignment: .top) {
                        previewStatus
                        Spacer()
                        InfoTag(icon: "hand.tap", text: model.comparing ? "原图" : "按住对比")
                            .opacity(model.comparing ? 1 : 0.8)
                    }
                    Spacer()
                    HStack {
                        Text("拖动 · 双指缩放 · 旋转")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(.white.opacity(0.55))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(Color.black.opacity(0.35)))
                        Spacer()
                    }
                }
                .padding(10)
                .allowsHitTesting(false)
            }
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.1), lineWidth: 1)
            )
            .onLongPressGesture(
                minimumDuration: 0.18,
                maximumDistance: 24,
                perform: {},
                onPressingChanged: { pressing in
                    withAnimation(.easeOut(duration: 0.12)) { model.comparing = pressing }
                }
            )
        }
    }

    @ViewBuilder
    private var previewStatus: some View {
        if model.addressLoading {
            HStack(spacing: 6) {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(TMColor.gold)
                    .scaleEffect(0.7)
                Text("解析地址…")
                    .font(.system(size: 11, weight: .medium))
            }
            .foregroundColor(TMColor.textPrimary)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Capsule().fill(Color.black.opacity(0.45)))
        } else if !model.providerName.isEmpty {
            InfoTag(
                icon: "mappin.and.ellipse",
                text: [model.providerName, model.precision?.title ?? ""]
                    .filter { !$0.isEmpty }
                    .joined(separator: " · ")
            )
            .background(Capsule().fill(Color.black.opacity(0.45)))
        }
    }
}

@MainActor
private struct PreviewCanvas: View {
    @ObservedObject var transform: TransformState
    let image: UIImage
    let template: WatermarkTemplate
    let text: FormattedWatermark?
    let comparing: Bool
    let size: CGSize

    var body: some View {
        WatermarkPreview(
            image: image,
            template: template,
            text: text,
            transform: transform.value,
            showsWatermark: !comparing
        )
        .contentShape(Rectangle())
        .gesture(dragGesture)
        .simultaneousGesture(magnifyGesture)
        .simultaneousGesture(rotateGesture)
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                transform.drag(translation: value.translation, canvas: size)
            }
            .onEnded { _ in transform.endGesture() }
    }

    private var magnifyGesture: some Gesture {
        MagnificationGesture()
            .onChanged { value in transform.magnify(value) }
            .onEnded { _ in transform.endGesture() }
    }

    private var rotateGesture: some Gesture {
        RotationGesture()
            .onChanged { value in transform.rotate(degrees: value.degrees) }
            .onEnded { _ in transform.endGesture() }
    }
}

// MARK: - Tab bar

private struct EditorTabBar: View {
    @Binding var selection: EditorTab

    var body: some View {
        HStack(spacing: 6) {
            ForEach(EditorTab.allCases) { tab in
                Button {
                    withAnimation(.easeInOut(duration: 0.16)) { selection = tab }
                } label: {
                    Text(tab.rawValue)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(selection == tab ? .black : TMColor.textSecondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 32)
                        .background(
                            Capsule(style: .continuous)
                                .fill(selection == tab ? TMColor.gold : Color.clear)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Capsule(style: .continuous).fill(TMColor.chip.opacity(0.55)))
    }
}

// MARK: - Template panel

@MainActor
private struct TemplatePanel: View {
    @ObservedObject var model: EditorModel
    @Binding var showSaveDialog: Bool
    @Binding var templateName: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(model.templates) { template in
                        Button {
                            model.selectTemplate(template)
                        } label: {
                            TemplateThumb(
                                template: template,
                                selected: template.id == model.template.id,
                                width: 96
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 2)
            }

            HStack(alignment: .center, spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(model.template.description.isEmpty ? "自定义水印样式" : model.template.description)
                        .font(.system(size: 11))
                        .foregroundColor(TMColor.textSecondary)
                        .lineLimit(2)
                }
                Spacer()
                Button {
                    templateName = "\(model.template.name) 副本"
                    showSaveDialog = true
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "square.on.square.dashed")
                            .font(.system(size: 11, weight: .semibold))
                        Text("另存为模板")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundColor(TMColor.gold)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(TMColor.gold.opacity(0.12)))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Style panel

@MainActor
private struct StylePanel: View {
    @ObservedObject var model: EditorModel
    @ObservedObject private var transform: TransformState

    init(model: EditorModel) {
        self.model = model
        self._transform = ObservedObject(wrappedValue: model.transform)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("水印位置")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(TMColor.textSecondary)
                    AnchorPicker(selection: Binding(
                        get: { model.template.style.anchor },
                        set: { model.setAnchor($0) }
                    ))
                }
                Spacer(minLength: 0)
                VStack(alignment: .leading, spacing: 8) {
                    Text("背景")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(TMColor.textSecondary)
                    LazyVGrid(columns: [GridItem(.fixed(56)), GridItem(.fixed(56))], spacing: 6) {
                        ForEach(BackdropStyle.allCases) { style in
                            ChipButton(
                                title: style.title,
                                selected: model.template.style.background == style
                            ) {
                                model.setBackground(style)
                            }
                        }
                    }
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("主题色")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(TMColor.textSecondary)
                HStack(spacing: 10) {
                    ForEach(EditorModel.accentPresets) { preset in
                        Button {
                            model.setAccent(preset.argb)
                        } label: {
                            Circle()
                                .fill(Color(argb: UInt32(truncatingIfNeeded: preset.argb)))
                                .frame(width: 24, height: 24)
                                .overlay(
                                    Circle().strokeBorder(
                                        model.template.style.accentColor == preset.argb
                                            ? Color.white
                                            : Color.white.opacity(0.16),
                                        lineWidth: 2
                                    )
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            SliderRow(
                title: "水印大小",
                value: Binding(
                    get: { Double(transform.value.scale) },
                    set: { transform.setScale(CGFloat($0)) }
                ),
                range: 0.4...2.5,
                display: String(format: "%.2fx", transform.value.scale)
            )

            let opacity = Binding<Double>(
                get: { Double(model.template.style.opacity) },
                set: { model.setOpacity($0) }
            )
            SliderRow(
                title: "不透明度",
                value: opacity,
                range: 0.2...1.0,
                display: "\(Int(round(model.template.style.opacity * 100)))%"
            )

            HStack(spacing: 8) {
                ChipButton(title: "文字阴影", selected: model.template.style.shadow) {
                    model.updateStyle { $0.shadow.toggle() }
                }
                ChipButton(title: "重置位置", selected: false) {
                    withAnimation(.easeOut(duration: 0.2)) { model.resetTransform() }
                }
                Spacer()
            }
        }
        .padding(.top, 4)
    }
}

private struct SliderRow: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let display: String

    var body: some View {
        VStack(spacing: 6) {
            HStack {
                Text(title)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(TMColor.textSecondary)
                Spacer()
                Text(display)
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .foregroundColor(TMColor.gold)
            }
            Slider(value: $value, in: range)
                .tint(TMColor.gold)
        }
    }
}

// MARK: - Info panel

private struct FieldToggle: Identifiable {
    let title: String
    let keyPath: WritableKeyPath<FieldConfig, Bool>
    var id: String { title }
}

@MainActor
private struct InfoPanel: View {
    @ObservedObject var model: EditorModel

    private let fieldToggles: [FieldToggle] = [
        FieldToggle(title: "地址", keyPath: \.showAddress),
        FieldToggle(title: "经纬度", keyPath: \.showCoords),
        FieldToggle(title: "海拔", keyPath: \.showAltitude),
        FieldToggle(title: "定位精度", keyPath: \.showAccuracy),
        FieldToggle(title: "时间", keyPath: \.showTime),
        FieldToggle(title: "日期", keyPath: \.showDate),
        FieldToggle(title: "星期", keyPath: \.showWeek),
        FieldToggle(title: "天气", keyPath: \.showWeather),
        FieldToggle(title: "备注", keyPath: \.showNote),
        FieldToggle(title: "设备", keyPath: \.showDevice),
        FieldToggle(title: "防伪码", keyPath: \.showCode),
        FieldToggle(title: "品牌标识", keyPath: \.showLogo),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            addressSection
            timeSection
            coordinateSection
            fieldSection
        }
        .padding(.top, 4)
    }

    private var addressSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Text("地址")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(TMColor.textSecondary)
                Spacer()
                if model.addressLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(TMColor.gold)
                        .scaleEffect(0.65)
                }
                if !model.providerName.isEmpty {
                    InfoTag(
                        icon: "antenna.radiowaves.left.and.right",
                        text: "\(model.providerName) · \(model.precision?.title ?? "地址")",
                        tint: TMColor.gold
                    )
                }
                providerMenu
            }

            TextField(
                "输入或编辑地址",
                text: Binding(
                    get: { model.source.address },
                    set: { model.setAddress($0) }
                ),
                axis: .vertical
            )
            .lineLimit(2...4)
            .font(.system(size: 14))
            .foregroundColor(TMColor.textPrimary)
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(TMColor.chip.opacity(0.7))
            )

            if let error = model.addressError {
                Text(error)
                    .font(.system(size: 11))
                    .foregroundColor(TMColor.danger)
            }

            if !model.candidates.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(model.candidates.prefix(8)) { poi in
                            Button {
                                model.choosePoi(poi)
                            } label: {
                                HStack(spacing: 5) {
                                    Image(systemName: "mappin.circle.fill")
                                        .font(.system(size: 10))
                                    Text(poi.distanceText.map { "\(poi.name) \($0)" } ?? poi.name)
                                        .font(.system(size: 11, weight: .medium))
                                        .lineLimit(1)
                                }
                                .foregroundColor(TMColor.textSecondary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .background(Capsule().fill(TMColor.chip))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            TextField(
                "备注（会显示在水印上）",
                text: Binding(
                    get: { model.source.note },
                    set: { model.setNote($0) }
                )
            )
            .font(.system(size: 13))
            .foregroundColor(TMColor.textPrimary)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(TMColor.chip.opacity(0.7))
            )
        }
    }

    private var providerMenu: some View {
        Menu {
            Button {
                model.refreshAddress(provider: "auto")
            } label: {
                Label("自动择优", systemImage: model.settings.geoProvider == "auto" ? "checkmark" : "wand.and.stars")
            }
            Divider()
            ForEach(GeoProviderRegistry.all.map { $0.info }, id: \.id) { info in
                Button {
                    model.refreshAddress(provider: info.id)
                } label: {
                    Label(
                        info.requiresKey && model.settings.key(for: info.id).isBlank
                            ? "\(info.name)（未配置 Key）"
                            : info.name,
                        systemImage: model.settings.geoProvider == info.id ? "checkmark" : "mappin.and.ellipse"
                    )
                }
            }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.system(size: 10, weight: .semibold))
                Text("重新解析")
                    .font(.system(size: 11, weight: .semibold))
            }
            .foregroundColor(TMColor.gold)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Capsule().fill(TMColor.gold.opacity(0.12)))
        }
    }

    private var timeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("拍摄时间")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(TMColor.textSecondary)
                Spacer()
                Button {
                    model.useCurrentTime()
                } label: {
                    Text("使用当前时间")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(TMColor.gold)
                }
                .buttonStyle(.plain)
            }
            HStack(spacing: 8) {
                DatePicker(
                    "",
                    selection: Binding(
                        get: { model.source.date },
                        set: { model.setDate($0) }
                    ),
                    displayedComponents: [.date, .hourAndMinute]
                )
                .labelsHidden()
                .datePickerStyle(.compact)

                Spacer()

                if !model.source.weather.isBlank {
                    Button {
                        model.refreshWeather()
                    } label: {
                        InfoTag(icon: "cloud.sun.fill", text: model.source.weather, tint: TMColor.textSecondary)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var coordinateSection: some View {
        HStack(spacing: 6) {
            Text("坐标")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(TMColor.textSecondary)
            ForEach(CoordinateSystem.allCases) { system in
                ChipButton(
                    title: system.title,
                    selected: model.coordSystem == system
                ) {
                    model.setCoordSystem(system)
                }
            }
            Spacer()
            if let precision = model.precision {
                InfoTag(text: precision.title, tint: TMColor.success)
            }
        }
    }

    private var fieldSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("显示内容")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(TMColor.textSecondary)
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 76), spacing: 6)],
                alignment: .leading,
                spacing: 6
            ) {
                ForEach(fieldToggles) { item in
                    ChipButton(
                        title: item.title,
                        selected: model.template.fields[keyPath: item.keyPath]
                    ) {
                        model.toggleField(item.keyPath)
                    }
                }
            }
            HStack(spacing: 8) {
                ChipButton(title: "时间显示秒", selected: model.template.fields.timeWithSeconds) {
                    model.updateFields { $0.timeWithSeconds.toggle() }
                }
                ChipButton(title: "24 小时制", selected: model.template.fields.time24h) {
                    model.updateFields { $0.time24h.toggle() }
                }
                Spacer()
            }
        }
    }
}

// MARK: - Export success

@MainActor
private struct ExportSuccessSheet: View {
    let payload: ExportPayload
    let onClose: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var share: SharePayload?

    var body: some View {
        ZStack {
            Color(argb: 0xFF0B0F14).ignoresSafeArea()
            VStack(spacing: 18) {
                Capsule()
                    .fill(Color.white.opacity(0.2))
                    .frame(width: 38, height: 4)
                    .padding(.top, 8)

                Text("已保存到相册")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(TMColor.textPrimary)
                Text("原图分辨率导出，可在「照片 · TraceMark」中查看")
                    .font(.system(size: 12))
                    .foregroundColor(TMColor.textSecondary)

                Image(uiImage: payload.image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxHeight: 320)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .strokeBorder(TMColor.hairline, lineWidth: 1)
                    )

                Text(payload.fileName)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(TMColor.textTertiary)

                HStack(spacing: 10) {
                    Button {
                        if let url = try? PhotoSaver.writeTemporaryFile(
                            data: payload.data,
                            fileName: payload.fileName
                        ) {
                            share = SharePayload(items: [url])
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.system(size: 13, weight: .semibold))
                            Text("分享")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        .foregroundColor(TMColor.textPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(TMColor.chip)
                        )
                    }
                    .buttonStyle(.plain)

                    Button {
                        onClose()
                        dismiss()
                    } label: {
                        Text("完成")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(GoldGradient.linear)
                            )
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)

                Spacer(minLength: 8)
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .sheet(item: $share) { item in
            ShareSheet(items: item.items)
        }
    }
}

private struct SharePayload: Identifiable {
    let id = UUID()
    let items: [Any]
}
