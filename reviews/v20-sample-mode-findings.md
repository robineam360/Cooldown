# v20-sample-mode — Astra findings

review: calls=1 (1 full + ≤ 1 delta) effort=xhigh size=5.9 KB · verdict `concerns`, coverage full ·
input `reviews/v20-sample-mode-astra-input.md`, verdict `reviews/v20-sample-mode-astra-call1.txt`.
Wireframe: `design/2026-11-10-v20-feedback-wireframes.html` §9 (item 9, Android sample mode).

| ID | Grade | Conf. | Where | What | Fix | Closed by | Status |
|---|---|---|---|---|---|---|---|
| C1-1 | concerns | 96 | Notification and widgets | Real polling and in-flight requests undefined during the sample | the guarantee is scoped to the sample: real polling, reset checks and alerts carry on and write only real stores; an in-flight request completes into the real store; the sample never fetches | build: byte-identity unit test (enter/exit) | fixed (wireframe §9 isolation rules) |
| C1-2 | concerns | 94 | Settings isolation | The settings copy could reach background readers | only app screens read the copy; notify/, widgets/, alerts/, polling read real settings | build: Appearance change with notification + widget live | fixed (§9) |
| C1-3 | concerns | 88 | Leaving / editors | A late sample edit could commit to real storage after a context switch | enter/exit dismisses dialogs and drops pending edits; a sample edit binds to the sample store at open | build: Rename open → real-account launch → real name unchanged | fixed (§9) |
| C1-4 | concerns | 95 | Indicator | Debug synthetic already on at entry | entry suspends it in memory; exit restores it; nothing written | build: unit test of the two flags | fixed (§9) |
