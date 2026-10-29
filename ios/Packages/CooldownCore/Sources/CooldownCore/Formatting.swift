// Ported from CooldownMac sources/ccooldown-core/Formatting.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// Value formatting shared with the Android client. See contract/BEHAVIOR-SPEC.md §2, §6.
public enum Fmt {

    /// Renders minor units as money the way Claude does: `599, exp 2, USD` → `$5.99`.
    ///
    /// US-locale symbol conventions regardless of the user's locale, so two installs
    /// agree. An unrecognised currency code falls back to the code plus a space.
    public static func money(minorUnits: Int64, exponent: Int, currencyCode: String) -> String {
        let exp = min(max(exponent, 0), 6)
        let amount = Double(minorUnits) / pow(10.0, Double(exp))

        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = true
        formatter.groupingSeparator = ","
        formatter.minimumFractionDigits = exp
        formatter.maximumFractionDigits = exp
        let digits = formatter.string(from: NSNumber(value: amount)) ?? "\(amount)"

        return symbol(for: currencyCode) + digits
    }

    private static func symbol(for currencyCode: String) -> String {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.numberStyle = .currency
        formatter.currencyCode = currencyCode
        guard let symbol = formatter.currencySymbol, symbol != currencyCode else {
            // Unknown code — mirror the Android fallback of "CODE ".
            return "\(currencyCode) "
        }
        return symbol
    }

    /// Window bars **truncate** where credits round: a window at 99.7% must read 99
    /// rather than claim a limit the user hasn't reached. The visible consequence is
    /// that windows and credits can differ by a point on the same screen; that is
    /// intended (see the Android roadmap, CCRM-3 phase 2a).
    public static func windowPercentDisplay(_ percent: Double?) -> Int {
        Int(percent ?? 0)
    }

    /// The even-pace diagonal: 0% at the window's start to 100% at its reset.
    /// Staying below it means finishing inside the limit. BEHAVIOR-SPEC §6.
    public static func elapsedPercent(resetsAtMs: Int64?, windowLengthMs: Int64, nowMs: Int64) -> Double? {
        guard let resetsAtMs, windowLengthMs > 0 else { return nil }
        let total = Double(windowLengthMs)
        let remaining = Double(resetsAtMs - nowMs)
        return min(100, max(0, (total - remaining) / total * 100))
    }

    /// A duration as two units at most: "3d 4h", "2h 14m", "9m". Anything at or below
    /// zero, or under a minute, reads "any moment" — a countdown that says "0m" for the
    /// last sixty seconds looks broken.
    public static func compactDuration(ms: Int64) -> String {
        if ms < 60_000 { return "any moment" }
        let totalMinutes = ms / 60_000
        let days = totalMinutes / 1440
        let hours = (totalMinutes % 1440) / 60
        let minutes = totalMinutes % 60
        if days > 0 { return hours > 0 ? "\(days)d \(hours)h" : "\(days)d" }
        if hours > 0 { return minutes > 0 ? "\(hours)h \(minutes)m" : "\(hours)h" }
        return "\(minutes)m"
    }

    /// How long ago something happened: "just now", "2m ago", "3h ago", "2d ago".
    ///
    /// Anything under a minute is "just now" rather than "0m ago", and a clock that has
    /// somehow run backwards also reads "just now" rather than a negative duration.
    public static func relativeAgo(epochMs: Int64, nowMs: Int64) -> String {
        let elapsed = nowMs - epochMs
        if elapsed < 60_000 { return "just now" }
        return "\(compactDuration(ms: elapsed)) ago"
    }

    /// A calendar day in the user's zone: "28 Jul". For the accounts pane's "expires around"
    /// line, where the day is all the precision an estimate can honestly carry.
    public static func day(epochMs: Int64, timeZone: TimeZone = .current, locale: Locale = .current) -> String {
        let formatter = DateFormatter()
        formatter.timeZone = timeZone
        formatter.locale = locale
        formatter.setLocalizedDateFormatFromTemplate("dMMM")
        return formatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }

    /// A reset instant as a calendar-aware phrase: "today at 4:30 PM", "tomorrow at
    /// 9:00 AM", or "Aug 16 at 9:00 AM" once it's further out (CCM-15).
    ///
    /// "Today"/"tomorrow" are calendar days in the *passed* zone, not the machine's —
    /// a reset that is tomorrow in UTC can still be today for the user. The month-day
    /// form comes from a locale template rather than a hardcoded pattern, so the order
    /// localizes; the "today"/"tomorrow" literals are English like the rest of the app's copy.
    ///
    /// `spoken` (CCM-153 [Panel]): the month in full, "October 2 at 9:00 AM", for VoiceOver.
    public static func resetPhrase(
        epochMs: Int64, nowMs: Int64, use24h: Bool,
        timeZone: TimeZone = .current, locale: Locale = .current, spoken: Bool = false
    ) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let reset = Date(timeIntervalSince1970: Double(epochMs) / 1000)
        let now = Date(timeIntervalSince1970: Double(nowMs) / 1000)
        let time = clock(epochMs: epochMs, use24h: use24h, timeZone: timeZone)

        if calendar.isDate(reset, inSameDayAs: now) {
            return "today at \(time)"
        }
        if let tomorrow = calendar.date(byAdding: .day, value: 1, to: now),
           calendar.isDate(reset, inSameDayAs: tomorrow) {
            return "tomorrow at \(time)"
        }
        let formatter = DateFormatter()
        formatter.timeZone = timeZone
        formatter.locale = locale
        formatter.setLocalizedDateFormatFromTemplate(spoken ? "MMMM d" : "MMM d")
        return "\(formatter.string(from: reset)) at \(time)"
    }

    /// A reset moment short enough for a widget ring: "7:34 PM" on the same calendar day as
    /// `nowMs` (in the passed zone), "Sat 9:20 PM" on any other — a 7-day window's clock alone
    /// doesn't say which day (wireframe rev B §2–§3). English weekday names, like the rest of
    /// the app's copy.
    public static func dayClock(
        epochMs: Int64, nowMs: Int64, use24h: Bool, timeZone: TimeZone = .current
    ) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000)
        let time = clock(epochMs: epochMs, use24h: use24h, timeZone: timeZone)
        if calendar.isDate(date, inSameDayAs: Date(timeIntervalSince1970: Double(nowMs) / 1000)) {
            return time
        }
        let formatter = DateFormatter()
        formatter.timeZone = timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "EEE"
        return "\(formatter.string(from: date)) \(time)"
    }

    /// Wall-clock time in the user's zone: "16:46" or "4:46 PM".
    /// CCM-153 [Panel]: dates for VoiceOver, every word in full — "Tuesday 22 September"
    /// (`weekday: true`) or "22 September". English, like the rest of the app's copy.
    public static func spokenDate(epochMs: Int64, weekday: Bool, timeZone: TimeZone = .current) -> String {
        let formatter = DateFormatter()
        formatter.timeZone = timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = weekday ? "EEEE d MMMM" : "d MMMM"
        return formatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }

    /// CCM-153 [Panel]: "Thursday".
    public static func spokenWeekday(epochMs: Int64, timeZone: TimeZone = .current) -> String {
        let formatter = DateFormatter()
        formatter.timeZone = timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "EEEE"
        return formatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }

    /// Whether two instants fall on the same calendar day in `timeZone`.
    public static func sameDay(_ a: Int64, _ b: Int64, timeZone: TimeZone = .current) -> Bool {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar.isDate(
            Date(timeIntervalSince1970: Double(a) / 1000), inSameDayAs: Date(timeIntervalSince1970: Double(b) / 1000))
    }

    public static func clock(epochMs: Int64, use24h: Bool, timeZone: TimeZone = .current) -> String {
        let formatter = DateFormatter()
        formatter.timeZone = timeZone
        // POSIX locale with an explicit pattern, so the 12/24-hour choice is ours and not
        // silently overridden by the region's own convention.
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = use24h ? "HH:mm" : "h:mm a"
        return formatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }
}
