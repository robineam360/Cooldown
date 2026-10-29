import Foundation
import Testing

@testable import CooldownCore

/// CCRM-94 (iOS App): what changes on iPhone and iPad. These run in the iOS simulator
/// (`xcodebuild test`) as well as on the Mac.

@Test("the honest User-Agent names the platform it runs on (API-CONTRACT §2)")
func userAgentNamesPlatform() {
    #if os(iOS)
    #expect(APIClient.userAgent(version: "1.0") == "Cooldown/1.0 (iOS)")
    #else
    #expect(APIClient.userAgent(version: "1.0") == "Cooldown/1.0 (macOS)")
    #endif
}

// The Keychain store is tested in the app-hosted target (ios/AppTests/KeychainStoreTests.swift): on iOS a
// bare package test has no keychain entitlement (errSecMissingEntitlement, -34018).
