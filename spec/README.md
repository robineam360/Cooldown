# spec/ — the shared Cooldown spec (master copy)

Everything here is **platform-neutral**: Markdown and JSON only, no Kotlin, no Swift, no build
system. It is the one source of truth for what every Cooldown client must agree on — Android and
iPhone/iPad in this repo, and the desktop clients (Mac today, Linux and Windows later) in the
desktop repo. CCRM-92 (Shared Spec) created it; the decision behind it is in
[docs/REPO-STRUCTURE.md](../docs/REPO-STRUCTURE.md).

| Path | What it pins down |
|---|---|
| [API-CONTRACT.md](API-CONTRACT.md) | The undocumented endpoints: URLs, headers, the User-Agent rule, OAuth PKCE and device-code sign-in, token semantics, errors, backoff |
| [BEHAVIOR-SPEC.md](BEHAVIOR-SPEC.md) | Product rules that must be identical everywhere: severity, credits, projection, pace, colour breakpoints, history, alerts, profiles, fetch outcomes |
| [fixtures/](fixtures/) | Captured and constructed API payloads with expected parse results, plus projection, history and pace cases — see [fixtures/README.md](fixtures/README.md) |
| [fixtures/captured/](fixtures/captured/) | Raw payloads captured on the phone, read by Android's tests |
| [tools/](tools/) | Checks on this folder (`check_no_code.sh`) |

## Two layers, one tree

- **Core — this folder.** Identical for every client. Presentation is out of scope: widgets, the
  pinned notification, the menu bar and the Live Activity are each platform's own design.
- **Desktop layer — the desktop repo's `contract/desktop/`.** What only a computer can do:
  reading local Claude Code transcripts and Codex logs (BEHAVIOR-SPEC §11 Ledger), discovered
  local logins (§9.3–§9.5), pricing, and their fixtures (`ledger/`, `logins/`, `pricing/`). The
  section numbers stay reserved here so references are stable.

The desktop repo keeps a **pinned copy** of this folder in `contract/shared/`, with a `PIN` file
naming the Cooldown commit it was copied from and a test that fails when the copy and the pin
disagree. Nothing else copies it.

## How it stays true

- **The spec leads, clients follow.** When shared behaviour changes, change this folder first —
  the docs and a fixture — then each client.
- **A bug in shared logic starts here.** Add the failing fixture in this folder first, then fix
  each client; every client goes red until it is fixed. A desktop session adds it through a
  worker rooted in this repo, then the desktop repo pulls the new pin.
- **Never edit a captured payload** to make a test pass; add a new one. The old shape may still
  be served to other accounts.
- **No code here.** `tools/check_no_code.sh` fails CI if a source file lands outside `tools/`.
- **Nothing private.** This repo is public: identifying fields in any captured payload are
  replaced before it is committed.

## How a client uses it

1. Read `API-CONTRACT.md` end to end before writing networking code; every rule in it was
   learned by a failure.
2. Implement parsing and the pure logic against `fixtures/`: walk `fixtures/usage/*.json` against
   the matching `.expected.json`, and run the projection, history and pace cases.
3. Implement product behaviour from `BEHAVIOR-SPEC.md`, not from another client's source. If the
   spec is ambiguous, fix the spec.

Android is the reference implementation. Today its tests read `fixtures/captured/`; running its
parser against every shared fixture is CCRM-37 (Contract Tests). The iPhone/iPad app's tests run
the shared fixtures from the start (CCRM-94 (iOS App)).

## Provenance

Seeded on 2026-10-23 from the desktop repo's `contract/` at commit `2bfbdfd`: `API-CONTRACT.md`
and the core fixtures verbatim; `BEHAVIOR-SPEC.md` and `fixtures/README.md` verbatim except the
desktop sections named above, replaced by a pointer. That repo's `contract/README.md` became
this file. `fixtures/captured/` came from Android's `app/src/test/resources/`.
