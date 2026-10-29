import AppIntents
import CooldownCore
import SwiftUI
import WidgetKit

/// CCRM-94 (iOS App): the home-screen and lock-screen widgets of wireframe rev B §4–§5 — the v1.8 faces (Number,
/// Countdown, Strip) in iOS's sizes, the Bars small and the Overview of rev C (2026-10-24, which dropped the Ring),
/// and the lock-screen family. They read
/// what the app wrote to the App Group and never fetch; tapping opens the app on the account.
@main
struct CooldownWidgets: WidgetBundle {
    var body: some Widget {
        NumberWidget()
        CountdownWidget()
        BarsWidget()
        StripWidget()
        OverviewWidget()
        LockWidget()
        AccountControl()
        CooldownLiveActivity()
    }
}

// MARK: - Timeline

/// One provider for every widget: it loads the faces and asks to be reloaded at the next reset (so a closed window
/// reads 0%) and every 30 minutes (so "as of" appears on time). The app also reloads timelines after each fetch.
struct FaceProvider<Intent: WidgetConfigurationIntent>: AppIntentTimelineProvider {
    let key: @Sendable (Intent) -> String?
    var window: @Sendable (Intent) -> WindowChoice = { _ in .fiveHour }

    func placeholder(in context: Context) -> FaceEntry { FaceEntry(date: .now, faces: Demo.faces, key: nil) }

    func snapshot(for configuration: Intent, in context: Context) async -> FaceEntry {
        let faces = Faces.load()
        return FaceEntry(date: .now, faces: faces.isEmpty && context.isPreview ? Demo.faces : faces,
                         key: key(configuration), window: window(configuration))
    }

    func timeline(for configuration: Intent, in context: Context) async -> Timeline<FaceEntry> {
        let now = Date.now
        var dates = [now]
        let faces = Faces.load()
        if let reset = faces.compactMap(\.reset).filter({ $0 > now }).min() { dates.append(reset.addingTimeInterval(1)) }
        let entries = dates.map { date in
            FaceEntry(date: date, faces: date == now ? faces : Faces.load(nowMs: date.ms), key: key(configuration),
                      window: window(configuration))
        }
        return Timeline(entries: entries, policy: .after(now.addingTimeInterval(30 * 60)))
    }
}

// MARK: - Shared face pieces

// MARK: - Number (small)

struct NumberWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Number", intent: AccountWindowWidgetIntent.self,
                               provider: FaceProvider<AccountWindowWidgetIntent>(key: { $0.account?.id }, window: { $0.window })) { entry in
            WidgetFrame(key: entry.face?.account.key) { NumberFace(entry: entry) }
        }
        .configurationDisplayName("Number")
        .description("One account's 5h or Weekly number, its bar and when it resets.")
        .supportedFamilies([.systemSmall])
    }
}

// MARK: - Countdown (small)

struct CountdownWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Countdown", intent: AccountWidgetIntent.self,
                               provider: FaceProvider<AccountWidgetIntent>(key: { $0.account?.id })) { entry in
            WidgetFrame(key: entry.face?.account.key) { CountdownFace(entry: entry) }
        }
        .configurationDisplayName("Countdown")
        .description("Time until an account's 5-hour window resets, ticking live.")
        .supportedFamilies([.systemSmall])
    }
}

// MARK: - Bars (small): one account's 5h and Weekly

struct BarsWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Bars", intent: AccountWidgetIntent.self,
                               provider: FaceProvider<AccountWidgetIntent>(key: { $0.account?.id })) { entry in
            WidgetFrame(key: entry.face?.account.key) { BarsFace(entry: entry) }
        }
        .configurationDisplayName("Bars")
        .description("One account's 5h and Weekly, each with its bar and reset.")
        .supportedFamilies([.systemSmall])
    }
}

// MARK: - Strip (medium)

struct StripWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Strip", intent: AccountWidgetIntent.self,
                               provider: FaceProvider<AccountWidgetIntent>(key: { $0.account?.id })) { entry in
            WidgetFrame(key: entry.face?.account.key) { StripFace(entry: entry) }
        }
        .configurationDisplayName("Strip")
        .description("One account: 5h big, with Weekly under it.")
        .supportedFamilies([.systemMedium])
    }
}

// MARK: - Overview (rev C): every account as a card, Medium to Extra Large

struct OverviewWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "Overview", provider: StaticFaceProvider()) { entry in
            WidgetFrame(key: nil) { OverviewFace(entry: entry) }
        }
        .configurationDisplayName("Overview")
        .description("Every account's 5h and Weekly — with the trend chart where there's room.")
        .supportedFamilies([.systemMedium, .systemLarge, .systemExtraLarge])
    }
}

struct StaticFaceProvider: TimelineProvider {
    func placeholder(in context: Context) -> FaceEntry { FaceEntry(date: .now, faces: Demo.faces, key: nil) }
    func getSnapshot(in context: Context, completion: @escaping (FaceEntry) -> Void) {
        let faces = Faces.load()
        completion(FaceEntry(date: .now, faces: faces.isEmpty && context.isPreview ? Demo.faces : faces, key: nil))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<FaceEntry>) -> Void) {
        // As FaceProvider: a second entry just after the next reset, so the live countdown never sits at 0:00:00
        // over a closed window.
        let now = Date.now
        let faces = Faces.load()
        var entries = [FaceEntry(date: now, faces: faces, key: nil)]
        if let reset = faces.compactMap(\.reset).filter({ $0 > now }).min() {
            let at = reset.addingTimeInterval(1)
            entries.append(FaceEntry(date: at, faces: Faces.load(nowMs: at.ms), key: nil))
        }
        completion(Timeline(entries: entries, policy: .after(now.addingTimeInterval(30 * 60))))
    }
}

// MARK: - Lock screen (circular, rectangular, inline). iOS tints these itself, so colour can't carry severity.

struct LockWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Lock", intent: AccountWidgetIntent.self,
                               provider: FaceProvider<AccountWidgetIntent>(key: { $0.account?.id })) { entry in
            LockFace(entry: entry)
                .containerBackground(.clear, for: .widget)
                .widgetURL(entry.face.map { DeepLink.account($0.account.key) })
        }
        .configurationDisplayName("Cooldown")
        .description("One account's 5h on the lock screen.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

