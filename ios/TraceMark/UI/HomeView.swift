import Photos
import PhotosUI
import SwiftUI
import UIKit

@MainActor
struct HomeView: View {
    @ObservedObject var settingsStore: SettingsStore
    @ObservedObject var templateStore: TemplateStore

    @State private var pickerItem: PhotosPickerItem?
    @State private var loading = false
    @State private var errorMessage: String?
    @State private var editorPhoto: PickedPhoto?
    @State private var showSettings = false

    private var defaultTemplate: WatermarkTemplate {
        templateStore.template(id: settingsStore.value.defaultTemplateId)
    }

    var body: some View {
        ZStack {
            AppBackground()
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    hero
                    importButton
                    featureRow
                    templateSection
                    serviceSummary
                    footerNote
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 28)
            }

            if loading {
                LoadingOverlay(text: "正在读取照片与 EXIF 信息…")
            }
        }
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showSettings) {
            SettingsView(settingsStore: settingsStore, templateStore: templateStore)
        }
        .fullScreenCover(item: $editorPhoto) { photo in
            EditorView(photo: photo, settingsStore: settingsStore, templateStore: templateStore)
        }
        .onAppear {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("-traceMarkDebugEditor"), editorPhoto == nil {
                editorPhoto = .debugSample
            }
            #endif
        }
        .onChange(of: pickerItem) { _ in
            Task { await loadPickedPhoto() }
        }
        .alert("无法读取照片", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("好", role: .cancel) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .fill(GoldGradient.linear)
                Image(systemName: "mappin.and.ellipse")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.black)
            }
            .frame(width: 44, height: 44)
            .shadow(color: TMColor.gold.opacity(0.4), radius: 12, y: 5)

            VStack(alignment: .leading, spacing: 2) {
                Text("迹印相机")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(TMColor.textPrimary)
                Text("真实经纬 · 现场水印")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(TMColor.textSecondary)
            }
            Spacer()
            IconCircleButton(systemName: "gearshape.fill", size: 40) {
                showSettings = true
            }
        }
    }

    // MARK: - Hero

    private var hero: some View {
        VStack(alignment: .leading, spacing: 10) {
            ZStack(alignment: .topLeading) {
                Image(uiImage: TemplateThumbnailCache.image(
                    for: defaultTemplate,
                    size: CGSize(width: 600, height: 800)
                ))
                .resizable()
                .aspectRatio(3.0 / 4.0, contentMode: .fit)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))

                HStack(spacing: 6) {
                    InfoTag(icon: "checkmark.seal.fill", text: "所见即所得", tint: TMColor.gold)
                    InfoTag(icon: "location.fill", text: "GPS + 地址")
                }
                .padding(14)
            }
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.1), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.5), radius: 24, y: 14)

            HStack {
                Text(defaultTemplate.name)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(TMColor.gold)
                Text(defaultTemplate.description)
                    .font(.system(size: 11))
                    .foregroundColor(TMColor.textSecondary)
                    .lineLimit(1)
                Spacer()
                Text("默认模板")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(TMColor.textTertiary)
            }
            .padding(.horizontal, 4)
        }
    }

    // MARK: - Import

    private var importButton: some View {
        VStack(spacing: 8) {
            PhotosPicker(
                selection: $pickerItem,
                matching: .images,
                photoLibrary: .shared()
            ) {
                HStack(spacing: 9) {
                    Image(systemName: "photo.badge.plus")
                        .font(.system(size: 17, weight: .bold))
                    Text("导入照片，开始加水印")
                        .font(.system(size: 16, weight: .bold))
                }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(GoldGradient.linear)
                )
                .shadow(color: TMColor.gold.opacity(0.35), radius: 18, y: 8)
            }

            Text("自动读取 EXIF 经纬度、拍摄时间与设备信息，可手动修改")
                .font(.system(size: 11))
                .foregroundColor(TMColor.textTertiary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
    }

    // MARK: - Features

    private var featureRow: some View {
        HStack(spacing: 10) {
            featureCard(icon: "location.circle.fill", title: "GPS 解析", subtitle: "9 家逆地理服务")
            featureCard(icon: "square.stack.3d.up.fill", title: "6 套模板", subtitle: "可另存自定义")
            featureCard(icon: "arrow.up.left.and.arrow.down.right", title: "原图导出", subtitle: "最高 6000px")
        }
    }

    private func featureCard(icon: String, title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(TMColor.gold)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(TMColor.textPrimary)
                Text(subtitle)
                    .font(.system(size: 10))
                    .foregroundColor(TMColor.textTertiary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(TMColor.panel.opacity(0.8))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(TMColor.hairline, lineWidth: 1)
                )
        )
    }

    // MARK: - Templates

    private var templateSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(title: "水印模板", detail: "点选设为默认")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(templateStore.all) { template in
                        Button {
                            settingsStore.value.defaultTemplateId = template.id
                        } label: {
                            TemplateThumb(
                                template: template,
                                selected: template.id == settingsStore.value.defaultTemplateId,
                                width: 92
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    // MARK: - Summary

    private var serviceSummary: some View {
        Button {
            showSettings = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(TMColor.gold)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(TMColor.gold.opacity(0.12)))
                VStack(alignment: .leading, spacing: 2) {
                    Text(providerSummary)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(TMColor.textPrimary)
                    Text(settingsStore.value.configuredKeyCount == 0
                        ? "未填写 Key 时自动使用免费服务，可配置高德/百度获得门牌级地址"
                        : "已配置 \(settingsStore.value.configuredKeyCount)/6 家 Key，优先输出门牌级地址")
                        .font(.system(size: 10))
                        .foregroundColor(TMColor.textTertiary)
                        .lineLimit(2)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(TMColor.textTertiary)
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(TMColor.panel.opacity(0.8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .strokeBorder(TMColor.hairline, lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }

    private var providerSummary: String {
        let id = settingsStore.value.geoProvider
        if id == "auto" {
            return "地址解析：自动择优"
        }
        return "地址解析：\(GeoProviderRegistry.info(id)?.name ?? id)"
    }

    private var footerNote: some View {
        VStack(spacing: 6) {
            HStack(spacing: 5) {
                Image(systemName: "iphone.gen3")
                    .font(.system(size: 10))
                Text("个人自签版本 · 免费证书 7 天有效")
                    .font(.system(size: 10))
            }
            Text("需要 iOS 16+ 并开启「设置 → 隐私与安全性 → 开发者模式」")
                .font(.system(size: 10))
                .multilineTextAlignment(.center)
        }
        .foregroundColor(TMColor.textTertiary)
        .frame(maxWidth: .infinity)
        .padding(.top, 6)
    }

    // MARK: - Loading

    private func loadPickedPhoto() async {
        guard let item = pickerItem, !loading else { return }
        loading = true
        defer {
            loading = false
            pickerItem = nil
        }
        do {
            let photo = try await Task.detached(priority: .userInitiated) { () -> PickedPhoto in
                guard let data = try await item.loadTransferable(type: Data.self) else {
                    throw PhotoPickError.noData
                }
                let meta = PhotoLoader.meta(from: data)
                guard let preview = PhotoLoader.thumbnail(data: data, maxPixel: 2000) else {
                    throw PhotoPickError.decodeFailed
                }
                return PickedPhoto(data: data, meta: meta, preview: preview)
            }.value
            editorPhoto = photo
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private enum PhotoPickError: LocalizedError {
    case noData
    case decodeFailed

    var errorDescription: String? {
        switch self {
        case .noData: return "没有读取到照片数据，请换一张试试。"
        case .decodeFailed: return "无法解码这张照片，支持 JPEG / HEIC / PNG。"
        }
    }
}
