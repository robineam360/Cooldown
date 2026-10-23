# Fixtures

Language-agnostic test data. Every CCooldown client should have a test that walks these
files and asserts the documented expectations. This is what makes a port cheap: the parser
and the pure logic are "make these cases pass" rather than "rediscover the schema".

## Layout

```
usage/       <name>.json          — an /api/oauth/usage response (Claude)
             <name>.expected.json — what parsing it must produce
profile/     <name>.json          — an /api/oauth/profile response, or the usage endpoint's
                                     Free-plan 403 body (CCM-68 [Accounts], CCM-67 [Readout])
chatgpt/     <name>.json          — a chatgpt.com/backend-api/wham/usage response
                                     (CCM-76 [Accounts]); see chatgpt/README.md
projection/  cases.json           — burn-rate estimate cases
history/     cases.json           — bar-building and week-grouping cases
```

Each `usage/*.json` carries a `_meta` key describing its provenance. **`_meta` is not part of
the API** — strip it, or rely on the parser ignoring unknown keys (it must).

`captured: true` means the payload came off the wire from a real account. `captured: false`
means it was constructed from the schema to cover a branch that hasn't been seen live —
useful, but weaker evidence. Prefer adding captured payloads.

## Expected-value conventions

- **`resetsAtMs`** is epoch milliseconds, **truncated** (not rounded) from the source
  microseconds — `…59.956769+00:00` → `…999956`. Assert on this rather than on a formatted
  string, so time-zone and formatting differences can't produce false failures.
- **`resetsAt`** is the same instant in ISO-8601 UTC, for human readability only.
- **`severity`** is the derived value (BEHAVIOR-SPEC §1); **`serverSeverity`** is what the
  payload said, or `null`.
- **`null`** means "absent / not reported" and is distinct from `0`.
- Money strings (`moneyUsed` etc.) use US-locale symbol conventions (BEHAVIOR-SPEC §2).
- `"parsed": false` means the payload is unusable and the parser must return nothing — not an
  empty success object.

## Adding a case

When a bug is found in shared logic, **add the failing fixture here first**, then fix each
client. Every client goes red until it is fixed, which is as close to automatic propagation
as separate repos allow.

When the live schema shifts, capture the new payload, add it with `captured: true`, and note
the date. Never edit a captured payload to make a test pass — add a new one; the old shape
may still be served to other accounts or plans.

## The CCM-71 [Foundation] canonical set — one fixture set for every client

`uncapped-credits.json`, `no-limits-array.json`, `resets-at-drift.jsonl` and
`null-keys-2026-09.json` are the four edge cases Android had already hit in its own captured
payloads but the shared fixtures had not pinned: an uncapped credits cap (CCBG-9 / CCM-44), a
payload with no `limits[]` array (CCBG-8 / CCM-45), `resets_at` drifting by sub-second amounts
poll to poll (CCBG-4), and September's named-but-null experiment keys. Each is redacted from a
real account and cross-references the earlier, client-local fixture that first caught the same
bug, where one exists — they aren't duplicates of intent, they're the copy meant to be shared.

The Free-plan 403 that `profile/403-plan-refusal.json` already pins (CCM-67 [Readout],
CCM-68 [Accounts]) belongs to this same set of "edge cases every client should test against" —
it lives under `profile/` because it's a profile-endpoint response, not `usage/`, but a new
client's test suite should read it exactly like the files above.

## ChatGPT provider fixtures (CCM-76 [Accounts])

`chatgpt/` covers the second provider's usage endpoint the same way `usage/`/`profile/` cover
Claude's — see [`chatgpt/README.md`](chatgpt/README.md) for the case list. It has no
`.expected.json` pairing: `ChatGPTUsageParser` produces the same `UsageData` shape Claude's
parser does, so its tests assert each fixture's parsed value directly.

### For other clients

Any future CCooldown client — Android, the Linux port, or one not yet written — starts here,
not by re-deriving the schema from `API-CONTRACT.md` alone. The Android app should point its
own parser tests at these files directly (that's its CCRM-37): walk `usage/*.json` against
`usage/*.expected.json` the same way `ContractTests.swift` does, replay
`resets-at-drift.jsonl` through its own same-window check, and assert its Free-plan 403 handling
against `profile/403-plan-refusal.json`. A new client is "make these fixtures pass" from day
one, not a second discovery of bugs the first client already paid for.

Desktop-only fixtures (`ledger/`, `logins/`, `pricing/`) live in the desktop repo's
`spec/desktop/fixtures/`, not here.
