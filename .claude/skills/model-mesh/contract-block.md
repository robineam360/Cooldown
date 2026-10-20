<!-- model-mesh contract block v2.8 (2026-10-19), either seat. install.sh --user writes it into ~/.claude/CLAUDE.md,
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
**Cross-family review: OFF.** — when ON, **one full review by the family other than the plan's author** — Astra
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
