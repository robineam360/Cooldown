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
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let accent = account.provider.accent(scheme)
        let five = CardContent.window(title: "5h", window: usage.session, kind: HistoryStats.sessionKind, history: history, nowMs: nowMs)
        let week = CardContent.window(title: "Weekly", window: usage.weekly, kind: HistoryStats.weeklyKind, history: history, nowMs: nowMs)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 14) {
                UsageRing(pct: (five ?? week)?.pct, pace: (five ?? week)?.pace, accent: accent, lineWidth: 7, fontSize: 20).frame(width: 80, height: 80)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 7) {
                        ProviderMark(provider: account.provider, size: 22)
                        Text(account.label).font(.system(size: 21, weight: .bold)).foregroundStyle(p.ink)
                    }
                    Text(asOf).font(.system(size: 13)).foregroundStyle(p.sub).fixedSize(horizontal: false, vertical: true)
                }
            }
            if let five { row(five, accent: accent).padding(.top, 18) }
            if let week { row(week, accent: accent).padding(.top, 14) }
            Rectangle().fill(p.line).frame(height: 1).padding(.vertical, 14)
            // A weekly-only plan has no 5h window: its Weekly trend takes the chart.
            Text(five == nil ? "Weekly trend" : "5h trend").font(.system(size: 13)).foregroundStyle(p.sub)
            if let chart = (five ?? week)?.chart {
                TrendChart(model: chart, accent: accent).frame(height: 130).padding(.top, 6)
            }
            Text("COOLDOWN").font(.system(size: 11, weight: .medium)).tracking(1.5).foregroundStyle(p.sub).padding(.top, 14)
        }
        .padding(20)
        .frame(width: 300)
        .background(p.bg)
    }

    private func row(_ c: WindowContent, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(c.title).foregroundStyle(p.txt)
                Spacer()
                Text(c.pct.map { "\(Int($0))%" } ?? "—").font(.system(size: 19, weight: .bold)).foregroundStyle(p.ink)
            }
            .font(.system(size: 16))
            UsageBar(pct: c.pct, pace: c.pace, accent: accent, height: 10)
            HStack {
                Text(c.notStarted ? c.resetsIn : c.paceLine).foregroundStyle(c.paceLineAlarm ? p.red : p.sub)
                Spacer()
                Text(c.resetsAt.replacingOccurrences(of: "Resets at ", with: "Resets ")).foregroundStyle(p.sub)
            }
            .font(.system(size: 12.5))
        }
    }

    /// "as of 6:26 pm, Thu 23 Oct" — the reading's own time, never the moment of sharing.
    private var asOf: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_GB")
        f.dateFormat = "EEE d MMM"
        return "as of \(CardContent.clock(fetchedAtMs, false, .current)), \(f.string(from: Date(ms: fetchedAtMs)))"
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
        return ShareCardView(account: account, usage: usage, history: model.histories[accountKey] ?? [],
                             fetchedAtMs: snapshot.fetchedAtMs, nowMs: model.nowMs)
    }

    private func render(_ card: ShareCardView) -> UIImage? {
        let renderer = ImageRenderer(content: card.environment(\.palette, p).environment(\.colorScheme, scheme))
        renderer.scale = 3
        return renderer.uiImage
    }
}
