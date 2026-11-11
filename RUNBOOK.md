# Runbook — the v2.0 feedback build, then the v2.0 release and Play Day 0

**Plan status: FROZEN 2026-11-11.** Cross-family review: Astra at high, one full call, verdict **blocking**
with one finding (C1-1, no decision gates on Robin's open questions), fixed by Convention 10 and closed by a
named check (`reviews/v20-build-arc-gatecheck.py`, PASS). No delta call: no step added, no reversal changed.
Table: `reviews/v20-build-arc-findings.md`; Step 0's Log.

This runbook takes `main` as of 2026-11-11 (v2.0 code on main, versionCode 25 / "1.9", the nine feedback
items approved but not built) to a published **v2.0**: the GitHub release (APK, with the iOS build under the
same version), the TestFlight build, and Day 0 of the Google Play closed test.

**Items** (each on Android and iOS unless marked):
- CCRM-105 (Duet Stack) — Android only (iOS has no ongoing notification);
- CCRM-106 (7d Chip);
- CCRM-107 (Day Grid);
- CCRM-108 (Reset Named);
- CCRM-109 (Quiet Accounts);
- CCRM-110 (Account Tags);
- CCRM-111 (Map Layers), which closes CCBG-79 (Leader Reads as Trail);
- CCRM-112 (Summary Snapshot);
- CCRM-113 (Sample Port) — Android, plus the iOS follow-up (the iOS sample swaps "Product" for a Gemini account).

**Carried in from [RUNBOOK-v2.0.md](RUNBOOK-v2.0.md):** its Steps 7 (device passes) and 8 (release v2.0) are
not ticked; this file's Steps 3 and 4–7 replace them (its Progress rows say so since Step 0), and Step 3 picks
up their open checks (listed there).
CCRM-104 (Pace Line Parity) and CCBG-78 (Collapsed Pace Line Clip) are built and wait for the device check in
Step 3. Earlier arcs are in git history: v1.9 at `1f8ee7b` (this file before it was replaced).

**The design.** `design/2026-11-10-v20-feedback-wireframes.html`, **approved rev B 2026-11-10** (§1–§8 the
eight items, §9 the sample mode). Rules: BEHAVIOR-SPEC §6.3 "Planned — v2.0 feedback" (CCRM-107, 108, 109,
111), BEHAVIOR-MOBILE §7 (sample mode), ROADMAP.md CCRM-105 to CCRM-113, and the per-file deltas in
`spec/mobile/surfaces/README.md` § "Planned changes". The wireframe's §4 heading still says "since / until";
Robin chose **B, the pace line names the reset** — ROADMAP's CCRM-108 and core §6.3 are the rule.

## Closed decisions (Robin, 2026-11-04 to 2026-11-10)

1. All nine items ship in v2.0; v2.0 waits for their build and a second Fold 7 + iPad pass.
2. The Duet keeps its +/− sign. Collapsed Duet text is capped in dp at large font scales.
3. Tags are optional (None · Personal · Work); untagged accounts show only under All.
4. The pace line names the reset: "6d 23h behind · resets Wed 12:30 PM". Where it does not fit, a bare clock
   always means today: another day drops "resets", then the clock, then the weekday; later today drops
   "resets", then the clock (c1454d4).
5. An account leaves the Pace map only when its row reads "0%" (1f8ee7b).
6. The sample mode never reaches the notification, the status-bar icon, the reset pings or the widgets.
7. Play launches at v2.0 on a **personal** developer account (not yet registered). The closed test (12+
   testers opted in for 14 days in a row) starts after these items land. iOS stays on TestFlight, published by
   EAM360. Terms risk accepted ("Proceed, risk accepted").
8. Version: Android versionCode **26** / versionName **"2.0"**; iOS `MARKETING_VERSION` **2.0**, build
   (`CURRENT_PROJECT_VERSION`) **1**, both set in the release commit and never earlier.

## How the work is split, and why

**Spec first, then two code lanes side by side, then one serial tail.**

- **Step 1 (serial)** writes everything both platforms read: the new shared fixtures, and the lane-scoped
  places in the shared files (below). Both lanes branch from `main` after Step 1 lands.
- **Lane A (Android)** and **Lane I (iOS)** then run at the same time, each in its own session and worktree.
  They share no code. Inside a lane the steps run one after another.
- **Lane D (docs)** writes the release text and the feature-graphic wireframe. It touches no code and no
  tracker file, so it may run beside the lanes.
- **Steps 2–8 (serial)** fold the lanes together, run the device pass, cut the release and start Play.

**Batches, not one item per step.** Nine items on two platforms as single steps would be seventeen sessions,
each re-reading the same chart and pace-line code and each paying for a judge round and a landing. One step
per platform would be too big to judge in one round. So the items are grouped by the code they share:

| Batch | Items | Shared code (Android · iOS) |
|---|---|---|
| Lines and labels | CCRM-106, CCRM-107, CCRM-108 | the pace-line formatter (`PaceText` · `PrintedForm`), the trend chart's painter, the tile chips; the §6.3 printed-form fixtures |
| Pace map | CCRM-109, CCRM-111 (CCBG-79) | the map painter, its label placer and foot line; the `mapLabels` fixtures |
| Summary | CCRM-110, CCRM-112 | the Summary header, the account settings, the share card |
| Duet | CCRM-105 | the notification builder and its fit tables (Android only) |
| Sample | CCRM-113 | the account store, the first screen, every tab's notice slot |

The four §6.3 items are split across two batches on purpose: the day grid and the reset wording change the
*text and charts everywhere*, while quiet accounts and layers change *only the map*. The Pace map batch comes
second so it draws over the finished grid and axis. The Duet comes after Lines and labels, so its fit is
measured with the final pace line. The Sample comes last on Android, so the sample shows every new surface.

**Why only two code lanes.** A second Android session would share the emulator, the gradle daemons and the
app's UI files with the first. The Android batches touch overlapping screens, so they would have to take
turns anyway.

## Who owns each shared file

Two writers never edit the same lines. Git merges two branches cleanly when at least one unchanged line
separates their edits, and conflicts when the edits touch or abut (dry run 2026-11-11, Step 0's Log). Every
rule below rests on that.

| File | Owner during the lanes | Rule |
|---|---|---|
| `spec/core/**`, `spec/mobile/BEHAVIOR-MOBILE.md`, `spec/mobile/fixtures/**` | **Step 1** writes them; then **read-only** for every lane | A lane that finds a fixture or rule wrong stops and reports to the orchestrating session, which gives the fix to one session as a spec-only commit (spec leads). |
| `ROADMAP.md` | Lane A: each item's **Status** line only. Lane I: each item's **iOS:** line only (Step 1 adds it as the entry's last bullet, so the What text separates the two lines) | Step 2 rewrites both lines into the closing status. Nobody else edits a CCRM-105…113 entry during the lanes. |
| `BUGS.md` | **Lane A only** (the CCBG-79 Android note, any defect it files) | Lane I and Lane D never edit it; they record a defect in their step's Log, and Step 2 files it. From Step 2 on, the serial step in progress owns it. |
| `spec/mobile/surfaces/*.md` | Lane A: only inside the section **"v2.0 feedback on Android"** of each file. Lane I: only inside **"v2.0 feedback on iOS"** | Step 1 adds both sections at the end of each file, Android first, each with a placeholder line, so the iOS heading always separates the two. `README.md`'s "Planned changes" list is Step 2's. |
| `RUNBOOK.md` | Each lane ticks only its own Progress table and writes only under its own steps' **Log** | Tables are separated by headings. |
| `HANDOVER.md` | Each lane replaces only its own block ("Next — Lane A", "Lane I", "Lane D") | Step 1 creates the three blocks under separate headings. |
| `design/research/2026-11-v20-build/<step>/` | The step named in the folder | Captures only; Cooldown screens only, never home pages or the shade's other notifications. |
| `app/**`, `tools/widget_layouts.py`, Android test code | Lane A | Step 2 then owns the fixture switch-over in tests. |
| `ios/**` | Lane I | Same. |
| `docs/privacy.md`, `release/play/*`, `release/docs/*`, `README.md`, a new `design/` page for the feature graphic | Lane D | From Step 4 on, the serial step in progress owns them. |

**Fixtures without breaking `main`.** Both clients' tests read `spec/core/fixtures/pace-distance/printed.json`
directly. Changing it in place would turn the other platform's tests red the moment one lane lands. So Step 1
writes the changed and new cases into **new files** that today's tests do not read:
`spec/core/fixtures/pace-distance/printed-v20.json` (and new files under `spec/mobile/fixtures/` for mobile-only
rules). Each lane points its own tests at them as it builds. Step 2 folds them into the old files, switches
both clients' tests back in one commit, and promotes the §6.3 planned block into the rules. Until Step 2, the
§6.3 rules "above the planned block" stay the shipped ones, as the spec already says.

## Conventions — every session reads this block first

1. **Start from the paste.** Each step's fenced prompt needs no earlier conversation. Open a fresh session in
   `~/Projects/Cooldown` with **worktree ticked**, model **Opus 5.5**, and paste it.
2. **Session start:** read `CLAUDE.md` and `WORKTREE.md`, load the `model-mesh` skill (you are the seat, Opus at
   effort medium), run WORKTREE.md's mode check and its sweeper.
3. **Tests.** Android: `./gradlew testGithubDebugUnitTest testPlayDebugUnitTest lintGithubDebug` green and
   `./gradlew assembleGithubDebug assemblePlayDebug` compiling. Set `JAVA_HOME=/opt/homebrew/opt/openjdk@21` and
   `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools` (a fresh worktree has no `local.properties`). If
   a widget layout changes, `tools/widget_layouts.py` must regenerate the layouts byte for byte. iOS:
   `swift test --package-path ios/Packages/CooldownCore`, then the app tests (`xcodegen generate` in `ios/`, then
   `xcodebuild test … -only-testing:CooldownTests -destination 'platform=iOS Simulator,name=iPhone 17 Pro'`).
   Spec: `sh spec/tools/check_no_code.sh`.
4. **One fresh Opus judge round per code step**, on the step's diff against the wireframe sections it builds,
   before landing; then tests decide. Adopt each finding or record why not in the step's Log. Judges and other
   sub-agents run as model-mesh workers on the product account: `model-mesh-review worker judge --repo <this
   worktree> --spec <file> --model opus --account ~/.claude-product`. Workers cannot run gradle or xcodebuild,
   so the seat runs every build and test and makes every commit.
5. **Commits.** The commit that completes an item carries its remaining code, its ROADMAP (and, where it has
   one, BUGS) status line, and its `spec/mobile/surfaces/` delta (CLAUDE.md §2). Earlier code-only commits on
   the branch are fine. Subjects `feat(CCRM-110): …`, `fix(CCBG-79): …`. Tracker IDs carry their epic names on
   first use in a body (CLAUDE.md §1). Never stage `ccooldown-release.jks`, `keystore.properties`,
   `local.properties`, `ios/Config/Antigravity.secrets.xcconfig` or any PEPK output.
6. **Landing — WORKTREE.md §4, never another way.** Commit and `git push -u origin HEAD` as you go; on the first
   push `gh pr create --base main --fill`. To land: `git fetch origin`, `git merge --no-edit origin/main`, the
   ID check, the verify command on that head, then `git push -u origin HEAD` and `git push origin HEAD:main`, each
   as its own call. Never force, in any spelling. A merge conflict means `git merge --abort`, stop and report.
7. **The wireframe is the contract.** Anything visible that the approved rev B does not draw, or draws
   differently, stops the step and goes back to the orchestrating session. It is never improvised. Choosing
   among the wireframe's own drop orders and stated fallbacks is not a departure.
8. **Machine.** Android checks run on the AVD `fold7inner`, booted headless on port 5556 (`emulator -avd
   fold7inner -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot-save`), with `wm size`/`wm density` for the
   cover-like (1080x2520 @411), inner (750 dp) and 360 dp widths, dark and light. It is reset and killed
   (`adb -s emulator-5556 emu kill`) at the end of the step. **Never touch `testfone` (emulator-5554):** another
   project may be driving it. A debug build allows `run-as com.robin.claudeusage` to seed preferences and the
   debug synthetic series for states that need data. iOS checks run on the iPhone 17 Pro, iPhone 17e and iPad Pro
   13″ simulators, both orientations on the iPad. **Robin does every physical Fold 7 and iPad check** (Step 3);
   lane steps use the emulator and simulators only.
9. **Close-out, in order:** every *Done when* item; the status lines; the Progress tick and one dated Log line
   under the step; land; replace your own HANDOVER block with the next step's prompt. The last message gives
   what changed, what Robin must do, and the next prompt.
10. **Decision gates.** A step whose *Gated on* names a question (Q1–Q6) does not start until that question's
    answer is in "Robin's answers" below, in his words with the date. If it is not there, the step's first act is
    to ask Robin that question (one at a time, with the options listed), record the answer, and only then go on.
    An answer that adds a step (Q5 "in v2.0": a wireframe and a build) stops the arc: the revision takes the plan's
    one delta review before any session builds it. No session answers a question for Robin.
11. **Questions go to Robin one at a time, with options.** Minor fit calls inside the wireframe go to a Fable
    judge first (`--model fable`); Robin hears only a departure, a disagreement or a reserved decision.

## Progress

### Plan and spec (serial)

| Step | What | Who | Gated on | Status |
|---|---|---|---|---|
| 0 | This plan, Astra at high, freeze | planning session | — | ☑ 2026-11-11 |
| 1 | Spec first: new fixtures, lane sections, tracker lines; **Q6 asked and recorded** | session · judge | 0 frozen | ☑ 2026-11-11 |

### Lane A — Android (one step at a time)

| Step | What | Gated on | Status |
|---|---|---|---|
| A1 | Lines and labels: CCRM-106, CCRM-107, CCRM-108 | 1, Q6 | ☐ |
| A2 | Duet: CCRM-105 | A1 | ☐ |
| A3 | Pace map: CCRM-109, CCRM-111, CCBG-79 | A2 | ☐ |
| A4 | Summary: CCRM-110, CCRM-112 | A3, Q5 | ☐ |
| A5 | Sample: CCRM-113 | A4 | ☐ |

### Lane I — iOS (one step at a time; runs beside Lane A)

| Step | What | Gated on | Status |
|---|---|---|---|
| I1 | Lines and labels: CCRM-106, CCRM-107, CCRM-108 | 1, Q6 ("one lane at a time" → also A5) | ☐ |
| I2 | Pace map: CCRM-109, CCRM-111 | I1 | ☐ |
| I3 | Summary: CCRM-110, CCRM-112; the CCRM-113 iOS follow-up | I2 | ☐ |

### Lane D — release text and the graphic (beside the lanes)

| Step | What | Gated on | Status |
|---|---|---|---|
| D1 | Privacy policy, Data safety, App access, listing copy, release notes draft, feature-graphic wireframe | 0 frozen; the graphic part on Q4 | ☑ 2026-11-11 |

### Robin's own track (starts now; no session needed)

| Step | What | Gated on | Status |
|---|---|---|---|
| R1 | Play Console Phases A–C (`release/play/CONSOLE-STEPS.md`): account, identity, device verification, app record, declarations | — | ☐ |
| R2 | Recruit 15–18 testers ([TESTER-INVITE.md](release/play/TESTER-INVITE.md) §1 "the ask"), make the Google Group | — | ☐ |

### Tail (serial)

| Step | What | Who | Gated on | Status |
|---|---|---|---|---|
| 2 | Fold-in: fixtures and §6.3 promoted, surfaces folded, tracker closed | session · judge | A5, I3 | ☐ |
| 3 | Device pass 2: Fold 7 and iPad; defects filed and fixed | session · **Robin at the devices** | 2 | ☐ |
| 4 | Release commit: 26 / "2.0", iOS 2.0 (1), docs; APK, AAB, IPA archive built and checked | session · judge | 3, D1, Q1 | ☐ |
| 5 | Store assets on the release build: screenshots, FGS video, feature graphic | session · **Robin approves the graphic** | 4, Q3, Q4 | ☐ |
| 6 | TestFlight upload and Beta App Review | **Robin at the Mac** | 4 | ☐ |
| 7 | Publish GitHub v2.0 (APK, iOS build under the same version) | session · Fable judge + Sol · **Robin's go** | 4, 6, Q1, Q2 | ☐ |
| 8 | Play Day 0: signing, internal upload, closed track, invites | **Robin at the console** · session guides | 4, 5, 7, R1, R2 | ☐ |

---

## Step 0 · Plan, review, freeze

**Who:** the planning session, 2026-11-11.

**Done when:**
- ☑ This file and HANDOVER.md's next-session block are on `main`.
- ☑ Astra (high, one full call) has returned; every finding is in the findings table
  (`reviews/v20-build-arc-findings.md`); the `blocking` one is closed by a named check.
- ☑ FROZEN replaces DRAFT in the status line, with the date.

**Reversal:** documentation only — `git revert` of the planning commit.

**Log:**
- 2026-11-11 — Drafted from ROADMAP CCRM-105…113, core §6.3's planned block, BEHAVIOR-MOBILE §7, the surfaces
  README, RUNBOOK-v2.0 (Steps 7–8 open), RELEASING.md and `release/play/*`. Merge dry run (scratch repo): two
  branches editing lines separated by one unchanged line merged cleanly; two branches editing adjacent lines
  conflicted. The shared-file table rests on this.
- 2026-11-11 — Astra, high, one full call (`reviews/v20-build-arc-astra-call1.txt`): **blocking**, coverage
  full, one finding.

  | ID | Grade | Conf. | Where | What | Fix | Closed by | Status |
  |---|---|---|---|---|---|---|---|
  | C1-1 | blocking | 97 | Shape; Steps 5–7; Reserved | Steps could start on Robin's open questions with no gate (parallel lanes, share-card scope, assets, release contents, Beta App Review timing) | Convention 10 decision gates; Q-numbers in *Gated on*; the "Robin's answers" table; every gated prompt checks it and asks first; Q6 asked in Step 1; Step 7 needs Beta App Review approval logged if Q2 says wait | `reviews/v20-build-arc-gatecheck.py` — PASS | fixed |

  No delta call (no step added, no reversal changed). Plan **FROZEN**.

---

## Step 1 · Spec first — fixtures, lane sections, tracker lines

**Owns:** `spec/core/fixtures/pace-distance/printed-v20.json` (new), new files under `spec/mobile/fixtures/`,
the scaffolding in `ROADMAP.md`, `spec/mobile/surfaces/*.md` and `HANDOVER.md`, and this step's tick.

**Prompt:**

```
Read CLAUDE.md, WORKTREE.md, RUNBOOK.md (Conventions, "Who owns each shared file", Step 1) and HANDOVER.md;
load the model-mesh skill; run the mode check and the sweeper. Do RUNBOOK Step 1, spec only, no app code.
Q6 is already answered in "Robin's answers" (both lanes at once); do not ask it again.
(1) Write spec/core/fixtures/pace-distance/printed-v20.json from BEHAVIOR-SPEC §6.3 "Planned — v2.0 feedback":
the paceLine, paceLineForms and endToEnd cases in their "resets" forms and the weekday-only form (both
drop orders: another day, later today); the day grid (column edges at start + k x 24 h, sevenths, the 5h
hourly columns, labels, the bold now-column, an ordinal yielding to "now" or an end label); mapLabels with a
dense 200-wide map (leaders at 24/36/48, refusal across a placed name or dot, the name not drawn when nothing
is free); the quiet rule (row prints "0%" -> not plotted; 0.5 rounding edge); the callout forecast ("~74% at
reset", "100% at Wed 6:10 PM"); the projection layer (no tail when the estimate is null or the week is spent).
Mobile-only forms go in new files under spec/mobile/fixtures/ (the foot-line count "2 at 0% · 1 no reading",
the tag filter: untagged only under All). Derive every number by a script from the written rules, and check
the wireframe's worked examples against it; do not edit printed.json or any file today's tests read.
(2) Scaffold the shared files exactly as RUNBOOK's table says: in ROADMAP.md add a last bullet "- **iOS:** not
started" to CCRM-106…112 and "- **iOS follow-up:** not started" to CCRM-113 (CCRM-105 gets none); at the end of
spec/mobile/surfaces/app.md, foundations.md, notification.md and widgets.md add "## v2.0 feedback on Android"
then "## v2.0 feedback on iOS" (notification.md: Android only), each with one placeholder line; in HANDOVER.md
add three blocks "Next — Lane A", "Next — Lane I", "Next — Lane D" holding the A1, I1 and D1 prompts.
Check: check_no_code.sh passes; both platforms' full test suites pass unchanged; the generator script is
committed under spec/tools/. One fresh Opus judge round on the fixtures against §6.3 and the wireframe
(Conventions 4). Close out per Convention 9 and land per WORKTREE.md.
```

**Done when:**
- ☑ `printed-v20.json` and the mobile fixture files exist, every case derived by the committed script, and the
  wireframe's worked examples agree with them.
- ☑ `check_no_code.sh`, the Android suite and both iOS suites pass with no test changed.
- ☑ The ROADMAP iOS lines, the two surfaces sections per file and the three HANDOVER lane blocks exist.
- ☑ Q6 is answered and recorded in "Robin's answers" (2026-11-11, before Step 1).
- ☑ Judge round recorded; landed on `main`.
- ☑ The orchestrating session is told the fixtures commit and Q6's answer, so it can send the desktop session the frozen set
  (outward step, the orchestrator's).

**Reversal:** `git revert` of the step's commit; no test reads the new files yet.

**Log:**

- 2026-11-11 · Step 1 done (worktree `relaxed-bhaskara-eb72bc`). **Fixtures**, all from
  `spec/tools/gen_v20_fixtures.py` (`--check` re-derives them; its self-check first reproduces printed.json's
  shipped endToEnd, dayClock and duration cases, then asserts the wireframe's worked examples):
  `spec/core/fixtures/pace-distance/printed-v20.json` (paceLine, paceLineForms with both drop orders and the
  weekday-only form, endToEnd, dayGrid, quiet, mapLabels words / figures / placement incl. the dense 200-wide map
  covering right, below, left, above-left, leaders 24/36/48 and "none", with path refusals across a placed name
  and a dot, callout forecast, projectionLayer); `spec/mobile/fixtures/map-foot/cases.json` (the foot-line count);
  `spec/mobile/fixtures/tags/cases.json` (the tag filter). No file a test reads today was changed. Pointers added
  at the end of core §6.3's planned block and in `spec/core/fixtures/README.md`. **Checks:** check_no_code.sh;
  Android `testGithubDebugUnitTest testPlayDebugUnitTest lintGithubDebug` green; `swift test` 196 passed; iOS
  app tests 95 passed (iPhone 17 Pro). HANDOVER's "Next — Lane D" block records D1 as done (f156f42), on the
  orchestrating session's word.
- 2026-11-11 · **Judge** (fresh Opus, high, worker on claude-product): **accept with concerns**, five findings.
  F1 (concern) the wireframe's iOS cap-half grid example disagrees with the prose rule (the drawing measures 5.2
  per character, |Δx| < 16) → case added, rule kept; F2 (concern) a reading of 1% or more with no reset clock was
  neither plotted nor counted → case added; F3 (concern) a spent account in Left mode is labelled "Teams 0%" (the
  drawing's `figOf`); F4 (nit) name-area margin recorded in the §6.3 pointer, `nowLabel` off in the column-7 case;
  F5 (nit) surfaces headings trimmed to the exact names. F1–F3 went to a **Fable judge** (Convention 11): F1 the
  prose rule (6 to "now", 4 to an end label), not a departure; the §3 caption "day 5 bold" at now 68% is wrong
  under either rule (the drawing itself omits the "5"). F2 count it "no reading", not a departure. F3 Fable
  prefers "Teams 0% left", but that changes what Robin saw drawn (the SIX frame's "Atlas 0%"), so the fixture
  keeps the drawn "Teams 0%" and the question goes to the orchestrating session for Robin.
  **Choices the fixtures fix where §6.3 is open** (in `_meta` and the §6.3 pointer): leader directions clockwise
  from east at each distance; the name box touches the leader's end by its side facing away from the dot; the
  fixture's own placement offsets (each family keeps its drawn ones); only ordinals are bold, never the end labels.

---

## Lane A — Android

Every Lane A step uses the same prompt shape. Paste it with the step's ID:

```
Read CLAUDE.md, WORKTREE.md, RUNBOOK.md (Conventions, "Who owns each shared file", Lane A and Step <A-n>),
the HANDOVER.md block "Next — Lane A", the wireframe sections Step <A-n> names in
design/2026-11-10-v20-feedback-wireframes.html, and the ROADMAP entries it builds; load the model-mesh
skill; run the mode check and the sweeper. Check the step's "Gated on" questions are answered in "Robin's
answers" (Convention 10; ask and record first if not). Build Step <A-n> on Android only (repo root, never ios/). Write
only the files Lane A owns: in ROADMAP.md only each item's Status line, in spec/mobile/surfaces/ only inside
"v2.0 feedback on Android". Tests read spec/core/fixtures/pace-distance/printed-v20.json and the new mobile
fixtures for the changed rules. Check every state the wireframe draws for these items on the fold7inner
emulator (port 5556, headless; never emulator-5554) at the cover-like, inner and 360 dp widths, dark and
light; save the captures in design/research/2026-11-v20-build/<A-n>/. Stop and report at anything the
wireframe does not draw. One fresh Opus judge round, then close out per Convention 9 and land per WORKTREE.md.
```

### Step A1 · Lines and labels — CCRM-106, CCRM-107, CCRM-108

**Builds:** wireframe §2, §3 and §4 (form B). The tile chips and the classic 7-day card read "7d" (TalkBack
"7-day, all models"; the stored chip key unchanged). The day grid on every 7-day chart (Large, Small, share card)
and on 5h charts; the Pace map's axis "week start", 2…6, "reset", and "each account's own week" moved to its foot
line. The pace line's "resets" forms and the new drop order on the notification, the tile, the Summary line 3 and
the widgets' Weekly lines, with the fit tables re-measured.

**Done when:**
- ☐ Unit tests on `printed-v20.json` (paceLine, paceLineForms, endToEnd, the grid) pass on both flavors, with
  lint, and the widget generator is byte-identical.
- ☐ Emulator captures: each chart with the grid (7-day and 5h, now-column bold, an ordinal yielding), the
  notification's collapsed and expanded pace line at the cover-like width showing "· Sun" for a reset days away
  (not "· 9:30 PM"), the tile and a widget's Weekly line.
- ☐ ROADMAP Status lines of CCRM-106, 107 and 108 read "Android built" with the commit; the Android sections
  of `app.md`, `foundations.md`, `notification.md` and `widgets.md` carry the delta.
- ☐ Judge round recorded; landed.

### Step A2 · Duet — CCRM-105

**Builds:** wireframe §1. Collapsed 52 dp: two 36 dp 5h rings on the left (First left, unsigned, account
colours), two stacked rows (14 dp mark, 12 sp name, right-aligned 11 sp state word; an 8 dp Weekly bar with tick
and red slice, both bars on one edge and length; an 18 sp bold tabular signed figure in a fixed 42 dp slot). The
narrow drop order: the bar absorbs, the name to its 24 dp floor, then the state word's tail. Expanded keeps both
headers, figure 36 → 24 sp. Collapsed text capped in dp at large font scales. The bare 14 dp ring retires.

**Done when:**
- ☐ Fit tests for the new width budget (136 dp chrome, no halves) and the drop order; both flavors' tests and lint.
- ☐ Emulator captures: collapsed and expanded at the three widths, font scale 1.0 and 2.0, both signs, one
  account spent, one with no 5h.
- ☐ CCRM-105 Status "Android built"; `notification.md` §2.1–§2.4 and §8 and `foundations.md` §2 deltas inside the
  Android sections.
- ☐ **UNVERIFIED until Step 3:** the real cover and inner shade widths. Write the measured emulator widths in the
  Log so Step 3 can compare.
- ☐ Judge round recorded; landed.

### Step A3 · Pace map — CCRM-109, CCRM-111, closes CCBG-79

**Builds:** wireframe §5 and §7. Accounts whose Weekly row prints "0%" leave the map; the foot line counts them
("2 at 0%", "· 1 no reading"); all at 0% keeps the frame with "Nothing to plot yet". The Figures · Trails ·
Projection chips (32 dp, defaults off/on/off, remembered, wrapping under the title at large text); the figure
after the name; the projection's dashed tail and hollow ring (red on the 100% line when it runs out); the last
24 h of each trail heavier; a tap pins the callout (others dim to 35%, "~74% at reset"), a second tap opens the
account; dotted leaders of at most 48 dp, the name not drawn when nothing is free.

**Done when:**
- ☐ Tests on `mapLabels` (the dense 200-wide case), the quiet rule, the callout forecast and the projection layer;
  both flavors' tests and lint.
- ☐ Emulator captures: each chip combination, the callout pinned, a dense map with a refused label, all quiet,
  two-column inner layout.
- ☐ CCRM-109 and CCRM-111 Status "Android built"; CCBG-79's status notes the Android fix (Lane A owns BUGS.md);
  `app.md` Android section.
- ☐ Judge round recorded; landed.

### Step A4 · Summary — CCRM-110, CCRM-112

**Builds:** wireframe §6 and §8. "Tag…" in the account menu (None · Personal · Work), the neutral chip with a
person or briefcase glyph and the word in Settings › Accounts; the All · Personal · Work control beside "Weekly"
once any account has a tag (remembered; untagged only under All), filtering the list, the map and the snapshot;
"your order" becomes the sort menu of the four orders. "Share snapshot" in the Summary's ⋮: header with the
filter's name and time, the rows in screen order, then the map with the on-screen layers, never the chips or a
callout; names, never e-mail addresses; the CCRM-24 (Share Card) share dialog.

**Done when:**
- ☐ The new tag preference is additive and downgrade-tested (v2.0 prefs read through v1.9's accessors give
  v1.9's defaults); filter and sort tests on the mobile fixtures; both flavors' tests and lint.
- ☐ Emulator captures: no tags (no control), one tag, each filter, the sort menu, the snapshot for six accounts
  and for a filter, the share sheet.
- ☐ CCRM-110 and CCRM-112 Status "Android built"; `app.md` Android section.
- ☐ Judge round recorded; landed.

### Step A5 · Sample — CCRM-113

**Builds:** wireframe §9 and BEHAVIOR-MOBILE §7. "Try with sample data" under the first screen's "No Claude
account yet" card and as the last row of Add account; three made-up accounts (Teams · Claude, Personal ·
ChatGPT, Lab · Gemini); the violet "● Sample data — made-up accounts · Exit" banner on every tab, Settings and
History; held in memory, settings in an in-memory copy. Isolation: real polling carries on into the real stores;
only the app's screens read the copy; a sample edit binds to the sample store; the debug synthetic series pauses.

**Done when:**
- ☐ The four isolation tests from `reviews/v20-sample-mode-findings.md`: byte-identical real stores across
  enter and exit; an Appearance change in the sample leaves the live notification and a widget untouched; a
  Rename open across a switch never renames a real account; the synthetic flag suspended and restored, nothing
  written. Both flavors' tests and lint.
- ☐ Emulator captures: entry points, the banner on every tab, Settings, History, the share band "● SAMPLE DATA",
  and exit; a process kill returns to the real app.
- ☐ CCRM-113 Status "Android built"; `app.md`, `notification.md`, `widgets.md` Android sections.
- ☐ Judge round recorded; landed. Lane A's HANDOVER block now says "Lane A done".

**Lane A reversal:** each step is its own commits; `git revert` of them, newest first, restores the previous
step. A4's tag preference is additive, so a revert leaves only an unread key.

**Lane A Log:**

---

## Lane I — iOS

Every Lane I step uses the same prompt shape:

```
Read CLAUDE.md, WORKTREE.md, RUNBOOK.md (Conventions, "Who owns each shared file", Lane I and Step <I-n>),
the HANDOVER.md block "Next — Lane I", the wireframe sections Step <I-n> names in
design/2026-11-10-v20-feedback-wireframes.html, and the ROADMAP entries it builds; load the model-mesh
skill; run the mode check and the sweeper. Check the step's "Gated on" questions are answered in "Robin's
answers" (Convention 10; ask and record first if not). Build Step <I-n> on iOS only (ios/, never the Android code).
Write only the files Lane I owns: in ROADMAP.md only each item's "iOS:" line, in spec/mobile/surfaces/ only
inside "v2.0 feedback on iOS"; never BUGS.md (record a defect in this step's Log). CooldownCore first, with
its tests reading spec/core/fixtures/pace-distance/printed-v20.json and the new mobile fixtures, then the app,
widgets, lock screen and Live Activity as the wireframe says. Check every state the wireframe draws on the
iPhone 17 Pro, iPhone 17e and iPad Pro 13" simulators (iPad in both orientations), dark and light; save the
captures in design/research/2026-11-v20-build/<I-n>/. Stop and report at anything the wireframe does not
draw. One fresh Opus judge round, then close out per Convention 9 and land per WORKTREE.md.
```

### Step I1 · Lines and labels — CCRM-106, CCRM-107, CCRM-108

**Builds:** wireframe §2, §3 and §4 on iOS: the "7d" chip and label (VoiceOver "7-day, all models"); the day
grid on every 7-day and 5h chart and the Pace map's axis and foot line; the "resets" pace line and drop order on
the tile, the Summary, "the pace line on iOS", the widgets, lock screen and Live Activity.

**Done when:**
- ☐ `swift test` on the v20 fixtures and the app tests pass.
- ☐ Simulator captures of each chart and pace-line surface, including the iPhone 17e's narrowest Summary row.
- ☐ The "iOS:" lines of CCRM-106, 107 and 108 read "built" with the commit; the iOS sections of `app.md`,
  `foundations.md` and `widgets.md` carry the delta.
- ☐ Judge round recorded; landed.

### Step I2 · Pace map — CCRM-109, CCRM-111

**Builds:** wireframe §5 and §7 on iOS (chips 13 pt, wrapping at large text; the callout; leaders; quiet
accounts and the foot-line count). Fixes the leader defect of CCBG-79 on iOS; the bug's own status is written in
Step 2.

**Done when:**
- ☐ `swift test` on `mapLabels`, quiet, callout and projection cases; the app tests pass.
- ☐ Simulator captures: each chip combination, the callout, a dense map, all quiet, the iPad two-column Summary.
- ☐ The "iOS:" lines of CCRM-109 and CCRM-111 read "built"; the iOS section of `app.md`.
- ☐ Judge round recorded; landed.

### Step I3 · Summary — CCRM-110, CCRM-112, and the CCRM-113 iOS follow-up

**Builds:** wireframe §6 and §8 on iOS (the tag on the account page and by long-press; the filter control; the
sort menu; "Share snapshot" in the Summary's ellipsis menu), and the iOS sample's swap of "Product" (Claude) for a
Gemini account, so both stores' review notes describe the same three accounts.

**Done when:**
- ☐ The tag key is additive and round-trips through the v1.9-era reader; filter, sort and snapshot tests;
  `swift test` and the app tests pass.
- ☐ Simulator captures: tags, filters, the sort menu, the snapshot and share sheet, the sample's three accounts.
- ☐ The "iOS:" lines of CCRM-110 and CCRM-112 and the "iOS follow-up:" line of CCRM-113 read "built"; the iOS
  section of `app.md`.
- ☐ Judge round recorded; landed. Lane I's HANDOVER block now says "Lane I done".

**Lane I reversal:** as Lane A: `git revert` of the step's commits, newest first.

**Lane I Log:**

---

## Lane D — release text and the feature graphic

### Step D1 · Release text, and the feature-graphic wireframe

**Owns:** `docs/privacy.md`, `release/play/{data-safety,app-access,listing,fgs-declaration}.md`,
`release/docs/release-notes-v2.0.md` (draft), and a new `design/2026-11-xx-play-feature-graphic-v20.html`.
Never code, never a tracker file (anything for a tracker goes in this step's Log; Step 2 writes it).

**Prompt:**

```
Read CLAUDE.md, WORKTREE.md, RUNBOOK.md (Conventions, "Who owns each shared file", Step D1), the HANDOVER.md
block "Next — Lane D", RELEASING.md §3 and §5, release/play/V2.0-ASSET-CHECK.md, CONSOLE-STEPS.md steps
11-18 and docs/privacy.md; load the model-mesh skill; run the mode check and the sweeper. Before part (7),
Q4 must be answered in "Robin's answers" (Convention 10; ask and record first if not). Do RUNBOOK Step D1,
no code: (1) docs/privacy.md — the Android half gains the Google (Gemini) row (accounts.google.com,
oauth2.googleapis.com, www.googleapis.com, daily-cloudcode-pa.googleapis.com), marked "from version 2.0", and
the providers sentence names Google; (2) release/play/data-safety.md — Google joins the recipients of the
OAuth tokens, the code table gains AntigravitySource and its sign-in, and say whether the Google account email
stays on the device; (3) app-access.md — the reviewer path is "Try with sample data" per wireframe §9, the
notification and widgets need a real account and the FGS video shows them; (4) listing.md — every row
V2.0-ASSET-CHECK marks Rewrite, Wrong, Incomplete, Extend or Add, counted against Play's limits; (5)
fgs-declaration.md — "Claude, ChatGPT or Gemini"; (6) a draft of release/docs/release-notes-v2.0.md (the app
only, no model credits or build story); (7) the feature graphic redrawn on the Icon v3 "Shared pace" mark as a
design/ page with variants, for Robin's approval (CLAUDE.md §2), one question at a time. Do not export it
until Robin approves. Close out per Convention 9 and land per WORKTREE.md.
```

**Done when:**
- ☑ The six text files are updated and land. The privacy page's Google row is marked "from version 2.0", so the
  live page stays true for v1.9 users until v2.0 ships.
- ☑ The feature-graphic page is in `design/` and Robin has approved a named variant (or the step's Log records
  that the approval is still open; Step 5 waits on it).

**Reversal:** `git revert` of the step's commits.

**Log:**

- 2026-11-11 · D1 done (Lane D session, worktree `hungry-williams-0089c1`, PR #36). Text in c4ebeb3, graphic
  page in 4ee764f. **Listing** (`release/play/listing.md`): short description rewritten, 75/80; full
  description rewritten row by row against V2.0-ASSET-CHECK (opening line, Gemini line, What it shows,
  notification, widgets, accounts, sharing, privacy recipients; a new *Summary* and *Try it first*),
  3,930/4,000; a new §6 Play release notes, 420/500; app name unchanged, 26/30. App access instructions
  1,256 characters (Play's limit to be read on the day). **Graphic:** Robin approved **variant C** (three
  Summary rows, Teams/Personal/Lab in the icon's arc colours) on 2026-11-11, asked once with A, B, C as
  options (B recommended); `design/2026-11-11-play-feature-graphic-v20.html`, export `?only=c` at Step 5.
  HANDOVER.md not edited: Step 1 had not landed its "Next — Lane D" block at close-out.
  **For Step 2 (trackers and shared files D1 does not own):**
  (a) RELEASING.md §5 still names `design/2026-10-01-play-feature-graphic.html` variant B as the source of
  `feature-graphic.png`; it becomes the 2026-11-11 page, variant C.
  (b) `release/testflight/privacy-ios-draft.md`, V2.0-ASSET-CHECK and CONSOLE-STEPS step 11 list
  `www.googleapis.com` as a host; neither app contacts it (it appears only in the OAuth scope names, checked
  by grep of `app/` and `ios/`). `docs/privacy.md` now says so in both halves; the TestFlight draft should too.
  (c) listing.md §5 (Step 5's asset table) still says to export the icon from the Pulse and the graphic
  from the v1.9 page.
  (d) V2.0-ASSET-CHECK's "Pace in words (band and daily allowance)" predates CCRM-104 (Pace Line Parity),
  which took the band words off the lines; the listing follows CCRM-104.
  (e) The listing and app-access text use the wireframe's labels ("Try with sample data", "Exit",
  Teams/Personal/Lab); Step 5 checks them on the release build before pasting.
  (f) Gemini plans: only a Pro account has been measured; the listing says only "the usage windows that
  Google's Antigravity reports", no plan list.

---

## Step 2 · Fold-in — one tree, one spec

**Owns:** `spec/**`, both clients' fixture-reading tests, `ROADMAP.md`, `BUGS.md`, `spec/mobile/surfaces/**`,
and the lane-recorded defects.

**Prompt:**

```
Read CLAUDE.md, WORKTREE.md, RUNBOOK.md (Conventions, Step 2 and the Logs of Lanes A, I and D) and HANDOVER.md;
load the model-mesh skill; run the mode check and the sweeper. Lanes A and I are done. Do RUNBOOK Step 2: fold
printed-v20.json into printed.json (the changed blocks replace theirs) and delete printed-v20.json; point both
clients' tests back at printed.json in the same commit; move the BEHAVIOR-SPEC §6.3 "Planned — v2.0 feedback"
rules into the rules they replace and drop the "planned" label; fold each surfaces file's "v2.0 feedback on
Android/iOS" sections into the body where they replace a body rule (keep a platform section only where the
platforms differ), and empty README's "Planned changes" of these items; rewrite each CCRM-105…113 Status and
iOS line into one status ("Built, device check pending", with commits); file every defect the Lane I and Lane D
Logs recorded; write CCBG-79's status.
Check: the Android suite and both iOS suites, check_no_code.sh. One fresh Opus judge round. Close out and land.
```

**Done when:**
- ☐ One fixture file per topic, no `-v20` file left, both platforms green on it.
- ☐ §6.3 has no planned block for these items; the surfaces folder describes v2.0 as built.
- ☐ Tracker lines closed as above; lane-recorded defects filed in BUGS.md.
- ☐ Judge round recorded; landed. The orchestrating session is told the commit, the desktop's core re-pin point
  (outward step, the orchestrator's).

**Reversal:** `git revert` of the step's commit restores the lane state (both clients then read the v20 file again).

**Log:**

---

## Step 3 · Device pass 2 — Fold 7 and iPad

**Who:** the session, with **Robin at the devices** (he connects the Fold 7 and unlocks it; he drives the iPad
while a capture loop runs).

**Owns:** `BUGS.md`, the fixes that follow, `design/research/2026-11-v20-build/device-pass/`.

**Prompt:**

```
Read CLAUDE.md, WORKTREE.md, RUNBOOK.md (Conventions and Step 3), HANDOVER.md, and RUNBOOK-v2.0.md's Step 7
and its Log for how the first pass was run; load the model-mesh skill; run the mode check and the sweeper.
Do RUNBOOK Step 3. Ask Robin to connect the Fold 7 and the iPad, one question at a time. Before anything,
record each device's starting state in this step's Log (installed build and flavor, screen timeout, the
Cooldown settings you will change, placed widgets, Live Activity); keep private captures (home pages, the
shade's other notifications) in the scratchpad, never the repo. Install a release-signed github build of main
on the Fold 7 (the keystore files copied from ~/Projects/Cooldown, deleted after; never staged) and a Debug
build on the iPad. Check every item of Step 3's list, file every defect in BUGS.md, and ask Robin which ones
block v2.0. Fix the blocking ones here, one commit per defect with its status, a fresh Opus judge round on the
batch, and anything visible beyond the wireframe goes to Robin first. Run the "Always after" block, then
close out and land.
```

**The check list:**
- **The nine items** on the cover and inner screens, dark and light: the Duet collapsed and expanded on both
  shades (CCRM-105's real widths against A2's emulator numbers), "7d", the grid on every chart, the "resets"
  line and its drop order ("· Sun" on the cover), the quiet accounts, the chips and callout, tags, filter, sort,
  the snapshot, the sample mode (entry, banner, exit, the notification and widgets still on real accounts).
  The iPad: the same where iOS has them, both orientations; the iOS sample's three accounts.
- **Carried from RUNBOOK-v2.0 Step 7:** CCBG-78's collapsed pace line on the cover; CCRM-104's pace line and
  signed Vs pace on both devices; CCBG-72 (Live Activity follows a Usage display change) and CCBG-73's plan
  names on the iPad; a running, binding and full 5h with Headline 5-hour and Auto; a spent week; Gemini's pool
  shown once used and a refresh after an hour; the 4×2 picker previews; Robin removes the old Ring widget on the
  iPad (CCBG-70 (Stale Ring Widget)). Any "For Robin" item still open in RUNBOOK-v2.0's Log is asked here.

**Always after:** every recorded setting put back; widgets as recorded; the screen timeout restored before the
phone disconnects; the v2.0 candidate stays installed on purpose, and the Log says so per device.

**Done when:**
- ☐ Every check above is ticked, or marked not reachable with the reason.
- ☐ Robin has said which defects block; each blocking one is fixed, judged and rechecked on the device.
- ☐ *Always after* done and logged. Landed.

**Reversal:** the device: *Always after* against the recorded state. The fixes: `git revert`.

**Log:**

---

## Step 4 · The release commit, and the three builds

**Owns:** `app/build.gradle.kts` (versionCode 26, versionName "2.0"), `ios/project.yml` (`MARKETING_VERSION`
"2.0", `CURRENT_PROJECT_VERSION` "1"), RELEASING.md §3's docs (README, the user guide, `release/docs/`), the final
release notes.

**Prompt:**

```
Read CLAUDE.md, WORKTREE.md, RUNBOOK.md (Conventions and Step 4), RELEASING.md (all of it) and
release/testflight/version-plan.md; load the model-mesh skill; run the mode check and the sweeper. Q1 must be
answered in "Robin's answers" (Convention 10); if it is "attach an IPA", also export that IPA from the archive and
log its sha256. Do RUNBOOK Step 4, stopping before any tag: bump Android to versionCode 26 / "2.0" and iOS to MARKETING_VERSION 2.0 /
build 1; finish RELEASING.md §3's docs and release/docs/release-notes-v2.0.md (the app only); README and the
guide gain Gemini and its ToS line. Make the release commit, land it per WORKTREE.md, and wait for CI green on
that exact sha. Then, from a clean tree at that sha: check the Gemini secret is present for both platforms
without printing it; build the github APK (RELEASING §2) and the play AAB with every §5 check (bundletool
validate, signer = the trusted digest, versionCode 26 on both, check_artefact.py for both channels); run the
iOS tests and the archive with the §6 checks. Record the APK's and AAB's sha256s and the archive's path in
the Log. Install the APK on the Fold 7 for a smoke check (Robin connects). Nothing is tagged, uploaded or
published in this step.
```

**Done when:**
- ☐ The release commit is on `main` with CI green; its sha is in the Log.
- ☐ APK and AAB built from that sha, every RELEASING §5 check passed, sha256s logged; the AAB is kept out of
  the GitHub release.
- ☐ The iOS archive built from that sha and passes RELEASING §6's checks (versions match in app and widget,
  secret present, privacy manifests in, export compliance false).
- ☐ The APK runs on the Fold 7 (Gemini row present, update check reads v1.9 as older).

**Reversal:** `git revert` of the release commit before anything is tagged. Built files are local.

**Log:**

---

## Step 5 · Store assets on the release build

**Owns:** `release/play/screenshots/` (new), `release/play/fgs-demo.mp4`, `release/play/feature-graphic.png`,
`release/play/listing.md`'s asset table.

**Prompt:**

```
Read CLAUDE.md, WORKTREE.md, RUNBOOK.md (Conventions and Step 5), release/play/V2.0-ASSET-CHECK.md and
CONSOLE-STEPS.md § Policy check 7; load the model-mesh skill. Q3 and Q4 must be answered in "Robin's
answers" (Convention 10). Do RUNBOOK Step 5 on the Step 4 APK installed
on the Fold 7 (Robin connects): the eight phone screenshots in V2.0-ASSET-CHECK's order, no e-mail address on
screen, real accounts, cropped to 1080x2160 per Q3 (long side at most twice the short), every shot checked
for visible account names before it is committed or uploaded; the FGS video
(switch on -> Duet notification -> refresh -> switch off, no e-mail on screen) for Robin to upload unlisted;
export the feature graphic from the variant Robin approved in D1. Crop shade captures to the Cooldown card
only. Commit the assets, close out and land.
```

**Done when:**
- ☐ Eight screenshots at the agreed size, each checked against Play's ratio rule.
- ☐ The FGS video re-recorded on v2.0; the feature graphic exported from the approved variant at 1024×500.

**Reversal:** `git revert`; the old assets come back.

**Log:**

---

## Step 6 · TestFlight upload — Robin's

**Who:** **Robin at the Mac.** A session never uploads, never opens App Store Connect and never changes signing.

**What Robin does:** uploads Step 4's archive (Xcode Organizer → Distribute App → App Store Connect), creates
the app record if it is not there, pastes `release/testflight/whats-to-test.md` and `beta-review-notes.md` (the
sample-mode path, with the three accounts I3 made), fills the privacy-policy URL, answers App Privacy with *Data
Not Collected*, submits for Beta App Review and creates the public link. The link stays unshared until Step 7.

**Done when:** ☐ Robin reports the build "2.0 (1)" uploaded and its review state; the Log records it.

**Reversal:** none once uploaded (the build number is spent; a new upload is "2.0 (2)"). A rejected build is
fixed forward.

**Log:**

---

## Step 7 · Publish GitHub v2.0

**Owns:** the tag `v2.0` and the GitHub release.

**Prompt:**

```
Read CLAUDE.md, WORKTREE.md, RUNBOOK.md (Conventions, Step 4's and Step 6's Logs, Step 7) and RELEASING.md
§4 and §6; load the model-mesh skill. Q1 and Q2 must be answered in "Robin's answers" (Convention 10); if Q2
says wait, Step 6's Log must show Beta App Review approved before the tag. Do RUNBOOK Step 7: on the Step 4 release commit, tag v2.0, push the tag,
and create the draft release with the github APK as app-release.apk and release/docs/release-notes-v2.0.md as
its notes; per Q1 the notes carry the TestFlight public link and the iOS build "2.0 (1)", and there is no IPA
asset. Verify the draft per RELEASING §4 step 5 (hashes equal Step 4's, signer = the
trusted digest, 26 / "2.0"). Then the gate: a review packet on the publish command, a fresh Fable judge at high
(author Opus, irreversible) and a Sol cross-check (it publishes). Then ask Robin for his go in chat, naming the
commit and the APK sha256 (and the IPA's if one is attached); record his words verbatim in the Log. Publish
only after that. Install the published asset on the Fold 7 and check "Check for updates" reads up to date.
```

**Done when:**
- ☐ The release is published with the APK, and the iOS build named under the same version, never an iOS-only
  release.
- ☐ The judge, Sol and Robin's go are in the Log; the installed asset's sha256 equals the published one.

**Reversal:** none for anyone who installed. `gh release edit v2.0 --draft` withdraws it from `releases/latest`;
a fix ships as v2.0.1 with versionCode 27 on both channels.

**Log:**

---

## Step 8 · Play Day 0 — Robin at the console

**Who:** **Robin** clicks, pays, verifies, enrols and uploads; the session guides one screen at a time from
`release/play/CONSOLE-STEPS.md` Phases D–E and `CLOSED-TEST-PLAN.md`'s Day 0–3 rows, and checks what can be
checked.

**Needs:** R1 done (identity verified), R2 done (15+ testers' Google accounts), Step 4's AAB and its sha256,
Step 5's assets and Step 7 published.

**Prompt (for the guiding session):**

```
Read CLAUDE.md, RUNBOOK.md (Step 8 and Step 4's Log), release/play/CONSOLE-STEPS.md Phases D-E,
CLOSED-TEST-PLAN.md and TESTER-INVITE.md; load the model-mesh skill. Guide Robin through Play Day 0, one
screen at a time. Never open the Play Console yourself, never see the keystore, its passwords or the PEPK
output, and name every irreversible action before he takes it: Play App Signing with the existing key (PEPK);
the AAB from Step 4 to Internal testing (the package name is claimed here, for good); then check App bundle
explorer's signed universal APK SHA-256 against RELEASING.md's trusted digest before any tester installs;
the same AAB to the Closed track with the release notes and countries; the App content declarations
re-checked against the v2.0 drafts (data safety with Google, app access via the sample, the FGS declaration
and video link). When Google approves the closed release, give Robin TESTER-INVITE §2 "the go" with the
opt-in link filled. Log every action with the date; the clock (Day S) starts when 12 are opted in.
```

**Done when:**
- ☐ The AAB is on Internal testing and its Play-signed APK's digest matches; the same AAB is rolled out to
  Closed testing; the invite is sent; the Log records Day 0 and, once reached, Day S.

**Reversal:** none — the package name, the enrolled key and the fee are permanent. A bad closed release is
replaced by a new versionCode on both channels.

**Log:**

---

## Whole-plan reversal

- **Steps 0–2 and the lanes are commits.** `git revert` of the arc's commits, newest first, restores the v2.0
  candidate as it was on 2026-11-11. The only stored-data change is CCRM-110's additive tag key, downgrade-tested.
- **From a partly built state** (one lane done, the other not): `main` stays green throughout, because each
  lane tests against fixtures the other does not read and the spec keeps the shipped rules until Step 2. Either
  finish the slower lane, or revert the faster lane's commits; nothing in between ships, because Step 4 needs
  Step 2, which needs both lanes.
- **Step 3 changes the devices**, restored by its *Always after* (only the candidate build stays).
- **Steps 6, 7 and 8 cannot be undone** once done; a defect after them is fixed forward in a new versionCode on
  both channels.

## Reserved for Robin

The physical device checks and which defects block; any visible change beyond rev B; the feature-graphic
approval; the TestFlight upload and App Store Connect; the release go; everything in the Play Console (account,
fee, identity, device verification, PEPK, uploads, the closed track); recruiting and inviting testers; the
answers to the open questions below.

## Questions for Robin — answered 2026-11-11 (see "Robin's answers")

- **Q1** (gates Steps 4 and 7). RELEASING.md §6 says the GitHub release carries the APK and, in its notes, the TestFlight public link
  and the iOS build number (a TestFlight build cannot be installed from a GitHub asset). Keep that, or attach an
  ad-hoc IPA as a second asset?
- **Q2** (gates Step 7). Does the GitHub v2.0 publish (Step 7) wait for Beta App Review to approve the TestFlight build, so the
  link in the notes works on the day? Or publish first and add the link when it is approved?
- **Q3** (gates Step 5). Play screenshots: crop the Fold 7 full frames to 1080×2160 (drop the 149-px status band and 62 px of
  the gesture bar), or pad them? And real accounts with names, or the sample mode (its violet banner shows)?
- **Q4** (gates D1's graphic and Step 5). The feature graphic: redraw it on the Icon v3 mark for v2.0 (Lane D draws, you approve), or launch with
  the v1.9 graphic and redraw later?
- **Q5** (gates A4). The Android half of the Weekly-first share card (CCRM-24 (Share Card)) was left open after the first
  v2.0 pass and needs its own wireframe. In v2.0, or after?
- **Q6** (gates A1 and I1; asked in Step 1). Lanes A and I may run at the same time on this Mac (two sessions, one gradle, simulators beside the
  emulator). Fine, or one lane at a time?

## Robin's answers

Filled by the step that asks (Convention 10), in Robin's words, dated. Empty means not decided.

| Q | Gates | Answer | Date |
|---|---|---|---|
| Q1 | Steps 4, 7 | "Link in notes (Recommended)" — RELEASING.md §6: the APK is the asset; the notes carry the TestFlight public link and the iOS build number. No IPA asset. | 2026-11-11 |
| Q2 | Step 7 | "Wait for approval (Recommended)" — Step 7 starts only once Step 6's Log records Beta App Review approval. | 2026-11-11 |
| Q3 | Step 5 | "Crop to 1080×2160 (Recommended)" and "Real accounts (Recommended)" — check every shot for visible account names before upload. | 2026-11-11 |
| Q4 | D1 part (7), Step 5 | "Redraw for v2.0 (Recommended)" — Lane D draws the graphic on the Icon v3 mark; Robin approves. | 2026-11-11 |
| Q5 | A4 | "After v2.0 (Recommended)" — the Android half of CCRM-24 (Share Card) is not in v2.0; A4 is CCRM-110 + CCRM-112 only. | 2026-11-11 |
| Q6 | Step 1's close, A1, I1 | "Both at once (Recommended)" — Lanes A and I run side by side. | 2026-11-11 |

All six were answered by Robin in the orchestrating session on 2026-11-11 (AskUserQuestion, options quoted
verbatim) and relayed to the planning session, which recorded them. No answer adds or removes a step.

## Failure modes

- **A landing conflicts.** WORKTREE.md: the step aborts the merge, stops and reports; the orchestrating session
  sorts it out. The shared-file table should make this rare.
- **A fixture is wrong.** Spec leads: the orchestrator gives one session a spec-only fix; the other lane re-merges.
- **The Duet does not fit the real shade.** A2 builds to the wireframe's widths; Step 3 measures; a new width is
  a wireframe revision for Robin.
- **The sample leaks into real data.** A5's isolation tests; Step 3 checks the notification and widgets.
- **Play refuses something.** The AAB checks run before the tag; Play's rules are re-read on the day; a
  `specialUse` objection falls back to CCRM-67 (Pin Service), a new release on both channels.
- **Beta App Review or Play review is slow.** GitHub v2.0 does not wait for Play (CLOSED-TEST-PLAN.md); Q2
  decides whether it waits for TestFlight.
- **The tester count dips below 12.** Treated as restarting the 14 days; recruit 15–18.

## UNVERIFIED

The Duet's real collapsed widths on the cover and inner shades and its expanded column (≈186 dp is
arithmetic); iOS chip and segment widths; whether two worktree sessions building at once slow each other enough
to matter; whether a new closed-track build resets Play's 14-day clock (secondary sources say no); whether
Play's reviewer accepts the sample mode as App access; Beta App Review's timing.

## Tests and gates

- `./gradlew testGithubDebugUnitTest testPlayDebugUnitTest lintGithubDebug`, `assembleGithubDebug assemblePlayDebug` (every Android step, Steps 2–4)
- `swift test --package-path ios/Packages/CooldownCore` and the iOS app tests on the iPhone 17 Pro simulator (every iOS step, Steps 2–4)
- `sh spec/tools/check_no_code.sh` (Steps 1, 2)
- The fixture generator script re-derives `printed-v20.json` and the mobile fixtures (Step 1)
- `tools/widget_layouts.py` byte-identical (any step that changes a widget layout)
- The downgrade test for the tag key (A4, I3)
- The four sample-isolation tests (A5)
- One fresh Opus judge round per code step (1, A1–A5, I1–I3, 2, 3's fixes, 4)
- RELEASING.md §4 step 5 and §5's checks (Step 4 and 7); §6's archive checks (Step 4)
- Fable judge, Sol cross-check and Robin's recorded go before publishing (Step 7)
- The Play-signed APK's digest = the trusted digest (Step 8)
- The merge dry run (Step 0's Log)
