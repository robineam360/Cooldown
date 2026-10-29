// Ported from CooldownMac sources/ccooldown-core/HistoryStats.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// Turns closed-window records (plus the current open window) into history bars.
/// Pure maths, no AppKit. See contract/BEHAVIOR-SPEC.md §7.
public enum HistoryStats {

    public static let sessionMs: Int64 = 5 * 60 * 60_000
    public static let weekMs: Int64 = 7 * 24 * 60 * 60_000

    public static let sessionKind = "session"
    public static let weeklyKind = "weekly"

    /// The window *names* `UsageRepository.track` keys `ProfileState.lastSeenWindowKey` and
    /// `windowPeak` by (and passes to `onWindowReset`). Not the same strings as the lowercase
    /// history `kind`s above: reading `lastSeenWindowKey[sessionKind]` finds nothing, which is
    /// why the Accounts card's "renews in …" never showed in the app (since CCM-74 [Accounts]
    /// `6031d8c`). Persisted in `state.json`, so never rename these.
    public static let sessionWindowName = "Session"
    public static let weeklyWindowName = "Weekly"

    /// The server rarely reports a clean 100, so "exhausted" is 99.5 inclusive.
    public static let hitLimitThreshold: Double = 99.5

    /// One closed window, as persisted long-term.
    public struct Record: Sendable, Equatable {
        public let kind: String
        /// The window's identity — its `resets_at`.
        public let resetAtMs: Int64
        public let peakPct: Double
        public let hitLimit: Bool

        public init(kind: String, resetAtMs: Int64, peakPct: Double, hitLimit: Bool) {
            self.kind = kind
            self.resetAtMs = resetAtMs
            self.peakPct = peakPct
            self.hitLimit = hitLimit
        }
    }

    /// One bar: a single 5-hour or 7-day window. `current` marks the open one.
    public struct Bar: Sendable, Equatable {
        public let startMs: Int64
        public let resetMs: Int64
        public let peakPct: Double
        public let hitLimit: Bool
        public let current: Bool

        public init(startMs: Int64, resetMs: Int64, peakPct: Double, hitLimit: Bool, current: Bool) {
            self.startMs = startMs
            self.resetMs = resetMs
            self.peakPct = peakPct
            self.hitLimit = hitLimit
            self.current = current
        }
    }

    /// Sessions grouped under the calendar week (Mon-Sun) they started in.
    /// `monday` is an ISO `yyyy-MM-dd` date in the caller's zone.
    public struct Week: Sendable, Equatable {
        public let monday: String
        public let bars: [Bar]
    }

    public static func windowMs(kind: String) -> Int64 {
        kind == weeklyKind ? weekMs : sessionMs
    }

    /// All bars of one kind, newest first. `current` is merged in and de-duplicated
    /// against the log by reset time — the real-world race is a window closing and being
    /// logged while the UI still holds it as current.
    ///
    /// A bar's start is **derived**, never stored: `reset - windowLength`.
    public static func bars(records: [Record], kind: String, current: Bar?) -> [Bar] {
        let length = windowMs(kind: kind)
        let fromLog = records
            .filter { $0.kind == kind }
            .map {
                Bar(startMs: $0.resetAtMs - length, resetMs: $0.resetAtMs,
                    peakPct: $0.peakPct, hitLimit: $0.hitLimit, current: false)
            }
        // Log records come first so, on a collision, the logged (closed) bar wins —
        // matching the Android client's `distinctBy`, which keeps the first occurrence.
        var seen = Set<Int64>()
        return (fromLog + (current.map { [$0] } ?? []))
            .filter { seen.insert($0.resetMs).inserted }
            .sorted { $0.startMs > $1.startMs }
    }

    /// Groups session bars into calendar weeks, newest week first.
    public static func weeks(bars: [Bar], timeZone: TimeZone) -> [Week] {
        Dictionary(grouping: bars) { mondayOf(ms: $0.startMs, timeZone: timeZone) }
            .map { Week(monday: $0.key, bars: $0.value.sorted { $0.startMs > $1.startMs }) }
            .sorted { $0.monday > $1.monday }
    }

    /// The Monday of the ISO week containing `ms`, in `timeZone`, as `yyyy-MM-dd`.
    ///
    /// Grouping must use the **user's** zone, not UTC: two instants six hours apart can
    /// fall in different ISO weeks depending on where the user is.
    public static func mondayOf(ms: Int64, timeZone: TimeZone) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let date = Date(timeIntervalSince1970: Double(ms) / 1000)
        // `.weekday` is 1=Sunday...7=Saturday; ISO wants Monday=1.
        let weekday = calendar.component(.weekday, from: date)
        let isoDay = weekday == 1 ? 7 : weekday - 1
        let monday = calendar.date(byAdding: .day, value: -(isoDay - 1), to: date) ?? date

        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: monday)
    }
}
