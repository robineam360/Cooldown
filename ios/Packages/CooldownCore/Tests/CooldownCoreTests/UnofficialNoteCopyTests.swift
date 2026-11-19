import Foundation
import Testing
@testable import CooldownCore

/// CCRM-120 (Unofficial Note): the note's words against `spec/mobile/fixtures/unofficial-note/cases.json`, the same
/// file Android's `UnofficialNoteCopyTest` reads.
@Suite struct UnofficialNoteCopyTests {
    let fx: [String: Any]
    init() throws { fx = try Fixtures.mobileJSON("unofficial-note/cases.json") }

    @Test func titleAndBodyForEveryProvider() throws {
        #expect(UnofficialNoteCopy.title == fx.str("title"))
        let companies = try #require(fx.dict("companies"))
        let template = try #require(fx.str("bodyTemplate"))
        for p in [Provider.claude, .chatgpt, .antigravity] {
            let company = try #require(companies.str(p.rawValue))
            #expect(UnofficialNoteCopy.company(p) == company)
            #expect(UnofficialNoteCopy.body(p) == template.replacingOccurrences(of: "{company}", with: company))
        }
    }

    @Test func addFootnote() {
        #expect(UnofficialNoteCopy.addFootnote == fx.str("iosAddFootnote"))
    }

    @Test func chatGPTStages() throws {
        let stages = try #require(fx.dict("chatgptStages"))
        for s in DeviceCodeStage.allCases {
            let key = "\(s)"
            #expect(UnofficialNoteCopy.shows(on: s) == (stages[key] as? Bool), "\(key)")
        }
    }

    @Test func neverTheRuledOutWords() throws {
        let never = try #require(fx.dict("_meta")?["neverWords"] as? [String])
        let all = ([Provider.claude, .chatgpt, .antigravity].map(UnofficialNoteCopy.body) + [UnofficialNoteCopy.title, UnofficialNoteCopy.addFootnote])
            .joined(separator: " ").lowercased()
        let words = Set(all.split(whereSeparator: { !$0.isLetter }).map(String.init))
        for w in never { #expect(!words.contains(w), "\(w)") }
    }
}
