import CooldownCore
import Foundation
import Testing

/// CCRM-94 (iOS App): the Keychain credential store as it ships — in the shared group the app and its
/// widget both carry. Hosted by the app so the test has the app's keychain entitlement.
@Suite("Keychain credential store, shared group", .serialized)
struct KeychainStoreTests {
    // The host app's team prefix, as the app itself resolves it (Info.plist `AppIdentifierPrefix`).
    static var group: String {
        let prefix = Bundle.main.object(forInfoDictionaryKey: "AppIdentifierPrefix") as? String ?? ""
        return prefix + "com.robinrichardrajan.cooldown.shared"
    }
    // The shipping service name with a unique profile key, so a test run never touches a real account.
    let store = CredentialStore.shared(accessGroup: KeychainStoreTests.group)
    let key = "test-\(UUID().uuidString)"

    private func credentials(_ access: String) -> Credentials {
        Credentials(accessToken: access, refreshToken: "refresh-\(access)", expiresAtMs: 1_800_000_000_000)
    }

    @Test("save then load returns the same credentials")
    func roundTrip() throws {
        defer { store.clear(profileKey: key) }
        try store.save(credentials("a1"), profileKey: key)
        #expect(store.load(profileKey: key) == credentials("a1"))
    }

    @Test("a second save updates in place")
    func updateInPlace() throws {
        defer { store.clear(profileKey: key) }
        try store.save(credentials("a1"), profileKey: key)
        try store.save(credentials("a2"), profileKey: key)
        #expect(store.load(profileKey: key) == credentials("a2"))
    }

    @Test("clear removes the item, and a missing item reads as absent")
    func clearAndAbsent() throws {
        try store.save(credentials("a1"), profileKey: key)
        store.clear(profileKey: key)
        #expect(store.load(profileKey: key) == nil)
        #expect(store.loadResult(profileKey: key) == .absent)
    }

    @Test("profiles are separate items")
    func separateProfiles() throws {
        let other = key + "-other"
        defer { store.clear(profileKey: key); store.clear(profileKey: other) }
        try store.save(credentials("a1"), profileKey: key)
        try store.save(credentials("b1"), profileKey: other)
        #expect(store.load(profileKey: key) == credentials("a1"))
        #expect(store.load(profileKey: other) == credentials("b1"))
    }
}
