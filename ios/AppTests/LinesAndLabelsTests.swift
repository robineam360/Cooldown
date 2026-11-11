import CooldownCore
import Foundation
import Testing

@testable import Cooldown

/// RUNBOOK Step I1 (Lines and labels): CCRM-106 (7d Chip), CCRM-107 (Day Grid) and CCRM-108 (Reset Named) on iOS —
/// wireframe `design/2026-11-10-v20-feedback-wireframes.html` rev B §2–§4.
@Suite("v2.0 feedback: 7d, the day grid, the reset named")
struct LinesAndLabelsTests {
    let now: Int64 = 1_790_000_000_000
    let utc = TimeZone(identifier: "UTC")!

    private func weekly(_ pct: Double?, elapsed: Double, title: String = "Weekly") -> WindowContent? {
        CardContent.window(title: title, window: UsageWindow(
            percent: pct, resetsAtMs: now + Int64((1 - elapsed) * Double(HistoryStats.weekMs)), serverSeverity: nil),
            kind: HistoryStats.weeklyKind, history: [], nowMs: now, use24h: false, timeZone: utc)
    }

    @Test("CCRM-106: the whole-week chip and the classic card's half read 7d; VoiceOver reads 7-day, all models")
    func sevenDay() {
        #expect(CardContent.sevenDayTitle == "7d")
        #expect(CardContent.spokenTitle("7d") == "7-day, all models")
        #expect(CardContent.spokenTitle("Fable") == "Fable")
    }

    @Test("CCRM-107: a weekly chart has seven columns, a 5h chart five, each with the column holding now")
    func grid() throws {
        let w = try #require(weekly(52, elapsed: 0.39)?.chart)
        #expect(w.grid == 7 && w.nowColumn == 3)
        let late = try #require(weekly(90, elapsed: 0.95)?.chart)
        #expect(late.nowColumn == 7)
        let five = try #require(CardContent.window(
            title: "5h", window: UsageWindow(percent: 34, resetsAtMs: now + Int64(0.48 * Double(HistoryStats.sessionMs)), serverSeverity: nil),
            kind: HistoryStats.sessionKind, history: [], nowMs: now, timeZone: utc)?.chart)
        #expect(five.grid == 5 && five.nowColumn == 3)
        // A window that has not started has no chart, so no grid.
        #expect(CardContent.window(title: "Weekly", window: UsageWindow(percent: 0, resetsAtMs: nil, serverSeverity: nil),
                                   kind: HistoryStats.weeklyKind, history: [], nowMs: now)?.chart == nil)
    }

    @Test("CCRM-108: every weekly pace line names the reset, and a reset days away drops to its weekday, never a bare clock")
    func resetNamed() throws {
        let line = try #require(weekly(40, elapsed: 0.6071)?.line)
        #expect(line.text().contains(" · resets "))
        #expect(line.weekday != nil)
        #expect(line.forms.count == 4)
        #expect(line.forms[2] == line.lead + PaceText.dot + (line.weekday ?? "?"))
        #expect(line.forms.last == line.lead)
        // The folded line prints the widest form; the drop is the surface's (FitLine).
        let c = try #require(weekly(40, elapsed: 0.6071))
        #expect(CardContent.compactLine(c) == line.text() && c.paceLineFull == line.text())
    }
}
