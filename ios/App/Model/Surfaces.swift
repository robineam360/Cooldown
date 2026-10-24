import ActivityKit
import BackgroundTasks
import CooldownCore
import Foundation
import UserNotifications
import WidgetKit

/// CCRM-94 (iOS App): what happens after every fetch — widgets and controls reload, the Live Activity updates, reset
/// alerts are rescheduled. iOS can't run anything at the moment a window resets, so everything is prepared ahead
/// from the known reset times (spec mobile §1–§2).
@MainActor
enum Surfaces {
    static func update(_ model: AppModel) async {
        guard !model.isDemo else { return }
        WidgetCenter.shared.reloadAllTimelines()
        ControlCenter.shared.reloadAllControls()
        let faces = Faces.load(storage: model.storage, nowMs: model.nowMs)
        await LiveActivities.sync(faces: faces)
        await ResetAlerts.schedule(faces: faces, states: model.states)
    }
}

// MARK: - Live Activity (wireframe rev B §6)

@MainActor
enum LiveActivities {
    /// The accounts it shows: the two picked in Settings, else the first two in your order — those with a window
    /// running, or a sign-in to fix.
    static func rows(_ faces: [Face]) -> [CooldownActivityAttributes.Row] {
        let keys = Prefs.liveActivityKeys ?? faces.prefix(2).map(\.account.key)
        return faces.filter { keys.contains($0.account.key) && ($0.reset != nil || $0.status == .signIn) }
            .prefix(2).map(CooldownActivityAttributes.Row.init)
    }

    static func sync(faces: [Face]) async {
        let running = Activity<CooldownActivityAttributes>.activities
        guard Prefs.liveActivityOn, ActivityAuthorizationInfo().areActivitiesEnabled else {
            for activity in running { await activity.end(nil, dismissalPolicy: .immediate) }
            return
        }
        let rows = rows(faces)
        if rows.isEmpty {
            // Every window it showed has reset: a final frame for a few minutes, then iOS removes it. It re-arms when
            // a fetch sees a new window.
            for activity in running {
                let last = activity.content.state.rows.first
                let ended = last.map { CooldownActivityAttributes.Ended(label: $0.label, provider: $0.provider, peak: Int($0.pct ?? 0)) }
                await activity.end(ActivityContent(state: .init(rows: [], ended: ended), staleDate: nil),
                                   dismissalPolicy: .after(.now.addingTimeInterval(5 * 60)))
            }
            return
        }
        // Stale when the first window resets, so iOS greys it if the app hasn't refreshed by then.
        let stale = rows.compactMap(\.reset).min()
        let content = ActivityContent(state: CooldownActivityAttributes.ContentState(rows: rows, ended: nil), staleDate: stale)
        if let activity = running.first {
            await activity.update(content)
        } else {
            _ = try? Activity.request(attributes: CooldownActivityAttributes(), content: content)
        }
    }
}

// MARK: - Reset alerts (spec mobile §2)

enum ResetAlerts {
    static func requestPermission() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
    }

    /// One pending alert per account and window, at its reset. "If busy" is decided from the peak the last check saw.
    static func schedule(faces: [Face], states: [String: AccountState]) async {
        let center = UNUserNotificationCenter.current()
        let now = Date.now
        for face in faces {
            for weekly in [false, true] {
                let key = face.account.key
                let id = "reset.\(key).\(weekly ? "weekly" : "session")"
                center.removePendingNotificationRequests(withIdentifiers: [id])
                let mode = Prefs.resetMode(key, weekly: weekly)
                guard mode != "off", face.status != .plan, let reset = weekly ? face.weeklyReset : face.reset, reset > now else { continue }
                if mode == "smart" {
                    let state = states[key] ?? AccountState()
                    let peak = max(weekly ? state.weeklyPeak : state.sessionPeak, (weekly ? face.weeklyPct : face.pct) ?? 0)
                    guard peak >= 80 else { continue }
                }
                let content = UNMutableNotificationContent()
                content.title = "\(face.account.label): \(weekly ? "Weekly" : "5h") reset"
                content.body = "Usage is back at 0%."
                content.sound = .default
                content.threadIdentifier = key
                content.userInfo = ["key": key]
                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, reset.timeIntervalSince(now)), repeats: false)
                try? await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
            }
        }
    }
}

// MARK: - Background refresh

enum BackgroundRefresh {
    static let id = "com.robinrichardrajan.cooldown.refresh"

    /// Asks iOS for a refresh in 15 minutes or later; iOS decides when (spec mobile §1).
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: id)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}
