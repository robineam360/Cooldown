import ActivityKit
import CooldownCore
import Foundation
import SwiftUI

/// CCRM-94 (iOS App): the Live Activity's data — the Duet with Weekly (wireframe rev B §6, Robin's Q2): up to two
/// accounts under iOS's 160 pt cap. CCRM-103 (Hybrid Notification): each row is the 5h ring on the left and the
/// Weekly stack on the right.
struct CooldownActivityAttributes: ActivityAttributes {
    struct Row: Codable, Hashable {
        var key: String
        var label: String
        var provider: String
        var pct: Double?
        var pace: Double?
        var reset: Date?
        var runOut: Date?
        var weeklyPct: Double?
        var weeklyPace: Double?
        /// Set when the numbers are over 30 minutes old: the row says "as of" instead of the countdown.
        var asOf: Date?
        var signIn: Bool
        /// True when the row's main window is Weekly (a weekly-only plan, or CCRM-96 (Weekly First)'s Headline);
        /// `weeklyPct`/`weeklyPace` then carry the 5h companion. Optional so older states still decode.
        var weeklyLeads: Bool?
        /// CCRM-96 (Weekly First): the 5h companion binds (bold, its colour step); the lead week is spent (no
        /// companion, "out · Sun 9:30 pm"); and the 5h projection for the Island ("~ runs out 3:25 pm"). Optional,
        /// so states written before v2.0 still decode.
        var companionLoud: Bool?
        var spent: Bool?
        var projection: String?
        /// CCRM-102: the 5h can't be used while the week is spent — its ring greys.
        var weekGated: Bool?
        /// CCRM-103 (Hybrid Notification): the 5h's own reset (the ring reads 0% once it passes), and the Weekly's words
        /// — the full line ("Slow down · ≈11%/day") and the band alone ("Slow down"). Optional, so older states decode.
        var fiveReset: Date?
        var weeklyLine: String?
        var weeklyWords: String?
        /// CCRM-104 (Pace Line Parity): the Weekly's pace line (core §6.3 Printed form) and the 5h's projection words
        /// ("on track for ~69%") for the single account's second line. Optional, so older states decode.
        var paceLine: PaceLine?
        var fiveFold: String?

        var providerValue: Provider { Provider(rawValue: provider) ?? .claude }
        var leadsWeekly: Bool { weeklyLeads ?? false }
        /// The account's colour as Settings resolves it (its own, the theme colour, or the provider's).
        var accent: Color { Display.accent(key: key, provider: providerValue, .dark) }
        var windowLabel: String { leadsWeekly ? "Weekly" : "5h" }
        var companionLabel: String { leadsWeekly ? "5h" : "Weekly" }
        var isSpent: Bool { spent ?? false }
        var isWeekGated: Bool { weekGated ?? false }
        /// The window has reset since the app last wrote this row (the activity went stale at that moment).
        var hasReset: Bool { reset.map { $0 <= .now } ?? false }
        /// What the number shows: a window that has reset is back at 0%.
        var shownPct: Double? { hasReset ? 0 : pct }
        func clock(_ date: Date) -> String {
            leadsWeekly ? CardContent.dayClock(date.ms, Date.now.ms, Display.use24h, .current) : shortClock(date)
        }

        /// The 5h for the ring: the companion of a Weekly-led row, the main slot of an older 5h-led state; nil on a
        /// plan with no 5h (B1). A 5h that has reset since the row was written reads 0%.
        var five: Face.FiveHour? {
            if leadsWeekly {
                guard weeklyPct != nil else { return nil }
                let gone = fiveReset.map { $0 <= .now } ?? false
                return Face.FiveHour(pct: gone ? 0 : weeklyPct, pace: gone ? nil : weeklyPace, reset: fiveReset)
            }
            return Face.FiveHour(pct: shownPct, pace: hasReset ? nil : pace, reset: reset)
        }
        /// The Weekly for the bar: the main slot of a Weekly-led row, nil when the row carries no Weekly lead.
        var weekPct: Double? { leadsWeekly ? shownPct : nil }
        var weekPace: Double? { leadsWeekly && !hasReset ? pace : nil }

        /// CCRM-103: whatever the Headline, the row is drawn Weekly-led (the bars), the 5h its companion (the ring) —
        /// so `source` may be led either way (`Face.lead(for:)`).
        init(_ source: Face) {
            let face = source.grammar
            key = face.account.key
            label = face.account.label
            provider = face.account.provider.rawValue
            pct = face.pct
            pace = face.pace
            reset = face.reset
            runOut = face.runOut
            weeklyPct = face.weeklyPct
            weeklyPace = face.weeklyPace
            asOf = (face.status == .aging || face.status == .stale) ? face.fetchedAt : nil
            signIn = face.status == .signIn
            weeklyLeads = face.leadsWeekly ? true : nil
            companionLoud = face.companionLoud ? true : nil
            spent = face.leadSpent ? true : nil
            weekGated = face.fiveGreyed ? true : nil
            let fiveContent = face.fiveHour?.content
            projection = fiveContent?.projectionShort.flatMap { $0.hasPrefix("~ runs out") ? $0 : nil }
            fiveReset = face.fiveHour?.reset
            weeklyLine = face.leadsWeekly ? face.session.flatMap { $0.paceLine.isEmpty ? nil : $0.paceLine } : nil
            weeklyWords = face.leadsWeekly ? face.session?.band.flatMap(PaceText.bandWords) : nil
            paceLine = face.leadsWeekly ? face.session?.line : nil
            fiveFold = fiveContent?.projectionFold
        }
    }

    struct ContentState: Codable, Hashable {
        var rows: [Row]
        /// The final frame at a reset: "Teams · 5h reset — Usage is back at 0%. The last window peaked at 87%."
        var ended: Ended?
        /// CCBG-72 (Live Activity Stale Mode): the Usage display and end mark the activity is drawn with. The view reads
        /// them from the shared settings, so they ride in the state only to make a display change a new state, which
        /// iOS redraws at once. Optional, so older states still decode.
        var display: String?

        init(rows: [Row], ended: Ended?, display: String? = Self.currentDisplay) {
            self.rows = rows
            self.ended = ended
            self.display = display
        }

        /// "vsPace·line": the mode and the end mark as the settings hold them now.
        static var currentDisplay: String { "\(Display.mode.rawValue)·\(Display.usageEndMark.rawValue)" }
    }

    struct Ended: Codable, Hashable {
        var label: String
        var provider: String
        var peak: Int
    }
}

// MARK: CCRM-103 (Hybrid Notification): every row is Weekly-led, the 5h rides in its ring fields

extension CooldownActivityAttributes {
    /// When iOS should grey the activity: the first future reset among the rows' Weekly and their 5h rings.
    static func staleDate(_ rows: [Row], now: Date = .now) -> Date? {
        (rows.compactMap(\.reset) + rows.compactMap(\.fiveReset)).filter { $0 > now }.min()
    }

    /// The final frame names the 5h's reset, so its peak is the 5h's last reading — the stored companion, not `five`,
    /// which reads 0% once that reset has passed — or the Weekly's on a plan with no 5h.
    static func endedPeak(_ row: Row) -> Int { Int((row.leadsWeekly ? row.weeklyPct : nil) ?? row.pct ?? 0) }
}
