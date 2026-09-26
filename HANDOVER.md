# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: CCRM-78 (Widgets Reborn) Step 8, release v1.8 (written 2026-09-26)

Everything held for is fixed and verified on the Fold 7: CCBG-42 (Kebab Drift), CCBG-43 (Widget
Settings Hidden), CCBG-44 (Widget Fill, rev H) and CCBG-34 (Account Display Name, the email, which Robin
added to v1.8). RUNBOOK.md Step 8's Log has the details. The tree still carries the uncommitted release
prep (versionCode 24 / "1.8", RELEASING.md's six-line order with the v1.7 signer digest); it goes in at
step 1. Robin shoots the home-screen widget screenshots (full frame, lock-screen/phone JPGs from
~/Downloads); the docs also need the email on the Accounts card and v1.8's changelog.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`, with the Fold 7 on USB:

> Read CLAUDE.md and HANDOVER.md, then RELEASING.md and RUNBOOK.md Conventions and Step 8 in full, and
> BUGS.md CCBG-34 and CCBG-42 to CCBG-44. Run Step 8's Resume block: the version bump and RELEASING.md
> are already in the tree; docs and Robin's widget shots from ~/Downloads (never cropped); release notes
> — widgets are back as a new suite that fills any size, the email under each account, CCBG-24 fixed,
> Clear history, the share card; then the six steps strictly in order, a judge verdict before step 6
> (publish). Robin confirms "Check for updates" on the phone. Set shipped items to Shipped v1.8 and
> close out per Convention 4.
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
