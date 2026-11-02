# Runbook — the v2.0 arc: Weekly first, the Pace in words, the Summary tab

**Plan status: FROZEN 2026-11-02.** Cross-family review: Astra at high, one call, verdict blocking; all three
findings adopted (table in `design/research/2026-11-02-v20-findings.md`). RUNBOOK.md stays the v1.9
arc until v1.9 ships; this file is the next arc, and Gate G0 below keeps the two apart.

**Items:** CCRM-96 (Weekly First), CCRM-97 (Pace Number), CCRM-98 (Summary Tab), CCRM-99 (Model Limits
Toggle), CCRM-100 (Vs Pace Display), on Android and iPhone/iPad. Shipped together as **v2.0**, one GitHub
release carrying both the APK and the IPA (CCRM-95 (Dual Release)).

**Amended 2026-11-02 (Robin, in the Step 2–6 session):** CCRM-55 (Antigravity Account) joins v2.0 on Android
and iOS ("let's build the Gemini/Antigravity option as well in this build"; wireframe rev B approved), with
CCRM-102 (Spent Week Gate) from its review. Steps 6a–6c are added; Steps 7 and 8 gain their Gemini parts.
CCRM-101 (Icon v3) is not in this arc unless Robin says so. Delta review: Astra, one call (findings in
`design/research/2026-11-02-v20-findings.md`).

**Not in this arc:** CCBG-53 (History Footnote), CCBG-54 (Plan Copy Gap) and CCBG-55 (Spec Drift) may ride
along only if a step touches the same file; "Use next" as a separate card (dropped by Robin); the desktop
client, which runs its own arc from the prompt in HANDOVER.md.

## Closed decisions (Robin, 2026-11-02; every wireframe approved)

1. Weekly leads every surface; 5h is the companion. Per-account **Headline**: Weekly (default), 5-hour,
   Auto. Auto and the status-bar gauge switch to 5h while it binds (BEHAVIOR-SPEC §6.2). Headline 5-hour
   also restores the 5h card on top; installs that had an account before v2.0 see a one-time "Weekly now
   leads" notice offering "Keep 5h first". Placed widgets keep their stored window.
2. Pace (BEHAVIOR-SPEC §6.1) on weekly-length windows only, worded as band + allowance; the signed number
   is never printed. The 5h line is the §3 projection.
3. Pace-mark caption V1 under app and Summary bars, mode-aware; dashed room under pace; the notification's
   expanded line 2 carries the distance.
4. Summary: an "All" tab first with 2+ accounts, opened at launch (toggle), ranked by Pace with
   5h-binding, spent and unread accounts held back; the Pace map. No Use next card.
5. "Show model limits" per account, default on.
6. Usage display gains **Vs pace**: the figure is `floor(mark − used)` signed, falling back to usage when
   spent, unclocked or unread.
7. v1.9 ships first (Robin): see G0.

Wireframes: `design/2026-11-02-weekly-first-pace.html` (Android), `-ios-weekly-first.html` (iOS),
`-pace-line-label.html` (V1), `-summary-tab.html` (rev B), `-model-limits-toggle.html`,
`-vs-pace-display.html`. Rules: BEHAVIOR-SPEC §6.1–6.2, BEHAVIOR-MOBILE §5. As-shipped layouts:
`spec/mobile/surfaces/`.

## Conventions

- Delegation per the `model-mesh` skill (CLAUDE.md §4): the seat builds; Sonnet builders only for
  independent pieces with a written spec; **one fresh Opus judge round per code step**, then tests decide.
- Code, its ROADMAP/BUGS status and its `spec/mobile/surfaces/` update land in one commit.
- Anything visible beyond an approved wireframe stops and goes to Robin (CLAUDE.md §2); small fit calls go
  to a Fable judge.
- Each session ticks Progress, replaces HANDOVER's next-session block, and lands per WORKTREE.md.
- **The prompt for every step:** "Read CLAUDE.md, WORKTREE.md and RUNBOOK-v2.0.md, then do the first
  unticked step of RUNBOOK-v2.0.md, and stop when its check passes."
- **Gate G0:** no app-code commit of this arc reaches `main` until RUNBOOK.md's v1.9 Step 8 is ticked.
  Before that, code steps live on their session branch with an open PR; the docs/spec of Step 2 may land.

## Progress

| Step | What | Gated on | Status |
|---|---|---|---|
| 1 | Cross-family review of this plan, findings adopted, plan FROZEN | — | ☑ |
| 2 | Spec completion: mobile fixtures for the Summary ranking, Vs pace figure and caption text | 1 | ☑ |
| 3 | Android logic and settings (pure functions, prefs, migration), tests on every fixture | 1, 2 | ☑ |
| 4 | Android always-on surfaces: notification, status-bar gauge, widgets | 3 | ☑ |
| 5 | Android app: cards, caption, Settings, Summary tab and Pace map, the notice | 3 | ☑ (no Compose previews; Step 7 captures stand in, Robin confirmed 2026-11-02) |
| 6 | iOS: CooldownCore logic on the same fixtures, then every iOS surface per rev B | 2 | ☑ |
| 6a | CCRM-102 (Spent Week Gate): the 5h greyed while the week is spent, Android and iOS | 4, 5, 6 | ☑ |
| 6b | CCRM-55 on Android: spec/core facts, AntigravitySource, credentials and secret, sign-in, every surface | 2, 5 | ☑ |
| 6c | CCRM-55 on iOS: the same on CooldownCore and every iOS surface | 6, 6b's spec | ☑ |
| 6d | CCRM-103 shape grammar (rev D, frozen): rings = 5h, bars = 7-day, end mark, tap readout, status-bar icon | 5, 6, Astra call 3 (accept) | ☑ (Robin's two calls built, see the log) |
| 7 | Device passes: Fold 7 (cover, inner, dark, light) and iPad; captures; bugs filed; a real Gemini Pro account | 4, 5, 6, 6a–6d, G0 | ☐ |
| 8 | Release v2.0, APK + IPA, per RELEASING.md | 7 | ☐ |

## Steps

**Step 1 · Review and freeze.** The plan below goes to Astra at high. Every finding is dispositioned in
`design/research/2026-11-02-v20-findings.md`; a blocking one is closed by a named test, dry run or evidence.
Check: findings table complete. Reversal: none needed (docs).

**Step 2 · Spec completion.** Add `spec/mobile/fixtures/summary/cases.json` (rank orders for the wireframe's
states, the held/spent/unread order, tie to the earlier reset) and `spec/mobile/fixtures/display/cases.json`
(Vs pace figure incl. every fallback; caption text in Used, Left and Vs pace modes; the flip side of the
mark). Check: `spec/tools/check_no_code.sh` passes and each case's numbers re-derive by the formulas.
Reversal: one revert. May land on main before G0.

**Step 3 · Android logic.** Pure Kotlin beside `Projection`: pace, band, allowance, sessionBinding,
headline resolution, Summary ranking, Vs pace figure, caption text. Prefs: per-account `headline` and
`showModelLimits`, `openOnSummary`, the notice's shown flag and its "had accounts before 2.0" test. **Vs pace
storage:** Android writes `vsPace` into the existing `usageDisplay` string, which v1.9 reads as Used (its
`usageLeft()` is true only for `left`); iOS adds a separate Bool key `display.usageVsPace` and leaves
`display.usageLeft` as it is. Tests read `spec/core/fixtures/pace/` and `spec/mobile/fixtures/*` directly, plus a
**downgrade test**: preferences written by v2.0 (every new key, `vsPace` included) read through v1.9's accessors
give v1.9's defaults. Check: both flavors' unit tests green; fresh Opus judge. Reversal: revert the step's
commits; a downgrade shows Used and Weekly-by-default behaviour of v1.9.

**Step 4 · Android always-on surfaces.** Notification single and Duet (Weekly headline, companion, loud
companion, Auto takeover, expanded lines, model rows hidden, Vs pace figure and its caption), the status-bar
gauge takeover, widgets' default window and figure. Check: unit tests, the surfaces' render tests, the
Duet height budget unchanged, judge. Reversal: revert.

**Step 5 · Android app.** Card order by Headline, the Weekly and 5h lines, the V1 caption with dashed room,
Settings (account page Headline and model limits; Appearance Usage display and Open on the summary), the
All tab, Summary list and Pace map (two columns on the inner screen), the one-time notice. Check: unit
tests, Compose previews of every wireframe state, judge. Reversal: revert.

**Step 6 · iOS.** CooldownCore first (Swift tests on the same fixtures), then app, iPad sidebar, Settings
page, widgets, lock screen, Live Activity, Summary, notice, per rev B. Check: `swift test`, CI's macOS job,
the render test over every face, judge. Reversal: revert.

**Step 6a · Spent Week Gate.** Per `design/2026-11-02-antigravity-account.html` §3b and BEHAVIOR-MOBILE §5:
the 5h card greyed with its note on both platforms, the notification's 5h panel row, widget faces showing the
5h. Check: unit tests for the predicate and copy, both platforms' test suites, judge. Reversal: revert.

**Step 6b · CCRM-55 on Android.** spec/core first (API-CONTRACT §10, BEHAVIOR-SPEC §1.1, `fixtures/antigravity/`);
then `AntigravitySource` (the parser reads the fixtures), `Credentials.project`, the GET-only guard's two named
exceptions, the `antigravityClientSecret` Gradle property → `BuildConfig` (absent → the Gemini row hidden; CI
gets a repository secret, Robin's step), the code-paste sign-in, the Add-account row, the pool's card pair,
plan pill, the pool-only notice, and the disclaimer lines. Check: both flavors' tests (parser on every fixture,
UA-version test, secret-wired test), lint, judge. Reversal: revert; no stored data changes for other accounts.

**Step 6c · CCRM-55 on iOS.** The same on CooldownCore (fixtures) and the iOS surfaces (Add sheet, sign-in
sheet with `ASWebAuthenticationSession`, cards, sidebar, widgets, Live Activity, the disclaimer lines), the
secret from an uncommitted xcconfig. Check: `swift test`, the CI iOS job, judge. Reversal: revert.

**Step 6d · Shape grammar, CCRM-103 (Hybrid Notification).** Per `design/2026-11-02-shape-grammar.html` rev D,
frozen 2026-11-02: the 5h a hollow ring on the left and the 7-day windows bars on the right on every mobile
surface (app tile in place of the two cards, Summary rows, notification H4 and Duet, widgets, iOS app, widgets,
lock screen, Live Activity); no "5h" label; tabular sans figures with the small "%"; dashed room on rings
≥ 28 dp; usage end mark E1 plus Appearance › Usage end mark; the tap readout (core §6.3 `markTime`, both
platforms, words per the page's C4); the no-5h greyed ring with a cross; the status bar's 5h ring, red cross,
greyed ring, the Weekly bar for no 5h and the per-account Status-bar icon choice (Android). spec/mobile and
the surfaces folder updated in the same commits. Split into Android data + tests, Android surfaces, iOS, each
a commit. Check: unit tests (markTime fixtures, end-mark and status-icon choice, the new prefs' downgrade),
both flavors' tests, lint, `swift test`, judge per commit. Reversal: revert; the new prefs are additive.

**Step 7 · Device passes.** After G0. **Before:** record each device's starting state in the Step 7 log:
installed build and flavor, screen timeout, the Cooldown settings that will be changed (Headline per account,
Usage display, model limits, Open on the summary), placed widgets and lock-screen widgets (a screenshot of each
home page), any Live Activity. Fold 7 over USB (cover and inner, dark and light, Weekly/Auto/5-hour,
Vs pace, a no-5h account, a 5h-binding state via the synthetic series) and the iPad (sidebar, widgets, lock
screen, Live Activity). **Gemini (6b–6c):** Robin signs a Gemini Pro account in on each device himself (the
Google page and the code paste are his; the session never sees the token); check not started, used, Argon,
the pool hidden/shown, a spent week (6a's grey 5h, via the synthetic series), and a refresh after an hour. The
expanded single notification under three strips and the synthetic band (Step 4 judge F4) on the cover and
inner screens. Captures in `design/research/<date>-v20-device-pass/`; every defect filed in BUGS.md;
Robin decides what blocks. *Always after:* every recorded setting is put back, widgets match the screenshots,
the screen timeout is restored; the v2.0 candidate build stays installed on purpose (it is what ships), and the
log says so per device.

**Step 8 · Release v2.0.** Version bump, user docs, release notes, APK and IPA from one commit, per
RELEASING.md; the README and the guide gain Gemini and its ToS line (Cooldown presents as Google's Antigravity
CLI; Google can change or close it). Before the build: `antigravityClientSecret` set locally and as a CI
repository secret (Robin's step), and the release build shows the Gemini row. Fresh judge plus Sol cross-check before publishing (publishes). **Then Robin's go, in chat, for
the named commit and the APK and IPA sha256s, recorded verbatim in the Step 8 log; no publish without it.**
Irreversible once published.

## Whole-plan reversal

Steps 2–6 are commits: `git revert` of the arc's commits restores v1.9 behaviour, and no stored data is
migrated destructively (new prefs only, downgrade-tested in Step 3). Step 7 changes the Fold 7 and the iPad,
restored by its *Always after* against the recorded starting state (only the candidate build stays). Step 8 cannot be undone once published; a defect then is fixed forward in
v2.0.1.

## Reserved for Robin

Any visible change beyond the approved wireframes; what blocks after the device pass; the release go. Also:
the Gemini sign-in on each device, putting the client secret into `~/.gradle/gradle.properties`, the iOS
xcconfig and the CI repository secret, and which CCRM-101 (Icon v3) concept, if any, ships.

## Failure modes

- v1.9 slips: G0 holds the code on branches; branches rebase by merging main when it moves.
- A fixture disagrees with a formula: the spec is fixed first, then both clients (spec leads).
- An existing user loses their 5h-first view: the notice and Headline 5-hour cover it; the device pass
  checks an upgrade from v1.9 with two accounts.
- Notification height: the expanded single gains a 16 dp line; the Duet budget must stay inside ~120 dp.
- Widget fit: "−20%" must fit wherever "100%" does; the render tests check every bucket.
- iOS and Android drift: both read the same fixtures; any copy difference is recorded in the surfaces spec.

## UNVERIFIED

The Fold 7 inner-screen two-column Summary at 760 dp; the iPad 11″ two-column Summary at its real height;
WidgetKit's refresh budget showing a Vs pace figure that moves with time even without a new reading. Gemini:
the Free and Ultra replies (`g1-ultra-tier` is a guess; Ultra may have no Argon bucket); how long Google keeps
accepting the `antigravity/cli/1.4.12` token and the client; whether one prompt can move the 5h without the
weekly.

## Tests and gates

- `./gradlew testGithubDebugUnitTest testPlayDebugUnitTest` (every code step)
- `swift test --package-path ios/Packages/CooldownCore` and CI's macOS job (Step 6)
- `sh spec/tools/check_no_code.sh` (Step 2)
- Pace, allowance, sessionBinding cases: `spec/core/fixtures/pace/cases.json`
- Headline cases: `spec/mobile/fixtures/headline/cases.json`
- Summary and display cases: `spec/mobile/fixtures/summary/`, `display/` (Step 2)
- Downgrade test: v2.0-written preferences through v1.9's accessors (Step 3)
- Fresh Opus judge per code step; Sol cross-check and Robin's recorded go before the release
- Antigravity parse and plan cases: `spec/core/fixtures/antigravity/cases.json`; the UA-version test; the
  secret-wired test (Steps 6b–6c)
- Device pass checklist (Step 7); release checklist in RELEASING.md (Step 8)

## Log

- **2026-11-02 · Step 2.** `spec/mobile/fixtures/summary/cases.json` (9 rankings, 3 tab cases) and
  `display/cases.json` (10 Vs pace figures, 14 captions, 4 caption sides), written by script from the rules and
  checked against the wireframes' worked examples (C1: Personal −34 first, Product held, Side spent).
  BEHAVIOR-MOBILE §5 gains the caption's rounding; §6 is new (the Summary rule). Seat calls, recorded: the mark and
  the distance round half up; Left mode words over-pace as "20 over" (the wireframe showed only "to spare"); with
  the toggle off launch opens the last account looked at (the wireframe's P2 copy). `check_no_code.sh` passes.
- **2026-11-02 · Step 3** (d9dbab3). Pace, HeadlineRule, SummaryRank, PaceText, prefs, the notice settle; tests
  read every fixture; downgrade test. Fresh Opus judge: **concerns**, five findings, all adopted in 9440a6b
  (unclocked-row fixture, 1e-9 float-edge epsilon with fixtures, legacy-key sweep test; Left-mode line 2 and "<1h"
  went to a Fable judge, whose six copy calls are now in BEHAVIOR-MOBILE §5 — none major).
- **2026-11-02 · Step 4** (9440a6b + judge fixes). Notification single and Duet, status-bar gauge, widget window
  default and Vs pace figures. Fresh Opus judge: **concerns**, four findings. F1 adopted (" · vs pace" where Left
  says " · left", as the approved wireframe draws); F2 adopted (an unassigned widget follows the first account;
  `WidgetWindowDefaultTest`); F3 (the Strip's window) asked of Robin; F4 (height of the single expanded view with
  `sub2` under three strips and the synthetic band) moves to **Step 7: capture that worst case on the Fold 7 cover
  and inner screen**.
- **2026-11-02 · Step 6** (197f020, iOS, built by a delegated Opus builder). `swift test` 113 passed; the CI iOS
  job reproduced locally, succeeded. Fresh Opus judge: **concerns**. F1 (a weekly-only Number on Weekly shows
  "—"), F3 (a Left choice lost after a v1.9 round-trip) and F5 (the All tab removed under its own selection) are
  sent back to the builder, each closed by a test; F2 (a placed v1.9 Number must not flip) is a **Step 7**
  simulator upgrade check; F4's visible calls went to a Fable judge, which confirmed the builder's twelve fit
  calls (Bars, Strip and lock rectangular fixed Weekly-first; Overview, lock circular/inline, Live Activity and
  Island follow the Headline live; iPad portrait "All" tab; launch on the last account; reordered layouts; "Limit
  reached" at 100%; projection only when it runs out; the Island's Vs pace line; the Density note; the share
  card keeps its order — a Weekly-first share card needs its own wireframe under CCRM-24 (Share Card); the lock
  title may scale to 80%; the sidebar's Headline segment). The iOS Countdown stays on 5h in v2.0 (its intent
  has no window; a tested migration is a follow-up). The Android Strip's window is **Robin's call** (Fable:
  follow each account's Headline, flipping placed Strips; Step 4 judge F3).
- **2026-11-02 · Amendment.** CCRM-55 (Antigravity Account) and CCRM-102 (Spent Week Gate) added as Steps
  6a–6c after Robin approved the CCRM-55 wireframe rev B; spec/core facts and fixtures for CCRM-55 written first.
  CCRM-101 (Icon v3): ChatGPT (Astra) proposed two concepts, drawn for Robin in `design/2026-11-02-icon-v3.html`.
- **2026-11-02 · Step 5** (26c1075, Android app, built by a delegated Opus builder). Both flavors' tests and lint
  pass. Robin approved the Headline settings' place: a sheet from the account card's ⋮ menu, "Headline…" (Android
  has no per-account page). Robin also chose ChatGPT's "Shared pace" launcher icon (`design/icon-v3/`) for
  CCRM-101 (Icon v3), asked that every "%" sign be drawn smaller than its number, and that Vs pace drop its sign
  for green (under) / red (over) — both in the shape-grammar page for approval.
- **2026-11-02 · Step 6d** (CCRM-103 (Hybrid Notification), three pieces). **Piece 1** 5e11545: core §6.3
  `markTime`, the readout words (`spec/mobile/fixtures/display` → `markReadout`), the `usageEndMark` and per-account
  `statusIcon` prefs (downgrade-tested), `StatusIconRule` (`spec/mobile/fixtures/status-icon`). Judge: concerns,
  three — the side now uses §6's clamped elapsed (edge fixtures added), the Headline's `statusGauge` cases retired, a
  Duet's "Whichever is higher" compares each account's drawn window (seat call). **Piece 2** 0a3c502 + f873e9c: the
  shared painter (`ui/ShapeGrammar.kt`) by the seat, then the app, notification and widgets by three delegated Opus
  builders on claude-product; Vs pace unsigned and coloured (fixture `tone`). Judge: concerns, five — the Duet's bare
  ring on a large screen's shade panel (sw ≥ 600, re-posted on a configuration change), saved layouts never hide the
  5h or blank the page, no "5h" in the compact Summary row, `ShapeGrammarTest`; a Fable judge ruled the builders' 25
  fit calls (8 changed: the open tile's ring readout, the 5h chip default, the 5h-only Number, the dead Window row,
  the picker previews, the iOS held row, the iOS model-limit bars, the lock band; the rest kept).
  `tools/widget_layouts.py` regenerates the layouts byte for byte. **Piece 3** 9591e03 + ec4cce9: CooldownCore
  (`MarkTime`, readout, `UsageEndMark`, same fixtures), then the app and the widgets/lock/Live Activity by two
  delegated Opus builders. Judge: concerns, four — the Live Activity's stale date and ended peak (tested), the small
  5h glyph as the page's `gauge()`, the Overview tile's model row, the lock bore. Checks: both flavors' unit tests (836)
  and lint, `swift test` 127, the iOS app tests 53 on the iPhone 17 Pro simulator. **Open for Robin:** the layout
  sheet's row for the merged tile (Android labels it "7-day window"; iOS still lists two rows) and the iOS
  lock-screen circular for a plan with no 5h (today's Weekly ring with "W" — a 7-day ring). **Step 7 must check:**
  the Duet's bare ring on the Fold 7 inner shade and the 36 dp ring on the cover; the picker previews; the 12 × 6 dp
  Weekly-bar glyph (C6); the held compact word "Held · 3:40 PM" in its 90 dp column.
- **2026-11-02 · Step 6d, Robin's calls** (relayed by the arc session). (1) The merged tile is one layout-sheet row,
  **"Usage"**, on both platforms; (2) the iOS lock circular for a plan with no 5h is Android's N3 Weekly-bar glyph
  under the Used figure — no ring, no "W"; (3) the Vs pace Settings description ("Shows "10%" — how far from the pace
  mark you are: green under it, red over it.") is approved as is on both platforms; (4) Step 5's Compose previews:
  the Step 7 device captures stand in (confirmed). Built in 6ee08e6; one fresh Opus judge: **concerns**, four, all
  adopted in the next commit — Android's row now reads the stored layout through `tileLayout` (a legacy layout that
  hid the reported window id reads as the tile shown, so the row's switch, fold and place match the screen), the iOS
  glyph's unit fits its 40 × 28 pt frame (the tick is no longer clipped), pure tests for the row's hide/show
  (Android) and move pairing (iOS), and the `tileStartsOnFive` doc. Checks: Android 838 tests on both flavors and
  lint; iOS app tests 55. Also merged origin/main 0951dae (spec/core §6.3 `markTime` wording, the 99.6 spent case),
  which both clients already met.
- **2026-11-02 · Step 7, Fold 7 (part 1).** **G0 lifted for the device pass only, Robin's call in chat** ("Yes, run
  Step 7 now"); app code still stays off main. Wireless adb (USB answered "not responding"). **Before:** v1.9
  (versionCode 25, github release) installed; screen timeout 300 000 ms; always-on notification **off**, every reset
  ping off; Appearance: theme System, time System, Usage display Used, density Comfortable; seven Cooldown widgets on
  the cover's fifth home page (Number 4×1, Strip 4×2, Ring 2×2 ×2, Countdown 2×2, Number 2×1 ×2; home pages captured
  to the session scratchpad only — they show other apps' private widgets). **Installed** the release-signed github
  build of 5636bde over v1.9 (`install -r`; accounts kept). **Checked** on the cover and inner screens, dark and
  light: the Summary (rings, grey × for Personal, Vs pace unsigned green/red), the tile (dashed room, E1 end line,
  chips), the tap readout on a bar ("Pace catches up Wed 4 Nov, 6:37 am") and on the ring (5h chip + "1h 22m behind
  pace"), the account sheet's Status-bar icon (switches; restored), the notification collapsed and expanded on both
  screens (OVER/UNDER PACE captions), the status bar's N3 Weekly-bar glyph for Personal, the seven widgets. **Filed**
  CCBG-56 (Summary Readout Tap, Medium) and CCBG-57 to CCBG-66 (Low; CCBG-64 and CCBG-66 are Robin's call).
  **Unverified:** Headline 5-hour/Auto on a live 5h (Claude's 5h had not started), a binding or full 5h, a spent week
  (the release build has no synthetic series), the 4×2 widget picker previews. **Restored:** notification off (gone
  from the shade), Usage display Used, theme System, Work's and Claude's Headline Weekly and status icon 5h ring; the
  v2.0 candidate stays installed on purpose. **Still open:** Gemini (needs `antigravityClientSecret`, Robin's step,
  then a rebuild and his sign-in), the screen timeout (restored at disconnect), and the iPad.

