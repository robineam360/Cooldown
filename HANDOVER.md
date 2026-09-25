# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: CCRM-78 (Widgets Reborn) Step 6 (written 2026-09-25, late night)

**Step 5 is done.** CCBG-24 (Duet Label Clamp) is fixed pending the device pass; CCRM-14 (Clear
History), CCRM-15 (Above-Pace Verification)'s synthetic series and CCRM-84 (Faces Gallery) are
Built; the emulator pass found and fixed CCBG-36 (Widget Reapply Residue). Tests green.

**One call for Robin before Step 7:** with synthetic data on, the *collapsed* notification is too
short for rev D's full-width "SYNTHETIC DATA" band above its row, so the 32 sp figure clips (the
expanded view matches rev D). The seat's recommendation, to wireframe: keep the band on the
expanded view only, and on the collapsed row use the widgets' short-face marker, a 7 dp violet
dot. That is the rule rev D already uses for widgets under 160 dp.

Still open, none of it blocking Step 6: CCBG-34 (Account Display Name), CCRM-85 (Crash Capture)
and CCBG-35 (Tab Clip) each need a wireframe; tell Raja the crash was the always-on notification
with one account.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md; RUNBOOK.md Conventions and Step 6; ROADMAP.md CCRM-24 (Share Card) and the
> approved share-card section. Build share/ShareCard.kt at 4× (1440 px wide) from RingRenderer,
> BarRenderer and a ChartBitmap adapted from git show 530781f:.../widget/ChartBitmap.kt; the
> FileProvider (res/xml/share_paths.xml, cache-path "share/") with FLAG_GRANT_READ_URI_PERMISSION
> on the chooser intent; every earlier file in cacheDir/share deleted before each render and the
> folder emptied on app start; the render-then-preview dialog; ACTION_SEND; "Share snapshot" in
> the Main ⋮ menu. Privacy: the profile label only — no email, no plan tier; nothing written
> outside app cache. Tests green; one commit.

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
