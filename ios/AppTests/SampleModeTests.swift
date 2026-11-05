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
    /// Two throwaway "real" suites stand in for the App Group and the app's standard defaults.
    func realSuites() -> (group: UserDefaults, standard: UserDefaults, cleanup: () -> Void) {
        let g = "cooldown.test.real.group", s = "cooldown.test.real.standard"
        let group = UserDefaults(suiteName: g)!, standard = UserDefaults(suiteName: s)!
        group.removePersistentDomain(forName: g); standard.removePersistentDomain(forName: s)
        return (group, standard, { group.removePersistentDomain(forName: g); standard.removePersistentDomain(forName: s); SampleStore.wipe() })
    }

    @Test("the sample starts from the user's settings, and what it changes never reaches the real ones")
    func seedAndIsolate() {
        let r = realSuites(); defer { r.cleanup() }
        r.group.set("24", forKey: "display.timeFormat")
        r.standard.set("dark", forKey: "theme")
        // seed() reads the persistent domain by name; the test suites' domains are their own names.
        SampleStore.group.setPersistentDomain(r.group.persistentDomain(forName: "cooldown.test.real.group") ?? [:], forName: SampleStore.groupName)
        SampleStore.standard.setPersistentDomain(r.standard.persistentDomain(forName: "cooldown.test.real.standard") ?? [:], forName: SampleStore.standardName)
        #expect(SampleStore.group.string(forKey: "display.timeFormat") == "24")
        #expect(SampleStore.standard.string(forKey: "theme") == "dark")
        SampleStore.group.set("12", forKey: "display.timeFormat")
        SampleStore.standard.set("light", forKey: "theme")
        #expect(r.group.string(forKey: "display.timeFormat") == "24")
        #expect(r.standard.string(forKey: "theme") == "dark")
    }

    @Test("wipe removes the throwaway suites")
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
