# Working agreements — Claude Cooldown

Rules for anyone (human or agent) working in this repo. These override default behaviour.

## 1. Always name a tracker ID, never leave it bare

Every reference to a tracker ID carries its epic name in brackets on **first use in any
message, commit body, or document paragraph**:

- ✅ `CCRM-17 (Window Pings)`, `CCBG-4 (Alert Dedup)`
- ❌ `CCRM-17`, `see CCBG-4`

The epic name is the short title already in the tracker heading — the part between the ID and
the em-dash. In `### CCRM-20 · Wide Chart — one profile at full width`, the epic name is
**Wide Chart**.

**Why:** an ID alone is unreadable to a human who doesn't have the file open, which includes
future-you scanning `git log`. Every ID has a name; use it.

Exceptions, kept narrow: the tracker headings themselves (the name is right there), commit
message *subjects* where the convention is `feat(CCRM-17): …` and the subject line explains
itself, and repeat references later in the same paragraph.

New IDs are allocated from the highest existing number in
[ROADMAP.md](ROADMAP.md) (`CCRM-N`) or [BUGS.md](BUGS.md) (`CCBG-N`). **IDs are never reused
and never renumbered**, including for dropped or retracted items.

## 2. Show a wireframe and get approval before building

Anything that changes what the user sees — a new screen, card, notification layout, notification
style, chart element, settings section, or a visible change to an existing one — needs a
**wireframe reviewed and approved before implementation starts**. Not after. Not alongside.

- A wireframe is enough: ASCII/markdown sketch, an SVG, or a described layout with real
  numbers (dp sizes, text sizes, what's above what, what happens at each width class).
  Fidelity is not the point; agreeing on the layout before code exists is.
- Show the **states**, not just the happy path — empty, error, at 100%, above pace, and the
  narrow width where something has to drop out. Half the defects in
  [BUGS.md](BUGS.md) are states nobody looked at; CCRM-15 (Above-Pace Verification) exists
  purely because a visual state shipped unobserved.
- **Wait for an explicit approval.** Silence is not approval. If the answer is "change X",
  show the revised wireframe before building.

**Applies to:** the in-app screens, the always-on notification (both halves and the status-bar
ring), the reset pings, the launcher icon, and the share card (CCRM-24 (Share Card)). (The
widgets and the Quick Settings tile it used to name left in v1.6, CCRM-61 (Settings Diet).) **Does not apply to:** pure logic,
parsing, scheduling, tests, docs, or a bug fix that restores an already-approved design.

## 3. Where things live

- [ROADMAP.md](ROADMAP.md) — feature work, `CCRM-N`, ordered by when we intend to build it.
  Its appendix records what deliberately does *not* port from other apps; read it before
  proposing something that was already ruled out.
- [BUGS.md](BUGS.md) — defects, `CCBG-N`, with a status and a severity.
- [RELEASING.md](RELEASING.md) — the release process.
- [RUNBOOK.md](RUNBOOK.md) — the ordered, checkable execution plan for a multi-session arc
  (currently the Play Store arc, CCRM-66 (Play Store Launch); earlier arcs are in git history at the commits the file names): one step per fresh
  session, the prompt to paste, and what "done" means. Tick steps off there; statuses still
  live in the tracker files.
- `release/docs/` — the user-facing guide and brochure (HTML → headless Chrome; sources in
  `docs/src/`).

This is an **Android-only** repo. The Mac client is a separate repo by deliberate decision —
see CCRM-8 (Mac Menu-Bar) for the reasoning, which centres on the GitHub `releases/latest`
stream being repo-wide. Do not add another platform's code here, and do not fork this repo to
start another client — start clean and copy the shared contract in.

The app tracks the usage windows of **exactly three services — Claude, ChatGPT and Google's
Antigravity (Gemini)** — decided 2026-09-06, see the *Multi-provider* section of
[ROADMAP.md](ROADMAP.md) (CCRM-53 (Provider Model) onwards). Not "any AI provider": three named
ones, each with its own colour. Cursor, Copilot, OpenRouter and the rest are out of scope
permanently, not deferred. Nothing here may read a local file or a local process to get usage —
a phone has neither — so a provider only qualifies if its usage is a plain HTTPS call with a
token the phone can mint itself.

## 4. Delegation & review — the `model-mesh` skill

<!-- model-mesh contract block v2.3 (2026-09-25) -->
**Delegation & review — the `model-mesh` skill applies to every prompt here.** Load it at session start
without being asked. The seat is **Opus 5.5 at effort medium** and does the work itself; it settles the
skill's three gates first (reserved? new? state-changing with a reversal of more than one command, or
irreversible?) and spawns a sub-agent **only for a named purpose** — (a) expertise: Fable; (b) cost:
Haiku or Sonnet for simple volume; (c) independence: a fresh judge, never the author; (d) parallelism —
picking one of three rungs per role (lookup, build, judge, plan; skill §1). No purpose, no spawn.
**Cross-family (GPT) review: ON.** — when ON, an Astra review is **mandatory** before executing or
freezing any plan whose reversal (whole, or from a partly executed state) takes more than one command or
is impossible — Astra at high for the seat's plans, xhigh for Fable's; waivable only by the user,
explicitly, on the record — and the execution gate, discretionary triggers, disposition rules and secret
floor in skill §2 and §5–§6 bind; OFF means no GPT call from here and such a plan gets a fresh Opus review
instead. A repo's own switch word wins over the one in `~/.claude/CLAUDE.md`. Sub-agents follow this
contract like any session: a writing sub-agent runs the git gate first and commits and pushes before
returning, read-only agents return `no HEAD (read-only)`, reserved decisions stay the user's (skill §4).
**This block overrides any other delegation or model-choice rule in this repo (who does the work, which
model, what effort) unless that rule explicitly says it overrides model-mesh; project gates and commit
discipline bind sub-agents as they bind a session.**

Project gates that bind sub-agents exactly as they bind a session: a sub-agent that would change anything
the user sees beyond the approved wireframe (§2) stops and reports instead of improvising; code and its
ROADMAP/BUGS status update land in the same commit; two sub-agents never edit the same file at once.
