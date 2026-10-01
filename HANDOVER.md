# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: finish v1.9 Step 7, then Step 8 (written 2026-10-01)

**What changed today.** Step 6 is closed: Robin approved feature graphic B, and it is exported to
`release/play/feature-graphic.png`. On Robin's calls, the Debug Endpoint probe is now in debug builds
only, and CCBG-51 (Held Change Residue) is fixed (945122b). versionCode 25 / 1.9 went in at 0e8ac99.
Most of the Step 7 device pass is done on the Fold 7, and every item checked passed. The release
gate so far: no High is open. One new Low was filed, CCBG-52 (Boot Pin Delay): after a reboot the
always-on notification took almost 6 minutes to come back. The phone is back on the github 1.9
candidate, and the restore step's diff is clean.

**Robin, before the next session:**
1. Decide CCBG-52 (Boot Pin Delay, Low): fix it in v1.9 (an expedited boot job, then one more
   reboot on the phone), or defer it on the record.
2. Have the Fold 7 on USB, and be ready to sign Product and Work in again in the browser.
3. Before the video, swipe away your own notifications so the shade shows only Cooldown's.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md; RUNBOOK.md Conventions and Step 7 (its 2026-10-01 Log entry and
> design/research/2026-10-01-v19-device-pass/findings.md). The phone is on USB. Run Before again
> into phone-state-before-2.txt. Then the two items still open:
> (a) CCBG-50 (Degraded Store Notice)'s restart cards. Debug → Simulate on. Robin signs Product in
> through the browser; then Robin signs Work in and I tap Clear on Work. Force-stop, reopen: one card
> with two items ("back on the sign-in saved before" and "signed in again"), both accounts still
> signed in. Capture it, tap OK, confirm it is gone.
> (b) Item 6, the CCRM-90 (Listing Pack) FGS video. screenrecord with --display-id per the memory
> note, after Robin has cleared his own notifications: switch on, the notification and ring appear,
> Refresh, switch off. Save to release/play/, watch it for private content, and put the path in
> fgs-declaration.md.
> Robin's call on CCBG-52 (Boot Pin Delay): <fix | defer>. Then Always after, tick Step 7 per
> Convention 4 (the gate: no High open, and every deferral in Robin's words), and run Step 8's Resume
> block.

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
