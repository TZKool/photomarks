package com.tracemark.watermark.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tracemark.watermark.model.Anchor
import com.tracemark.watermark.model.CoordinateSystem
import com.tracemark.watermark.model.FormattedWatermark
import com.tracemark.watermark.model.WatermarkSource
import com.tracemark.watermark.model.WatermarkTemplate
import com.tracemark.watermark.model.WatermarkTransform
import com.tracemark.watermark.render.WatermarkRenderer
import com.tracemark.watermark.ui.theme.Gold
import com.tracemark.watermark.ui.theme.HairLine
import com.tracemark.watermark.ui.theme.Panel
import com.tracemark.watermark.ui.theme.TextSecondary
import com.tracemark.watermark.util.WatermarkFormatter
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.math.roundToInt

object SampleData {
    private val source = WatermarkSource(
        dateTime = LocalDateTime.of(2025, 6, 22, 19, 16, 43),
        latitude = 39.908735,
        longitude = 116.397477,
        altitude = 52.0,
        accuracyMeters = 8f,
        address = "北京市东城区东长安街 天安门广场东侧路 12 号",
        weather = "晴 21°C",
        note = "项目现场记录",
        brand = "迹印相机",
        device = "Xiaomi 15 Pro",
        code = "T8K2M9Q7XR4D"
    )

    fun textFor(template: WatermarkTemplate): FormattedWatermark =
        WatermarkFormatter.format(source, template, CoordinateSystem.WGS84)

    fun formatted(source: WatermarkSource, template: WatermarkTemplate): FormattedWatermark =
        WatermarkFormatter.format(source, template, CoordinateSystem.WGS84)
}

@Composable
fun TemplateThumbnail(
    template: WatermarkTemplate,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp
) {
    val text = remember(template) { SampleData.textFor(template) }
    val shape = RoundedCornerShape(corner)
    Canvas(modifier.clip(shape)) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFF4A6076), Color(0xFF141C26), Color(0xFF0B1016)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
        drawCircle(Color(0x38FFD9A0), radius = size.minDimension * 0.5f, center = Offset(size.width * 0.82f, size.height * 0.18f))
        drawCircle(Color(0x2246C7FF), radius = size.minDimension * 0.34f, center = Offset(size.width * 0.12f, size.height * 0.82f))
        val ground = Path().apply {
            moveTo(0f, size.height * 0.74f)
            lineTo(size.width * 0.30f, size.height * 0.40f)
            lineTo(size.width * 0.55f, size.height * 0.66f)
            lineTo(size.width * 0.76f, size.height * 0.50f)
            lineTo(size.width, size.height * 0.76f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(ground, Color(0xFF16202B))
        drawIntoCanvas { canvas ->
            WatermarkRenderer.draw(canvas.nativeCanvas, size.width, size.height, template, text, WatermarkTransform())
        }
    }
}

@Composable
fun WatermarkPreview(
    bitmap: Bitmap,
    template: WatermarkTemplate?,
    text: FormattedWatermark?,
    transform: WatermarkTransform,
    showOriginal: Boolean,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    onGesture: (dx: Float, dy: Float, zoom: Float, rotation: Float) -> Unit = { _, _, _, _ -> }
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(
        modifier.pointerInput(bitmap) {
            detectTransformGestures { _, pan, zoom, rotation ->
                val iw = bitmap.width.toFloat()
                val ih = bitmap.height.toFloat()
                if (iw <= 0f || ih <= 0f) return@detectTransformGestures
                val s = min(size.width / iw, size.height / ih)
                val w = iw * s
                val h = ih * s
                if (w > 1f && h > 1f) onGesture(pan.x / w, pan.y / h, zoom, rotation)
            }
        }
    ) {
        val iw = bitmap.width.toFloat()
        val ih = bitmap.height.toFloat()
        val s = min(size.width / iw, size.height / ih)
        val dw = iw * s
        val dh = ih * s
        val left = (size.width - dw) / 2f
        val top = (size.height - dh) / 2f
        translate(left, top) {
            val clip = Path().apply {
                addRoundRect(
                    RoundRect(0f, 0f, dw, dh, CornerRadius(corner.toPx(), corner.toPx()))
                )
            }
            clipPath(clip) {
                drawImage(
                    image = image,
                    dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1))
                )
                if (!showOriginal && template != null && text != null) {
                    drawIntoCanvas { canvas ->
                        WatermarkRenderer.draw(canvas.nativeCanvas, dw, dh, template, text, transform)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke(this)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Panel.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(Modifier.border(1.dp, HairLine, RoundedCornerShape(18.dp)).padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun InfoTag(text: String, color: Color = Gold) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AnchorPicker(selected: Anchor, modifier: Modifier = Modifier, onSelect: (Anchor) -> Unit) {
    val rows = listOf(
        listOf(Anchor.TOP_LEFT, Anchor.TOP_CENTER, Anchor.TOP_RIGHT),
        listOf(Anchor.CENTER_LEFT, Anchor.CENTER, Anchor.CENTER_RIGHT),
        listOf(Anchor.BOTTOM_LEFT, Anchor.BOTTOM_CENTER, Anchor.BOTTOM_RIGHT)
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { anchor ->
                    val active = anchor == selected
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(
                                if (active) Gold.copy(alpha = 0.22f) else Color.Transparent,
                                RoundedCornerShape(9.dp)
                            )
                            .border(
                                1.dp,
                                if (active) Gold else HairLine,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable { onSelect(anchor) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(if (active) Gold else Color(0xFF56606E), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentShotThumb(uri: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF151A22))
    )
}

@Composable
fun MinusPlusRow(value: Float, label: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        StepDot("-", onDecrease)
        Text(String.format("%.2f", value), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        StepDot("+", onIncrease)
    }
}

@Composable
private fun StepDot(sign: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .border(1.dp, HairLine, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(sign, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
}

fun formatDistance(meters: Double?): String = when {
    meters == null -> ""
    meters < 1000 -> "${meters.roundToInt()} m"
    else -> String.format("%.1f km", meters / 1000)
}
