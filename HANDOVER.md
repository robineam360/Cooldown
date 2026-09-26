# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: CCRM-78 (Widgets Reborn) Step 7 close-out, over USB (written 2026-09-26)

The Step 7 re-run (USB, 2026-09-26, e5bb5c9) **verified CCBG-38 (Cover Buckets)**: every face at every
size draws its own layout on the cover, plus synthetic states, gallery, fold, reboot, clock,
timezone and deep-idle alarms (findings: `design/research/2026-09-26-v18-device-pass/findings.md`,
captures 20–52). It found three Low defects; Robin chose to fix all three before v1.8, approved
wireframe rev G (`design/2026-09-26-v18-gate-fixes.html`), and they are built but **not yet seen
on the phone**:

- **CCBG-39 (Inner Duet Squeeze):** the collapsed Duet is two lines per half — the name across
  the half, the bar and the 30 sp figure under it. Unfolded it read "P… 12%"; it should now
  read "Personal" whole on the cover and unfolded, in Used and Left.
- **CCBG-41 (Cover Strip Type):** a Strip 4×1 on the cover (333×108) draws Ø64 rings, 16 sp
  figures, 12 sp names.
- **CCBG-40 (Picker Preview Ellipsis):** the Countdown picker preview's caption reads
  "5h · at 9:10 PM", on both pickers.

When all three look right, Step 7 is done: tick it (no High open, no deferrals left) and go to
Step 8 (release v1.8). Still open, not blocking: CCBG-34 (Account Display Name), CCRM-85 (Crash
Capture) and CCBG-35 (Tab Clip) each need a wireframe.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`, with the Fold 7 on USB:

> Read CLAUDE.md and HANDOVER.md, then RUNBOOK.md Step 7's Log. Record the phone's Before
> state, install the release build of HEAD, and check on the Fold 7 the three rev G fixes —
> CCBG-39 (the collapsed Duet on the cover and unfolded, Used and Left, with synthetic on for
> the dots), CCBG-41 (a Strip 4×1 on the cover) and CCBG-40 (the Countdown preview in both
> pickers). Remove any test widget, run the Always-after block, set the three Verified, tick
> Step 7 and write Step 8's handover.

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
