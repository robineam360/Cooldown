import CooldownCore
import SwiftUI
import UserNotifications

/// CCRM-94 (iOS App): the app's entry and the main screens of wireframe rev B — iPhone tabs (§1), the iPad (§2; since
/// 2026-11-02 the Summary and one account at a time, no pinned account), and the first-launch screen.
@main
struct CooldownApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var phase
    @AppStorage("theme") private var theme = "system"

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .modifier(PaletteProvider())
                .preferredColorScheme(theme == "light" ? .light : theme == "dark" ? .dark : nil)
                .task(id: phase) {
                    // The app fetches when it opens or returns (spec mobile §1); the repository's floor keeps it polite.
                    if phase == .active { await model.refresh(manual: false) }
                }
                .task {
                    // Countdowns and "ago" lines move with the clock, not with fetches.
                    while !Task.isCancelled {
                        try? await Task.sleep(nanoseconds: 30_000_000_000)
                        model.nowMs = AppModel.clock()
                    }
                }
                .onOpenURL { url in if let key = DeepLink.key(from: url) { model.selectedKey = key } }
                .onReceive(NotificationCenter.default.publisher(for: .openAccount)) { note in
                    if let key = note.object as? String { model.selectedKey = key }
                }
                .onChange(of: phase) { if phase == .background { BackgroundRefresh.schedule() } }
        }
        .backgroundTask(.appRefresh(BackgroundRefresh.id)) {
            await model.refresh(manual: false)
            BackgroundRefresh.schedule()
        }
    }
}

/// Shows reset alerts while the app is open, and opens the app on the alert's account when one is tapped.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                            withCompletionHandler done: @escaping (UNNotificationPresentationOptions) -> Void) {
        done([.banner, .sound])
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                            withCompletionHandler done: @escaping () -> Void) {
        let key = response.notification.request.content.userInfo["key"] as? String
        Task { @MainActor in NotificationCenter.default.post(name: .openAccount, object: key) }
        done()
    }
}

extension Notification.Name { static let openAccount = Notification.Name("cooldown.openAccount") }

enum Sheet: String, Identifiable {
    case settings, addAccount, claudeSignIn, chatGPTSignIn, geminiSignIn, layout, share
    var id: String { rawValue }

    /// The sign-in sheet for an account's provider.
    static func signIn(for provider: Provider) -> Sheet {
        switch provider {
        case .claude: .claudeSignIn
        case .chatgpt: .chatGPTSignIn
        case .antigravity: .geminiSignIn
        }
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.palette) private var p
    @State private var sheet: Sheet?
    @State private var showHistory = false

    var body: some View {
        NavigationStack {
            Group {
                if model.accounts.isEmpty {
                    EmptyStateView(sheet: $sheet)
                } else if sizeClass == .regular {
                    GeometryReader { geo in
                        if geo.size.width > geo.size.height && geo.size.width >= 1000 {
                            IPadLandscape(sheet: $sheet)
                        } else {
                            IPadTabs(width: geo.size.width, sheet: $sheet)
                        }
                    }
                } else {
                    PhoneMain(sheet: $sheet)
                }
            }
            // A display choice changed in Settings: redraw with it.
            .id(model.displayStamp)
            .background(p.bg.ignoresSafeArea())
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if !model.accounts.isEmpty {
                        Button { showHistory = true } label: { Image(systemName: "calendar") }
                            .accessibilityLabel("Usage history")
                        Button { sheet = .settings } label: { Image(systemName: "gearshape") }
                            .accessibilityLabel("Settings")
                        Menu {
                            Button("Refresh", systemImage: "arrow.clockwise") { Task { await model.refresh(manual: true) } }
                            Button("Layout", systemImage: "rectangle.3.group") { sheet = .layout }
                            Button("Share card", systemImage: "square.and.arrow.up") { sheet = .share }
                            Button("Add account", systemImage: "plus") { sheet = .addAccount }
                        } label: { Image(systemName: "ellipsis.circle") }
                    }
                }
            }
            .tint(p.ink)
            .navigationDestination(isPresented: $showHistory) { HistoryScreen() }
        }
        .sheet(item: $sheet) { which in
            Group {
                switch which {
                case .settings: SettingsScreen(sheet: $sheet)
                case .addAccount: AddAccountSheet(sheet: $sheet)
                case .claudeSignIn: ClaudeSignInSheet()
                case .chatGPTSignIn: ChatGPTSignInSheet()
                case .geminiSignIn: GeminiSignInSheet()
                case .layout: LayoutSheet(accountKey: model.currentAccountKey ?? "")
                case .share: ShareSheet(accountKey: model.currentAccountKey ?? "")
                }
            }
            .environment(model)
            .modifier(PaletteProvider())
        }
        .onAppear(perform: openRequestedScreen)
    }

    /// Demo screenshots can open a screen directly: `-screen settings|history|addAccount|claude|chatgpt`.
    private func openRequestedScreen() {
        let args = ProcessInfo.processInfo.arguments
        if let j = args.firstIndex(of: "-tab"), j + 1 < args.count { model.selectedKey = args[j + 1] }
        guard let i = args.firstIndex(of: "-screen"), i + 1 < args.count else { return }
        switch args[i + 1] {
        case "settings": sheet = .settings
        case "history": showHistory = true
        case "addAccount": sheet = .addAccount
        case "claude": sheet = .claudeSignIn
        case "chatgpt": sheet = .chatGPTSignIn
        case "gemini": sheet = .geminiSignIn
        case "layout": sheet = .layout
        case "share": sheet = .share
        default: break
        }
    }
}

/// Large title with both marks, as on Android.
struct AppTitle: View {
    var size: CGFloat = 34
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        HStack(spacing: 8) {
            ProviderMark(provider: .claude, size: size * 0.76)
            ProviderMark(provider: .chatgpt, size: size * 0.76)
            Text("Cooldown").font(.system(size: size, weight: .bold))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Cooldown")
        .accessibilityAddTraits(.isHeader)
    }
}

/// The account tabs: mark + name, underline in the selected account's accent.
struct AccountTabs: View {
    let accounts: [Account]
    @Binding var selected: String?
    /// CCRM-98 (Summary Tab): an "All" tab first (with two or more accounts).
    var showsAll = false
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    private var count: Int { accounts.count + (showsAll ? 1 : 0) }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                if showsAll {
                    let on = selected == AppModel.summaryKey
                    Button { withAnimation { selected = AppModel.summaryKey } } label: {
                        HStack(spacing: 7) {
                            Image(systemName: "text.alignleft").font(.system(size: 15, weight: .semibold))
                            Text("All").font(.system(size: 16, weight: .medium))
                        }
                        .foregroundStyle(on ? p.ink : p.sub)
                        .padding(.horizontal, 12)
                        .frame(height: 46)
                        .frame(minWidth: count <= 3 ? nil : 80)
                        .overlay(alignment: .bottom) { if on { Capsule().fill(p.ink).frame(height: 3) } }
                    }
                    .buttonStyle(.plain)
                    .containerRelativeFrame(.horizontal, count: max(min(count, 3), 1), spacing: 0)
                    .accessibilityLabel("All accounts")
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
                ForEach(accounts) { account in
                    let on = account.key == selected
                    Button { withAnimation { selected = account.key } } label: {
                        HStack(spacing: 7) {
                            ProviderMark(provider: account.provider, size: 20)
                            Text(account.label).font(.system(size: 16, weight: .medium)).lineLimit(1)
                        }
                        .foregroundStyle(on ? p.ink : p.sub)
                        .padding(.horizontal, 12)
                        .frame(height: 46)
                        .frame(minWidth: count <= 3 ? nil : 110)
                        .overlay(alignment: .bottom) {
                            if on { Capsule().fill(account.accent(scheme)).frame(height: 3) }
                        }
                    }
                    .buttonStyle(.plain)
                    .containerRelativeFrame(.horizontal, count: max(min(count, 3), 1), spacing: 0)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
        .scrollDisabled(count <= 3)
        .overlay(alignment: .bottom) { Rectangle().fill(p.line).frame(height: 1) }
    }
}

struct Footer: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    var body: some View {
        Button { Task { await model.refresh(manual: true) } } label: {
            HStack(spacing: 8) {
                if model.isRefreshing { Text("Checking…") } else if let at = model.lastCheckedMs {
                    Text("Checked \(Fmt.relativeAgo(epochMs: at, nowMs: model.nowMs))")
                } else { Text("Not checked yet") }
                Image(systemName: "arrow.clockwise").font(.system(size: 14, weight: .semibold))
            }
            .font(.system(size: 15)).foregroundStyle(p.sub)
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 2)
    }
}

/// iPhone (and iPad Split View narrower than ~700 pt): one account per screen, tabs on top, swipe between.
struct PhoneMain: View {
    @Environment(AppModel.self) private var model
    @Binding var sheet: Sheet?

    var body: some View {
        @Bindable var model = model
        VStack(spacing: 0) {
            AppTitle().frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.bottom, 4)
            let showsAll = SummaryRank.tabShown(accountCount: model.accounts.count)
            AccountTabs(accounts: model.accounts, selected: $model.selectedKey, showsAll: showsAll)
            TabView(selection: $model.selectedKey) {
                if showsAll {
                    ScrollView {
                        VStack(spacing: 12) {
                            SummaryView()
                            Footer()
                        }
                        .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 24)
                    }
                    .refreshable { await model.refresh(manual: true) }
                    .tag(Optional(AppModel.summaryKey))
                }
                ForEach(model.accounts) { account in
                    ScrollView {
                        VStack(spacing: 12) {
                            AccountCards(account: account, onSignIn: { signIn(account) })
                            Footer()
                        }
                        .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 24)
                    }
                    .refreshable { await model.refresh(manual: true) }
                    .tag(Optional(account.key))
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
        }
    }

    private func signIn(_ account: Account) { sheet = Sheet.signIn(for: account.provider) }
}

/// iPad landscape (Robin, 2026-11-02: "drop the 'pinned' account … make the summary/all as the default one … only on tap
/// of the individual account, we'll show the details as we have the whole space"): the sidebar is "All accounts" (the
/// Summary, where the app opens per "Open on the summary") and one row per account; the detail shows the Summary or
/// only the tapped account, full width. No pinned half, no pin hint or menu; the stored pinned flag is left unread.
struct IPadLandscape: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    @Binding var sheet: Sheet?
    /// CCRM-103 (Hybrid Notification) rev D I2: the row's figure is always the Weekly and its ring always the 5h, so the
    /// Headline · Weekly · 5h switch (CCRM-96 (Weekly First) rev B) has nothing left to pick and is gone; its stored
    /// "sidebarWindow" value is left unread.

    var body: some View {
        let shown = Self.shownKey(selectedKey: model.selectedKey, accountKeys: model.accounts.map(\.key),
                                  summaryShown: SummaryRank.tabShown(accountCount: model.accounts.count))
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                AppTitle(size: 30).padding(.horizontal, 18).padding(.bottom, 10)
                if SummaryRank.tabShown(accountCount: model.accounts.count) { allRow(on: shown == nil) }
                ForEach(model.accounts) { account in sidebarRow(account, selected: account.key == shown) }
                Spacer()
                Footer().padding(.horizontal, 22).padding(.bottom, 16)
            }
            .frame(width: 300)
            .background(scheme == .dark ? Color(hex: 0x161617) : Color(hex: 0xF7F7F9))
            .overlay(alignment: .trailing) { Rectangle().fill(p.line).frame(width: 1) }
            GeometryReader { geo in
                ScrollView {
                    VStack(spacing: 12) {
                        if let key = shown, let account = model.account(key) {
                            page(account, width: geo.size.width - 40)
                        } else {
                            Text("All accounts").font(.system(size: 30, weight: .bold)).frame(maxWidth: .infinity, alignment: .leading)
                            SummaryView(wide: true)
                        }
                    }
                    .padding(20)
                }
                .refreshable { await model.refresh(manual: true) }
                // A new page starts at its top.
                .id(shown ?? AppModel.summaryKey)
            }
        }
    }

    /// The detail: nil = the Summary — chosen, or by default (no account chosen yet) when it is shown at all; else the
    /// chosen account, or the first one when there is no Summary (one account).
    nonisolated static func shownKey(selectedKey: String?, accountKeys: [String], summaryShown: Bool) -> String? {
        if let selectedKey, accountKeys.contains(selectedKey) { return selectedKey }
        return summaryShown ? nil : accountKeys.first
    }

    /// One account's page, the detail's full width.
    private func page(_ account: Account, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            AccountHeader(account: account)
            AccountCards(account: account, twoAcross: true, cardWidth: max(396, width),
                         onSignIn: { sheet = Sheet.signIn(for: account.provider) })
        }
    }

    /// CCRM-98 (Summary Tab) W2: "All accounts" tops the sidebar, with the account count instead of a figure.
    private func allRow(on: Bool) -> some View {
        Button { model.selectedKey = AppModel.summaryKey } label: {
            HStack(spacing: 10) {
                Image(systemName: "text.alignleft").font(.system(size: 17, weight: .semibold)).frame(width: 22)
                Text("All accounts").font(.system(size: 16, weight: on ? .semibold : .regular))
                Spacer()
                Text("\(model.accounts.count)").font(.system(size: 15)).foregroundStyle(p.sub)
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(on ? p.fill : .clear, in: RoundedRectangle(cornerRadius: 10))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain).foregroundStyle(p.ink).padding(.horizontal, 10)
    }

    /// CCRM-103 rev D I2 (`sideRow`): the 5h ring (40 pt; greyed while the week is spent, a cross for a plan with no 5h,
    /// so the names line up), the mark and name over a 4 pt Weekly bar, the Weekly figure.
    private func sidebarRow(_ account: Account, selected: Bool) -> some View {
        let usage = model.usage(account.key)
        let session = usage?.session
        let pct = usage?.weekly?.percent
        let pace = usage?.weekly?.resetsAtMs.flatMap {
            Fmt.elapsedPercent(resetsAtMs: $0, windowLengthMs: HistoryStats.weekMs, nowMs: model.nowMs)
        }
        // As the faces read it (`Face.build`): a 5h with no reset clock yet reads 0%.
        let fivePct = session.map { $0.resetsAtMs == nil ? 0 : ($0.percent ?? 0) }
        let fivePace = session?.resetsAtMs.flatMap {
            Fmt.elapsedPercent(resetsAtMs: $0, windowLengthMs: HistoryStats.sessionMs, nowMs: model.nowMs)
        }
        let ringState: GrammarRing.State = session == nil ? .notApplicable : Pace.isSpent(pct) ? .grey : .normal
        let accent = account.accent(scheme)
        let surface = scheme == .dark ? Color(hex: 0x161617) : Color(hex: 0xF7F7F9)
        return Button { model.selectedKey = account.key } label: {
            HStack(spacing: 10) {
                GrammarRing(pct: fivePct, pace: fivePace, accent: accent,
                            figure: session == nil ? nil : Display.number(fivePct, pace: fivePace),
                            figureColour: ringState == .normal && Display.isDistance(fivePct, pace: fivePace)
                                ? Display.figureColour(fivePct, pace: fivePace, ink: p.ink, usage: p.ink, scheme) : nil,
                            state: ringState, surface: surface)
                    .frame(width: 40, height: 40)
                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 6) {
                        ProviderMark(provider: account.provider, size: 16)
                        Text(account.label).font(.system(size: 16, weight: selected ? .semibold : .regular))
                            .lineLimit(1).layoutPriority(1)
                        // CCBG-68 (iPad Plan Chip Wrap): the chip stays on one line, and gives way to the name — shown
                        // whole when it fits beside it, else left out (never "Team / Premiu / m", never "C…").
                        ViewThatFits(in: .horizontal) {
                            PlanChip(plan: model.states[account.key]?.plan).lineLimit(1).fixedSize()
                            Color.clear.frame(width: 0, height: 0)
                        }
                    }
                    GrammarBar(pct: pct, pace: pace, accent: accent, height: 4, showsRoom: false, surface: surface)
                }
                Display.grammarFigure(Display.number(pct, pace: pace), size: 16)
                    .foregroundStyle(Display.figureColour(pct, pace: pace, ink: p.ink,
                                                          usage: pct == nil ? p.sub : p.severity(pct, accent: accent), scheme))
                    .lineLimit(1).minimumScaleFactor(0.8)
                    .frame(minWidth: 40, alignment: .trailing)
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(selected ? accent.opacity(0.18) : .clear, in: RoundedRectangle(cornerRadius: 10))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(p.ink)
        .padding(.horizontal, 10)
    }
}

/// iPad portrait and Split View wider than ~700 pt: the phone's model (Robin, 2026-11-02) — tabs for All and every
/// account, one page at a time, the Summary by default; no pinned section.
struct IPadTabs: View {
    @Environment(AppModel.self) private var model
    let width: CGFloat
    @Binding var sheet: Sheet?

    var body: some View {
        if width < 700 {
            PhoneMain(sheet: $sheet)
        } else {
            let showsAll = SummaryRank.tabShown(accountCount: model.accounts.count)
            let shown = IPadLandscape.shownKey(selectedKey: model.selectedKey, accountKeys: model.accounts.map(\.key),
                                               summaryShown: showsAll)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    AppTitle()
                    AccountTabs(accounts: model.accounts,
                                selected: Binding(get: { shown ?? AppModel.summaryKey }, set: { model.selectedKey = $0 }),
                                showsAll: showsAll)
                        .padding(.horizontal, -22)
                    if let key = shown, let account = model.account(key) {
                        AccountHeader(account: account)
                        AccountCards(account: account, twoAcross: true, cardWidth: 356, onSignIn: { signIn(account) })
                    } else {
                        SummaryView(wide: width >= 900)
                    }
                    Footer()
                }
                .padding(.horizontal, 22).padding(.vertical, 10)
            }
            .refreshable { await model.refresh(manual: true) }
        }
    }

    private func signIn(_ account: Account) { sheet = Sheet.signIn(for: account.provider) }
}

/// An iPad account page's header: mark, name, plan chip, and the account's credits on the right.
struct AccountHeader: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    let account: Account

    var body: some View {
        HStack(spacing: 9) {
            ProviderMark(provider: account.provider, size: 22)
            Text(account.label).font(.system(size: 21, weight: .bold))
            PlanChip(plan: model.states[account.key]?.plan, size: 12)
            Spacer()
            if let credits = CardContent.credits(model.usage(account.key)?.credits) {
                Group {
                    if let pct = credits.pct {
                        Text("Usage credits \(credits.text) · ") + Text("\(pct)%").bold()
                    } else {
                        Text("Credits \(credits.text)")
                    }
                }
                .font(.system(size: 14)).foregroundStyle(p.txt)
            }
        }
        .padding(.horizontal, 2)
    }
}

struct PlanChip: View {
    let plan: String?
    var size: CGFloat = 11
    @Environment(\.palette) private var p
    var body: some View {
        if let plan {
            Text(plan).font(.system(size: size, weight: .semibold))
                .padding(.horizontal, 7).padding(.vertical, 2)
                .background(p.fill, in: RoundedRectangle(cornerRadius: 6)).foregroundStyle(p.sub)
        }
    }
}

/// First launch: no accounts yet.
struct EmptyStateView: View {
    @Binding var sheet: Sheet?
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        VStack(spacing: 0) {
            Text("Cooldown").font(.system(size: 34, weight: .bold)).frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
            Spacer()
            HStack(spacing: 14) {
                ProviderMark(provider: .claude, size: 54); ProviderMark(provider: .chatgpt, size: 54)
                if Antigravity.available { ProviderMark(provider: .antigravity, size: 54) }
            }
            Text("Track your usage windows").font(.system(size: 22, weight: .bold)).padding(.top, 22)
            Text(Antigravity.available
                 ? "See how much of your 5-hour and weekly limits you’ve used, and when they reset — for Claude, ChatGPT and Gemini."
                 : "See how much of your 5-hour and weekly limits you’ve used, and when they reset — for Claude and ChatGPT.")
                .font(.system(size: 15.5)).foregroundStyle(p.txt).multilineTextAlignment(.center).padding(.top, 8)
                .padding(.horizontal, 32)
            VStack(spacing: 12) {
                addButton("Add a Claude account", .claude) { sheet = .claudeSignIn }
                addButton("Add a ChatGPT account", .chatgpt) { sheet = .chatGPTSignIn }
                if Antigravity.available { addButton("Add a Gemini account", .antigravity) { sheet = .geminiSignIn } }
            }
            .padding(.top, 28)
            Spacer()
            Text("Not affiliated with Anthropic, OpenAI or Google. “Gemini” and “Antigravity” are trademarks of Google LLC.")
                .font(.system(size: 12.5)).foregroundStyle(p.sub).multilineTextAlignment(.center).padding(.horizontal, 24).padding(.bottom, 24)
        }
    }

    private func addButton(_ title: String, _ provider: Provider, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.system(size: 16, weight: .semibold)).foregroundStyle(.black)
                .frame(width: 260).padding(.vertical, 12)
                .background(provider.accent(scheme), in: Capsule())
        }
    }
}
