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
        static let usageLeft = "display.usageLeft"       // Bool — v1.9's key, still read by v1.9 after a downgrade
        /// RUNBOOK-v2.0 Step 3's iOS storage rule: Vs pace is its own Bool, so v1.9 (which reads only `usageLeft`)
        /// sees Used when it is on — choosing Vs pace clears `usageLeft`.
        static let usageVsPace = "display.usageVsPace"   // Bool
        static let usageEndMark = "display.usageEndMark" // "round" | "line" | "pace" (CCRM-103), default "line"
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
    /// CCRM-100 (Vs Pace Display): Used · Left · Vs pace.
    static var mode: PaceText.Mode { mode(left: defaults.bool(forKey: Key.usageLeft), vsPace: defaults.bool(forKey: Key.usageVsPace)) }

    /// `setMode` never writes both keys true, so both true means v1.9 (which writes only `usageLeft`) chose Left
    /// after v2.0 chose Vs pace — a downgrade and re-upgrade: Left wins, the user's latest choice.
    static func mode(left: Bool, vsPace: Bool) -> PaceText.Mode {
        if left { return .left }
        return vsPace ? .vsPace : .used
    }

    /// Clears a stale Vs pace flag left behind by that round trip; run at launch.
    static func repairMode() {
        if defaults.bool(forKey: Key.usageLeft) && defaults.bool(forKey: Key.usageVsPace) { setMode(.left) }
    }
    static func setMode(_ mode: PaceText.Mode) {
        defaults.set(mode == .left, forKey: Key.usageLeft)
        defaults.set(mode == .vsPace, forKey: Key.usageVsPace)
    }
    static var usageLeft: Bool { mode == .left }
    /// CCRM-103 (Hybrid Notification): Appearance › Usage end mark; Line by default, v1.9 never reads the key.
    static var usageEndMark: UsageEndMark { UsageEndMark(key: defaults.string(forKey: Key.usageEndMark)) }
    static func setUsageEndMark(_ mark: UsageEndMark) { defaults.set(mark.rawValue, forKey: Key.usageEndMark) }
    static var chartDown: Bool { defaults.bool(forKey: Key.chartDown) }
    static var clockFirst: Bool { defaults.bool(forKey: Key.clockFirst) }
    static var redPastPace: Bool { defaults.object(forKey: Key.redPastPace) as? Bool ?? true }
    static var themeColour: String { defaults.string(forKey: Key.themeColour) ?? ThemeColours.perProvider }

    static func accentName(_ key: String) -> String? { defaults.string(forKey: Key.accent(key)) }
    static func setAccentName(_ name: String?, for key: String) { defaults.set(name, forKey: Key.accent(key)) }
    static func creditsVisible(_ key: String) -> Bool { defaults.object(forKey: Key.credits(key)) as? Bool ?? true }
    static func setCreditsVisible(_ on: Bool, for key: String) { defaults.set(on, forKey: Key.credits(key)) }

    // MARK: Used / Left / Vs pace — the words flip, the bars never do (they always show what's spent)

    /// The usage figure alone, in Used or Left terms (never a distance): 62 or 38. The 5h and Weekly companions
    /// always show usage, bare in Left mode (spec mobile §5).
    static func value(_ pct: Double?) -> Int? {
        pct.map { usageLeft ? max(0, 100 - Int($0)) : Int($0) }
    }
    /// Whether the figure for (`pct`, `pace`) is a distance from the pace mark (Vs pace mode and not a fallback).
    static func isDistance(_ pct: Double?, pace: Double?) -> Bool {
        mode == .vsPace && Pace.vsPace(usedPct: pct, elapsedPct: pace) != nil
    }
    /// The figure without any word: "62%", "38%" (Left), "10%" (Vs pace, unsigned — `figureColour` says the side; the
    /// usage when it falls back).
    static func number(_ pct: Double?, pace: Double?) -> String {
        if mode == .vsPace { return PaceText.vsPaceFigure(usedPct: pct, elapsedPct: pace) }
        return usage(pct)
    }
    /// A companion's figure: always usage ("5h 34%"), bare in Left mode.
    static func usage(_ pct: Double?) -> String { value(pct).map { "\($0)%" } ?? "—" }
    /// "62%", "38% left" or "13% over pace" / "10% under pace" / "0% on pace" as a plain string, for text-only surfaces:
    /// with no colour to carry the side, the words do (rev D §7b's colour-blind cue, as the notification's caption).
    static func pct(_ pct: Double?, pace: Double?) -> String {
        if isDistance(pct, pace: pace) {
            let side: String
            switch PaceText.vsPaceTone(usedPct: pct, elapsedPct: pace) {
            case .under: side = "under pace"
            case .over: side = "over pace"
            default: side = "on pace"
            }
            return "\(number(pct, pace: pace)) \(side)"
        }
        guard let v = value(pct) else { return "—" }
        return usageLeft ? "\(v)% left" : "\(v)%"
    }
    /// A headline figure: "62%", or "38%" with a smaller "left" (or "vs pace") after it so big numbers keep their size.
    static func pctText(_ pct: Double?, pace: Double?, size: CGFloat) -> Text {
        let small = Font.system(size: max(10, size * 0.42), weight: .semibold)
        if isDistance(pct, pace: pace) {
            // Unsigned since rev D §7b: the small word says the side where the figure is not coloured.
            let side = String(self.pct(pct, pace: pace).drop { $0 != " " })
            return Text("\(number(pct, pace: pace))\(Text(side).font(small))")
        }
        guard let v = value(pct) else { return Text("—") }
        guard usageLeft else { return Text("\(v)%") }
        return Text("\(v)%\(Text(" left").font(small))")
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

// MARK: CCRM-103 (Hybrid Notification) — the shape grammar's figures (rev C §1c, rev B §1b, rev D §7b)

extension Display {
    /// A figure in the grammar's type: SF Pro with monospaced digits, its "%" at 58% of the digits on the baseline.
    static func grammarFigure(_ text: String, size: CGFloat, weight: Font.Weight = .bold) -> Text {
        let font = Font.system(size: size, weight: weight).monospacedDigit()
        guard text.count >= 2, text.hasSuffix("%"), text.dropLast().last?.isNumber == true else {
            return Text(text).font(font)
        }
        return Text(String(text.dropLast())).font(font)
            + Text("%").font(.system(size: size * 0.58, weight: weight))
    }

    /// Vs pace green (under the mark) and red (over it); the red is the bar's own red slice.
    static func vsUnder(_ scheme: ColorScheme) -> Color { scheme == .dark ? Color(hex: 0x9CCC65) : Color(hex: 0x2E7D32) }
    static func vsOver(_ scheme: ColorScheme) -> Color { scheme == .dark ? Color(hex: 0xFF5252) : Color(hex: 0xC62828) }

    /// A figure's colour: in Vs pace green / red / `ink` by the side, else (and for a fallback usage figure) `usage`.
    static func figureColour(_ pct: Double?, pace: Double?, ink: Color, usage: Color, _ scheme: ColorScheme) -> Color {
        guard mode == .vsPace else { return usage }
        switch PaceText.vsPaceTone(usedPct: pct, elapsedPct: pace) {
        case .under: return vsUnder(scheme)
        case .over: return vsOver(scheme)
        case .on: return ink
        case .usage: return usage
        }
    }
}
