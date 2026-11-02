# Antigravity (Gemini) fixtures — CCRM-55 (Antigravity Account)

`retrieveUserQuotaSummary` and `loadCodeAssist` replies (API-CONTRACT §10). **Reconstructed, not
captured**: values, bucket ids and field names come from the 2026-11-02 capture record
(`design/research/2026-11-02-antigravity-respike.md`) and the 2026-09-08 captured shape; a live
payload carries the Cloud project id and the account's email, so none is stored. Every case is in
`cases.json`, with `nowMs` for the not-started rule.

- `pro-light-use.json` — the record's Pro account after two prompts: Gemini 5h 0.2381%, weekly
  0.1512%, Argon 1.0601%, each anchored to first use; the Claude + GPT pool untouched (sliding) → its
  pool is parsed but not shown.
- `not-started.json` — every bucket at fraction 1 with `resetTime` = now + its window → every
  `resetsAt` null ("Not started yet"); the pool hidden.
- `pool-used.json` — the pool's weekly at 6%: the pool is shown; its untouched 5h is not started.
- `weekly-spent.json` — Gemini weekly at fraction 0 while the 5h is fresh: the Pro confusion.
- `ua-rejected.json`, `no-groups.json` — no Gemini group → `invalidResponse`, never 0% or 100%.
- `malformed.json` — not JSON → unparseable.
- `load-code-assist-{pro,free,ultra}.json` — the plan from `paidTier.id`; Ultra's id is unverified.
