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

**Applies to:** the in-app screens, the home-screen widgets (CCRM-78 (Widgets Reborn)), the
always-on notification (both halves and the status-bar ring), the reset pings, the launcher icon,
and the share card (CCRM-24 (Share Card)). **Does not apply to:** pure logic,
parsing, scheduling, tests, docs, or a bug fix that restores an already-approved design.

## 3. Where things live

- [ROADMAP.md](ROADMAP.md) — feature work, `CCRM-N`, ordered by when we intend to build it.
  Its appendix records what deliberately does *not* port from other apps; read it before
  proposing something that was already ruled out.
- [BUGS.md](BUGS.md) — defects, `CCBG-N`, with a status and a severity.
- [RELEASING.md](RELEASING.md) — the release process.
- [RUNBOOK.md](RUNBOOK.md) — the ordered, checkable execution plan for a multi-session arc
  (currently the v1.8 widgets arc, CCRM-78 (Widgets Reborn); earlier arcs are in git history at the commits the file names): one step per fresh
  session, the prompt to paste, and what "done" means. Tick steps off there; statuses still
  live in the tracker files.
- [HANDOVER.md](HANDOVER.md) — the paste-ready prompt for the next session; each session that
  ends with a next step replaces its "Next session" block (as CooldownMac does).
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

<!-- model-mesh contract block v2.7 (2026-10-09), either seat. install.sh --user writes it into ~/.claude/CLAUDE.md,
     --codex-seat into ~/.codex/AGENTS.md; paste it once into a repo's AGENTS.md (CLAUDE.md = @AGENTS.md). Keep the
     wording; change only the ON/OFF switch word. The rule itself lives in the model-mesh skill. -->
**Delegation & review — the `model-mesh` skill applies to every prompt here, whichever CLI runs it.** Read the
skill at session start without being asked (`~/.claude/skills/model-mesh/SKILL.md`, or the repo's
`.agents/skills/model-mesh/`). **The seat is whichever CLI runs this session**, as the model-mesh state line says:
Claude Code → the **account's tier seat at effort medium** — the current Opus (alias `opus`) where Fable is in the
plan's limits (tier `fable`: Max, Team or Enterprise premium), the current Sonnet (alias `sonnet`) otherwise (tier
`standard`); the tier is the one word in `<config-dir>/model-mesh-tier`, undeclared means standard, and **standard
never calls Fable** (the model-mesh hook enforces it). Codex → **Sol at effort medium**. The seat does the work
itself, settles the skill's three gates first (reserved? new? state-changing with a reversal of more than one
command, or irreversible?) and spawns **only for a named purpose** — (a) expertise: Fable at xhigh (the Agent tool
on a Fable-tier Claude seat, else `model-mesh-review worker plan` on a listed Fable-tier account); (b) cost: Haiku
or Luna for narrow, fully specified work, Sonnet otherwise; (c) independence: a fresh Opus judge, never the author;
(d) parallelism — picking one of three rungs per role (lookup, build, judge, plan; skill §1) and **naming the model
(and the effort where it departs from the default) on every spawn**. No purpose, no spawn. Under a Claude seat a
GPT model never has tools; under a Codex seat every write in a repo that must stay governed goes to a Claude worker
rooted in that repo (skill §5d). A seat that routes work into other repos (an orchestrator) never edits them
directly: it delegates to a worker rooted in the target repo, whose own contract, hooks and git gate then bind.
**Cross-family review: ON.** — when ON, **one full review by the family other than the plan's author** — Astra
for a Claude-drafted plan, the fenced Claude plan slot for a Sol-drafted one — is **mandatory** before executing
or freezing any plan whose reversal (whole, or from a partly executed state) takes more than one command or is
impossible — at high for the seat's plans, xhigh for the expert's, on a prose plan under ~8 KB with no inline
shell; at most **one delta call**, only when a step is added or removed or a reversal changes; a `blocking`
finding is closed by a named test, dry run or registered evidence, not by a re-read; more calls only by the
user's explicit exception (skill §5b); waivable only by the user, explicitly, on the record; a review that cannot
be obtained never self-waives — the plan waits. The execution gate, discretionary triggers, disposition rules and
secret floor in skill §2 and §5–§6 bind. OFF means no other-family call; such a plan gets a fresh single-family
review instead, recorded as such. A gated step gets **one fresh Opus judge round** on a review packet (the Agent
tool, a worker, or `model-mesh-review --reviewer claude verdict --packet` under Codex), after which tests decide;
a Sol cross-check on the packet (when GPT review is on) is required only for a step that publishes, pays, deletes or grants; the gate
re-checks packet, state and step, consumes the authorization, and the seat then runs exactly the reviewed
command; a step that publishes git history names a commit whose tree equals the reviewed tree. A repo's own
switch word wins over the global one. **Seat switch:** a seat out of quota commits what is reviewed, writes its
handoff line and stops; the other seat starts with the git gate. Sub-agents and workers follow this contract like
any session: a writing one runs the git gate first and commits and pushes before returning, read-only ones return
`no HEAD (read-only)`, reserved decisions stay the user's (skill §4).
**This block overrides any other delegation or model-choice rule in this repo (who does the work, which
model, what effort) unless that rule explicitly says it overrides model-mesh; project gates and commit
discipline bind sub-agents and workers as they bind a session.**

Project gates that bind sub-agents exactly as they bind a session: a sub-agent that would change anything
the user sees beyond the approved wireframe (§2) stops and reports instead of improvising; code and its
ROADMAP/BUGS status update land in the same commit; two sub-agents never edit the same file at once.

---

## 5. Git — worktree sessions

**Worktree sessions: read [`WORKTREE.md`](WORKTREE.md) at every session start** (2026-09-25). In an app-created worktree (`git rev-parse --git-dir` ≠ `--git-common-dir`) it replaces the "work on `main`" and "never push a `claude/*` branch" rules for that session only; in the main checkout those rules stand unchanged. Either mode runs its sweeper after the pull. Main-checkout sessions work exactly as before.
