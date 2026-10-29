// Ported from CooldownMac tests/ccooldown-core-tests/Fixtures.swift at 2bfbdfd (CCRM-94 (iOS App)); reads spec/core/fixtures.
import Foundation

/// Loads the shared core fixtures from `spec/core/fixtures` (CCRM-92 (Shared Spec)).
///
/// Located relative to `#filePath` rather than bundled as a SwiftPM resource, so the
/// tests read the *same* files the Android client reads and there is no copy to drift.
enum Fixtures {

    static let root: URL = {
        // .../ios/Packages/CooldownCore/Tests/CooldownCoreTests/Fixtures.swift -> repo root
        URL(filePath: #filePath)
            .deletingLastPathComponent()   // CooldownCoreTests
            .deletingLastPathComponent()   // Tests
            .deletingLastPathComponent()   // CooldownCore
            .deletingLastPathComponent()   // Packages
            .deletingLastPathComponent()   // ios
            .deletingLastPathComponent()   // repo root
            .appending(path: "spec/core/fixtures")
    }()

    static func text(_ relativePath: String) throws -> String {
        try String(contentsOf: root.appending(path: relativePath), encoding: .utf8)
    }

    static func json(_ relativePath: String) throws -> [String: Any] {
        let data = try Data(contentsOf: root.appending(path: relativePath))
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw FixtureError.notAnObject(relativePath)
        }
        return object
    }

    /// Every `usage/*.json` that has a matching `.expected.json`.
    static func usageCaseNames() throws -> [String] {
        let usage = root.appending(path: "usage")
        let names = try FileManager.default.contentsOfDirectory(atPath: usage.path())
        return names
            .filter { $0.hasSuffix(".json") && !$0.hasSuffix(".expected.json") }
            .map { String($0.dropLast(".json".count)) }
            .filter { FileManager.default.fileExists(atPath: usage.appending(path: "\($0).expected.json").path()) }
            .sorted()
    }

    enum FixtureError: Error {
        case notAnObject(String)
    }
}

// Small typed accessors, so the tests read as assertions rather than as casting.
extension Dictionary where Key == String, Value == Any {
    func dict(_ key: String) -> [String: Any]? { self[key] as? [String: Any] }
    func list(_ key: String) -> [Any]? { self[key] as? [Any] }
    func str(_ key: String) -> String? { self[key] as? String }
    func num(_ key: String) -> Double? { (self[key] as? NSNumber)?.doubleValue }
    func i64(_ key: String) -> Int64? { (self[key] as? NSNumber)?.int64Value }
    func int(_ key: String) -> Int? { (self[key] as? NSNumber)?.intValue }
    func flag(_ key: String) -> Bool? { (self[key] as? NSNumber)?.boolValue }
    /// Present-and-non-null.
    func has(_ key: String) -> Bool { self[key] != nil && !(self[key] is NSNull) }
}
