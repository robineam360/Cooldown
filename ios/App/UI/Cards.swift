import CooldownCore
import SwiftUI

/// CCRM-94 (iOS App): the account cards of wireframe rev B §1–2 — the Android v1.7 cards, nothing removed.
struct CardBox<Content: View>: View {
    @ViewBuilder var content: Content
    @Environment(\.palette) private var p
    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(p.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

/// The 5-hour card: title, number, bar with pace tick, the full chart when expanded, the pace and
/// projection lines, and both reset lines.
struct WindowCard: View {
    let content: WindowContent
    let accent: Color
    @Binding var expanded: Bool
    var chartHeight: CGFloat = 150
    var dimmed = false
    var rightResetLine: String?
    @Environment(\.palette) private var p

    var body: some View {
        CardBox {
            header
            UsageBar(pct: content.pct, pace: content.pace, accent: accent, height: 11, dimmed: dimmed)
                .padding(.top, 12)
            if expanded, let chart = content.chart {
                TrendChart(model: chart, accent: accent, dimmed: dimmed)
                    .frame(height: chartHeight)
                    .padding(.top, 12)
                Text(content.paceLine)
                    .foregroundStyle(content.paceLineAlarm ? p.red : p.txt)
                    .font(.system(size: 14.5)).padding(.top, 6)
                if let line = content.projectionLine {
                    Text(line).foregroundStyle(p.txt).font(.system(size: 14.5)).padding(.top, 4)
                }
            }
            HStack {
                Text(content.resetsIn)
                Spacer(minLength: 8)
                Text(rightResetLine ?? content.resetsAt)
            }
            .font(.system(size: 14.5)).foregroundStyle(p.txt).padding(.top, 10)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(content.title), \(content.pct.map { "\(Int($0)) percent" } ?? "no reading"). \(content.paceLine). \(content.resetsIn).")
    }

    private var header: some View {
        Button { withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() } } label: {
            HStack(spacing: 8) {
                Text(content.title).foregroundStyle(p.txt)
                Spacer()
                Text(content.pct.map { "\(Int($0))%" } ?? "—")
                    .font(.system(size: 19, weight: .bold)).monospacedDigit()
                    .foregroundStyle(dimmed ? p.sub : ((content.pct ?? 0) >= 100 ? p.red : p.ink))
                Image(systemName: expanded ? "chevron.up" : "chevron.down")
                    .font(.system(size: 15, weight: .semibold)).foregroundStyle(p.sub)
            }
            .font(.system(size: 17))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// The 7-day card: "All" and, when the account has one, a model cap side by side (Android's two halves).
struct WeeklyCard: View {
    let all: WindowContent
    let cap: WindowContent?
    let accent: Color
    @Binding var expanded: Bool
    var dimmed = false
    @Environment(\.palette) private var p

    var body: some View {
        CardBox {
            Button { withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() } } label: {
                HStack {
                    Text("7-day window").foregroundStyle(p.txt)
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(p.sub)
                }
                .font(.system(size: 17)).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.bottom, 10)
            if let cap {
                HStack(alignment: .top, spacing: 16) { half(all, short: true); half(cap, short: true) }
            } else {
                half(all, short: false)
            }
        }
    }

    private func half(_ c: WindowContent, short: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(c.title).foregroundStyle(p.txt)
                Spacer()
                Text(c.pct.map { "\(Int($0))%" } ?? "—").font(.system(size: 17, weight: .bold)).monospacedDigit()
                    .foregroundStyle(dimmed ? p.sub : p.ink)
            }
            .font(.system(size: 16))
            UsageBar(pct: c.pct, pace: c.pace, accent: accent, height: 9, dimmed: dimmed).padding(.top, 10)
            if expanded, let chart = c.chart {
                TrendChart(model: chart, accent: accent, dimmed: dimmed).frame(height: 120).padding(.top, 10)
                Text(c.paceLine).foregroundStyle(c.paceLineAlarm ? p.red : p.txt).font(.system(size: 13.5)).padding(.top, 6)
                if let line = c.projectionLine {
                    Text(line).foregroundStyle(p.txt).font(.system(size: 13.5)).padding(.top, 3)
                }
            }
            Text(short ? c.resetsIn : "\(c.resetsIn) · \(c.resetsAt.replacingOccurrences(of: "Resets at ", with: ""))")
                .font(.system(size: 13.5)).foregroundStyle(p.txt).padding(.top, 6)
        }
    }
}

struct CreditsCard: View {
    let text: String
    let pct: Int?
    let isBalance: Bool
    @Environment(\.palette) private var p
    var body: some View {
        CardBox {
            HStack(spacing: 8) {
                Text(isBalance ? "Credits" : "Usage credits").fontWeight(isBalance ? .regular : .semibold).foregroundStyle(p.txt)
                if !isBalance { Text(text).foregroundStyle(p.txt) }
                Spacer()
                if isBalance {
                    Text(text).fontWeight(.semibold)
                } else if let pct {
                    Text("\(pct)%").fontWeight(.bold)
                    Image(systemName: "info.circle").foregroundStyle(p.sub).font(.system(size: 14))
                }
            }
            .font(.system(size: 16))
        }
    }
}

struct NoticeView: View {
    let notice: Notice
    var onAction: () -> Void = {}
    @Environment(\.palette) private var p
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(notice.title).font(.system(size: 15, weight: .semibold)).foregroundStyle(p.ink)
            Text(notice.body).font(.system(size: 14)).foregroundStyle(p.txt)
            if let action = notice.action {
                Button(action, action: onAction)
                    .font(.system(size: 14.5, weight: .semibold))
                    .padding(.horizontal, 14).padding(.vertical, 7)
                    .background(p.ink, in: Capsule()).foregroundStyle(p.bg)
                    .padding(.top, 8)
            }
        }
        .padding(.horizontal, 13).padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tint.opacity(0.16), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
    private var tint: Color { notice.tone == .error ? p.red : p.orange }
}

/// One account's cards, in the order and fold the user chose. Used by the iPhone tabs and both iPad halves.
struct AccountCards: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    let account: Account
    var twoAcross = false
    var chartHeight: CGFloat = 150
    var onSignIn: () -> Void = {}
    @AppStorage("fold.5h") private var fiveHourOpen = true
    @AppStorage("fold.7d") private var weeklyOpen = false
    @AppStorage("fold.7d.wide") private var weeklyOpenWide = true

    var body: some View {
        let accent = account.provider.accent(scheme)
        let state = model.states[account.key] ?? AccountState()
        let snapshot = model.snapshots[account.key]
        let notice = Notice.forAccount(label: account.label, state: state, snapshot: snapshot, nowMs: model.nowMs)
        let dimmed = notice?.tone == .error || notice?.tone == .warning
        let usage = model.usage(account.key)
        let history = model.histories[account.key] ?? []
        VStack(spacing: 12) {
            if let notice { NoticeView(notice: notice, onAction: onSignIn) }
            if state.lastKind != "plan", let usage {
                let five = CardContent.window(title: "5-hour window", window: usage.session, kind: HistoryStats.sessionKind,
                                              history: history, nowMs: model.nowMs)
                let all = CardContent.window(title: "All", window: usage.weekly, kind: HistoryStats.weeklyKind,
                                             history: history, nowMs: model.nowMs)
                let cap = usage.modelCaps.first.flatMap {
                    CardContent.window(title: $0.modelName.replacingOccurrences(of: "Claude ", with: ""), window: $0.window,
                                       kind: HistoryStats.weeklyKind, history: [], nowMs: model.nowMs)
                }
                if twoAcross {
                    HStack(alignment: .top, spacing: 14) {
                        if let five { WindowCard(content: five, accent: accent, expanded: $fiveHourOpen, chartHeight: chartHeight, dimmed: dimmed) }
                        if let all { WeeklyCard(all: all, cap: cap, accent: accent, expanded: $weeklyOpenWide, dimmed: dimmed) }
                    }
                } else {
                    if let five {
                        WindowCard(content: five, accent: accent, expanded: $fiveHourOpen, chartHeight: chartHeight, dimmed: dimmed,
                                   rightResetLine: dimmed ? "as of \(CardContent.clock(snapshot?.fetchedAtMs ?? 0, false, .current))" : nil)
                    }
                    if let all { WeeklyCard(all: all, cap: cap, accent: accent, expanded: $weeklyOpen, dimmed: dimmed) }
                }
                if !twoAcross, let credits = CardContent.credits(usage.credits) {
                    CreditsCard(text: credits.text, pct: credits.pct, isBalance: credits.pct == nil)
                }
            }
        }
    }
}
