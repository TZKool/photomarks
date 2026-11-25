# 迹印相机 · TraceMark

一款以「经纬度 + 地址」为核心的水印相机 App。导入照片后自动读取 EXIF 拍摄时间、设备与 GPS，调用多家逆地理解析服务还原精确地址，在照片上实时叠加可拖拽、可缩放的水印，并以原图画质导出。

内置 6 套参考元道经纬相机与文件夹内 PSD 设计稿的模板，全部字段、颜色、版式都可以在「模板工作室」里改造并保存。

提供 **Android**（Kotlin + Compose）与 **iOS**（SwiftUI + CoreGraphics）两个原生版本。iOS 版通过 GitHub Actions 构建未签名 IPA，再以个人 Apple ID 自签安装，详见 [ios/README.md](ios/README.md)。

---

## 功能

- **EXIF 自动读取**：拍摄时间、厂商/机型/镜头、GPS 经纬度与海拔、方向自动校正。
- **逆地理解析（9 家）**：高德、百度、腾讯、天地图、Mapbox、Geoapify、Photon、Nominatim、BigDataCloud。支持「智能优选」链式尝试，命中门牌号 / POI 即停止。
- **实时预览**：接入圆角照片画布，水印随手势拖动、双指缩放、旋转，调整立即生效；长按画面可对比原图。
- **精度优先**：按高德 AOI/POI、百度语义化地址、腾讯推荐地址等做了服务端重组；可手动从「附近地点」里挑选更准确的 POI；地址文本亦可直接编辑。
- **6 套模板 + 模板工作室**：经纬列表、极简大字、红标品牌、蓝色打卡卡片、金色竖线、底部胶囊；可改主色、底色、锚点、字号、边距、透明度、字段开关，保存为自定义模板。
- **原图导出**：按 4096 / 6000 / 8000 / 12000 最大边长导出 JPEG（默认 6000，质量 95），写入 `Pictures/TraceMark`；可选保留拍摄 EXIF 并写入水印说明与防伪码。
- **系统集成**：Photo Picker 免存储权限选图；支持从相册「分享 → 迹印相机」直接进入编辑。
- **天气**：按 GPS 与拍摄日期调用 Open-Meteo 免费接口（无需 Key），自动匹配晴/雨/温度。
- **坐标系**：WGS84 / GCJ-02（高德腾讯）/ BD-09（百度）三套显示与导出基准，内置迭代反算，误差小于 1 米。

## 构建

环境要求：JDK 17、Android SDK Platform 36（compileSdk 36 / minSdk 26）。

```bash
# 在本机创建 local.properties，指向你的 SDK
echo "sdk.dir=D:\\AndroidSdk" > local.properties

# 构建 Debug APK
./gradlew :app:assembleDebug

# 运行单元测试（坐标转换往返、地址格式化、防伪码稳定性）
./gradlew :app:testDebugUnitTest
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

> `gradle/wrapper/gradle-wrapper.properties` 默认使用腾讯云镜像下载 Gradle 8.13；如网络通畅可换回 `https://services.gradle.org/distributions/gradle-8.13-bin.zip`。

## iOS 自签版

iOS 版源码位于 `ios/`，使用 XcodeGen 管理工程，最低 iOS 16.0。

```bash
cd ios
xcodegen generate        # 生成 TraceMark.xcodeproj（macOS 上需先 brew install xcodegen）
```

推送到 GitHub 后，`.github/workflows/ios.yml` 会在 macOS runner 上执行无签名 Release 构建，产出未签名 IPA：

1. 打开仓库 **Actions → iOS Build**，进入最近一次运行。
2. 下载 Artifacts 中的 `TraceMark-unsigned-ipa.zip`，解压得到 `TraceMark-unsigned.ipa`。
3. 用 Sideloadly / AltStore 以个人 Apple ID 自签安装（免费证书 7 天有效）。
4. 首次打开前在 **设置 → 通用 → VPN 与设备管理** 信任开发者，并在 **隐私与安全性** 中开启开发者模式。

完整安装步骤、常见问题与 iOS 功能说明见 [ios/README.md](ios/README.md)。

## 使用流程

1. 首页点「选择照片」（或从相册分享图片到本 App）。
2. 进入编辑器：顶部实时预览，底部面板依次是地址、模板、显示字段、排版、备注。
3. 有 GPS 时自动解析地址并取天气；点「解析服务」可切换服务商，点「附近地点」可挑选更精确的 POI。
4. 挑选模板、勾选字段，拖动/双指调整水印，点「按住看原图」核对。
5. 「导出高清照片」写入相册；可继续编辑、分享或完成。
6. 想固化当前样式，点「另存为模板」进入模板工作室保存。

## 模板

| 模板 | 版式 | 主色 | 默认锚点 | 主要字段 |
| --- | --- | --- | --- | --- |
| 经纬现场 | `COORDINATE_LIST` 描边标题 + 标签行 | `#E9B949` | 左下 | 标题、纬度、经度、地址、时间、备注、品牌标识 |
| 极简大字 | `HERO_TIME` 超大时间 + 元信息 | `#FFC93C` | 左下 | 时间、日期、星期、天气、地址、经纬度、防伪码 |
| 红标品牌 | `BRAND_BADGE` 品牌色块 + 时间分隔线 | `#B42128` | 左下 | 徽标、时间、日期、星期、天气、地址 |
| 蓝色打卡 | `CHECKIN_CARD` 白色卡片 + 蓝色标题页签 | `#2F9EFF` | 左上 | 打卡时间、日期、地址、核验印章 |
| 金色竖线 | `GOLD_DIVIDER` 时间 + 金色分隔 | `#E7C46A` | 左下 | 时间、日期、星期、天气、地址 |
| 底部胶囊 | `BOTTOM_PILL` 胶囊打卡 + 渐变暗角 | `#F5B53C` | 左下 | 打卡时间、地址、日期、核验印章 |

渲染器 `render/WatermarkRenderer.kt` 只依据图片**短边**推导字号、间距与圆角，因此预览、模板缩略图与全尺寸导出完全同构，所见即所得。

模板持久化在 `filesDir/user_templates/<id>.json`，设置使用 DataStore（`tracemark_settings`）。所有 Key 仅保存在本机。

## 逆地理解析与精度

- 逆解析入口：`geo/GeoRepository.kt`。「智能优选」顺序为 高德 → 百度 → 腾讯 → 天地图 → Mapbox → Geoapify → Photon → Nominatim → BigDataCloud，仅尝试已配置 Key 的服务；未命中目标精度时继续尝试。
- 精度目标：设置中的「道路级 / 门牌号 / POI 兴趣点」，命中即停止。
- 坐标基准：EXIF 原始为 WGS84；高德、腾讯使用 GCJ-02，百度使用 BD-09。各 Provider 内部完成转换后再请求，返回结果统一标记 `coordSystem`。
- 高德请求 `extensions=all`，额外合并 AOI 与周边 POI；百度使用 `sematic_description` 语义化位置；腾讯使用 `formatted_addresses.recommend`。地址重组逻辑位于 `geo/GeoProviders.kt`。
- 结果带 10 分钟 LRU 缓存；设置页可手动清空。

### 配置 Key

设置 → 逆地理解析 → API Key 配置：

| 服务 | 控制台 |
| --- | --- |
| 高德（Web 服务 Key） | https://console.amap.com/dev/key/app |
| 百度（服务端/浏览器端 AK） | https://lbsyun.baidu.com/apiconsole/key |
| 腾讯（WebService Key，勾选逆地址解析） | https://lbs.qq.com/dev/console/application/mine |
| 天地图（服务端 tk） | https://console.tianditu.gov.cn/api/key |
| Mapbox（公共 Token） | https://account.mapbox.com/access-tokens/ |
| Geoapify（免费 API Key） | https://myprojects.geoapify.com/ |

国内用途建议至少配置高德或百度；Photon / Nominatim / BigDataCloud 无需 Key，可直接兜底。

## 导出与 EXIF

`export/PhotoExporter.kt`：

- 解码 + 方向校正 → 按最大边长缩放 → 在 `ARGB_8888` 画布上绘制原图与水印 → JPEG 编码。
- API 29+ 通过 MediaStore 写入 `Pictures/TraceMark`（`IS_PENDING` 保护，失败自动删除占位）；API 26–28 写入公共 Pictures 目录后插入媒体库。
- 保留 EXIF 时写回拍摄时间、厂商/机型/镜头、曝光参数与 GPS；方向归一为 1，并写入 `Software=TraceMark 迹印相机`、`ImageDescription` 与 `UserComment`（含经纬度、地址、防伪码）。
- 大图 OOM 自动降级（6000 → 4096 → 3200 → 2400）重试。

防伪码由 `WatermarkFormatter` 基于坐标与时间生成 12 位 base32（去掉易混淆字符），同一张照片稳定可复现。

## 目录结构

```
app/src/main/java/com/tracemark/watermark/
├── TraceMarkApp.kt            # Application + AppContainer 单例
├── MainActivity.kt            # Photo Picker / SEND 分享入口 / 旧版权限
├── model/                     # 模板、字段、变换、内置 6 套模板
├── geo/                       # GCJ/BD 转换、9 家逆解析、Repository 缓存
├── data/                      # DataStore 设置、EXIF 读取、模板持久化、天气
├── util/WatermarkFormatter.kt # 文案合成、坐标系显示、防伪码
├── render/WatermarkRenderer.kt# 单渲染器：预览 / 缩略图 / 导出共用
├── export/PhotoExporter.kt    # MediaStore 导出、EXIF 写回、分享
└── ui/                        # 首页、编辑器、模板工作室、设置、组件、主题
```

## 参考素材

- `Nipic_6830321_20250622191643363.psd`：已解析出完整设计板（`.work/psd/composite.png`），6 套模板的配色、字段与排版均对照该设计稿实现。
- `yuandaojingweixiangji_39.apk`：仅作交互与信息层级参考（其 AXML 为二进制，未在工程内复用任何其代码或资源）。

## 已知限制

- 本次开发环境没有连接真机/模拟器，功能通过编译、单元测试与静态审查验证，交互与导出流程建议在真机上再做一轮回归。
- 国内厂商免费额度有限，高频解析可能触发限流；失败时会继续尝试下一家并提示具体错误。
- 超大字模板（`HERO_TIME`）信息较多时依赖换行与最大行数截断，极小尺寸缩略图会有细节损失，导出不受影响。
- 逆解析结果由第三方地图服务提供，请遵守各自服务条款；用于法律取证前建议人工核对地址与坐标。
