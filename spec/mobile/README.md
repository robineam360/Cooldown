# spec/mobile/ — the mobile layer (Android and iPhone/iPad)

What the two phone-and-tablet clients agree on beyond the shared [core](../core/). The core holds
what a provider can break; this layer holds **product choices** the mobile family makes the same
way on Android and iOS — cadence, which alerts exist, what counts as a state worth showing.
Desktop clients have their own layer in the desktop repo and are not bound by anything here.

| File | What it pins down |
|---|---|
| [BEHAVIOR-MOBILE.md](BEHAVIOR-MOBILE.md) | Polling cadence, reset pings, the conditions a client shows as a state, and the surfaces each platform has |

Where Android and iOS cannot do the same thing (iOS has no background polling and no persistent
notification), the rule says what each does, and the iOS half is settled with CCRM-94 (iOS App)'s
wireframes. Android is the reference: where this file and Android disagree, fix one of them on
purpose.
