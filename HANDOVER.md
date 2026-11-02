# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ v2.0 arc — starts after v1.9 ships (written 2026-11-02)

> Read CLAUDE.md, WORKTREE.md and RUNBOOK-v2.0.md, then do the first unticked step of RUNBOOK-v2.0.md, and stop
> when its check passes. (Step 2, spec completion, may run now; its code steps reach `main` only after
> RUNBOOK.md's v1.9 Step 8 is ticked — Gate G0.)

## ▶ Desktop repo — paste into a session in `~/Projects/CooldownMac` (written 2026-11-02)

> Read CLAUDE.md, WORKTREE.md, notes/README.md and HANDOVER.md; load the model-mesh skill; run the git gate and the
> sweeper. Call Astra with `CODEX_BIN=/Applications/ChatGPT.app/Contents/Resources/codex-cli/bin/codex`.
>
> **Context.** The mobile repo (`~/Projects/Cooldown`) decided and approved a new arc on 2026-11-02 for Android
> and iOS: CCRM-96 (Weekly First), CCRM-97 (Pace Number), CCRM-98 (Summary Tab), CCRM-99 (Model Limits Toggle),
> CCRM-100 (Vs Pace Display). Why: the weekly window is the only limit every plan carries (Claude Ultra dropped the
> 5-hour limit for verified accounts on 2026-10-30; ChatGPT Pro has had none since 2026-07-12). The shared maths is
> in Cooldown's master spec: `spec/core/BEHAVIOR-SPEC.md` §6.1 (the Pace number: formula, bands, allowance) and
> §6.2 (when the 5-hour session binds), cases in `spec/core/fixtures/pace/cases.json`. The mobile product choices
> are in `spec/mobile/BEHAVIOR-MOBILE.md` §5, and the approved mobile wireframes are Cooldown's
> `design/2026-11-02-*.html` (weekly-first-pace, pace-line-label, summary-tab, model-limits-toggle,
> vs-pace-display, ios-weekly-first). Read them as the reference; the desktop's own layer decides its surfaces.
>
> **1. Bring in the shared spec** — Cooldown `docs/REPO-STRUCTURE.md` step 3 (Astra-reviewed 2026-10-23). Pin
> Cooldown commit `27929a239692c772bb615cbc96c7ef614ef948e3` (on Cooldown `main`). `contract/` becomes `spec/`:
> `spec/core/` = a verbatim copy of Cooldown's `spec/core/` at that commit (`git -C ~/Projects/Cooldown fetch`,
> then `git -C ~/Projects/Cooldown archive <sha> spec/core | tar -x`), with a `spec/core/PIN` naming it; and
> `spec/desktop/` = everything the core cut that is desktop's (alerts §8, polling cadence, gauge geometry, Mac
> surfaces, §9.3–9.5, §11 Ledger, and the ledger/logins/pricing/alerts fixtures). Diff the Mac's current
> `contract/` against the pinned core first: anything the Mac has that the core lacks is either desktop layer or a
> core fix that goes to Cooldown first, through a worker rooted there. Move the tests' fixture paths, add the pull
> script and the pin test the plan names; tests green before and after; gate it per model-mesh.
>
> **2. Decide the desktop version of the arc, one question at a time, wireframe first** (CLAUDE.md §1). Weekly
> leads the menu-bar item and the popover, with 5h as the companion; a per-account Headline (Weekly · 5-hour ·
> Auto), and the §6.2 takeover for the single-window menu-bar item; Pace words (band · allowance) on weekly-length
> windows replacing the gap verdict, the signed Pace never printed; the pace-mark caption under bars; Vs pace as a
> third Used/Left value; a Summary view of all accounts ranked by room to spare, with a Pace map; a per-account
> "Show model limits" switch; and a 5h-first path for existing users. Map each onto the Mac's own surfaces
> (menu-bar item, popover/panel, widget, Settings, Usage pane) and draw Mac wireframes with every state — layouts
> may differ from mobile; the numbers may not.
>
> **3. Then** file the CCM items with [Epic] names, write the desktop rules into `spec/desktop/` (fixtures where a
> desktop number exists), and write the desktop runbook for Astra review. Anything shared that looks wrong goes to
> Cooldown's `spec/core/` first.

## ▶ iOS track — a separate session, runs beside v1.9 (written 2026-10-24, after the iPad device check)

> CCRM-94 (iOS App) is built and on main (last 0a6b6fb): the app, widgets rev C (Bars small; the Overview as
> account cards in five tiers across Medium, Large and Extra Large; the Ring dropped), lock-screen widgets, the
> Control Center control, reset alerts, the Live Activity Duet, the iPad sidebar 5h/Weekly switch, and Android's
> Appearance, Theme colour and account extras. Weekly-only plans lead with Weekly everywhere. Designs approved
> 24 Oct: `design/2026-10-24-ios-widgets.html`, `-ios-sidebar-window.html`, `-ios-appearance-accounts.html`.
> The iPad (UDID 00008132-001C398E14D9001C) runs the latest build, signed on the Sedin team
> (`DEVELOPMENT_TEAM=546C62BX57 -allowProvisioningUpdates`, never committed); install with
> `xcrun devicectl device install app`. Debug builds print `[LiveActivity]` lines to a
> `devicectl device process launch --console` session; widget crashes are in the device's systemCrashLogs.
> Still open from the device check: (1) a reset alert with the window set to "Always" (none arrived on "If busy",
> correctly, at a 55% peak); (2) on the iPad lock screen, tapping a second placed widget didn't reopen its Account
> picker — unresolved, a screen recording would help. Then CCRM-95 (Dual Release) phase B: the IPA to colleagues
> through the EAM360 team (TestFlight or ad hoc), which waits on EAM360 agreeing to publish; one GitHub release
> carries both the APK and the IPA, never an iOS-only one. Separately, in the desktop repo: REPO-STRUCTURE step 3.

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
