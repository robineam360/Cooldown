import CooldownCore
import SwiftUI

/// CCRM-94 (iOS App): the share card, ported from Android's (CCRM-24 (Share Card), design/research/2026-09-25-share-card):
/// the account's ring with its figure, its name and "as of" stamp, the 5h and Weekly rows with pace and reset, the
/// 5h trend, and the wordmark. Rendered to an image and shared from a preview sheet.
struct ShareCardView: View {
    let account: Account
    let usage: UsageData
    let history: [HistoryPoint]
    let fetchedAtMs: Int64
    let nowMs: Int64
    /// CCRM-96 (Weekly First): the account's Headline and whether its 5h is the binding window, for the row order.
    var headline: Headline = .default
    var sessionBinding = false
    /// Sample data: the card carries Android's synthetic band, so made-up numbers can't pass for a real share.
    var sample = false
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let accent = account.accent(scheme)
        let five = CardContent.window(title: "5h", window: usage.session, kind: HistoryStats.sessionKind, history: history, nowMs: nowMs, use24h: Display.use24h)
        let week = CardContent.window(title: "Weekly", window: usage.weekly, kind: HistoryStats.weeklyKind, history: history, nowMs: nowMs, use24h: Display.use24h)
        let order = ShareCardPlan.order(headline: headline, hasSession: five != nil, sessionBinding: sessionBinding,
                                        weeklySpent: Pace.isSpent(usage.weekly?.percent))
        let rows: [WindowContent] = order.compactMap { $0 == .session ? five : week }
        let weeklyLeads = order.first == .weekly && order.count == 2
        let weekSpent = weeklyLeads && Pace.isSpent(usage.weekly?.percent)   // a spent week dims the 5h row and the ring
        VStack(alignment: .leading, spacing: 0) {
            if sample {
                Text("● SAMPLE DATA").font(.system(size: 8.5, weight: .bold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 16).background(Color(red: 0.486, green: 0.302, blue: 1.0))
                    .padding(.horizontal, -20).padding(.top, -20).padding(.bottom, 20)
            }
            HStack(spacing: 14) {
                // The ring is the 5h gauge; Weekly only when the plan has no 5h window.
                UsageRing(pct: (five ?? week)?.pct, pace: (five ?? week)?.pace, accent: accent, lineWidth: 7, fontSize: 20).frame(width: 80, height: 80)
                    .opacity(weekSpent ? 0.45 : 1)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 7) {
                        ProviderMark(provider: account.provider, size: 22)
                        Text(account.label).font(.system(size: 21, weight: .bold)).foregroundStyle(p.ink)
                    }
                    Text(asOf).font(.system(size: 13)).foregroundStyle(p.sub).fixedSize(horizontal: false, vertical: true)
                }
            }
            ForEach(Array(rows.enumerated()), id: \.offset) { i, c in
                row(c, accent: accent, figure: i == 0 ? 24 : 19, spentWeek: weekSpent && i == 0,
                    greyed: weekSpent && i == 1)
                    .padding(.top, i == 0 ? 18 : 14)
            }
            Rectangle().fill(p.line).frame(height: 1).padding(.vertical, 14)
            // The trend follows the lead window; a weekly-only plan's is the Weekly.
            Text("\(rows.first?.title ?? "Weekly") trend").font(.system(size: 13)).foregroundStyle(p.sub)
            if let chart = rows.first?.chart {
                TrendChart(model: chart, accent: accent).frame(height: 130).padding(.top, 6)
            }
            Text("COOLDOWN").font(.system(size: 11, weight: .medium)).tracking(1.5).foregroundStyle(p.sub).padding(.top, 14)
        }
        .padding(20)
        .frame(width: 300)
        .background(p.bg)
        .clipped()
    }

    private func row(_ c: WindowContent, accent: Color, figure: CGFloat, spentWeek: Bool, greyed: Bool) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(c.title).foregroundStyle(p.txt)
                Spacer()
                // Vs pace renders the share card as Used (wireframe CCRM-104 §7, approved 2026-11-06).
                (Display.mode == .vsPace ? Text(Display.usage(c.pct)) : Display.pctText(c.pct, pace: c.pace, size: figure))
                    .font(.system(size: figure, weight: .bold)).foregroundStyle(spentWeek ? p.red : p.ink)
            }
            .font(.system(size: 16))
            UsageBar(pct: c.pct, pace: c.pace, accent: accent, height: 10)
            HStack {
                // v2.0: Weekly reads its band words; the 5h (no Pace) its projection.
                // A greyed 5h (the week is spent) leaves its phrase slot empty and keeps its reset.
                // CCRM-104 D9: Weekly's left slot is the pace line's tinted span ("13h 59m ahead"); "Resets …" stays right.
                if !greyed, !c.notStarted, !spentWeek, let line = c.line {
                    Text(line.lead).fontWeight(.semibold).foregroundStyle(line.tone(colorSchemeFor(p), ink: p.ink))
                } else {
                    Text(greyed ? "" : c.notStarted ? c.resetsIn : (c.paceLine.isEmpty ? (c.projectionFold.map { $0.prefix(1).uppercased() + $0.dropFirst() } ?? "") : c.paceLine)).foregroundStyle(c.paceLineAlarm || spentWeek ? p.red : p.sub)
                }
                Spacer()
                Text(c.resetsAt.replacingOccurrences(of: "Resets at ", with: "Resets ")).foregroundStyle(p.sub)
            }
            .font(.system(size: 12.5))
        }
        .opacity(greyed ? 0.45 : 1)
    }

    /// "as of 6:26 pm, Thu 23 Oct" — the reading's own time, never the moment of sharing.
    private var asOf: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_GB")
        f.dateFormat = "EEE d MMM"
        return "as of \(CardContent.clock(fetchedAtMs, Display.use24h, .current)), \(f.string(from: Date(ms: fetchedAtMs)))"
    }
}

/// Previews the card for the selected account and shares it as an image.
struct ShareSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    let accountKey: String

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                if let card {
                    card.clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .shadow(radius: 8, y: 2)
                    if let image = render(card) {
                        ShareLink(item: Image(uiImage: image),
                                  preview: SharePreview("Cooldown — \(model.account(accountKey)?.label ?? "")", image: Image(uiImage: image))) {
                            Label("Share", systemImage: "square.and.arrow.up").font(.system(size: 16, weight: .semibold))
                        }
                        .buttonStyle(.borderedProminent).tint(p.ink).foregroundStyle(p.bg)
                    }
                } else {
                    Text("Nothing to share yet — this account has no reading.").foregroundStyle(p.txt)
                }
                Spacer(minLength: 0)
            }
            .padding(.top, 16)
            .frame(maxWidth: .infinity)
            .background(p.bg.ignoresSafeArea())
            .navigationTitle("Share card").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }

    private var card: ShareCardView? {
        guard let account = model.account(accountKey), let usage = model.usage(accountKey),
              let snapshot = model.snapshots[accountKey] else { return nil }
        let face = Face.build(account: account, snapshot: snapshot, state: model.states[accountKey] ?? AccountState(),
                              history: model.histories[accountKey] ?? [], nowMs: model.nowMs)
        return ShareCardView(account: account, usage: usage, history: model.histories[accountKey] ?? [],
                             fetchedAtMs: snapshot.fetchedAtMs, nowMs: model.nowMs,
                             headline: Prefs.headline(accountKey), sessionBinding: face.sessionBinding, sample: model.isSample)
    }

    private func render(_ card: ShareCardView) -> UIImage? {
        let renderer = ImageRenderer(content: card.environment(\.palette, p).environment(\.colorScheme, scheme))
        renderer.scale = 3
        return renderer.uiImage
    }
}
