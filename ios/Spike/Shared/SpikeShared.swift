// CCRM-93 (iOS Spike): what the app and the widget extension share. The probe proves App Groups
// (a container both can see) and a shared Keychain group on a free personal team.
import ActivityKit
import AppIntents
import Foundation
import Security

enum Spike {
    static let appGroup = "group.com.robinrichardrajan.cooldown"
    static let probeFile = "probe.txt"
    static let defaultsKey = "probe"
    static let keychainService = "cooldown.spike"
    static let keychainAccount = "probe"

    /// `$(AppIdentifierPrefix)` is the team id plus a dot; it is empty on an unsigned build.
    static var keychainGroup: String? {
        guard let prefix = Bundle.main.object(forInfoDictionaryKey: "AppIdentifierPrefix") as? String,
              !prefix.isEmpty, !prefix.hasPrefix("$") else { return nil }
        return prefix + "com.robinrichardrajan.cooldown.shared"
    }

    static var groupContainer: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)
    }

    static func writeKeychain(_ value: String) -> OSStatus {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
        ]
        if let group = keychainGroup { query[kSecAttrAccessGroup as String] = group }
        SecItemDelete(query as CFDictionary)
        query[kSecValueData as String] = Data(value.utf8)
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(query as CFDictionary, nil)
    }

    static func readKeychain() -> (status: OSStatus, value: String?) {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecReturnData as String: true,
        ]
        if let group = keychainGroup { query[kSecAttrAccessGroup as String] = group }
        var out: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &out)
        let value = (out as? Data).flatMap { String(data: $0, encoding: .utf8) }
        return (status, value)
    }

    static func readGroupFile() -> String? {
        guard let url = groupContainer?.appendingPathComponent(probeFile) else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }
}

struct SpikeActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var percent: Int
        var resetsAt: Date
    }
    var account: String
}

struct OpenCooldownIntent: AppIntent {
    static let title: LocalizedStringResource = "Open Cooldown"
    static let openAppWhenRun = true
    func perform() async throws -> some IntentResult { .result() }
}
