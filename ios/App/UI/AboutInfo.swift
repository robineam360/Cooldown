import Foundation
import UIKit

/// CCRM-94 (iOS App) parity wireframes rev B §8: the About rows. The version is read from the installed app, never typed:
/// numbering stays the owner's call.
enum AboutInfo {
    static let author = "Robin Richard Rajan"
    static let feedbackAddress = "robin@eam360.com"
    static let privacyURL = "https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md"

    /// "2.0 (14)": `CFBundleShortVersionString (CFBundleVersion)`.
    static func version(_ info: [String: Any]? = Bundle.main.infoDictionary) -> String {
        let short = info?["CFBundleShortVersionString"] as? String ?? "—"
        guard let build = info?["CFBundleVersion"] as? String else { return short }
        return "\(short) (\(build))"
    }

    /// The pre-addressed mail: subject "Cooldown <version> feedback", a prompt, and a footer naming the app, iOS and the
    /// device class ("iPhone" or "iPad": UIDevice does not give the marketing model).
    static func feedbackURL(version: String, osVersion: String, device: String) -> URL? {
        var parts = URLComponents()
        parts.scheme = "mailto"
        parts.path = feedbackAddress
        parts.queryItems = [
            URLQueryItem(name: "subject", value: "Cooldown \(version) feedback"),
            URLQueryItem(name: "body", value: "What happened:\n\n\n— Cooldown \(version), iOS \(osVersion), \(device)"),
        ]
        return parts.url
    }

    @MainActor static func currentFeedbackURL() -> URL? {
        feedbackURL(version: version(), osVersion: UIDevice.current.systemVersion,
                    device: UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "iPhone")
    }
}
