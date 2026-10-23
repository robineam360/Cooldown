// Ported from CooldownMac sources/ccooldown-core/CredentialStore.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation
import Security

public struct Credentials: Sendable, Equatable, Codable {
    public let accessToken: String
    public let refreshToken: String
    /// Epoch millis; 0 = unknown.
    public let expiresAtMs: Int64
    /// CCM-76 [Accounts] checkpoint 3b: ChatGPT's `ChatGPT-Account-Id` header value, carried
    /// alongside the tokens it belongs to rather than looked up separately — a discovered Codex
    /// login has its own `DiscoveredCredentialInfo.chatGPTAccountId` for this, but an
    /// app-signed-in (device-code) credential has nowhere else to keep it. `nil` for every
    /// Claude credential, and for any credential stored before this field existed — an Optional
    /// stored property decodes missing-key JSON to `nil` rather than failing, so this is a
    /// backward-compatible addition to what's already sitting in the Keychain.
    public let accountId: String?
    /// CCM-76 [Accounts] checkpoint 3b fix (mesh-judge review, blocker): which service these
    /// tokens actually belong to — written once, by whichever completion function persists this
    /// credential (`UsageRepository.completeSignIn` → `.claude`, `.completeDeviceSignIn` →
    /// `.chatgpt`), and never touched again. `UsageRepository.fetch`'s app-credential branch
    /// reads this back to set `ProfileState.provider`, instead of trusting whatever
    /// `ProfileState.provider` already says — that field can be flipped by an unrelated code
    /// path (`RegistryReconciler.matchingKey`'s email-match pass can attach a discovered Codex
    /// login to a `.claude` app-signed-in record sharing that email, which sets
    /// `ProfileState.provider = .chatgpt` via the *discovered* branch; when that Codex login
    /// later goes away, `fetch` would otherwise fall back to the still-`.chatgpt`-tagged Claude
    /// credential and send an Anthropic bearer token to `chatgpt.com`). A stored credential's own
    /// provider is fixed at the moment it's saved, by the one function with the actual guarantee,
    /// so it's the trustworthy source here — `ProfileState.provider` is not.
    public let provider: Provider

    public init(
        accessToken: String, refreshToken: String, expiresAtMs: Int64, accountId: String? = nil,
        provider: Provider = .claude
    ) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.expiresAtMs = expiresAtMs
        self.accountId = accountId
        self.provider = provider
    }

    private enum CodingKeys: String, CodingKey {
        case accessToken, refreshToken, expiresAtMs, accountId, provider
    }

    /// `accessToken`/`refreshToken`/`expiresAtMs` stay required (a blob missing any of them was
    /// never valid and `load()` returning nil for it is existing, correct behaviour). `accountId`
    /// and `provider` are tolerant, matching every other stored type in this file's neighbourhood
    /// (`AccountRecord`, `ProfileState`): a Keychain blob written before either field existed
    /// must decode successfully rather than silently signing the user out, and `.claude` is the
    /// accurate default for every credential that could have been stored before `provider`
    /// existed — app sign-in was Claude-only until this checkpoint.
    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        accessToken = try container.decode(String.self, forKey: .accessToken)
        refreshToken = try container.decode(String.self, forKey: .refreshToken)
        expiresAtMs = try container.decode(Int64.self, forKey: .expiresAtMs)
        accountId = (try? container.decodeIfPresent(String.self, forKey: .accountId)) ?? nil
        provider = (try? container.decodeIfPresent(Provider.self, forKey: .provider)) ?? .claude
    }
}

/// CCM-136 [Readout] checkpoint cp1(b): what a Keychain read actually found, distinguishing
/// "there's nothing there" from "the user (or macOS) refused to hand it over" from "something
/// else went wrong" — `CredentialStore.load()` collapses all three to `nil`, which is exactly
/// right for a signed-out card but wrong for `CredentialCache` (a denial should stay cached so a
/// popover open doesn't re-prompt on every redraw; an unexplained failure should not).
public enum CredentialLoadResult: Sendable, Equatable {
    case found(Credentials)
    case absent
    case denied(OSStatus)
    case failed(OSStatus)
}

/// The read side `CredentialCache` needs from whatever it wraps — `CredentialStore` today, a
/// fake in tests. Distinct from `CredentialStoring` (`UsageRepository.swift`): that protocol is
/// `load() -> Credentials?`, the collapsed view every existing caller still gets: this one keeps
/// the Keychain status apart so the cache can tell a denial from an empty item.
public protocol CredentialReading: Sendable {
    func loadResult(profileKey: String) -> CredentialLoadResult
    func save(_ credentials: Credentials, profileKey: String) throws
    @discardableResult func delete(profileKey: String) -> OSStatus
}

/// Keychain-backed token storage, one item per profile.
///
/// The Apple counterpart to the Android client's `EncryptedSharedPreferences` (macOS and iOS). Tokens
/// never touch `UserDefaults` or a plist — a refresh token is a long-lived credential for
/// the user's whole Claude account.
public struct CredentialStore: Sendable {

    let service: String
    /// iOS (CCRM-94 (iOS App)): the shared Keychain group the app and its widget extension both carry
    /// (`<team>.com.robinrichardrajan.cooldown.shared`), so a widget can fetch with the app's token. nil keeps
    /// the default group — the Mac's behaviour, and the tests'.
    let accessGroup: String?

    public init(service: String = "com.robin.ccooldown.tokens", accessGroup: String? = nil) {
        self.service = service
        self.accessGroup = accessGroup
    }

    /// The iOS app's store: its own service name and the shared group.
    public static func shared(accessGroup: String) -> CredentialStore {
        CredentialStore(service: "com.robinrichardrajan.cooldown.tokens", accessGroup: accessGroup)
    }

    public func save(_ credentials: Credentials, profileKey: String) throws {
        let data = try JSONEncoder().encode(credentials)
        let query = baseQuery(profileKey)

        // Update in place when the item already exists, rather than delete-then-add: a
        // delete followed by a fresh add creates a brand-new keychain item with a blank
        // ACL, discarding any "Always Allow" trust the user granted earlier and forcing a
        // re-prompt on every token refresh. SecItemUpdate keeps the existing item (and its
        // ACL) and only touches the data.
        let updateStatus = SecItemUpdate(
            query as CFDictionary,
            [kSecValueData as String: data] as CFDictionary
        )
        if updateStatus == errSecSuccess { return }
        guard updateStatus == errSecItemNotFound else { throw KeychainError.unhandled(updateStatus) }

        var attributes = query
        attributes[kSecValueData as String] = data
        // ThisDeviceOnly on iOS: a refresh token never travels in an encrypted backup to another
        // device. AfterFirstUnlock either way, so the widget and a background refresh can read it.
        #if os(iOS)
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        #else
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        #endif
        let addStatus = SecItemAdd(attributes as CFDictionary, nil)
        guard addStatus == errSecSuccess else { throw KeychainError.unhandled(addStatus) }
    }

    public func load(profileKey: String) -> Credentials? {
        guard case .found(let credentials) = loadResult(profileKey: profileKey) else { return nil }
        return credentials
    }

    /// CCM-136 [Readout] cp1(b): the same read `load()` does, without collapsing the status.
    public func loadResult(profileKey: String) -> CredentialLoadResult {
        var query = baseQuery(profileKey)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        return Self.loadResult(status: status, data: item as? Data)
    }

    /// The status mapping, pure so it can be tested without a Keychain.
    /// `errSecAuthFailed`/`errSecUserCanceled`/`errSecInteractionNotAllowed` are the three
    /// statuses `SecItemCopyMatching` returns for "the user (or the system) declined to hand
    /// this item over" rather than "it isn't there" — a denial on a self-signed build's own ACL
    /// prompt (RD10) is exactly this. A found item that fails to decode reads as `.absent`
    /// (judge review of cp1(b), finding 1): bad data stays bad until a save or clear replaces it,
    /// so re-reading it on every call would only re-prompt; `.absent` is cached, `load()` still
    /// returns nil as before, and sign-in's update-in-place overwrites it.
    static func loadResult(status: OSStatus, data: Data?) -> CredentialLoadResult {
        switch status {
        case errSecSuccess:
            guard let data, let credentials = try? JSONDecoder().decode(Credentials.self, from: data)
            else { return .absent }
            return .found(credentials)
        case errSecItemNotFound:
            return .absent
        case errSecAuthFailed, errSecUserCanceled, errSecInteractionNotAllowed:
            return .denied(status)
        default:
            return .failed(status)
        }
    }

    /// Existing behaviour for `CredentialStoring` callers (`UsageRepository.swift`): fire and
    /// forget.
    public func clear(profileKey: String) {
        SecItemDelete(baseQuery(profileKey) as CFDictionary)
    }

    /// CCM-136 [Readout] cp1(b): the `CredentialReading` delete — the same `SecItemDelete` as
    /// `clear`, but it hands back the status so `CredentialCache` can tell a denied delete from
    /// a clean one. A distinct name, not a `clear` overload differing only by return type: that
    /// made every direct call on a concrete `CredentialStore` ambiguous (judge review, finding 2).
    @discardableResult
    public func delete(profileKey: String) -> OSStatus {
        SecItemDelete(baseQuery(profileKey) as CFDictionary)
    }

    private func baseQuery(_ profileKey: String) -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: profileKey
        ]
        if let accessGroup { query[kSecAttrAccessGroup as String] = accessGroup }
        return query
    }

    public enum KeychainError: Error, Equatable {
        case unhandled(OSStatus)
    }
}

extension CredentialStore: CredentialReading {}

/// Profiles are modelled as **string keys from the start**, not a two-case enum.
///
/// The Android client hard-coded `PERSONAL`/`WORK` and CCRM-6 exists to undo that; there
/// is no reason to inherit the migration here. BEHAVIOR-SPEC §9.
public struct Profile: Sendable, Equatable, Hashable, Codable {
    public let key: String
    public var label: String

    public init(key: String, label: String) {
        self.key = key
        self.label = label
    }

    public static let personal = Profile(key: "personal", label: "Personal")
    public static let work = Profile(key: "work", label: "Work")

    /// Longest label the UI accepts; also enforced here so a hand-edited settings file can't
    /// blow the layout up.
    public static let maxLabelLength = 30

    /// This profile with a custom label applied (CCM-41). Overrides are keyed by profile key —
    /// labels never key anything persisted (BEHAVIOR-SPEC §9), so renaming is display-only.
    /// A missing, empty, or whitespace-only override falls back to the built-in label.
    public func withLabel(from overrides: [String: String]) -> Profile {
        guard let raw = overrides[key] else { return self }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return self }
        return Profile(key: key, label: String(trimmed.prefix(Self.maxLabelLength)))
    }
}

/// From the Mac's `UsageRepository.swift`: the seam fetch code uses, so tests can swap in an in-memory store.
public protocol CredentialStoring: Sendable {
    func load(profileKey: String) -> Credentials?
    func save(_ credentials: Credentials, profileKey: String) throws
    func clear(profileKey: String)
}

extension CredentialStore: CredentialStoring {}
