package com.tracemark.watermark.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.tracemark.watermark.model.Anchor
import com.tracemark.watermark.model.BackdropStyle
import com.tracemark.watermark.model.FieldConfig
import com.tracemark.watermark.model.FormattedWatermark
import com.tracemark.watermark.model.TemplateLayout
import com.tracemark.watermark.model.TemplateStyle
import com.tracemark.watermark.model.WatermarkTemplate
import com.tracemark.watermark.model.WatermarkTransform
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a watermark into any target size. All metrics derive from the image short side,
 * so preview and full-resolution export are pixel-identical in proportion.
 */
object WatermarkRenderer {

    private val sansRegular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val sansBold = Typeface.create("sans-serif", Typeface.BOLD)
    private val condensedBold = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        template: WatermarkTemplate,
        text: FormattedWatermark,
        transform: WatermarkTransform = WatermarkTransform()
    ) {
        if (width <= 8f || height <= 8f) return
        val base = min(width, height)
        val painter = Painter(canvas, base, template.style, template.fields, text, template.style.layout)
        val block = painter.build(template.style.layout, width - painter.margin * 2f)
        if (block.width <= 0f || block.height <= 0f) return

        val scale = transform.scale.coerceIn(0.4f, 2.5f)
        val scaledW = block.width * scale
        val scaledH = block.height * scale
        val margin = painter.margin

        var x = when (template.style.anchor) {
            Anchor.TOP_LEFT, Anchor.CENTER_LEFT, Anchor.BOTTOM_LEFT -> margin
            Anchor.TOP_CENTER, Anchor.CENTER, Anchor.BOTTOM_CENTER -> (width - scaledW) / 2f
            Anchor.TOP_RIGHT, Anchor.CENTER_RIGHT, Anchor.BOTTOM_RIGHT -> width - margin - scaledW
        }
        var y = when (template.style.anchor) {
            Anchor.TOP_LEFT, Anchor.TOP_CENTER, Anchor.TOP_RIGHT -> margin
            Anchor.CENTER_LEFT, Anchor.CENTER, Anchor.CENTER_RIGHT -> (height - scaledH) / 2f
            Anchor.BOTTOM_LEFT, Anchor.BOTTOM_CENTER, Anchor.BOTTOM_RIGHT -> height - margin - scaledH
        }
        x += transform.offsetX * width
        y += transform.offsetY * height

        val opacity = (template.style.opacity.coerceIn(0f, 1f) * 255).roundToInt()

        if (template.style.background == BackdropStyle.SCRIM) {
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, height * 0.52f, 0f, height,
                    intArrayOf(Color.TRANSPARENT, 0x99000000.toInt()),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, height * 0.52f, width, height, scrimPaint)
        }

        canvas.save()
        canvas.saveLayerAlpha(0f, 0f, width, height, opacity)
        val centerX = x + scaledW / 2f
        val centerY = y + scaledH / 2f
        if (transform.rotation != 0f) canvas.rotate(transform.rotation, centerX, centerY)
        if (scale != 1f) canvas.scale(scale, scale, centerX, centerY)
        if (template.style.background == BackdropStyle.CARD &&
            template.style.layout != TemplateLayout.COORDINATE_LIST &&
            template.style.layout != TemplateLayout.CHECKIN_CARD
        ) {
            val pad = base * 0.022f
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (template.style.cardColor and 0x00FFFFFF) or (216 shl 24)
            }
            val radius = base * template.style.cornerRatio.coerceIn(0.002f, 0.08f)
            canvas.drawRoundRect(
                x - pad, y - pad, x + block.width + pad, y + block.height + pad,
                radius, radius, cardPaint
            )
        }
        block.draw(canvas, x, y)
        canvas.restore()
        canvas.restore()
    }

    private class Block(
        val width: Float,
        val height: Float,
        val background: ((Canvas) -> Unit)?,
        val content: (Canvas) -> Unit
    ) {
        fun draw(canvas: Canvas, left: Float, top: Float) {
            canvas.save()
            canvas.translate(left, top)
            background?.invoke(canvas)
            content(canvas)
            canvas.restore()
        }
    }

    private class Column {
        private val draws = ArrayList<(Canvas) -> Unit>()
        var width = 0f
            private set
        var height = 0f
            private set

        fun add(height: Float, width: Float, drawAt: (Canvas, Float) -> Unit) {
            val top = this.height
            draws.add { canvas -> drawAt(canvas, top) }
            this.height += height
            this.width = max(this.width, width)
        }

        fun gap(extra: Float) {
            if (draws.isNotEmpty()) height += extra
        }

        fun content(): (Canvas) -> Unit = { canvas -> draws.forEach { it(canvas) } }
    }

    private class Painter(
        val canvas: Canvas,
        val base: Float,
        val style: TemplateStyle,
        val fields: FieldConfig,
        val text: FormattedWatermark,
        val layout: TemplateLayout
    ) {
        val margin: Float = base * style.marginRatio.coerceIn(0.02f, 0.12f)
        private val accent: Int = style.accentColor
        private val onCard: Boolean = style.background == BackdropStyle.CARD &&
            layout != TemplateLayout.CHECKIN_CARD
        private val textColor: Int = if (onCard) style.cardTextColor else style.textColor
        private val subColor: Int = if (onCard) {
            (style.cardTextColor and 0x00FFFFFF) or (150 shl 24)
        } else {
            style.subTextColor
        }
        private val cardColor: Int = style.cardColor
        private val cardTextColor: Int = style.cardTextColor
        private val radius: Float = base * style.cornerRatio.coerceIn(0.002f, 0.08f)
        private val shadowOffset = base * 0.0022f

        private fun Int.withAlpha(alpha: Int): Int =
            (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

        private fun fs(value: Float): Float = base * style.fontScale * value

        private fun paint(
            size: Float,
            color: Int,
            bold: Boolean = false,
            medium: Boolean = false,
            condensed: Boolean = false,
            letterSpacing: Float = style.letterSpacing
        ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = when {
                condensed -> condensedBold
                bold -> sansBold
                medium -> sansMedium
                else -> sansRegular
            }
            this.letterSpacing = letterSpacing
            isSubpixelText = true
        }

        private fun Paint.lineHeight(): Float = fontMetrics.descent - fontMetrics.ascent
        private fun Paint.textWidth(value: String): Float = measureText(value)

        private fun drawText(canvas: Canvas, value: String, x: Float, baseline: Float, p: Paint) {
            if (value.isEmpty()) return
            if (style.shadow) {
                val shadow = Paint(p).apply { color = 0x55000000 }
                canvas.drawText(value, x, baseline + shadowOffset, shadow)
            }
            canvas.drawText(value, x, baseline, p)
        }

        private fun wrap(value: String, p: Paint, maxWidth: Float, maxLines: Int): List<String> {
            if (value.isEmpty()) return emptyList()
            val lines = ArrayList<String>()
            var rest = value
            while (rest.isNotEmpty() && lines.size < maxLines) {
                var count = p.breakText(rest, true, maxWidth, null)
                if (count <= 0) count = 1
                if (count < rest.length) {
                    val boundary = rest[count]
                    if (boundary.isLetterOrDigit()) {
                        val space = rest.lastIndexOf(' ', count)
                        if (space > 0 && count - space < 14) count = space
                    }
                }
                lines.add(rest.substring(0, count).trimEnd())
                rest = rest.substring(count).trimStart()
            }
            if (rest.isNotEmpty() && lines.isNotEmpty()) {
                var last = lines[lines.lastIndex]
                while (last.isNotEmpty() && p.textWidth("$last…") > maxWidth) {
                    last = last.substring(0, last.length - 1)
                }
                lines[lines.lastIndex] = "$last…"
            }
            return lines
        }

        private fun drawSegments(x: Float, baseline: Float, segments: List<Pair<String, Paint>>): Float {
            var cursor = x
            for ((value, p) in segments) {
                drawText(canvas, value, cursor, baseline, p)
                cursor += p.textWidth(value)
            }
            return cursor - x
        }

        private fun segmentsWidth(segments: List<Pair<String, Paint>>): Float =
            segments.sumOf { (value, p) -> p.textWidth(value).toDouble() }.toFloat()

        private fun accentColonTime(timePaint: Paint, colonPaint: Paint): List<Pair<String, Paint>> {
            val parts = text.time.split(":")
            val result = ArrayList<Pair<String, Paint>>()
            parts.forEachIndexed { index, part ->
                result += part to timePaint
                if (index < parts.lastIndex) result += ":" to colonPaint
            }
            return result
        }

        private fun drawRoundedRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int, r: Float = radius) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            canvas.drawRoundRect(left, top, right, bottom, r, r, p)
        }

        private fun drawSeal(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = size * 0.09f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawCircle(x + size / 2f, y + size / 2f, size * 0.46f, p)
            val path = Path().apply {
                moveTo(x + size * 0.30f, y + size * 0.52f)
                lineTo(x + size * 0.44f, y + size * 0.66f)
                lineTo(x + size * 0.70f, y + size * 0.36f)
            }
            canvas.drawPath(path, p)
        }

        private fun logoMetrics(scale: Float): Triple<Float, Float, Float> {
            val brand = text.brand.ifBlank { "迹印相机" }
            val brandPaint = paint(fs(0.024f) * scale, textColor, bold = true, letterSpacing = 0.02f)
            val chipPaint = paint(fs(0.015f) * scale, Color.WHITE, medium = true, letterSpacing = 0.02f)
            val codePaint = paint(fs(0.012f) * scale, subColor.withAlpha(160), letterSpacing = 0.04f)
            val mark = fs(0.028f) * scale
            val gap = fs(0.008f) * scale
            val chipPadX = fs(0.006f) * scale
            val chipH = fs(0.020f) * scale
            val chipW = chipPaint.textWidth("真实可验") + chipPadX * 2
            val line1H = brandPaint.lineHeight()
            val line2H = if (fields.showCode) max(chipH, codePaint.lineHeight()) else 0f
            val height = max(mark, line1H + fs(0.004f) * scale + line2H)
            val rightW = max(
                brandPaint.textWidth(brand),
                if (fields.showCode) chipW + fs(0.004f) * scale + codePaint.textWidth("防伪 ${text.code}") else 0f
            )
            return Triple(mark + gap + rightW, height, brandPaint.textSize)
        }

        private fun drawLogo(canvas: Canvas, right: Float, top: Float, scale: Float = 1f): Float {
            val brand = text.brand.ifBlank { "迹印相机" }
            val brandPaint = paint(fs(0.024f) * scale, textColor, bold = true, letterSpacing = 0.02f)
            val chipPaint = paint(fs(0.015f) * scale, Color.WHITE, medium = true, letterSpacing = 0.02f)
            val codePaint = paint(fs(0.012f) * scale, subColor.withAlpha(160), letterSpacing = 0.04f)
            val mark = fs(0.028f) * scale
            val gap = fs(0.008f) * scale
            val chipText = "真实可验"
            val chipPadX = fs(0.006f) * scale
            val chipH = fs(0.020f) * scale
            val chipW = chipPaint.textWidth(chipText) + chipPadX * 2
            val (totalW, logoH, _) = logoMetrics(scale)
            val left = right - totalW

            // Brand mark: accent rounded square with a location pin cut-out.
            drawRoundedRect(canvas, left, top, left + mark, top + mark, accent, mark * 0.26f)
            val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val cx = left + mark / 2f
            val pinTop = top + mark * 0.26f
            val pinR = mark * 0.17f
            canvas.drawCircle(cx, pinTop + pinR * 0.6f, pinR, pin)
            val tip = Path().apply {
                moveTo(cx - pinR * 0.85f, pinTop + pinR * 0.95f)
                lineTo(cx, pinTop + pinR * 2.35f)
                lineTo(cx + pinR * 0.85f, pinTop + pinR * 0.95f)
                close()
            }
            canvas.drawPath(tip, pin)
            val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            canvas.drawCircle(cx, pinTop + pinR * 0.6f, pinR * 0.38f, hole)

            val textX = left + mark + gap
            drawText(canvas, brand, textX, top + brandPaint.textSize * 0.9f, brandPaint)
            if (fields.showCode) {
                val chipTop = top + brandPaint.lineHeight() + fs(0.004f) * scale
                drawRoundedRect(canvas, textX, chipTop, textX + chipW, chipTop + chipH, accent, chipH * 0.24f)
                drawText(
                    canvas, chipText, textX + chipPadX,
                    chipTop + chipH * 0.5f + chipPaint.textSize * 0.36f, chipPaint
                )
                drawText(
                    canvas, "防伪 ${text.code}", textX + chipW + fs(0.004f) * scale,
                    chipTop + chipH * 0.5f + codePaint.textSize * 0.36f, codePaint
                )
            }
            return logoH
        }

        fun build(layout: TemplateLayout, maxWidth: Float): Block = when (layout) {
            TemplateLayout.COORDINATE_LIST -> buildCoordinateList(maxWidth)
            TemplateLayout.HERO_TIME -> buildHeroTime(maxWidth)
            TemplateLayout.BRAND_BADGE -> buildBrandBadge(maxWidth)
            TemplateLayout.CHECKIN_CARD -> buildCheckinCard(maxWidth)
            TemplateLayout.GOLD_DIVIDER -> buildGoldDivider(maxWidth)
            TemplateLayout.BOTTOM_PILL -> buildBottomPill(maxWidth)
        }

        // ---------- Layouts ----------

        private fun buildCoordinateList(maxWidth: Float): Block {
            val col = Column()
            val rowGap = fs(0.014f)

            if (fields.showHeader) {
                val headerPaint = paint(fs(0.070f), textColor.withAlpha(90), bold = true, letterSpacing = 0.14f).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = fs(0.0016f)
                }
                val header = "现场拍照"
                val h = headerPaint.lineHeight()
                col.add(h, headerPaint.textWidth(header)) { c, top ->
                    drawText(c, header, 0f, top + headerPaint.textSize * 0.92f, headerPaint)
                }
                col.gap(fs(0.028f))
            }

            val labelSize = fs(0.030f)
            val valueSize = fs(0.030f)
            val labelPaint = paint(labelSize, accent, medium = true, letterSpacing = 0.01f)
            val valuePaint = paint(valueSize, textColor, letterSpacing = 0.01f)
            val labelWidth = labelPaint.textWidth("地址：")
            val lineHeight = max(labelPaint.lineHeight(), valuePaint.lineHeight())
            val rowSpacing = fs(0.008f)

            fun addLabeledRow(label: String, value: String, maxLines: Int = 1) {
                if (value.isBlank()) return
                val available = maxWidth - labelWidth
                val lines = wrap(value, valuePaint, available, maxLines)
                if (lines.isEmpty()) return
                val height = lineHeight * lines.size + rowSpacing * (lines.size - 1)
                val width = labelWidth + (lines.maxOfOrNull { valuePaint.textWidth(it) } ?: 0f)
                col.add(height, min(width, maxWidth)) { c, top ->
                    var baseline = top + labelPaint.textSize * 0.98f
                    drawText(c, label, 0f, baseline, labelPaint)
                    drawText(c, lines[0], labelWidth, baseline, valuePaint)
                    for (i in 1 until lines.size) {
                        baseline += lineHeight + rowSpacing
                        drawText(c, lines[i], 0f, baseline, valuePaint)
                    }
                }
                col.gap(rowGap)
            }

            if (fields.showCoords && fields.showLatitude && text.latValue.isNotBlank()) {
                addLabeledRow("${text.latLabel}：", text.latValue)
            }
            if (fields.showCoords && fields.showLongitude && text.lngValue.isNotBlank()) {
                addLabeledRow("${text.lngLabel}：", text.lngValue)
            }
            if (fields.showAltitude && text.altitudeText.isNotBlank()) {
                addLabeledRow("海拔：", text.altitudeText.removePrefix("海拔 "))
            }
            if (fields.showAccuracy && text.accuracyText.isNotBlank()) {
                addLabeledRow("精度：", text.accuracyText.removePrefix("定位精度 "))
            }
            if (fields.showAddress) {
                addLabeledRow("地址：", text.address, maxLines = 3)
            }
            if (fields.showTime) {
                val value = buildString {
                    if (fields.showDate && text.date.isNotBlank()) {
                        append(text.date)
                        if (fields.showWeek && text.week.isNotBlank()) append(" ").append(text.week)
                        append("  ")
                    }
                    append(text.time)
                    if (fields.showWeather && text.weather.isNotBlank()) append("  ").append(text.weather)
                }
                addLabeledRow("${text.timeLabel}：", value)
            }
            if (fields.showDevice && text.device.isNotBlank()) {
                addLabeledRow("设备：", text.device)
            }
            if (fields.showNote) {
                addLabeledRow("${text.noteLabel}：", text.note, maxLines = 2)
            }

            if (fields.showLogo) {
                val (logoW, logoH, _) = logoMetrics(1f)
                col.gap(fs(0.01f))
                col.add(logoH, logoW) { c, top -> drawLogo(c, col.width, top) }
            }

            val contentHeight = col.height
            col.gap(0f)
            val padX = if (style.background == BackdropStyle.CARD) fs(0.03f) else 0f
            val padY = if (style.background == BackdropStyle.CARD) fs(0.026f) else 0f
            val width = min(col.width + padX * 2, maxWidth)
            val background: ((Canvas) -> Unit)? = if (style.background == BackdropStyle.CARD) {
                { c -> drawRoundedRect(c, 0f, 0f, width, contentHeight + padY * 2, cardColor.withAlpha(210)) }
            } else null
            return Block(
                width = width,
                height = contentHeight + padY * 2,
                background = background,
                content = { c ->
                    c.save()
                    if (padX > 0f || padY > 0f) c.translate(padX, padY)
                    col.content()(c)
                    c.restore()
                }
            )
        }

        private fun buildHeroTime(maxWidth: Float): Block {
            val col = Column()
            val timePaint = paint(fs(0.118f), textColor, condensed = true, letterSpacing = -0.01f)
            val colonPaint = paint(fs(0.118f), accent, condensed = true)
            val timeSegments = accentColonTime(timePaint, colonPaint)
            val timeWidth = segmentsWidth(timeSegments)
            val timeHeight = timePaint.lineHeight() * 0.86f
            col.add(timeHeight, timeWidth) { c, top ->
                val baseline = top + timePaint.textSize * 0.92f
                drawSegments(0f, baseline, timeSegments)
            }
            col.gap(fs(0.024f))

            val metaPaint = paint(fs(0.029f), textColor, medium = true, letterSpacing = 0.01f)
            val dotPaint = paint(fs(0.029f), accent, medium = true)
            val metaSegments = mutableListOf<Pair<String, Paint>>()
            if (fields.showDate && text.date.isNotBlank()) metaSegments += text.date to metaPaint
            if (fields.showWeek && text.week.isNotBlank()) {
                if (metaSegments.isNotEmpty()) metaSegments += "  " to metaPaint
                metaSegments += text.week to metaPaint
            }
            if (fields.showWeather && text.weather.isNotBlank()) {
                if (metaSegments.isNotEmpty()) metaSegments += "  " to dotPaint
                metaSegments += text.weather to metaPaint
            }
            if (!fields.showDate && fields.showTime) {
                val timeOnly = paint(fs(0.029f), textColor, medium = true)
                metaSegments.add(0, text.time to timeOnly)
            }
            if (metaSegments.isNotEmpty()) {
                val height = metaPaint.lineHeight()
                col.add(height, segmentsWidth(metaSegments)) { c, top ->
                    drawSegments(0f, top + metaPaint.textSize * 0.95f, metaSegments)
                }
                col.gap(fs(0.012f))
            }

            if (fields.showAddress) {
                val addressPaint = paint(fs(0.032f), textColor, medium = true, letterSpacing = 0.005f)
                val lines = wrap(text.address, addressPaint, maxWidth, 2)
                if (lines.isNotEmpty()) {
                    val lh = addressPaint.lineHeight() * 1.06f
                    col.add(lh * lines.size, lines.maxOf { addressPaint.textWidth(it) }) { c, top ->
                        var baseline = top + addressPaint.textSize * 0.95f
                        lines.forEach { line ->
                            drawText(c, line, 0f, baseline, addressPaint)
                            baseline += lh
                        }
                    }
                }
            }

            if (fields.showCoords && (text.latValue.isNotBlank() || text.lngValue.isNotBlank())) {
                col.gap(fs(0.012f))
                val coordPaint = paint(fs(0.023f), subColor, letterSpacing = 0.02f)
                val coordText = "纬度 ${text.latValue}   经度 ${text.lngValue}"
                col.add(coordPaint.lineHeight(), coordPaint.textWidth(coordText)) { c, top ->
                    drawText(c, coordText, 0f, top + coordPaint.textSize * 0.95f, coordPaint)
                }
            }

            if (fields.showNote && text.note.isNotBlank()) {
                col.gap(fs(0.012f))
                val notePaint = paint(fs(0.024f), subColor)
                val lines = wrap("备注：${text.note}", notePaint, maxWidth, 2)
                val lh = notePaint.lineHeight()
                col.add(lh * lines.size, maxWidth) { c, top ->
                    var baseline = top + notePaint.textSize * 0.95f
                    lines.forEach { line ->
                        drawText(c, line, 0f, baseline, notePaint)
                        baseline += lh
                    }
                }
            }

            if (fields.showLogo) {
                val (logoW, logoH, _) = logoMetrics(1f)
                col.gap(fs(0.02f))
                col.add(logoH, logoW) { c, top -> drawLogo(c, col.width, top) }
            }

            return Block(col.width, col.height, null, col.content())
        }

        private fun buildBrandBadge(maxWidth: Float): Block {
            val col = Column()
            val badgeText = text.brand.ifBlank { "品牌打卡" }
            val badgePaint = paint(fs(0.030f), Color.WHITE, bold = true, letterSpacing = 0.02f)
            val padX = fs(0.018f)
            val padY = fs(0.009f)
            val badgeW = badgePaint.textWidth(badgeText) + padX * 2
            val badgeH = badgePaint.lineHeight() + padY * 2
            val badgeRadius = fs(0.006f)
            col.add(badgeH, badgeW) { c, top ->
                drawRoundedRect(c, 0f, top, badgeW, top + badgeH, accent, badgeRadius)
                drawText(c, badgeText, padX, top + padY + badgePaint.textSize * 0.98f, badgePaint)
            }
            col.gap(fs(0.016f))

            val timePaint = paint(fs(0.062f), textColor, condensed = true, letterSpacing = -0.005f)
            val colonPaint = paint(fs(0.062f), accent, condensed = true)
            val timeSegments = accentColonTime(timePaint, colonPaint)
            val timeW = segmentsWidth(timeSegments)
            val timeH = timePaint.lineHeight() * 0.9f

            val datePaint = paint(fs(0.026f), textColor, medium = true)
            val subPaint = paint(fs(0.024f), subColor, letterSpacing = 0.01f)
            val rightW = max(datePaint.textWidth(text.date), subPaint.textWidth(
                listOf(text.week, text.weather).filter { it.isNotBlank() }.joinToString("  ")
            ))
            val dividerW = fs(0.004f)
            val gap = fs(0.02f)
            val rowW = timeW + gap + dividerW + gap + rightW
            val rowH = max(timeH, datePaint.lineHeight() + fs(0.008f) + subPaint.lineHeight())
            col.add(rowH, rowW) { c, top ->
                drawSegments(0f, top + timePaint.textSize * 0.9f, timeSegments)
                val barX = timeW + gap
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                c.drawRoundRect(barX, top + fs(0.004f), barX + dividerW, top + rowH - fs(0.002f), dividerW / 2, dividerW / 2, barPaint)
                val textX = barX + dividerW + gap
                drawText(c, text.date, textX, top + datePaint.textSize * 0.98f, datePaint)
                val subText = listOf(text.week, text.weather).filter { it.isNotBlank() }.joinToString("  ")
                drawText(
                    c, subText, textX,
                    top + datePaint.lineHeight() + fs(0.008f) + subPaint.textSize * 0.95f, subPaint
                )
            }
            col.gap(fs(0.018f))

            if (fields.showAddress) {
                val addressPaint = paint(fs(0.029f), textColor, medium = true, letterSpacing = 0.005f)
                val lines = wrap(text.address, addressPaint, maxWidth, 2)
                if (lines.isNotEmpty()) {
                    val lh = addressPaint.lineHeight() * 1.04f
                    col.add(lh * lines.size, lines.maxOf { addressPaint.textWidth(it) }) { c, top ->
                        var baseline = top + addressPaint.textSize * 0.95f
                        lines.forEach {
                            drawText(c, it, 0f, baseline, addressPaint)
                            baseline += lh
                        }
                    }
                }
            }

            if (fields.showNote && text.note.isNotBlank()) {
                col.gap(fs(0.01f))
                val notePaint = paint(fs(0.022f), subColor)
                col.add(notePaint.lineHeight(), notePaint.textWidth(text.note)) { c, top ->
                    drawText(c, "备注：${text.note}", 0f, top + notePaint.textSize * 0.95f, notePaint)
                }
            }

            if (fields.showLogo) {
                val (logoW, logoH, _) = logoMetrics(1f)
                col.gap(fs(0.016f))
                col.add(logoH, logoW) { c, top -> drawLogo(c, col.width, top) }
            }

            return Block(col.width, col.height, null, col.content())
        }

        private fun buildCheckinCard(maxWidth: Float): Block {
            val col = Column()
            val showHeader = fields.showHeader
            val title = "打卡记录"
            val titlePaint = paint(fs(0.023f), Color.WHITE, medium = true, letterSpacing = 0.08f)
            val timePaint = paint(fs(0.056f), cardTextColor, condensed = true, letterSpacing = -0.005f)
            val timeW = timePaint.textWidth(text.time)
            val padX = fs(0.024f)
            val padY = fs(0.012f)
            val titleW = titlePaint.textWidth(title)
            val headerH = if (showHeader) titlePaint.lineHeight() + padY * 2f else 0f
            val cardBodyH = timePaint.lineHeight() * 1.12f + padY * 2f
            val cardW = max(timeW + padX * 2f, if (showHeader) titleW + padX * 2f + fs(0.03f) else 0f)

            val header: (Canvas, Float) -> Unit = { c, top ->
                drawRoundedRect(c, 0f, top, cardW, top + headerH + cardBodyH, cardColor, radius)
                if (showHeader) {
                    val tab = Path().apply {
                        addRoundRect(
                            RectF(0f, top, cardW, top + headerH),
                            floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
                            Path.Direction.CW
                        )
                    }
                    c.drawPath(tab, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
                    drawText(c, title, padX, top + padY + titlePaint.textSize * 0.98f, titlePaint)
                }
                drawText(
                    c, text.time, padX,
                    top + headerH + padY + timePaint.textSize * 1.0f, timePaint
                )
            }
            col.add(headerH + cardBodyH, cardW, header)
            col.gap(fs(0.022f))

            val datePaint = paint(fs(0.024f), subColor, medium = true, letterSpacing = 0.03f)
            val dateLine = listOf(text.date, text.week).filter { it.isNotBlank() }.joinToString("  ")
            if (fields.showDate || fields.showWeek) {
                col.add(datePaint.lineHeight(), datePaint.textWidth(dateLine)) { c, top ->
                    drawText(c, dateLine, 0f, top + datePaint.textSize * 0.95f, datePaint)
                }
                col.gap(fs(0.008f))
            }

            if (fields.showAddress) {
                val addressPaint = paint(fs(0.029f), textColor, medium = true, letterSpacing = 0.005f)
                val lines = wrap(text.address, addressPaint, maxWidth, 2)
                if (lines.isNotEmpty()) {
                    val lh = addressPaint.lineHeight() * 1.05f
                    col.add(lh * lines.size, lines.maxOf { addressPaint.textWidth(it) }) { c, top ->
                        var baseline = top + addressPaint.textSize * 0.95f
                        lines.forEach {
                            drawText(c, it, 0f, baseline, addressPaint)
                            baseline += lh
                        }
                    }
                    col.gap(fs(0.012f))
                }
            }

            if (fields.showCoords && text.latValue.isNotBlank()) {
                val coordPaint = paint(fs(0.022f), subColor, letterSpacing = 0.02f)
                val value = "纬度 ${text.latValue}   经度 ${text.lngValue}"
                col.add(coordPaint.lineHeight(), coordPaint.textWidth(value)) { c, top ->
                    drawText(c, value, 0f, top + coordPaint.textSize * 0.95f, coordPaint)
                }
                col.gap(fs(0.008f))
            }

            if (fields.showNote && text.note.isNotBlank()) {
                val notePaint = paint(fs(0.022f), subColor)
                val lines = wrap("备注：${text.note}", notePaint, maxWidth, 2)
                val lh = notePaint.lineHeight()
                col.add(lh * lines.size, maxWidth) { c, top ->
                    var baseline = top + notePaint.textSize * 0.95f
                    lines.forEach {
                        drawText(c, it, 0f, baseline, notePaint)
                        baseline += lh
                    }
                }
                col.gap(fs(0.008f))
            }

            if (fields.showSeal) {
                val footerPaint = paint(fs(0.019f), subColor, letterSpacing = 0.03f)
                val sealSize = footerPaint.lineHeight() * 0.9f
                val footerText = "${text.brand}已确保时间真实不可篡改"
                val footerW = sealSize + fs(0.006f) + footerPaint.textWidth(footerText)
                col.add(sealSize, footerW) { c, top ->
                    drawSeal(c, 0f, top, sealSize, subColor)
                    drawText(
                        c, footerText, sealSize + fs(0.006f),
                        top + sealSize * 0.5f + footerPaint.textSize * 0.36f, footerPaint
                    )
                }
            }

            if (fields.showLogo) {
                val (logoW, logoH, _) = logoMetrics(0.9f)
                col.gap(fs(0.012f))
                col.add(logoH, logoW) { c, top -> drawLogo(c, col.width, top, 0.9f) }
            }

            return Block(col.width, col.height, null, col.content())
        }

        private fun buildGoldDivider(maxWidth: Float): Block {
            val col = Column()
            val timePaint = paint(fs(0.075f), textColor, condensed = true, letterSpacing = 0f)
            val colonPaint = paint(fs(0.075f), textColor, condensed = true)
            val timeSegments = accentColonTime(timePaint, colonPaint)
            val timeW = segmentsWidth(timeSegments)
            val timeH = timePaint.lineHeight() * 0.9f

            val datePaint = paint(fs(0.027f), textColor, medium = true)
            val subPaint = paint(fs(0.026f), subColor, letterSpacing = 0.01f)
            val rightText = listOf(text.week, text.weather).filter { it.isNotBlank() }.joinToString("  ")
            val rightW = max(datePaint.textWidth(text.date), subPaint.textWidth(rightText))
            val barW = fs(0.0045f)
            val gap = fs(0.022f)
            val rowW = timeW + gap + barW + gap + rightW
            val rightH = datePaint.lineHeight() + fs(0.006f) + subPaint.lineHeight()
            val rowH = max(timeH, rightH)
            col.add(rowH, rowW) { c, top ->
                drawSegments(0f, top + timePaint.textSize * 0.9f, timeSegments)
                val barX = timeW + gap
                val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                c.drawRoundRect(
                    barX, top + fs(0.004f), barX + barW, top + rowH - fs(0.004f),
                    barW / 2, barW / 2, bar
                )
                val textX = barX + barW + gap
                val startY = top + (rowH - rightH) / 2f
                drawText(c, text.date, textX, startY + datePaint.textSize * 0.98f, datePaint)
                drawText(
                    c, rightText, textX,
                    startY + datePaint.lineHeight() + fs(0.006f) + subPaint.textSize * 0.95f, subPaint
                )
            }
            col.gap(fs(0.02f))

            if (fields.showAddress) {
                val addressPaint = paint(fs(0.030f), textColor, medium = true, letterSpacing = 0.005f)
                val lines = wrap(text.address, addressPaint, maxWidth, 2)
                if (lines.isNotEmpty()) {
                    val lh = addressPaint.lineHeight() * 1.05f
                    col.add(lh * lines.size, lines.maxOf { addressPaint.textWidth(it) }) { c, top ->
                        var baseline = top + addressPaint.textSize * 0.95f
                        lines.forEach {
                            drawText(c, it, 0f, baseline, addressPaint)
                            baseline += lh
                        }
                    }
                }
            }

            if (fields.showNote && text.note.isNotBlank()) {
                col.gap(fs(0.01f))
                val notePaint = paint(fs(0.022f), subColor)
                col.add(notePaint.lineHeight(), notePaint.textWidth(text.note)) { c, top ->
                    drawText(c, "备注：${text.note}", 0f, top + notePaint.textSize * 0.95f, notePaint)
                }
            }

            if (fields.showLogo) {
                val (logoW, logoH, _) = logoMetrics(1f)
                col.gap(fs(0.016f))
                col.add(logoH, logoW) { c, top -> drawLogo(c, col.width, top) }
            }

            return Block(col.width, col.height, null, col.content())
        }

        private fun buildBottomPill(maxWidth: Float): Block {
            val col = Column()
            val labelPaint = paint(fs(0.028f), accent, bold = true, letterSpacing = 0.04f)
            val timePaint = paint(fs(0.040f), cardTextColor, condensed = true, letterSpacing = 0f)
            val pillPadX = fs(0.020f)
            val pillPadY = fs(0.010f)
            val pillH = max(labelPaint.lineHeight(), timePaint.lineHeight()) + pillPadY * 2
            val pillW = labelPaint.textWidth("打卡") + fs(0.01f) + timePaint.textWidth(text.time) + pillPadX * 2
            col.add(pillH, pillW) { c, top ->
                drawRoundedRect(c, 0f, top, pillW, top + pillH, cardColor, pillH / 2f)
                val baseline = top + pillH / 2f + labelPaint.textSize * 0.36f
                drawText(c, "打卡", pillPadX, baseline, labelPaint)
                drawText(c, text.time, pillPadX + labelPaint.textWidth("打卡") + fs(0.01f), baseline, timePaint)
            }
            col.gap(fs(0.016f))

            val barW = fs(0.005f)
            val textIndent = barW + fs(0.014f)
            val addressPaint = paint(fs(0.028f), textColor, medium = true, letterSpacing = 0.005f)
            val datePaint = paint(fs(0.023f), subColor, letterSpacing = 0.02f)
            val addressLines = if (fields.showAddress) wrap(text.address, addressPaint, maxWidth - textIndent, 2) else emptyList()
            val dateLine = listOf(text.date, text.week).filter { it.isNotBlank() }.joinToString("  ")
            val lh = addressPaint.lineHeight() * 1.05f
            val infoGap = if (addressLines.isNotEmpty() && dateLine.isNotBlank()) fs(0.008f) else 0f
            val blockH = addressLines.size * lh + infoGap + if (dateLine.isNotBlank()) datePaint.lineHeight() else 0f
            if (addressLines.isNotEmpty() || dateLine.isNotBlank()) {
                col.add(blockH, maxWidth) { c, top ->
                    val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                    c.drawRoundRect(
                        0f, top + blockH * 0.06f, barW, top + blockH * 0.88f,
                        barW / 2, barW / 2, bar
                    )
                    var baseline = top + addressPaint.textSize * 0.95f
                    addressLines.forEach {
                        drawText(c, it, textIndent, baseline, addressPaint)
                        baseline += lh
                    }
                    if (dateLine.isNotBlank()) {
                        drawText(
                            c, dateLine, textIndent,
                            top + addressLines.size * lh + infoGap + datePaint.textSize * 0.95f,
                            datePaint
                        )
                    }
                }
            }

            if (fields.showSeal) {
                col.gap(fs(0.014f))
                val footerPaint = paint(fs(0.018f), subColor, letterSpacing = 0.03f)
                val sealSize = footerPaint.lineHeight() * 0.9f
                val textValue = "${text.brand}已验证｜时间地点真实"
                col.add(sealSize, sealSize + fs(0.006f) + footerPaint.textWidth(textValue)) { c, top ->
                    drawSeal(c, 0f, top, sealSize, subColor)
                    drawText(
                        c, textValue, sealSize + fs(0.006f),
                        top + sealSize * 0.5f + footerPaint.textSize * 0.36f, footerPaint
                    )
                }
            }

            if (fields.showLogo) {
                val (logoW, logoH, _) = logoMetrics(0.85f)
                col.gap(fs(0.012f))
                col.add(logoH, logoW) { c, top -> drawLogo(c, col.width, top, 0.85f) }
            }

            return Block(col.width, col.height, null, col.content())
        }
    }
}
