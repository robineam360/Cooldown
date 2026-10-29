# spec/ — the mobile family's spec, and the master of the shared core

One spec folder per repo, one repo per product family ([docs/REPO-STRUCTURE.md](../docs/REPO-STRUCTURE.md)).
This repo is the mobile family (Android and iPhone/iPad); the desktop repo (Mac, later Linux and
Windows) has its own `spec/`. Everything here is **Markdown and JSON only** — no Kotlin, no Swift,
no build system. CCRM-92 (Shared Spec) created it.

| Path | What it is |
|---|---|
| [core/](core/) | **The shared core — this is its master copy.** Identical for all five clients; the desktop repo keeps a pinned copy at its own `spec/core/`. |
| [core/API-CONTRACT.md](core/API-CONTRACT.md) | The undocumented endpoints: URLs, headers, the User-Agent rule, OAuth PKCE and device-code sign-in, token semantics, errors, backoff |
| [core/BEHAVIOR-SPEC.md](core/BEHAVIOR-SPEC.md) | The numbers every install must agree on: severity, credits, projection, pace, colour breakpoints, the even-pace line, history, reset detection, profiles, fetch outcomes |
| [core/fixtures/](core/fixtures/) | Payloads with expected parse results, projection and history cases; `captured/` holds raw payloads Android's tests read |
| [mobile/](mobile/) | **The mobile layer** — product choices Android and iOS make the same way: polling cadence, reset pings, conditions, surfaces |
| [tools/](tools/) | `check_no_code.sh`, run by CI |

## What goes in the core — the admission rule

> **Core** is a server fact, or a number or rule — backed by a fixture — that two installs on
> different platforms must agree on. **Whether a feature exists, and its surfaces, cadence and
> copy, belong to the family layer.**

Split by the **cause of change**, not by family. The core changes when a provider changes
something (a payload, the User-Agent gate, an error code) or when the shared maths changes — and
then every client must change, so it must turn every client red at once. A feature changes
because one family decided to, so it lives in that family's layer. When in doubt, it is family:
promoting a rule to the core later is cheap, untangling a feature from it is not.

## How it stays true

- **The spec leads, clients follow.** Change the docs and a fixture here first, then each client.
- **A shared bug starts in `core/`.** Add the failing fixture here first; every client goes red
  until fixed. A desktop session adds it through a worker rooted in this repo, then the desktop
  repo pulls the new pin.
- **Never edit a captured payload** to make a test pass; add a new one.
- **No code here** (`tools/check_no_code.sh`), and **nothing private**: this repo is public, so
  identifying fields in any captured payload are replaced before commit.

## How a client uses it

1. Read `core/API-CONTRACT.md` end to end before writing networking code.
2. Make the fixtures pass: walk `core/fixtures/usage/*.json` against the matching
   `.expected.json`, and run the projection and history cases.
3. Implement the shared numbers from `core/BEHAVIOR-SPEC.md` and the mobile product rules from
   `mobile/BEHAVIOR-MOBILE.md`, not from another client's source. If a rule is ambiguous, fix it here.

Android is the reference implementation. Today its tests read `core/fixtures/captured/`; running
its parser over every core fixture is CCRM-37 (Contract Tests). The iPhone/iPad app runs the core
fixtures from the start (CCRM-94 (iOS App)).

## Provenance

Seeded 2026-10-23 from the desktop repo's `contract/` at `2bfbdfd`, then cut to the core by the
admission rule: desktop-only sections (§9.3–§9.5, §11 Ledger), the desktop's polling cadence (API
§6), the alert features (BEHAVIOR §8, except reset detection) and its fixture, gauge geometry and
desktop surfaces all stay in the desktop repo; User-Agent examples made platform-neutral.
`core/fixtures/captured/` came from Android's `app/src/test/resources/`. `mobile/` was written
from Android's code.
