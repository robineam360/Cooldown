// Ported from CooldownMac sources/ccooldown-core/HistoryContent.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// Presentation for the History pane's week groups. The heavy lifting — grouping windows into
/// weeks, deriving each bar's span — is `HistoryStats`; this only turns the results into the
/// two strings a week header shows.
public enum HistoryContent {

    /// "Week of 28 Jul" from the ISO `yyyy-MM-dd` a `HistoryStats.Week` carries.
    ///
    /// Falls back to the raw string if it somehow doesn't parse, rather than dropping the
    /// header — a labelled group with an odd date beats a group with no label.
    public static func weekTitle(
        mondayISO: String, timeZone: TimeZone = .current, locale: Locale = .current
    ) -> String {
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.timeZone = timeZone
        parser.dateFormat = "yyyy-MM-dd"
        guard let date = parser.date(from: mondayISO) else { return "Week of \(mondayISO)" }

        let ms = Int64(date.timeIntervalSince1970 * 1000)
        return "Week of \(Fmt.day(epochMs: ms, timeZone: timeZone, locale: locale))"
    }

    /// "7 windows · 1 hit the limit", or just "5 windows" when none did (CCM-150 [Foundation]:
    /// History says "window" for both lengths, and "limit reached" rather than "maxed").
    public static func weekSummary(bars: [HistoryStats.Bar], noun: String = "window") -> String {
        let count = bars.count
        let unit = count == 1 ? noun : "\(noun)s"
        var summary = "\(count) \(unit)"
        let maxed = bars.filter(\.hitLimit).count
        if maxed > 0 { summary += " · \(maxed) hit the limit" }
        return summary
    }

    /// CCM-153 [Panel]: the week header as one VoiceOver heading — "Week of 21 September,
    /// 3 windows, 1 hit the limit". The month in full; the parts joined by commas.
    public static func spokenWeekHeader(
        mondayISO: String, bars: [HistoryStats.Bar], timeZone: TimeZone = .current
    ) -> String {
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.timeZone = timeZone
        parser.dateFormat = "yyyy-MM-dd"
        let week = parser.date(from: mondayISO).map {
            "Week of \(Fmt.spokenDate(epochMs: Int64($0.timeIntervalSince1970 * 1000), weekday: false, timeZone: timeZone))"
        } ?? "Week of \(mondayISO)"
        return "\(week), " + weekSummary(bars: bars).replacingOccurrences(of: " · ", with: ", ")
    }

    /// CCM-153 [Panel]: one History column as one VoiceOver sentence. 5-hour: "Tuesday 22
    /// September, 9:10 AM to 2:10 PM, peak 84 percent" (the other day named when it crosses
    /// midnight); 7-day: "7-day window, 15 September to 22 September, peak 71 percent". Then
    /// ", hit the limit"; the running window says "current window, 84 percent so far" in place
    /// of the peak.
    public static func spokenBar(
        _ bar: HistoryStats.Bar, weekly: Bool, use24h: Bool, timeZone: TimeZone = .current
    ) -> String {
        let span: String
        if weekly {
            span = "7-day window, \(Fmt.spokenDate(epochMs: bar.startMs, weekday: false, timeZone: timeZone)) "
                + "to \(Fmt.spokenDate(epochMs: bar.resetMs, weekday: false, timeZone: timeZone))"
        } else {
            let start = "\(Fmt.spokenDate(epochMs: bar.startMs, weekday: true, timeZone: timeZone)), "
                + Fmt.clock(epochMs: bar.startMs, use24h: use24h, timeZone: timeZone)
            let endClock = Fmt.clock(epochMs: bar.resetMs, use24h: use24h, timeZone: timeZone)
            let end = Fmt.sameDay(bar.startMs, bar.resetMs, timeZone: timeZone)
                ? endClock
                : "\(Fmt.spokenDate(epochMs: bar.resetMs, weekday: true, timeZone: timeZone)), \(endClock)"
            span = "\(start) to \(end)"
        }
        let percent = Int(bar.peakPct.rounded())
        var parts = [span]
        parts.append(bar.current ? "current window, \(percent) percent so far" : "peak \(percent) percent")
        if bar.hitLimit { parts.append("hit the limit") }
        return parts.joined(separator: ", ")
    }
}
