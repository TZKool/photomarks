# 迹印相机 iOS · TraceMark

与 Android 版同功能的原生 iOS 实现：SwiftUI + CoreGraphics，最低 iOS 16.0，Bundle ID `com.tzkool.tracemark`，显示名「迹印相机」。

由于没有 Apple 开发者证书，本项目采用 **GitHub Actions 构建未签名 IPA + 个人 Apple ID 自签安装** 的方式分发。

---

## 目录

```
ios/
├── project.yml                  # XcodeGen 工程描述（生成 TraceMark.xcodeproj）
├── TraceMark/
│   ├── TraceMarkApp.swift       # App 入口
│   ├── Info.plist               # 由 XcodeGen 生成，勿手改
│   ├── Models.swift             # 模板/字段/变换/水印数据模型
│   ├── Templates.swift          # 6 套内置模板 + 自定义模板持久化
│   ├── Settings.swift           # 设置持久化（UserDefaults JSON）
│   ├── CoordTransform.swift     # WGS84 / GCJ-02 / BD-09 互转
│   ├── Formatter.swift          # 水印文案合成 + 防伪码
│   ├── Geo/                     # 9 家逆地理编码 + Open-Meteo 天气
│   ├── Photo/                   # EXIF 读取、缩略图、JPEG 导出、相册写入
│   ├── Render/                  # 单一 CoreGraphics 水印渲染器
│   ├── UI/                      # 首页、编辑器、设置、组件、主题
│   └── Assets.xcassets/         # AppIcon（1024 单尺寸）
└── README.md
```

## 构建方式一：GitHub Actions（推荐）

仓库内置工作流 `.github/workflows/ios.yml`，在 `macos-15` 上：

1. `brew install xcodegen`
2. `xcodegen generate` 生成 `TraceMark.xcodeproj`
3. `xcodebuild -configuration Release -sdk iphoneos CODE_SIGNING_ALLOWED=NO`
4. 打包 `Payload/TraceMark.app` 为未签名 IPA
5. 上传产物 `TraceMark-unsigned-ipa`

使用方法：

1. 把代码 push 到 GitHub 仓库（`main` 分支，改动 `ios/**` 或工作流文件即触发）。
2. 打开 **Actions → iOS Build → 最新一次运行**。
3. 在页面底部 **Artifacts** 下载 `TraceMark-unsigned-ipa.zip`，解压得到 `TraceMark-unsigned.ipa`。

也可以手动触发：Actions → iOS Build → Run workflow。

## 构建方式二：本地 macOS

```bash
brew install xcodegen
cd ios
xcodegen generate
open TraceMark.xcodeproj
```

在 Xcode 中选择自己的 Team（自动签名）、真机运行即可。若只需要 IPA，可执行：

```bash
xcodebuild -project TraceMark.xcodeproj -scheme TraceMark \
  -configuration Release -sdk iphoneos \
  -destination 'generic/platform=iOS' build
```

## 自签安装（Sideloadly / AltStore）

免费 Apple ID 签发的证书 **7 天有效**，到期用同一工具重新签名即可，App 数据不会丢失。

### Sideloadly（Windows / macOS 均可）

1. 安装 iTunes（Windows 需 Apple 移动设备支持）与 [Sideloadly](https://sideloadly.io/)。
2. 数据线连接 iPhone，选择 `TraceMark-unsigned.ipa`。
3. Apple ID 一栏填个人账号（会用于生成免费证书，只在本机与 Apple 服务器之间验证）。
4. 点 Start，按提示输入密码；如开启双重认证，需使用专用 App 密码。
5. 安装完成后，在 iPhone **设置 → 通用 → VPN 与设备管理** 中信任你的开发者证书。
6. iOS 16 及以上还需要 **设置 → 隐私与安全性 → 开发者模式** 打开并重启设备（若入口未出现，先连接一次 Xcode 或用 Sideloadly 安装后即可出现）。

### AltStore / SideStore

1. 在电脑安装 AltServer 并登录同一 Apple ID。
2. 将 IPA 放入 AltStore 「My Apps」中安装；AltStore 会在后台 7 天内自动续签。

### 常见问题

| 现象 | 处理 |
| --- | --- |
| 提示「无法验证 App」 | 设置 → 通用 → VPN 与设备管理 → 信任对应开发者 |
| 提示需要开发者模式 | 设置 → 隐私与安全性 → 开发者模式 → 打开并重启 |
| 打开即闪退 | 确认 iOS ≥ 16.0；删掉旧签名版本后重新安装 |
| 7 天后无法打开 | 用 Sideloadly / AltStore 重新签名安装，数据保留 |
| 免费账号最多 3 个自签 App | 删除其他自签应用或换账号 |

> 自签仅适合个人使用，不能上架 App Store，也不支持推送等需要付费开发者账号的能力。

## 功能对照

- **EXIF 自动读取**：拍摄时间、厂商/机型/镜头、GPS 与海拔、方向（`kCGImageSourceCreateThumbnailWithTransform` 自动转正）。
- **9 家逆地理解析**：高德、百度、腾讯、天地图、Mapbox、Geoapify、Photon、Nominatim、BigDataCloud；自动链式尝试，命中目标精度即停止；10 分钟缓存。
- **实时预览**：拖拽、双指缩放、旋转、长按对比原图；水印与导出共用同一渲染器，短边推导演算，预览与成片一致。
- **6 套模板 + 自定义**：经纬现场、极简大字、红标品牌、蓝色打卡、金色竖线、底部胶囊；可改主色、背景、锚点、大小、透明度、字段开关并另存为模板。
- **原图导出**：长边 2048 / 4096 / 6000 可选，JPEG 质量 60–100；写入相册「TraceMark」并使用 `PHAssetCreationRequest` 保留资源名。
- **EXIF 写回**：方向归一、像素尺寸更新、`UserComment` 写入纬度/经度/地址/时间/防伪码、`TIFF Software = TraceMark 1.1 (iOS)`。
- **天气**：Open-Meteo，历史日期自动切换 archive 接口，无需 Key。
- **坐标系**：WGS84 / GCJ-02 / BD-09，内置迭代反算。

## 配置逆地理 Key

设置 → 逆地理编码 → 选择服务商并粘贴 Key（Key 只保存在本机 UserDefaults）：

| 服务 | 控制台 |
| --- | --- |
| 高德（Web 服务 Key） | https://console.amap.com/dev/key/app |
| 百度（服务端/浏览器端 AK） | https://lbsyun.baidu.com/apiconsole/key |
| 腾讯（WebService Key） | https://lbs.qq.com/dev/console/application/mine |
| 天地图（服务端 tk） | https://console.tianditu.gov.cn/api/key |
| Mapbox Token | https://account.mapbox.com/access-tokens/ |
| Geoapify Key | https://myprojects.geoapify.com/ |

未配置 Key 时自动退回 Photon / Nominatim / BigDataCloud 三家免费服务。

## 实现说明

- 预览与导出均由 `Render/WatermarkRenderer.swift` 绘制，所有尺寸按图片短边比例计算。
- 图片解码走 ImageIO，以 `CGImageSourceCreateThumbnailAtIndex` 生成带方向校正的缩略图；导出在后台线程用 `UIGraphicsImageRenderer` 合成后经 `CGImageDestination` 写 JPEG。
- 模板与设置使用 Codable JSON 存于 UserDefaults（`TraceMark.CustomTemplates.v1` / `TraceMark.Settings.v1`）。
- 手势状态存放在独立的 `TransformState`，拖动/缩放时只重绘预览层，不阻塞面板交互。
