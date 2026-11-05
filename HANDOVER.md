# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ v2.0 arc — Android, iPhone and iPad (written 2026-11-02)

New session in `~/Projects/Cooldown`, **worktree ticked**, model **Opus 5.5**:

> Read CLAUDE.md, WORKTREE.md, RUNBOOK-v2.0.md and this block of HANDOVER.md, and load the model-mesh skill (you are
> the seat: Opus at effort medium; spawn only for a named purpose and name the model on every spawn). Run the git
> gate and the sweeper. Execute RUNBOOK-v2.0.md in order from its first unticked step: for each step do the work,
> run its check, tick Progress, put code, its ROADMAP/BUGS status and its `spec/mobile/surfaces/` update in one
> commit, and get one fresh Opus judge round on every code step before moving on. Gate G0: until RUNBOOK.md's v1.9
> Step 8 is ticked, app-code commits stay on your branch with the PR open and never reach `main` (Step 2's spec
> commits may land). Stop and ask me at: anything visible beyond the approved wireframes (`design/2026-11-02-*.html`),
> Step 7 (I connect the Fold 7 and the iPad), and Step 8's release go. Before you stop for any reason, replace
> this block of HANDOVER.md with the prompt for the next session.

## ▶ Desktop repo, part 1 — rename CooldownMac to CooldownDesktop (written 2026-11-02)

GitHub already renamed the repo to `robineam360/CooldownDesktop` (private; the old URL redirects). Session in
`~/Projects/CooldownMac`, **worktree not ticked** (main mode), no other session open in that repo:

> Read CLAUDE.md, WORKTREE.md, notes/README.md and HANDOVER.md, load the model-mesh skill, run the git gate. Goal:
> prepare the local repo for its new name, CooldownDesktop, per Cooldown's `docs/REPO-STRUCTURE.md` step 5. Do not
> move the folder yourself — this session runs inside it.
> 1. Check what the rename touches and report it before changing anything: `sources/ccooldown-core/UpdateCheck.swift`
>    (the repo name compiled into the update check — which builds are installed on my Mac and the testers', and
>    whether the old `CooldownMac` API URL still answers after GitHub's rename), every other in-repo reference to
>    `CooldownMac` (docs, scripts, notes, CLAUDE.md, HANDOVER.md; the product name "Cooldown for Mac" and the bundle
>    ids stay), and `git worktree list` (nine `agent-*` worktrees: say which are merged and safe to prune).
> 2. After my go: set `origin` to `https://github.com/robineam360/CooldownDesktop.git`, update the references
>    (the update-check URL as its own CCM item with an [Epic] name, gated on the tests), prune the merged worktrees,
>    commit and push.
> 3. Finish by giving me the exact Terminal commands to run after this session closes: quit any session still open
>    in the folder; `mv ~/Projects/CooldownMac ~/Projects/CooldownDesktop`; `git -C ~/Projects/CooldownDesktop
>    worktree repair`; copy Claude Code's memory folder
>    `~/.claude/projects/-Users-robinrichardrajan-Projects-CooldownMac` to
>    `~/.claude/projects/-Users-robinrichardrajan-Projects-CooldownDesktop` (copy, keep the old one); and anything
>    else you find that keys on the old path (VS Code workspaces, scripts, model-mesh lists).
> Record what you did in notes/ and replace HANDOVER's next-session block with part 2 below.

## ▶ Desktop repo, part 2 — the weekly-first arc on the desktop (written 2026-11-02)

After the move, session in `~/Projects/CooldownDesktop`:

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
> surfaces, §9.3–9.5, §11 Ledger, and the ledger/logins/pricing/alerts fixtures). Diff the current `contract/`
> against the pinned core first: anything the desktop has that the core lacks is either desktop layer or a core fix
> that goes to Cooldown first, through a worker rooted there. Move the tests' fixture paths, add the pull script and
> the pin test the plan names; tests green before and after; gate it per model-mesh.
>
> **2. Decide the desktop version of the arc, one question at a time, wireframe first** (CLAUDE.md §1). Weekly
> leads the menu-bar item and the popover, with 5h as the companion; a per-account Headline (Weekly · 5-hour ·
> Auto), and the §6.2 takeover for the single-window menu-bar item; Pace words (band · allowance) on weekly-length
> windows replacing the gap verdict, the signed Pace never printed; the pace-mark caption under bars; Vs pace as a
> third Used/Left value; a Summary view of all accounts ranked by room to spare, with a Pace map; a per-account
> "Show model limits" switch; and a 5h-first path for existing users. Map each onto the desktop's own surfaces
> (menu-bar item, popover/panel, widget, Settings, Usage pane) and draw wireframes with every state — layouts may
> differ from mobile; the numbers may not. Mac first; keep each decision written so Windows and Linux can follow.
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

## ▶ v1.9 is published (2026-11-05) — what is left, then the v2.0 session

**Published.** GitHub release **v1.9** (APK only, no AAB), `releases/latest` = v1.9. Release commit
`3905c147865bee70da39429b3a6ab81873070575`, tag `v1.9`, CI green on it. **Uploaded asset: sha256
`6ff15bdc64cf8544b28d948bd6ed59a8bb9961ffcd893c005154268180736037`**, 1.9 / 25, signer = the trusted digest. It is a clean
rebuild of the tagged commit and **not** the earlier `8dad5c87…2e7e` build (a rebuild is not byte-identical); the
rebuild is what was tested on the Fold 7. The crash that blocked the first candidate was CCBG-75 (Antigravity Crash on Refresh): a Gemini
account left on a phone by a v2.0 build made every v1.9 refresh throw; fixed in e6130b8 and shipped. RUNBOOK Step 8 Log has the full record.
**Gate G0 is lifted** for the v2.0 branch (PR #21), with one obligation: **the v2.0 merge must drop `hasSource` in
`UsageRepository.kt` (CCBG-75's guard) once `AntigravitySource` lands**, and CCBG-76 / CCBG-77 (both Low, filed) go with it.

**Still open on the phone (the Fold 7 was off the cable right after publishing):**
1. Restore the screen timeout to the recorded **300000** ms (it is at 1800000 from the CCBG-75 re-test).
2. `adb install -r` the published asset (`gh release download v1.9`), check the installed sha256 = `6ff15bdc…6037`, read the crash card
   (it read "7 reports, newest 10:18 AM"; nothing newer).
3. Robin taps **Check for updates** and confirms it reads up to date.
Then tick the pending bullet in Step 8's Done-when.

**Robin, morning questions still open:** keep the FGS demo video (recorded on v1.9) or re-record on v2.0? Keep the Play feature graphic or
redo it after CCRM-101 (Icon v3)? Anything in `release/play/CHECKLIST.md` to decide now? Play is after v2.0.

The v2.0 session prompt is the "v2.0 arc" block above (Gate G0 no longer applies).

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
