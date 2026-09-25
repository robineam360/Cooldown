# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: CRITICAL BUGS FIRST (written 2026-09-25, evening)

**Fix these before any feature work. CCRM-78 (Widgets Reborn) Step 5, below, waits until they
are closed.** Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md and BUGS.md CCBG-31 (Alert Crash), CCBG-32 (Accounts Button Wrap), CCBG-33
> (Device-Code Prerequisite) and CCBG-34 (Account Display Name) — tester feedback from Raja
> Jawahar on v1.7, logged but not analysed. Work them in that order, one commit each with the
> BUGS.md status in the same commit. CCBG-31 first: reproduce the crash when alerts are
> enabled (Fold 7 over adb and an emulator, logcat capturing the trace), find the cause, fix it
> and add a test that would have caught it. CCBG-32: restore the approved Accounts layout so the
> sign-in card's Finish sign-in / Reopen page / Cancel row fits at every width; if the fix needs
> a new arrangement, wireframe first. CCBG-33 and CCBG-34 change what the user sees: wireframe
> and wait for approval (CLAUDE.md §2); CCBG-34 needs Raja's answer on what "Display Name"
> means before anything. Also raise with Robin, before fixing CCBG-31, a discussion on crash
> reporting: the app has none today (Raja asked whether Crashlytics is on), so testers' crashes
> reach us only as messages. Lay out the options and trade-offs and let Robin decide; build
> nothing for it until he has. When all four are closed or parked on Robin, replace this block with
> the widgets prompt below.

---

## Then — CCRM-78 Step 5 (written 2026-09-25, before the bug reports)


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
