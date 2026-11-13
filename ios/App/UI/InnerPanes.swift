import CooldownCore
import OSLog
import SwiftUI
import UIKit

// CCRM-116 (Inner Panes), wireframe design/2026-11-12-iphone-duo-inner-screen.html (approved by Robin 2026-11-12): on a
// regular-width window every page is two panes. The rule itself is `PaneGeometry` (CooldownCore, unit-tested); this
// file draws it. The same view tree in every pose: a fold or a turn only moves the panes' frames, so nothing inside them
// is recreated or reset (§4).

enum PaneLog {
    static let log = Logger(subsystem: "com.robin.cooldown", category: "panes")
}

/// Two panes in the area this view is given. Wider than tall they sit side by side, taller than wide they stack; flat
/// the split is even (16 pt gap), half open the system's division region is the gap (iOS 27.1 `reservedRegions`).
/// Each pane gets exactly its frame; what scrolls is up to the pane.
struct PaneSplit<First: View, Second: View>: View {
    /// nil second pane: the first pane alone, never across the fold (`PaneGeometry.single`).
    var hasSecond = true
    @ViewBuilder let first: First
    @ViewBuilder let second: Second

    var body: some View {
        GeometryReader { proxy in
            let fold = Self.fold(proxy)
            let split = PaneGeometry.split(size: proxy.size, fold: fold)
            let one = hasSecond ? split.first : PaneGeometry.single(size: proxy.size, fold: fold)
            ZStack(alignment: .topLeading) {
                first
                    .frame(width: one.width, height: one.height, alignment: .top)
                    .offset(x: one.minX, y: one.minY)
                second
                    .frame(width: split.second.width, height: split.second.height, alignment: .top)
                    .offset(x: split.second.minX, y: split.second.minY)
                    .opacity(hasSecond ? 1 : 0)
                    .allowsHitTesting(hasSecond)
                    .accessibilityHidden(!hasSecond)
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
            .onChange(of: fold, initial: true) { _, region in
                // The wireframe assumed a 32 pt band; the build measures it (open question in §2).
                if let region { PaneLog.log.notice("division region \(region.debugDescription, privacy: .public) in \(proxy.size.debugDescription, privacy: .public)") }
            }
        }
    }

    /// The fold, when the system reports an active division region crossing this area.
    static func fold(_ proxy: GeometryProxy) -> CGRect? {
        if #available(iOS 27.1, *) {
            return proxy.reservedRegions(kind: .division).first(where: \.isActive)?.frame
        }
        return nil
    }
}

/// Whether this window draws two panes: regular width (§2 — no pt thresholds). The cover screen, an iPhone and a narrow
/// Split View are compact and keep the phone page.
struct PanesActiveKey: EnvironmentKey { static let defaultValue = false }
extension EnvironmentValues {
    var panesActive: Bool {
        get { self[PanesActiveKey.self] }
        set { self[PanesActiveKey.self] = newValue }
    }
}

/// A pane whose one flexible element (a chart, the Pace map) takes the height left once the rest is laid out: `content`
/// is called with that height, clamped to `minimum` and to `cap(width)`. The rest's height is measured, never guessed;
/// spare room stays empty below (§3a "Plot height follows the pane"). Scrolls only if even the minimum does not fit.
struct PaneFill<Content: View>: View {
    var minimum: CGFloat = 160
    var cap: (CGFloat) -> CGFloat = { _ in .infinity }
    @ViewBuilder let content: (CGFloat) -> Content
    /// Everything but the flexible element; seeded with a typical card's chrome and corrected after the first pass.
    @State private var overhead: CGFloat = 120

    var body: some View {
        GeometryReader { proxy in
            let h = max(minimum, min(proxy.size.height - overhead, cap(proxy.size.width)))
            ScrollView {
                content(h)
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { total in
                        let next = max(0, total - h)
                        if abs(next - overhead) > 0.5 { overhead = next }
                    }
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }
}

// MARK: - Scroll position across a fold (§4)

let trackedScrollSpace = "trackedScroll"

enum TrackedRow {
    static func scrollID(_ key: String) -> String { "row.\(key)" }
}

/// Each Summary row reports where it sits in its scroll view, so the page can remember the first visible row's id.
struct RowFrameKey: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue()) { $1 }
    }
}

extension View {
    /// Marks a row whose position survives a fold (its id is also its `.id` for `ScrollViewReader`).
    /// The scroll id is namespaced (`row.<key>`): a bare account key would also match other views keyed by the account
    /// in the same scroll view (the Pace map's dots), and `scrollTo` would land on the map.
    func trackedRow(_ id: String) -> some View {
        self.id(TrackedRow.scrollID(id)).background {
            GeometryReader { g in
                Color.clear.preference(key: RowFrameKey.self, value: [id: g.frame(in: .named(trackedScrollSpace))])
            }
        }
    }
}

/// A vertical scroll view that stores, per tab, the id of its first visible tracked row (not a pt offset) in the model,
/// and scrolls back to it when it appears — so the phone page and the Usage pane hand the position to each other on a
/// fold. Scrolled past every row (to the map), it stores nothing, and the other side starts at its top.
///
/// Only a scroll the user makes is stored: a pane too short to bring the row to its top (the tall inner screen's Usage
/// pane scrolls by ~57 pt) must not overwrite the row the other side will look for. A fold animates the window, and the
/// system may re-apply an old offset after the first scroll, so for up to `restoreWindow` seconds the view scrolls the
/// row back to the top whenever it sees it elsewhere.
struct TrackedScroll<Content: View>: View {
    let tab: String
    @ViewBuilder let content: Content
    @Environment(AppModel.self) private var model
    @State private var restoreUntil = Date.distantPast
    @State private var userScrolled = false
    @State private var viewport: CGFloat = 0
    /// Where the row sits once scrolled to the top (the scroll view's top inset), and when it may be nudged again.
    @State private var settledTop: CGFloat?
    @State private var nextNudge = Date.distantPast
    /// Restored once per view: a fold builds a new page (the phone page ↔ the panes); a tab switch or a pop from
    /// History keeps this one, which keeps its own offset.
    @State private var restored = false
    /// The content sits at its top: nothing is stored then, so coming back never hides the title.
    @State private var atTop = true

    static var restoreWindow: TimeInterval { 2 }

    var body: some View {
        ScrollViewReader { reader in
            ScrollView {
                content
            }
            .coordinateSpace(name: trackedScrollSpace)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { viewport = $0 }
            .onScrollGeometryChange(for: Bool.self) { $0.contentOffset.y + $0.contentInsets.top <= 1 } action: { _, top in
                atTop = top
                if top, userScrolled, model.topRow[tab] != nil { model.topRow[tab] = nil }
            }
            .onScrollPhaseChange { _, phase in
                if phase == .interacting { userScrolled = true; restoreUntil = .distantPast }
            }
            .onPreferenceChange(RowFrameKey.self) { frames in
                if userScrolled {
                    let top = atTop ? nil : Self.firstVisible(frames, top: 0, viewport: viewport)
                    if model.topRow[tab] != top {
                        model.topRow[tab] = top
                        PaneLog.log.debug("top row \(tab, privacy: .public) = \(top ?? "none", privacy: .public)")
                    }
                    return
                }
                guard Date() < restoreUntil, let target = model.topRow[tab], let frame = frames[target] else { return }
                if settledTop == nil, nextNudge != .distantPast, Date() >= nextNudge {
                    // Where the row rests once the scroll has landed (a quarter second after it).
                    settledTop = frame.minY
                } else if Date() >= nextNudge, let settled = settledTop, abs(frame.minY - settled) > 40 {
                    // Pushed off by the fold animation: scroll back, at most every quarter second.
                    nextNudge = Date().addingTimeInterval(0.25)
                    reader.scrollTo(TrackedRow.scrollID(target), anchor: .top)
                }
            }
            .onAppear {
                // Test hook: `-topRow <key>` seeds the remembered row (captures of the restore without a fold).
                if let i = ProcessInfo.processInfo.arguments.firstIndex(of: "-topRow"), i + 1 < ProcessInfo.processInfo.arguments.count,
                   model.topRow[tab] == nil { model.topRow[tab] = ProcessInfo.processInfo.arguments[i + 1] }
                guard !restored else { return }
                restored = true
                userScrolled = false
                settledTop = nil
                nextNudge = .distantPast
                guard let target = model.topRow[tab] else { return }
                PaneLog.log.debug("restore \(tab, privacy: .public) to \(target, privacy: .public)")
                restoreUntil = Date().addingTimeInterval(Self.restoreWindow)
                Task { @MainActor in
                    // After the first layout pass: the rows must exist to be scrolled to.
                    try? await Task.sleep(nanoseconds: 60_000_000)
                    reader.scrollTo(TrackedRow.scrollID(target), anchor: .top)
                    nextNudge = Date().addingTimeInterval(0.25)
                }
            }
        }
    }

    /// The topmost row still at least half on screen, in content coordinates: the visible band starts at `top` (the
    /// content offset plus the top inset) and is `viewport` high; nil when no row is.
    nonisolated static func firstVisible(_ frames: [String: CGRect], top: CGFloat, viewport: CGFloat) -> String? {
        frames.filter { $0.value.midY >= top && $0.value.minY < top + viewport }
            .min { $0.value.minY < $1.value.minY }?.key
    }

}

// MARK: - CCBG-86 (Rail Marks Alike): the wide inner screen's rail icons (§9a, B + C)

/// CCRM-118 (Header Glyphs) §1, 13: the account's one icon, on every account tab, the History strip and the account
/// header: its provider mark in the account's accent at the box's top-left, with the person / briefcase as a badge at the
/// lower right on a knocked-out disc (cleared circle r 8, ink figure in a 12 box at 6.5 in from the corner). `marked`
/// false (Appearance › Tag marks) draws the whole mark, same size and place, nothing cleared. Drawn in original rendering,
/// per look, so the system does not tint it.
@MainActor
enum RailIcon {
    /// The icon box and the mark inside it: tab bar, rail, sidebar, More 25/21; History strip 24/20; header 26/22.
    struct Size: Hashable {
        let side: CGFloat, mark: CGFloat
        static let tab = Size(side: 25, mark: 21)
        static let strip = Size(side: 24, mark: 20)
        static let header = Size(side: 26, mark: 22)
    }
    /// The system's tab icon box.
    static let side: CGFloat = Size.tab.side
    /// Drawn once per look, so a redraw of the tabs hands the system the same image.
    private static var cache: [String: UIImage] = [:]

    static func image(provider: Provider, accent: Color, tag: AccountTag, marked: Bool = true, scheme: ColorScheme, ink: Color,
                      size: Size = .tab) -> UIImage {
        let key = "\(provider.rawValue)|\(UIColor(accent).description)|\(tag.rawValue)|\(marked)|\(size.side)/\(size.mark)|\(scheme == .dark)|\(UIColor(ink).description)"
        if let hit = cache[key] { return hit }
        let made = draw(provider: provider, accent: accent, tag: tag, marked: marked, scheme: scheme, ink: ink, size: size)
        cache[key] = made
        return made
    }

    private static func draw(provider: Provider, accent: Color, tag: AccountTag, marked: Bool, scheme: ColorScheme, ink: Color,
                             size: Size) -> UIImage {
        let side = size.side
        let traits = UITraitCollection(userInterfaceStyle: scheme == .dark ? .dark : .light)
        let accentUI = UIColor(accent).resolvedColor(with: traits)
        let inkUI = UIColor(ink).resolvedColor(with: traits)
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))
        let image = renderer.image { ctx in
            if let mark = UIImage(named: provider.markAsset)?.withTintColor(accentUI, renderingMode: .alwaysOriginal) {
                mark.draw(in: CGRect(x: 0, y: 0, width: size.mark, height: size.mark))
            }
            guard marked else { return }
            // The badge: a disc of r 6.5 centred 6.5 in from the lower-right corner, cleared 1.5 wider out of the mark, the
            // glyph in ink inside it.
            let centre = CGPoint(x: side - 6.5, y: side - 6.5)
            ctx.cgContext.setBlendMode(.clear)
            ctx.cgContext.fillEllipse(in: CGRect(x: centre.x - 8, y: centre.y - 8, width: 16, height: 16))
            ctx.cgContext.setBlendMode(.normal)
            let config = UIImage.SymbolConfiguration(pointSize: 9, weight: .semibold)
            if let glyph = UIImage(systemName: TagGlyph.symbol(tag) + ".fill", withConfiguration: config)?
                .withTintColor(inkUI, renderingMode: .alwaysOriginal) {
                let s = glyph.size
                glyph.draw(in: CGRect(x: centre.x - s.width / 2, y: centre.y - s.height / 2, width: s.width, height: s.height))
            }
        }
        return image.withRenderingMode(.alwaysOriginal)
    }
}

/// The icon as a view, for the header and the History strip (hidden from VoiceOver: the name carries "Teams, Work account").
struct AccountIcon: View {
    let account: Account
    var size: RailIcon.Size = .strip
    /// The badge figure's ink; nil = the palette's ink. History's strip passes `sub` for an unselected tab (§7).
    var figure: Color? = nil
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        // Read so a Tag marks change in Settings redraws the icon at once.
        let _ = model.displayStamp
        Image(uiImage: RailIcon.image(provider: account.provider, accent: account.accent(scheme), tag: account.accountTag,
                                      marked: Prefs.tagMarks.shows(account.accountTag), scheme: scheme, ink: figure ?? p.ink, size: size))
            .frame(width: size.side, height: size.side)
            .accessibilityHidden(true)
    }
}

/// CCRM-116 (Inner Panes) §3b: taller than wide, the page title sits in the top bar's free leading side, as text — not in
/// a glass button capsule.
struct BarTitle<Title: View>: ToolbarContent {
    @ViewBuilder let title: Title
    var body: some ToolbarContent {
        if #available(iOS 26.0, *) {
            ToolbarItem(placement: .topBarLeading) { title }.sharedBackgroundVisibility(.hidden)
        } else {
            ToolbarItem(placement: .topBarLeading) { title }
        }
    }
}
