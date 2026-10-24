import CooldownCore
import SwiftUI

/// CCRM-94 (iOS App): the display choices ported from Android's Appearance settings (design/2026-10-24-ios-appearance-
/// accounts.html, approved 24 Oct) — Time format, Usage display (Used / Left), Chart orientation, Reset time, red past the
/// pace mark, Theme colour, per-account accent colour and credits visibility. They live in the App Group so the widgets,
/// lock screen, Control Center and Live Activity follow the same choices as the app.
enum Display {
    static var defaults: UserDefaults { Prefs.defaults }

    enum Key {
        static let timeFormat = "display.timeFormat"     // "system" | "12" | "24"
        static let usageLeft = "display.usageLeft"       // Bool
        static let chartDown = "display.chartDown"       // Bool
        static let clockFirst = "display.clockFirst"     // Bool
        static let redPastPace = "display.redPastPace"   // Bool, default true
        static let themeColour = "display.themeColour"   // ThemeColours name or "Per provider"
        static func accent(_ key: String) -> String { "display.accent.\(key)" }
        static func credits(_ key: String) -> String { "display.credits.\(key)" }
    }

    static var timeFormat: String { defaults.string(forKey: Key.timeFormat) ?? "system" }
    /// 24-hour clocks: picked, or — on "System" — whatever the device's locale uses.
    static var use24h: Bool {
        switch timeFormat {
        case "24": true
        case "12": false
        default: !(DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: .current) ?? "h a").contains("a")
        }
    }
    static var usageLeft: Bool { defaults.bool(forKey: Key.usageLeft) }
    static var chartDown: Bool { defaults.bool(forKey: Key.chartDown) }
    static var clockFirst: Bool { defaults.bool(forKey: Key.clockFirst) }
    static var redPastPace: Bool { defaults.object(forKey: Key.redPastPace) as? Bool ?? true }
    static var themeColour: String { defaults.string(forKey: Key.themeColour) ?? ThemeColours.perProvider }

    static func accentName(_ key: String) -> String? { defaults.string(forKey: Key.accent(key)) }
    static func setAccentName(_ name: String?, for key: String) { defaults.set(name, forKey: Key.accent(key)) }
    static func creditsVisible(_ key: String) -> Bool { defaults.object(forKey: Key.credits(key)) as? Bool ?? true }
    static func setCreditsVisible(_ on: Bool, for key: String) { defaults.set(on, forKey: Key.credits(key)) }

    // MARK: Used / Left — the words flip, the bars never do (they always show what's spent)

    /// The figure a readout shows: what's used, or what's left.
    static func value(_ pct: Double?) -> Int? {
        pct.map { usageLeft ? max(0, 100 - Int($0)) : Int($0) }
    }
    /// "62%" or "38% left" as a plain string, for text-only surfaces.
    static func pct(_ pct: Double?) -> String {
        guard let v = value(pct) else { return "—" }
        return usageLeft ? "\(v)% left" : "\(v)%"
    }
    /// The figure without the word, for a secondary readout beside a headline that already says "left".
    static func number(_ pct: Double?) -> String { value(pct).map { "\($0)%" } ?? "—" }
    /// A headline figure: "62%", or "38%" with a smaller "left" after it so big numbers keep their size.
    static func pctText(_ pct: Double?, size: CGFloat) -> Text {
        guard let v = value(pct) else { return Text("—") }
        guard usageLeft else { return Text("\(v)%") }
        return Text("\(v)%\(Text(" left").font(.system(size: max(10, size * 0.42), weight: .semibold)))")
    }

    // MARK: Accent: the account's own, else the theme colour, else the provider's (Android's three levels)

    static func accentName(for key: String, provider: Provider) -> String? {
        if let own = accentName(key) { return own }
        return themeColour == ThemeColours.perProvider ? nil : themeColour
    }
    static func accent(key: String, provider: Provider, _ scheme: ColorScheme) -> Color {
        if let name = accentName(for: key, provider: provider), let option = ThemeColours.option(name) {
            return scheme == .dark ? option.dark : option.light
        }
        return provider.accent(scheme)
    }
}

extension Account {
    /// The colour this account's bars, figures and charts use (Display's three-level resolution).
    func accent(_ scheme: ColorScheme) -> Color { Display.accent(key: key, provider: provider, scheme) }
}

/// Android's theme colours (ui/Palette.kt `selectableOptions`), light and dark. "Material You" is Android-only.
struct ThemeOption: Identifiable, Equatable {
    let name: String
    let light: Color
    let dark: Color
    var id: String { name }
}

enum ThemeColours {
    static let perProvider = "Per provider"
    static let options: [ThemeOption] = [
        ThemeOption(name: "Blue", light: Color(hex: 0x1A73E8), dark: Color(hex: 0x8AB4F8)),
        ThemeOption(name: "Indigo", light: Color(hex: 0x3949AB), dark: Color(hex: 0x9FA8DA)),
        ThemeOption(name: "Cyan", light: Color(hex: 0x00ACC1), dark: Color(hex: 0x4DD0E1)),
        ThemeOption(name: "Teal", light: Color(hex: 0x00897B), dark: Color(hex: 0x4DB6AC)),
        ThemeOption(name: "Green", light: Color(hex: 0x188038), dark: Color(hex: 0x81C995)),
        ThemeOption(name: "Amber", light: Color(hex: 0xF9A825), dark: Color(hex: 0xFDD663)),
        ThemeOption(name: "Deep Orange", light: Color(hex: 0xE64A19), dark: Color(hex: 0xFF8A65)),
        ThemeOption(name: "Red", light: Color(hex: 0xD93025), dark: Color(hex: 0xF28B82)),
        ThemeOption(name: "Pink", light: Color(hex: 0xD81B60), dark: Color(hex: 0xF48FB1)),
        ThemeOption(name: "Purple", light: Color(hex: 0x8E24AA), dark: Color(hex: 0xCE93D8)),
        ThemeOption(name: "Brown", light: Color(hex: 0x6D4C41), dark: Color(hex: 0xBCAAA4)),
    ]
    static func option(_ name: String) -> ThemeOption? { options.first { $0.name == name } }
}
