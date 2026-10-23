// Ported from CooldownMac tests/ccooldown-core-tests/PaceSeverityTests.swift at 2bfbdfd (CCRM-94 (iOS App)).
import Foundation
import Testing

@testable import CooldownCore

/// The ladder's rungs and thresholds are pinned by the shared fixtures
/// (`projection/cases.json` → `severity`, asserted in `ContractTests`), so this file covers
/// what a fixture can't: the floor arithmetic, the ordering CCM-19 [Alerts] will lean on,
/// the copy, and the coupling between the chart and the line — the two surfaces that used
/// to be free to disagree.

private let hour: Int64 = 3_600_000
private let now: Int64 = 1_785_000_000_000
private let utc = TimeZone(identifier: "UTC")!

// MARK: - The floors

@Test("the young-window floor scales with the period and bottoms out at a minute")
func youngFloorArithmetic() {
    // 1% of five hours is three minutes.
    #expect(Projection.paceMinElapsedMs(windowLengthMs: HistoryStats.sessionMs) == 3 * 60_000)
    // 1% of a week is 1h 41m.
    #expect(Projection.paceMinElapsedMs(windowLengthMs: HistoryStats.weekMs) == 6_048_000)
    // 1% of 90 minutes is 54s, so the floor takes over.
    #expect(Projection.paceMinElapsedMs(windowLengthMs: 90 * 60_000) == 60_000)
}

@Test("elapsed never goes negative, because resets_at drifts forward")
func elapsedClamps() {
    let len = HistoryStats.sessionMs
    // A window whose reset is a full length away plus drift: elapsed would be negative,
    // and a negative must not read as "old enough" via a sign error.
    #expect(Projection.elapsedMs(resetsAtMs: now + len + 60_000, windowLengthMs: len, nowMs: now) == 0)
    #expect(Projection.elapsedMs(resetsAtMs: now + 2 * hour, windowLengthMs: len, nowMs: now) == 3 * hour)
}

@Test("the ladder is ordered, so a later escalation can be compared not enumerated")
func ladderOrders() {
    #expect(Projection.PaceSeverity.untracked < .healthy)
    #expect(Projection.PaceSeverity.healthy < .close)
    #expect(Projection.PaceSeverity.close < .runningOut)
    #expect(Projection.PaceSeverity.runningOut < .spent)
}

// MARK: - The three silences

@Test("each suppression reason has its own sentence")
func suppressionCopy() {
    let all: [Projection.PaceSuppression] = [.youngWindow, .tooLittleUsage, .noProjection]
    #expect(Set(all.map(\.explanation)).count == 3, "three reasons, three sentences")
    #expect(all.allSatisfy { !$0.explanation.isEmpty })
    // A reader shown "too early" hours into a window would rightly call it a bug.
    #expect(Projection.PaceSuppression.youngWindow.explanation.contains("Too early"))
    #expect(Projection.PaceSuppression.tooLittleUsage.explanation.contains("Not enough usage"))
    // Widget audit P17 / S2: one no-pace sentence, shared with the chart's empty state.
    #expect(Projection.PaceSuppression.noProjection.explanation
            == "Not enough readings yet to project a pace")
}

// MARK: - Tone
// Ported with the app's theme (CCRM-94 (iOS App) UI step): it checks the Mac popover's colours.

// MARK: - Chart and line, one ladder
// Ported with the app's chart (CCRM-94 (iOS App) UI step): those tests drive the Mac popover's
// PaceChartBuilder, which is presentation, not core.
