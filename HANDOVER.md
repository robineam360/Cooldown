# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: v1.9 Step 3, channel split, scoped queries and CI (written 2026-09-30)

**Step 2 is done.** Robin approved wireframe rev B of
[design/2026-09-28-v19-play-ready.mockup.html](design/2026-09-28-v19-play-ready.mockup.html) on
2026-09-30, answering Q1–Q7 one at a time, each as drawn. The decisions are in the Status lines of
CCRM-85 (Crash Capture), CCRM-87 (Update Channel), CCRM-89 (Privacy Policy), CCBG-35 (Tab Clip) and
CCBG-48 (Reconfigure Label); CCBG-48 is a restoration of rev D. Fable was out of usage credits for
the review, so a fresh Opus judge ruled in its place and Astra (high) cross-checked; every concern
is adopted or was decided by Robin. **Q4, the Play account type, is still open** and gates only Step 9.

Nothing for Robin to do before Step 3. Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md; RUNBOOK.md Conventions and Step 3, and run Step 3's Resume block.

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
