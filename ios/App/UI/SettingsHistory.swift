import ActivityKit
import CooldownCore
import SwiftUI

/// CCRM-94 (iOS App): Settings and History of wireframe rev B §3. Settings is an iOS grouped list with the Android
/// sections, plus Lock Screen (the Live Activity switch).
struct SettingsScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var p
    @Binding var sheet: Sheet?
    @AppStorage("theme") private var theme = "system"
    @AppStorage("density") private var density = Density.comfortable.rawValue
    @AppStorage("chartSize") private var chartSize = ChartSize.medium.rawValue
    @State private var renaming: Account?
    @State private var newName = ""

    var body: some View {
        NavigationStack {
            List {
                Section("Accounts") {
                    ForEach(model.accounts) { account in
                        HStack(spacing: 10) {
                            ProviderMark(provider: account.provider, size: 20)
                            Text(account.label)
                            PlanChip(plan: model.states[account.key]?.plan)
                            if account.key == model.pinnedKey { Image(systemName: "pin.fill").font(.system(size: 11)).foregroundStyle(p.sub) }
                            Spacer()
                            let needsSignIn = model.states[account.key]?.reauthNeeded == true
                            Text(needsSignIn ? "Sign in again" : "Signed in").font(.system(size: 14))
                                .foregroundStyle(needsSignIn ? p.orange : p.sub)
                        }
                        .contextMenu {
                            Button("Rename", systemImage: "pencil") { newName = account.label; renaming = account }
                            Button("Pin to the top", systemImage: "pin") { model.pin(account.key) }
                            Button("Sign in again", systemImage: "arrow.triangle.2.circlepath") {
                                sheet = account.provider == .claude ? .claudeSignIn : .chatGPTSignIn
                            }
                            Button("Remove", systemImage: "trash", role: .destructive) { Task { await model.remove(account.key) } }
                        }
                    }
                    .onMove { model.move(from: $0, to: $1) }
                    Button { sheet = .addAccount } label: { Label("Add account", systemImage: "plus") }
                }
                Section {
                    ForEach(model.accounts) { account in
                        ResetModeRow(account: account, weekly: false)
                        ResetModeRow(account: account, weekly: true)
                    }
                } header: { Text("Reset alerts") } footer: {
                    Text("“If busy” only alerts when the window that closed peaked at 80% or more. Alerts are set ahead from the reset times Cooldown last saw.")
                }
                LockScreenSection()
                Section {
                    Picker("Theme", selection: $theme) {
                        Text("System").tag("system"); Text("Light").tag("light"); Text("Dark").tag("dark")
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Density")
                        Picker("Density", selection: $density) {
                            Text("Comfortable").tag(Density.comfortable.rawValue); Text("Compact").tag(Density.compact.rawValue)
                        }
                        .pickerStyle(.segmented)
                        Text("Comfortable opens the 5-hour chart and keeps the rest a tap away. Compact folds each card to a title, bar and one line — tap it to open the chart in place.")
                            .font(.system(size: 13)).foregroundStyle(p.sub)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Chart height")
                        Picker("Chart height", selection: $chartSize) {
                            Text("Small").tag(ChartSize.small.rawValue); Text("Medium").tag(ChartSize.medium.rawValue)
                            Text("Large").tag(ChartSize.large.rawValue)
                        }
                        .pickerStyle(.segmented)
                        Text("Large draws everything. Small keeps just the lines — guides, pace, curve — and drops every label; the pace and estimate text below the chart still say the numbers.")
                            .font(.system(size: 13)).foregroundStyle(p.sub)
                    }
                } header: { Text("Appearance") }
                Section {
                    LabeledContent("Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—")
                    Text("Unofficial. Not affiliated with Anthropic or OpenAI. “Claude” is a trademark of Anthropic, PBC; “ChatGPT” is a trademark of OpenAI.")
                        .font(.system(size: 13)).foregroundStyle(p.sub)
                } header: { Text("About") } footer: {
                    Text("Touch and hold an account to rename, pin, sign in again or remove it. Drag to reorder: the order is the same everywhere.")
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { EditButton() }
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
            .alert("Rename account", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
                TextField("Name", text: $newName)
                Button("Save") { if let r = renaming { model.rename(r.key, to: newName) }; renaming = nil }
                Button("Cancel", role: .cancel) { renaming = nil }
            }
        }
    }
}

/// One account's window: Off / If busy / Always (spec mobile §2). Turning one on asks for notification permission.
struct ResetModeRow: View {
    @Environment(AppModel.self) private var model
    let account: Account
    let weekly: Bool
    @State private var mode = "smart"

    var body: some View {
        HStack(spacing: 10) {
            ProviderMark(provider: account.provider, size: 18)
            Text("\(account.label) · \(weekly ? "Weekly" : "5h")").lineLimit(1)
            Spacer(minLength: 8)
            Picker("", selection: $mode) {
                Text("Off").tag("off"); Text("If busy").tag("smart"); Text("Always").tag("always")
            }
            .pickerStyle(.segmented).fixedSize()
        }
        .onAppear { mode = Prefs.resetMode(account.key, weekly: weekly) }
        .onChange(of: mode) { _, new in
            guard new != Prefs.resetMode(account.key, weekly: weekly) else { return }
            Prefs.setResetMode(account.key, weekly: weekly, new)
            Task {
                if new != "off" { await ResetAlerts.requestPermission() }
                model.surfacesChanged()
            }
        }
    }
}

/// The Live Activity switch and which accounts it shows (wireframe rev B §3, §6).
struct LockScreenSection: View {
    @Environment(AppModel.self) private var model
    @AppStorage("liveActivity.on", store: Prefs.defaults) private var on = false
    @State private var picking = false
    /// Mirrors `Prefs.liveActivityKeys` so the ticks and the summary redraw on every tap (UserDefaults isn't observed).
    @State private var keys = Prefs.liveActivityKeys

    var body: some View {
        Section {
            Toggle("Live Activity", isOn: $on)
                .onChange(of: on) { model.surfacesChanged() }
            NavigationLink {
                List(model.accounts) { account in
                    Button { toggle(account.key) } label: {
                        HStack {
                            ProviderMark(provider: account.provider, size: 18)
                            Text(account.label)
                            Spacer()
                            if shown.contains(account.key) { Image(systemName: "checkmark") }
                        }
                    }
                }
                .navigationTitle("Shows")
            } label: {
                LabeledContent("Shows", value: model.accounts.filter { shown.contains($0.key) }.map(\.label).joined(separator: ", "))
            }
        } header: { Text("Lock Screen") } footer: {
            Text(ActivityAuthorizationInfo().areActivitiesEnabled
                 ? "Two accounts’ 5h and Weekly on the lock screen while a window runs. iOS keeps it up to 8 hours and it updates when Cooldown checks."
                 : "Live Activities are switched off for Cooldown in the iOS Settings app.")
        }
    }

    private var shown: [String] { keys ?? model.accounts.prefix(2).map(\.key) }

    /// Up to two; picking a third replaces the oldest.
    private func toggle(_ key: String) {
        var keys = shown
        if let i = keys.firstIndex(of: key) { keys.remove(at: i) } else { keys.append(key); if keys.count > 2 { keys.removeFirst() } }
        Prefs.liveActivityKeys = keys
        self.keys = keys
        model.surfacesChanged()
    }
}

/// History: Android's screen as shipped — per account, 5-hour / 7-day, week by week.
struct HistoryScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    @State private var key: String?
    @State private var kind = HistoryStats.sessionKind
    @State private var weekIndex = 0

    var body: some View {
        let account = model.account(key ?? model.selectedKey) ?? model.accounts.first
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                AccountTabs(accounts: model.accounts, selected: Binding(get: { account?.key }, set: { key = $0; weekIndex = 0 }))
                    .padding(.horizontal, -16)
                Picker("Window", selection: $kind) {
                    Text("5-hour").tag(HistoryStats.sessionKind)
                    Text("7-day").tag(HistoryStats.weeklyKind)
                }
                .pickerStyle(.segmented).frame(width: 200)
                .onChange(of: kind) { weekIndex = 0 }
                if let account { weekCard(account) }
                Text("History is recorded as each window closes, so it fills in over time. Peak %s are sampled when the app checks.")
                    .font(.system(size: 13.5)).foregroundStyle(p.txt)
            }
            .padding(.horizontal, 16).padding(.top, 8)
        }
        .background(p.bg.ignoresSafeArea())
        .navigationTitle("Usage history")
        .navigationBarTitleDisplayMode(.large)
    }

    @ViewBuilder private func weekCard(_ account: Account) -> some View {
        let weeks = HistoryStats.weeks(bars: bars(account), timeZone: .current)
        let index = min(weekIndex, max(weeks.count - 1, 0))
        CardBox {
            if weeks.isEmpty {
                Text("No closed windows yet.").foregroundStyle(p.txt).frame(maxWidth: .infinity)
            } else {
                let week = weeks[index]
                HStack {
                    Button { weekIndex = index + 1 } label: { Image(systemName: "chevron.left") }
                        .disabled(index + 1 >= weeks.count).accessibilityLabel("Earlier week")
                    VStack(spacing: 2) {
                        Text(index == 0 ? "This week" : index == 1 ? "Last week" : HistoryContent.weekTitle(mondayISO: week.monday))
                            .font(.system(size: 17, weight: .semibold))
                        Text(range(week.monday)).font(.system(size: 14)).foregroundStyle(p.txt)
                    }
                    .frame(maxWidth: .infinity)
                    Button { weekIndex = index - 1 } label: { Image(systemName: "chevron.right") }
                        .disabled(index == 0).accessibilityLabel("Later week")
                }
                .foregroundStyle(p.ink)
                let maxed = week.bars.filter(\.hitLimit).count
                Text("\(week.bars.count) \(kind == HistoryStats.sessionKind ? "sessions" : "weeks")\(maxed > 0 ? " · \(maxed) maxed out" : "")")
                    .font(.system(size: 14)).foregroundStyle(p.txt).frame(maxWidth: .infinity).padding(.vertical, 10)
                ForEach(week.bars, id: \.resetMs) { bar in
                    HStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(CardContent.dayClock(bar.startMs, model.nowMs, false, .current)).font(.system(size: 13.5)).foregroundStyle(p.txt)
                            if bar.current {
                                Text("now").font(.system(size: 12.5, weight: .semibold)).foregroundStyle(account.provider.accent(scheme))
                            }
                        }
                        .frame(width: 104, alignment: .leading)
                        UsageBar(pct: bar.peakPct, pace: nil, accent: account.provider.accent(scheme), height: 11)
                        Text("\(Int(bar.peakPct))%").font(.system(size: 13.5)).monospacedDigit()
                            .foregroundStyle(bar.peakPct >= 100 ? p.red : p.txt).frame(width: 44, alignment: .trailing)
                    }
                    .padding(.vertical, 3)
                }
            }
        }
    }

    private func bars(_ account: Account) -> [HistoryStats.Bar] {
        let records = model.records[account.key] ?? []
        let usage = model.usage(account.key)
        let window = kind == HistoryStats.sessionKind ? usage?.session : usage?.weekly
        let state = model.states[account.key]
        var current: HistoryStats.Bar?
        if let window, let reset = window.resetsAtMs {
            let peak = max(window.percent ?? 0, kind == HistoryStats.sessionKind ? state?.sessionPeak ?? 0 : state?.weeklyPeak ?? 0)
            current = .init(startMs: reset - HistoryStats.windowMs(kind: kind), resetMs: reset, peakPct: peak,
                            hitLimit: peak >= HistoryStats.hitLimitThreshold, current: true)
        }
        return HistoryStats.bars(records: records, kind: kind, current: current)
    }

    /// "20–26 Oct" for the week starting on that Monday.
    private func range(_ mondayISO: String) -> String {
        let parse = DateFormatter()
        parse.locale = Locale(identifier: "en_US_POSIX"); parse.dateFormat = "yyyy-MM-dd"
        guard let monday = parse.date(from: mondayISO),
              let sunday = Calendar(identifier: .gregorian).date(byAdding: .day, value: 6, to: monday) else { return mondayISO }
        let day = DateFormatter(); day.locale = Locale(identifier: "en_GB"); day.dateFormat = "d"
        let dayMonth = DateFormatter(); dayMonth.locale = Locale(identifier: "en_GB"); dayMonth.dateFormat = "d MMM"
        let sameMonth = Calendar.current.component(.month, from: monday) == Calendar.current.component(.month, from: sunday)
        return "\(sameMonth ? day.string(from: monday) : dayMonth.string(from: monday))–\(dayMonth.string(from: sunday))"
    }
}
