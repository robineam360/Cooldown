import Foundation
import Testing

@testable import CooldownCore

/// spec/core API-CONTRACT §11 (Robin, 2026-11-02): every case in `spec/core/fixtures/plans/cases.json`, the same file
/// Android's PlanNamesTest reads.
@Suite("plan names")
struct PlanNamesTests {
    private func list(_ key: String) throws -> [[String: Any]] {
        try #require(try Fixtures.json("plans/cases.json")[key] as? [[String: Any]])
    }

    @Test("Claude, every core case")
    func claude() throws {
        for c in try list("claude") {
            let label = PlanTag.label(organizationType: c.str("organizationType"), seatTier: c.str("seatTier"),
                                      hasMax: c.flag("hasMax") ?? false, hasPro: c.flag("hasPro") ?? false)
                .map { PlanNames.claude($0, rateLimitTier: c.str("rateLimitTier")) }
            #expect(label == c.str("expected"), "\(c)")
        }
    }

    @Test("ChatGPT, every core case")
    func chatgpt() throws {
        for c in try list("chatgpt") {
            #expect(PlanNames.chatgpt(c.str("planType") ?? "") == c.str("expected"), "\(c)")
            #expect(PlanTag.fromChatGPT(planType: c.str("planType"))?.plan == c.str("expected"))
        }
    }

    @Test("Gemini, every core case")
    func gemini() throws {
        for c in try list("gemini") {
            #expect(PlanNames.gemini(paidTierId: c.str("paidTierId"), paidTierName: c.str("paidTierName")) == c.str("expected"), "\(c)")
        }
    }

    @Test("display re-maps stored plans per provider, never with a multiplier")
    func display() {
        #expect(PlanNames.display(.chatgpt, "Prolite") == "Pro 100")
        #expect(PlanNames.display(.claude, "Max", tier: "default_claude_max_20x") == "Ultra")
        #expect(PlanNames.display(.claude, "Team Premium", tier: "default_claude_max_5x") == "Team Premium")
        #expect(PlanNames.display(.antigravity, "Ultra 5x") == "Ultra 5x")
    }
}
