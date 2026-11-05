import Foundation
import Testing

@testable import Cooldown

/// CCRM-94 (iOS App) parity wireframes rev B §8: About and the feedback mail.
@Suite("About (parity rev B)")
struct AboutTests {
    @Test("the version reads the bundle: short version and build, never a typed number")
    func version() {
        #expect(AboutInfo.version(["CFBundleShortVersionString": "2.0", "CFBundleVersion": "14"]) == "2.0 (14)")
        #expect(AboutInfo.version(["CFBundleShortVersionString": "2.0"]) == "2.0")
        #expect(AboutInfo.version([:]) == "—")
        let live = AboutInfo.version()
        #expect(live.range(of: #"^\d+(\.\d+)+( \(\d+\))?$"#, options: .regularExpression) != nil)
    }

    @Test("the feedback mail: address, subject with the version, body with app, iOS and device")
    func mail() throws {
        let url = try #require(AboutInfo.feedbackURL(version: "2.0 (14)", osVersion: "26.1", device: "iPhone"))
        let c = try #require(URLComponents(url: url, resolvingAgainstBaseURL: false))
        #expect(c.scheme == "mailto" && c.path == "robin@eam360.com")
        let q = Dictionary(uniqueKeysWithValues: (c.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        #expect(q["subject"] == "Cooldown 2.0 (14) feedback")
        #expect(q["body"] == "What happened:\n\n\n— Cooldown 2.0 (14), iOS 26.1, iPhone")
    }

    @Test("the privacy link is the existing page; the author line is fixed")
    func links() {
        #expect(AboutInfo.privacyURL == "https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md")
        #expect(AboutInfo.author == "Robin Richard Rajan")
    }
}
