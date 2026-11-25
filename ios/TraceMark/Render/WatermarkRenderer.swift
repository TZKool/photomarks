import UIKit

/// Single CoreGraphics renderer used by both the live preview and the full-resolution export.
/// All metrics derive from the image short side, so both stay visually identical.
enum WatermarkRenderer {
    static func draw(
        in ctx: CGContext,
        canvasSize: CGSize,
        template: WatermarkTemplate,
        text: FormattedWatermark,
        transform: WatermarkTransform
    ) {
        guard canvasSize.width > 8, canvasSize.height > 8 else { return }
        let base = min(canvasSize.width, canvasSize.height)
        let painter = Painter(
            base: base,
            style: template.style,
            fields: template.fields,
            text: text,
            layout: template.style.layout
        )
        let block = painter.build(maxWidth: canvasSize.width - painter.margin * 2)
        guard block.width > 0, block.height > 0 else { return }

        let scale = clamp(transform.scale, 0.4, 2.5)
        let scaledW = block.width * scale
        let scaledH = block.height * scale
        let margin = painter.margin

        var x = margin
        switch template.style.anchor {
        case .topLeft, .centerLeft, .bottomLeft:
            x = margin
        case .topCenter, .center, .bottomCenter:
            x = (canvasSize.width - scaledW) / 2
        case .topRight, .centerRight, .bottomRight:
            x = canvasSize.width - margin - scaledW
        }
        var y = margin
        switch template.style.anchor {
        case .topLeft, .topCenter, .topRight:
            y = margin
        case .centerLeft, .center, .centerRight:
            y = (canvasSize.height - scaledH) / 2
        case .bottomLeft, .bottomCenter, .bottomRight:
            y = canvasSize.height - margin - scaledH
        }
        x += transform.offsetX * canvasSize.width
        y += transform.offsetY * canvasSize.height

        let opacity = clamp(template.style.opacity, 0, 1)

        if template.style.background == .scrim {
            drawScrim(ctx, size: canvasSize)
        }

        ctx.saveGState()
        ctx.setAlpha(opacity)

        let centerX = x + scaledW / 2
        let centerY = y + scaledH / 2
        if abs(transform.rotation) > 0.001 {
            ctx.translateBy(x: centerX, y: centerY)
            ctx.rotate(by: transform.rotation * .pi / 180)
            ctx.translateBy(x: -centerX, y: -centerY)
        }
        if abs(scale - 1) > 0.001 {
            ctx.translateBy(x: centerX, y: centerY)
            ctx.scaleBy(x: scale, y: scale)
            ctx.translateBy(x: -centerX, y: -centerY)
        }

        if template.style.background == .card,
           template.style.layout != .coordinateList,
           template.style.layout != .checkinCard {
            let pad = base * 0.022
            let card = UIColor(argb: template.style.cardColor).withAlphaByte(216)
            let radius = base * clamp(template.style.cornerRatio, 0.002, 0.08)
            ctx.setFillColor(card.cgColor)
            ctx.addPath(UIBezierPath(
                roundedRect: CGRect(
                    x: x - pad, y: y - pad,
                    width: block.width + pad * 2, height: block.height + pad * 2
                ),
                cornerRadius: radius
            ).cgPath)
            ctx.fillPath()
        }

        block.draw(ctx, left: x, top: y)
        ctx.restoreGState()
    }

    private static func drawScrim(_ ctx: CGContext, size: CGSize) {
        let colors = [UIColor.clear.cgColor, UIColor.black.withAlphaComponent(0.6).cgColor] as CFArray
        let space = CGColorSpaceCreateDeviceRGB()
        guard let gradient = CGGradient(colorsSpace: space, colors: colors, locations: [0, 1]) else { return }
        ctx.saveGState()
        ctx.clip(to: CGRect(x: 0, y: size.height * 0.52, width: size.width, height: size.height * 0.48))
        ctx.drawLinearGradient(
            gradient,
            start: CGPoint(x: 0, y: size.height * 0.52),
            end: CGPoint(x: 0, y: size.height),
            options: []
        )
        ctx.restoreGState()
    }

    private struct Block {
        let width: CGFloat
        let height: CGFloat
        let background: ((CGContext) -> Void)?
        let content: (CGContext) -> Void

        func draw(_ ctx: CGContext, left: CGFloat, top: CGFloat) {
            ctx.saveGState()
            ctx.translateBy(x: left, y: top)
            background?(ctx)
            content(ctx)
            ctx.restoreGState()
        }
    }

    private final class Column {
        private var draws: [(CGContext) -> Void] = []
        private(set) var width: CGFloat = 0
        private(set) var height: CGFloat = 0

        func add(height: CGFloat, width: CGFloat, draw: @escaping (CGContext, CGFloat) -> Void) {
            let top = height
            draws.append { ctx in draw(ctx, top) }
            self.height += height
            self.width = max(self.width, width)
        }

        func gap(_ extra: CGFloat) {
            if !draws.isEmpty { height += extra }
        }

        func content() -> (CGContext) -> Void {
            { ctx in self.draws.forEach { $0(ctx) } }
        }
    }

    private struct TextStyle {
        let font: UIFont
        let attributes: [NSAttributedString.Key: Any]

        init(font: UIFont, color: UIColor, kern: CGFloat, shadow: Bool, shadowOffset: CGFloat) {
            self.font = font
            var attrs: [NSAttributedString.Key: Any] = [
                .font: font,
                .foregroundColor: color,
            ]
            if abs(kern) > 0.001 {
                attrs[.kern] = kern
            }
            if shadow {
                let shadowValue = NSShadow()
                shadowValue.shadowColor = UIColor.black.withAlphaComponent(0.38)
                shadowValue.shadowOffset = CGSize(width: 0, height: shadowOffset)
                shadowValue.shadowBlurRadius = shadowOffset * 0.8
                attrs[.shadow] = shadowValue
            }
            attributes = attrs
        }

        var lineHeight: CGFloat { font.lineHeight }

        func width(_ text: String) -> CGFloat {
            (text as NSString).size(withAttributes: attributes).width
        }
    }

    private final class Painter {
        let base: CGFloat
        let style: TemplateStyle
        let fields: FieldConfig
        let text: FormattedWatermark
        let layout: TemplateLayout
        let margin: CGFloat

        init(base: CGFloat, style: TemplateStyle, fields: FieldConfig, text: FormattedWatermark, layout: TemplateLayout) {
            self.base = base
            self.style = style
            self.fields = fields
            self.text = text
            self.layout = layout
            margin = base * clamp(style.marginRatio, 0.02, 0.12)
        }

        private var accent: UIColor { UIColor(argb: style.accentColor) }
        private var onCard: Bool {
            style.background == .card && layout != .checkinCard
        }
        private var textColor: UIColor {
            onCard ? UIColor(argb: style.cardTextColor) : UIColor(argb: style.textColor)
        }
        private var subColor: UIColor {
            onCard
                ? UIColor(argb: style.cardTextColor).withAlphaByte(150)
                : UIColor(argb: style.subTextColor)
        }
        private var cardColor: UIColor { UIColor(argb: style.cardColor) }
        private var cardTextColor: UIColor { UIColor(argb: style.cardTextColor) }
        private var radius: CGFloat { base * clamp(style.cornerRatio, 0.002, 0.08) }

        private func fs(_ value: CGFloat) -> CGFloat {
            base * style.fontScale * value
        }

        private func paint(
            size: CGFloat,
            color: UIColor,
            bold: Bool = false,
            medium: Bool = false,
            condensed: Bool = false,
            letterSpacing: CGFloat? = nil
        ) -> TextStyle {
            let fontSize = max(1, fs(size))
            let font: UIFont
            if condensed, let condensedFont = UIFont(name: "HelveticaNeue-CondensedBold", size: fontSize) {
                font = condensedFont
            } else if bold {
                font = .systemFont(ofSize: fontSize, weight: .bold)
            } else if medium {
                font = .systemFont(ofSize: fontSize, weight: .medium)
            } else {
                font = .systemFont(ofSize: fontSize)
            }
            let kern = (letterSpacing ?? style.letterSpacing) * fontSize
            return TextStyle(
                font: font,
                color: color,
                kern: kern,
                shadow: style.shadow,
                shadowOffset: base * 0.0022
            )
        }

        private func drawText(_ value: String, x: CGFloat, baseline: CGFloat, style textStyle: TextStyle, ctx: CGContext) {
            guard !value.isEmpty else { return }
            (value as NSString).draw(
                at: CGPoint(x: x, y: baseline - textStyle.font.ascender),
                withAttributes: textStyle.attributes
            )
        }

        private func roundedRect(
            _ ctx: CGContext,
            x: CGFloat,
            y: CGFloat,
            width: CGFloat,
            height: CGFloat,
            color: UIColor,
            radius customRadius: CGFloat? = nil
        ) {
            let r = customRadius ?? radius
            ctx.setFillColor(color.cgColor)
            ctx.addPath(UIBezierPath(
                roundedRect: CGRect(x: x, y: y, width: width, height: height),
                cornerRadius: r
            ).cgPath)
            ctx.fillPath()
        }

        private func drawSeal(_ ctx: CGContext, x: CGFloat, y: CGFloat, size: CGFloat, color: UIColor) {
            ctx.saveGState()
            ctx.setStrokeColor(color.cgColor)
            ctx.setLineWidth(size * 0.09)
            ctx.setLineCap(.round)
            ctx.strokeEllipse(in: CGRect(
                x: x + size * 0.04, y: y + size * 0.04,
                width: size * 0.92, height: size * 0.92
            ))
            ctx.beginPath()
            ctx.move(to: CGPoint(x: x + size * 0.30, y: y + size * 0.52))
            ctx.addLine(to: CGPoint(x: x + size * 0.44, y: y + size * 0.66))
            ctx.addLine(to: CGPoint(x: x + size * 0.70, y: y + size * 0.36))
            ctx.strokePath()
            ctx.restoreGState()
        }

        private func wrap(_ value: String, style textStyle: TextStyle, maxWidth: CGFloat, maxLines: Int) -> [String] {
            guard !value.isEmpty else { return [] }
            let ns = value as NSString
            var start = 0
            var lines: [String] = []

            while start < ns.length && lines.count < maxLines {
                let rest = ns.substring(from: start) as NSString
                var count = breakText(rest, style: textStyle, maxWidth: maxWidth)
                if count <= 0 { count = 1 }
                if count < rest.length {
                    let boundary = rest.character(at: count - 1)
                    if isLatin(boundary) {
                        let range = rest.range(of: " ", options: [], range: NSRange(location: 0, length: count))
                        if range.location != NSNotFound && count - range.location < 14 {
                            count = range.location + 1
                        }
                    }
                }
                let line = rest.substring(with: NSRange(location: 0, length: count))
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                lines.append(line)
                start += count
                while start < ns.length && ns.substring(with: NSRange(location: start, length: 1)) == " " {
                    start += 1
                }
            }

            if start < ns.length, !lines.isEmpty {
                var last = lines.removeLast()
                while !last.isEmpty && textStyle.width(last + "…") > maxWidth {
                    last.removeLast()
                }
                lines.append(last + "…")
            }
            return lines
        }

        private func breakText(_ value: NSString, style textStyle: TextStyle, maxWidth: CGFloat) -> Int {
            var low = 1
            var high = value.length
            var best = 0
            while low <= high {
                let mid = (low + high) / 2
                let candidate = value.substring(with: NSRange(location: 0, length: mid))
                if textStyle.width(candidate) <= maxWidth {
                    best = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return best
        }

        private func isLatin(_ character: unichar) -> Bool {
            (character >= 48 && character <= 57) ||
                (character >= 65 && character <= 90) ||
                (character >= 97 && character <= 122)
        }

        private func accentColonTime(_ timeStyle: TextStyle, _ colonStyle: TextStyle) -> [(String, TextStyle)] {
            let parts = text.time.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
            var result: [(String, TextStyle)] = []
            for (index, part) in parts.enumerated() {
                result.append((part, timeStyle))
                if index < parts.count - 1 {
                    result.append((":", colonStyle))
                }
            }
            return result
        }

        private func segmentsWidth(_ segments: [(String, TextStyle)]) -> CGFloat {
            segments.reduce(0) { $0 + $1.1.width($1.0) }
        }

        @discardableResult
        private func drawSegments(_ ctx: CGContext, x: CGFloat, baseline: CGFloat, segments: [(String, TextStyle)]) -> CGFloat {
            var cursor = x
            for (value, textStyle) in segments {
                drawText(value, x: cursor, baseline: baseline, style: textStyle, ctx: ctx)
                cursor += textStyle.width(value)
            }
            return cursor - x
        }

        private func logoMetrics(_ scale: CGFloat) -> (width: CGFloat, height: CGFloat) {
            let brand = text.brand.isBlank ? "迹印相机" : text.brand
            let brandStyle = paint(size: 0.024 * scale, color: textColor, bold: true, letterSpacing: 0.02)
            let chipStyle = paint(size: 0.015 * scale, color: .white, medium: true, letterSpacing: 0.02)
            let codeStyle = paint(
                size: 0.012 * scale,
                color: subColor.withAlphaByte(160),
                letterSpacing: 0.04
            )
            let mark = fs(0.028) * scale
            let gap = fs(0.008) * scale
            let chipPadX = fs(0.006) * scale
            let chipH = fs(0.020) * scale
            let chipW = chipStyle.width("真实可验") + chipPadX * 2
            let line1H = brandStyle.lineHeight
            let line2H = fields.showCode ? max(chipH, codeStyle.lineHeight) : 0
            let height = max(mark, line1H + fs(0.004) * scale + line2H)
            let rightW = max(
                brandStyle.width(brand),
                fields.showCode ? chipW + fs(0.004) * scale + codeStyle.width("防伪 \(text.code)") : 0
            )
            return (mark + gap + rightW, height)
        }

        @discardableResult
        private func drawLogo(_ ctx: CGContext, right: CGFloat, top: CGFloat, scale: CGFloat = 1) -> CGFloat {
            let brand = text.brand.isBlank ? "迹印相机" : text.brand
            let brandStyle = paint(size: 0.024 * scale, color: textColor, bold: true, letterSpacing: 0.02)
            let chipStyle = paint(size: 0.015 * scale, color: .white, medium: true, letterSpacing: 0.02)
            let codeStyle = paint(
                size: 0.012 * scale,
                color: subColor.withAlphaByte(160),
                letterSpacing: 0.04
            )
            let mark = fs(0.028) * scale
            let gap = fs(0.008) * scale
            let chipText = "真实可验"
            let chipPadX = fs(0.006) * scale
            let chipH = fs(0.020) * scale
            let chipW = chipStyle.width(chipText) + chipPadX * 2
            let metrics = logoMetrics(scale)
            let left = right - metrics.width

            roundedRect(ctx, x: left, y: top, width: mark, height: mark, color: accent, radius: mark * 0.26)

            let cx = left + mark / 2
            let pinTop = top + mark * 0.26
            let pinR = mark * 0.17
            ctx.setFillColor(UIColor.white.cgColor)
            ctx.fillEllipse(in: CGRect(
                x: cx - pinR, y: pinTop + pinR * 0.6 - pinR,
                width: pinR * 2, height: pinR * 2
            ))
            let tip = UIBezierPath()
            tip.move(to: CGPoint(x: cx - pinR * 0.85, y: pinTop + pinR * 0.95))
            tip.addLine(to: CGPoint(x: cx, y: pinTop + pinR * 2.35))
            tip.addLine(to: CGPoint(x: cx + pinR * 0.85, y: pinTop + pinR * 0.95))
            tip.close()
            ctx.addPath(tip.cgPath)
            ctx.fillPath()
            ctx.setFillColor(accent.cgColor)
            ctx.fillEllipse(in: CGRect(
                x: cx - pinR * 0.38, y: pinTop + pinR * 0.6 - pinR * 0.38,
                width: pinR * 0.76, height: pinR * 0.76
            ))

            let textX = left + mark + gap
            drawText(brand, x: textX, baseline: top + brandStyle.font.pointSize * 0.9, style: brandStyle, ctx: ctx)
            if fields.showCode {
                let chipTop = top + brandStyle.lineHeight + fs(0.004) * scale
                roundedRect(
                    ctx, x: textX, y: chipTop, width: chipW, height: chipH,
                    color: accent, radius: chipH * 0.24
                )
                drawText(
                    chipText, x: textX + chipPadX,
                    baseline: chipTop + chipH * 0.5 + chipStyle.font.pointSize * 0.36,
                    style: chipStyle, ctx: ctx
                )
                drawText(
                    "防伪 \(text.code)",
                    x: textX + chipW + fs(0.004) * scale,
                    baseline: chipTop + chipH * 0.5 + codeStyle.font.pointSize * 0.36,
                    style: codeStyle, ctx: ctx
                )
            }
            return metrics.height
        }

        func build(maxWidth: CGFloat) -> Block {
            switch layout {
            case .coordinateList: return buildCoordinateList(maxWidth: maxWidth)
            case .heroTime: return buildHeroTime(maxWidth: maxWidth)
            case .brandBadge: return buildBrandBadge(maxWidth: maxWidth)
            case .checkinCard: return buildCheckinCard(maxWidth: maxWidth)
            case .goldDivider: return buildGoldDivider(maxWidth: maxWidth)
            case .bottomPill: return buildBottomPill(maxWidth: maxWidth)
            }
        }

        // MARK: - Layouts

        private func buildCoordinateList(maxWidth: CGFloat) -> Block {
            let col = Column()
            let rowGap = fs(0.014)

            if fields.showHeader {
                let headerStyle = paint(
                    size: 0.070,
                    color: textColor.withAlphaByte(90),
                    bold: true,
                    letterSpacing: 0.14
                )
                let header = "现场拍照"
                col.add(height: headerStyle.lineHeight, width: headerStyle.width(header)) { ctx, top in
                    drawText(
                        header, x: 0,
                        baseline: top + headerStyle.font.pointSize * 0.92,
                        style: headerStyle, ctx: ctx
                    )
                }
                col.gap(fs(0.028))
            }

            let labelStyle = paint(size: 0.030, color: accent, medium: true, letterSpacing: 0.01)
            let valueStyle = paint(size: 0.030, color: textColor, letterSpacing: 0.01)
            let labelWidth = labelStyle.width("地址：")
            let lineHeight = max(labelStyle.lineHeight, valueStyle.lineHeight)
            let rowSpacing = fs(0.008)

            func addLabeledRow(_ label: String, _ value: String, maxLines: Int = 1) {
                guard !value.isBlank else { return }
                let available = maxWidth - labelWidth
                let lines = wrap(value, style: valueStyle, maxWidth: available, maxLines: maxLines)
                guard !lines.isEmpty else { return }
                let height = lineHeight * CGFloat(lines.count) + rowSpacing * CGFloat(max(0, lines.count - 1))
                let width = labelWidth + (lines.map { valueStyle.width($0) }.max() ?? 0)
                col.add(height: height, width: min(width, maxWidth)) { ctx, top in
                    var baseline = top + labelStyle.font.pointSize * 0.98
                    drawText(label, x: 0, baseline: baseline, style: labelStyle, ctx: ctx)
                    drawText(lines[0], x: labelWidth, baseline: baseline, style: valueStyle, ctx: ctx)
                    if lines.count > 1 {
                        for index in 1..<lines.count {
                            baseline += lineHeight + rowSpacing
                            drawText(lines[index], x: 0, baseline: baseline, style: valueStyle, ctx: ctx)
                        }
                    }
                }
                col.gap(rowGap)
            }

            if fields.showCoords, fields.showLatitude, !text.latValue.isBlank {
                addLabeledRow("\(text.latLabel)：", text.latValue)
            }
            if fields.showCoords, fields.showLongitude, !text.lngValue.isBlank {
                addLabeledRow("\(text.lngLabel)：", text.lngValue)
            }
            if fields.showAltitude, !text.altitudeText.isBlank {
                addLabeledRow("海拔：", text.altitudeText.replacingOccurrences(of: "海拔 ", with: ""))
            }
            if fields.showAccuracy, !text.accuracyText.isBlank {
                addLabeledRow("精度：", text.accuracyText.replacingOccurrences(of: "定位精度 ", with: ""))
            }
            if fields.showAddress {
                addLabeledRow("地址：", text.address, maxLines: 3)
            }
            if fields.showTime {
                var value = ""
                if fields.showDate, !text.date.isBlank {
                    value += text.date
                    if fields.showWeek, !text.week.isBlank { value += " \(text.week)" }
                    value += "  "
                }
                value += text.time
                if fields.showWeather, !text.weather.isBlank { value += "  \(text.weather)" }
                addLabeledRow("\(text.timeLabel)：", value)
            }
            if fields.showDevice, !text.device.isBlank {
                addLabeledRow("设备：", text.device)
            }
            if fields.showNote {
                addLabeledRow("\(text.noteLabel)：", text.note, maxLines: 2)
            }

            if fields.showLogo {
                let metrics = logoMetrics(1)
                col.gap(fs(0.01))
                col.add(height: metrics.height, width: metrics.width) { ctx, top in
                    drawLogo(ctx, right: col.width, top: top)
                }
            }

            let contentHeight = col.height
            let padX = style.background == .card ? fs(0.03) : 0
            let padY = style.background == .card ? fs(0.026) : 0
            let width = min(col.width + padX * 2, maxWidth)
            let background: ((CGContext) -> Void)? = style.background == .card ? { ctx in
                roundedRect(
                    ctx, x: 0, y: 0,
                    width: width, height: contentHeight + padY * 2,
                    color: cardColor.withAlphaByte(210)
                )
            } : nil
            return Block(
                width: width,
                height: contentHeight + padY * 2,
                background: background,
                content: { ctx in
                    ctx.saveGState()
                    if padX > 0 || padY > 0 {
                        ctx.translateBy(x: padX, y: padY)
                    }
                    col.content()(ctx)
                    ctx.restoreGState()
                }
            )
        }

        private func buildHeroTime(maxWidth: CGFloat) -> Block {
            let col = Column()
            let timeStyle = paint(size: 0.118, color: textColor, condensed: true, letterSpacing: -0.01)
            let colonStyle = paint(size: 0.118, color: accent, condensed: true)
            let timeSegments = accentColonTime(timeStyle, colonStyle)
            let timeHeight = timeStyle.lineHeight * 0.86
            col.add(height: timeHeight, width: segmentsWidth(timeSegments)) { ctx, top in
                drawSegments(ctx, x: 0, baseline: top + timeStyle.font.pointSize * 0.92, segments: timeSegments)
            }
            col.gap(fs(0.024))

            let metaStyle = paint(size: 0.029, color: textColor, medium: true, letterSpacing: 0.01)
            let dotStyle = paint(size: 0.029, color: accent, medium: true)
            var metaSegments: [(String, TextStyle)] = []
            if fields.showDate, !text.date.isBlank {
                metaSegments.append((text.date, metaStyle))
            }
            if fields.showWeek, !text.week.isBlank {
                if !metaSegments.isEmpty { metaSegments.append(("  ", metaStyle)) }
                metaSegments.append((text.week, metaStyle))
            }
            if fields.showWeather, !text.weather.isBlank {
                if !metaSegments.isEmpty { metaSegments.append(("  ", dotStyle)) }
                metaSegments.append((text.weather, metaStyle))
            }
            if !fields.showDate, fields.showTime {
                metaSegments.insert((text.time, paint(size: 0.029, color: textColor, medium: true)), at: 0)
            }
            if !metaSegments.isEmpty {
                col.add(height: metaStyle.lineHeight, width: segmentsWidth(metaSegments)) { ctx, top in
                    drawSegments(ctx, x: 0, baseline: top + metaStyle.font.pointSize * 0.95, segments: metaSegments)
                }
                col.gap(fs(0.012))
            }

            if fields.showAddress {
                let addressStyle = paint(size: 0.032, color: textColor, medium: true, letterSpacing: 0.005)
                let lines = wrap(text.address, style: addressStyle, maxWidth: maxWidth, maxLines: 2)
                if !lines.isEmpty {
                    let lineHeight = addressStyle.lineHeight * 1.06
                    col.add(
                        height: lineHeight * CGFloat(lines.count),
                        width: lines.map { addressStyle.width($0) }.max() ?? 0
                    ) { ctx, top in
                        var baseline = top + addressStyle.font.pointSize * 0.95
                        for line in lines {
                            drawText(line, x: 0, baseline: baseline, style: addressStyle, ctx: ctx)
                            baseline += lineHeight
                        }
                    }
                }
            }

            if fields.showCoords, !text.latValue.isBlank || !text.lngValue.isBlank {
                col.gap(fs(0.012))
                let coordStyle = paint(size: 0.023, color: subColor, letterSpacing: 0.02)
                let coordText = "纬度 \(text.latValue)   经度 \(text.lngValue)"
                col.add(height: coordStyle.lineHeight, width: coordStyle.width(coordText)) { ctx, top in
                    drawText(
                        coordText, x: 0,
                        baseline: top + coordStyle.font.pointSize * 0.95,
                        style: coordStyle, ctx: ctx
                    )
                }
            }

            if fields.showNote, !text.note.isBlank {
                col.gap(fs(0.012))
                let noteStyle = paint(size: 0.024, color: subColor)
                let lines = wrap("备注：\(text.note)", style: noteStyle, maxWidth: maxWidth, maxLines: 2)
                let lineHeight = noteStyle.lineHeight
                col.add(height: lineHeight * CGFloat(lines.count), width: maxWidth) { ctx, top in
                    var baseline = top + noteStyle.font.pointSize * 0.95
                    for line in lines {
                        drawText(line, x: 0, baseline: baseline, style: noteStyle, ctx: ctx)
                        baseline += lineHeight
                    }
                }
            }

            if fields.showLogo {
                let metrics = logoMetrics(1)
                col.gap(fs(0.02))
                col.add(height: metrics.height, width: metrics.width) { ctx, top in
                    drawLogo(ctx, right: col.width, top: top)
                }
            }

            return Block(width: col.width, height: col.height, background: nil, content: col.content())
        }

        private func buildBrandBadge(maxWidth: CGFloat) -> Block {
            let col = Column()
            let badgeText = text.brand.isBlank ? "品牌打卡" : text.brand
            let badgeStyle = paint(size: 0.030, color: .white, bold: true, letterSpacing: 0.02)
            let padX = fs(0.018)
            let padY = fs(0.009)
            let badgeW = badgeStyle.width(badgeText) + padX * 2
            let badgeH = badgeStyle.lineHeight + padY * 2
            let badgeRadius = fs(0.006)
            col.add(height: badgeH, width: badgeW) { ctx, top in
                roundedRect(ctx, x: 0, y: top, width: badgeW, height: badgeH, color: accent, radius: badgeRadius)
                drawText(
                    badgeText, x: padX,
                    baseline: top + padY + badgeStyle.font.pointSize * 0.98,
                    style: badgeStyle, ctx: ctx
                )
            }
            col.gap(fs(0.016))

            let timeStyle = paint(size: 0.062, color: textColor, condensed: true, letterSpacing: -0.005)
            let colonStyle = paint(size: 0.062, color: accent, condensed: true)
            let timeSegments = accentColonTime(timeStyle, colonStyle)
            let timeW = segmentsWidth(timeSegments)
            let timeH = timeStyle.lineHeight * 0.9

            let dateStyle = paint(size: 0.026, color: textColor, medium: true)
            let subStyle = paint(size: 0.024, color: subColor, letterSpacing: 0.01)
            let rightText = [text.week, text.weather].filter { !$0.isBlank }.joined(separator: "  ")
            let rightW = max(dateStyle.width(text.date), subStyle.width(rightText))
            let dividerW = fs(0.004)
            let gap = fs(0.02)
            let rowW = timeW + gap + dividerW + gap + rightW
            let rowH = max(timeH, dateStyle.lineHeight + fs(0.008) + subStyle.lineHeight)
            col.add(height: rowH, width: rowW) { ctx, top in
                drawSegments(ctx, x: 0, baseline: top + timeStyle.font.pointSize * 0.9, segments: timeSegments)
                let barX = timeW + gap
                ctx.setFillColor(accent.cgColor)
                ctx.addPath(UIBezierPath(
                    roundedRect: CGRect(
                        x: barX, y: top + fs(0.004),
                        width: dividerW, height: rowH - fs(0.006)
                    ),
                    cornerRadius: dividerW / 2
                ).cgPath)
                ctx.fillPath()
                let textX = barX + dividerW + gap
                drawText(
                    text.date, x: textX,
                    baseline: top + dateStyle.font.pointSize * 0.98,
                    style: dateStyle, ctx: ctx
                )
                drawText(
                    rightText, x: textX,
                    baseline: top + dateStyle.lineHeight + fs(0.008) + subStyle.font.pointSize * 0.95,
                    style: subStyle, ctx: ctx
                )
            }
            col.gap(fs(0.018))

            if fields.showAddress {
                let addressStyle = paint(size: 0.029, color: textColor, medium: true, letterSpacing: 0.005)
                let lines = wrap(text.address, style: addressStyle, maxWidth: maxWidth, maxLines: 2)
                if !lines.isEmpty {
                    let lineHeight = addressStyle.lineHeight * 1.04
                    col.add(
                        height: lineHeight * CGFloat(lines.count),
                        width: lines.map { addressStyle.width($0) }.max() ?? 0
                    ) { ctx, top in
                        var baseline = top + addressStyle.font.pointSize * 0.95
                        for line in lines {
                            drawText(line, x: 0, baseline: baseline, style: addressStyle, ctx: ctx)
                            baseline += lineHeight
                        }
                    }
                }
            }

            if fields.showNote, !text.note.isBlank {
                col.gap(fs(0.01))
                let noteStyle = paint(size: 0.022, color: subColor)
                let value = "备注：\(text.note)"
                col.add(height: noteStyle.lineHeight, width: noteStyle.width(value)) { ctx, top in
                    drawText(
                        value, x: 0,
                        baseline: top + noteStyle.font.pointSize * 0.95,
                        style: noteStyle, ctx: ctx
                    )
                }
            }

            if fields.showLogo {
                let metrics = logoMetrics(1)
                col.gap(fs(0.016))
                col.add(height: metrics.height, width: metrics.width) { ctx, top in
                    drawLogo(ctx, right: col.width, top: top)
                }
            }

            return Block(width: col.width, height: col.height, background: nil, content: col.content())
        }

        private func buildCheckinCard(maxWidth: CGFloat) -> Block {
            let col = Column()
            let showHeader = fields.showHeader
            let title = "打卡记录"
            let titleStyle = paint(size: 0.023, color: .white, medium: true, letterSpacing: 0.08)
            let timeStyle = paint(size: 0.056, color: cardTextColor, condensed: true, letterSpacing: -0.005)
            let timeW = timeStyle.width(text.time)
            let padX = fs(0.024)
            let padY = fs(0.012)
            let titleW = titleStyle.width(title)
            let headerH = showHeader ? titleStyle.lineHeight + padY * 2 : 0
            let cardBodyH = timeStyle.lineHeight * 1.12 + padY * 2
            let cardW = max(timeW + padX * 2, showHeader ? titleW + padX * 2 + fs(0.03) : 0)

            col.add(height: headerH + cardBodyH, width: cardW) { ctx, top in
                roundedRect(
                    ctx, x: 0, y: top, width: cardW, height: headerH + cardBodyH,
                    color: cardColor
                )
                if showHeader {
                    let tab = UIBezierPath(
                        roundedRect: CGRect(x: 0, y: top, width: cardW, height: headerH),
                        byRoundingCorners: [.topLeft, .topRight],
                        cornerRadii: CGSize(width: radius, height: radius)
                    )
                    ctx.setFillColor(accent.cgColor)
                    ctx.addPath(tab.cgPath)
                    ctx.fillPath()
                    drawText(
                        title, x: padX,
                        baseline: top + padY + titleStyle.font.pointSize * 0.98,
                        style: titleStyle, ctx: ctx
                    )
                }
                drawText(
                    text.time, x: padX,
                    baseline: top + headerH + padY + timeStyle.font.pointSize,
                    style: timeStyle, ctx: ctx
                )
            }
            col.gap(fs(0.022))

            let dateStyle = paint(size: 0.024, color: subColor, medium: true, letterSpacing: 0.03)
            let dateLine = [text.date, text.week].filter { !$0.isBlank }.joined(separator: "  ")
            if fields.showDate || fields.showWeek {
                col.add(height: dateStyle.lineHeight, width: dateStyle.width(dateLine)) { ctx, top in
                    drawText(
                        dateLine, x: 0,
                        baseline: top + dateStyle.font.pointSize * 0.95,
                        style: dateStyle, ctx: ctx
                    )
                }
                col.gap(fs(0.008))
            }

            if fields.showAddress {
                let addressStyle = paint(size: 0.029, color: textColor, medium: true, letterSpacing: 0.005)
                let lines = wrap(text.address, style: addressStyle, maxWidth: maxWidth, maxLines: 2)
                if !lines.isEmpty {
                    let lineHeight = addressStyle.lineHeight * 1.05
                    col.add(
                        height: lineHeight * CGFloat(lines.count),
                        width: lines.map { addressStyle.width($0) }.max() ?? 0
                    ) { ctx, top in
                        var baseline = top + addressStyle.font.pointSize * 0.95
                        for line in lines {
                            drawText(line, x: 0, baseline: baseline, style: addressStyle, ctx: ctx)
                            baseline += lineHeight
                        }
                    }
                    col.gap(fs(0.012))
                }
            }

            if fields.showCoords, !text.latValue.isBlank {
                let coordStyle = paint(size: 0.022, color: subColor, letterSpacing: 0.02)
                let value = "纬度 \(text.latValue)   经度 \(text.lngValue)"
                col.add(height: coordStyle.lineHeight, width: coordStyle.width(value)) { ctx, top in
                    drawText(
                        value, x: 0,
                        baseline: top + coordStyle.font.pointSize * 0.95,
                        style: coordStyle, ctx: ctx
                    )
                }
                col.gap(fs(0.008))
            }

            if fields.showNote, !text.note.isBlank {
                let noteStyle = paint(size: 0.022, color: subColor)
                let lines = wrap("备注：\(text.note)", style: noteStyle, maxWidth: maxWidth, maxLines: 2)
                let lineHeight = noteStyle.lineHeight
                col.add(height: lineHeight * CGFloat(lines.count), width: maxWidth) { ctx, top in
                    var baseline = top + noteStyle.font.pointSize * 0.95
                    for line in lines {
                        drawText(line, x: 0, baseline: baseline, style: noteStyle, ctx: ctx)
                        baseline += lineHeight
                    }
                }
                col.gap(fs(0.008))
            }

            if fields.showSeal {
                let footerStyle = paint(size: 0.019, color: subColor, letterSpacing: 0.03)
                let sealSize = footerStyle.lineHeight * 0.9
                let footerText = "\(text.brand)已确保时间真实不可篡改"
                let footerW = sealSize + fs(0.006) + footerStyle.width(footerText)
                col.add(height: sealSize, width: footerW) { ctx, top in
                    drawSeal(ctx, x: 0, y: top, size: sealSize, color: subColor)
                    drawText(
                        footerText, x: sealSize + fs(0.006),
                        baseline: top + sealSize * 0.5 + footerStyle.font.pointSize * 0.36,
                        style: footerStyle, ctx: ctx
                    )
                }
            }

            if fields.showLogo {
                let metrics = logoMetrics(0.9)
                col.gap(fs(0.012))
                col.add(height: metrics.height, width: metrics.width) { ctx, top in
                    drawLogo(ctx, right: col.width, top: top, scale: 0.9)
                }
            }

            return Block(width: col.width, height: col.height, background: nil, content: col.content())
        }

        private func buildGoldDivider(maxWidth: CGFloat) -> Block {
            let col = Column()
            let timeStyle = paint(size: 0.075, color: textColor, condensed: true, letterSpacing: 0)
            let colonStyle = paint(size: 0.075, color: textColor, condensed: true)
            let timeSegments = accentColonTime(timeStyle, colonStyle)
            let timeW = segmentsWidth(timeSegments)
            let timeH = timeStyle.lineHeight * 0.9

            let dateStyle = paint(size: 0.027, color: textColor, medium: true)
            let subStyle = paint(size: 0.026, color: subColor, letterSpacing: 0.01)
            let rightText = [text.week, text.weather].filter { !$0.isBlank }.joined(separator: "  ")
            let rightW = max(dateStyle.width(text.date), subStyle.width(rightText))
            let barW = fs(0.0045)
            let gap = fs(0.022)
            let rowW = timeW + gap + barW + gap + rightW
            let rightH = dateStyle.lineHeight + fs(0.006) + subStyle.lineHeight
            let rowH = max(timeH, rightH)
            col.add(height: rowH, width: rowW) { ctx, top in
                drawSegments(ctx, x: 0, baseline: top + timeStyle.font.pointSize * 0.9, segments: timeSegments)
                let barX = timeW + gap
                ctx.setFillColor(accent.cgColor)
                ctx.addPath(UIBezierPath(
                    roundedRect: CGRect(
                        x: barX, y: top + fs(0.004),
                        width: barW, height: rowH - fs(0.008)
                    ),
                    cornerRadius: barW / 2
                ).cgPath)
                ctx.fillPath()
                let textX = barX + barW + gap
                let startY = top + (rowH - rightH) / 2
                drawText(
                    text.date, x: textX,
                    baseline: startY + dateStyle.font.pointSize * 0.98,
                    style: dateStyle, ctx: ctx
                )
                drawText(
                    rightText, x: textX,
                    baseline: startY + dateStyle.lineHeight + fs(0.006) + subStyle.font.pointSize * 0.95,
                    style: subStyle, ctx: ctx
                )
            }
            col.gap(fs(0.02))

            if fields.showAddress {
                let addressStyle = paint(size: 0.030, color: textColor, medium: true, letterSpacing: 0.005)
                let lines = wrap(text.address, style: addressStyle, maxWidth: maxWidth, maxLines: 2)
                if !lines.isEmpty {
                    let lineHeight = addressStyle.lineHeight * 1.05
                    col.add(
                        height: lineHeight * CGFloat(lines.count),
                        width: lines.map { addressStyle.width($0) }.max() ?? 0
                    ) { ctx, top in
                        var baseline = top + addressStyle.font.pointSize * 0.95
                        for line in lines {
                            drawText(line, x: 0, baseline: baseline, style: addressStyle, ctx: ctx)
                            baseline += lineHeight
                        }
                    }
                }
            }

            if fields.showNote, !text.note.isBlank {
                col.gap(fs(0.01))
                let noteStyle = paint(size: 0.022, color: subColor)
                let value = "备注：\(text.note)"
                col.add(height: noteStyle.lineHeight, width: noteStyle.width(value)) { ctx, top in
                    drawText(
                        value, x: 0,
                        baseline: top + noteStyle.font.pointSize * 0.95,
                        style: noteStyle, ctx: ctx
                    )
                }
            }

            if fields.showLogo {
                let metrics = logoMetrics(1)
                col.gap(fs(0.016))
                col.add(height: metrics.height, width: metrics.width) { ctx, top in
                    drawLogo(ctx, right: col.width, top: top)
                }
            }

            return Block(width: col.width, height: col.height, background: nil, content: col.content())
        }

        private func buildBottomPill(maxWidth: CGFloat) -> Block {
            let col = Column()
            let labelStyle = paint(size: 0.028, color: accent, bold: true, letterSpacing: 0.04)
            let timeStyle = paint(size: 0.040, color: cardTextColor, condensed: true, letterSpacing: 0)
            let pillPadX = fs(0.020)
            let pillPadY = fs(0.010)
            let pillH = max(labelStyle.lineHeight, timeStyle.lineHeight) + pillPadY * 2
            let labelText = "打卡"
            let pillW = labelStyle.width(labelText) + fs(0.01) + timeStyle.width(text.time) + pillPadX * 2
            col.add(height: pillH, width: pillW) { ctx, top in
                roundedRect(
                    ctx, x: 0, y: top, width: pillW, height: pillH,
                    color: cardColor, radius: pillH / 2
                )
                let baseline = top + pillH / 2 + labelStyle.font.pointSize * 0.36
                drawText(labelText, x: pillPadX, baseline: baseline, style: labelStyle, ctx: ctx)
                drawText(
                    text.time, x: pillPadX + labelStyle.width(labelText) + fs(0.01),
                    baseline: baseline, style: timeStyle, ctx: ctx
                )
            }
            col.gap(fs(0.016))

            let barW = fs(0.005)
            let textIndent = barW + fs(0.014)
            let addressStyle = paint(size: 0.028, color: textColor, medium: true, letterSpacing: 0.005)
            let dateStyle = paint(size: 0.023, color: subColor, letterSpacing: 0.02)
            let addressLines = fields.showAddress
                ? wrap(text.address, style: addressStyle, maxWidth: maxWidth - textIndent, maxLines: 2)
                : []
            let dateLine = [text.date, text.week].filter { !$0.isBlank }.joined(separator: "  ")
            let lineHeight = addressStyle.lineHeight * 1.05
            let infoGap = (!addressLines.isEmpty && !dateLine.isBlank) ? fs(0.008) : 0
            let blockH = lineHeight * CGFloat(addressLines.count) + infoGap +
                (dateLine.isBlank ? 0 : dateStyle.lineHeight)
            if !addressLines.isEmpty || !dateLine.isBlank {
                col.add(height: blockH, width: maxWidth) { ctx, top in
                    ctx.setFillColor(accent.cgColor)
                    ctx.addPath(UIBezierPath(
                        roundedRect: CGRect(
                            x: 0, y: top + blockH * 0.06,
                            width: barW, height: blockH * 0.82
                        ),
                        cornerRadius: barW / 2
                    ).cgPath)
                    ctx.fillPath()
                    var baseline = top + addressStyle.font.pointSize * 0.95
                    for line in addressLines {
                        drawText(line, x: textIndent, baseline: baseline, style: addressStyle, ctx: ctx)
                        baseline += lineHeight
                    }
                    if !dateLine.isBlank {
                        drawText(
                            dateLine, x: textIndent,
                            baseline: top + lineHeight * CGFloat(addressLines.count) + infoGap +
                                dateStyle.font.pointSize * 0.95,
                            style: dateStyle, ctx: ctx
                        )
                    }
                }
            }

            if fields.showSeal {
                col.gap(fs(0.014))
                let footerStyle = paint(size: 0.018, color: subColor, letterSpacing: 0.03)
                let sealSize = footerStyle.lineHeight * 0.9
                let value = "\(text.brand)已验证｜时间地点真实"
                col.add(
                    height: sealSize,
                    width: sealSize + fs(0.006) + footerStyle.width(value)
                ) { ctx, top in
                    drawSeal(ctx, x: 0, y: top, size: sealSize, color: subColor)
                    drawText(
                        value, x: sealSize + fs(0.006),
                        baseline: top + sealSize * 0.5 + footerStyle.font.pointSize * 0.36,
                        style: footerStyle, ctx: ctx
                    )
                }
            }

            if fields.showLogo {
                let metrics = logoMetrics(0.85)
                col.gap(fs(0.012))
                col.add(height: metrics.height, width: metrics.width) { ctx, top in
                    drawLogo(ctx, right: col.width, top: top, scale: 0.85)
                }
            }

            return Block(width: col.width, height: col.height, background: nil, content: col.content())
        }
    }
}

private func clamp(_ value: CGFloat, _ lower: CGFloat, _ upper: CGFloat) -> CGFloat {
    min(max(value, lower), upper)
}
