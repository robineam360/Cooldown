import CooldownCore
import Foundation
import Testing

@testable import Cooldown

/// CCRM-94 (iOS App) parity wireframes rev B §7: the reviewer sample data touches nothing real.
@Suite("Sample data (parity rev B)")
struct SampleModeTests {
    @Test("the sample model is the demo: three made-up accounts, nothing written to the real store, Prefs or Keychain")
    @MainActor func isolated() {
        let before = Prefs.lastAccountKey
        let realBefore = AccountsStore(storage: Shared.storage).load()
        let model = AppModel(demo: .standard)
        model.isSample = true
        #expect(model.isDemo && model.isSample && model.accounts.count == 3)
        for a in model.accounts { model.selectedKey = a.key }
        model.rename(model.accounts[0].key, to: "Renamed in the sample")
        #expect(Prefs.lastAccountKey == before)
        #expect(AccountsStore(storage: Shared.storage).load() == realBefore)
        let creds = CredentialStore.shared(accessGroup: Shared.keychainGroup)
        for key in ["teams", "product", "personal"] { #expect(creds.load(profileKey: key) == nil) }
    }

    @Test("the real model is not a sample, and the entry copy is Fable's")
    @MainActor func flags() {
        #expect(!AppModel(demo: .standard).isSample)
        #expect(SampleRules.entryTitle == "Try with sample data")
        #expect(SampleRules.entryDetail == "Three made-up accounts to look around. Nothing is saved or sent.")
        #expect(SampleRules.accountWord == "Sample")
    }

    @Test("the share card carries the sample band only for sample data")
    @MainActor func shareBand() {
        let model = AppModel(demo: .standard)
        #expect(model.isSample == false)   // the screenshot demo is not the sample mode: no band on its share card
    }
}

/// Owner's call 2026-11-05: the sample data saves nothing at all, display settings included.
@Suite("Sample store (saves nothing)", .serialized)
struct SampleStoreTests {
    @Test("begin seeds both suites from the real settings, redirects Prefs, and end leaves the real values untouched")
    func beginEnd() {
        let realGroup = UserDefaults(suiteName: Shared.appGroup) ?? .standard
        let realStandard = UserDefaults.standard
        let gKey = "zz.sample.test.group", sKey = "zz.sample.test.standard"
        realGroup.set("real-g", forKey: gKey); realStandard.set("real-s", forKey: sKey)
        defer { realGroup.removeObject(forKey: gKey); realStandard.removeObject(forKey: sKey); SampleStore.end() }

        SampleStore.begin()
        #expect(SampleStore.active)
        // The sample starts from the user's own settings…
        #expect(Prefs.defaults.string(forKey: gKey) == "real-g")
        #expect(Prefs.standard.string(forKey: sKey) == "real-s")
        // …and what it changes goes to the throwaway copies.
        Prefs.defaults.set("sample-g", forKey: gKey); Prefs.standard.set("sample-s", forKey: sKey)
        Prefs.defaults.set("only-in-sample", forKey: "zz.sample.new")
        SampleStore.end()

        #expect(!SampleStore.active)
        #expect(realGroup.string(forKey: gKey) == "real-g")
        #expect(realStandard.string(forKey: sKey) == "real-s")
        #expect(realGroup.object(forKey: "zz.sample.new") == nil)
        #expect(Prefs.defaults.string(forKey: gKey) == "real-g")
        #expect(SampleStore.group.object(forKey: gKey) == nil && SampleStore.standard.object(forKey: sKey) == nil)
    }

    @Test("wipe removes the throwaway suites (also run at launch after a kill)")
    func wipe() {
        SampleStore.group.set("x", forKey: "k")
        SampleStore.wipe()
        #expect(SampleStore.group.object(forKey: "k") == nil)
    }

    @Test("Prefs hands out the sample suites only while the sample is active")
    func pick() {
        let a = UserDefaults(suiteName: "cooldown.test.pick.a")!, b = UserDefaults(suiteName: "cooldown.test.pick.b")!
        #expect(Prefs.pick(active: false, real: a, sample: b) === a)
        #expect(Prefs.pick(active: true, real: a, sample: b) === b)
        #expect(SampleStore.active == false)
    }
}
