import SwiftUI

/// CCRM-94 (iOS App) parity wireframes rev B §7: the reviewer sample-data mode. Entered from the empty screen or Add account,
/// it swaps the app's model for the built-in demo (made-up accounts, temporary storage, no Keychain, nothing saved or sent)
/// and swaps back on Exit, leaving the real accounts untouched.
struct SampleControl: @unchecked Sendable {
    var enter: () -> Void = {}
    var exit: () -> Void = {}
}

private struct SampleControlKey: EnvironmentKey { static let defaultValue = SampleControl() }
extension EnvironmentValues {
    var sampleControl: SampleControl {
        get { self[SampleControlKey.self] }
        set { self[SampleControlKey.self] = newValue }
    }
}

/// The purple band above each tab page while the sample data is showing.
struct SampleBanner: View {
    @Environment(\.sampleControl) private var control
    @Environment(\.palette) private var p
    static let purple = Color(red: 0.486, green: 0.302, blue: 1.0)   // #7C4DFF, Android's synthetic colour

    var body: some View {
        HStack(spacing: 8) {
            (Text("● ").foregroundStyle(Self.purple) + Text("Sample data").fontWeight(.semibold).foregroundStyle(Self.purple)
             + Text(" — made-up accounts").foregroundStyle(p.ink))
                .font(.system(size: 13.5))
            Spacer(minLength: 8)
            Button("Exit") { control.exit() }.font(.system(size: 13.5, weight: .semibold)).foregroundStyle(p.ink)
        }
        .padding(.horizontal, 14).padding(.vertical, 9)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Self.purple.opacity(0.16), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// What the sample mode changes in Settings, in one place for the tests.
enum SampleRules {
    /// The Settings row's right-hand word for an account.
    static let accountWord = "Sample"
    static let entryTitle = "Try with sample data"
    static let entryDetail = "Three made-up accounts to look around. Nothing is saved or sent."
}
