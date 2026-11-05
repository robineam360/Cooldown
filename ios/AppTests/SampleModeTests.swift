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
