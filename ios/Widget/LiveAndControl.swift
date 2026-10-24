import ActivityKit
import AppIntents
import CooldownCore
import SwiftUI
import WidgetKit

/// CCRM-94 (iOS App): the Live Activity — the Duet with Weekly, wireframe rev B §6 — and the Dynamic Island on iPhone.
struct CooldownLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CooldownActivityAttributes.self) { context in
            LockPanel(state: context.state)
                .environment(\.palette, .dark)
                .activityBackgroundTint(Color(hex: 0x1C1C1E, alpha: 0.86))
                .activitySystemActionForegroundColor(.white)
                .widgetURL(context.state.rows.first.map { DeepLink.account($0.key) })
        } dynamicIsland: { context in
            let row = context.state.rows.first
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    if let row { Label { Text("\(row.label) · \(row.windowLabel)") } icon: { ProviderMark(provider: row.providerValue, size: 18) } }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if let row { BigNumber(row: row, size: 34) }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    if let row {
                        VStack(alignment: .leading, spacing: 8) {
                            UsageBar(pct: row.pct, pace: row.pace, accent: row.providerValue.accent(.dark), height: 8)
                            RowLine(row: row, long: true).font(.system(size: 13))
                        }
                        .environment(\.palette, .dark)
                    }
                }
            } compactLeading: {
                if let row {
                    HStack(spacing: 4) {
                        ProviderMark(provider: row.providerValue, size: 16)
                        Text(row.pct.map { "\(Int($0))%" } ?? "—").foregroundStyle(Palette.dark.severity(row.pct, accent: row.providerValue.accent(.dark)))
                    }
                }
            } compactTrailing: {
                if let row, row.leadsWeekly || row.hasReset {
                    Text(row.hasReset ? "reset" : row.reset.map { Fmt.compactDuration(ms: max($0.ms - Date.now.ms, 0)) } ?? "")
                        .monospacedDigit().frame(maxWidth: 52)
                } else if let reset = row?.reset {
                    Text(timerInterval: Date.now...max(reset, .now), countsDown: true, showsHours: true)
                        .monospacedDigit().frame(maxWidth: 52)
                        .foregroundStyle((row?.pct ?? 0) >= 100 ? Palette.dark.red : .white)
                }
            } minimal: {
                if let row {
                    UsageRing(pct: row.pct, pace: row.pace, accent: row.providerValue.accent(.dark), lineWidth: 3.4, label: "")
                        .environment(\.palette, .dark)
                }
            }
            .widgetURL(row.map { DeepLink.account($0.key) })
        }
    }
}

// MARK: - Control Center (wireframe rev B §7): one account's 5h number; tapping opens the app on it.

struct AccountControlIntent: ControlConfigurationIntent {
    static let title: LocalizedStringResource = "Account"
    @Parameter(title: "Account") var account: AccountEntity?
    init() {}
}

struct AccountControlValue: Equatable {
    let key: String?
    let title: String
    let value: String
}

struct AccountControlProvider: AppIntentControlValueProvider {
    func previewValue(configuration: AccountControlIntent) -> AccountControlValue {
        AccountControlValue(key: nil, title: "Teams · 5h", value: "46%")
    }

    func currentValue(configuration: AccountControlIntent) async throws -> AccountControlValue {
        let faces = Faces.load().map(\.leading)
        guard let face = faces.first(where: { $0.account.key == configuration.account?.id }) ?? faces.first else {
            return AccountControlValue(key: nil, title: "Cooldown", value: "Add an account")
        }
        let value: String
        switch face.status {
        case .signIn: value = "Sign in again"
        case .plan: value = "No usage on this plan"
        case .stale, .aging: value = "\(face.pct.map { "\(Int($0))%" } ?? "—") · as of \(face.fetchedAt.map(shortClock) ?? "—")"
        default: value = face.atLimit ? "Limit reached" : (face.pct.map { "\(Int($0))%" } ?? "—")
        }
        return AccountControlValue(key: face.account.key, title: "\(face.account.label) · \(face.windowLabel)", value: value)
    }
}

struct AccountControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        AppIntentControlConfiguration(kind: "AccountControl", provider: AccountControlProvider()) { value in
            ControlWidgetButton(action: OpenAccountIntent(account: value.key.map { AccountEntity(id: $0, label: value.title) })) {
                Label {
                    Text(value.title)
                    Text(value.value)
                } icon: {
                    Image(systemName: "hourglass")
                }
            }
        }
        .displayName("Cooldown")
        .description("One account's 5h number. Tap to open it.")
    }
}
