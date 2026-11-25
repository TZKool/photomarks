package com.tracemark.watermark.model

object BuiltInTemplates {

    private val defaultFields = FieldConfig()

    val coordinateList = WatermarkTemplate(
        id = "builtin_coordinate",
        name = "经纬现场",
        description = "经纬度 + 地址 + 拍摄时间，元道经纬风格",
        builtIn = true,
        style = TemplateStyle(
            layout = TemplateLayout.COORDINATE_LIST,
            accentColor = 0xFFE9B949.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            subTextColor = 0xF2FFFFFF.toInt(),
            background = BackdropStyle.NONE,
            anchor = Anchor.BOTTOM_LEFT,
            marginRatio = 0.05f,
            letterSpacing = 0.01f,
            shadow = true,
            brandLabel = "迹印经纬"
        ),
        fields = defaultFields.copy(
            showHeader = true,
            showAddress = true,
            showCoords = true,
            showTime = true,
            showDate = false,
            showWeek = false,
            showWeather = false,
            showNote = true,
            showDevice = false,
            showLogo = true,
            showCode = true
        )
    )

    val heroTime = WatermarkTemplate(
        id = "builtin_hero",
        name = "极简大字",
        description = "超大时间 + 日期地址，干净通透",
        builtIn = true,
        style = TemplateStyle(
            layout = TemplateLayout.HERO_TIME,
            accentColor = 0xFFFFC93C.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            subTextColor = 0xE6FFFFFF.toInt(),
            background = BackdropStyle.NONE,
            anchor = Anchor.BOTTOM_LEFT,
            marginRatio = 0.045f,
            shadow = true,
            brandLabel = "迹印相机"
        ),
        fields = defaultFields.copy(
            showHeader = false,
            showCoords = false,
            showAddress = true,
            showTime = true,
            showDate = true,
            showWeek = true,
            showWeather = true,
            showNote = false,
            showLogo = true,
            showCode = true
        )
    )

    val brandBadge = WatermarkTemplate(
        id = "builtin_brand",
        name = "红标品牌",
        description = "品牌色块 + 时间 + 天气，门店打卡常用",
        builtIn = true,
        style = TemplateStyle(
            layout = TemplateLayout.BRAND_BADGE,
            accentColor = 0xFFB42128.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            subTextColor = 0xF0FFFFFF.toInt(),
            background = BackdropStyle.NONE,
            anchor = Anchor.BOTTOM_LEFT,
            marginRatio = 0.045f,
            brandLabel = "门店打卡"
        ),
        fields = defaultFields.copy(
            showHeader = true,
            showCoords = false,
            showAddress = true,
            showTime = true,
            showDate = true,
            showWeek = true,
            showWeather = true,
            showNote = false,
            showLogo = true,
            showCode = false
        )
    )

    val checkinCard = WatermarkTemplate(
        id = "builtin_checkin",
        name = "蓝色打卡",
        description = "打卡记录卡片，时间醒目不遮挡画面",
        builtIn = true,
        style = TemplateStyle(
            layout = TemplateLayout.CHECKIN_CARD,
            accentColor = 0xFF2F9EFF.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            subTextColor = 0xD9FFFFFF.toInt(),
            cardColor = 0xFFFFFFFF.toInt(),
            cardTextColor = 0xFF101820.toInt(),
            background = BackdropStyle.CARD,
            anchor = Anchor.TOP_LEFT,
            marginRatio = 0.045f,
            cornerRatio = 0.02f
        ),
        fields = defaultFields.copy(
            showHeader = true,
            showCoords = false,
            showAddress = true,
            showTime = true,
            showDate = true,
            showWeek = true,
            showWeather = false,
            showNote = false,
            showSeal = true,
            showLogo = true,
            showCode = true,
            showBrand = true
        )
    )

    val goldDivider = WatermarkTemplate(
        id = "builtin_gold",
        name = "金色竖线",
        description = "金属质感时间配金色分隔线，质感高级",
        builtIn = true,
        style = TemplateStyle(
            layout = TemplateLayout.GOLD_DIVIDER,
            accentColor = 0xFFE7C46A.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            subTextColor = 0xE6FFFFFF.toInt(),
            background = BackdropStyle.NONE,
            anchor = Anchor.BOTTOM_LEFT,
            marginRatio = 0.05f,
            brandLabel = "迹印相机"
        ),
        fields = defaultFields.copy(
            showHeader = false,
            showCoords = false,
            showAddress = true,
            showTime = true,
            showDate = true,
            showWeek = true,
            showWeather = true,
            showNote = false,
            showLogo = true,
            showCode = true
        )
    )

    val bottomPill = WatermarkTemplate(
        id = "builtin_pill",
        name = "底部胶囊",
        description = "胶囊打卡 + 地址日期，信息量充足",
        builtIn = true,
        style = TemplateStyle(
            layout = TemplateLayout.BOTTOM_PILL,
            accentColor = 0xFFF5B53C.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            subTextColor = 0xE0FFFFFF.toInt(),
            cardColor = 0xFFFFFFFF.toInt(),
            cardTextColor = 0xFF14181F.toInt(),
            background = BackdropStyle.SCRIM,
            anchor = Anchor.BOTTOM_LEFT,
            marginRatio = 0.04f,
            brandLabel = "迹印相机"
        ),
        fields = defaultFields.copy(
            showHeader = true,
            showCoords = false,
            showAddress = true,
            showTime = true,
            showDate = true,
            showWeek = true,
            showWeather = false,
            showNote = false,
            showSeal = true,
            showLogo = true,
            showCode = true
        )
    )

    val all: List<WatermarkTemplate> = listOf(
        coordinateList, heroTime, brandBadge, checkinCard, goldDivider, bottomPill
    )

    fun byId(id: String): WatermarkTemplate? = all.firstOrNull { it.id == id }

    fun defaultFor(id: String?): WatermarkTemplate = byId(id ?: "") ?: coordinateList
}
