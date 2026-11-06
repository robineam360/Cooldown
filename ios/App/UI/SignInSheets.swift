import AuthenticationServices
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
            // CCRM-55 (I1): hidden on a build without the client secret.
            if Antigravity.available {
                row(.antigravity, "Signs in with Google in your browser, then paste the code back.") { sheet = .geminiSignIn }
            }
            Text("Cooldown reads the same usage numbers the Claude, ChatGPT and Gemini apps show. Tokens stay in this device’s Keychain. Not affiliated with Anthropic, OpenAI or Google. “Gemini” and “Antigravity” are trademarks of Google LLC.")
                .font(.system(size: 12.5)).foregroundStyle(p.sub).padding(.top, 2)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20).padding(.top, 24)
        .presentationDetents([.medium, .large])
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

/// CCRM-55 (Antigravity Account) rev B I2–I5: Google's sign-in in an `ASWebAuthenticationSession` (never a web view —
/// Google refuses them), ending on Google's hosted page with a `4/…` code the user pastes back here.
struct GeminiSignInSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.webAuthenticationSession) private var webAuth
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    @State private var code = ""
    @State private var opened = false
    @State private var working = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Sign in to Gemini").font(.system(size: 20, weight: .bold)).padding(.bottom, 8)
            if !opened {
                Text("1. Sign in with Google and approve Cooldown.\n2. Copy the code Google shows you and paste it here.")
                    .font(.system(size: 15)).foregroundStyle(p.txt)
                Button { open() } label: {
                    Text("Sign in with Google").font(.system(size: 15, weight: .semibold)).foregroundStyle(.black)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(Provider.antigravity.accent(scheme), in: Capsule())
                }
                .padding(.vertical, 16)
            } else {
                Text("Copy the code Google shows you and paste it here.").font(.system(size: 15)).foregroundStyle(p.txt)
                    .padding(.bottom, 14)
                TextField(Self.placeholder, text: $code)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .lineLimit(1).truncationMode(.tail)
                    .padding(.horizontal, 14).padding(.vertical, 13)
                    .background(p.fill, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(alignment: .bottom) { if error != nil { Rectangle().fill(p.red).frame(height: 2).padding(.horizontal, 6) } }
                    .onChange(of: code) { error = nil }
                if let error {
                    Text(error).font(.system(size: 13.5)).foregroundStyle(p.red).padding(.top, 8)
                } else {
                    Text("The code starts with 4/. You can also paste the whole address.")
                        .font(.system(size: 13)).foregroundStyle(p.sub).padding(.top, 8)
                }
                HStack(spacing: 10) {
                    if error != nil {
                        Button("Start again") { code = ""; error = nil; open() }.buttonStyle(Pill(fill: p.fill, ink: p.ink))
                    } else {
                        Button("Cancel") { dismiss() }.buttonStyle(Pill(fill: p.fill, ink: p.ink))
                    }
                    Button(working ? "Signing in…" : "Continue") {
                        working = true
                        Task {
                            error = await model.completeAntigravitySignIn(pasted: code)
                            working = false
                            if error == nil { dismiss() }
                        }
                    }
                    .buttonStyle(Pill(fill: p.ink, ink: p.bg))
                    .disabled(!Self.canContinue(code: code, error: error, working: working))
                    .opacity(Self.canContinue(code: code, error: error, working: working) ? 1 : 0.45)
                }
                .padding(.top, 16)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20).padding(.top, 24)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    /// The field's label, as decided (rev B §5).
    static let placeholder = "Paste the code from the sign-in page"

    /// Continue needs a code, no error showing (editing the field clears it), and no exchange in flight.
    static func canContinue(code: String, error: String?, working: Bool) -> Bool {
        !code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && error == nil && !working
    }

    /// Test seam: the sheet as it stands after a paste that failed (I4).
    init(code: String = "", opened: Bool = false, error: String? = nil) {
        _code = State(initialValue: code)
        _opened = State(initialValue: opened)
        _error = State(initialValue: error)
    }

    /// Opens Google's page. The session never "completes" — the page ends on Google's own address, not ours — so
    /// closing it is the normal way back; its error is ignored.
    private func open() {
        let url = model.beginAntigravitySignIn()
        opened = true
        Task { _ = try? await webAuth.authenticate(using: url, callbackURLScheme: "cooldown") }
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
