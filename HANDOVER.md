# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: pick the arc after v1.8 (written 2026-09-27)

**v1.8 shipped 2026-09-27** (`releases/latest` = v1.8, release commit `40a3bb5`). CCRM-78 (Widgets
Reborn) is closed: RUNBOOK.md Step 8 is ticked, and every arc item and v1.8 bug fix reads Shipped v1.8.
Open, all Low: CCBG-45 (Single Panel Wording), which Robin shipped on purpose ("Ship with it open"),
CCBG-35 (Tab Clip), CCBG-15 (Amber Ladder Blindness) and CCBG-3 (Credits Visibility). The widget
questions for Robin are still in the section below. **Robin, before the next session:** tap "Check for
updates" on the Fold 7 and confirm it offers v1.8. That is Step 8's last Done-when box, so tick it in
RUNBOOK.md once confirmed. There is also a leftover worktree
(`.claude/worktrees/widgets-reborn-impl-d382a0`) and two `claude/widgets-reborn-impl-*` branches to clean up.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md and HANDOVER.md, then ROADMAP.md's "Next — small, high value, ready to build" section
> and BUGS.md's Open section. v1.8 has shipped. Propose the next arc: the candidates, what each
> costs, and your recommendation, including whether CCBG-45 (Single Panel Wording) and CCBG-35 (Tab
> Clip) go into a v1.8.1 or wait for it. Ask me one question at a time. Once I decide, write RUNBOOK.md
> for it (the v1.8 runbook stays in git history at the close-out commit).
---

### What changed on 2026-09-25 (Step 4, the short version)

**The four widgets are built** — CCRM-78 (Widgets Reborn) to CCRM-82 (Accounts Strip) read Built:
Ring, Number, Countdown and "All accounts" providers (R9 names), the config screen, the on-face
chips and account cycler, the one transition alarm, and the system receiver. All four were
placed on an API 36 emulator and every R7 trigger redrew them; that pass found and fixed
cover-cell overflows and broken chip backgrounds, now guarded by `WidgetFitTest`. The Countdown
ships a live H:MM:SS count (H:MM is not reachable through RemoteViews). Calls made: the widget
bars take rev D's 3 dp tick and the Number figure keeps its severity colour (Robin); "No reading
yet", R7's fourth kind (the stamp's weekday) and the Strip 4×1 "Free" placement (Fable).

### Open for Robin or the Step 7 device pass

- A system light/dark switch has no R7 trigger: widgets keep the old theme until the next redraw.
- A widget the launcher placed without config, reconfigured later, shows "Add widget" rather
  than "Save changes".
- A long unassigned label ("Personal (unassigned)") pushes the Ring 2×2's "as of" stamp off
  its row.
- One UI's handling of `configuration_optional` and `previewLayout` (the Pixel launcher skipped
  config on add and showed the previews).
