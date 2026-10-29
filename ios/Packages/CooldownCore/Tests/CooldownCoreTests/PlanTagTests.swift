// Ported from CooldownMac tests/ccooldown-core-tests/PlanTagTests.swift at 2bfbdfd (CCRM-94 (iOS App)).
import Foundation
import Testing

@testable import CooldownCore

// CCM-68 [Accounts]: `PlanTag.parse` against every plan value in contract/fixtures/profile,
// plus the unknown-shape case. CCM-67 [Readout]: the 403 body it must recognise as a plan
// refusal rather than some other forbidden.

@Test("each plan fixture parses to its label")
func planTagFixtures() throws {
    #expect(try PlanTag.parse(Fixtures.text("profile/free.json"))?.plan == "Free")
    #expect(try PlanTag.parse(Fixtures.text("profile/pro.json"))?.plan == "Pro")
    #expect(try PlanTag.parse(Fixtures.text("profile/max.json"))?.plan == "Max")
    #expect(try PlanTag.parse(Fixtures.text("profile/team-standard.json"))?.plan == "Team Standard")
    #expect(try PlanTag.parse(Fixtures.text("profile/team-premium.json"))?.plan == "Team Premium")
    #expect(try PlanTag.parse(Fixtures.text("profile/enterprise.json"))?.plan == "Enterprise")
}

@Test("the free fixture is the one that blocks usage; nothing else does")
func planTagUsageBlocked() throws {
    let free = try PlanTag.parse(Fixtures.text("profile/free.json"))
    #expect(free?.usageBlocked == true)

    for name in ["pro", "max", "team-standard", "team-premium", "enterprise"] {
        let tag = try PlanTag.parse(Fixtures.text("profile/\(name).json"))
        #expect(tag?.usageBlocked == false, "\(name) must not read as blocked")
    }
}

@Test("the tier rides along, raw, for AccountsBuilder's multiplier")
func planTagTier() throws {
    #expect(try PlanTag.parse(Fixtures.text("profile/free.json"))?.tier == "default_claude_ai")
    #expect(try PlanTag.parse(Fixtures.text("profile/max.json"))?.tier == "default_claude_max_20x")
    #expect(try PlanTag.parse(Fixtures.text("profile/team-standard.json"))?.tier == "default_raven")
    #expect(try PlanTag.parse(Fixtures.text("profile/team-premium.json"))?.tier == "default_claude_max_5x")
}

@Test("garbage and an empty object parse to nil rather than a guessed plan")
func planTagUnknown() {
    #expect(PlanTag.parse("not json") == nil)
    #expect(PlanTag.parse("{}") == nil)
}

@Test("team seats: no seat is plain Team, an unnamed one is Premium")
func planTagTeamSeats() {
    #expect(PlanTag.label(organizationType: "claude_team", seatTier: nil, hasMax: false, hasPro: false) == "Team")
    #expect(PlanTag.label(organizationType: "claude_team", seatTier: "team_standard", hasMax: false, hasPro: false)
        == "Team Standard")
    #expect(PlanTag.label(organizationType: "claude_team", seatTier: "some_other_name", hasMax: false, hasPro: false)
        == "Team Premium")
}

@Test("with no organization_type, the account flags decide, Max first")
func planTagNoOrgType() {
    #expect(PlanTag.label(organizationType: nil, seatTier: nil, hasMax: true, hasPro: true) == "Max")
    #expect(PlanTag.label(organizationType: nil, seatTier: nil, hasMax: false, hasPro: true) == "Pro")
    #expect(PlanTag.label(organizationType: nil, seatTier: nil, hasMax: false, hasPro: false) == nil)
}

@Test("an organisation type this build has never seen keeps its own name, capitalized once")
func planTagUnrecognizedOrgType() {
    // Matches Android exactly: only the first character of the whole string, not each word.
    #expect(PlanTag.label(organizationType: "claude_something_new", seatTier: nil, hasMax: false, hasPro: false)
        == "Something new")
    #expect(PlanTag.label(organizationType: "not_prefixed", seatTier: nil, hasMax: false, hasPro: false)
        == "Not prefixed")
}

@Test("the 403 plan refusal is recognised by its error code, not by status alone")
func planRefusalRecognition() throws {
    #expect(PlanTag.isPlanRefusal(try Fixtures.text("profile/403-plan-refusal.json")))
    #expect(!PlanTag.isPlanRefusal(#"{"type":"error","error":{"type":"permission_error","message":"nope"}}"#))
    #expect(!PlanTag.isPlanRefusal("<html>403</html>"))
}
