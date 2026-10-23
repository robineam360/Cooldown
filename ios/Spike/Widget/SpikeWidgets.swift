// CCRM-93 (iOS Spike): the widget side of the probe — home screen, lock screen, the Live Activity
// and a Control Center control. It only reads what the app wrote.
import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

@main
struct SpikeWidgets: WidgetBundle {
    var body: some Widget {
        ProbeWidget()
        SpikeLiveActivity()
        OpenCooldownControl()
    }
}

struct ProbeEntry: TimelineEntry {
    let date: Date
    let groupFile: String?
    let defaults: String?
    let keychain: String
}

struct ProbeProvider: TimelineProvider {
    func placeholder(in context: Context) -> ProbeEntry {
        ProbeEntry(date: .now, groupFile: "…", defaults: "…", keychain: "…")
    }
    func getSnapshot(in context: Context, completion: @escaping (ProbeEntry) -> Void) {
        completion(entry())
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<ProbeEntry>) -> Void) {
        completion(Timeline(entries: [entry()], policy: .after(.now.addingTimeInterval(15 * 60))))
    }
    private func entry() -> ProbeEntry {
        let read = Spike.readKeychain()
        return ProbeEntry(date: .now,
                          groupFile: Spike.readGroupFile(),
                          defaults: UserDefaults(suiteName: Spike.appGroup)?.string(forKey: Spike.defaultsKey),
                          keychain: read.value ?? "OSStatus \(read.status)")
    }
}

struct ProbeWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: ProbeEntry

    var body: some View {
        switch family {
        case .accessoryCircular:
            Image(systemName: entry.groupFile == nil ? "xmark" : "checkmark")
        case .accessoryRectangular, .accessoryInline:
            Text("group \(entry.groupFile == nil ? "✗" : "✓") · key \(entry.keychain.hasPrefix("token") ? "✓" : "✗")")
        default:
            VStack(alignment: .leading, spacing: 4) {
                Text("Spike probe").font(.headline)
                Text("file: \(entry.groupFile ?? "nil")").font(.caption2)
                Text("defaults: \(entry.defaults ?? "nil")").font(.caption2)
                Text("keychain: \(entry.keychain)").font(.caption2)
                Text(entry.date, style: .time).font(.caption2).foregroundStyle(.secondary)
            }
        }
    }
}

struct ProbeWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ProbeWidget", provider: ProbeProvider()) { entry in
            ProbeWidgetView(entry: entry).containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Spike probe")
        .description("Shows what the app wrote to the App Group and the shared Keychain.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct SpikeLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SpikeActivityAttributes.self) { context in
            HStack {
                Text("\(context.attributes.account) · 5h \(context.state.percent)%")
                Spacer()
                Text(context.state.resetsAt, style: .timer)
            }
            .padding()
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.center) { Text("5h \(context.state.percent)%") }
            } compactLeading: {
                Text("\(context.state.percent)%")
            } compactTrailing: {
                Text(context.state.resetsAt, style: .timer)
            } minimal: {
                Text("\(context.state.percent)")
            }
        }
    }
}

struct OpenCooldownControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "OpenCooldownControl") {
            ControlWidgetButton(action: OpenCooldownIntent()) {
                Label("Cooldown", systemImage: "hourglass")
            }
        }
        .displayName("Open Cooldown")
    }
}
