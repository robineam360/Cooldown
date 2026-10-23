// CCRM-93 (iOS Spike): a status screen, not the app. Each row is one free-team fact the plan
// rests on; the widget shows the same probe from the other side of the App Group.
import ActivityKit
import SwiftUI
import UIKit
import WidgetKit

@main
struct SpikeApp: App {
    var body: some Scene {
        WindowGroup { ProbeView() }
    }
}

struct ProbeRow: Identifiable {
    let id = UUID()
    let name: String
    let ok: Bool
    let detail: String
}

@MainActor
@Observable
final class Probe {
    var rows: [ProbeRow] = []
    var activityResult = "not tried"

    func run() {
        let stamp = Date().formatted(date: .omitted, time: .standard)
        var out: [ProbeRow] = []
        let device = UIDevice.current
        out.append(ProbeRow(name: "Device", ok: true,
                            detail: "\(device.model) · \(device.systemName) \(device.systemVersion)"))

        let container = Spike.groupContainer
        out.append(ProbeRow(name: "App Group container", ok: container != nil,
                            detail: container?.lastPathComponent ?? "nil — entitlement missing"))
        if let url = container?.appendingPathComponent(Spike.probeFile) {
            let written = (try? "app wrote \(stamp)".write(to: url, atomically: true, encoding: .utf8)) != nil
            out.append(ProbeRow(name: "Group file write", ok: written, detail: written ? stamp : "failed"))
        }
        UserDefaults(suiteName: Spike.appGroup)?.set("app wrote \(stamp)", forKey: Spike.defaultsKey)

        out.append(ProbeRow(name: "Keychain group", ok: Spike.keychainGroup != nil,
                            detail: Spike.keychainGroup ?? "no AppIdentifierPrefix (unsigned build)"))
        let write = Spike.writeKeychain("token \(stamp)")
        out.append(ProbeRow(name: "Keychain write", ok: write == errSecSuccess, detail: "OSStatus \(write)"))
        let read = Spike.readKeychain()
        out.append(ProbeRow(name: "Keychain read", ok: read.status == errSecSuccess,
                            detail: read.value ?? "OSStatus \(read.status)"))

        let auth = ActivityAuthorizationInfo()
        out.append(ProbeRow(name: "Live Activities enabled", ok: auth.areActivitiesEnabled,
                            detail: "\(auth.areActivitiesEnabled)"))
        rows = out
        WidgetCenter.shared.reloadAllTimelines()
    }

    func startActivity() {
        let state = SpikeActivityAttributes.ContentState(percent: 42, resetsAt: .now.addingTimeInterval(2 * 3600))
        do {
            let activity = try Activity.request(attributes: SpikeActivityAttributes(account: "Spike"),
                                                content: .init(state: state, staleDate: nil))
            activityResult = "started \(activity.id.prefix(8))"
        } catch {
            activityResult = "failed: \(error.localizedDescription)"
        }
    }
}

struct ProbeView: View {
    @State private var probe = Probe()

    var body: some View {
        NavigationStack {
            List {
                Section("Free-team probe") {
                    ForEach(probe.rows) { row in
                        HStack(alignment: .firstTextBaseline) {
                            Image(systemName: row.ok ? "checkmark.circle.fill" : "xmark.octagon.fill")
                                .foregroundStyle(row.ok ? .green : .red)
                            VStack(alignment: .leading) {
                                Text(row.name).font(.headline)
                                Text(row.detail).font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                        .accessibilityIdentifier("probe.\(row.name)")
                    }
                }
                Section("Live Activity") {
                    Button("Start a Live Activity") { probe.startActivity() }
                    Text(probe.activityResult).font(.footnote).accessibilityIdentifier("probe.activity")
                }
                Section {
                    Button("Run probe again") { probe.run() }
                }
            }
            .navigationTitle("Cooldown spike")
        }
        .task { probe.run() }
    }
}
