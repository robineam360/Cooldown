import CooldownCore
import Foundation

/// CCRM-94 (iOS App): one signed-in account as the app lists it. Order in the list is the user's order —
/// the same everywhere: tabs, sidebar, widgets, Live Activity (wireframe rev B, Settings).
struct Account: Codable, Identifiable, Equatable, Sendable {
    var key: String
    var label: String
    var provider: Provider
    /// The iPad's pinned account, retired 2026-11-02 (Robin: no pinned account; the Summary and one account at a time).
    /// Still decoded and kept, never read: stored keys are never reused.
    var pinned = false
    /// When it was signed in here, for Details… (nil for accounts added before this was recorded).
    var addedAtMs: Int64? = nil
    var id: String { key }

    /// The positional default a cleared rename restores: the provider's name for the first account of that provider
    /// in the list, "<name> 2" for the second, and so on. Android freezes the label at creation; iOS stores none, so
    /// the position is the stand-in, and a name another account already carries is skipped so two tabs never read alike.
    static func defaultLabel(for key: String, in accounts: [Account]) -> String {
        guard let account = accounts.first(where: { $0.key == key }) else { return "" }
        let same = accounts.filter { $0.provider == account.provider }
        let n = (same.firstIndex(where: { $0.key == key }) ?? 0) + 1
        let taken = Set(accounts.filter { $0.key != key }.map { $0.label.lowercased() })
        func name(_ k: Int) -> String { k == 1 ? account.provider.displayName : "\(account.provider.displayName) \(k)" }
        let order = Array(n...(n + accounts.count)) + Array(1..<n)
        return name(order.first { !taken.contains(name($0).lowercased()) } ?? n)
    }
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

/// CCRM-94 (iOS App): Android's `AccountEmail.suggestion` — the rename sheet's "Use “robin”" row. The local part,
/// unless another account's email shares it (then the domain's first label, which is what differs); nil when there is
/// no email, or the suggestion is already the name. Clipped to `maxLen`.
enum AccountEmail {
    static let nameLimit = 16

    static func split(_ email: String) -> (local: String, domain: String) {
        guard let at = email.lastIndex(of: "@"), at != email.startIndex else { return (email, "") }
        return (String(email[..<at]), String(email[at...]))
    }

    static func suggestion(email: String?, others: [String], current: String, maxLen: Int = nameLimit) -> String? {
        guard let email = email?.trimmingCharacters(in: .whitespaces), email.contains("@") else { return nil }
        let (local, domain) = split(email)
        let shared = others.contains { $0.contains("@") && split($0.trimmingCharacters(in: .whitespaces)).local.lowercased() == local.lowercased() }
        let pick = shared ? String(domain.dropFirst().prefix { $0 != "." }) : local
        let out = String(pick.prefix(maxLen))
        let name = current.trimmingCharacters(in: .whitespaces)
        return out.isEmpty || out.lowercased() == name.lowercased() ? nil : out
    }
}
