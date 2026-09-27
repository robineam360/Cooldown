# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: v1.9 (Play-ready and steady), freeze and then Step 2 (written 2026-09-27)

v1.8 shipped 2026-09-27. The Fold 7 runs the published v1.8 APK, and the leftover worktree and
branches are gone. **The v1.9 plan is written** ([RUNBOOK.md](RUNBOOK.md); ROADMAP.md's v1.9 section;
CCRM-86 (Play Readiness) to CCRM-91 (CI Gate), CCBG-46 (Keystore Wedge) to CCBG-49). Robin answered
Q1–Q3. **Q4, the Play account type, is open:** Robin will talk it through with Fable, and it gates
only Step 9. Astra at xhigh drew blocking twice, and all nine findings are adopted in the text. The
freeze is Robin's call (see RUNBOOK Step 1's Log). The Step 8 box still waiting on Robin ("Check for
updates") is moot now that the phone runs v1.8.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown` once the plan is FROZEN:

> Read CLAUDE.md; RUNBOOK.md Conventions and Step 2, and run Step 2's Resume block.

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
