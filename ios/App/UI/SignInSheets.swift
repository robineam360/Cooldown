import CooldownCore
import SwiftUI

/// CCRM-94 (iOS App): Add account and the two sign-in sheets of wireframe rev B §3 — Claude by browser approval and a
/// pasted code (PKCE), ChatGPT by OpenAI's device code with all five states.
struct AddAccountSheet: View {
    @Binding var sheet: Sheet?
    @Environment(\.palette) private var p

    var body: some View {
        VStack(spacing: 12) {
            Text("Add account").font(.system(size: 20, weight: .bold)).padding(.bottom, 10)
            row(.claude, "Sign in with your Claude account in the browser, then paste the code it shows.") { sheet = .claudeSignIn }
            row(.chatgpt, "Sign in with a code on any device — no password in this app.") { sheet = .chatGPTSignIn }
            Text("Cooldown reads the same usage numbers the Claude and ChatGPT apps show. Tokens stay in this device’s Keychain. Not affiliated with Anthropic or OpenAI.")
                .font(.system(size: 12.5)).foregroundStyle(p.sub).padding(.top, 2)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20).padding(.top, 24)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    private func row(_ provider: Provider, _ detail: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                ProviderMark(provider: provider, size: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text(provider.displayName).font(.system(size: 17, weight: .semibold)).foregroundStyle(p.ink)
                    Text(detail).font(.system(size: 13.5)).foregroundStyle(p.txt).multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(p.sub)
            }
            .padding(14)
            .background(p.fill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

struct ClaudeSignInSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    @State private var code = ""
    @State private var opened = false
    @State private var working = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Sign in to Claude").font(.system(size: 20, weight: .bold)).padding(.bottom, 8)
            Text("1. Open the sign-in page and approve Cooldown.\n2. Copy the code Claude shows you and paste it here.")
                .font(.system(size: 15)).foregroundStyle(p.txt)
            Button {
                if let url = model.beginClaudeSignIn() { opened = true; openURL(url) }
            } label: {
                Text("Open claude.com sign-in").font(.system(size: 15, weight: .semibold)).foregroundStyle(.black)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Provider.claude.accent(scheme), in: Capsule())
            }
            .padding(.vertical, 16)
            TextField("Paste the code", text: $code)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
                .padding(.horizontal, 14).padding(.vertical, 13)
                .background(p.fill, in: RoundedRectangle(cornerRadius: 12))
            if let error { Text(error).font(.system(size: 13.5)).foregroundStyle(p.red).padding(.top, 8) }
            HStack(spacing: 10) {
                Button("Cancel") { dismiss() }.buttonStyle(Pill(fill: p.fill, ink: p.ink))
                Button(working ? "Signing in…" : "Sign in") {
                    working = true
                    Task {
                        error = await model.completeClaudeSignIn(pasted: code)
                        working = false
                        if error == nil { dismiss() }
                    }
                }
                .buttonStyle(Pill(fill: p.ink, ink: p.bg))
                .disabled(code.trimmingCharacters(in: .whitespaces).isEmpty || !opened || working)
                .opacity(code.isEmpty || !opened ? 0.45 : 1)
            }
            .padding(.top, 16)
            Text("Name this account after signing in (e.g. “Teams”); the plan tag comes from Anthropic.")
                .font(.system(size: 12.5)).foregroundStyle(p.sub).padding(.top, 14)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20).padding(.top, 24)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

struct ChatGPTSignInSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    @State private var flow: AppModel.DeviceFlow = .stage(.starting, detail: nil)
    @State private var attempt = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(DeviceCodeCopy.title).font(.system(size: 20, weight: .bold)).padding(.bottom, 6)
            switch flow {
            case .stage(let stage, let detail):
                Text(DeviceCodeCopy.body(for: stage, detail: detail)).font(.system(size: 15))
                    .foregroundStyle(stage == .starting ? p.txt : p.orange)
                if let label = DeviceCodeCopy.primaryLabel(for: stage) {
                    Button(label) { attempt += 1 }.buttonStyle(Pill(fill: p.ink, ink: p.bg, wide: true)).padding(.top, 18)
                }
                if stage == .unavailable || stage == .denied {
                    Link(DeviceCodeCopy.securityLink, destination: URL(string: DeviceCodeCopy.securitySettingsURL)!)
                        .font(.system(size: 14)).padding(.top, 12)
                }
            case .code(let started):
                Text(DeviceCodeCopy.body(for: .waiting)).font(.system(size: 14.5)).foregroundStyle(p.txt)
                Text(started.userCode).font(.system(size: 34, weight: .semibold, design: .monospaced)).kerning(3)
                    .frame(maxWidth: .infinity).padding(.top, 18).padding(.bottom, 6)
                    .textSelection(.enabled)
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    let left = max(0, started.expiresAtMs - Int64(context.date.timeIntervalSince1970 * 1000)) / 1000
                    Text("Expires in \(left / 60):\(String(format: "%02d", left % 60))")
                        .font(.system(size: 14)).monospacedDigit().foregroundStyle(p.sub).frame(maxWidth: .infinity)
                }
                Button {
                    if let url = URL(string: started.verifyUrl) { openURL(url) }
                } label: {
                    Text("Open \(started.verifyUrl.replacingOccurrences(of: "https://", with: ""))")
                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity).padding(.vertical, 11)
                        .background(Provider.chatgpt.accent(scheme), in: Capsule())
                }
                .padding(.top, 18)
                Text(DeviceCodeCopy.waitingNote).font(.system(size: 14)).foregroundStyle(p.sub)
                    .frame(maxWidth: .infinity).padding(.top, 10)
                if let hint = DeviceCodeCopy.hint(for: .waiting) {
                    Text(hint).font(.system(size: 12.5)).foregroundStyle(p.sub).padding(.top, 14)
                }
            case .done(let label, let plan):
                VStack(spacing: 6) {
                    ProviderMark(provider: .chatgpt, size: 48).padding(.top, 22)
                    Text("Signed in").font(.system(size: 18, weight: .semibold))
                    Text([label, plan].compactMap { $0 }.joined(separator: " · ")).font(.system(size: 14.5)).foregroundStyle(p.txt)
                }
                .frame(maxWidth: .infinity)
                Button("Done") { dismiss() }.buttonStyle(Pill(fill: p.ink, ink: p.bg, wide: true)).padding(.top, 22)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20).padding(.top, 24)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .task(id: attempt) {
            flow = .stage(.starting, detail: nil)
            flow = await model.startDeviceSignIn()
            if case .code(let started) = flow { flow = await model.waitForDevice(started) }
        }
    }
}

struct Pill: ButtonStyle {
    let fill: Color
    let ink: Color
    var wide = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(ink)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 11)
            .background(fill, in: Capsule())
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}
