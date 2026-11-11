# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ v2.0 feedback build — Step 1 landed (2026-11-11); Lanes A and I may start

RUNBOOK.md Step 1 is done: the new fixtures (`spec/core/fixtures/pace-distance/printed-v20.json`,
`spec/mobile/fixtures/map-foot/cases.json`, `spec/mobile/fixtures/tags/cases.json`, from
`spec/tools/gen_v20_fixtures.py`), the ROADMAP "iOS:" lines and the "v2.0 feedback on Android / iOS" sections in
`spec/mobile/surfaces/`. Lane A (Android) and Lane I (iOS) run at the same time (Q6: "Both at once"), each in its
own fresh session (`~/Projects/Cooldown`, **worktree ticked**, model **Opus 5.5**),
pasting its block below. Each lane replaces only its own block (RUNBOOK "Who owns each shared file").

## ▶ Next — Lane A (Android): Step A4 · Summary (written 2026-11-11, after A3 landed)

Step A3 (Pace map: CCRM-109 (Quiet Accounts), CCRM-111 (Map Layers), closing the Android side of CCBG-79 (Leader Reads as
Trail)) landed on `main`; RUNBOOK's Lane A Log has the commits, the judge round and the captures
(`design/research/2026-11-v20-build/A3/`). Q5 is answered ("After v2.0": A4 is CCRM-110 (Account Tags) and CCRM-112
(Summary Snapshot) only; the Android half of CCRM-24 (Share Card) is not in v2.0). What A3 leaves A4: `SummaryScreen`
now builds `mapEntries` from `rows.filter { it.plotted }` and a `PaceMapRules.foot` over **every** row — the Summary
filter (CCRM-110) must filter `rows` before both, so the list, the map, its foot-line count and its empty message follow
the filter; the snapshot (CCRM-112) draws `PaceMap(entries, layers, mode, empty, pinned = null, onTap = {})` with the
on-screen `MapLayers` and never the chips or a callout (the `mapLabels` dense 200 × 120 case is the snapshot map).
**Emulator without a sign-in:** a scratch debug-only `SeedActivity` (not committed; `app/src/debug/`, plus a manifest
entry) seeded the registry, `UsageCache.saveSuccess` snapshots, `HistoryStore.record` history and fake credentials
(`CredentialStore(this, true).save(profile, Credentials(...), stampAdded = true)`, airplane mode on) and then started
`MainActivity`; scenarios via intent extras, `pm clear` + `pm grant POST_NOTIFICATIONS` between runs, `uiautomator dump`
to find a node. Much faster than the jdb route; rebuild it from this description, never commit it.

> Read CLAUDE.md, WORKTREE.md, RUNBOOK.md (Conventions, "Who owns each shared file", Lane A and Step A4),
> the HANDOVER.md block "Next — Lane A", the wireframe sections Step A4 names in
> design/2026-11-10-v20-feedback-wireframes.html, and the ROADMAP entries it builds; load the model-mesh
> skill; run the mode check and the sweeper. Check the step's "Gated on" questions are answered in "Robin's
> answers" (Convention 10; ask and record first if not). Build Step A4 on Android only (repo root, never ios/). Write
> only the files Lane A owns: in ROADMAP.md only each item's Status line, in spec/mobile/surfaces/ only inside
> "v2.0 feedback on Android". Tests read spec/core/fixtures/pace-distance/printed-v20.json and the new mobile
> fixtures (spec/mobile/fixtures/tags for CCRM-110 (Account Tags)) for the changed rules. Check every state the
> wireframe draws for these items on the fold7inner emulator (port 5556, headless; never emulator-5554) at the
> cover-like, inner and 360 dp widths, dark and light; save the captures in design/research/2026-11-v20-build/A4/.
> Stop and report at anything the wireframe does not draw. One fresh Opus judge round, then close out per Convention 9
> and land per WORKTREE.md.

## ▶ Next — Lane I (iOS): Lane I done (written 2026-11-11, after I3 landed)

Lane I done: Steps I1, I2 and I3 are on `main` (I3: CCRM-110 (Account Tags), CCRM-112 (Summary Snapshot) and the iOS
follow-up to CCRM-113 (Sample Port) built on iOS; code a43afb4, close-out in the commit after it). RUNBOOK's Lane I Log has
the judge round, the fit calls, the captures (`design/research/2026-11-v20-build/I3/`) and the "For Step 2" notes (§8 calls the
iOS item "Share card", the "Product" widget-gallery placeholder in `FaceViews.swift`, demo launches reading the real
`summaryFilter` key). Open for Robin (not Lane I's): whether a remembered filter that fell back to All is cleared (judge F4),
and whether the tag chip may appear beyond Settings (fit g). Lane I has no further steps; Step 2 (fold-in) waits for Lane A's
A5.

## ▶ Next — Lane D (release text): Lane D done (written 2026-11-11)

Lane D done: Step D1 landed on `main` at f156f42 (text c4ebeb3, graphic page 4ee764f). Robin approved **variant C**
of the feature graphic (`design/2026-11-11-play-feature-graphic-v20.html`). Lane D has no further steps; the
graphic is exported in Step 5. Its notes for Step 2 are in RUNBOOK.md's Step D1 Log.

## ▶ (superseded by the block above) v2.0 is on main (2026-11-06) — the arc continues on main

PR #21's branch `worktree-v20-arc-and-antigravity` was merged with v1.9's `main` and landed on `main` (Robin: "Yes, land it now"). Gate G0 is lifted; the next session works on `main` in a fresh worktree, not on the old branch. CCBG-75 (Antigravity Crash on Refresh) is closed by dropping `hasSource`; CCBG-76 (Probe Crash) and CCBG-77 (Gemini One-shot Retry) closed with it. versionCode and versionName are **not** bumped: that is RUNBOOK-v2.0 Step 8. The iOS parity PR #23 was based on the old branch and is retargeted by its own session.

**Still open on `main`** (the list in the block below still applies, minus "app code stays off main"): the Fold 7 and iPad Step 7 leftovers, the Android half of the Weekly-first share card (needs its own wireframe under CCRM-24 (Share Card)), Robin removing the old Ring widget (CCBG-70 (Stale Ring Widget)), then Step 8 with Robin's go in chat. Replace this block when the next step is known.

## ▶ (older, written before the landing) v2.0 arc + Gemini — finish Step 7, then Step 8 (written 2026-11-04)

**Where it stands.** Branch `worktree-v20-arc-and-antigravity` (worktree `.claude/worktrees/v20-arc-and-antigravity`),
PR robineam360/Cooldown#21, at bbc19db. **App code stays off main** until v1.9 ships (Gate G0); Robin lifted G0 *for
the device pass only*. main = 1f7fa0b (spec-only landings: §6.3 `markTime` 0951dae, §11 plan names 1f7fa0b; the
desktop pins 1f7fa0b). RUNBOOK-v2.0 Steps 1–6d ticked; **Step 7 is mostly done** (log: RUNBOOK-v2.0 "Step 7" entries;
captures `design/research/2026-11-02-v20-device-pass/`).

**Built and checked on devices since the 6d freeze** (Fold 7 release build, iPad Pro M4 Debug build): the shape
grammar (rings = 5h, bars = 7-day, no "5h" label), tap readouts, end mark, status-bar icon choice; CCBG-56–68, 71
fixed and verified; CCBG-69 closed (not reproduced); Robin's later calls — iPad shows one account at a time with no
pinned account, native iOS/iPadOS `TabView` (Liquid Glass sidebar/tab bar), Appearance › Summary order (Your order
default · Most room first · Resets soonest · Most used), the title shows Claude/ChatGPT/Gemini marks (or the app mark
where they don't fit), plan names per spec/core §11 (Claude Free/Pro/Max/Ultra; ChatGPT Free/Go/Plus/Pro 100/200/500;
Gemini Pro/Ultra 5x/Ultra 20x), no pace pip on Summary rows. **Gemini (CCRM-55) works** on both devices — Robin signed
in; the client secret is in `~/.gradle/gradle.properties` and `ios/Config/Antigravity.secrets.xcconfig` (both outside
git, copied from `~/.cooldown-private/gemini-client-secret` without printing it; proven by the Mac sign-in).

**Open, in order** (Robin, 2026-11-04: the device checks run before v1.9 is cut and v2.0 is prepared for release;
CCBG-74 is closed — his ChatGPT account was briefly Free, and he confirmed prolite = Pro 100):
1. Connect the Fold 7 and the iPad (ask Robin).
2. **iPad recheck of bbc19db**: portrait Liquid Glass tab bar, the title marks, the Summary order setting, CCBG-72
   (Live Activity follows a Usage display change), plan names on the iPad.
3. **Step 7 leftovers** (RUNBOOK log "Unverified"): a running/binding/full 5h and Headline 5-hour/Auto on it, a spent
   week, Gemini's pool shown once used and a refresh after an hour, the 4×2 picker previews. CCBG-70: Robin removes
   the old "Ring" widget on the iPad.
4. **Step 8 (release v2.0)** per RUNBOOK-v2.0 and RELEASING.md — needs G0 (v1.9 shipped first), a fresh judge plus
   Sol cross-check, and **Robin's go in chat** for the named commit and the APK/IPA sha256s.

**How the pass was run** (for the next device session): Fold over wireless adb (`adb mdns services`; the Fire TV at
192.168.0.14 is *not* the phone); USB adb answered "not responding". Keep the screen on (save and restore
`screen_off_timeout`, normally 300000). Release builds need `keystore.properties` + `ccooldown-release.jks` copied
from `~/Projects/Cooldown` (git-ignored; delete after). iPad: build from a clean copy (the nested worktree
`.claude/worktrees/v20-spec-land`, detached at the commit; `xcodegen` then `xcodebuild … DEVELOPMENT_TEAM=546C62BX57
-allowProvisioningUpdates`), install with `xcrun devicectl device install app`, screenshots with `devicectl device
capture screenshot`; the session can't tap the iPad, so Robin drives it while a 4-second capture loop runs. Home
pages, shade and Settings captures show private data: keep them out of the repo, commit only Cooldown crops.
Sub-agents run on claude-product (`model-mesh-review worker … --account ~/.claude-product`); workers can't run gradle
or xcodebuild, so the seat runs tests and commits. Robin's design decisions are recorded in the RUNBOOK-v2.0 log and
the CCBG entries.

New session in this worktree, model Opus 5.5:

> Read CLAUDE.md, WORKTREE.md, RUNBOOK-v2.0.md (Progress, Step 7 and its log) and this block of HANDOVER.md; load
> the model-mesh skill; run the git gate. Work the "Open, in order" list above: ask Robin to connect the devices,
> recheck bbc19db on the iPad, then the Step 7 leftovers; file every
> defect in BUGS.md, Robin decides what blocks, then tick Step 7. Stop and ask at anything visible beyond the frozen
> designs and at Step 8's release go. Before stopping, replace this block.

## ▶ (superseded by the block above) v2.0 arc + Gemini — Step 7, the device passes (written 2026-11-02)

Superseded 2026-11-04: Step 7 ran on the Fold 7 and the iPad (see the block above and the RUNBOOK-v2.0 log).

## ▶ (superseded by the block above) v2.0 arc — Android, iPhone and iPad (written 2026-11-02)

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
`UsageRepository.kt` (CCBG-75's guard) once `AntigravitySource` lands**, and CCBG-76 (Probe Crash) / CCBG-77 (Gemini One-shot Retry) (both Low, filed) go with it.

**Done on the phone (2026-11-05, after publishing):** screen timeout restored to 300000; the published asset installed with
`adb install -r` (installed sha256 = `6ff15bdc…6037`, 1.9 / 25); the crash card unchanged ("7 reports, newest 10:18 AM"), crash log buffer empty.
Robin tapped Check for updates and it reads up to date: **Step 8 is closed.**

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
