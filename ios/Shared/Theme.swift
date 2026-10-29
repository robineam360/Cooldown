import CooldownCore
import SwiftUI

/// CCRM-94 (iOS App): the colours of the approved wireframes (design/2026-10-23-ios-wireframes.js, DARK/LIGHT),
/// which are the Android app's. Severity breakpoints follow spec core §5: above 80 yellow, above 90 orange,
/// at 100 red; above pace the overshoot is red from the pace tick.
struct Palette {
    let bg, card, ink, txt, sub, line, fill: Color
    let yellow, orange, red, pace, tick, ringTrack: Color

    static let dark = Palette(
        bg: Color(hex: 0x0E0E0E), card: Color(hex: 0x1C1C1E), ink: Color(hex: 0xF2F2F2), txt: Color(hex: 0xD1D1D6),
        sub: Color(hex: 0x98989F), line: Color(hex: 0x38383A), fill: Color(hex: 0x2C2C2E),
        yellow: Color(hex: 0xFDD663), orange: Color(hex: 0xFFA726), red: Color(hex: 0xFF5252),
        pace: Color(hex: 0xFDD663), tick: Color(hex: 0xC7C7CC), ringTrack: Color(hex: 0x55555A))
    static let light = Palette(
        bg: Color(hex: 0xF2F2F7), card: .white, ink: .black, txt: Color(hex: 0x3A3A3C),
        sub: Color(hex: 0x6C6C70), line: Color(hex: 0xD1D1D6), fill: Color(hex: 0xE5E5EA),
        yellow: Color(hex: 0xF9A825), orange: Color(hex: 0xF57C00), red: Color(hex: 0xC62828),
        pace: Color(hex: 0xE0A000), tick: Color(hex: 0x8E8E93), ringTrack: Color(hex: 0xC7C7CC))

    static func current(_ scheme: ColorScheme) -> Palette { scheme == .dark ? .dark : .light }

    /// The account's colour turned up by severity (spec core §5). A nil percent counts as 0.
    func severity(_ pct: Double?, accent: Color) -> Color {
        let p = pct ?? 0
        if p >= 100 { return red }
        if p > 90 { return orange }
        if p > 80 { return yellow }
        return accent
    }
}

/// Each provider's accent: Claude terracotta, ChatGPT green (CCRM-56 (Provider Identity)).
extension Provider {
    func accent(_ scheme: ColorScheme) -> Color {
        switch self {
        case .claude: scheme == .dark ? Color(hex: 0xE59980) : Color(hex: 0xD97757)
        case .chatgpt: scheme == .dark ? Color(hex: 0x19C39A) : Color(hex: 0x10A37F)
        }
    }

    var markAsset: String { self == .claude ? "ClaudeMark" : "ChatGPTMark" }
    var displayName: String { self == .claude ? "Claude" : "ChatGPT" }
}

/// The provider's own mark, tinted (CCRM-56: logos, not dots).
struct ProviderMark: View {
    let provider: Provider
    var size: CGFloat = 20
    var color: Color?
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Image(provider.markAsset)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            // The Claude starburst runs edge to edge in its square while the ChatGPT knot fills ~70% of its own, so
            // Claude is drawn at 82% to match apparent size (approved 24 Oct; Android's drawable does the same).
            .scaleEffect(provider == .claude ? 0.82 : 1)
            .frame(width: size, height: size)
            .foregroundStyle(color ?? provider.accent(scheme))
            .accessibilityHidden(true)
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }
}

/// The palette for the current appearance, as an environment value so every view reads one source.
private struct PaletteKey: EnvironmentKey { static let defaultValue = Palette.dark }
extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

struct PaletteProvider: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    func body(content: Content) -> some View { content.environment(\.palette, Palette.current(scheme)) }
}
