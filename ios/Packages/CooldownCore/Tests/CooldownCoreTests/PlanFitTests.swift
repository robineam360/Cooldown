// Ported from Android PlanFitTest.kt and PlanFitCopyTest.kt (CCRM-70 (Plan Fit), CCRM-94 (iOS App)).
import Foundation
import Testing

@testable import CooldownCore

private let weekMs = HistoryStats.weekMs
private let now: Int64 = 2_000_000_000_000

private func w(_ weeksAgo: Int, _ peak: Double, hit: Bool = false, plan: String? = nil,
               cap: String? = nil) -> PlanFit.WeeklyEntry {
    PlanFit.WeeklyEntry(resetAtMs: now - Int64(weeksAgo) * weekMs, peakPct: peak, hitLimit: hit,
                        plan: plan, modelCap: cap)
}

private func s(_ ago: Int, hit: Bool) -> HistoryStats.Record {
    HistoryStats.Record(kind: HistoryStats.sessionKind, resetAtMs: now - Int64(ago) * weekMs,
                        peakPct: hit ? 100.0 : 50.0, hitLimit: hit)
}

private func compute(_ weekly: [PlanFit.WeeklyEntry], _ sessions: [HistoryStats.Record] = []) -> PlanFit.Reading {
    PlanFit.evaluate(weekly: weekly, sessions: sessions, nowMs: now)
}

private func fitted(_ weekly: [PlanFit.WeeklyEntry], _ sessions: [HistoryStats.Record] = []) -> PlanFit.Fitted {
    guard case .fitted(let f) = compute(weekly, sessions) else {
        Issue.record("expected a fitted reading")
        return PlanFit.Fitted(verdict: .fits, currentPlan: nil, weeks: [], recordedWeeks: 0,
                              periodTargetWeeks: 8, cappedCount: 0, heavyCount: 0, middleCount: 0,
                              lightCount: 0, fiveHourCapHits: 0, planChange: nil)
    }
    return f
}

private func notEnough(_ weekly: [PlanFit.WeeklyEntry]) -> PlanFit.NotEnoughData {
    guard case .notEnoughData(let r) = compute(weekly) else {
        Issue.record("expected notEnoughData")
        return PlanFit.NotEnoughData(recordedWeeks: -1, currentPlan: nil, planChange: nil)
    }
    return r
}

@Suite("PlanFit verdicts")
struct PlanFitVerdictTests {
    // --- not enough data ---

    @Test func noRecordsAtAllYieldsNotEnoughDataWithZeroRecordedWeeks() {
        let r = notEnough([])
        #expect(r.recordedWeeks == 0)
        #expect(r.currentPlan == nil)
        #expect(r.planChange == nil)
    }

    @Test func fewerThanFourRecordedWeeksNeverYieldsAVerdict() {
        for n in 1...3 {
            let r = notEnough((0..<n).map { w($0, 40.0) })
            #expect(r.recordedWeeks == n, "N=\(n)")
        }
    }

    @Test func exactlyFourRecordedWeeksIsEnoughForAVerdict() {
        let r = fitted((0...3).map { w($0, 40.0) })
        #expect(r.recordedWeeks == 4)
        #expect(r.verdict == .fits)
    }

    // --- FITS ---

    @Test func fitsVerdictForAnUnremarkableEightWeeks() {
        let peaks = [40.0, 55.0, 30.0, 60.0, 45.0, 65.0, 35.0, 50.0]
        let r = fitted(peaks.enumerated().map { w($0.offset, $0.element) })
        #expect(r.recordedWeeks == 8)
        #expect(r.cappedCount == 0)
        #expect(r.heavyCount == 0)
        #expect(r.middleCount == 8)
        #expect(r.lightCount == 0)
        #expect(r.verdict == .fits)
    }

    @Test func aHolidayFortnightDoesNotFlipAnOtherwiseFittingAccount() {
        let peaks = [10.0, 50.0, 55.0, 15.0, 60.0, 45.0, 50.0, 65.0]
        let r = fitted(peaks.enumerated().map { w($0.offset, $0.element) })
        #expect(r.lightCount == 2)
        #expect(r.verdict == .fits)
    }

    @Test func aSingleCrunchWeekDoesNotReachRunsOut() {
        let peaks = [100.0, 40.0, 50.0, 30.0, 45.0, 55.0, 35.0, 50.0]
        let r = fitted(peaks.enumerated().map { w($0.offset, $0.element, hit: $0.element >= 100.0) })
        #expect(r.cappedCount == 1)
        #expect(r.verdict == .fits)
    }

    // --- RUNS_OUT boundaries ---

    @Test func runsOutFiresAtExactlyCeilNOver3CappedWeeks() {
        let r = fitted((0...7).map { w($0, $0 < 3 ? 100.0 : 40.0, hit: $0 < 3) })
        #expect(r.cappedCount == 3)
        #expect(r.verdict == .runsOut)
    }

    @Test func oneCappedWeekShortOfTheThresholdDoesNotTriggerRunsOut() {
        let r = fitted((0...7).map { w($0, $0 < 2 ? 100.0 : 40.0, hit: $0 < 2) })
        #expect(r.cappedCount == 2)
        #expect(r.verdict == .fits)
    }

    @Test func runsOutFiresAtExactlyCeil2NOver3CappedPlusHeavyWeeks() {
        let r = fitted((0...7).map { i in
            if i == 0 { return w(i, 100.0, hit: true) }
            if (1...5).contains(i) { return w(i, 80.0) }
            return w(i, 20.0)
        })
        #expect(r.cappedCount == 1)
        #expect(r.heavyCount == 5)
        #expect(r.verdict == .runsOut)
    }

    @Test func oneHeavyWeekShortOfTheCeil2NOver3ThresholdDoesNotTriggerRunsOut() {
        let r = fitted((0...7).map { i in
            if i == 0 { return w(i, 100.0, hit: true) }
            if (1...4).contains(i) { return w(i, 80.0) }
            return w(i, 20.0)
        })
        #expect(r.cappedCount == 1)
        #expect(r.heavyCount == 4)
        #expect(r.verdict == .fits)
    }

    // --- SMALL_PART boundaries ---

    @Test func smallPartFiresAtExactlyCeil3NOver4LightWeeksOf8() {
        let r = fitted((0...7).map { w($0, $0 < 6 ? 10.0 : 50.0) })
        #expect(r.lightCount == 6)
        #expect(r.cappedCount == 0)
        #expect(r.verdict == .smallPart)
    }

    @Test func oneLightWeekShortOfTheThresholdOf8DoesNotTriggerSmallPart() {
        let r = fitted((0...7).map { w($0, $0 < 5 ? 10.0 : 50.0) })
        #expect(r.lightCount == 5)
        #expect(r.verdict == .fits)
    }

    @Test func smallPartThresholdAtN6RoundsUpTo5() {
        let r = fitted((0...5).map { w($0, $0 < 5 ? 10.0 : 50.0) })
        #expect(r.recordedWeeks == 6)
        #expect(r.lightCount == 5)
        #expect(r.verdict == .smallPart)
    }

    @Test func aCappedWeekBlocksSmallPartEvenWithAQualifyingLightCount() {
        let r = fitted((0...7).map { $0 == 0 ? w($0, 100.0, hit: true) : w($0, 10.0) })
        #expect(r.lightCount == 7)
        #expect(r.cappedCount == 1)
        #expect(r.verdict == .fits)
    }

    // --- IDLE ---

    @Test func idleWhenEveryWeeksPeakIsZero() {
        let r = fitted((0...7).map { w($0, 0.0) })
        #expect(r.lightCount == 8)
        #expect(r.verdict == .idle)
    }

    @Test func idleReachableWithAsFewAsTheMinimumFourWeeks() {
        let r = fitted((0...3).map { w($0, 0.0) })
        #expect(r.verdict == .idle)
    }

    // --- per-model weekly cap ---

    @Test func aWeekIsCappedViaTheModelCapTagEvenWhenThePoolPeakIsLow() {
        let record = w(0, 10.0, hit: false, cap: "Opus")
        let r = fitted([record] + (1...3).map { w($0, 15.0) })
        let opusWeek = r.weeks.first { $0.resetAtMs == record.resetAtMs }
        #expect(opusWeek?.band == .capped)
        #expect(opusWeek?.modelCap == "Opus")
    }

    @Test func opusCappedWeeksAloneCanReachRunsOutDespiteALowPoolPeak() {
        let r = fitted((0...7).map { $0 < 5 ? w($0, 10.0, cap: "Opus") : w($0, 20.0) })
        #expect(r.cappedCount == 5)
        #expect(r.verdict == .runsOut)
        #expect(r.weeks.filter { $0.band == .capped }.allSatisfy { $0.modelCap == "Opus" })
    }

    // --- gaps ---

    @Test func gapsInTheLogShrinkNWithoutBreakingTheReading() {
        let r = fitted([0, 2, 4, 6, 9, 12].map { w($0, 40.0) })
        #expect(r.recordedWeeks == 6)
        #expect(r.verdict == .fits)
    }

    // --- plan changes ---

    @Test func aPlanChangeInsideThePeriodRestartsItAndCanLeaveTooFewWeeks() throws {
        let oldPlan = (3...7).map { w($0, 90.0, plan: "Pro") }
        let newPlan = (0...2).map { w($0, 40.0, plan: "Max") }
        let r = notEnough(oldPlan + newPlan)
        #expect(r.recordedWeeks == 3)
        #expect(r.currentPlan == "Max")
        let change = try #require(r.planChange)
        #expect(change.previousPlan == "Pro")
        #expect(change.weeksAgo == 3)
    }

    @Test func aPlanChangeInsideThePeriodStillYieldsAVerdictOncePastFourWeeks() throws {
        let oldPlan = (5...7).map { w($0, 100.0, hit: true, plan: "Pro") }
        let newPlan = (0...4).map { w($0, 40.0, plan: "Max") }
        let r = fitted(oldPlan + newPlan)
        #expect(r.recordedWeeks == 5)
        #expect(r.currentPlan == "Max")
        #expect(r.cappedCount == 0)
        #expect(r.verdict == .fits)
        let change = try #require(r.planChange)
        #expect(change.previousPlan == "Pro")
        #expect(change.weeksAgo == 5)
    }

    @Test func untaggedRecordsAreTreatedAsThePlanFirstObservedAfterTheUpdate() {
        let untagged = (4...7).map { w($0, 30.0) }
        let tagged = (0...3).map { w($0, 30.0, plan: "Max") }
        let r = fitted(untagged + tagged)
        #expect(r.recordedWeeks == 8)
        #expect(r.currentPlan == "Max")
        #expect(r.planChange == nil)
    }

    @Test func anAccountWithNoPlanTagsAnywhereStillReads() {
        let r = fitted((0...7).map { w($0, 30.0) })
        #expect(r.recordedWeeks == 8)
        #expect(r.currentPlan == nil)
        #expect(r.planChange == nil)
        #expect(r.verdict == .fits)
    }

    // --- 5h cap hits ---

    @Test func fiveHourCapHitsAreCountedOnlyInsideThePeriod() {
        let weekly = (0...7).map { w($0, 40.0) }
        let sessions = [
            s(1, hit: true), s(2, hit: true), s(3, hit: true),
            s(2, hit: false),
            s(50, hit: true),
        ]
        #expect(fitted(weekly, sessions).fiveHourCapHits == 3)
    }

    @Test func noSessionRecordsIsAPlainZero() {
        #expect(fitted((0...7).map { w($0, 40.0) }).fiveHourCapHits == 0)
    }

    // --- band boundaries ---

    @Test func bandThresholdsAreInclusiveAt25_75And100ExclusiveJustBelow() {
        let r = fitted([
            w(0, 24.9), w(1, 25.0), w(2, 74.9), w(3, 75.0),
            w(4, 99.9), w(5, 100.0, hit: true), w(6, 10.0), w(7, 50.0),
        ])
        #expect(r.lightCount == 2)
        #expect(r.middleCount == 3)
        #expect(r.heavyCount == 2)
        #expect(r.cappedCount == 1)
    }

    @Test func weeksAreExposedOldestToNewest() {
        let r = fitted((0...7).map { w($0, 40.0 + Double($0)) })
        let resetAts = r.weeks.map(\.resetAtMs)
        #expect(resetAts == resetAts.sorted())
        #expect(!r.weeks.isEmpty)
    }

    // --- iOS record entry point ---

    @Test func historyRecordsSplitByKind() {
        var records = (0...7).map {
            HistoryStats.Record(kind: HistoryStats.weeklyKind, resetAtMs: now - Int64($0) * weekMs,
                                peakPct: 40.0, hitLimit: false)
        }
        records.append(s(1, hit: true))
        guard case .fitted(let f) = PlanFit.evaluate(records: records, nowMs: now) else {
            Issue.record("expected fitted")
            return
        }
        #expect(f.recordedWeeks == 8)
        #expect(f.fiveHourCapHits == 1)
    }
}

private func reading(_ weekly: [PlanFit.WeeklyEntry], _ sessions: [HistoryStats.Record] = []) -> PlanFit.Reading {
    compute(weekly, sessions)
}

@Suite("PlanFitCopy")
struct PlanFitCopyTests {
    // --- state a ---

    @Test func stateA_runsOutGenericCap5hLinePresent() {
        let weekly = (0...7).map { w($0, $0 < 3 ? 100.0 : 60.0, hit: $0 < 3, plan: "Max 5x") }
        let sessions = (0..<14).map { s($0 % 9, hit: true) }
        let lines = PlanFitCopy.build(reading(weekly, sessions))
        #expect(lines.header == "PLAN FIT · MAX 5X · LAST 8 WEEKS")
        #expect(lines.reading == "This plan runs out on you.")
        #expect(lines.evidence == "Hit the cap in 3 of 8 weeks")
        #expect(lines.fiveHourLine == "5h cap hit 14 times")
    }

    @Test func fiveHourLineIsSingularAtExactlyOneHit() {
        let weekly = (0...7).map { w($0, $0 < 3 ? 100.0 : 60.0, hit: $0 < 3) }
        let lines = PlanFitCopy.build(reading(weekly, [s(0, hit: true)]))
        #expect(lines.fiveHourLine == "5h cap hit 1 time")
    }

    // --- state b ---

    @Test func stateB_fitsCappedOnce() {
        let peaks = [38.0, 55.0, 62.0, 100.0, 48.0, 70.0, 33.0, 58.0]
        let weekly = peaks.enumerated().map { w($0.offset, $0.element, hit: $0.element >= 100.0, plan: "Pro") }
        let lines = PlanFitCopy.build(reading(weekly))
        #expect(lines.header == "PLAN FIT · PRO · LAST 8 WEEKS")
        #expect(lines.reading == "This plan fits how you use it.")
        #expect(lines.evidence == "Between 25% and 75% most weeks · capped once")
        #expect(lines.fiveHourLine == nil)
    }

    @Test func fitsWithZeroCappedWeeksReadsNeverCapped() {
        let lines = PlanFitCopy.build(reading((0...7).map { w($0, 40.0, plan: "Pro") }))
        #expect(lines.evidence == "Between 25% and 75% most weeks · never capped")
    }

    @Test func fitsWithMultipleCappedWeeksReadsCappedNTimes() {
        let weekly = (0...7).map { w($0, $0 < 2 ? 100.0 : 45.0, hit: $0 < 2, plan: "Pro") }
        let lines = PlanFitCopy.build(reading(weekly))
        #expect(lines.evidence == "Between 25% and 75% most weeks · capped 2 times")
    }

    // --- state c ---

    @Test func stateC_smallPartNeverCapped() {
        let peaks = [10.0, 12.0, 8.0, 30.0, 18.0, 22.0, 35.0, 15.0]
        let weekly = peaks.enumerated().map { w($0.offset, $0.element, plan: "Max 5x") }
        let lines = PlanFitCopy.build(reading(weekly))
        #expect(lines.header == "PLAN FIT · MAX 5X · LAST 8 WEEKS")
        #expect(lines.reading == "You use a small part of this plan.")
        #expect(lines.evidence == "Under 25% in 6 of 8 weeks · never capped")
    }

    // --- state d ---

    @Test func stateD_firstRunUsesTheFallbackPlanAndDropsThePeriod() {
        let lines = PlanFitCopy.build(reading([]), fallbackPlan: "Max 5x")
        #expect(lines.header == "PLAN FIT · MAX 5X")
        #expect(lines.reading == "First week in progress.")
        #expect(lines.evidence == "The reading starts after four weeks.")
        #expect(lines.fiveHourLine == nil)
    }

    @Test func stateD_noFallbackPlanKnownOmitsThePlanTokenToo() {
        let lines = PlanFitCopy.build(reading([]), fallbackPlan: nil)
        #expect(lines.header == "PLAN FIT")
    }

    // --- state e ---

    @Test func stateE_twoOfFourWeeksRecorded() {
        let weekly = (0...1).map { w($0, 30.0, plan: "Max 5x") }
        let lines = PlanFitCopy.build(reading(weekly), fallbackPlan: "irrelevant here")
        #expect(lines.header == "PLAN FIT · MAX 5X · 2 WEEKS")
        #expect(lines.reading == "2 of 4 weeks recorded.")
        #expect(lines.evidence == "The reading starts after four weeks.")
    }

    @Test func singularWeekInTheHeaderAndReadingAtExactlyOneRecordedWeek() {
        let lines = PlanFitCopy.build(reading([w(0, 30.0, plan: "Max 5x")]))
        #expect(lines.header == "PLAN FIT · MAX 5X · 1 WEEK")
        #expect(lines.reading == "1 of 4 weeks recorded.")
    }

    // --- state f ---

    @Test func stateF_planChangedThreeWeeksOnTheNewPlan() {
        let oldPlan = (3...7).map { w($0, 90.0, plan: "Max 20x") }
        let newPlan = (0...2).map { w($0, 20.0, plan: "Max 5x") }
        let lines = PlanFitCopy.build(reading(oldPlan + newPlan))
        #expect(lines.header == "PLAN FIT · MAX 5X · 3 WEEKS")
        #expect(lines.reading == "Changed from Max 20x 3 weeks ago.")
        #expect(lines.evidence == "The reading restarts after four weeks on the new plan.")
    }

    @Test func planChangeReadingIsSingularAtOneWeekAgo() {
        let oldPlan = (1...7).map { w($0, 90.0, plan: "Max 20x") }
        let lines = PlanFitCopy.build(reading(oldPlan + [w(0, 20.0, plan: "Max 5x")]))
        #expect(lines.reading == "Changed from Max 20x 1 week ago.")
    }

    // --- state g ---

    @Test func stateG_planUnknownDropsThePlanTokenEvenWithAFallback() {
        let peaks = [40.0, 54.0, 62.0, 100.0, 48.0, 70.0, 33.0, 58.0]
        let weekly = peaks.enumerated().map { w($0.offset, $0.element, hit: $0.element >= 100.0) }
        let lines = PlanFitCopy.build(reading(weekly), fallbackPlan: "Max 5x")
        #expect(lines.header == "PLAN FIT · LAST 8 WEEKS")
        #expect(lines.reading == "This plan fits how you use it.")
    }

    // --- state h ---

    @Test func stateH_idleAccount() {
        let lines = PlanFitCopy.build(reading((0...7).map { w($0, 0.0, plan: "Pro") }))
        #expect(lines.header == "PLAN FIT · PRO · LAST 8 WEEKS")
        #expect(lines.reading == "No use recorded on this account.")
        #expect(lines.evidence == "8 idle weeks.")
    }

    // --- state i ---

    @Test func stateI_cappedEveryWeek() {
        let lines = PlanFitCopy.build(reading((0...7).map { w($0, 100.0, hit: true, plan: "Max 5x") }))
        #expect(lines.reading == "This plan runs out on you.")
        #expect(lines.evidence == "Hit the cap in 8 of 8 weeks")
    }

    // --- state j (needs explicit model-cap input; iOS cannot supply it yet) ---

    @Test func stateJ_opusBoundMaxNamesTheModelInTheCapLine() {
        let weekly = (0...7).map {
            $0 % 2 == 0 ? w($0, 50.0, plan: "Max 20x", cap: "Opus") : w($0, 55.0, plan: "Max 20x")
        }
        let lines = PlanFitCopy.build(reading(weekly))
        #expect(lines.header == "PLAN FIT · MAX 20X · LAST 8 WEEKS")
        #expect(lines.reading == "This plan runs out on you.")
        #expect(lines.evidence == "Hit the Opus cap in 4 of 8 weeks")
    }

    @Test func mixedModelCapsAcrossCappedWeeksFallBackToTheGenericCapWording() {
        let weekly = (0...7).map { i -> PlanFit.WeeklyEntry in
            if i < 3 { return w(i, 50.0, cap: "Opus") }
            if i == 3 { return w(i, 100.0, hit: true) }
            return w(i, 55.0)
        }
        let lines = PlanFitCopy.build(reading(weekly))
        guard case .fitted(let f) = reading(weekly) else {
            Issue.record("expected fitted")
            return
        }
        #expect(f.verdict == .runsOut)
        #expect(lines.evidence == "Hit the cap in 4 of 8 weeks")
    }

    // --- state k ---

    @Test func stateK_gapsUseTheRecordedWording() {
        let peaks = [14.0, 12.0, 8.0, 30.0, 15.0, 9.0]
        let weekly = [0, 1, 2, 4, 5, 7].enumerated().map { w($0.element, peaks[$0.offset], plan: "Max 5x") }
        let lines = PlanFitCopy.build(reading(weekly))
        #expect(lines.header == "PLAN FIT · MAX 5X · LAST 8 WEEKS")
        #expect(lines.evidence == "Under 25% in 5 of 6 recorded weeks · never capped")
    }

    // --- state l ---

    @Test func stateL_noSessionRecordsMeansNoFiveHourLine() {
        let peaks = [44.0, 58.0, 64.0, 100.0, 50.0, 72.0, 35.0, 60.0]
        let weekly = peaks.enumerated().map { w($0.offset, $0.element, hit: $0.element >= 100.0, plan: "Plus") }
        let lines = PlanFitCopy.build(reading(weekly))
        #expect(lines.header == "PLAN FIT · PLUS · LAST 8 WEEKS")
        #expect(lines.fiveHourLine == nil)
    }

    // --- compact ---

    @Test func compactWidthDropsTheHeaderPeriodAndFoldsItIntoTheEvidenceLine() {
        let peaks = [10.0, 12.0, 8.0, 30.0, 18.0, 22.0, 35.0, 15.0]
        let weekly = peaks.enumerated().map { w($0.offset, $0.element, plan: "Max 5x") }
        let lines = PlanFitCopy.build(reading(weekly), compact: true)
        #expect(lines.header == "PLAN FIT · MAX 5X")
        #expect(lines.evidence == "Under 25% in 6 of the last 8 weeks · never capped")
    }

    @Test func compactWidthStillDropsTheHeaderPeriodBelowTheFourWeekMinimum() {
        let weekly = (0...1).map { w($0, 30.0, plan: "Max 5x") }
        let lines = PlanFitCopy.build(reading(weekly), fallbackPlan: "Max 5x", compact: true)
        #expect(lines.header == "PLAN FIT · MAX 5X")
        #expect(lines.reading == "2 of 4 weeks recorded.")
    }

    // --- RUNS_OUT via heavy rule ---

    @Test func runsOutViaTheHeavyRuleNamesTheCeilingNotACapItNeverHit() {
        let weekly = (0...7).map { w($0, $0 < 6 ? 88.0 : 40.0, plan: "Max 5x") }
        let lines = PlanFitCopy.build(reading(weekly))
        #expect(lines.reading == "This plan runs out on you.")
        #expect(lines.evidence == "Capped or above 75% in 6 of 8 weeks")
    }
}
