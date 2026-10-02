# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: finish v1.9 Step 7, then Step 8 (written 2026-10-02)

**What changed today.** Robin chose to fix CCBG-52 (Boot Pin Delay) in v1.9. The post-boot restart
is now an expedited WorkManager request with no delay (3c2f4e0, `BootRequestTest`; both flavors'
tests green). CI had been red since ce2b904 (Step 5) on one lint error in the CCBG-50 (Degraded
Store Notice) card; 3c70538 fixes it. A new github release candidate was built from 3c70538 and kept
outside git:
`design/research/2026-10-01-v19-device-pass/candidate-v1.9-github-3c70538.apk`, 1.9 / 25, sha256
`89e1b6aa7e4fb475d82f9550d5a3656e5c03d35f9b8dc6689c8e28fecc4f646e`, signer = the trusted digest.
That APK replaces 0e8ac99's (73a1c1f9…846e) as "the github build" that Always after checks for.
The phone was not on the cable, so no device work was done today. The phone still runs 0e8ac99's
candidate.
Step 8's user docs are drafted ahead and the PDFs built (RUNBOOK Step 8 Log, 2026-10-02); Step 8
only fills in the release day, and drops the CCBG-52 line if the reboot check fails.

**Robin, before the next session:**
1. Have the Fold 7 on USB and unlocked, and be ready to sign Product and Work in again in the browser.
2. Before the video, swipe away your own notifications so the shade shows only Cooldown's.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md; RUNBOOK.md Conventions and Step 7 (its 2026-10-01 and 2026-10-02 Log entries and
> design/research/2026-10-01-v19-device-pass/findings.md); BUGS.md CCBG-52 (Boot Pin Delay). The
> phone is on USB. Run Before again into phone-state-before-2.txt. The github build is now
> candidate-v1.9-github-3c70538.apk (sha256 89e1b6aa…646e): adb install -r it and check its sha256.
> Then:
> (0) CCBG-52: reboot with the pin on and time the pin from boot. Seconds → mark CCBG-52 verified
> on the phone. Still minutes → record it in CCBG-52 and ask Robin whether to defer the rest.
> (a) CCBG-50 (Degraded Store Notice)'s restart cards. Debug → Simulate on. Robin signs Product in
> through the browser; then Robin signs Work in and I tap Clear on Work. Force-stop, reopen: one card
> with two items ("back on the sign-in saved before" and "signed in again"), both accounts still
> signed in. Capture it, tap OK, confirm it is gone.
> (b) Item 6, the CCRM-90 (Listing Pack) FGS video. screenrecord with --display-id per the memory
> note, after Robin has cleared his own notifications: switch on, the notification and ring appear,
> Refresh, switch off. Save to release/play/, watch it for private content, and put the path in
> fgs-declaration.md.
> Then Always after (against the 3c70538 sha256), tick Step 7 per Convention 4 (the gate: no High
> open, and every deferral in Robin's words), and run Step 8's Resume block.

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
