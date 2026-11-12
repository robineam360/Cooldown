import CooldownCore
import SwiftUI
import UserNotifications

/// CCRM-94 (iOS App): the app's entry and the main screens of wireframe rev B — iPhone (§1), the iPad (§2; since
/// 2026-11-02 the Summary and one account at a time, no pinned account), and the first-launch screen. Since 2026-11-02
/// ("Native everywhere") both run on one system `TabView` (`MainTabs`).
@main
struct CooldownApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var realModel = AppModel()
    /// CCRM-94 parity rev B §7: the reviewer sample data swaps the model for the built-in demo; Exit swaps back.
    @State private var sampleModel: AppModel?
    @Environment(\.scenePhase) private var phase
    private var model: AppModel { sampleModel ?? realModel }

    /// A kill during the sample leaves its throwaway settings on disk; start clean.
    init() { SampleStore.wipe() }

    var body: some Scene {
        WindowGroup {
            RootView()
                .modifier(ThemeScheme())
                .id(model.isSample)
                .environment(model)
                .environment(\.sampleControl, SampleControl(enter: { SampleStore.begin(); let m = AppModel(demo: .standard); m.isSample = true; sampleModel = m }, exit: { SampleStore.end(); sampleModel = nil; Task { await realModel.refresh(manual: false) } }))
                .modifier(PaletteProvider())
                .task(id: phase) {
                    // The app fetches when it opens or returns (spec mobile §1); the repository's floor keeps it polite.
                    if phase == .active, !SampleStore.active { await realModel.refresh(manual: false) }
                }
                .task {
                    // Countdowns and "ago" lines move with the clock, not with fetches.
                    while !Task.isCancelled {
                        try? await Task.sleep(nanoseconds: 30_000_000_000)
                        realModel.nowMs = AppModel.clock()
                        sampleModel?.nowMs = AppModel.clock()
                    }
                }
                .onOpenURL { url in if let key = DeepLink.key(from: url) { model.selectedKey = key } }
                .onReceive(NotificationCenter.default.publisher(for: .openAccount)) { note in
                    if let key = note.object as? String { model.selectedKey = key }
                }
                .onChange(of: phase) { if phase == .background { BackgroundRefresh.schedule() } }
        }
        .backgroundTask(.appRefresh(BackgroundRefresh.id)) {
            // The real accounts; not while the sample data is showing (Exit refreshes).
            if !SampleStore.active { await realModel.refresh(manual: false) }
            BackgroundRefresh.schedule()
        }
    }
}

/// The theme choice as the window's colour scheme, read from the sample-aware defaults.
struct ThemeScheme: ViewModifier {
    @AppStorage("theme", store: Prefs.standard) private var theme = "system"
    func body(content: Content) -> some View {
        content.preferredColorScheme(theme == "light" ? .light : theme == "dark" ? .dark : nil)
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
    case settings, addAccount, claudeSignIn, chatGPTSignIn, geminiSignIn, layout, share, snapshot
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
                case .addAccount:
                    // Adding an account, not signing one in again: forget any earlier "Sign in again" target.
                    // The tag control starts on Personal each time the sheet opens (never remembered).
                    AddAccountSheet(sheet: $sheet).onAppear { model.reauthTarget = nil; model.pendingTag = .personal }
                case .claudeSignIn: ClaudeSignInSheet()
                case .chatGPTSignIn: ChatGPTSignInSheet()
                case .geminiSignIn: GeminiSignInSheet()
                case .layout: LayoutSheet(accountKey: model.currentAccountKey ?? "")
                case .share: ShareSheet(accountKey: model.currentAccountKey ?? "")
                case .snapshot: SummarySnapshotSheet()
                }
            }
            .environment(model)
            .modifier(PaletteProvider())
        }
        .onAppear(perform: openRequestedScreen)
        // A chosen tag is only for the sign-in in progress: it is dropped when the sheets close.
        .onChange(of: sheet?.id) { _, new in if new == nil { model.pendingTag = .personal } }
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
        case "snapshot": sheet = .snapshot
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
                // 1.2 × the mark size, as Android's 24 dp tile beside 20 dp marks.
                Image("AppMark").resizable().frame(width: size * 0.91, height: size * 0.91)
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
///
/// CCBG-88 (Tab Strip Truncates), the shared rule (`spec/mobile/surfaces/app.md` §2.3 Fit, Robin's option B for
/// CCBG-81 (History Tab Wrap)): the strip is fixed, in equal shares, while every tab's one-line label (mark + name +
/// padding) fits its share, and scrolls, each tab sized to its label, once one would not — as Android's
/// `ui/ProfileTabStrip.kt`. `ViewThatFits` measures; nothing follows the account count.
struct AccountTabs: View {
    let accounts: [Account]
    @Binding var selected: String?
    /// CCRM-98 (Summary Tab): an "All" tab first (with two or more accounts).
    var showsAll = false
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    /// Material's scrollable tab minimum (90 dp), so a short name keeps a tappable tab once the strip scrolls.
    static let scrollingMinWidth: CGFloat = 90

    var body: some View {
        ViewThatFits(in: .horizontal) {
            EqualShares { tabs(scrolling: false) }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) { tabs(scrolling: true) }
            }
        }
        .overlay(alignment: .bottom) { Rectangle().fill(p.line).frame(height: 1) }
    }

    @ViewBuilder private func tabs(scrolling: Bool) -> some View {
        if showsAll {
            let on = selected == AppModel.summaryKey
            tab(on: on, underline: p.ink, scrolling: scrolling, action: { selected = AppModel.summaryKey }) {
                Image(systemName: "text.alignleft").font(.system(size: 15, weight: .semibold))
                Text("All").font(.system(size: 16, weight: .medium)).fixedSize()
            }
            .accessibilityLabel("All accounts")
        }
        ForEach(accounts) { account in
            tab(on: account.key == selected, underline: account.accent(scheme), scrolling: scrolling,
                action: { selected = account.key }) {
                ProviderMark(provider: account.provider, size: 20)
                Text(account.label).font(.system(size: 16, weight: .medium)).lineLimit(1).fixedSize()
            }
        }
    }

    private func tab<Label: View>(on: Bool, underline: Color, scrolling: Bool, action: @escaping () -> Void,
                                  @ViewBuilder label: () -> Label) -> some View {
        Button { withAnimation { action() } } label: {
            HStack(spacing: 7) { label() }
                .foregroundStyle(on ? p.ink : p.sub)
                .padding(.horizontal, 12)
                .frame(height: 46)
                .frame(minWidth: scrolling ? Self.scrollingMinWidth : nil)
                .contentShape(Rectangle())
                .overlay(alignment: .bottom) { if on { Capsule().fill(underline).frame(height: 3) } }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// Equal shares of the proposed width. Its ideal width is the widest child's times the count, so inside
/// `ViewThatFits` it is chosen only while every child fits its share (CCBG-88 (Tab Strip Truncates)).
struct EqualShares: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let ideal = subviews.map { $0.sizeThatFits(.unspecified) }
        let height = ideal.map(\.height).max() ?? 0
        let widest = ideal.map(\.width).max() ?? 0
        guard let width = proposal.width, width.isFinite else { return CGSize(width: widest * CGFloat(subviews.count), height: height) }
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard !subviews.isEmpty else { return }
        let share = bounds.width / CGFloat(subviews.count)
        for (i, view) in subviews.enumerated() {
            let frame = ProposedViewSize(width: share, height: bounds.height)
            view.place(at: CGPoint(x: bounds.minX + share * CGFloat(i), y: bounds.minY), anchor: .topLeading, proposal: frame)
        }
    }

    /// The rule itself, pure, for the test: fixed while every label fits its equal share of `width`.
    static func fits(labelWidths: [CGFloat], width: CGFloat) -> Bool {
        guard !labelWidths.isEmpty else { return true }
        return (labelWidths.max() ?? 0) * CGFloat(labelWidths.count) <= width
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
                // CCRM-112 (Summary Snapshot): on the All tab the share item is the whole Summary's snapshot.
                if key == AppModel.summaryKey {
                    Button("Share snapshot", systemImage: "square.and.arrow.up") { sheet = .snapshot }
                } else {
                    Button("Share card", systemImage: "square.and.arrow.up") { sheet = .share }
                }
                if !model.isSample { Button("Add account", systemImage: "plus") { sheet = .addAccount } }
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
                if model.isSample { SampleBanner() }
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

    private func signIn(_ account: Account) { sheet = model.signInAgain(account) }
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
            PlanChip(provider: account.provider, plan: model.states[account.key]?.plan, tier: model.states[account.key]?.tier, size: 12)
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

/// The plan pill: API-CONTRACT §11's name for the provider's stored plan ("Pro 100", "Ultra", "Ultra 5x"), so a label an
/// older build stored ("Prolite", a 20x "Max") reads right before the next plan read; never a multiplier.
struct PlanChip: View {
    let provider: Provider
    let plan: String?
    var tier: String?
    var size: CGFloat = 11
    @Environment(\.palette) private var p
    var body: some View {
        if let plan {
            Text(PlanNames.display(provider, plan, tier: tier)).font(.system(size: size, weight: .semibold))
                .padding(.horizontal, 7).padding(.vertical, 2)
                .background(p.fill, in: RoundedRectangle(cornerRadius: 6)).foregroundStyle(p.sub)
        }
    }
}

/// First launch: no accounts yet.
struct EmptyStateView: View {
    @Binding var sheet: Sheet?
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    @Environment(\.sampleControl) private var control

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
                // CCRM-110: the first account never passes the Add sheet, so it is Personal without asking (addendum T2 A).
                addButton("Add a Claude account", .claude) { model.pendingTag = .personal; sheet = .claudeSignIn }
                addButton("Add a ChatGPT account", .chatgpt) { model.pendingTag = .personal; sheet = .chatGPTSignIn }
                if Antigravity.available { addButton("Add a Gemini account", .antigravity) { model.pendingTag = .personal; sheet = .geminiSignIn } }
            }
            .padding(.top, 28)
            // Reviewers (and the curious) can look around without an account.
            Button(SampleRules.entryTitle) { control.enter() }
                .font(.system(size: 15)).foregroundStyle(p.ink).padding(.top, 20)
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
