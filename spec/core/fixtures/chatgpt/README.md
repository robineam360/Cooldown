# ChatGPT fixtures

`GET https://chatgpt.com/backend-api/wham/usage` responses, for `ChatGPTUsageParser.parse` and
`PlanTag.fromChatGPT` (CCM-76 [Accounts]). See Android's ChatGPT provider notes (2026-09-20, maintainer notes)
for the reference implementation these port, and `spec/core/BEHAVIOR-SPEC.md`'s Provider column
for the rules pinned here. Same `_meta`/`captured` conventions as
[`../usage/README.md`](../../fixtures/README.md) — strip or ignore `_meta`, it isn't part of the API.

There is no shared `ChatGPTUsageParser`-vs-`UsageParser` split by `.expected.json` file the way
`usage/` has: `ChatGPTUsageParser` produces the exact same `UsageData` shape Claude's parser
does, so `tests/ccooldown-core-tests/CCM76ChatGPTParserTests.swift` asserts each fixture's
parsed value directly rather than against a paired expectation file.

- `plus-both-windows.json` — the real captured shape (2026-09-06, Plus account, ids redacted),
  verbatim from the research note. `primary_window` (18000s) → session, `secondary_window`
  (604800s) → weekly, classified by `limit_window_seconds`, never by slot name.
  `additional_rate_limits: null` → no model caps. `credits.has_credits: false` → no credits.
  `plan_type: "plus"` → `PlanTag.plan == "Plus"`.
- `weekly-only.json` — OpenAI suspended the 5-hour limit for Plus/Pro/Business on 2026-07-12
  (CCRM-54): the weekly limit lands in `primary_window` with no `secondary_window` at all.
  `session == nil`, `weekly` present.
- `phantom-untouched-session.json` — CCBG-30: the session window's `used_percent == 0` and
  `reset_after_seconds (18000) >= limit_window_seconds - 1 (17999)` → its `resetsAtMs` is nil
  despite `reset_at` being present ("not started", matching Claude's omitted `resets_at`). The
  weekly window in the same payload is a genuine in-progress reading and is untouched by the
  rule — both conditions are required, and only on the window they're true for.
- `credits-balance.json` — `has_credits: true`, `unlimited: false`, `balance: "12.40"` (a JSON
  string) → a reportable `SpendCredits` with `balanceMinor == 1240`, no used/limit, no percent.
- `credits-unlimited.json` — `has_credits: true`, `unlimited: true` → parses to a `SpendCredits`
  whose `isReportable == false`: unlimited means "not worth reporting", not "hide the object".
- `model-caps.json` — `additional_rate_limits[0].limit_name == "gpt-6-thinking"`, its own
  `rate_limit.secondary_window` (604800s) → one `ModelCap`. The entry's `primary_window` is
  `null` and contributes nothing; a cap's window is picked by length, not by slot, same as the
  top-level windows.
- `no-rate-limit.json` — no `rate_limit` key at all. Parses to a **non-nil, empty** `UsageData`
  (session/weekly nil, no caps, no credits) — tolerant parsing, not a parse failure. This is
  the one place `ChatGPTUsageParser` deliberately diverges from `UsageParser` (Claude), which
  returns `nil` for an equally empty payload.
- `malformed.json` — not valid JSON at all. `ChatGPTUsageParser.parse` returns `nil`.
