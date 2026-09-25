# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: CCRM-78 (Widgets Reborn) Step 7, the Fold 7 device pass (written 2026-09-25, late night)

**The collapsed-notification synthetic dot is built** (wireframe rev E §9c, Fable checked, Astra
accepted on round 2): collapsed views drop the band for a violet dot (7 dp single row, 6 dp on the
Duet's First half), TalkBack reads "Synthetic data", the content text is prefixed "Synthetic data · "
for surfaces that ignore custom views, the expanded Duet draws no condition strip under synthetic.
Seen on the API 36 emulator (single row); the Duet is unit-tested only — look at it on the Fold.
Also seen there with synthetic Off: the collapsed row's third line (reset/strip) clips on the Pixel
skin — check whether One UI clips it too, and file a CCBG if so.

Step 7 needs the Fold 7 on USB with Robin there to unlock it — no device was attached this session.

Still open, none of it blocking: CCBG-34 (Account Display Name), CCRM-85 (Crash Capture) and
CCBG-35 (Tab Clip) each need a wireframe; tell Raja the crash was the always-on notification with
one account.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`, with the Fold 7 plugged in:

> Read CLAUDE.md and HANDOVER.md, then RUNBOOK.md Step 7 from its Resume block, sharing one
> snapshot from the Fold 7 as part of the pass. Include the Duet collapsed with synthetic on (rev E
> §9c dot) and whether the collapsed row's third line clips on One UI.

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
