// Ported from CooldownMac sources/ccooldown-core/Storage.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// Where the app keeps its files, and how it writes them.
public struct Storage: Sendable {

    public let directory: URL

    /// Defaults to `~/Library/Application Support/CCooldown`. Tests pass a temp directory.
    public init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? URL(filePath: NSHomeDirectory()).appending(path: "Library/Application Support")
            // Stays "CCooldown" after the CCM-98 [Foundation] rename: the status-line command in
            // ~/.claude/settings.json and the widget sandbox exception both name this path.
            self.directory = base.appending(path: "CCooldown")
        }
        try? FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
    }

    public func file(_ name: String) -> URL { directory.appending(path: name) }

    /// Temp file plus rename. History and state are rewritten wholesale on every change, so
    /// a torn write would lose the file rather than one record — the rename makes the
    /// replacement atomic.
    public func writeAtomically(_ text: String, to url: URL) {
        let temp = url.appendingPathExtension("tmp")
        do {
            try text.write(to: temp, atomically: false, encoding: .utf8)
            _ = try FileManager.default.replaceItemAt(url, withItemAt: temp)
        } catch {
            // Best-effort by design: losing a data point must never surface as an error.
            try? FileManager.default.removeItem(at: temp)
        }
    }

    public func readLines(_ url: URL) -> [String] {
        guard let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return text.split(separator: "\n").map(String.init).filter { !$0.isEmpty }
    }
}

/// Raw samples for the trend chart: one record per successful fetch, one file per profile.
///
/// Kept separate from `SessionLog` because they answer different questions and have very
/// different retention. BEHAVIOR-SPEC §7.1.
public struct HistoryStore: Sendable {

    /// 8 days, so the 7-day window always has a full curve.
    public static let maxAgeMs: Int64 = 8 * 24 * 60 * 60_000

    let storage: Storage

    public init(storage: Storage) { self.storage = storage }

    func file(_ profile: Profile) -> URL { storage.file("usage-history-\(profile.key).jsonl") }

    public func record(_ profile: Profile, data: UsageData, atMs: Int64) {
        var fields: [String: Any] = ["t": atMs]
        if let pct = data.session?.percent { fields["sp"] = pct }
        if let reset = data.session?.resetsAtMs { fields["sr"] = reset }
        if let pct = data.weekly?.percent { fields["wp"] = pct }
        if let reset = data.weekly?.resetsAtMs { fields["wr"] = reset }
        guard let line = try? JSONSerialization.data(withJSONObject: fields),
              let text = String(data: line, encoding: .utf8)
        else { return }

        let kept = storage.readLines(file(profile)).filter { timestamp(of: $0) > atMs - Self.maxAgeMs }
        storage.writeAtomically((kept + [text]).joined(separator: "\n") + "\n", to: file(profile))
    }

    public func points(_ profile: Profile) -> [HistoryPoint] {
        storage.readLines(file(profile)).compactMap(Self.parse).sorted { $0.atMs < $1.atMs }
    }

    public func clear(_ profile: Profile) {
        try? FileManager.default.removeItem(at: file(profile))
    }

    private func timestamp(of line: String) -> Int64 {
        (try? JSONSerialization.jsonObject(with: Data(line.utf8)))
            .flatMap { JSON($0)["t"].int64 } ?? 0
    }

    static func parse(_ line: String) -> HistoryPoint? {
        guard let object = try? JSONSerialization.jsonObject(with: Data(line.utf8)) else { return nil }
        let json = JSON(object)
        guard let at = json["t"].int64, at > 0 else { return nil }
        return HistoryPoint(
            atMs: at,
            sessionPct: json["sp"].double,
            sessionResetAtMs: json["sr"].int64 ?? 0,
            weeklyPct: json["wp"].double,
            weeklyResetAtMs: json["wr"].int64 ?? 0)
    }
}

/// Closed windows, for the history bars. One compact record per window that finished.
/// BEHAVIOR-SPEC §7.2.
public struct SessionLog: Sendable {

    /// 366 days.
    public static let maxAgeMs: Int64 = 366 * 24 * 60 * 60_000

    let storage: Storage

    public init(storage: Storage) { self.storage = storage }

    func file(_ profile: Profile) -> URL { storage.file("usage-sessions-\(profile.key).jsonl") }

    /// Idempotent: writing the same (kind, resetAt) twice is a no-op, because a window can
    /// be observed as closed more than once.
    public func record(
        _ profile: Profile, kind: String, resetAtMs: Int64, peakPct: Double, hitLimit: Bool, nowMs: Int64
    ) {
        let existing = records(profile)
        guard !existing.contains(where: { $0.kind == kind && $0.resetAtMs == resetAtMs }) else { return }

        let fields: [String: Any] = ["k": kind, "r": resetAtMs, "p": peakPct, "h": hitLimit]
        guard let line = try? JSONSerialization.data(withJSONObject: fields),
              let text = String(data: line, encoding: .utf8)
        else { return }

        let kept = storage.readLines(file(profile)).filter { resetAt(of: $0) > nowMs - Self.maxAgeMs }
        storage.writeAtomically((kept + [text]).joined(separator: "\n") + "\n", to: file(profile))
    }

    public func records(_ profile: Profile) -> [HistoryStats.Record] {
        storage.readLines(file(profile)).compactMap(Self.parse).sorted { $0.resetAtMs < $1.resetAtMs }
    }

    public func clear(_ profile: Profile) {
        try? FileManager.default.removeItem(at: file(profile))
    }

    private func resetAt(of line: String) -> Int64 {
        (try? JSONSerialization.jsonObject(with: Data(line.utf8)))
            .flatMap { JSON($0)["r"].int64 } ?? 0
    }

    static func parse(_ line: String) -> HistoryStats.Record? {
        guard let object = try? JSONSerialization.jsonObject(with: Data(line.utf8)) else { return nil }
        let json = JSON(object)
        guard let kind = json["k"].nonEmptyString, let resetAt = json["r"].int64, resetAt > 0 else {
            return nil
        }
        return HistoryStats.Record(
            kind: kind, resetAtMs: resetAt,
            peakPct: json["p"].double ?? 0,
            hitLimit: (json["h"].raw as? NSNumber)?.boolValue ?? false)
    }
}
