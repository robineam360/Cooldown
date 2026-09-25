# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: CCRM-78 (Widgets Reborn) Step 5 (written 2026-09-25, late evening)

The tester bugs are done: CCBG-31 (Alert Crash), CCBG-32 (Accounts Button Wrap) and CCBG-33
(Device-Code Prerequisite) are Fixed and emulator-verified, and CCBG-34 (Account Display Name) is
parked on Raja's answer. Before the widgets work, do these two first:
**(1)** done: all three fixes were seen on the Fold 7 the same evening. The phone runs an
unreleased release-signed build of `main`;
**(2)** tell Raja that the crash was the always-on notification with one account, and ask him
again what "Display Name" means.
CCRM-85 (Crash Capture) is filed. Its option is decided: on-device only, as Fable recommended and
Astra accepted with concerns. Its next-launch card still needs a wireframe before it is built.
CCBG-35 (Tab Clip) is new and Low. Then paste the Step 5 prompt below.

---

## CCRM-78 Step 5 prompt (written 2026-09-25, before the bug reports)


Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md; RUNBOOK.md Conventions and Step 5; BUGS.md CCBG-24 (Duet Label Clamp);
> ROADMAP.md CCRM-14 (Clear History), CCRM-15 (Above-Pace Verification), CCRM-84 (Faces Gallery)
> and rule R8; the approved wireframe's ride-along sections. Build, one commit each: CCBG-24 —
> measure the 30 sp bold figure with Paint.measureText in the device font and clamp the label to
> the remainder, Duet.labelClampDp pure and tested; CCRM-14 — "Clear usage history…" in the
> Accounts card ⋮ with the confirm dialog naming both stores, HistoryStore.clear +
> SessionLog.clear for that profile, then Surfaces.refresh; CCRM-15 — data/SyntheticSeries per R8,
> applied where UsageRepository hands out a snapshot, the Debug chip row, the tap-to-off banner,
> the notification strip, the widget marker, and a unit test that no store changes while it is on;
> CCRM-84 — the gallery per its entry. Tests green.
>
> Also, from Step 4: `WidgetHost.state` passes `synthetic = false` — wire it to
> `SyntheticSeries` so the widgets' R8 marker (already rendered by `WidgetFace`) turns on; and
> `WidgetFitTest` must stay green for the gallery's faces too.

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
