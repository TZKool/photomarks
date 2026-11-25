import SwiftUI
import UIKit

@MainActor
struct SettingsView: View {
    @ObservedObject var settingsStore: SettingsStore
    @ObservedObject var templateStore: TemplateStore

    @Environment(\.dismiss) private var dismiss
    @State private var showResetConfirm = false
    @State private var cacheCleared = false
    @State private var cacheResetTask: Task<Void, Never>?

    private var settings: AppSettings { settingsStore.value }

    var body: some View {
        ZStack {
            AppBackground()
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    providerSection
                    displaySection
                    exportSection
                    otherSection
                    aboutCard
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 30)
            }
        }
        .preferredColorScheme(.dark)
        .confirmationDialog("恢复默认设置？", isPresented: $showResetConfirm, titleVisibility: .visible) {
            Button("恢复默认", role: .destructive) {
                settingsStore.value = AppSettings()
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("所有 Key、导出选项和默认模板都会还原。")
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: 12) {
            IconCircleButton(systemName: "xmark") { dismiss() }
            VStack(alignment: .leading, spacing: 2) {
                Text("设置")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(TMColor.textPrimary)
                Text("地址精度、导出与模板")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(TMColor.textSecondary)
            }
            Spacer()
        }
        .padding(.top, 4)
    }

    // MARK: - Providers

    private var providerSection: some View {
        settingsSection(title: "逆地理编码") {
            VStack(spacing: 10) {
                providerRow(
                    id: "auto",
                    name: "自动择优",
                    subtitle: "按高德 → 百度 → 腾讯 → 天地图 → Mapbox → Geoapify → Photon → Nominatim → BigDataCloud 顺序尝试，达到精度即停止",
                    requiresKey: false
                )

                ForEach(GeoProviderRegistry.all.map { $0.info }, id: \.id) { info in
                    providerRow(
                        id: info.id,
                        name: info.name,
                        subtitle: info.note,
                        requiresKey: info.requiresKey
                    )
                }

                if let info = GeoProviderRegistry.info(settings.geoProvider), info.requiresKey {
                    keyEditor(for: info)
                }

                Divider().overlay(TMColor.hairline)

                VStack(alignment: .leading, spacing: 8) {
                    Text("地址精度目标")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(TMColor.textSecondary)
                    HStack(spacing: 8) {
                        ForEach(AddressLevel.allCases) { level in
                            ChipButton(
                                title: level.title,
                                selected: settings.addressLevel == level
                            ) {
                                settingsStore.value.addressLevel = level
                            }
                        }
                        Spacer()
                    }
                    Text("目标越高会尝试更多服务；免费服务通常只能到道路/乡镇级。")
                        .font(.system(size: 10))
                        .foregroundColor(TMColor.textTertiary)
                }
            }
        }
    }

    private func providerRow(id: String, name: String, subtitle: String, requiresKey: Bool) -> some View {
        let selected = settings.geoProvider == id
        let configured = !settings.key(for: id).isBlank
        return Button {
            settingsStore.value.geoProvider = id
        } label: {
            HStack(alignment: .top, spacing: 10) {
                ZStack {
                    Circle()
                        .fill(selected ? TMColor.gold : TMColor.chip)
                        .frame(width: 22, height: 22)
                    if selected {
                        Image(systemName: "checkmark")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.black)
                    }
                }
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(name)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(TMColor.textPrimary)
                        if requiresKey {
                            Text(configured ? "已配置" : "需 Key")
                                .font(.system(size: 9, weight: .semibold))
                                .foregroundColor(configured ? TMColor.success : TMColor.gold)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(
                                    Capsule().fill(
                                        (configured ? TMColor.success : TMColor.gold).opacity(0.12)
                                    )
                                )
                        }
                    }
                    Text(subtitle)
                        .font(.system(size: 10))
                        .foregroundColor(TMColor.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func keyEditor(for info: ProviderInfo) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(info.keyLabel.isEmpty ? "\(info.name) Key" : info.keyLabel)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(TMColor.textSecondary)
                Spacer()
                if let url = URL(string: info.keyUrl) {
                    Link(destination: url) {
                        Text("申请 Key ↗")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(TMColor.gold)
                    }
                }
            }
            HStack(spacing: 8) {
                SecureField(
                    info.keyHint.isEmpty ? "粘贴 \(info.name) Key" : info.keyHint,
                    text: Binding(
                        get: { settings.key(for: info.id) },
                        set: { settingsStore.value.setKey($0.trimmed, for: info.id) }
                    )
                )
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.system(size: 13, design: .monospaced))
                .foregroundColor(TMColor.textPrimary)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(TMColor.chip.opacity(0.8))
                )

                Button {
                    if let text = UIPasteboard.general.string {
                        settingsStore.value.setKey(text.trimmed, for: info.id)
                    }
                } label: {
                    Text("粘贴")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(TMColor.gold)
                        )
                }
                .buttonStyle(.plain)
            }
            if !info.keyHint.isEmpty {
                Text(info.keyHint)
                    .font(.system(size: 10))
                    .foregroundColor(TMColor.textTertiary)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(TMColor.panelStrong)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(TMColor.gold.opacity(0.25), lineWidth: 1)
                )
        )
    }

    // MARK: - Display

    private var displaySection: some View {
        settingsSection(title: "水印显示") {
            VStack(alignment: .leading, spacing: 10) {
                Text("坐标系")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(TMColor.textSecondary)
                HStack(spacing: 8) {
                    ForEach(CoordinateSystem.allCases) { system in
                        ChipButton(
                            title: system.title,
                            selected: settings.coordSystem == system
                        ) {
                            settingsStore.value.coordSystem = system
                        }
                    }
                    Spacer()
                }
                Text("WGS84 为照片原始 GPS；GCJ-02 / BD-09 适配国内地图坐标。")
                    .font(.system(size: 10))
                    .foregroundColor(TMColor.textTertiary)

                Divider().overlay(TMColor.hairline)

                toggleRow(
                    title: "自动获取天气",
                    subtitle: "根据拍摄日期与坐标查询 Open-Meteo 历史天气",
                    isOn: Binding(
                        get: { settings.autoWeather },
                        set: { settingsStore.value.autoWeather = $0 }
                    )
                )
            }
        }
    }

    // MARK: - Export

    private var exportSection: some View {
        settingsSection(title: "导出") {
            VStack(alignment: .leading, spacing: 12) {
                Text("导出长边")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(TMColor.textSecondary)
                HStack(spacing: 8) {
                    ForEach([2048, 4096, 6000], id: \.self) { side in
                        ChipButton(
                            title: "\(side)px",
                            selected: settings.exportMaxSide == side
                        ) {
                            settingsStore.value.exportMaxSide = side
                        }
                    }
                    Spacer()
                }

                VStack(spacing: 6) {
                    HStack {
                        Text("JPEG 质量")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(TMColor.textSecondary)
                        Spacer()
                        Text("\(settings.jpegQuality)")
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                            .foregroundColor(TMColor.gold)
                    }
                    Slider(
                        value: Binding(
                            get: { Double(settings.jpegQuality) },
                            set: { settingsStore.value.jpegQuality = Int($0.rounded()) }
                        ),
                        in: 60...100,
                        step: 1
                    )
                    .tint(TMColor.gold)
                }

                Divider().overlay(TMColor.hairline)

                toggleRow(
                    title: "保留原始 EXIF",
                    subtitle: "保留拍摄参数与相机信息，并写入水印地址、经纬度与防伪码",
                    isOn: Binding(
                        get: { settings.keepExif },
                        set: { settingsStore.value.keepExif = $0 }
                    )
                )

                Divider().overlay(TMColor.hairline)

                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("默认模板")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(TMColor.textPrimary)
                        Text("进入编辑时自动选中")
                            .font(.system(size: 10))
                            .foregroundColor(TMColor.textTertiary)
                    }
                    Spacer()
                    Menu {
                        ForEach(templateStore.all) { template in
                            Button {
                                settingsStore.value.defaultTemplateId = template.id
                            } label: {
                                Label(
                                    template.name,
                                    systemImage: template.id == settings.defaultTemplateId
                                        ? "checkmark"
                                        : "square"
                                )
                            }
                        }
                    } label: {
                        HStack(spacing: 5) {
                            Text(templateStore.template(id: settings.defaultTemplateId).name)
                                .font(.system(size: 12, weight: .semibold))
                            Image(systemName: "chevron.up.chevron.down")
                                .font(.system(size: 9, weight: .bold))
                        }
                        .foregroundColor(TMColor.gold)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(Capsule().fill(TMColor.gold.opacity(0.12)))
                    }
                }
            }
        }
    }

    // MARK: - Other

    private var otherSection: some View {
        settingsSection(title: "其他") {
            VStack(spacing: 12) {
                Button {
                    GeoRepository.shared.clearCache()
                    cacheCleared = true
                    cacheResetTask?.cancel()
                    cacheResetTask = Task {
                        try? await Task.sleep(nanoseconds: 1_600_000_000)
                        cacheCleared = false
                    }
                } label: {
                    HStack {
                        Text("清除地址解析缓存")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(TMColor.textPrimary)
                        Spacer()
                        Text(cacheCleared ? "已清除" : "清除")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(cacheCleared ? TMColor.success : TMColor.gold)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                Divider().overlay(TMColor.hairline)

                Button {
                    showResetConfirm = true
                } label: {
                    HStack {
                        Text("恢复默认设置")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(TMColor.danger)
                        Spacer()
                        Image(systemName: "arrow.counterclockwise")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(TMColor.danger)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var aboutCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(GoldGradient())
                    Image(systemName: "mappin.and.ellipse")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.black)
                }
                .frame(width: 34, height: 34)
                VStack(alignment: .leading, spacing: 1) {
                    Text("迹印相机")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(TMColor.textPrimary)
                    Text("TraceMark 1.0.0 · iOS 16+")
                        .font(.system(size: 10))
                        .foregroundColor(TMColor.textTertiary)
                }
                Spacer()
            }

            Text("自签安装说明")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(TMColor.textSecondary)

            VStack(alignment: .leading, spacing: 6) {
                instructionRow(index: 1, text: "在 GitHub Actions 下载 TraceMark-unsigned.ipa")
                instructionRow(index: 2, text: "使用 Sideloadly / AltStore 以个人 Apple ID 自签安装")
                instructionRow(index: 3, text: "免费签名 7 天有效，到期重新签名即可，数据不丢")
                instructionRow(index: 4, text: "iOS 16+ 需在「设置 → 隐私与安全性」开启开发者模式")
            }

            Text("水印预览与导出共用同一套 CoreGraphics 绘制，导出结果与预览一致。")
                .font(.system(size: 10))
                .foregroundColor(TMColor.textTertiary)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(TMColor.panel.opacity(0.82))
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .strokeBorder(TMColor.hairline, lineWidth: 1)
                )
        )
    }

    // MARK: - Helpers

    private func settingsSection<Content: View>(
        title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(title: title)
            GlassCard(padding: 14) {
                content()
            }
        }
    }

    private func toggleRow(title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(TMColor.textPrimary)
                Text(subtitle)
                    .font(.system(size: 10))
                    .foregroundColor(TMColor.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .tint(TMColor.gold)
    }

    private func instructionRow(index: Int, text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("\(index)")
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(.black)
                .frame(width: 16, height: 16)
                .background(Circle().fill(TMColor.gold))
            Text(text)
                .font(.system(size: 11))
                .foregroundColor(TMColor.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
