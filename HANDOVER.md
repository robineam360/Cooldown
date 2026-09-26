# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: CCRM-78 (Widgets Reborn) Step 7 again, over USB (written 2026-09-26)

The first Step 7 pass (wireless, 2026-09-26) found two defects, both now fixed and unit-tested
but **not yet seen on the phone**:

- **CCBG-38 (Cover Buckets), High:** One UI's cover frames (2×2 = 155.8×237 dp) were narrower
  than every size-map key, so each face drew its smallest layout. Fixed to wireframe rev F.1
  (`design/2026-09-26-widgets-cover-refit.html`, approved by Fable on Robin's delegation): the
  size map is keyed by the reported frames and every face is drawn at its own frame. Two visible
  departures from rev D, both approved: the stacked Number 2×1 (label over the figure) and the
  larger cover Ring 2×2 (Ø131 / 31 sp).
- **CCBG-37 (Duet Dot Squeeze):** fixed and already verified on the Fold 7 cover.

Open for Robin (Fable's note in rev F.1 §7): One UI draws widgets at 71% on the cover, so the
Strip 4×1's 9.5 / 8 sp reads as ~6.8 / 5.7 sp — does its type move up on frames ≥108 dp tall?

Measured frames, the first pass's captures and findings: `design/research/2026-09-26-v18-device-pass/`.
Wireless adb cannot run the airplane-mode, wifi/data or reboot items — bring the USB cable.

Still open, none of it blocking: CCBG-34 (Account Display Name), CCRM-85 (Crash Capture) and
CCBG-35 (Tab Clip) each need a wireframe.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`, with the Fold 7 on USB:

> Read CLAUDE.md and HANDOVER.md, then RUNBOOK.md Step 7 from its Resume block. Run the whole
> pass again from the Before block with the rev F.1 build: place every face at every size on the
> cover and inner screens and confirm each draws its own layout (CCBG-38), the picker previews,
> the Duet collapsed dot with synthetic on (CCBG-37), and the USB-only items.

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
