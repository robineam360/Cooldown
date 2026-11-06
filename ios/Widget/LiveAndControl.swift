import ActivityKit
import AppIntents
import CooldownCore
import SwiftUI
import WidgetKit

/// CCRM-94 (iOS App): the Live Activity — the Duet with Weekly, wireframe rev B §6 — and the Dynamic Island on iPhone.
/// CCRM-103 (Hybrid Notification), rev D §6: the 5h ring left, the Weekly right. Compact: ring 30 with "34" (no "%"),
/// the Weekly figure trailing. Expanded: ring 48 left of "Teams · Weekly", its figure (34 pt), bar and reset line.
/// Minimal: the bare ring. A plan with no 5h shows its provider mark where the ring would be (B1).
struct CooldownLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CooldownActivityAttributes.self) { context in
            LockPanel(state: context.state)
                .environment(\.palette, .dark)
                .environment(\.colorScheme, .dark)
                .activityBackgroundTint(Color(hex: 0x1C1C1E, alpha: 0.86))
                .activitySystemActionForegroundColor(.white)
                .widgetURL(context.state.rows.first.map { DeepLink.account($0.key) })
        } dynamicIsland: { context in
            let row = context.state.rows.first
            return DynamicIsland {
                DynamicIslandExpandedRegion(.bottom) {
                    if let row {
                        IslandExpanded(row: row)
                            .environment(\.palette, .dark)
                            .environment(\.colorScheme, .dark)
                    }
                }
            } compactLeading: {
                if let row {
                    Group {
                        if let five = row.five {
                            FaceRing(five: five, accent: row.accent, figure: .digits, greyed: row.isWeekGated, blank: row.signIn,
                                     figureSize: 11, surface: .black)
                                .frame(width: 30, height: 30)
                        } else {
                            ProviderMark(provider: row.providerValue, size: 16)
                        }
                    }
                    .environment(\.palette, .dark)
                    .environment(\.colorScheme, .dark)
                }
            } compactTrailing: {
                if let row {
                    FaceFigure(pct: row.weekPct, pace: row.weekPace, accent: row.accent, size: 15, weight: .semibold,
                                  blank: row.signIn, dimmed: row.asOf != nil)
                        .fixedSize()
                        .environment(\.palette, .dark)
                        .environment(\.colorScheme, .dark)
                }
            } minimal: {
                if let row {
                    Group {
                        if let five = row.five {
                            FaceRing(five: five, accent: row.accent, figure: .none, greyed: row.isWeekGated, blank: row.signIn,
                                     surface: .black)
                        } else {
                            ProviderMark(provider: row.providerValue, size: 16)
                        }
                    }
                    .environment(\.palette, .dark)
                    .environment(\.colorScheme, .dark)
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
        case .stale, .aging: value = "\(Display.pct(face.pct, pace: face.pace)) · as of \(face.fetchedAt.map(shortClock) ?? "—")"
        default: value = face.weekGated ? CardContent.weekSpentStamp
            : face.atLimit ? "Limit reached" : Display.pct(face.pct, pace: face.pace)
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
