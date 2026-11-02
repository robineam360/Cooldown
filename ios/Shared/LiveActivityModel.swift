import ActivityKit
import CooldownCore
import Foundation
import SwiftUI

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
        /// True when the row's main window is Weekly (a weekly-only plan, or CCRM-96 (Weekly First)'s Headline);
        /// `weeklyPct`/`weeklyPace` then carry the 5h companion. Optional so older states still decode.
        var weeklyLeads: Bool?
        /// CCRM-96 (Weekly First): the 5h companion binds (bold, its colour step); the lead week is spent (no
        /// companion, "out · Sun 9:30 pm"); and the 5h projection for the Island ("~ runs out 3:25 pm"). Optional,
        /// so states written before v2.0 still decode.
        var companionLoud: Bool?
        var spent: Bool?
        var projection: String?

        var providerValue: Provider { Provider(rawValue: provider) ?? .claude }
        var leadsWeekly: Bool { weeklyLeads ?? false }
        /// The account's colour as Settings resolves it (its own, the theme colour, or the provider's).
        var accent: Color { Display.accent(key: key, provider: providerValue, .dark) }
        var windowLabel: String { leadsWeekly ? "Weekly" : "5h" }
        var companionLabel: String { leadsWeekly ? "5h" : "Weekly" }
        var isSpent: Bool { spent ?? false }
        /// The window has reset since the app last wrote this row (the activity went stale at that moment).
        var hasReset: Bool { reset.map { $0 <= .now } ?? false }
        /// What the number shows: a window that has reset is back at 0%.
        var shownPct: Double? { hasReset ? 0 : pct }
        func clock(_ date: Date) -> String {
            leadsWeekly ? CardContent.dayClock(date.ms, Date.now.ms, Display.use24h, .current) : shortClock(date)
        }

        /// `source` as already led (`Face.lead(for:)`); an unled face is drawn as today (5h, or Weekly when there is no 5h).
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
            companionLoud = face.companionLoud ? true : nil
            spent = face.leadSpent ? true : nil
            projection = face.leadsWeekly ? nil : face.session?.projectionShort.flatMap { $0.hasPrefix("~ runs out") ? $0 : nil }
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
