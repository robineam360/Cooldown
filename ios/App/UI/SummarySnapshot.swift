import CooldownCore
import SwiftUI

/// CCRM-112 (Summary Snapshot), wireframe design/2026-11-10-v20-feedback-wireframes.html rev B §8: one share card for the
/// whole Summary — a header with the filter's name, the time and the order's name; the rows in the screen's order and filter
/// (full rows, one line each from six); the Pace map with the layers chosen on screen (never the chips, never a pinned callout
/// or its dimming); the wordmark. Names are drawn, e-mail addresses never. 300 pt wide at 3×, as the account card.
struct SummarySnapshotView: View {
    let entries: [SummaryEntry]
    /// The filter's header: "All accounts", "Work accounts", "Personal accounts".
    let title: String
    /// The order's name, right of the header: "your order", "most room first".
    let orderName: String
    /// The newest reading among the rows shown (the card says when the numbers are from, never when it was shared).
    let asOfMs: Int64
    let nowMs: Int64
    var layers = MapLayers.saved
    /// Sample data: the same violet band as the account card, so made-up numbers can't pass for a real share.
    var sample = false
    /// CCRM-115 (Row Tag Glyph): the glyph column, drawn only while the rows on the card carry both tags (so only under All;
    /// an install whose accounts are all Personal shares the card without it).
    var glyphs = false
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let oneLine = entries.count >= SummaryFilter.oneLineFrom
        let accounts = entries.map { PaceMapRules.Account(usedPct: $0.usedPct, clocked: $0.clocked) }
        let foot = PaceMapRules.foot(accounts)
        VStack(alignment: .leading, spacing: 0) {
            if sample {
                Text("● SAMPLE DATA").font(.system(size: 8.5, weight: .bold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 16).background(SampleBanner.purple)
                    .padding(.horizontal, -20).padding(.top, -20).padding(.bottom, 14)
            }
            HStack(alignment: .firstTextBaseline) {
                Text(title).font(.system(size: 17, weight: .bold)).foregroundStyle(p.ink)
                Spacer(minLength: 8)
                Text(orderName).font(.system(size: 11)).foregroundStyle(p.sub)
            }
            Text(ShareCardView.asOf(asOfMs)).font(.system(size: 11)).foregroundStyle(p.sub.opacity(0.9)).padding(.top, 1)
            ForEach(entries) { entry in
                if oneLine { OneLine(entry: entry, nowMs: nowMs, first: entry.id == entries.first?.id, glyph: glyphs) }
                else { FullRow(entry: entry, nowMs: nowMs, glyph: glyphs) }
            }
            Rectangle().fill(p.line).frame(height: 1).padding(.top, 14).padding(.bottom, 6)
            Text("Pace map").font(.system(size: 10.5)).foregroundStyle(p.ink)
            PaceMap(entries: entries.filter { PaceMapRules.isPlotted(usedPct: $0.usedPct, clocked: $0.clocked) },
                    layers: layers, mode: Display.mode == .vsPace ? .used : Display.mode, empty: foot.empty,
                    pinned: .constant(nil))
                .frame(height: 200).padding(.top, 2)
            HStack(alignment: .firstTextBaseline) {
                Text(foot.left)
                Spacer(minLength: 6)
                if let count = foot.count { Text(count).lineLimit(1) }
            }
            .font(.system(size: 9.5)).foregroundStyle(p.sub).padding(.top, 2)
            Text("COOLDOWN").font(.system(size: 9.5, weight: .medium)).tracking(1.5).foregroundStyle(p.sub.opacity(0.8)).padding(.top, 10)
        }
        .padding(20)
        .frame(width: 300)
        .background(p.bg)
        .clipped()
    }

    // MARK: shared pieces

    /// What the pace slot says: the pace line's tinted span ("13h 59m ahead"), "Out until Thu 8:00 AM" red when spent,
    /// "Stale" when the reading is old, "No reading" / "Sign in again" without one.
    static func slot(_ e: SummaryEntry, nowMs: Int64, short: Bool) -> (text: String, tone: Tone) {
        if e.face.status == .signIn { return ("Sign in again", .quiet) }
        switch e.group {
        case .noReading: return ("No reading", .quiet)
        case .spent: return (short ? SummaryWords.word(e) : SummaryWords.line(e, nowMs: nowMs), .red)
        case .held: return (SummaryWords.word(e), .orange)
        case .eligible:
            if e.face.status == .stale { return ("Stale", .quiet) }
            if let line = SummaryWords.paceLine(e, nowMs: nowMs) { return (line.lead, .pace(line)) }
            return (SummaryWords.line(e, nowMs: nowMs), .quiet)
        }
    }

    enum Tone { case quiet, red, orange, pace(PaceLine) }

    /// A row with no reading or a signed-out account: at 50%, no bar fill.
    static func dead(_ e: SummaryEntry) -> Bool { e.usedPct == nil || e.face.status == .signIn }

    /// "Resets Sun 9:30 PM" (the Weekly's reset), right in a full row.
    static func resets(_ e: SummaryEntry, nowMs: Int64) -> String? {
        guard !dead(e), let reset = e.face.weeklyReset else { return nil }
        return "Resets " + CardContent.dayClock(reset.ms, nowMs, Display.use24h, .current)
    }

    fileprivate struct FullRow: View {
        let entry: SummaryEntry
        let nowMs: Int64
        var glyph = false
        @Environment(\.palette) private var p
        @Environment(\.colorScheme) private var scheme

        var body: some View {
            let accent = entry.account.accent(scheme)
            let dead = SummarySnapshotView.dead(entry)
            let slot = SummarySnapshotView.slot(entry, nowMs: nowMs, short: false)
            let used = dead ? nil : entry.usedPct
            HStack(spacing: 10) {
                ring(accent: accent)
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 6) {
                        HStack(spacing: 4) {
                            if glyph { TagRowGlyph(tag: entry.account.accountTag, compact: true) }
                            ProviderMark(provider: entry.account.provider, size: 14)
                        }
                        Text(entry.account.label).font(.system(size: 13, weight: .semibold)).foregroundStyle(p.ink).lineLimit(1)
                        Spacer(minLength: 4)
                        figure(used: used)
                    }
                    GrammarBar(pct: used, pace: dead || entry.group == .spent ? nil : entry.elapsedPct, accent: accent, height: 8,
                               surface: p.bg).padding(.top, 3)
                    HStack(spacing: 6) {
                        Group {
                            switch slot.tone {
                            case .pace(let line): Text(slot.text).fontWeight(.semibold).foregroundStyle(line.tone(scheme, ink: p.ink))
                            case .red: Text(slot.text).foregroundStyle(p.red)
                            case .orange: Text(slot.text).fontWeight(.semibold).foregroundStyle(p.orange)
                            case .quiet: Text(slot.text).foregroundStyle(p.sub)
                            }
                        }
                        .lineLimit(1).minimumScaleFactor(0.8)
                        Spacer(minLength: 4)
                        if let resets = SummarySnapshotView.resets(entry, nowMs: nowMs) {
                            Text(resets).foregroundStyle(p.sub).lineLimit(1).minimumScaleFactor(0.8)
                        }
                    }
                    .font(.system(size: 10.5)).padding(.top, 4)
                }
            }
            .opacity(dead ? 0.5 : 1)
            .padding(.top, 12)
        }

        /// Used (Left when chosen); Vs pace renders as Used, as the account card does.
        private func figure(used: Double?) -> some View {
            let text = Display.mode == .vsPace ? Text(Display.usage(used)) : Display.pctText(used, pace: entry.elapsedPct, size: 17)
            return text.font(.system(size: 17, weight: .bold))
                .foregroundStyle(entry.group == .spent ? p.red : p.ink).lineLimit(1).fixedSize()
        }

        private func ring(accent: Color) -> some View {
            let f = entry.face
            let state: GrammarRing.State = entry.group == .noReading ? .normal
                : !entry.hasSession ? .notApplicable : entry.group == .spent ? .grey : .normal
            let pct = state == .notApplicable ? nil : f.pct
            return GrammarRing(pct: pct, pace: f.pace, accent: accent, figure: nil, figureColour: nil, state: state, surface: p.bg)
                .frame(width: 32, height: 32)
        }
    }

    /// From six rows: one line each — mark, name, bar, the pace slot, the figure.
    fileprivate struct OneLine: View {
        let entry: SummaryEntry
        let nowMs: Int64
        var first = false
        var glyph = false
        @Environment(\.palette) private var p
        @Environment(\.colorScheme) private var scheme

        var body: some View {
            let accent = entry.account.accent(scheme)
            let dead = SummarySnapshotView.dead(entry)
            let slot = SummarySnapshotView.slot(entry, nowMs: nowMs, short: true)
            let used = dead ? nil : entry.usedPct
            HStack(spacing: 6) {
                HStack(spacing: 4) {
                    if glyph { TagRowGlyph(tag: entry.account.accountTag, compact: true) }
                    ProviderMark(provider: entry.account.provider, size: 14)
                }
                Text(entry.account.label).font(.system(size: 12, weight: .semibold)).foregroundStyle(p.ink).lineLimit(1)
                    .frame(width: 58, alignment: .leading)
                UsageBar(pct: used, pace: dead || entry.group == .spent ? nil : entry.elapsedPct, accent: accent, height: 7)
                    .frame(minWidth: 24)
                Group {
                    switch slot.tone {
                    case .pace(let line): Text(slot.text).fontWeight(.semibold).foregroundStyle(line.tone(scheme, ink: p.ink))
                    case .red: Text(slot.text).foregroundStyle(p.red)
                    case .orange: Text(slot.text).fontWeight(.semibold).foregroundStyle(p.orange)
                    case .quiet: Text(slot.text).foregroundStyle(p.sub)
                    }
                }
                .font(.system(size: 10)).lineLimit(1).minimumScaleFactor(0.7).frame(width: 64, alignment: .leading)
                (Display.mode == .vsPace ? Text(Display.usage(used)) : Display.pctText(used, pace: entry.elapsedPct, size: 14))
                    .font(.system(size: 14, weight: .bold)).foregroundStyle(entry.group == .spent ? p.red : p.ink)
                    .lineLimit(1).minimumScaleFactor(0.7).frame(width: 36, alignment: .trailing)
            }
            .frame(height: 26)
            .opacity(dead ? 0.5 : 1)
            .padding(.top, first ? 8 : 0)
        }
    }
}

/// Previews the Summary snapshot (the screen's filter, order and map layers) and shares it as an image.
struct SummarySnapshotSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    if let card {
                        card.clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .shadow(radius: 8, y: 2)
                        if let image = render(card) {
                            ShareLink(item: Image(uiImage: image),
                                      preview: SharePreview("Cooldown — \(title)", image: Image(uiImage: image))) {
                                Label("Share", systemImage: "square.and.arrow.up").font(.system(size: 16, weight: .semibold))
                            }
                            .buttonStyle(.borderedProminent).tint(p.ink).foregroundStyle(p.bg)
                        }
                    } else {
                        Text("Nothing to share yet — no account has a reading.").foregroundStyle(p.txt)
                    }
                }
                .padding(.vertical, 16)
                .frame(maxWidth: .infinity)
            }
            .background(p.bg.ignoresSafeArea())
            .navigationTitle("Share snapshot").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }

    private var title: String {
        SummaryEntry.current(model: model, filter: Prefs.summaryFilter, order: Prefs.summaryOrder).resolved.snapshotHeader
    }

    private var card: SummarySnapshotView? {
        Self.make(model: model, filter: Prefs.summaryFilter, order: Prefs.summaryOrder, layers: .saved)
    }

    /// The card for the screen's filter, order and map layers; nil when no account shown has a reading. The "as of" time is
    /// the newest reading among the rows shown.
    static func make(model: AppModel, filter: String?, order: SummaryOrder, layers: MapLayers) -> SummarySnapshotView? {
        let (entries, resolved) = SummaryEntry.current(model: model, filter: filter, order: order)
        guard entries.contains(where: { $0.usedPct != nil }) else { return nil }
        let asOf = entries.compactMap { model.snapshots[$0.account.key]?.fetchedAtMs }.max() ?? model.nowMs
        return SummarySnapshotView(entries: entries, title: resolved.snapshotHeader, orderName: order.header,
                                   asOfMs: asOf, nowMs: model.nowMs, layers: layers, sample: model.isSample,
                                   glyphs: resolved.snapshotGlyphs)
    }

    private func render(_ card: SummarySnapshotView) -> UIImage? {
        let renderer = ImageRenderer(content: card.environment(\.palette, p).environment(\.colorScheme, scheme))
        renderer.scale = 3
        return renderer.uiImage
    }
}
