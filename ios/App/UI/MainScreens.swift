import CooldownCore
import SwiftUI

/// CCRM-94 (iOS App): the app's entry and the main screens of wireframe rev B — iPhone tabs (§1), iPad pinned +
/// selected (§2), and the first-launch screen.
@main
struct CooldownApp: App {
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
        }
    }
}

enum Sheet: String, Identifiable {
    case settings, addAccount, claudeSignIn, chatGPTSignIn
    var id: String { rawValue }
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
                            PinnedAndTabs(width: geo.size.width, sheet: $sheet)
                        }
                    }
                } else {
                    PhoneMain(sheet: $sheet)
                }
            }
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
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
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
                        .frame(minWidth: accounts.count <= 3 ? nil : 110)
                        .overlay(alignment: .bottom) {
                            if on { Capsule().fill(account.provider.accent(scheme)).frame(height: 3) }
                        }
                    }
                    .buttonStyle(.plain)
                    .containerRelativeFrame(.horizontal, count: max(min(accounts.count, 3), 1), spacing: 0)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
        .scrollDisabled(accounts.count <= 3)
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
            AccountTabs(accounts: model.accounts, selected: $model.selectedKey)
            TabView(selection: $model.selectedKey) {
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

    private func signIn(_ account: Account) { sheet = account.provider == .claude ? .claudeSignIn : .chatGPTSignIn }
}

/// iPad landscape: the sidebar lists every account; the pinned one fills the top half, the selected one the bottom.
struct IPadLandscape: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    @Binding var sheet: Sheet?

    var body: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                AppTitle(size: 30).padding(.horizontal, 18).padding(.bottom, 10)
                ForEach(model.accounts) { account in sidebarRow(account) }
                Text("Touch and hold an account to pin it to the top.")
                    .font(.system(size: 12.5)).foregroundStyle(p.sub).padding(.horizontal, 22).padding(.top, 12)
                Spacer()
                Footer().padding(.horizontal, 22).padding(.bottom, 16)
            }
            .frame(width: 300)
            .background(scheme == .dark ? Color(hex: 0x161617) : Color(hex: 0xF7F7F9))
            .overlay(alignment: .trailing) { Rectangle().fill(p.line).frame(width: 1) }
            ScrollView {
                VStack(spacing: 12) {
                    if let pinned = model.account(model.pinnedKey) { half(pinned, pinned: true) }
                    if let selected = model.account(bottomKey), selected.key != model.pinnedKey {
                        Rectangle().fill(p.line).frame(height: 1).padding(.vertical, 2)
                        half(selected, pinned: false)
                    }
                }
                .padding(20)
            }
            .refreshable { await model.refresh(manual: true) }
        }
    }

    /// The bottom half shows the selected account, or the next one when the pinned account is selected.
    private var bottomKey: String? {
        if let s = model.selectedKey, s != model.pinnedKey { return s }
        return model.accounts.first { $0.key != model.pinnedKey }?.key
    }

    private func half(_ account: Account, pinned: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            AccountHeader(account: account, pinned: pinned)
            AccountCards(account: account, twoAcross: true, chartHeight: 112,
                         onSignIn: { sheet = account.provider == .claude ? .claudeSignIn : .chatGPTSignIn })
        }
    }

    private func sidebarRow(_ account: Account) -> some View {
        let usage = model.usage(account.key)
        let pct = usage?.session?.percent
        let pace = usage?.session?.resetsAtMs.flatMap {
            Fmt.elapsedPercent(resetsAtMs: $0, windowLengthMs: HistoryStats.sessionMs, nowMs: model.nowMs)
        }
        let accent = account.provider.accent(scheme)
        let selected = account.key == bottomKey
        return Button { model.selectedKey = account.key } label: {
            HStack(spacing: 10) {
                ProviderMark(provider: account.provider, size: 22)
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Text(account.label).font(.system(size: 16, weight: selected || account.key == model.pinnedKey ? .semibold : .regular))
                        PlanChip(plan: model.states[account.key]?.plan)
                        if account.key == model.pinnedKey { Image(systemName: "pin.fill").font(.system(size: 11)).foregroundStyle(p.sub) }
                    }
                    UsageBar(pct: pct, pace: pace, accent: accent, height: 5)
                }
                Text(pct.map { "\(Int($0))%" } ?? "—").font(.system(size: 16, weight: .bold)).foregroundStyle(p.severity(pct, accent: accent))
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(selected ? accent.opacity(0.18) : .clear, in: RoundedRectangle(cornerRadius: 10))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(p.ink)
        .padding(.horizontal, 10)
        .contextMenu { Button("Pin to the top", systemImage: "pin") { model.pin(account.key) } }
    }
}

/// iPad portrait and Split View wider than ~700 pt: the pinned account on top, tabs for the others, the selected one below.
struct PinnedAndTabs: View {
    @Environment(AppModel.self) private var model
    let width: CGFloat
    @Binding var sheet: Sheet?

    var body: some View {
        if width < 700 {
            PhoneMain(sheet: $sheet)
        } else {
            let others = model.accounts.filter { $0.key != model.pinnedKey }
            let selected = others.first { $0.key == model.selectedKey } ?? others.first
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    AppTitle()
                    if let pinned = model.account(model.pinnedKey) {
                        AccountHeader(account: pinned, pinned: true)
                        AccountCards(account: pinned, twoAcross: true, chartHeight: 200, onSignIn: { signIn(pinned) })
                    }
                    if !others.isEmpty {
                        AccountTabs(accounts: others, selected: Binding(get: { selected?.key }, set: { model.selectedKey = $0 }))
                            .padding(.horizontal, -22)
                        if let selected {
                            AccountHeader(account: selected, pinned: false)
                            AccountCards(account: selected, twoAcross: true, chartHeight: 200, onSignIn: { signIn(selected) })
                        }
                    }
                    Footer()
                }
                .padding(.horizontal, 22).padding(.vertical, 10)
            }
            .refreshable { await model.refresh(manual: true) }
        }
    }

    private func signIn(_ account: Account) { sheet = account.provider == .claude ? .claudeSignIn : .chatGPTSignIn }
}

/// An iPad half's header: mark, name, plan chip, "Pinned", and the account's credits on the right.
struct AccountHeader: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    let account: Account
    let pinned: Bool

    var body: some View {
        HStack(spacing: 9) {
            ProviderMark(provider: account.provider, size: 22)
            Text(account.label).font(.system(size: 21, weight: .bold))
            PlanChip(plan: model.states[account.key]?.plan, size: 12)
            if pinned {
                Label("Pinned", systemImage: "pin.fill").font(.system(size: 13)).foregroundStyle(p.sub).padding(.leading, 4)
            }
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
            HStack(spacing: 14) { ProviderMark(provider: .claude, size: 54); ProviderMark(provider: .chatgpt, size: 54) }
            Text("Track your usage windows").font(.system(size: 22, weight: .bold)).padding(.top, 22)
            Text("See how much of your 5-hour and weekly limits you’ve used, and when they reset — for Claude and ChatGPT.")
                .font(.system(size: 15.5)).foregroundStyle(p.txt).multilineTextAlignment(.center).padding(.top, 8)
                .padding(.horizontal, 32)
            VStack(spacing: 12) {
                addButton("Add a Claude account", .claude) { sheet = .claudeSignIn }
                addButton("Add a ChatGPT account", .chatgpt) { sheet = .chatGPTSignIn }
            }
            .padding(.top, 28)
            Spacer()
            Text("Not affiliated with Anthropic or OpenAI.").font(.system(size: 12.5)).foregroundStyle(p.sub).padding(.bottom, 24)
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
