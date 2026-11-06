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
    @AppStorage("theme", store: Prefs.standard) private var theme = "system"
    @AppStorage("density", store: Prefs.standard) private var density = Density.comfortable.rawValue
    @AppStorage("chartSize", store: Prefs.standard) private var chartSize = ChartSize.medium.rawValue
    @State private var renaming: Account?
    @State private var noMail = false
    @Environment(\.openURL) private var openURL
    @State private var details: Account?
    @State private var colouring: Account?
    @State private var clearing: Account?
    @State private var removing: Account?
    @AppStorage(Display.Key.timeFormat, store: Prefs.defaults) private var timeFormat = "system"
    @State private var usageMode = Display.mode.rawValue
    @State private var endMark = Display.usageEndMark
    @State private var openOnSummary = Prefs.openOnSummary
    @State private var summaryOrder = Prefs.summaryOrder
    @AppStorage(Display.Key.chartDown, store: Prefs.defaults) private var chartDown = false
    @AppStorage(Display.Key.clockFirst, store: Prefs.defaults) private var clockFirst = false
    @AppStorage(Display.Key.redPastPace, store: Prefs.defaults) private var redPastPace = true
    @AppStorage(Display.Key.themeColour, store: Prefs.defaults) private var themeColour = ThemeColours.perProvider

    private var chevron: some View {
        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(p.sub.opacity(0.7))
    }

    var body: some View {
        NavigationStack {
            List {
                Section("Accounts") {
                    ForEach(model.accounts) { account in
                        // CCRM-96 (Weekly First) rev B §2: the row pushes the account's page (Plan, Accent colour,
                        // Headline, Model limits, Details, Clear history); the touch-and-hold menu stays.
                        NavigationLink {
                            AccountPage(account: account, onRename: { renaming = account },
                                        onSignIn: { sheet = model.signInAgain(account) })
                        } label: {
                        HStack(spacing: 10) {
                            ProviderMark(provider: account.provider, size: 20)
                            Text(account.label)
                            PlanChip(provider: account.provider, plan: model.states[account.key]?.plan,
                                     tier: model.states[account.key]?.tier)
                            Spacer()
                            let word = AccountStatus.listWord(provider: account.provider, state: model.states[account.key] ?? AccountState(),
                                                              addedAtMs: account.addedAtMs, nowMs: model.nowMs)
                            Text(model.isSample ? SampleRules.accountWord : word.text).font(.system(size: 14))
                                .foregroundStyle(model.isSample || word.tone == nil ? p.sub : p.orange)
                        }
                        }
                        .contextMenu {
                            Button("Rename", systemImage: "pencil") { renaming = account }
                            Button("Details…", systemImage: "info.circle") { details = account }
                            Button("Accent colour…", systemImage: "paintpalette") { colouring = account }
                            if !model.isSample {
                                Button("Sign in again", systemImage: "arrow.triangle.2.circlepath") {
                                    sheet = model.signInAgain(account)
                                }
                            }
                            Button("Clear usage history…", systemImage: "clock.arrow.circlepath") { clearing = account }
                            if !model.isSample { Button("Remove", systemImage: "trash", role: .destructive) { removing = account } }
                        }
                    }
                    .onMove { model.move(from: $0, to: $1) }
                    if !model.isSample { Button { sheet = .addAccount } label: { Label("Add account", systemImage: "plus") } }
                }
                // Android's "Usage credits" switches: one per account whose reading reports credits.
                let withCredits = model.accounts.filter { CardContent.credits(model.usage($0.key)?.credits) != nil }
                if !withCredits.isEmpty {
                    Section {
                        ForEach(withCredits) { account in
                            Toggle(isOn: Binding(get: { Display.creditsVisible(account.key) },
                                                 set: { Display.setCreditsVisible($0, for: account.key); model.displayChanged() })) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Show for \(account.label)")
                                    Text("Credits card on this account's screen").font(.system(size: 13)).foregroundStyle(p.sub)
                                }
                            }
                        }
                    } header: { Text("Usage credits") }
                }
                // The sample data has no notifications or Live Activity to configure (rev B §7).
                if !model.isSample {
                Section {
                    ForEach(model.accounts) { account in
                        ResetModeRow(account: account, weekly: false)
                        ResetModeRow(account: account, weekly: true)
                    }
                } header: { Text("Reset alerts") } footer: {
                    Text("“If busy” only alerts when the window that closed peaked at 80% or more. Alerts are set ahead from the reset times Cooldown last saw.")
                }
                LockScreenSection()
                }
                Section {
                    Picker("Theme", selection: $theme) {
                        Text("System").tag("system"); Text("Light").tag("light"); Text("Dark").tag("dark")
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Theme colour")
                        ColourGrid(first: .perProvider, selection: Binding(
                            get: { themeColour == ThemeColours.perProvider ? nil : themeColour },
                            set: { themeColour = $0 ?? ThemeColours.perProvider; model.displayChanged() }))
                        Text(themeColour == ThemeColours.perProvider ? "Selected: Per provider: Claude orange, ChatGPT green."
                             : "Selected: \(themeColour). Every account's bars, numbers and charts use it, unless the account has its own accent colour.")
                            .font(.system(size: 13)).foregroundStyle(p.sub)
                    }
                    choice("Time format", $timeFormat, [("System", "system"), ("12-hour", "12"), ("24-hour", "24")],
                           note: "Times shown like \(CardContent.dayClock(Self.sampleTime, Self.sampleTime, Display.use24h, .current)).")
                    // CCRM-100 (Vs Pace Display): a third value. Stored as Display.setMode writes it (v1.9 then reads Used).
                    choice("Usage display", Binding(get: { usageMode }, set: { usageMode = $0; Display.setMode(PaceText.Mode(rawValue: $0) ?? .used) }),
                           [("Used", PaceText.Mode.used.rawValue), ("Left", PaceText.Mode.left.rawValue), ("Vs pace", PaceText.Mode.vsPace.rawValue)],
                           note: "How percentages read on every surface. Bars and colours always show what's spent. "
                               + (usageMode == PaceText.Mode.left.rawValue ? "Shows “53% left”: what remains of each window."
                                  : usageMode == PaceText.Mode.vsPace.rawValue ? "Shows “+10%” — how far under the pace mark you are (“−20%” over it): green under, red over."
                                  : "Shows “47% used”: what each window has consumed."))
                    // CCRM-103 (Hybrid Notification) rev D §1e: how a bar or ring ends at the usage. Line by default.
                    VStack(alignment: .leading, spacing: 8) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Usage end mark")
                            Text("How a bar or ring ends at your usage").font(.system(size: 13)).foregroundStyle(p.sub)
                        }
                        Picker("Usage end mark", selection: Binding(get: { endMark }, set: { mark in
                            endMark = mark
                            Display.setUsageEndMark(mark)
                            model.displayChanged()
                        })) {
                            Text("Round").tag(UsageEndMark.round); Text("Line").tag(UsageEndMark.line)
                            Text("Pace colour").tag(UsageEndMark.pace)
                        }
                        .pickerStyle(.segmented)
                        EndMarkPreviews()
                    }
                    // CCRM-98 (Summary Tab) P2: shown only with two or more accounts, on by default.
                    if SummaryRank.tabShown(accountCount: model.accounts.count) {
                        VStack(alignment: .leading, spacing: 6) {
                            Toggle("Open on the summary", isOn: Binding(get: { openOnSummary }, set: { openOnSummary = $0; Prefs.openOnSummary = $0 }))
                            Text("With two or more accounts, start on the All tab. Off: start on the last account you looked at.")
                                .font(.system(size: 13)).foregroundStyle(p.sub)
                        }
                        // Robin, 2026-11-02: the All tab's order, beside Open on the summary and shown with it.
                        Picker("Summary order", selection: Binding(get: { summaryOrder }, set: { order in
                            summaryOrder = order
                            Prefs.summaryOrder = order
                            model.displayChanged()
                        })) {
                            ForEach(SummaryOrder.allCases, id: \.self) { Text($0.label).tag($0) }
                        }
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Density")
                        Picker("Density", selection: $density) {
                            Text("Comfortable").tag(Density.comfortable.rawValue); Text("Compact").tag(Density.compact.rawValue)
                        }
                        .pickerStyle(.segmented)
                        Text("Comfortable opens the first card's chart and keeps the rest a tap away. Compact folds each card to a title, bar and one line — tap it to open the chart in place.")
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
                    choice("Chart orientation", $chartDown, [("Time across", false), ("Time down", true)],
                           note: "Time down puts usage on the same axis as the bar above it.")
                    choice("Reset time", $clockFirst, [("Countdown", false), ("Clock time", true)],
                           note: "Which form leads wherever a reset is shown. Where there's room, the other form keeps the second slot. "
                               + (clockFirst ? "Leads with “resets at 9:50 pm”." : "Leads with “resets in 2h 14m”, collapsing to “resets soon” inside five minutes."))
                    VStack(alignment: .leading, spacing: 6) {
                        Toggle("Show red past the pace mark", isOn: $redPastPace).onChange(of: redPastPace) { model.displayChanged() }
                        Text("Colours the bar past the even-pace tick, in the app, widgets and Live Activity. Off keeps the tick without the colour.")
                            .font(.system(size: 13)).foregroundStyle(p.sub)
                    }
                } header: { Text("Appearance") }
                Section {
                    LabeledContent("Version", value: AboutInfo.version())
                    LabeledContent("Made by", value: AboutInfo.author)
                    Button {
                        guard let url = AboutInfo.currentFeedbackURL() else { return }
                        openURL(url) { accepted in if !accepted { noMail = true } }
                    } label: {
                        HStack { Text("Share feedback").foregroundStyle(p.ink); Spacer(); chevron }
                    }
                    if let url = URL(string: AboutInfo.privacyURL) {
                        Link(destination: url) {
                            HStack { Text("Privacy policy").foregroundStyle(p.ink); Spacer(); chevron }
                        }
                    }
                    Text("Unofficial. Not affiliated with Anthropic, OpenAI or Google. “Claude” is a trademark of Anthropic, PBC; “ChatGPT” is a trademark of OpenAI; “Gemini” and “Antigravity” are trademarks of Google LLC.")
                        .font(.system(size: 13)).foregroundStyle(p.sub)
                } header: { Text("About") } footer: {
                    Text("Touch and hold an account to rename it, sign in again or remove it. Drag to reorder: the order is the same everywhere.")
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { EditButton() }
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
            .sheet(item: $renaming) { RenameSheet(account: $0) }
            .alert("Email me at \(AboutInfo.feedbackAddress)", isPresented: $noMail) { Button("OK", role: .cancel) {} }
            .alert("Remove “\(removing?.label ?? "")”?", isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } })) {
                Button("Cancel", role: .cancel) { removing = nil }
                Button("Remove account", role: .destructive) { if let r = removing { Task { await model.remove(r.key) } }; removing = nil }
            } message: {
                Text(RemoveCopy.message(accountCount: model.accounts.count))
            }
            .alert("Clear usage history?", isPresented: Binding(get: { clearing != nil }, set: { if !$0 { clearing = nil } })) {
                Button("Cancel", role: .cancel) { clearing = nil }
                Button("Clear", role: .destructive) { if let c = clearing { model.clearHistory(c.key) }; clearing = nil }
            } message: {
                Text("This clears, for “\(clearing?.label ?? "")” only:\n•  8 days of trend history\n•  a year of session and weekly history\n\nCurrent readings and the sign-in stay.")
            }
            .sheet(item: $details) { AccountDetailsSheet(account: $0) }
            .sheet(item: $colouring) { AccentSheet(account: $0) }
        }
    }
}

extension SettingsScreen {
    /// "Thu 11:45 pm" in the chosen format, for the Time format note.
    static let sampleTime: Int64 = {
        var c = DateComponents(); c.year = 2026; c.month = 10; c.day = 22; c.hour = 23; c.minute = 45
        return Int64((Calendar.current.date(from: c) ?? .now).timeIntervalSince1970 * 1000)
    }()

    /// A segmented choice with its note underneath, as Android's Appearance rows read.
    func choice<V: Hashable>(_ title: String, _ value: Binding<V>, _ options: [(String, V)], note: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
            Picker(title, selection: value) {
                ForEach(options, id: \.1) { Text($0.0).tag($0.1) }
            }
            .pickerStyle(.segmented)
            .onChange(of: value.wrappedValue) { model.displayChanged() }
            Text(note).font(.system(size: 13)).foregroundStyle(p.sub)
        }
    }
}

/// Appearance › Usage end mark's three previews (rev D §1e): each an over-pace ring (36 pt) and bar (54 × 8 pt), 64%
/// against a 52% mark, drawn Round · Line · Pace colour.
struct EndMarkPreviews: View {
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let accent = Provider.claude.accent(scheme)
        HStack(spacing: 18) {
            ForEach(UsageEndMark.allCases, id: \.self) { mark in
                HStack(spacing: 8) {
                    GrammarRing(pct: 64, pace: 52, accent: accent, figure: Display.ringNumber(64, pace: 52),
                                figureColour: Display.isDistance(64, pace: 52)
                                    ? Display.figureColour(64, pace: 52, ink: p.ink, usage: p.ink, scheme) : nil,
                                endMark: mark, surface: p.card)
                        .frame(width: 36, height: 36)
                    GrammarBar(pct: 64, pace: 52, accent: accent, height: 8, showsRoom: false, endMark: mark, surface: p.card)
                        .frame(width: 54)
                }
            }
        }
        .padding(.top, 4)
        .accessibilityHidden(true)
    }
}

/// CCRM-96 (Weekly First) rev B §2 and CCRM-99 (Model Limits Toggle): one account's page — Plan, Accent colour,
/// Headline (Weekly · 5-hour · Auto; greyed with its reason on a plan with no 5h), Show model limits (only while the
/// plan reports one), Details and Clear usage history.
struct AccountPage: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss
    let account: Account
    /// The Settings screen owns the rename sheet, the sign-in sheets and the Remove alert; the page asks for them.
    var onRename: () -> Void = {}
    var onSignIn: () -> Void = {}
    @State private var removing = false
    @State private var headline = Headline.default
    @State private var tokenExpiry: Int64?
    @State private var modelLimits = true
    @State private var colouring = false
    @State private var details = false
    @State private var clearing = false

    /// Parity rev B §1-2: the status line with its check-now button, the condition rows, and the sign-in action.
    @ViewBuilder private var signInSection: some View {
        let state = model.states[account.key] ?? AccountState()
        let broken = state.reauthNeeded || state.lastKind == "authNeeded"
        Section {
            HStack(spacing: 8) {
                Text(AccountStatus.line(provider: account.provider, state: state, tokenExpiresAtMs: tokenExpiry,
                                        busy: model.isRefreshing, nowMs: model.nowMs))
                    .font(.system(size: 13.5)).foregroundStyle(p.sub)
                Spacer(minLength: 8)
                Button {
                    Task { await model.refresh(manual: true) }
                } label: {
                    Group {
                        if model.isRefreshing { ProgressView().controlSize(.small) }
                        else { Image(systemName: "arrow.clockwise").font(.system(size: 14, weight: .semibold)) }
                    }
                    .frame(width: 28, height: 28)
                    .overlay(Circle().stroke(p.sub.opacity(0.3), lineWidth: 1))
                }
                .buttonStyle(.borderless)
                .disabled(model.isRefreshing || model.isDemo)
                .accessibilityLabel("Check now")
            }
            ForEach(AccountStatus.conditions(provider: account.provider, state: state, addedAtMs: account.addedAtMs, nowMs: model.nowMs), id: \.text) { row in
                Text(row.text).font(.system(size: 13.5)).foregroundStyle(row.tone == .red ? p.red : p.orange)
            }
            if !model.isSample {
                Button(account.provider == .chatgpt ? "Sign in with a code" : "Sign in again", action: onSignIn)
                    .font(.system(size: 16, weight: broken ? .semibold : .regular))
                    .foregroundStyle(account.accent(scheme))
            }
        } header: { Text("Sign-in") } footer: {
            if account.provider == .chatgpt && !model.isSample {
                Text("Shows a short code to type at auth.openai.com — on this phone or any other device.")
            }
        }
    }

    var body: some View {
        let usage = model.usage(account.key)
        let hasSession = usage?.session != nil || usage == nil
        let caps = usage?.modelCaps ?? []
        List {
            Section("Account") {
                // Parity rev B §1: the Name row renames (one sheet, shared with the touch-and-hold Rename).
                Button(action: onRename) {
                    LabeledContent("Name") {
                        HStack(spacing: 4) {
                            Text(account.label).lineLimit(1).truncationMode(.tail)
                            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(p.sub.opacity(0.7))
                        }
                    }
                }
                .foregroundStyle(p.ink)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Plan")
                    // API-CONTRACT §11's name, as the pill shows it.
                    Text(model.states[account.key]?.plan.map { PlanNames.display(account.provider, $0, tier: model.states[account.key]?.tier) } ?? "—")
                        .font(.system(size: 15)).foregroundStyle(p.sub)
                }
                Button { colouring = true } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Accent colour…").foregroundStyle(p.ink)
                        Circle().fill(account.accent(scheme)).frame(width: 12, height: 12)
                    }
                }
            }
            signInSection
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Lead with")
                    Text("What widgets, the lock screen, the Live Activity, the iPad sidebar and the card order show first.")
                        .font(.system(size: 13)).foregroundStyle(p.sub)
                    Picker("Lead with", selection: Binding(get: { hasSession ? headline : .weekly }, set: { new in
                        headline = new
                        Prefs.setHeadline(account.key, new)
                        model.displayChanged()
                    })) {
                        Text("Weekly").tag(Headline.weekly); Text("5-hour").tag(Headline.fiveHour); Text("Auto").tag(Headline.auto)
                    }
                    .pickerStyle(.segmented)
                    .disabled(!hasSession)
                }
            } header: { Text("Headline") } footer: {
                Text(hasSession ? "Auto shows Weekly, and switches to 5h while 5h is past 90% or on course to run out before it resets."
                     : "This plan has no 5-hour window, so Weekly is always the headline.")
            }
            if !caps.isEmpty {
                let names = caps.map { $0.modelName.replacingOccurrences(of: "Claude ", with: "") }
                Section("Model limits") {
                    Toggle(isOn: Binding(get: { modelLimits }, set: { on in
                        modelLimits = on
                        Prefs.setShowModelLimits(account.key, on)
                        model.displayChanged()
                    })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Show model limits")
                            Text(modelLimits
                                 ? caps.map { "\($0.modelName.replacingOccurrences(of: "Claude ", with: "")) · \(Int($0.window.percent ?? 0))% this week" }.joined(separator: ", ")
                                 : "Hidden: \(names.joined(separator: ", ")). The limit still applies to that model; Cooldown just stops drawing it.")
                                .font(.system(size: 13)).foregroundStyle(p.sub)
                        }
                    }
                }
            }
            Section("Usage") {
                Button("Details…") { details = true }.foregroundStyle(p.ink)
                Button("Clear usage history…") { clearing = true }.foregroundStyle(p.red)
            }
            if !model.isSample {
                Section {
                    Button { removing = true } label: { Text("Remove account").frame(maxWidth: .infinity) }
                        .foregroundStyle(p.red)
                }
            }
        }
        .navigationTitle(account.label)
        .onAppear {
            headline = Prefs.headline(account.key)
            modelLimits = Prefs.showModelLimits(account.key)
            tokenExpiry = model.tokenExpiresAtMs(account.key)
        }
        .onChange(of: model.isRefreshing) { tokenExpiry = model.tokenExpiresAtMs(account.key) }
        .alert("Remove “\(account.label)”?", isPresented: $removing) {
            Button("Cancel", role: .cancel) {}
            Button("Remove account", role: .destructive) { let key = account.key; dismiss(); Task { await model.remove(key) } }
        } message: { Text(RemoveCopy.message(accountCount: model.accounts.count)) }
        .sheet(isPresented: $colouring) { AccentSheet(account: account) }
        .sheet(isPresented: $details) { AccountDetailsSheet(account: account) }
        .alert("Clear usage history?", isPresented: $clearing) {
            Button("Cancel", role: .cancel) {}
            Button("Clear", role: .destructive) { model.clearHistory(account.key) }
        } message: {
            Text("This clears, for “\(account.label)” only:\n•  8 days of trend history\n•  a year of session and weekly history\n\nCurrent readings and the sign-in stay.")
        }
    }
}

/// The colour swatches shared by Theme colour (led by "Per provider") and an account's Accent colour (led by its
/// provider's own colour). `nil` is the leading choice.
struct ColourGrid: View {
    enum First { case perProvider, provider(Provider) }
    let first: First
    @Binding var selection: String?
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 6), spacing: 10) {
            swatch(nil)
            ForEach(ThemeColours.options) { swatch($0.name) }
        }
        .padding(.vertical, 2)
    }

    private func swatch(_ name: String?) -> some View {
        let on = selection == name
        return Button { selection = name } label: {
            ZStack {
                if let name, let option = ThemeColours.option(name) {
                    Circle().fill(scheme == .dark ? option.dark : option.light)
                } else {
                    switch first {
                    case .perProvider:
                        // Three equal bands since CCRM-55 (rev B §6 default).
                        Circle().fill(AngularGradient(stops: [
                            .init(color: Provider.claude.accent(scheme), location: 0), .init(color: Provider.claude.accent(scheme), location: 1 / 3),
                            .init(color: Provider.chatgpt.accent(scheme), location: 1 / 3), .init(color: Provider.chatgpt.accent(scheme), location: 2 / 3),
                            .init(color: Provider.antigravity.accent(scheme), location: 2 / 3), .init(color: Provider.antigravity.accent(scheme), location: 1),
                        ], center: .center))
                    case .provider(let provider):
                        Circle().fill(provider.accent(scheme))
                    }
                }
                if on { Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(.white) }
            }
            .frame(width: 36, height: 36)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(name ?? (first.isPerProvider ? "Per provider" : "Provider colour"))
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

extension ColourGrid.First {
    var isPerProvider: Bool { if case .perProvider = self { true } else { false } }
}

/// Touch and hold → Accent colour…: the account's own colour over the theme colour (Android's three levels).
struct AccentSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var p
    let account: Account
    @State private var name: String?

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                ColourGrid(first: .provider(account.provider), selection: Binding(
                    get: { name }, set: { name = $0; Display.setAccentName($0, for: account.key); model.displayChanged() }))
                Text("First swatch: provider colour (default). This account's bars, numbers, charts and widgets use the colour picked here, whatever the theme colour.")
                    .font(.system(size: 13)).foregroundStyle(p.sub)
                Spacer(minLength: 0)
            }
            .padding(20)
            .navigationTitle("Accent colour · \(account.provider.displayName)").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .presentationDetents([.medium])
        .onAppear { name = Display.accentName(account.key) }
    }
}

/// Touch and hold → Details…: what Android's dialog lists, from what this app records.
struct AccountDetailsSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let account: Account

    var body: some View {
        let state = model.states[account.key]
        NavigationStack {
            List {
                if let plan = state?.plan {
                    LabeledContent("Plan", value: PlanNames.display(account.provider, plan, tier: state?.tier))
                }
                if let email = state?.email { LabeledContent("Email", value: email) }
                LabeledContent("Last checked", value: stamp(state?.lastAttemptAtMs ?? 0))
                LabeledContent("Last successful check", value: stamp(state?.lastSuccessAtMs ?? 0))
                if state?.reauthNeeded == true {
                    Text("Sign-in stopped working: sign in again from the account's menu.").foregroundStyle(.orange)
                } else if account.provider == .claude, let added = account.addedAtMs {
                    let expiry = added + 30 * 86_400_000
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Sign-in expires around \(CardContent.dayClock(expiry, model.nowMs, Display.use24h, .current)) · ~\(max(0, (expiry - model.nowMs) / 86_400_000))d left")
                        Text("Anthropic doesn't report the real expiry. This is a flat ~30-day estimate from sign-in, corrected if renewal stops working earlier.")
                            .font(.system(size: 13)).foregroundStyle(.secondary)
                    }
                }
                LabeledContent("Added", value: account.addedAtMs.map { Date(ms: $0).formatted(date: .abbreviated, time: .omitted) } ?? "—")
            }
            .navigationTitle(account.label).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Close") { dismiss() } } }
        }
    }

    private func stamp(_ ms: Int64) -> String {
        guard ms > 0 else { return "never" }
        let ago = Fmt.compactDuration(ms: max(model.nowMs - ms, 0))
        return "\(CardContent.dayClock(ms, model.nowMs, Display.use24h, .current)) · \(ago) ago"
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
/// The Remove alert's message (Android's lines, adapted; the last account adds a line about the first-launch screen).
enum RemoveCopy {
    static func message(accountCount: Int) -> String {
        "This deletes, permanently and only for this account:\n•  the saved sign-in\n•  its cached usage and settings — alerts, credits, reset alerts\n•  8 days of trend history\n•  a year of session and weekly history"
            + (accountCount == 1 ? "\n\nThis is your only account. Cooldown goes back to its first-launch screen." : "")
    }
}

/// CCRM-94 (iOS App) parity wireframes rev B §4: History's layout rules.
enum HistoryLayout {
    /// iPad (and a wide Split View) shows 5-hour and 7-day side by side from this width.
    static let twoColumnWidth: CGFloat = 600
    static func twoColumns(width: CGFloat) -> Bool { width >= twoColumnWidth }
    static let maxContentWidth: CGFloat = 1100

    static func footnote(twoColumns: Bool, kind: String) -> String {
        "History is recorded as each window closes, so it fills in over time. Peak "
            + (twoColumns ? "5h and weekly" : kind == HistoryStats.sessionKind ? "5h" : "weekly")
            + " readings are sampled when the app checks."
    }
}

struct HistoryScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    @State private var key: String?
    @State private var kind = HistoryStats.sessionKind
    @State private var weekIndex5 = 0
    @State private var weekIndex7 = 0
    @State private var width: CGFloat = 0

    private func index(_ kind: String) -> Binding<Int> {
        kind == HistoryStats.sessionKind ? $weekIndex5 : $weekIndex7
    }

    var body: some View {
        let account = model.account(key ?? model.selectedKey) ?? model.accounts.first
        let state = account.flatMap { model.states[$0.key] }
        // A plan with no 5-hour window has one pane at every width (no picker, no columns).
        let usage = account.flatMap { model.usage($0.key) }
        let hasSession = usage == nil || usage?.session != nil
        let two = hasSession && HistoryLayout.twoColumns(width: width)
        let activeKind = hasSession ? kind : HistoryStats.weeklyKind
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                AccountTabs(accounts: model.accounts, selected: Binding(get: { account?.key }, set: { key = $0; weekIndex5 = 0; weekIndex7 = 0 }))
                    .padding(.horizontal, -16)
                if let account {
                    if two {
                        HStack(alignment: .top, spacing: 16) {
                            pane(account, kind: HistoryStats.sessionKind, heading: "5-hour windows", state: state, two: true)
                            pane(account, kind: HistoryStats.weeklyKind, heading: "7-day windows", state: state, two: true)
                        }
                    } else {
                        if hasSession {
                            Picker("Window", selection: $kind) {
                                Text("5-hour").tag(HistoryStats.sessionKind)
                                Text("7-day").tag(HistoryStats.weeklyKind)
                            }
                            .pickerStyle(.segmented).frame(width: 200)
                        }
                        pane(account, kind: activeKind, heading: nil, state: state, two: false)
                    }
                }
                Text(HistoryLayout.footnote(twoColumns: two, kind: activeKind))
                    .font(.system(size: 13.5)).foregroundStyle(p.txt)
            }
            .frame(maxWidth: HistoryLayout.maxContentWidth)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16).padding(.top, 8)
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
        .background(p.bg.ignoresSafeArea())
        .navigationTitle("Usage history")
        .navigationBarTitleDisplayMode(.large)
    }

    /// One window kind's column: an optional heading, Plan Fit on the 7-day side, then the week card.
    @ViewBuilder private func pane(_ account: Account, kind: String, heading: String?, state: AccountState?, two: Bool) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            if let heading {
                Text(heading).font(.system(size: 14, weight: .semibold)).foregroundStyle(account.accent(scheme)).padding(.leading, 4)
            }
            if kind == HistoryStats.weeklyKind, state?.lastKind != "plan" { planFit(account, two: two) }
            weekCard(account, kind: kind, index: index(kind))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Android's Plan Fit block (iOS subset: no plan or model tags on history records yet, so the plan is the live one and
    /// the cap is the generic "the cap"). Shown for a new account too, hidden for a plan-refused one (caller).
    @ViewBuilder private func planFit(_ account: Account, two: Bool) -> some View {
        let st = model.states[account.key]
        let live = st?.plan.map { PlanNames.display(account.provider, $0, tier: st?.tier) }
        let reading = PlanFit.evaluate(records: model.records[account.key] ?? [], livePlan: live, nowMs: model.nowMs)
        let lines = PlanFitCopy.build(reading, fallbackPlan: live, compact: !two)
        VStack(alignment: .leading, spacing: 0) {
            Text(lines.header).font(.system(size: 11, weight: .semibold)).foregroundStyle(p.sub)
            Text(lines.reading).font(.system(size: 15, weight: .semibold)).foregroundStyle(p.ink).padding(.top, 2)
            Text(lines.evidence).font(.system(size: 13)).foregroundStyle(p.sub).padding(.top, 2)
            if let five = lines.fiveHourLine { Text(five).font(.system(size: 13)).foregroundStyle(p.sub).padding(.top, 2) }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(p.fill, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private func weekCard(_ account: Account, kind: String, index weekIndex: Binding<Int>) -> some View {
        let weeks = HistoryStats.weeks(bars: bars(account, kind: kind), timeZone: .current)
        let index = min(weekIndex.wrappedValue, max(weeks.count - 1, 0))
        CardBox {
            if weeks.isEmpty {
                Text("No closed windows yet.").foregroundStyle(p.txt).frame(maxWidth: .infinity)
            } else {
                let week = weeks[index]
                HStack {
                    Button { weekIndex.wrappedValue = index + 1 } label: { Image(systemName: "chevron.left") }
                        .disabled(index + 1 >= weeks.count).accessibilityLabel("Earlier week")
                    VStack(spacing: 2) {
                        Text(index == 0 ? "This week" : index == 1 ? "Last week" : HistoryContent.weekTitle(mondayISO: week.monday))
                            .font(.system(size: 17, weight: .semibold))
                        Text(range(week.monday)).font(.system(size: 14)).foregroundStyle(p.txt)
                    }
                    .frame(maxWidth: .infinity)
                    Button { weekIndex.wrappedValue = index - 1 } label: { Image(systemName: "chevron.right") }
                        .disabled(index == 0).accessibilityLabel("Later week")
                }
                .foregroundStyle(p.ink)
                let maxed = week.bars.filter(\.hitLimit).count
                Text("\(week.bars.count) \(kind == HistoryStats.sessionKind ? "sessions" : "weeks")\(maxed > 0 ? " · \(maxed) maxed out" : "")")
                    .font(.system(size: 14)).foregroundStyle(p.txt).frame(maxWidth: .infinity).padding(.vertical, 10)
                ForEach(week.bars, id: \.resetMs) { bar in
                    HStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(CardContent.dayClock(bar.startMs, model.nowMs, Display.use24h, .current)).font(.system(size: 13.5)).foregroundStyle(p.txt)
                            if bar.current {
                                Text("now").font(.system(size: 12.5, weight: .semibold)).foregroundStyle(account.accent(scheme))
                            }
                        }
                        .frame(width: 104, alignment: .leading)
                        UsageBar(pct: bar.peakPct, pace: nil, accent: account.accent(scheme), height: 11)
                        Text("\(Int(bar.peakPct))%").font(.system(size: 13.5)).monospacedDigit()
                            .foregroundStyle(bar.peakPct >= 100 ? p.red : p.txt).frame(width: 44, alignment: .trailing)
                    }
                    .padding(.vertical, 3)
                }
            }
        }
    }

    private func bars(_ account: Account, kind: String) -> [HistoryStats.Bar] {
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
