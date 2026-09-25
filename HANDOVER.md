# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: the collapsed-notification synthetic dot, then CCRM-78 (Widgets Reborn) Step 7 (written 2026-09-25, night)

**Step 6 is done.** CCRM-24 (Share Card) is Built: "Share snapshot" in the Main ⋮, a 1440 px card
of the open account (ring, both bars, the full 120 dp trend chart — Robin chose it over rev D's
sparkline), a preview, then the share sheet through a FileProvider in `cacheDir/share/`. Renders:
`design/2026-09-25-share-card-renders.html`. Tests green.

**The collapsed-notification call is settled** (Fable, checked by Astra — no need to ask Robin):
with synthetic data on, the full-width band stays on the *expanded* notification only; the
*collapsed* row gets rev D's short-surface marker, a 7 dp `#B388FF` dot in flow at the end of the
title row of `notif_big_number.xml` (after `title`), gone unless `SyntheticSeries.isOn`, and the
collapsed notification's accessible text says "Synthetic data" (Astra's addition). Check
`notif_duet.xml` clips the same way before touching it. It revises an approved drawing, so a
tile goes first: Huge-number collapsed with the dot beside the expanded view unchanged, checked
by Fable then Astra, then build.

Still open, none of it blocking: CCBG-34 (Account Display Name), CCRM-85 (Crash Capture) and
CCBG-35 (Tab Clip) each need a wireframe; tell Raja the crash was the always-on notification with
one account.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md and HANDOVER.md. First, the collapsed-notification synthetic dot: add one tile to
> design/2026-09-25-widgets-reborn.html §9c (Huge-number collapsed with the 7 dp #B388FF dot at
> the end of the title row, expanded unchanged beside it), have Fable check it and Astra check
> Fable, then build it in notif_big_number.xml (and notif_duet.xml only if it clips too), with
> "Synthetic data" in the collapsed notification's content description; tests green; one commit.
> Then RUNBOOK.md Step 7 from its Resume block, sharing one snapshot from the Fold 7 as part of the
> pass.

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
