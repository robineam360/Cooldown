<!-- model-mesh contract block v2.3 (2026-09-25). Paste into a repo's AGENTS.md or CLAUDE.md (install.sh --user
     writes it into ~/.claude/CLAUDE.md for every session). Keep the wording; change only the ON/OFF switch
     word. The rule itself lives in the model-mesh skill. -->
**Delegation & review — the `model-mesh` skill applies to every prompt here.** Load it at session start
without being asked. The seat is **Opus 5.5 at effort medium** and does the work itself; it settles the
skill's three gates first (reserved? new? state-changing with a reversal of more than one command, or
irreversible?) and spawns a sub-agent **only for a named purpose** — (a) expertise: Fable; (b) cost:
Haiku or Sonnet for simple volume; (c) independence: a fresh judge, never the author; (d) parallelism —
picking one of three rungs per role (lookup, build, judge, plan; skill §1). No purpose, no spawn.
**Cross-family (GPT) review: OFF.** — when ON, an Astra review is **mandatory** before executing or
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
