# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: v1.9 Step 6, Docs and listing pack (written 2026-10-01)

**Step 5 is done.** CCBG-50 (Degraded Store Notice) was wireframed and judged. A fresh Fable judge
ruled rev A blocking, because Try again could reset the file and a failed reset had no wording, and
it accepted rev B. Robin approved rev B, with calls 1–7 all as drawn. It is built: the amber
notice, the restart card that goes by each account's last change (plus the CCBG-46 (Keystore
Wedge) reset card), the "can't remove now" dialog, and the Debug "Simulate degraded store" switch.
Also built: the About card's Privacy policy row for CCRM-89 (Privacy Policy), CCBG-35 (Tab Clip)'s
scroll-when-it-doesn't-fit tabs, CCBG-45 (Single Panel Wording)'s "5h"/"Weekly", and CCBG-48
(Reconfigure Label). Both flavors' tests are green. RUNBOOK Step 5's log lists what Step 7 must
check on the phone.

**Robin, before Step 6:** nothing is required. The Privacy policy link 404s until Step 6 puts
`docs/privacy.md` on main.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md; RUNBOOK.md Conventions and Step 6, and run Step 6's Resume block. The privacy
> policy must also cover CCBG-50 (Degraded Store Notice)'s `store_held_changes` record (see Step 5's
> log).

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
