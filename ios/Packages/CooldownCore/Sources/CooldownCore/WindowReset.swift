// Ported from CooldownMac sources/ccooldown-core/WindowReset.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// One rule, per window, for "this window's reset has passed since its figure was read"
/// (seat reconciliation S3, 2026-09-27 — shared by CCM-136 [Readout]'s `RefreshPolicy` and the
/// widget redesign's CCM-138 [Readout] display).
///
/// Per window, not per account: a reading obtained *after* a reset belongs to the new window
/// and must never be demoted, and one expired window must not demote another that is still
/// valid (Astra, widget round 1, point 3).
public enum WindowReset {

    /// True when the window reports a reset time that is now in the past, and the figure shown
    /// for it was read at or before that reset — i.e. the figure is the previous window's.
    /// A missing reading time counts as "before", so an old snapshot with no provenance can
    /// still be labelled honestly.
    public static func passed(resetsAtMs: Int64?, sourceAtMs: Int64?, nowMs: Int64) -> Bool {
        guard let resetsAtMs, resetsAtMs <= nowMs else { return false }
        return (sourceAtMs ?? 0) <= resetsAtMs
    }

    /// Whether two `resets_at` readings name the same window instance — sub-second drift poll to poll
    /// is the same window, a full window later is not (spec core §8 reset detection; fixture
    /// `usage/resets-at-drift.jsonl`). `0` means "nothing recorded yet" and matches nothing.
    /// Moved here from the Mac's `PaceAlertPlan.sameWindow`, which is a desktop feature.
    public static func sameWindow(_ a: Int64, _ b: Int64, windowLengthMs: Int64) -> Bool {
        a != 0 && b != 0 && abs(a - b) <= Projection.tolerance(windowLengthMs: windowLengthMs)
    }
}
