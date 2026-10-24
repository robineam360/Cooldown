import ActivityKit
import CooldownCore
import Foundation

/// CCRM-94 (iOS App): the Live Activity's data — the Duet with Weekly (wireframe rev B §6, Robin's Q2): up to two
/// accounts, each with its 5h row and a thin Weekly line, under iOS's 160 pt cap.
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
        /// True for a weekly-only plan: the row's main window is Weekly. Optional so older states still decode.
        var weeklyLeads: Bool?

        var providerValue: Provider { Provider(rawValue: provider) ?? .claude }
        var leadsWeekly: Bool { weeklyLeads ?? false }
        var windowLabel: String { leadsWeekly ? "Weekly" : "5h" }
        /// The window has reset since the app last wrote this row (the activity went stale at that moment).
        var hasReset: Bool { reset.map { $0 <= .now } ?? false }
        /// What the number shows: a window that has reset is back at 0%.
        var shownPct: Double? { hasReset ? 0 : pct }
        func clock(_ date: Date) -> String {
            leadsWeekly ? CardContent.dayClock(date.ms, Date.now.ms, false, .current) : shortClock(date)
        }

        init(_ source: Face) {
            let face = source.leading
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
        }
    }

    struct ContentState: Codable, Hashable {
        var rows: [Row]
        /// The final frame at a reset: "Teams · 5h reset — Usage is back at 0%. The last window peaked at 87%."
        var ended: Ended?
    }

    struct Ended: Codable, Hashable {
        var label: String
        var provider: String
        var peak: Int
    }
}
