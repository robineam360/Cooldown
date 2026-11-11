# v20-build-arc — Astra findings

review: calls=2 (1 full + 1 delta) effort=high size=8.4 KB full, 6.5 KB delta · verdict `blocking`, coverage full ·
input `reviews/v20-build-arc-astra-input.md`, verdict `reviews/v20-build-arc-astra-call1.txt`.
Plan: `RUNBOOK.md` (the v2.0 feedback build arc). The one delta call was spent on the 2026-11-11 amendment (steps A6 and I4 added, A4 widened, Step 2 regated): `reviews/v20-build-arc-astra-call2.txt` — verdict `accept`, coverage "changed text in full", no new finding; C1-1 ruled fixed (the gate checker re-run on the amended RUNBOOK: RESULT PASS). No further call without an exception granted by the owner.

| ID | Grade | Conf. | Where | What | Fix | Closed by | Status |
|---|---|---|---|---|---|---|---|
| C1-1 | blocking | 97 | Shape; Steps 5–7; Reserved | Steps start on the owner's open questions with no gate: parallel lanes, the share-card scope, asset choices, the release contents and Beta App Review timing | Convention 10 "Decision gates": each question names the steps it gates (Progress "Gated on"); a gated step's prompt checks the "Robin's answers" table first and, if empty, asks Robin and records his words before going on; an answer that adds a step stops the arc for the delta review; Q6 is asked in Step 1, before any lane; Step 7 requires Beta App Review approval in Step 6's Log when Q2 says wait | `reviews/v20-build-arc-gatecheck.py` over RUNBOOK.md: every Q in its gated steps' Progress rows, an answer row per Q, every gated prompt checks the answers — RESULT PASS (2026-11-11) | fixed |

**Call 2 (delta, 2026-11-11):** `accept`; "1. prior C1-1 [blocking] — fixed at Tests and gates added — the decision-gate checker was re-run on the amended runbook and passed; no change required." No row added; C1-1 stays `fixed`.
