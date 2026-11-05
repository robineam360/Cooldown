import SwiftUI

/// CCRM-94 (iOS App) parity wireframes rev B §1: the rename sheet, used by the account page's Name row and the touch-and-hold
/// Rename. A medium sheet like Android's dialog: the name (16 characters at most, with its counter), a "Use <email name>"
/// row when an e-mail is known, and the help line. Clearing the field and saving goes back to the default name.
struct RenameSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var p
    let account: Account
    @State private var text: String

    init(account: Account) {
        self.account = account
        _text = State(initialValue: account.label)
    }

    var body: some View {
        let fallback = model.defaultLabel(for: account.key)
        NavigationStack {
            Form {
                Section {
                    HStack {
                        TextField(fallback, text: $text)
                            .textInputAutocapitalization(.words)
                            .submitLabel(.done)
                            .onSubmit(save)
                            .onChange(of: text) { _, new in if new.count > AccountEmail.nameLimit { text = String(new.prefix(AccountEmail.nameLimit)) } }
                        Text("\(text.count)/\(AccountEmail.nameLimit)").font(.system(size: 13)).foregroundStyle(p.sub).monospacedDigit()
                    }
                    if let name = model.nameSuggestion(for: account.key, current: text) {
                        Button { text = name } label: {
                            Text("Use “\(name)” (from \(model.states[account.key]?.email ?? ""))").foregroundStyle(p.ink)
                        }
                    }
                } footer: {
                    Text("Used everywhere — tabs, widgets and the Live Activity. Clear the field to go back to “\(fallback)”.")
                }
            }
            .navigationTitle("Rename account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save) }
            }
        }
        .presentationDetents([.medium])
    }

    private func save() {
        model.rename(account.key, to: text)
        dismiss()
    }
}
