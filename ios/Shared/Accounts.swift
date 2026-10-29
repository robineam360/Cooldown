import CooldownCore
import Foundation

/// CCRM-94 (iOS App): one signed-in account as the app lists it. Order in the list is the user's order —
/// the same everywhere: tabs, sidebar, widgets, Live Activity (wireframe rev B, Settings).
struct Account: Codable, Identifiable, Equatable, Sendable {
    var key: String
    var label: String
    var provider: Provider
    var pinned = false
    /// When it was signed in here, for Details… (nil for accounts added before this was recorded).
    var addedAtMs: Int64? = nil
    var id: String { key }
}

struct AccountsStore: Sendable {
    let storage: Storage
    var file: URL { storage.file("accounts.json") }
    func load() -> [Account] {
        guard let data = try? Data(contentsOf: file) else { return [] }
        return (try? JSONDecoder().decode([Account].self, from: data)) ?? []
    }
    func save(_ accounts: [Account]) {
        guard let data = try? JSONEncoder().encode(accounts), let text = String(data: data, encoding: .utf8) else { return }
        storage.writeAtomically(text, to: file)
    }
}

/// Where the app and its widget extension share files and tokens (CCRM-93 (iOS Spike) proved both on the iPad).
enum Shared {
    static let appGroup = "group.com.robinrichardrajan.cooldown"
    static var keychainGroup: String {
        let prefix = Bundle.main.object(forInfoDictionaryKey: "AppIdentifierPrefix") as? String ?? ""
        return prefix + "com.robinrichardrajan.cooldown.shared"
    }
    static var storage: Storage {
        Storage(directory: FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup))
    }
}
