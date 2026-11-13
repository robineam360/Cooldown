import Testing

@testable import Cooldown

/// CCBG-99 (Live Activity 5h Cut): the single Live Activity's 5h line drops whole clauses, never a mid-phrase cut.
@Suite("Live Activity 5h line")
struct LiveFiveLineTests {
    @Test("on track: whole, the fold short, the countdown alone")
    func onTrack() {
        #expect(PanelLines.fiveVariants(countdown: "resets in 2h 19m", fold: "on track for ~64%")
                == ["resets in 2h 19m · on track for ~64%", "resets in 2h 19m · ~64%", "resets in 2h 19m"])
    }

    @Test("runs out: whole, the fold short, then the warning alone")
    func runsOut() {
        #expect(PanelLines.fiveVariants(countdown: "resets in 2h 19m", fold: "runs out ~3:25 PM")
                == ["resets in 2h 19m · runs out ~3:25 PM", "resets in 2h 19m · out ~3:25 PM", "runs out ~3:25 PM"])
    }

    @Test("no fold: the countdown alone")
    func noFold() {
        #expect(PanelLines.fiveVariants(countdown: "resets in 2h 19m", fold: nil) == ["resets in 2h 19m"])
    }
}

/// CCBG-102 (Number Footer Cut): the small Number widget's 5h footer drops its second part whole, never mid-clock.
@Suite("Number footer")
struct NumberFooterTests {
    @Test("countdown first: whole, then the countdown alone")
    func countdownFirst() {
        #expect(CardContent.numberFooterVariants(left: "4h 25m", clock: "10:41 PM", clockFirst: false)
                == ["Resets in 4h 25m · 10:41 PM", "Resets in 4h 25m"])
    }

    @Test("clock first: whole, then the clock alone")
    func clockFirst() {
        #expect(CardContent.numberFooterVariants(left: "4h 25m", clock: "10:41 PM", clockFirst: true)
                == ["Resets at 10:41 PM · in 4h 25m", "Resets at 10:41 PM"])
    }
}

