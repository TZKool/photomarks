import SwiftUI

enum TMColor {
    static let bg = Color(argb: 0xFF0B0F14)
    static let bgTop = Color(argb: 0xFF10161F)
    static let panel = Color(argb: 0xFF151C26)
    static let panelStrong = Color(argb: 0xFF1B2431)
    static let chip = Color(argb: 0xFF202A37)
    static let hairline = Color.white.opacity(0.08)
    static let gold = Color(argb: 0xFFE9B949)
    static let goldSoft = Color(argb: 0xFFF7D77E)
    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.62)
    static let textTertiary = Color.white.opacity(0.38)
    static let danger = Color(argb: 0xFFFF6B6B)
    static let success = Color(argb: 0xFF37C978)
}

extension Color {
    init(argb: UInt32) {
        let a = Double((argb >> 24) & 0xFF) / 255
        let r = Double((argb >> 16) & 0xFF) / 255
        let g = Double((argb >> 8) & 0xFF) / 255
        let b = Double(argb & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

struct AppBackground: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [TMColor.bgTop, TMColor.bg, Color.black],
                startPoint: .top,
                endPoint: .bottom
            )
            RadialGradient(
                colors: [TMColor.gold.opacity(0.12), .clear],
                center: .topTrailing,
                startRadius: 8,
                endRadius: 460
            )
            RadialGradient(
                colors: [Color(argb: 0xFF2F9EFF).opacity(0.08), .clear],
                center: .bottomLeading,
                startRadius: 10,
                endRadius: 420
            )
        }
        .ignoresSafeArea()
    }
}

enum GoldGradient {
    static let linear = LinearGradient(
        colors: [TMColor.goldSoft, TMColor.gold],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
}
