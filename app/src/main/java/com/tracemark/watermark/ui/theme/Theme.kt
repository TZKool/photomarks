package com.tracemark.watermark.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Gold = Color(0xFFF2B33D)
val GoldDeep = Color(0xFFB27B16)
val Ink = Color(0xFF0A0C10)
val Panel = Color(0xFF141821)
val PanelHigh = Color(0xFF1D2330)
val HairLine = Color(0x1FFFFFFF)
val TextPrimary = Color(0xFFF5F7FA)
val TextSecondary = Color(0xFF98A2B3)
val Mint = Color(0xFF6FD8B2)
val Danger = Color(0xFFFF6B6B)

enum class AccentPreset(val label: String, val argb: Long) {
    GOLD("鎏金", 0xFFF2B33D),
    AMBER("日落金", 0xFFE9B949),
    CRIMSON("品牌红", 0xFFB42128),
    SKY("打卡蓝", 0xFF2F9EFF),
    CHAMPAGNE("香槟金", 0xFFE7C46A),
    MINT("薄荷绿", 0xFF4ED1A1),
    GRAPHITE("石墨白", 0xFFFFFFFF),
    VIOLET("暮光紫", 0xFF9D7BFF);

    val color: Color get() = Color(argb)
}

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF271A03),
    primaryContainer = Color(0xFF3D2B0A),
    onPrimaryContainer = Color(0xFFFFD98A),
    secondary = Mint,
    onSecondary = Color(0xFF06281F),
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    outline = Color(0xFF2A3240),
    outlineVariant = HairLine,
    error = Danger
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C6A0E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE7B8),
    onPrimaryContainer = Color(0xFF3A2800),
    secondary = Color(0xFF0E7A5F),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF12161D),
    surface = Color.White,
    onSurface = Color(0xFF12161D),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF586170),
    outline = Color(0xFFC7CCD4),
    outlineVariant = Color(0x14000000),
    error = Color(0xFFC0392B)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.4.sp)
)

@Composable
fun TraceTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
