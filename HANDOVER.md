# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: v1.9 Step 5, Visible fixes (written 2026-09-30)

**Step 4 is done.** CCRM-85 (Crash Capture) is built. It uses no dependency and no network: an
Application class installs a chained uncaught-exception handler; a pure scrubbed report keeps
classes and frames and never an exception message; exit reasons add ANRs and native crashes past an
install-time watermark; the next-launch card sits above the tabs; Crash reports and Crash now sit
behind the 7-tap unlock. CCBG-46 (Keystore Wedge) is fixed: only an AEADBadTag or
KeyPermanentlyInvalidated error resets, only when the app is opened, and never from boot or the
background; every other failure runs in memory. A fresh Opus judge said `blocking` (share size),
then `accept` after the fixes. The gate is green on both flavors.

**Robin, before Step 5:** nothing is required. CCBG-50 (Degraded Store Notice), the notice you chose
to wireframe for v1.9, was cleared by the exception Astra call you granted (concerns, all three
adopted, none disputed). Step 5 starts with its wireframe, for your approval. Optional, as before:
comment `@dependabot rebase` on the two red Dependabot PRs.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md; RUNBOOK.md Conventions and Step 5, and run Step 5's Resume block, starting with
> the CCBG-50 (Degraded Store Notice) wireframe.

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
