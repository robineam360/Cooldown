# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: CCRM-78 (Widgets Reborn) Step 8, release v1.8 (written 2026-09-26)

**Step 7 is done** (RUNBOOK.md, ticked 2026-09-26). The rev G check on the Fold 7 over USB, release build of
c8d5fe7, verified all three gate fixes: CCBG-39 (Inner Duet Squeeze) reads "Personal" whole on the cover and
unfolded, in Used and Left, with the synthetic dot; CCBG-41 (Cover Strip Type) draws the roomy Strip 4×1 at the
cover's 332×107 frame; CCBG-40 (Picker Preview Ellipsis) reads "5h · at 9:10 PM" on both pickers (captures
60–69 in `design/research/2026-09-26-v18-device-pass/`). CCBG-36 (Widget Reapply Residue) and CCBG-24 (Duet
Label Clamp) now read Verified too. No High is open and no deferral from the pass remains, so the release gate
is clear. The phone is restored (Before/After diff clean) and has no Cooldown widget placed.

Step 8 is **irreversible once published** (an installed v1.8 cannot be downgraded, R9). Robin needs to be at
the phone to tap "Check for updates" at the end. `ccooldown-release.jks` and `keystore.properties` are
already in the repo root. Still open outside the arc, not blocking: CCBG-34 (Account Display Name),
CCBG-35 (Tab Clip) and CCRM-85 (Crash Capture), each waiting on a wireframe.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`, with the Fold 7 on USB:

> Read CLAUDE.md and HANDOVER.md, then RELEASING.md and RUNBOOK.md Conventions and Step 8 in full,
> and the Status lines Step 7 verified (CCRM-78 (Widgets Reborn) to CCRM-84 (Faces Gallery), CCRM-14
> (Clear History), CCRM-15 (Above-Pace Verification), CCRM-24 (Share Card), CCBG-24, CCBG-36 to
> CCBG-41). Run Step 8's Resume block: versionCode 24 / versionName "1.8", docs and widget shots,
> release notes, the six-line order into RELEASING.md with the trusted signer digest from the
> published v1.7 asset, then the six steps strictly in order, a judge verdict before step 6
> (publish). Robin confirms "Check for updates" on the phone. Set shipped items to Shipped v1.8
> and close out per Convention 4.
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
