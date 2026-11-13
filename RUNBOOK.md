# Runbook — the v2.0 feedback build, then the v2.0 release and Play Day 0

**Plan status: FROZEN 2026-11-11.** Cross-family review: Astra at high, one full call, verdict **blocking**
with one finding (C1-1, no decision gates on Robin's open questions), fixed by Convention 10 and closed by a
named check (`reviews/v20-build-arc-gatecheck.py`, PASS). No delta call: no step added, no reversal changed.
Table: `reviews/v20-build-arc-findings.md`; Step 0's Log.

**Amended 2026-11-11 (Robin's three changes, after A1–A3 and I1–I3 had landed).** (1) A narrow Duet for a 360 dp
phone's shade, Android only: CCRM-114 (Narrow Duet), with CCBG-80 (Fixed Pace Columns) as a build note — a new Lane A
step **A6**, after A5. (2) The tag glyph before the provider logo on Summary rows and the snapshot, both platforms:
CCRM-115 (Row Tag Glyph), folded into **A4** on Android and a new Lane I step **I4** on iOS. (3) The Summary filter's
memory rule, the same on both clients — a spec rule, no wireframe. In the same stroke Robin **dropped "None"**: every
account is Personal or Work, chosen on the Add account sheet, all existing accounts Personal at upgrade. The
addendum `design/2026-11-11-v20-narrow-duet-and-row-glyph-wireframes.html` is **approved 2026-11-11** and is the
contract for A4, A6 and I4 beside rev B; Step 2 waits for A6 and I4. One Astra delta review: see "Amendment
review" below.

This runbook takes `main` as of 2026-11-11 (v2.0 code on main, versionCode 25 / "1.9", the nine feedback
items approved but not built) to a published **v2.0**: the GitHub release (APK, with the iOS build under the
same version), the TestFlight build, and Day 0 of the Google Play closed test.

**Items** (each on Android and iOS unless marked):
- CCRM-105 (Duet Stack) — Android only (iOS has no ongoing notification);
- CCRM-114 (Narrow Duet) — Android only, added 2026-11-11; CCBG-80 (Fixed Pace Columns) is its build note;
- CCRM-106 (7d Chip);
- CCRM-107 (Day Grid);
- CCRM-108 (Reset Named);
- CCRM-109 (Quiet Accounts);
- CCRM-110 (Account Tags) — Personal or Work only since 2026-11-11, plus the filter-memory rule;
- CCRM-115 (Row Tag Glyph), added 2026-11-11;
- CCRM-111 (Map Layers), which closes CCBG-79 (Leader Reads as Trail);
- CCRM-112 (Summary Snapshot);
- CCRM-113 (Sample Port) — Android, plus the iOS follow-up (the iOS sample swaps "Product" for a Gemini account).

**Carried in from [RUNBOOK-v2.0.md](RUNBOOK-v2.0.md):** its Steps 7 (device passes) and 8 (release v2.0) are
not ticked; this file's Steps 3 and 4–7 replace them (its Progress rows say so since Step 0), and Step 3 picks
up their open checks (listed there).
CCRM-104 (Pace Line Parity) and CCBG-78 (Collapsed Pace Line Clip) are built and wait for the device check in
Step 3. Earlier arcs are in git history: v1.9 at `1f8ee7b` (this file before it was replaced).

**The design.** `design/2026-11-10-v20-feedback-wireframes.html`, **approved rev B 2026-11-10** (amended by the
**addendum page of 2026-11-11**, which wins where they differ: no "None", the Add-sheet control, the row glyph, the
narrow Duet) (§1–§8 the
eight items, §9 the sample mode). Rules: BEHAVIOR-SPEC §6.3 "Planned — v2.0 feedback" (CCRM-107, 108, 109,
111), BEHAVIOR-MOBILE §7 (sample mode), ROADMAP.md CCRM-105 to CCRM-113, and the per-file deltas in
`spec/mobile/surfaces/README.md` § "Planned changes". The wireframe's §4 heading still says "since / until";
Robin chose **B, the pace line names the reset** — ROADMAP's CCRM-108 and core §6.3 are the rule.

## Closed decisions (Robin, 2026-11-04 to 2026-11-11)

1. All nine items ship in v2.0; v2.0 waits for their build and a second Fold 7 + iPad pass.
2. The Duet keeps its +/− sign. Collapsed Duet text is capped in dp at large font scales.
3. ~~Tags are optional (None · Personal · Work)~~ **replaced 2026-11-11 (decision 9).**
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
9. **No "None" (Robin, 2026-11-11).** Every account is Personal or Work. It is chosen on the Add account sheet
   (a Personal | Work control above the provider rows, Personal selected each time), the first account (which never
   passes the sheet) is Personal without asking, and every account that exists at the upgrade is Personal, silently
   (a missing stored tag reads as Personal). The control shows whenever the Summary exists. Same on both platforms.
10. **The tag glyph on Summary rows (Robin, 2026-11-11):** before the provider logo, both platforms; one column, it
    leaves every row at once when a name would fall under its floor; the snapshot draws it only while its rows carry
    both tags. BEHAVIOR-MOBILE §6.
11. **The filter memory (Robin, 2026-11-11: "ensure specs and behaviour is the same across iOS and Android"):** a
    remembered filter whose group has gone empty falls back to All **and is stored as All**; tagging later never
    switches it back. iOS I3 kept the old value; I4 changes it. BEHAVIOR-MOBILE §6.
12. **The narrow Duet (Robin, 2026-11-11: "Yes, wireframe it"):** variant C — under 256 dp of content a figure-less
    row gives its "—" to the state word. CCRM-114.

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
| Summary | CCRM-110, CCRM-112, CCRM-115 | the Summary header and rows, the account settings and Add sheet, the share card |
| Duet | CCRM-105, then CCRM-114 (A6) | the notification builder and its fit tables (Android only) |
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

**The Personal/Work tag fixtures (amendment 2026-11-11).** `spec/mobile/fixtures/tags/cases.json` still holds the
rev B optional-tag cases that iOS's I3 tests read; the new model's cases are in `cases-personal-work.json` (written
by `spec/tools/gen_v20_fixtures.py`, filter cases with `storedAfter`, and `memory` sequences). **A4 and I4 read the
new file**; I4 retires the old tag tests. Step 2 replaces `cases.json`'s content with the new file's and points both
clients back at `cases.json`.

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
7. **The wireframe is the contract** — rev B plus its 2026-11-11 addendum (`design/2026-11-11-v20-narrow-duet-and-row-glyph-wireframes.html`;
   where they differ the addendum wins). Anything visible that they do not draw, or draw
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
| A1 | Lines and labels: CCRM-106, CCRM-107, CCRM-108 | 1, Q6 | ☑ 2026-11-11 |
| A2 | Duet: CCRM-105 | A1 | ☑ 2026-11-11 |
| A3 | Pace map: CCRM-109, CCRM-111, CCBG-79 | A2 | ☑ 2026-11-11 |
| A4 | Summary: CCRM-110 (Personal/Work, filter memory), CCRM-112, CCRM-115 (row glyph) | A3, Q5 | ☑ 2026-11-11 |
| A5 | Sample: CCRM-113 | A4 | ☑ 2026-11-11 |
| A6 | Narrow Duet: CCRM-114, CCBG-80 (+ CCBG-81, Robin) | A5 | ☑ 2026-11-11 |

### Lane I — iOS (one step at a time; runs beside Lane A)

| Step | What | Gated on | Status |
|---|---|---|---|
| I1 | Lines and labels: CCRM-106, CCRM-107, CCRM-108 | 1, Q6 ("one lane at a time" → also A5) | ☑ 2026-11-11 |
| I2 | Pace map: CCRM-109, CCRM-111 | I1 | ☑ 2026-11-11 |
| I3 | Summary: CCRM-110, CCRM-112; the CCRM-113 iOS follow-up | I2 | ☑ 2026-11-11 |
| I4 | Row glyph and the Personal/Work model: CCRM-115; CCRM-110 converted; filter memory; the sample's Lab is Work | I3 | ☑ 2026-11-11 |

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
| 2 | Fold-in: fixtures and §6.3 promoted, surfaces folded, tracker closed | session · judge | A6, I4 | ☑ 2026-11-12 |
| 3 | Device pass 2: Fold 7 and iPad; defects filed and fixed | session · **Robin at the devices** | 2 | ◐ Fold 7 half 2026-11-12; iPad half and the iOS fixes in the iOS bug-fix session |
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

## Amendment review — 2026-11-11

Steps A6 and I4 were added and A4 and Step 2 changed, so the plan took its one delta call (cross-family review is ON):
Astra at high, `reviews/v20-build-arc-astra-call2.txt`, **accept**, coverage "changed text in full", **no new finding**;
the prior blocking finding C1-1 stays fixed (`reviews/v20-build-arc-gatecheck.py RUNBOOK.md` re-run on the amended
runbook: RESULT PASS). Table: `reviews/v20-build-arc-findings.md`. The plan stays **FROZEN**; a further step would need
an exception granted by Robin (model-mesh §5b).

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
- 2026-11-11 · **Correction** (spec only, on the orchestrating session's word, after the desktop ran the file): the
  quiet rule and the map's figure labels used half up, which §2 gives for credits; window rows truncate (Android
  `Palette.usageInt`, iOS `windowPercentDisplay`), and Robin's rule is "match the row". So a row prints "0%" below 1,
  not 0.5: 0.99 is hidden, 1.0 is plotted; the labels truncate ("Teams 51%" and "48% left" at 51.5). Core §6.3's
  planned sentence now reads "under 1: a window row truncates (§2's window bars)". The `quiet`, `mapLabels.figures`
  and `map-foot` cases were regenerated; the callout's "~N% at reset" stays half up (an estimate), spent stays 100%.
  No judge round (a one-rule correction applying Robin's existing call). Lane I's `PrintedFormV20Tests` reads only
  the pace-line blocks, which are unchanged. ROADMAP CCRM-109's What text and the wireframe §5 still say "under
  0.5"; neither is this step's to edit during the lanes (Step 2 rewrites the tracker lines).

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
- ☑ Unit tests on `printed-v20.json` (paceLine, paceLineForms, endToEnd, the grid) pass on both flavors, with
  lint, and the widget generator is byte-identical.
- ☑ Emulator captures: each chart with the grid (7-day and 5h, now-column bold, an ordinal yielding), the
  notification's collapsed and expanded pace line at the cover-like width showing "· Sun" for a reset days away
  (not "· 9:30 PM"), the tile and a widget's Weekly line.
- ☑ ROADMAP Status lines of CCRM-106, 107 and 108 read "Android built" with the commit; the Android sections
  of `app.md`, `foundations.md`, `notification.md` and `widgets.md` carry the delta.
- ☑ Judge round recorded; landed.

### Step A2 · Duet — CCRM-105

**Builds:** wireframe §1. Collapsed 52 dp: two 36 dp 5h rings on the left (First left, unsigned, account
colours), two stacked rows (14 dp mark, 12 sp name, right-aligned 11 sp state word; an 8 dp Weekly bar with tick
and red slice, both bars on one edge and length; an 18 sp bold tabular signed figure in a fixed 42 dp slot). The
narrow drop order: the bar absorbs, the name to its 24 dp floor, then the state word's tail. Expanded keeps both
headers, figure 36 → 24 sp. Collapsed text capped in dp at large font scales. The bare 14 dp ring retires.

**Done when:**
- ☑ Fit tests for the new width budget (136 dp chrome, no halves) and the drop order; both flavors' tests and lint.
- ☑ Emulator captures: collapsed and expanded at the three widths, font scale 1.0 and 2.0, both signs, one
  account spent, one with no 5h.
- ☑ CCRM-105 Status "Android built"; `notification.md` §2.1–§2.4 and §8 and `foundations.md` §2 deltas inside the
  Android sections.
- ☑ **UNVERIFIED until Step 3:** the real cover and inner shade widths. Write the measured emulator widths in the
  Log so Step 3 can compare.
- ☑ Judge round recorded; landed.

### Step A3 · Pace map — CCRM-109, CCRM-111, closes CCBG-79

**Builds:** wireframe §5 and §7. Accounts whose Weekly row prints "0%" leave the map; the foot line counts them
("2 at 0%", "· 1 no reading"); all at 0% keeps the frame with "Nothing to plot yet". The Figures · Trails ·
Projection chips (32 dp, defaults off/on/off, remembered, wrapping under the title at large text); the figure
after the name; the projection's dashed tail and hollow ring (red on the 100% line when it runs out); the last
24 h of each trail heavier; a tap pins the callout (others dim to 35%, "~74% at reset"), a second tap opens the
account; dotted leaders of at most 48 dp, the name not drawn when nothing is free.

**Done when:**
- ☑ Tests on `mapLabels` (the dense 200-wide case), the quiet rule, the callout forecast and the projection layer;
  both flavors' tests and lint.
- ☑ Emulator captures: each chip combination, the callout pinned, a dense map with a refused label, all quiet,
  two-column inner layout.
- ☑ CCRM-109 and CCRM-111 Status "Android built"; CCBG-79's status notes the Android fix (Lane A owns BUGS.md);
  `app.md` Android section.
- ☑ Judge round recorded; landed.

### Step A4 · Summary — CCRM-110, CCRM-112, CCRM-115

**Builds:** wireframe §6 and §8 **as amended by the 2026-11-11 addendum** (`design/2026-11-11-v20-narrow-duet-and-
row-glyph-wireframes.html`, §Row glyph and §Adding and tagging — read both pages; the addendum wins). **Personal or
Work only, no None:** the Add account sheet's "This account is" control above the provider rows (Personal selected
each time it opens, a scroll when the sheet outgrows the screen, "Try with sample data" ignores it, signing in again
keeps the tag; the first account, the built-in Claude card, is Personal); "Tag…" in the account menu after "Rename…"
(two radio rows, glyph + word); the neutral chip with the glyph and the word on **every** card in Settings › Accounts;
the All · Personal · Work control beside "Weekly" whenever the Summary exists (a segment with no accounts disabled; a
tap on it shows "No Work accounts yet. Tag one in Settings." for 3 s), remembered, filtering the list, the ranking,
the map, its foot-line count and the snapshot; "your order" becomes the sort menu of the four orders. **The memory
rule** (BEHAVIOR-MOBILE §6): a stored filter whose group is empty is replaced by `all` at once, and later tagging
never switches it back. **The row glyph (CCRM-115):** a person or briefcase before the provider mark on every full
row (14 dp + 4), between the rank disc and the name on a one-line row (12 dp + 4), the column leaving every row at
once when a name would fall under its floor (64 dp at its own text scale) or a one-line bar under 40 dp, measured on
the tightest row shown; TalkBack "Teams, Work account". "Share snapshot" in the Summary's ⋮: header with the filter's
name and time, the rows in screen order — the glyph column only while the rows on the card carry both tags — then the
map with the on-screen layers, never the chips or a callout; names, never e-mail addresses; the CCRM-24 (Share Card)
share dialog. Sample accounts' tags are Teams Work, Personal Personal, Lab Work (A5 wires them).

**Done when:**
- ☑ The tag preference is additive and downgrade-tested (a missing, empty or unknown value reads as Personal; an
  unknown value is written back untouched; v2.0 prefs read through v1.9's accessors give v1.9's defaults). Tests read
  `spec/mobile/fixtures/tags/cases-personal-work.json` — every `filter` case and every `memory` sequence replayed
  against the real store — plus the row-fit rule (pure, in `ui/SummaryRows.kt`) at 360 dp, the cover and the inner
  list, font scale 1.0 and 2.0; both flavors' tests and lint.
- ☑ Emulator captures (the addendum's states): the Add sheet with Personal and Work selected, at 360 dp and font 2.0;
  the chip on every card, the Tag dialog; the Summary after an "upgrade" (everything Personal, Work disabled) and
  with both tags, each filter, the disabled-segment line, the sort menu; the row glyph at the cover, inner and 360 dp
  widths, the glyph column dropped, one-line rows; the snapshot with both tags and with one (no glyph), the share
  sheet; dark and light.
- ☑ CCRM-110, CCRM-112 and CCRM-115 Status "Android built"; `app.md` Android section.
- ☑ Judge round recorded; landed.

### Step A5 · Sample — CCRM-113

**Builds:** wireframe §9 and BEHAVIOR-MOBILE §7. "Try with sample data" under the first screen's "No Claude
account yet" card and as the last row of Add account; three made-up accounts (Teams · Claude, Personal ·
ChatGPT, Lab · Gemini — tagged Work, Personal, Work, fixed at entry; "Tag…" edits the in-memory copy only); the violet "● Sample data — made-up accounts · Exit" banner on every tab, Settings and
History; held in memory, settings in an in-memory copy. Isolation: real polling carries on into the real stores;
only the app's screens read the copy; a sample edit binds to the sample store; the debug synthetic series pauses.

**Done when:**
- ☑ The four isolation tests from `reviews/v20-sample-mode-findings.md`: byte-identical real stores across
  enter and exit; an Appearance change in the sample leaves the live notification and a widget untouched; a
  Rename open across a switch never renames a real account; the synthetic flag suspended and restored, nothing
  written. Both flavors' tests and lint.
- ☑ Emulator captures: entry points, the banner on every tab, Settings, History, the share band "● SAMPLE DATA",
  and exit; a process kill returns to the real app.
- ☑ CCRM-113 Status "Android built"; `app.md`, `notification.md`, `widgets.md` Android sections.
- ☑ Judge round recorded; landed.
- ☑ (added) The Add-sheet sign-in rule: a cancelled or ended sign-in leaves no account and no tag; `AddSheetSignInTest`.

### Step A6 · Narrow Duet — CCRM-114, CCBG-80

**Builds:** the addendum's §N1 (`design/2026-11-11-v20-narrow-duet-and-row-glyph-wireframes.html`), Robin's variant
**C**, on top of A2's Duet. **F1:** under 256 dp of content (smallest width under 396 dp) a row with no figure ("Sign
in", "No reading", "Not started") gives its "—" to the state word, which sits right-aligned in the 42 dp figure slot,
the name line running 50 dp further and the bar keeping its length; new ladder rungs 4 and 5 (a bare word that still
leaves the name under 32 dp, or its own width, goes — "Held", "Out", "No reading", "Not started"; "Stale" leaves its
fault-red dot; "Sign in" never goes); the cover (272 dp) and wider keep rev B's "—"; TalkBack hears the full word
whichever rung printed. Expanded: the ring's time line sized in dp, "· Weekly" giving before the name. **F2 (a build
note, no wireframe; CCBG-80 (Fixed Pace Columns)):** the pace line's column widths come from the screen's smallest
width for **both** layouts — the single layout (142 / 141 dp) and the Duet's expanded header (186 dp) — by the
addendum's formula and table (content = `Duet.contentDp(sw)`, expanded +40, column = (content − ring box − gaps − the
figure's own width) × 0.965, sizes in dp from the system), never wider than today's constants until Step 3 has
measured the Fold 7; this restores the approved drop order ("· resets Wed 12:30 PM" → "· Wed 12:30 PM" → "· Wed" →
the floor).

**Done when:**
- ☑ `DuetTest` has a case for every cell the addendum draws (226 · 220 · 200 · 176 dp, font 1.0 and 2.0, the states of
  its grid); `TextFitTest` pins the derived-column table (360 · 411 · 420 · 750 dp at font 1.0 / 1.3 / 2.0) and the
  pace line dropping its weekday where the column is short, for both layouts; both flavors' tests and lint; the
  widget generator byte-identical.
- ☑ Emulator captures at 360 dp, the cover-like and inner widths, font 1.0 and 2.0, dark and light: the Duet collapsed
  and expanded with "Sign in", "No reading", "Not started", "Held", "Out", "Stale", a long name; the single layout
  collapsed and expanded with a signed figure; the "Sun 6:…" cut of A2's capture 25 gone.
- ☑ CCRM-114 Status "Android built"; CCBG-80 "Fixed" with the commit; `notification.md` Android section.
- ☑ **UNVERIFIED until Step 3:** One UI's real widths at 360 dp (assumed to match AOSP's 220); write the measured
  emulator widths in the Log.
- ☑ Judge round recorded; landed. Lane A's HANDOVER block now says "Lane A done".

**Lane A reversal:** each step is its own commits; `git revert` of them, newest first, restores the previous
step. A4's tag preference is additive, so a revert leaves only an unread key; A6 changes only the Duet's layout and fit code.

**Lane A Log:**

- **2026-11-11 · A1 Lines and labels (Android).** Code `bdbb90a` (CCRM-106 (7d Chip) "7d" chip with TalkBack
  "7-day, all models"; CCRM-107 (Day Grid) `ui/DayGrid.kt` in the in-app chart, share-card chart and Pace map, the
  map's note on a foot line; CCRM-108 (Reset Named) `PaceText.paceLineForms`), judge fixes and close-out in the
  following commit; both flavors' tests, lint and both assembles green; `tools/widget_layouts.py` byte-identical.
  Tests read `printed-v20.json` (`paceLine`, `paceLineForms`, `endToEnd`, `dayGrid`, `mapLabels.words`); `TextFitTest`
  pins the wireframe's §4 B fit table at 142/141/159 dp. **Captures** `design/research/2026-11-v20-build/A1/` (22, on
  fold7inner at 5556): cover-like dark (Summary, 7d chart with "3" yielding, 5h chart, under pace with "5" yielding,
  now-column "4" bold, shade collapsed/expanded reading "21h 49m ahead · Sun", widget Number 4×1/4×2 lines), cover
  light, Small (grid, no ordinals), time down, inner two stacks dark/light, 360 dp dark/light, share-card chart.
  **Judge** (fresh Opus @ high, product account): accept-with-fixes, no blocking. Adopted: F2 chip test (constants;
  the selected-state announcement is **UNVERIFIED** — a TalkBack check in Step 3), F3 wide-column `TextFitTest` row,
  F4 stale comments. Not adopted: F1 (docs in the code commit) — Convention 5 allows earlier code-only commits, the
  close-out commit carries the status lines and surfaces deltas. F5 accepted as is (wiring shown by captures 02/18).
  Rulings 4a–4d all "within the wireframe": time-down draws no ordinals (no axis strip, as Small); at 360 dp the
  map's "2" gives way to "week start" (core's end-label give-way); the share card draws ordinals, bold in now's
  column; `contentDescription` on the `FilterChip` keeps its selected semantics. **For the orchestrator:** the 360 dp
  map state (no "2") is undrawn in rev B though the rule covers it — worth a glance in Step 3. **Emulator notes:**
  seeded with `run-as` (`usage_cache.xml`, history JSONL) and a generated test token stored through
  `CredentialStore.save` with `jdb` (airplane mode on, nothing sent); afterwards both profiles signed out, the
  history files removed, network and screen size restored, emulator killed; the original `screen_off_timeout` was not
  recorded and is now 60000. The AOSP shade clips the collapsed card's third row vertically (capture 06; layout
  unchanged by A1; One UI showed it whole) — Step 3 checks it on the Fold 7.
- **2026-11-11 · A2 Duet (Android).** Code `abb323f` (CCRM-105 (Duet Stack): `notif_duet.xml` rewritten — two
  36 dp rings, two stacked rows, 42 dp figure slot; `Duet.kt` width budget, state words, `keepsTail`; expanded figure
  24 sp and `TextFit.DUET_EXPANDED_COLUMN_DP` 186), `2a97300` (nominal width from the measured shade chrome), judge
  fixes and close-out in the following commit; both flavors' tests, lint and both assembles green. **Fable fit calls**
  (Convention 11, fresh Fable via the product account, no departure): smallest-width classes; "Out · <clock>" for a
  reset later today; "—" and a bare track for Sign in / No reading / Not started in both views (notification.md §1.4);
  the synthetic marker kept. **Measured on the emulator (AOSP shade, for Step 3 to compare with One UI):** collapsed
  content 280 dp at 420 dp (bar 144), 402 at 750 (bar 265), **220 at 360 (bar 84)** — a phone's shade takes ≈140 dp
  (One UI's cover: 272 at 411, the v1.8 figure), so `Duet.contentDp` is smallest width − 140 below 600 dp and 352 from
  600 up (the wireframe's 328 for a 360 dp phone did not hold on AOSP); expanded header column 194 dp at 420, 315–328
  at 750, 133 at 360, ≈121 / 61 at font 2.0 (420 / 360); the AOSP collapsed slot shows ≈46 dp of the 52 at font 1.0
  (the second bar clipped; the same clip as A1's capture 06) and the whole 52+ at font 2.0. **Captures**
  `design/research/2026-11-v20-build/A2/` (34, fold7inner at 5556): cover-like 01–16, inner 17–23, 360 dp 24–34; dark
  and light; font 1.0 and 2.0; Vs pace both signs, Used, Left; held + spent, no 5h + not started, sign in + no
  reading, stale + 91%, a long name. **Judge** (fresh Opus @ high, product account): accept-with-fixes, no blocking.
  Adopted: F4 (the name-room arithmetic uses the padded word, so the decision and the layout agree; `DuetTest`), F5
  ("Held · <day clock>" when the 5h lets go after midnight — core §6.3's bare-clock rule; a stale spent week drops its
  tick too; `LeadLinesTest`). **For the orchestrator, not fixed here:** F1 (concerns) — at 360 dp on AOSP (220 dp,
  under the narrowest drawn 226) a word with no tail ("No reading", "Not started", "Sign in") leaves the name at ".."
  (captures 31, 33): undrawn, Robin's call (a wireframe delta for ≤ 226 dp); F2 (concerns) — the expanded column is
  one constant (186) while 360 dp AOSP measures 133, so the pace line ellipsizes ("Sun 6:…", capture 25) instead of
  dropping its weekday; A1's single-layout constants (142/141) have the same 360 dp gap, so the fix (columns keyed on
  the smallest width, a Fable fit call) belongs to one step for both layouts; F3 — the AOSP clip above, Step 3 checks
  One UI (blocking if One UI clips too). **Emulator notes:** seeded with `run-as` (`usage_cache.xml` with
  `pinnedSecondProfile`, `profiles.xml` labels) and the app's own start path; no credential was needed for the
  notification; airplane mode on throughout; afterwards both prefs files restored from copies, font 1.0, size and
  density reset, dark mode, network restored, emulator killed. Sweeper skipped: the session list returned exactly 50
  rows (WORKTREE.md rule).
- **2026-11-11 · A3 Pace map (Android).** Code `92b4244` (CCRM-111 (Map Layers) and CCRM-109 (Quiet Accounts): `ui/PaceMapRules.kt`
  quiet rule / foot line / figures / forecast / projection end / pin and hit test, `ui/MapLabels.kt` the placer (the same
  rules as iOS's, dp offsets equal the fixture's), `ui/PaceMap.kt` the card with the 32 dp chips, scene, trails with the
  heavier last 24 h, tails and rings, dotted leaders, callout and per-dot TalkBack nodes, `data/MapLayers.kt` + three new
  `paceMap.*` prefs, `SummaryScreen` plotted-only entries and the foot count), judge fixes and close-out in the following
  commit; both flavors' tests, lint and both assembles green; no widget layout changed. Tests read `printed-v20.json`
  (`quiet`, `mapLabels` words / figures / placement incl. every box, leader and refusal reason, `callout`,
  `projectionLayer`) and `spec/mobile/fixtures/map-foot`; the dp offsets the map always drew are the fixture's, so the
  port passed first time. **Captures** `design/research/2026-11-v20-build/A3/` (44, fold7inner at 5556): cover dark 02–24
  (all eight chip combinations 02–09; figures in Vs pace and Left 10–11; the callout on Teams, Gemini flipped left and
  below, spent Atlas, and a second tap opening Teams 12–15; a dense eleven-account map with figures and with all layers
  16–17; a stack of seven dots with refused names 18; two hidden, two hidden + one unread, all at 0%, nothing plotted
  with one unread, one account, no history 19–24), cover light 30–33, inner two-column 40–44, 360 dp 50–56, large
  text 52, 57, 60, 61 (chips wrap among themselves, the callout grows), the chip tap and its memory after a restart
  58–59. **Judge** (fresh Opus @ high, product account): concerns, no blocking. Adopted: F1 the pin was hidden, not
  dropped, when its account left the map (it would reopen later) → dropped by a `LaunchedEffect`, and the pin moved up
  to `SummaryScreen` so a fold keeps it; F2 the callout's rows were fixed dp and overlapped from font 1.3 → stacked from
  measured heights (captures 60–61); F3 an account with one sample drew a line where iOS draws none → `hasTrail` (the
  trail ends on the live point, so it needs two samples behind it; **departs from the shipped Android rule on purpose,
  to match iOS**, pinned by a test); F4 vacuous test branches made unconditional, the leader-length test now reads the
  placer, a ring test added; F5 `pointerInput` keyed on the targets, not the scene. Not changed: the y-axis "100%" is cut
  to "00%" at font 2.0 (the 30 dp left inset predates this step; Step 3 or a fix of its own). **Matches iOS where rev B is
  silent:** no trail for an account with no history; one TalkBack node per dot on its 48 dp target; the all-at-0% message
  is announced as the map's state; nothing plotted with some accounts unread shows the empty frame and the count, no
  message. **Wireframe calls, all within rev B:** the chips are 32 dp *including* their touch target (Material's 48 dp
  minimum is not applied, so the header is the drawn 32 dp; they are switches for TalkBack, iOS marks them selected buttons);
  a reading of 1% or more with no reset clock is no longer plotted on the origin but counted as no reading (the foot-line
  fixture's rule); "even pace" also keeps off rings. **For the orchestrator:** nothing undrawn stopped the step; the
  wireframe's "Largest font" drawing shows only the chips, the map text at font 2.0 follows the existing sp scaling.
  **Emulator notes:** a scratch debug-only `SeedActivity` (not committed) seeded the registry, snapshots, history and fake
  credentials in-process and started `MainActivity` (airplane mode on); afterwards the app was cleared and uninstalled,
  size and density reset to 411, font 1.0, stay-awake off, airplane mode off, emulator killed. Sweeper skipped: the
  session list was not read (a landing step carries it, see WORKTREE.md).

- **2026-11-11 · A4 Summary (Android).** Code `e2570e5` (CCRM-110 (Account Tags): `data/AccountTag.kt` the tag and the filter's rules,
  pure; `Profile.tagRaw` + the registry's additive `"t"`; `UsageCache.summaryFilterStored/settleSummaryFilter/tag`;
  `UsageRepository` add / tag / remove run the memory rule; `ui/AccountTags.kt` glyph, chip, dialog, the Add-sheet control and
  the header control; Settings chip on every card, "Tag…", the scrolling Add sheet; the Summary header with the control and
  the sort menu. CCRM-115 (Row Tag Glyph): `ui/SummaryRows.kt` `fullKeepsGlyph` / `oneLineKeepsGlyph`, one decision per list.
  CCRM-112 (Summary Snapshot): `share/SummarySnapshot.kt` (the screen's own map painter drawn into the card), the All tab's
  ⋮ "Share snapshot"), judge fixes and close-out in the following commit; both flavors' tests, lint and both assembles
  green. Tests read `cases-personal-work.json` — every `filter` case and every `memory` sequence replayed against the real
  store (registry + `UsageCache` prefs + `UsageRepository` add / tag / remove, a plain-file credential backend) — plus the
  glyph fit rule, a v1.9 registry-decode downgrade test and the snapshot render. **Captures**
  `design/research/2026-11-v20-build/A4/` (36, fold7inner at 5556): cover dark 01–19, 29; 360 dp 20–24; cover light 25;
  inner 750 dp 26–28 — all states of the addendum: the Add sheet with Personal and Work, the chip on every card, "Tag…", the
  Tag dialog, the upgrade Summary (Work disabled) and its 3 s note, each filter, the sort menu, one-line rows, the glyph
  column dropped (one-line list at 360 dp, 288 < 295), the snapshot with both tags / one tag (no glyph) / one line each /
  filter Work, font 2.0 header and Add sheet. **Judge** (fresh Opus @ high): concerns, nothing blocking. Adopted: F1 a
  segment with no accounts is no longer `disabled()` in semantics (a disabled node has no click, so TalkBack could not reach
  the note; T3), F2 the chip's "change" label moved to `clickable(onClickLabel)` and `selectableGroup()` on the dialog and
  the control (the "1 of 2"), F3 the snapshot's glyph and mark dim with a faded row, F4 the downgrade test now decodes with a
  verbatim v1.9 `decode` (incl. `"t":"family"`) and reads the v2.0 prefs through v1.9's keys, and the height test checks the
  sub-line inside its row, F5a the ⋮'s visibility follows the stored filter. Not changed: F5b (the spent snapshot row prints
  "Out until …" left and "Resets …" right, as the per-account card and §8's Atlas row do), F5d (measured, see below).
  **For the orchestrator — fixture:** the `filter` case "one account" has `storedAfter: "all"` for a remembered `work`, but the
  `memory` case "a second account added to a single-account install" expects the value to stay `work` while there is no
  Summary; the build follows BEHAVIOR-MOBILE §6 ("evaluated only while the Summary exists") and the test skips that one
  `storedAfter` — Step 2 should fix the generator (iOS I4 will meet it too). **Wireframe calls, all within the addendum:**
  accounts counted for the filter are the signed-in ones (the Summary's tabs list only those; an Add-sheet account is
  created before its sign-in, as shipped, so it counts once signed in); the snapshot's "as of" is the oldest reading shown;
  a held row reads "Held · back at …" (not drawn); the Add sheet's glyphs stay at font 2.0 on AOSP (the control measures its
  word: "Personal" 103 dp of the 160 dp half — AOSP's non-linear scaling; **UNVERIFIED on One UI**, Step 3); the filter
  control shares the width by word length under large text so "Personal" is never cut; the control, sort button and Add
  sheet grow rather than clip at font 2.0 (28 / 28 / 40 dp are the scale-1.0 minimums). **Orchestrator's two points:** (1)
  Android creates the account on the Add-sheet row tap, before the browser or code sheet opens (the shipped CCRM-56 flow, and
  the addendum draws it: "the account is created with the tag, and its card shows the chip before the browser … opens"), so
  a cancelled or failed sign-in **leaves a signed-out card holding its tag** — unlike iOS, which creates the account on
  success. Changing that is a design change; not done, Robin's call. (2) The control's TalkBack label stays the
  wireframe's "This account is" (segments "Personal, selected, 1 of 2"), not Desktop's "Tag for the new account, Personal":
  the addendum's TalkBack paragraph fixes it. **Emulator notes:** a scratch debug-only `SeedActivity` (never committed)
  seeded the registry, snapshots and fake credentials (airplane mode on); afterwards the app was cleared, size / density /
  font reset, dark mode off, airplane mode off, emulator killed. Sweeper skipped: the session list was not read.
- **2026-11-11 · A4 fixture fix (spec-only, at the orchestrator's request after Desktop's replay).** `spec/tools/gen_v20_fixtures.py`
  set a `filter` case's `storedAfter` to the filter in force even with fewer than two accounts; with no Summary the memory rule
  does not run (BEHAVIOR-MOBILE §6) and the value stays as it was, as memory sequence 6 already said. Fixed in `build_tags_pw()`
  and the file's note; regenerated, `--check` passes; **only the "one account" case changed** (`storedAfter` all → work), the
  other ten `filter` cases and every `memory` sequence are byte-identical, and the other three generated files are unchanged.
  Android needed no code change (A4 already left the value alone below two accounts); `AccountTagsTest` now asserts
  `storedAfter` for every case, the special case removed. `check_no_code.sh`, both flavors' unit tests and `swift test` pass.

- **2026-11-11 · A5 Sample (Android).** Code in this step's commit (CCRM-113 (Sample Port): `data/SampleMode.kt` the flag and the
  three accounts, `data/MemoryPrefs.kt`, in-memory twins of the cache / registry / history / session log, `UsageRepository`'s
  sample variant (no credentials, never fetches), `ui/SampleUi.kt` banner, first-screen entry and Add-sheet row; the root
  re-keys the tree and hands it the sample repository; Settings sample cards, Alerts card, Appearance caption, hidden More
  items; share bands "● SAMPLE DATA"). Both flavors' tests, lint and both assembles green; `tools/widget_layouts.py --check` ok.
  **The four isolation tests** are in `SampleModeTest` (C1-1 byte-identical real prefs + files across enter / use / exit, and a
  real poll landing during the sample; C1-2 a real notification stays posted and the real settings unchanged after a sample
  Appearance change; C1-3 a Rename held across exit reaches only the sample copy; C1-4 the debug series suspended, restored,
  a second enter safe) plus the sample's contents, and `SampleBannerFitTest`. **Added fix (Robin, both platforms):** an Add-sheet
  account is created provisional and removed on Cancel (Claude, Gemini), on dismissing the ChatGPT code sheet, or by the launch
  sweep (`UsageRepository.abandonProfile` / `settleProvisional`); no account and no tag remain; `AddSheetSignInTest` (8 cases:
  every provider × tag, slots never reused, a saved token keeps it, a racing dismiss keeps it, the sweep and its one-hour limit).
  **For Step 2:** reword BEHAVIOR-MOBILE §6's "the row tap commits the account with that tag" as "the account is created with
  that tag only when its sign-in succeeds" (spec files are read-only for the lanes). Also: BEHAVIOR-MOBILE §7 and the iOS
  paragraph differ on settings — iOS's sample says display settings "apply and persist", §7 says an in-memory copy dropped on
  Exit (Android follows §7); worth one line when the iOS surfaces are folded. **Captures**
  `design/research/2026-11-v20-build/A5/` (33, fold7inner at 5556): cover dark 01–19 (first screen and entry, the All tab and each
  account tab with the banner, Settings Accounts / Alerts / Appearance / More, History, the share snapshot "● SAMPLE DATA", exit,
  process kill returning to the real app, the Add sheet and its sample row, entering with two real accounts present, an
  Appearance change to Light inside the sample dropped on exit with the real dark accounts back), 360 dp dark 20–25 (font 1.3
  drops the banner tail, font 2.0 keeps "● Sample data" and "Exit"), light 26–30, inner 750 dp 29–32. **Judge** (fresh Opus @
  high): concerns, nothing blocking. Adopted: F1 the C1-2 test now posts the user's real notification and asserts it survives
  (it could not fail before; the widget half is the same seam, `Surfaces.refresh`, and is not separately asserted); F2 the
  POST_NOTIFICATIONS request moved out of the re-keyed tree so entering or leaving never prompts again; F3 the sample exits on
  a real-surface launch only when `savedInstanceState == null`, so a dark-mode or font recreation keeps it; F4 `abandonProfile`
  runs under the repository lock; F5 the sweep also takes a provisional account with no token older than one hour whatever is
  pending (whether an abandoned browser trip counts as ended is Robin's call). Not changed: F6 (the byte-identity harness
  scans prefs and `filesDir`, not the external AppLog; the AppLog line is unreachable from the sample's UI). **UNVERIFIED:** F2 /
  F3 were fixed after the capture pass and not re-run on the emulator; the banner's 48 dp "Exit" target inside the wireframe's
  36 dp band (a drawn-behind band in a 48 dp row, 6 dp more layout above and below); One UI. **Defect filed:** CCBG-81
  (History Tab Wrap, "Personal" wraps at 360 dp in History's fixed strip, capture 23). **Wireframe calls, all within §9 and the
  addendum:** the sample's settings are seeded from the user's Appearance / Summary settings; the first screen's card still
  reads "No Personal account yet" (the registry's first account, unchanged); the sample's Teams reads Max (rev B), not iOS's
  "Team". **Emulator notes:** a scratch debug-only `SeedActivity` seeded two real accounts for the entering-with-real-accounts
  captures (never committed, deleted); afterwards app data cleared, size / density / font reset, dark mode off, airplane mode off,
  emulator killed; `screen_off_timeout` was already 2147483647 and untouched. Sweeper skipped: the session list was not read.

- **2026-11-11 · A6 Narrow Duet (Android).** Code `3ab1181` (CCRM-114 (Narrow Duet), addendum §N1 variant C: `Duet.rowFit` rungs 4
  and 5 — under 256 dp of content a row with no figure gives its slot to the word, the figure view gone and the bar padded 50 dp;
  a bare word that would leave the name under min(32, its width) goes, "Stale" leaving its fault-red dot, "Sign in" never; TalkBack
  hears the full word on the word view, or "<name>, <word>" on the name. Expanded: the ring's time line in 11 dp, "· Weekly"
  gives before the name, the spent line drops " · in …". CCBG-80 (Fixed Pace Columns): `TextFit.collapsedColumnDp` /
  `expandedColumnDp` / `fitDp`, sizes via `TypedValue.applyDimension`, capped at 142 / 141 / 186. CCBG-81 (History Tab Wrap):
  `ui/ProfileTabStrip.kt`, Main and History), judge fixes and close-out in the following commit; both flavors' tests, lint and
  both assembles green; `tools/widget_layouts.py --check` ok. **Tests:** `DuetTest` pins all **109** cells the page draws (the
  four widths × font 1.0 / 2.0 × its two grids, plus the 272 / 253 / 220 trio), each generated from the page's own `decide()`
  and matching word, slot, dot and name room; `TextFitTest` pins the derived-column table (360 · 411 · 420 · 750 dp × 1.0 / 1.3 /
  2.0, all three columns, AOSP's non-linear table as the test's `SpToDp`) and the form the pace line takes in each — matching
  the page's `cols()`; `TabStripFitTest`. **CCBG-81:** Robin, asked with options, chose **B** (both strips scroll when a label
  will not fit) over the recommended A, then approved B as captured (`design/2026-11-11-ccbg81-history-tab-strip.html`, before /
  A / B captures in `A6/ccbg81/`); approved side effect: on the cover and the inner screen Main's strip is fixed whenever its
  labels fit. **Captures** `design/research/2026-11-v20-build/A6/` (76 + 23 strip shots, fold7inner at 5556, AOSP shade, each
  collapsed and expanded): 360 dp dark font 1.0 01–08 (Vs pace both signs; Held · Out; no 5h · Not started; Sign in · No reading
  in the figure's slot; Stale · 91%; a long name beside Held; the single layout, signed figure, short and long name), 360 dark
  font 2.0 09–15, 360 light 16–19, cover-like dark 20–27 (rev B's "—" kept, "Out · Sat" keeps its tail), cover font 2.0 28–30,
  cover light 31–32, inner 33–38. The "Sun 6:…" cut of A2's capture 25 is gone: the 360 dp Duet header reads "21h 50m ahead ·
  Mon" (01). **Measured emulator widths (AOSP, for Step 3 against One UI):** collapsed Duet content / bar **220 / 84 dp at
  360**, 280 / 144 at the cover-like 420, 402 / 265 at the inner 750 — as A2. **Judge** (fresh Opus @ high, product account):
  concerns, nothing blocking. Adopted: F1 (no notification captures yet — taken, above); F2 ("· Weekly" was weighed against the
  column only, so on a wide shade a Duet label passed and was then cut inside its 142 dp maxWidth, "Personal Max 20x · …",
  inner capture 35 before the fix — now weighed against min(column − 18, the label's bound), `TextFitTest`; re-shot 28, 35, 37);
  F3 (a "Stale" that goes now shares the row's one dot view with an update dot instead of counting a second, `DuetTest`; the
  row's own fault colours it, as the expanded header already ranks fault over update). Not changed: F4 (older than A6: on a
  SystemUI reapply the word colour, the stale alpha and the label's maxWidth are not reset — new RemoteViews are built each
  update; worth a look in Step 2 or 3), F5 (Convention 5: the status lines and surfaces deltas ride in the close-out commit).
  **Wireframe calls, within §N1:** rung 5 is read as the page's text says, for a *bare* word — the page's script would also drop
  a still-tailed "Held · 3:40 PM" whose name room falls in [24, 32), which no drawn cell reaches (a 400 dp phone would); the
  judge accepted the reading. The spent line's second-clause drop and "· Weekly" giving are applied to both layouts, as the page's
  `xh()` / `sgl()` draw them. At font 2.0 on a 360 dp phone the Duet line ends in the stated last resort ("23h 31m be…", N1-Q8
  (a)) and the single collapsed line is cut (the page's own caveat). **For Step 2:** (1) check whether the iPhone 17e's
  History strip wraps "Personal" the same way (Lane I is closed; any iOS fix is Step 2's, and BEHAVIOR / surfaces should then
  state the measured-label rule for both platforms); (2) `spec/mobile/surfaces/app.md` §1's "Fit" row and the All tab's
  "fixed ≤ 3" sentence are superseded on Android by A6's section — fold them; (3) the judge's F4 above. **UNVERIFIED until
  Step 3:** One UI's real widths at 360 dp (assumed AOSP's 220), expanded = collapsed + 40 on One UI, One UI's font curve.
  **Emulator notes:** a scratch debug-only `SeedActivity` (never committed, deleted) seeded registry, snapshots, plans and fake
  credentials (airplane mode on); afterwards the app was cleared and uninstalled, size / density / font reset, dark mode off,
  airplane mode off, stay-awake off, emulator killed; `screen_off_timeout` was set to 2147483647 without recording the old
  value. Sweeper skipped: the session list was not read.
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
- ☑ `swift test` on the v20 fixtures and the app tests pass.
- ☑ Simulator captures of each chart and pace-line surface, including the iPhone 17e's narrowest Summary row.
- ☑ The "iOS:" lines of CCRM-106, 107 and 108 read "built" with the commit; the iOS sections of `app.md`,
  `foundations.md` and `widgets.md` carry the delta.
- ☑ Judge round recorded; landed.

### Step I2 · Pace map — CCRM-109, CCRM-111

**Builds:** wireframe §5 and §7 on iOS (chips 13 pt, wrapping at large text; the callout; leaders; quiet
accounts and the foot-line count). Fixes the leader defect of CCBG-79 on iOS; the bug's own status is written in
Step 2.

**Done when:**
- ☑ `swift test` on `mapLabels`, quiet, callout and projection cases; the app tests pass.
- ☑ Simulator captures: each chip combination, the callout, a dense map, all quiet, the iPad two-column Summary.
- ☑ The "iOS:" lines of CCRM-109 and CCRM-111 read "built"; the iOS section of `app.md`.
- ☑ Judge round recorded; landed.

### Step I3 · Summary — CCRM-110, CCRM-112, and the CCRM-113 iOS follow-up

**Builds:** wireframe §6 and §8 on iOS (the tag on the account page and by long-press; the filter control; the
sort menu; "Share snapshot" in the Summary's ellipsis menu), and the iOS sample's swap of "Product" (Claude) for a
Gemini account, so both stores' review notes describe the same three accounts.

**Done when:**
- ☑ The tag key is additive and round-trips through the v1.9-era reader; filter, sort and snapshot tests;
  `swift test` and the app tests pass.
- ☑ Simulator captures: tags, filters, the sort menu, the snapshot and share sheet, the sample's three accounts.
- ☑ The "iOS:" lines of CCRM-110 and CCRM-112 and the "iOS follow-up:" line of CCRM-113 read "built"; the iOS
  section of `app.md`.
- ☑ Judge round recorded; landed.

### Step I4 · Row glyph and the Personal/Work model — CCRM-115, CCRM-110 converted, the filter memory

**Builds** (added 2026-11-11; the addendum `design/2026-11-11-v20-narrow-duet-and-row-glyph-wireframes.html`, §Row glyph
and §Adding and tagging, is the contract beside rev B §6 and §8): **(1) The model.** `AccountTag` loses None —
`Account` carries a tag that is always Personal or Work (a missing, empty or unknown stored value reads as Personal;
the raw string still survives a save, so the downgrade and unknown-value tests stay); `TagPickerRows` has two rows;
`setTag` takes a tag; the touch-and-hold "Tag" submenu's icon is always the current glyph; the Settings chip and the
account page's value show on every account. **(2) Adding.** The Add account sheet gains "This account is" with a
Personal | Work control above the provider rows (Personal selected every time it opens, not remembered); a pending tag
rides with the sign-in and is written when the account is created, ignored for "Sign in again"; the first-launch
buttons leave Personal; "Try with sample data" ignores it. **(3) The Summary.** The All · Personal · Work control shows
whenever the Summary exists; **the memory rule** (BEHAVIOR-MOBILE §6): a stored `summaryFilter` that is not in force
is rewritten to `all` (I3 left it), evaluated while the Summary exists, and in the sample against its copy only; a tap
on a disabled segment shows "No Work accounts yet. Tag one in Settings." for 3 s. **(4) The row glyph (CCRM-115):** a
person or briefcase before the provider mark on every full row (a 14 pt box + 4), between the rank disc and the name on
a one-line row, the column leaving every row at once by the fit rule (the name's floor 64 pt, a one-line bar under
50 pt), VoiceOver "Teams, Work account"; the snapshot draws it only while its rows carry both tags. **(5)** The
iPhone 17e Settings row order of the addendum (T4); the sample's Lab is **Work** in every demo scenario; "plan name"
in the Claude sign-in footnote (T7).

**Done when:**
- ☑ CooldownCore tests read `spec/mobile/fixtures/tags/cases-personal-work.json` (every `filter` case with
  `storedAfter`, every `memory` sequence replayed against the real store); I3's tests on the optional tag are
  retired in the same commit; the downgrade and unknown-value tests pass; `swift test` and the app tests pass.
- ☑ Simulator captures (the addendum's states) on the iPhone 17 Pro, iPhone 17e and iPad Pro 13″ (both orientations),
  dark and light: the Add sheet with each choice, the chips on every row (the 17e's row order), the Tag row and
  submenu without None, the Summary after an "upgrade" (everything Personal, Work disabled), both tags, each filter,
  the glyph on full and one-line rows, the glyph column dropped, the snapshot with both tags and with one.
- ☑ The "iOS:" lines of CCRM-110, CCRM-112 and CCRM-115 read "built"; CCRM-113's iOS follow-up notes Lab Work; the iOS
  section of `app.md`.
- ☑ Judge round recorded; landed. Lane I's HANDOVER block now says "Lane I done".

**Lane I reversal:** as Lane A: `git revert` of the step's commits, newest first.

**Lane I Log:**

- 2026-11-11 · **Step I1 done** (worktree `peaceful-aryabhata-f425f7`, PR #38). Code 7d479ba: CooldownCore `PaceLine.Drop`
  (none → word → clock → floor: "· resets Wed 12:30 PM" → "· Wed 12:30 PM" → "· Wed" → the floor; later today one fewer)
  and a new `DayGrid` (columns, the now column by integer maths, ordinals that give way); `FitLine` takes four variants;
  `TrendChart` draws the grid (`line` 60%, 0.5 pt, after the wash, under the guides) and the ordinals on every 7-day and 5h
  chart (Small: grid only; Time down: ordinals down the right edge); the Pace map draws sevenths, "week start", 2…6,
  "reset", and the note moves to its foot line; the tile chip and `WeeklyCard`'s half read "7d" (VoiceOver "7-day, all
  models"). Tests: CooldownCore reads `printed-v20.json` for `paceLine`, `paceLineForms`, `endToEnd`, `dayGrid`
  (`PrintedFormV20Tests`); the three old printed.json tests those replace moved there; `swift test` 198 passed, app tests
  98 passed (iPhone 17 Pro). Docs 2047cd9: ROADMAP "iOS:" lines, the iOS sections of `app.md`, `foundations.md`,
  `widgets.md`. **Captures** `design/research/2026-11-v20-build/I1/`: `iphone17pro/` (tile 7d and 5h charts, Summary, Pace
  map; light and dark), `iphone17e/` (Summary light/dark incl. compact density, tile), `ipadpro13/` (portrait and
  landscape, light and dark), `render/` (36 widget, lock-screen, Live Activity and Island faces from `SurfaceRenderTests`,
  e.g. `la-single` "21h 50m ahead · Sun", `bars-se` the floor, `dc-down-overview` the Time-down grid). The app's fonts are
  fixed sizes, so Dynamic Type does not move the drop; the drop is shown by the narrow faces and pinned by
  `PaceLineFitTests` at 100–300 pt.
- 2026-11-11 · **Judge** (fresh Opus, high, worker on claude-product): **accept with concerns**, no blocking. F1 (concern)
  the dayGrid horizontals had no count check → added. F2 (concern) the endToEnd spent case passed with no assertion, and
  nowX was checked against the test's own formula → the spent case now asserts `Pace.isSpent`, the nowX self-check is
  gone (the fixture's nowX feeds `DayGrid`). F3 (nit) ROADMAP's CCRM-106 line claimed the classic card's half reads "7d"
  though `WeeklyCard` has no caller since CCRM-103 (Hybrid Notification) → the line says so. F4 (nit) see the defect
  below. Fit calls (a)–(c) accepted. Fit call (a), the Time-down ordinals down the right edge (rev B says only "the grid
  transposes"), went to a **Fable judge** (Convention 11): **keep** — the ordinals belong to the axis strip, which Time
  down moves to the right edge; rev B reserves "no ordinals" for Small. Not a departure.
- 2026-11-11 · **Defect for Step 2 to file** (Lane I never edits BUGS.md): on iOS the pace line's clock prints "AM/PM"
  (core `Fmt.dayClock`, per the fixtures) while the older iOS lines built by `CardContent.clock` print "am/pm", so the open
  tile now shows "resets Sun 10:17 AM" right above "Resets at Sun 10:17 am" (`iphone17pro/teams-tile-light.png`).
  Pre-existing since CCRM-104 (Pace Line Parity); the new "resets" word makes it easier to see. Severity Low.
- 2026-11-11 · **Step I2 done** (worktree `jovial-mendel-a0b95c`, PR #42). Code 42ad02e, judge fixes and docs in the
  close-out commit. CooldownCore `PaceMapRules` (quiet: the row's truncated "0%" leaves the map, 0.99 hidden, 1.0
  plotted; the mobile foot line "N at 0% · N no reading"; the figure after a name in Used, Left and Vs pace; the callout's
  forecast; the projection end) and `MapLabels` (the core placement with 24/36/48 dotted leaders in eight directions;
  nothing free = no name), on `printed-v20.json` `quiet`, `mapLabels`, `callout`, `projectionLayer` and
  `spec/mobile/fixtures/map-foot` (`PaceMapV20Tests`, every case incl. boxes, leaders and refusal reasons). App
  `ios/App/UI/PaceMap.swift` replaces `PaceMapLayout`: chips (13 pt capsules, remembered, wrap under the title),
  trails with the heavier last 24 h, dashed tails to rings (red on the 100% line), the pinned callout (others 35%, tap
  again opens, empty plot clears), VoiceOver per dot, the foot-line count and the all-at-0% message; `-demo
  map|dense|quiet|hidden` for the simulator. Tests: `swift test` 206 passed, app tests 106 passed (iPhone 17 Pro),
  gradle suites green. **Captures** `design/research/2026-11-v20-build/I2/`: `iphone17pro/` (each chip combination, the
  callout on Teams light/dark and on Gemini flipped left and below, dense ten, all at 0%, two hidden + one unread),
  `iphone17e/` (defaults, all three, dense scrolled, all at 0%), `ipadpro13/` (the two-column Summary, portrait and
  landscape), `render/` (34 faces from `PaceMapTests`: every layer combination in Used/Left/Vs pace, three callouts, the
  four quiet states, dense ten, 320 and 300 pt widths with the chips wrapped, the iPad square).
- 2026-11-11 · **Robin's answer, recorded here** (the answers table has no row for it): a spent account in Left mode on
  the map reads **"Teams 0%" (as approved)** — the drawing and the fixture. Built so.
- 2026-11-11 · **Judge** (fresh Opus, high, worker on claude-product): **concerns**, no blocking. F1 (concerns) an
  account with no history got a made-up trail from the origin → the shipped `trail.count > 1` guard is back, pinned by
  `PaceMap.trail` and a test (the render's spent Atlas now has a dot only). F2 (concerns) each dot's VoiceOver element
  was positioned before it was made an element, so it could span the map → the element is the 48 pt frame, `.position`
  last (checked by reading SwiftUI's modifier order; not checked with Accessibility Inspector). F3 (concerns) the
  nearest-wins and name-tap checks could pass vacuously and the pin transitions were untested → two dots 11 pt apart
  must each win their side; `PaceMap.tap` and `keepPin` are pure and test all transitions. F4 (nit) the all-at-0%
  message was invisible to VoiceOver → the map's accessibility value. F5 (nit) one `clocked` term in the core `quiet`
  test never decides → not changed; the map-foot "Odd 3.0" case covers it. F6 (nit) no fixture between 99.5 and 99.99
  (spent prints "100%" where the row truncates to 99%) → for Step 2 below.
- 2026-11-11 · **For Step 2** (spec and tracker text, not defects): (a) ROADMAP CCRM-109's What text and wireframe §5 still
  say "under 0.5"; the fixture and §6.3 truncate (as told, left for Step 2). (b) The §5 drawing's empty message reads
  "every account is under 1% this week"; the fixture and the annotation say "at 0%", built so. (c) A `quiet`/`figures`
  case at 99.7 (spent, "100%"; the row prints 99%) would pin judge F6. (d) A map with nothing plotted where some accounts
  have no reading is not drawn in rev B (the fixture says so): iOS shows the empty frame with the foot-line count and no
  message, as before I2.
- 2026-11-11 · **Step I3 done** (worktree `jovial-mendel-a0b95c`). Code a43afb4, judge fixes and docs in the close-out
  commit. CooldownCore `AccountTag` / `SummaryFilter` (`AccountTagsTests` reads `spec/mobile/fixtures/tags`, all eight cases);
  `Account.tag` is a `String?` (additive: v1.9's `Account` shape reads a tagged file, and what it writes back reads as None; an
  unknown value like "family" survives a round trip; `SummaryTagsTests.downgrade`, `.unknownTag`). App: `AccountTags.swift`
  (glyphs, chip, picker rows, the filter control, the sort menu), `Summary.swift` (`SummaryEntry.current` filters the accounts
  before ranking, so the list, the rank numbers, the map, its foot-line count and the snapshot read the same rows; the one-line
  form follows the rows shown), `SummarySnapshot.swift`, the Tag row and submenu, "Share snapshot" on the All tab. The sample is
  Teams · Personal · Lab (Gemini, Pro) with the tags of rev B §9; `-demo untagged|tagsdisabled|noreading` and a tagged `map` /
  `dense` / `signin` seed the simulator. Tests: `swift test` 210 passed, app tests 121 passed (iPhone 17 Pro). **Captures**
  `design/research/2026-11-v20-build/I3/`: `iphone17pro/` (Summary untagged / All / Work filter / disabled segment / six / ten,
  sort menu open, Work + "most room first", Settings chips, account page row and picker, touch-and-hold Tag submenu, snapshot
  sheets incl. the system share sheet and "Nothing to share yet", the sample's three accounts; light and dark where a launch
  argument gives both), `iphone17e/` (Summary, Settings, snapshot, a warning word beside both chips), `ipadpro13/` (the two-column
  Summary portrait and landscape, snapshot sheet), `render/` (the snapshot card in every state the wireframe draws, Left and Vs
  pace, the header at 393 / 320 / 280 pt, the chips; the sort menu is drawn as its label there, `ImageRenderer` cannot draw a `Menu`).
- 2026-11-11 · **Judge** (fresh Opus, high): **accept with concerns**, no blocking. F1 (concern) the snapshot sheet's wiring was
  untested → `SummarySnapshotSheet.make(model:filter:order:layers:)` is tested (title, order name, rows, layers = the ones on
  screen, "as of" = the newest reading shown, nil without a reading). F2 (concern) the sample-isolation test could not fail →
  it asserts the demo store wrote no accounts file, and a new test runs `SampleStore.begin()`/`end()` around the filter key.
  F3 (concern) "Signed in" shrinking beside two chips also clipped the amber warning words → a warning word outranks the name
  (`layoutPriority`); checked on the iPhone 17e with a signed-out Work-tagged Lab (`iphone17e/settings-warning-beside-chips`).
  F5 (nit) the Tag row's menu label read symbol names to VoiceOver → one label ("Work" / "None"). F4 (nit) the remembered filter
  stays in `Prefs` after it fell back to All, so untagging the last Work account and tagging another later snaps back to
  Work: **Robin's call**, left as built (the rev B wording reads either way). Fit calls (a)–(h) accepted: (a) the sort button
  drops to its glyph when the order's name does not fit, then the control wraps under the title; (b) Settings' status word
  shrinks and may truncate ("Signed…" on the 17e, never a warning); (c) the snapshot's "as of" is the newest reading shown, in
  the account card's own form; (d) one-line snapshot rows use the short word; (e) a held row says "Held · 3:28 pm", a signed-out
  row "Sign in again" at 50%; (f) a spent row keeps "Resets …" at the right; (g) the tag chip is in Settings only (rev B §6's
  Settings clause), Robin may veto; (h) "Share card" is "Share snapshot" on the All tab.
- 2026-11-11 · **For Step 2** (spec and tracker text, not defects): (a) wireframe §8 calls the iOS item "Share card"; it reads
  "Share snapshot" on the All tab. (b) `ios/Shared/FaceViews.swift` still names a "Product" placeholder in the widget gallery's
  preview data (not the sample mode). (c) The iOS "Share snapshot" opens a sheet with a preview (as the account card), where
  Android's top-bar ⋮ opens a dialog: same image. (d) A `-demo` launch reads the real `summaryFilter` / `summaryOrder` keys
  from the App Group (the sample copy seeds from them too), so a screenshot run can come out filtered; reset them in the
  simulator's `group.com.robinrichardrajan.cooldown` plist if so. (e) Fixture gap: no case for the filter with a spent or
  unread account among the rows (the rule does not look at readings).

---

- 2026-11-11 · **Step I4 done** (worktree `angry-dhawan-37e588`). Code cfcd998, judge fixes b3421b8, docs in the close-out commit.
  CooldownCore: `AccountTag` is Personal · Work (`init(stored:)` reads missing, empty or unknown as Personal; `accountWord` "Work account"),
  `SummaryFilter.resolve(tags:remembered:)` (control from two accounts; `snapshotGlyphs` = the rows shown carry both tags) and
  `SummaryFilter.memory` (the §6 rule), `RowGlyphFit` (name floor 64 pt or its own width; one-line bar floor 50 pt; 18 pt full
  column, 16 pt one-line). `AccountTagsTests` read `cases-personal-work.json` (every `filter` case, every `memory` sequence); the I3
  tests on `cases.json` are retired. App: `AppModel.runFilterMemory` (init, `setTag`, `remove`, `addAccount`, Summary appear and
  `displayStamp`; a `-demo` launch that is not the sample leaves the user's key alone), `pendingTag`, `newAccount`; the Summary reads
  the stored filter on every draw; `TagRowGlyph` on full and one-line rows with the list-wide fit decided from measured widths
  (preference keys); the snapshot's glyph column; `AddTagControl` on the Add account sheet (opens at large); the dimmed-segment line;
  the Settings row order of addendum T4 B; Lab is Work in every demo scenario (`-demo upgraded` is the upgrade, `-demo fitdrop` the
  one-line drop). Tests: `swift test` 215 passed; app tests 124 passed (iPhone 17 Pro, `SummaryTagsTests.memoryReplay` replays every
  fixture sequence against `AppModel` and the sample's settings copy); no new crash report. Captures
  `design/research/2026-11-v20-build/I4/` (`iphone17pro/`, `iphone17e/`, `ipadpro13/`, `render/`).
- 2026-11-11 · **Judge** (fresh Opus, high, worker on claude-product): **concerns**, no blocking. F1 (the tap gesture on a disabled
  Button) → the segment is a Button that is never disabled; checked on the iPhone 17 Pro simulator (Work narrows the list; a tap on the
  dimmed Work reaches the handler, logged; the line itself is drawn in `render/i4-list-disabled-work-notice-*`, it expires in 3 s).
  F2 (the fixture's one-account `storedAfter` contradicts its memory case) → fixed on main in d4d1598 (`storedAfter: "work"`); the core test follows the fixture. F3 (a plan chip could
  truncate before the name) → `.fixedSize()` (the tag chip already had it). F4 (Add sheet at the medium detent) → opens at large only.
  F5 (tests claiming more than they check) → retitled; a new test pins that a failed sign-in leaves no account. F6 (an unmeasured row
  reported width 0 and dropped the glyph for a frame) → rows with no width are left out. Fit calls accepted: the notice line sits between
  the header and the first divider; the announcement is posted with it; without a warning the last Settings layout drops the status word
  (the sample's word too); the Add sheet title gap 10 → 2; the sheet at large.
- 2026-11-11 · **For Step 2** (spec and tracker text, not defects): (a) the one-account `storedAfter` fixture case was corrected on main in d4d1598; the core test now reads the fixture as written. (b) A full row never drops its glyph on
  an iPhone 17e or an iPad with the sample names (the one-line drop is captured with `-demo fitdrop`); the full-row rule is covered by
  `RowGlyphFitTests` only. (c) The `-demo` launches read the real `summaryFilter` key and no longer write it (I3's note d). (d) The
  captures `I3/…/summary-untagged-no-control-*` and `i3-header-untagged-*` no longer describe a reachable state (the addendum says so).
  (e) Peer message 2026-11-11: Desktop's VoiceOver label for the Add control is "Tag for the new account, Personal"; the addendum
  fixes "This account is, Personal, selected", which iOS keeps. A cancelled or failed sign-in leaves no account and so no tag.

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
clients' tests back at printed.json in the same commit; replace `spec/mobile/fixtures/tags/cases.json`'s content with `cases-personal-work.json`'s (delete the new file; retire
`build_tags()` in the generator, `build_tags_pw()` writes `cases.json`) and point A4's and I4's tests at `cases.json`;
move the BEHAVIOR-SPEC §6.3 "Planned — v2.0 feedback"
rules into the rules they replace and drop the "planned" label; fold each surfaces file's "v2.0 feedback on
Android/iOS" sections into the body where they replace a body rule (keep a platform section only where the
platforms differ), and empty README's "Planned changes" of these items; rewrite each CCRM-105…113 Status and
iOS line into one status ("Built, device check pending", with commits); file every defect the Lane I and Lane D
Logs recorded; write CCBG-79's status.
Check: the Android suite and both iOS suites, check_no_code.sh. One fresh Opus judge round. Close out and land.
```

**Done when:**
- ☑ One fixture file per topic, no `-v20` file and no `cases-personal-work.json` left, both platforms green on it.
- ☑ §6.3 has no planned block for these items; the surfaces folder describes v2.0 as built.
- ☑ Tracker lines closed as above; lane-recorded defects filed in BUGS.md.
- ☑ Judge round recorded; landed. The orchestrating session is told the commit, the desktop's core re-pin point
  (outward step, the orchestrator's).

**Reversal:** `git revert` of the step's commit restores the lane state (both clients then read the v20 file again).

**Log:**

- **2026-11-12 · Step 2 done** (worktree `jovial-mendel-a0b95c`). **Fixtures:** `printed-v20.json`'s blocks replace their
  namesakes in `printed.json` (paceLine, paceLineForms, endToEnd, mapLabels) and add dayGrid, quiet, callout,
  projectionLayer; the hand-kept blocks (figure, duration, dayClock, caption) and `_meta.note` are byte-identical to before,
  `_meta.tolerance` is now "exact strings; numbers to 1e-6". `tags/cases.json` holds the Personal/Work cases. Both lane-era
  files are deleted. `spec/tools/gen_v20_fixtures.py` now writes `printed.json`'s derived blocks (merging the hand-kept ones
  from the file) and `tags/cases.json`; `build_tags()` is retired; `self_check` keeps the dayClock and duration
  re-derivation (the shipped endToEnd lines it also re-derived are now generated). **Tests** point at `printed.json` and
  `tags/cases.json` (Android `PrintedFormFixturesTest`, `DayGridTest`, `PaceMapV20Test`, `AccountTagsTest`; iOS
  `PrintedFormTests` — its old paceLine test dropped, `PrintedFormV20Tests` covers it on the same file, map words read
  `mapLabels.words` — `PrintedFormV20Tests`, `PaceMapV20Tests`, `AccountTagsTests`, `SummaryTagsTests`); comments in main
  code no longer name the planned block. **Spec:** core §6.3's planned block promoted: the pace line names the reset with the
  2026-11-10 drop order (the 2026-11-06 order is recorded as replaced), the map's words / figures / leaders replace the
  "leader to the right edge" rule, and the day grid, quiet accounts, forecast and projection join the printed form;
  `spec/core/fixtures/README.md` follows. BEHAVIOR-MOBILE §6: built on both clients, the fixture path, and "an account is
  created only when its sign-in succeeds" (Android's provisional card and one-hour launch sweep noted); §7: no longer
  planned, settings in a copy dropped on Exit on both (Android in memory, iOS in throwaway suites). **Surfaces:** every
  "v2.0 feedback on Android/iOS" section folded into its file's body by two Opus builders (purpose (d), product account;
  app.md, and foundations / notification / widgets / README), the seat reviewing; app.md §2.3 carries the shared tab-strip
  rule (Robin's option B: fixed while every label fits on one line, scrolling once one would not) with iOS's difference
  (CCBG-88); the "Reviewer sample data" section is both platforms; README's "Planned changes" lost the v2.0 feedback items.
  **Trackers:** CCRM-105…115 each carry one status, "Built, device check pending", with their commits (the "iOS:" lines
  folded in); CCRM-109's What text and wireframe §5's note read "under 1" (window rows truncate, c24e675). CCBG-79 Fixed,
  device check pending; CCBG-81's iOS line written (no wrap: one line at 16 pt, "Personal" whole at 127 / 130 pt, it would
  truncate below a ≈354 pt column; `design/2026-11-11-iphone-duo-pass.html` §5). **Filed** (none marked blocking — Robin
  decides): CCBG-82 (Clock Case Mix, iOS, Low — Lane I's AM/am defect; the spec and Android print "PM", iOS's older lines
  lower-case it), CCBG-83 (Duet Reapply Residue, Android, Low — A6 judge F4), CCBG-84 (One-line Name Overrun, iOS, Medium,
  wireframe — Duo D1), CCBG-85 (Reset Alert Labels, iOS, Medium, wireframe — Duo D2), CCBG-86 (Rail Marks Alike, iOS,
  Medium, Robin's decision — Duo D3), CCBG-87 (Strip Under Rail, iOS, Low — Duo D4), CCBG-88 (Tab Strip Truncates, iOS,
  Low, latent), CCBG-89 (Map Axis Clip, Android, Low — A3's "00%" at font 2.0). **Not filed:** the orchestrator's "iOS
  sample mode persists display settings" — the code does not (`SampleStore` redirects `Prefs` to throwaway suites seeded
  from the real ones, deleted on Exit and at launch; `.id(model.isSample)` rebuilds the tree so every `@AppStorage` binds to
  them; `SampleStoreTests`); only app.md's sentence "Display settings still apply and persist" was stale, and it is fixed;
  the judge confirmed the reading. **Lane D's notes:** RELEASING.md §5 and listing.md §5 name the 2026-11-11 graphic page,
  variant C, and the Shared pace icon; the TestFlight privacy draft, CONSOLE-STEPS step 11 and V2.0-ASSET-CHECK drop
  `www.googleapis.com` as a recipient; V2.0-ASSET-CHECK's "since/until" is the reset-named line. **Checks:** Android
  `testGithubDebugUnitTest testPlayDebugUnitTest lintGithubDebug` and `assembleGithubDebug assemblePlayDebug` green;
  `swift test` 214 passed; iOS app tests 124 passed (iPhone 17 Pro, iOS 26.5); `check_no_code.sh`; the generator's
  `--check`. **spec/core changed:** `BEHAVIOR-SPEC.md` §6.3, `fixtures/pace-distance/printed.json` (derived blocks; the
  endToEnd lines now carry "resets", so the desktop's pinned tests change at its re-pin), `fixtures/README.md`; the
  v20 file is gone.
- **2026-11-12 · Judge** (fresh Opus @ high, worker on claude-product, read-only): **accept with concerns**, nothing blocking.
  F1 (results not shown; seven hashes unchecked; `_meta` keys dropped) → closed by the runs above, `git log` on each hash
  (all real close-out commits), and `_meta` at HEAD held only `note` and `tolerance` (tolerance superseded as above). F2
  (wireframe §5 still "under 0.5") → a dated correction on the page. F3 (stale "planned" wording in the generator and in
  map-foot's note; Tests and gates naming the deleted files) → fixed, regenerated, `--check` passes. F4 (lane follow-ups
  dropped) → the asset-check rows fixed; **deferred, on purpose:** I2's judge F6 (a `quiet`/`figures` case between 99.5 and
  99.99, where §6.1 calls the week spent and the map prints "100%" while the row truncates to 99%) needs Robin's call on
  which wins before a fixture can pin it — for Step 3 / the orchestrator; I3's (e) (a filter case with a spent or unread
  row) adds nothing the rule reads (it never looks at readings). F5 (nits) → foundations' drop-order attribution and
  BUGS' heading names fixed; notification.md's "271" kept (it is `Duet.contentDp` at 411 dp; One UI measured 272), now
  said so; README's CCRM-96/97 bullet predates this step (left).
- **For Step 3 — open questions for Robin** (iPhone Duo pass §4, not filed): **Q1** the unfolded Duo (≈669 pt) gets the
  phone page; lower the 700 pt iPad-page threshold to ≈660? (a wireframe question, needs an unfolded pass); **Q2** tap
  targets under 44 pt on every iPhone (History week arrows, the banner's "Exit", the sort button, "Check now", the map and
  tile chips, the filter segments, one-line rows, History strip tabs); **Q3** "Try with sample data" opens on Teams, not
  All (`DemoData` selects "teams"); **Q4** the iPhone build is portrait-only (landscape on the unfolded Duo?). And which of
  CCBG-82…89 block v2.0.
- **For Step 3 — captures the build can no longer reach** (superseded by I4's Personal/Work model and the sample's Lab as
  Work; I4's captures show the current states): `I3/iphone17pro/` and `I3/iphone17e/` `summary-untagged-no-control-*`
  and `I3/render/i3-header-untagged-*` (the control now shows whenever the Summary exists); `I3/iphone17pro/sample-mode-
  three-accounts-all-*` (Lab untagged in the sample); `I3/iphone17pro/account-page-tag-picker-light`,
  `long-press-menu-tag-submenu-light`, `long-press-tag-submenu-open-light` (a "None" row); `I3/*/settings-tag-chips-*` and
  `I3/render/i3-tag-chips-*` (chips only on tagged accounts). Step 3 checks the current states, not these.

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
  the snapshot, the sample mode (entry, banner, exit, the notification and widgets still on real accounts); **the
  2026-11-11 additions:** the narrow Duet (CCRM-114) on the Fold 7 cover and inner shades — the real widths against A6's
  emulator numbers, "Sign in" / "No reading" / "Not started" with the word in the figure's slot, the pace line
  dropping its weekday and not cut mid-clock (CCBG-80), One UI's vertical clip of the collapsed card's second row (A1
  and A2 flagged it: blocking if One UI clips too); the tag glyph on every Summary row (the column dropping at 360 dp
  and under large text), the Add sheet's Personal | Work control, an upgrade from v1.9 (everything Personal, Work
  disabled), the filter falling back to All and staying there, the snapshot's glyph rule; on the iPad the same.
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

- **2026-11-12 · Fold 7 half** (session "v2.0 Step 3 — Fold 7 device pass", branch `claude/peaceful-meninsky-959f27`, PR
  robineam360/Cooldown#55). **iPad half deferred by Robin's choice (2026-11-12)**: it runs in the separate iOS bug-fix
  session with the iOS defects; every iPad item below stays ☐ "deferred: iPad half".
  - **Starting state, Fold 7 (USB adb, RZCY70YN0LJ):** installed the 2026-11-06 github release candidate (labelled 1.9 /
    25, release-signed, updated 2026-11-06 19:45); screen timeout 300000 ms; dark mode; font 1.0; density 420; folded;
    Cooldown settings: theme System, time System, Usage display Vs pace, end mark Line, Open on the summary on, Reset
    time Countdown, red past the mark on, theme Per provider, notification on, Duet First Claude / Second ChatGPT, tap
    opens Cooldown, status icon "Whichever is higher"; five accounts (Claude Pro, ChatGPT Plus, Gemini Pro, Work and
    Product Team Premium), all Personal; widgets: two Number 4×1 (ids 125, 127) on the cover home; no Live Activity
    (Android). Changed during the pass and put back: Second account (Gemini, None, back to ChatGPT), dark/light,
    font 2.0, density 480 (360 dp), sample mode (entered, exited).
  - **Builds:** github release of main 3d4aed2 (sha256 a7d530ea…3eed) for the pass; fix candidates 8894d64
    (18f81ab2…6e04) and a5e3e7e (e2e3c0d1…b121), all release-signed over the existing install (accounts kept). Keystore
    copied per build and deleted after; never staged. **The a5e3e7e candidate stays installed on purpose.**
  - **Checks ☑ on the Fold 7:** the Duet collapsed and expanded on the cover and inner shades, dark and light (real
    widths: expanded header column 183 dp at 411, 134 at 360, 265 inner; collapsed half 181 / 133 / 262 dp, bar
    131 / 83 / 212; A6's emulator said 144 / 84 / 265); "7d"; the grid on every chart; the "resets" line and its drop
    order (collapsed single "2h 2m ahead · Fri", CCBG-78 (Collapsed Pace Line Clip) whole); quiet accounts ("1 at 0%");
    chips; tags (person glyph on every row; the column stays at 360 dp, the list being ≥ 295 dp, and drops at font 2.0);
    filter; sort; the snapshot and its glyph rule (no column, all Personal); sample mode (entry, banner, exit; the
    notification stayed on Claude/ChatGPT and Robin saw the widgets stay on real accounts); the narrow Duet: "Not
    started" keeps the "—" over 256 dp (cover 411, inner) and takes the figure's slot at 360 dp; the pace line drops
    its weekday and clock, never cut, after CCBG-91; One UI does not clip the collapsed card's second row (360 and
    411); the Add sheet's Personal | Work; the upgrade from the old candidate (all Personal, Work disabled); the filter
    falling back to All after the sample; CCRM-104 (Pace Line Parity)'s signed Vs pace; CCBG-79 (Leader Reads as Trail)
    looks right; the picker previews (no 4×2 entry by design: Number and Strip pick at 4×1).
  - **Not reachable on the Fold 7, with the reason:** a binding or full 5h with Headline 5-hour and Auto, and a spent
    week (no account was in that state, and the release build has no synthetic series); Gemini's pool once used and a
    refresh after an hour (Gemini had not started its week); "Open on the summary" off relaunching the last account (a
    process restart needs a force-stop, which the memory forbids on Robin's phone); the Countdown 2×2 "~ runs out"
    note (no 2×2 placed). CCBG-72 / CCBG-73 / CCBG-70 are iPad items: deferred.
  - **Filed:** CCBG-91 (One UI Expanded Column) to CCBG-96 (Picker Preview Truncation) in the pass, CCBG-97 (Map Foot
    Clip) in the recheck. **Robin's blocker decision:** "v2.0 will be our first public beta and public release build.
    So we have to fix all our bugs and not keep it after v2.0": every open defect blocks, "only if it's still
    applicable in the updated/new design"; iOS ones go to a sister iOS bug-fix session that also runs the iPad half.
  - **Fixed here, one commit each, rechecked on the Fold 7:** CCBG-91, 92, 93 (Robin kept the short caption form), 89,
    94, 53, 83, 3, 95 (Robin: the projection label yields, OK as built), 97, 96, 15 (Amber → #B07D00 / #D9A84A),
    55 items 1 (core §5 follows Android) and 3 (Android names its three forecast silences); closed CCBG-47 (fixed by
    design since CCRM-103 (Hybrid Notification); Robin saw the widgets follow a theme switch) and CCBG-49 (no longer
    applies). Wireframe `design/2026-11-12-step3-fix-wireframes.html`, options A approved by Robin. CCBG-95 and
    CCBG-55 item 3 could not be seen on the device (no projection near the now label; the 5h was over 5%): unit tests.
  - **Judge** (fresh Opus @ high, product account): concerns, five, all adopted (6082eea, a5e3e7e). F1 was real: the
    state word's theme-attribute colour drew dark grey on the dark shade; it now takes an explicit ink, rechecked
    light and dark.
  - **Shared:** spec/core §5 changed (Amber, always-on chrome); the parent session was asked to tell Desktop, which
    re-pins after this lands. iOS items (CCBG-15's hex, CCBG-55 item 2's Claude Orange, a check of CCBG-89/92–97 on
    iOS, CCBG-82/84/85/87/88) were sent to the iOS bug-fix session with the findings note.
  - **Always after:** every changed setting put back (Second ChatGPT, dark, font 1.0, density reset, sample exited);
    widgets untouched. **The screen timeout was not restored by adb**: the phone dropped off USB first, and Robin said he would set
    it back to 5 minutes himself (his word, 2026-11-12; not seen by adb). The a5e3e7e candidate stays installed on purpose.

- **2026-11-13 · Fold 7 recheck of main fcc8bfd** (session "v2.0 final device pass", USB adb RZCY70YN0LJ; the iPad half
  runs in its own session and logs its own bullet).
  - **Starting state:** the a5e3e7e candidate (1.9 / 25, release-signed); screen timeout 600000 ms (the 2026-11-12 Log said
    300000; Robin had set it back to 10 minutes); dark; font 1.0; density 420; Theme per provider; Tag marks Personal and
    Work both on; widgets two Number 4×1 on the cover home. **Build:** github release of fcc8bfd, installed over the
    candidate (accounts kept); keystore files copied and deleted after, never staged. Then one fix candidate (CCBG-100).
  - **Rechecked ☑ on the Fold 7:** CCBG-89 (font 2.0: "100%" whole), CCBG-91 (360 dp shade, collapsed and expanded: the
    pace line drops to "· Fri", never cut), CCBG-92 and CCBG-93 (Summary lines at 360 dp and font 2.0: nothing cut
    mid-clock), CCBG-94 (snapshot reads "Not started yet"), CCBG-96 (Countdown "at 9:10 PM", Number "Pro" whole),
    CCBG-97 (foot note wraps whole at font 2.0), CCBG-15 (Amber `#D9A84A` is distinct from the yellow rung), CCBG-83
    (the Duet draws correctly collapsed and expanded on cover 411 / 360 dp and the inner shade; the stale-residue case
    itself did not occur). **Unit-tested only, not drawable on the device:** CCBG-95 (no account near its projection),
    CCBG-3 (no payload switches extra usage off), CCBG-55 items 1 and 3 (core §5 changed; the three forecast silences
    need a state the account was not in).
  - **CCRM-118 (Header Glyphs) on Android ☑:** the badged account icon at real density on the cover's top tabs (person on
    Claude, ChatGPT, Gemini) and on the inner screen's tabs and History strip (person and briefcase on all five); the
    filter All · person 3 · briefcase 2 and the sort glyph with its four-item menu; Tag marks in all four states (both
    on, Personal off, Work off, both off: an unmarked row keeps its blank slot). **TalkBack on the Tag marks note: not
    checked** (Robin skipped it, 2026-11-13) — **open for the release pass.**
  - **Smoke ☑ (cover and inner, dark and light):** Weekly first, Vs pace figures, the Summary tab, the Claude tab's
    Model limits chip (Fable), Gemini "Not started yet", the notification collapsed and expanded on both screens, the
    placed Number widget on the cover home, the picker previews. CCRM-55 (Antigravity Account) shows as a Gemini account.
  - **Filed and fixed:** CCBG-100 (Picker Number Clip): the Number 4×1 picker preview clipped its "Resets …" line;
    preview padding 8 → 4 dp and figure 32 → 28 sp, rechecked in the One UI picker (this restores the preview's own
    intent, the whole face; the placed widget is unchanged).
  - **Filed and fixed after the log above (Robin, on the device):** CCBG-101 (Filter Glyph Low Centre): the Summary filter's glyph and count sat about 1.5 dp above the bar's middle; moved down, rechecked on the Fold 7 cover.
  - **Always after:** screen timeout back to 600000 ms by adb; dark, font 1.0, density 420, Theme per provider, both Tag
    marks on; widgets untouched. The fcc8bfd + CCBG-100 candidate stays installed on purpose.

- **2026-11-13 · iPad half** (session "v2.0 final device pass: iPad half", iPad Pro 11-inch M4, iPadOS 27.2, USB, UDID
  …001C; Debug build signed on the Sedin team; the app's in-app steps driven by `ios/UITests/Step3PadTests.swift`, Robin
  did the lock, unlock and Split View). Private captures (Home Screen, lock screen) stayed in the scratchpad.
  - **Starting state:** Cooldown 1.0 (1) installed (the 2026-11-06 PR #23 build); dark; airplane mode on, Wi-Fi on;
    four accounts, all Personal (Claude Pro, ChatGPT Plus, Gemini Pro, Work Team Premium); Theme colour Per provider,
    Time format System, Usage display Vs pace, end mark Line, Open on the summary on, Reset time Countdown, red past the
    mark on, Density Comfortable, Chart height Large, Time across, Summary order Your order, Tag marks Personal and Work
    both on, Live Activity on, Reset alerts Claude 5h If busy / Weekly Always (others Off); Home page 1 holds the Overview
    and 2×2 large widgets, three small faces and a Strip; no old Ring widget on any page; the Live Activity runs
    (Claude and Work, Weekly). **Build:** main merged to fcc8bfd → 7f67f6a, Debug, installed over the 1.0 (1) build (accounts and
    settings kept); then the CCBG-102 fix installed over it. **That build stays installed on purpose.**
  - **Rechecked ☑ on the iPad:** CCBG-82 (no lower-case "am/pm" in any screen), CCBG-85 (Reset alerts: account header, rows
    "5h" and "Weekly", control whole), CCBG-86 (sidebar marks badged; portrait tab bar and the one-third Split View's bottom
    bar badged), CCBG-88 (History strip fixed in equal shares, portrait and landscape), CCBG-87 (History strip inside its
    column), CCBG-90 (CCRM-116 Inner Panes below), CCBG-98 (`-demo max` chart: no label collision), CCBG-15 (Amber swatch
    measures #D9A84A dark) and CCBG-55 item 2 (Claude Orange leads the picker, #E59980 dark), CCBG-72 (Live Activity at
    −4% / −20% followed a switch to Used at once, 16% / 90%, and back to Vs pace), CCBG-73 (Claude Pro, ChatGPT Plus, Work
    Team Premium, Gemini Pro; no "Prolite"), CCBG-70 (no stale Ring on any Home page; the small ChatGPT ring is the Number
    widget and offers Edit Widget, Robin; nothing to remove).
  - **CCRM-116 (Inner Panes) ☑:** All and each account: Usage | Pace side by side in landscape, stacked (Usage over Pace)
    in portrait and in a half-width Split View; at a one-third Split View the phone layout with the bottom tab bar; History:
    5-hour | 7-day panes side by side in landscape, stacked in portrait; sidebar badges. **Not reachable: no Duo device**
    — the iPhone Duo items (the scroll across a real fold, the 40 pt fold region, fold-nudge F2) and the Duo rail.
  - **CCRM-118 (Header Glyphs) ☑:** the badged account icon in the sidebar and in every tab bar; the Summary filter (All · 4
    · briefcase 0, the disabled Work segment, labelled "Work, no accounts") and the sort button with its four-item menu
    ("Sort: your order"); Appearance › Tag marks in all four states (Personal on/off × Work on/off: rows and sidebar marks follow, the
    filter keeps its glyphs); the demo's Work tags (briefcase 7 · person 3). VoiceOver: read from the accessibility tree
    (the filter is a group "Filter" of "All, 4 accounts", "Personal, 4 accounts", "Work, no accounts"; the sort "Sort: your
    order") — **not listened to with VoiceOver on**. **Not reachable:** the iPhone More list.
  - **Smoke ☑ (dark; light on the main screens and an account, both orientations):** CCRM-96 (Weekly First: Weekly leads the
    tile, sidebar, Live Activity), CCRM-97 (the Number widget's ring and Weekly), CCRM-98 (the All tab and the Summary), CCRM-99
    (account pages opened; model chips Fable on Claude and Work, Argon on Gemini), CCRM-100 (signed Vs pace figures on the Summary,
    tiles and Live Activity), CCRM-55 (Gemini as an account: "Starts when a message is sent", Weekly, Argon), the dense ten
    and `fitdrop` (CCBG-84's name reads whole), the iOS sample mode (Add account › sample data: three accounts, exit
    restores the four real ones), the widgets and the Live Activity on the Home and lock screens. **Not reachable:** the
    single-account Live Activity (CCBG-99: the iPad's is the two-account Duet; `LiveFiveLineTests`), a binding or full 5h
    (no account in that state), a spent week.
  - **Filed and fixed:** **CCBG-102 (Number Footer Cut)**, found on the Home Screen: the small Number widget's 5h footer
    read "Resets in 4h 25m · 10:41…". Wireframe `design/2026-11-13-ipad-step3-number-footer.html`, option A approved by
    Robin (the lead part alone, never half a clock); `NumberFooterTests`; rechecked on the Home Screen ("Resets in 3h 52m").
    CCBG-102's ID was free at the merge of origin/main (CCBG-100 and CCBG-101 are the Fold 7 half's).
  - **Judge:** one fresh Opus round (high) on the CCBG-102 batch: concerns, three low, none blocking. Adopted: VoiceOver now reads the whole
    footer line where the lead alone is drawn (`accessibilityLabel`); the device-pass tests' header now says which tests pair. Not adopted: a 158 pt render
    test (the Home Screen recheck covers it).
  - **Always after:** every setting identical to the start (the Settings screens diffed before and after: Vs pace, Tag
    marks both on, Live Activity on…); Split View closed; the iPad unlocked and at Cooldown. No setting of the iPad was
    changed outside Cooldown. The CCBG-102 candidate stays installed on purpose.

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

The physical device checks and which defects block; any visible change beyond rev B and its 2026-11-11 addendum; the feature-graphic
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

**Added 2026-11-11 (the amendment, Robin in the amendment session; AskUserQuestion, options quoted):** the narrow Duet,
**"C: word takes the figure's slot (Recommended)"**; the tag model, **"Always Personal or Work (Recommended)"**, after
Robin asked why "None" should exist at all; existing accounts at upgrade, **"All Personal, silently (Recommended)"**; where
the choice sits when adding, **"On the Add account sheet (Recommended)"**; the first account, which never passes the sheet
— Robin: **"Pls ensure we have parity between both of the iOS/Android platforms"**, recorded as Personal without asking on
both (the addendum's T2 A; both options drawn behave the same on both platforms; Robin may overrule at any step's
captures); the snapshot glyph, **"Only while both tags appear (Recommended)"**; the addendum page **"Approved as drawn"**
with Fable's fit calls; the filter reset **"As per your recommendation but ensure specs and behaviour is the same across
iOS and Android"**; and Robin's two requests, the row glyph ("Also on summary rows please (next to or better: before the
account logos?)") and the narrow Duet ("Yes, wireframe it"). No new gate: none of A4, A6, I4 waits on a question.

**Added 2026-11-11 (relayed by the orchestrating session to Step A6, recorded there):** the Personal/Work filter keeps its
tags **as words** ("All · Personal · Work"), not glyphs; a half-added account abandoned in the browser is **removed at the
next launch once it is over an hour old**, as A5 built it (`UsageRepository.settleProvisional`). And CCBG-81 (History Tab
Wrap), added to A6 by Robin: asked with options (A: History counts tabs like Main, recommended; B: both strips scroll when a
label will not fit; leave as is), Robin chose **"B: scroll if a label won't fit"**, then, shown B's captures, **"Approve B as
captured"** (`design/2026-11-11-ccbg81-history-tab-strip.html`).

All six were answered by Robin in the orchestrating session on 2026-11-11 (AskUserQuestion, options quoted
verbatim) and relayed to the planning session, which recorded them. No answer adds or removes a step.

## Failure modes

- **A landing conflicts.** WORKTREE.md: the step aborts the merge, stops and reports; the orchestrating session
  sorts it out. The shared-file table should make this rare.
- **A fixture is wrong.** Spec leads: the orchestrator gives one session a spec-only fix; the other lane re-merges.
- **The Duet does not fit the real shade.** A2 builds to the wireframe's widths; Step 3 measures; a new width is
  a wireframe revision for Robin.
- **The narrow Duet's real widths differ (One UI at 360 dp is assumed to match AOSP's 220).** A6 derives its widths from
  the screen and never widens past today's constants; Step 3 measures; a different width is a wireframe revision for
  Robin, not a silent retune.
- **A v1.9 user with work accounts upgrades.** Every account reads Personal until changed (Robin: silently); the glyph
  and the disabled Work segment make it visible, and Tag… changes it. A mislabelled account is cosmetic, never data.
- **Two clients drift on the filter memory.** Both replay the same `memory` sequences from one fixture; Step 2 folds the
  file once.
- **The sample leaks into real data.** A5's isolation tests; Step 3 checks the notification and widgets.
- **Play refuses something.** The AAB checks run before the tag; Play's rules are re-read on the day; a
  `specialUse` objection falls back to CCRM-67 (Pin Service), a new release on both channels.
- **Beta App Review or Play review is slow.** GitHub v2.0 does not wait for Play (CLOSED-TEST-PLAN.md); Q2
  decides whether it waits for TestFlight.
- **The tester count dips below 12.** Treated as restarting the 14 days; recruit 15–18.

## UNVERIFIED

The Duet's real collapsed widths on the cover and inner shades and its expanded column (≈186 dp is
arithmetic), and One UI's widths at 360 dp (the narrow Duet's 220 dp is AOSP's); iOS chip and segment widths; whether two worktree sessions building at once slow each other enough
to matter; whether a new closed-track build resets Play's 14-day clock (secondary sources say no); whether
Play's reviewer accepts the sample mode as App access; Beta App Review's timing.

## Tests and gates

- `./gradlew testGithubDebugUnitTest testPlayDebugUnitTest lintGithubDebug`, `assembleGithubDebug assemblePlayDebug` (every Android step, Steps 2–4)
- `swift test --package-path ios/Packages/CooldownCore` and the iOS app tests on the iPhone 17 Pro simulator (every iOS step, Steps 2–4)
- `sh spec/tools/check_no_code.sh` (Steps 1, 2)
- The fixture generator script re-derives `printed-v20.json` and the mobile fixtures (Step 1); since Step 2 it writes `printed.json`'s derived blocks and `tags/cases.json` (`--check`)
- `tools/widget_layouts.py` byte-identical (any step that changes a widget layout)
- The downgrade test for the tag key (A4, I3, I4)
- The tag fixture replay, filter cases and memory sequences (`cases-personal-work.json`; A4, I4; `tags/cases.json` since Step 2)
- The Duet narrow-width and derived-column tests (A6)
- The four sample-isolation tests (A5)
- One fresh Opus judge round per code step (1, A1–A6, I1–I4, 2, 3's fixes, 4)
- RELEASING.md §4 step 5 and §5's checks (Step 4 and 7); §6's archive checks (Step 4)
- Fable judge, Sol cross-check and Robin's recorded go before publishing (Step 7)
- The Play-signed APK's digest = the trusted digest (Step 8)
- The merge dry run (Step 0's Log)
