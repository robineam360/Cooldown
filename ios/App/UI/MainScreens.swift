import CooldownCore
import SwiftUI
import UserNotifications

/// CCRM-94 (iOS App): the app's entry and the main screens of wireframe rev B — iPhone (§1), the iPad (§2; since
/// 2026-11-02 the Summary and one account at a time, no pinned account), and the first-launch screen. Since 2026-11-02
/// ("Native everywhere") both run on one system `TabView` (`MainTabs`).
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
    @Environment(\.palette) private var p
    @State private var sheet: Sheet?
    /// The tab whose navigation stack shows History (each tab has its own stack), nil when none does.
    @State private var historyKey: String?

    var body: some View {
        Group {
            if model.accounts.isEmpty {
                NavigationStack {
                    EmptyStateView(sheet: $sheet)
                        .background(p.bg.ignoresSafeArea())
                        .tint(p.ink)
                        .navigationDestination(isPresented: Binding(get: { historyKey != nil },
                                                                    set: { if !$0 { historyKey = nil } })) {
                            HistoryScreen()
                        }
                }
            } else {
                MainTabs(sheet: $sheet, historyKey: $historyKey)
            }
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
        // The accounts may not be loaded yet, so History opens on whichever tab ends up selected.
        case "history": historyKey = MainTabs.currentTab
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
    /// Robin, 2026-11-02: the title is the app's identity — Claude, ChatGPT and Gemini, always all three, at one
    /// optical size (the marks' own sizing, as on Android); where the three do not fit beside "Cooldown" the app's own
    /// "Shared pace" tile (CCRM-101 (Icon v3)) stands in. `ViewThatFits` measures, nothing is guessed.
    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) {
                HStack(spacing: size * 0.12) {
                    ProviderMark(provider: .claude, size: size * 0.76)
                    ProviderMark(provider: .chatgpt, size: size * 0.76)
                    ProviderMark(provider: .antigravity, size: size * 0.76)
                }
                Text("Cooldown").font(.system(size: size, weight: .bold)).fixedSize()
            }
            HStack(spacing: 8) {
                Image("AppMark").resizable().frame(width: size * 0.82, height: size * 0.82)
                Text("Cooldown").font(.system(size: size, weight: .bold)).lineLimit(1).minimumScaleFactor(0.8)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Cooldown")
        .accessibilityAddTraits(.isHeader)
    }
}

/// The account tabs: mark + name, underline in the selected account's accent. Since 2026-11-02 ("Native everywhere")
/// only History's account picker uses this strip; the main screen's tabs are the system `TabView` (`MainTabs`).
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

/// The main screen ("Native everywhere", Robin 2026-11-02): one system `TabView` in the sidebar-adaptable style, so
/// iOS draws its own Liquid Glass chrome — the tab bar at the bottom on iPhone (with the system's More past five
/// tabs), the floating tab bar at the top in iPad portrait, the sidebar in iPad landscape (which the user can collapse
/// into the tab bar). Tabs: "All" first (CCRM-98 (Summary Tab), two or more accounts), then the accounts in Settings ›
/// Account order. The selection is `AppModel.selectedKey`, so deep links, `-tab`, reset alerts and "Open on the
/// summary" pick the tab exactly as before. The chrome is the system's: never restyled, no hand-drawn glass.
struct MainTabs: View {
    @Environment(AppModel.self) private var model
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.palette) private var p
    @Binding var sheet: Sheet?
    /// A History request from outside the tabs (the `-screen history` launch argument): taken into `historyOpen`.
    @Binding var historyKey: String?
    /// The tabs whose own stack shows History. Per tab, so opening it on one tab never pops or dismisses another's.
    @State private var historyOpen: Set<String> = []
    @State private var window: CGSize = .zero

    /// `historyKey` for "open History on whichever tab is selected" (the `-screen history` launch argument).
    static let currentTab = "__current__"

    /// iPad landscape (today's rule): the sidebar shows, and carries the app title.
    private var sidebarLayout: Bool {
        sizeClass == .regular && window.width > window.height && window.width >= 1000
    }

    var body: some View {
        let showsAll = SummaryRank.tabShown(accountCount: model.accounts.count)
        let keys = model.accounts.map(\.key)
        let selection = Binding<String>(
            get: { Self.shownKey(selectedKey: model.selectedKey, accountKeys: keys, summaryShown: showsAll)
                ?? AppModel.summaryKey },
            set: { model.selectedKey = $0 })
        TabView(selection: selection) {
            if showsAll {
                Tab("All", systemImage: "text.alignleft", value: AppModel.summaryKey) {
                    stack(AppModel.summaryKey) {
                        TabPage(account: nil, showsTitle: !sidebarLayout, sheet: $sheet)
                    }
                }
            }
            ForEach(model.accounts) { account in
                // The provider mark is a 24 × 24 template SVG in the asset catalog, so the system tab bar and sidebar
                // draw it like a symbol, in their own tint.
                Tab(account.label, image: account.provider.markAsset, value: account.key) {
                    stack(account.key) {
                        TabPage(account: account, showsTitle: !sidebarLayout, sheet: $sheet)
                    }
                }
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .tabViewSidebarHeader { AppTitle(size: 30) }
        .onGeometryChange(for: CGSize.self) { $0.size } action: { window = $0 }
        .onChange(of: historyKey, initial: true) { _, request in
            guard let request else { return }
            historyOpen.insert(request == Self.currentTab ? selection.wrappedValue : request)
            historyKey = nil
        }
    }

    /// The tab's content: chosen, or by default (no account chosen yet) the Summary — nil — when it is shown at all;
    /// else the chosen account, or the first one when there is no Summary (one account).
    nonisolated static func shownKey(selectedKey: String?, accountKeys: [String], summaryShown: Bool) -> String? {
        if let selectedKey, accountKeys.contains(selectedKey) { return selectedKey }
        return summaryShown ? nil : accountKeys.first
    }

    /// Each tab keeps its own navigation stack (Apple's tab pattern), with the toolbar RootView had and History.
    private func stack<Content: View>(_ key: String, @ViewBuilder _ content: () -> Content) -> some View {
        NavigationStack {
            content()
                // A display choice changed in Settings: redraw with it.
                .id(model.displayStamp)
                .background(p.bg.ignoresSafeArea())
                .toolbar { toolbar(key) }
                .tint(p.ink)
                .navigationDestination(isPresented: Binding(
                    get: { historyOpen.contains(key) },
                    set: { if $0 { historyOpen.insert(key) } else { historyOpen.remove(key) } })) { HistoryScreen() }
        }
    }

    @ToolbarContentBuilder
    private func toolbar(_ key: String) -> some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button { historyOpen.insert(key) } label: { Image(systemName: "calendar") }
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

/// One tab's page: the Summary (`account` nil) or one account. On a regular width of 700 pt or more (iPad, and iPad
/// Split View that wide) an account is the full-width page — `AccountHeader` over the cards two across — and the
/// Summary goes wide from 900 pt; narrower (iPhone, narrow Split View) it is the phone's page. The app title tops the
/// page except where the sidebar carries it (iPad landscape), where the Summary is headed "All accounts" instead.
struct TabPage: View {
    @Environment(AppModel.self) private var model
    @Environment(\.horizontalSizeClass) private var sizeClass
    let account: Account?
    let showsTitle: Bool
    @Binding var sheet: Sheet?
    @State private var width: CGFloat = 0

    /// Before the first measurement the size class alone decides.
    private var iPad: Bool { sizeClass == .regular && (width == 0 || width >= 700) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if showsTitle { AppTitle().frame(maxWidth: .infinity, alignment: .leading) }
                if let account {
                    if iPad {
                        VStack(alignment: .leading, spacing: 10) {
                            AccountHeader(account: account)
                            AccountCards(account: account, twoAcross: true, cardWidth: max(396, width - 40),
                                         onSignIn: { signIn(account) })
                        }
                    } else {
                        AccountCards(account: account, onSignIn: { signIn(account) })
                    }
                } else {
                    if !showsTitle {
                        Text("All accounts").font(.system(size: 30, weight: .bold))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    SummaryView(wide: iPad && width >= 900)
                }
                Footer()
            }
            .padding(.horizontal, iPad ? 20 : 16).padding(.top, 4).padding(.bottom, 24)
        }
        .refreshable { await model.refresh(manual: true) }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
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
