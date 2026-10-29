import Foundation

/// CCRM-94 (iOS App): the four network calls the fetch repository makes, as a seam so its tests run
/// without a network. `APIClient` is the real one.
public protocol UsageAPI: Sendable {
    func fetchUsage(_ credentials: Credentials) async throws -> HTTPResult
    func renew(_ credentials: Credentials) async throws -> HTTPResult
    /// Claude's `/api/oauth/profile`, for the plan tag and the Free-plan check (spec core §9.2, §10.1).
    func fetchProfile(accessToken: String) async throws -> HTTPResult
}

extension APIClient: UsageAPI {
    public func fetchUsage(_ credentials: Credentials) async throws -> HTTPResult {
        switch credentials.provider {
        case .claude: try await fetchUsage(accessToken: credentials.accessToken)
        case .chatgpt: try await fetchChatGPTUsage(accessToken: credentials.accessToken, accountId: credentials.accountId ?? "")
        }
    }

    public func renew(_ credentials: Credentials) async throws -> HTTPResult {
        switch credentials.provider {
        case .claude: try await refreshToken(credentials.refreshToken)
        case .chatgpt: try await refreshChatGPTToken(credentials.refreshToken)
        }
    }
}
