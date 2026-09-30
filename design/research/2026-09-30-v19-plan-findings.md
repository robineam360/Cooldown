# v1.9 plan (RUNBOOK.md) — Astra findings

Calls 1–3 (2026-09-27): 13 findings, all adopted before the freeze (RUNBOOK Step 1 Log).
Call 4 (2026-09-30): an owner exception, delta, xhigh. Verdict **concerns**, verbatim in
`2026-09-30-v19-plan-astra-call4.txt`. Astra confirmed all 13 prior findings fixed.

| ID | Grade | Where | What | Why it matters | Fix | Status |
|---|---|---|---|---|---|---|
| C4-1 | concerns | CCBG-46 opener / Step 5 | Recovery mid-session swaps the in-memory store for the file, and nothing covers writes made in memory | A degraded sign-in could vanish at the 5-minute retry or the next open, earlier than CCBG-50's restart boundary | A process whose in-memory store holds a write stays on it, with no merge and no overwrite of the file; tests; CCBG-50 wireframe states and a Debug simulate entry; Step 7 (4b) | fixed |
| C4-2 | concerns | CrashStore.readTrace / CrashReport | Truncation before scrubbing could leave a secret fragment the scrubber no longer matches | A partial email or token in a shared report | `dropPartialLine` on a trace cut at the read cap, before any scrub (the report cap already runs after the scrub); CrashReportTest | fixed |
| C4-3 | concerns | CrashStore.share / Step 4 | "Completed share" was not an observable event | Wrong dismissal, or a crash on a failed launch | Dismissal is the chooser's pick callback only (wireframe rev B); `share()` never throws; tests for back-out and failed launch; NEW_TASK for non-Activity contexts | fixed |
