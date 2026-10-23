// Ported from CooldownMac sources/ccooldown-core/TimeFormat.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// Whether reset times read as "4:46 PM" or "16:46".
///
/// Defaults to following the system. The previous behaviour was a hardcoded boolean that
/// happened to be false, which meant the app ignored the user's own 24-hour preference and
/// there was no way to change it.
public enum TimeFormat: String, Sendable, Codable, CaseIterable {
    /// Follow the user's macOS setting. The right default: the OS already knows.
    case system
    case twelveHour
    case twentyFourHour

    public static let `default`: TimeFormat = .system

    public var displayName: String {
        switch self {
        case .system: "Match system"
        case .twelveHour: "12-hour"
        case .twentyFourHour: "24-hour"
        }
    }

    /// Resolves to a concrete choice.
    ///
    /// `.system` is detected by asking the locale for its preferred hour skeleton: the pattern
    /// for `"j"` contains an am/pm symbol exactly when the locale is 12-hour. This is the
    /// documented way to read the preference, and it tracks the System Settings toggle rather
    /// than guessing from the region.
    public func uses24Hour(locale: Locale = .current) -> Bool {
        switch self {
        case .twelveHour: return false
        case .twentyFourHour: return true
        case .system:
            guard let pattern = DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: locale)
            else { return false }
            return !pattern.contains("a")
        }
    }
}
