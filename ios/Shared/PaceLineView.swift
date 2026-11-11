import CooldownCore
import SwiftUI

/// CCRM-104 (Pace Line Parity): drawing `PaceLine` (core §6.3 Printed form). The duration and word take the tone
/// (green under the mark, red over it, ink on it) and are set semibold; the rest is the line's own ink. CCRM-108 (Reset
/// Named): the line names the reset ("· resets Wed 12:30 PM"); where a column cannot hold it, "resets" goes first, then
/// the clock (a reset on another day keeps its weekday to the last: a bare clock always means today), then the text steps
/// to 0.8 of its size; the duration and word are the floor and are never cut (Astra C1-4: measured per string on the
/// scaled text, so Dynamic Type drops earlier).
extension PaceLine {

    func tone(_ scheme: ColorScheme, ink: Color) -> Color {
        switch side {
        case .under: Display.vsUnder(scheme)
        case .over: Display.vsOver(scheme)
        case .on: ink
        }
    }

    /// The line at a drop level as one `Text`: the tinted span, then " · " and the reset in `rest`.
    func styled(_ drop: Drop, rest: Color, ink: Color, scheme: ColorScheme, weight: Font.Weight = .semibold) -> Text {
        let lead = Text(self.lead).fontWeight(weight).foregroundStyle(tone(scheme, ink: ink))
        guard let r = reset(drop) else { return lead }
        return lead + Text(PaceText.dot + r).foregroundStyle(rest)
    }

    /// Every distinct drop level, widest first, for `FitLine` (a reset later today has one fewer: no weekday-only form).
    func variants(rest: Color, ink: Color, scheme: ColorScheme, weight: Font.Weight = .semibold) -> [Text] {
        var seen: [String] = []
        return Drop.allCases.compactMap { d in
            let t = text(d)
            guard seen.last != t else { return nil }
            seen.append(t)
            return styled(d, rest: rest, ink: ink, scheme: scheme, weight: weight)
        }
    }
}

/// The first variant that fits the width; the last (the floor) scales to 0.8 before it would truncate. Up to four
/// variants (the pace line's "resets" form, the bare reset, the weekday alone, the floor); fewer repeat the last.
struct FitLine: View {
    /// Widest first; at least one.
    let variants: [Text]

    var body: some View {
        let v = variants
        let last = v.count - 1
        ViewThatFits(in: .horizontal) {
            v[0].lineLimit(1).fixedSize(horizontal: true, vertical: false)
            v[min(1, last)].lineLimit(1).fixedSize(horizontal: true, vertical: false)
            v[min(2, last)].lineLimit(1).fixedSize(horizontal: true, vertical: false)
            v[last].lineLimit(1).minimumScaleFactor(0.8)
        }
    }
}

/// A pace line on a surface: the font and the line's own ink come from the caller.
struct PaceLineView: View {
    let line: PaceLine
    var rest: Color?
    var weight: Font.Weight = .semibold
    @Environment(\.palette) private var p

    var body: some View {
        let scheme = colorSchemeFor(p)
        FitLine(variants: line.variants(rest: rest ?? p.txt, ink: p.ink, scheme: scheme, weight: weight))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(line.text())
    }
}

/// A weekly window's line: the pace line when it has one (CCRM-104), else the text it always was — spent ("Out until
/// …"), or the 5h's own line. The caller sets the font.
struct WindowLine: View {
    let content: WindowContent
    var folded = false
    @Environment(\.palette) private var p

    var body: some View {
        if let line = content.line {
            PaceLineView(line: line)
        } else {
            Text(folded ? CardContent.compactLine(content) : content.paceLineFull)
                .foregroundStyle(content.paceLineAlarm ? p.red : p.txt)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
