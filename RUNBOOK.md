# Runbook — the v1.7 arc: Main Screen Redesign, then ship

Ordered, checkable steps to take Cooldown from `main` as of 2026-09-17 to a released v1.7 that
carries the main-screen redesign. Roadmap items: CCRM-72 (Main Screen Redesign) — under which
CCRM-25 (Card Layout) and CCRM-35 (Layout Reset) are built — CCRM-73 (Model Cap Chart), and the
four filed from Robin's rev C review: CCRM-74 (Chart Polish), CCRM-75 (Chart Height), CCRM-76
(Black Room) and CCRM-77 (Transposed Chart, painted to decide). Plus the device passes v1.7
already owes: CCRM-65 (Accounts Redesign), CCRM-70 (Plan Fit),
CCRM-71 (Account Order), CCRM-51 (Rails Gauge) and CCRM-6 (Multi-Account)'s four-account strip.

Earlier arcs are in git history: the Play Store runbook (dropped, CCRM-66 (Play Store Launch)) at
commit `b827dc5`, the v1.6 dual-identity runbook at `a7dba1b`, the v1.5 multi-provider one at
`e18cf32`.

**What is different about this arc.** It is one visible redesign with a hard wireframe gate in the
middle. Step 1 lands the only code allowed before the gate — forward-only history fields that
cannot be backfilled. Step 2 is the gate. Nothing in Steps 3–4 starts until Robin has said "approved"
to a wireframe revision in `design/`.

**How this arc is run.** Delegation follows the `model-mesh` skill (CLAUDE.md §4) — which model does
what is its call, not this file's. The session owns every decision, reviews, tests, commits and ticks
this file; the device pass and anything touching signing stay with the session itself, never a
sub-agent. If a session runs out mid-arc, a fresh session resumes from the *Resume* block of the first
unticked step.

**Where the detail lives.** The design reasoning is in ROADMAP.md items CCRM-72 and CCRM-73. This
file says what order, what to paste, and what "done" means. Release mechanics stay in
[RELEASING.md](RELEASING.md).

## Conventions — every session reads this block first

1. **Start from the paste.** Each step has a fenced *Resume in a fresh session* block that
   names what to read. It never needs an earlier conversation.
2. **Tests:** `./gradlew testDebugUnitTest` green and `./gradlew assembleDebug` compiling before a
   step closes.
3. **Commits:** straight to `main`, subject `feat(CCRM-72): …` / `feat(CCRM-73): …` /
   `docs(CCRM-72): …`, body naming anything unrelated riding along. Never stage
   `ccooldown-release.jks`, `keystore.properties` or `local.properties`.
4. **Close-out, in this order:** (a) every item in the step's *Done when* list; (b) the roadmap
   item's Status line; (c) tick the step in the Progress table (☐ → ☑) and write one dated line
   after its **Log:**; (d) commit and push, the tick in the same commit as the work.
5. **Handover.** The session's last message: what changed in one paragraph, what Robin has to do
   himself before the next step, then the next step's *Resume* block copied from this file.
6. **Wireframe gate.** Step 2 draws every state named in CCRM-72 and CCRM-73 before Step 4 builds
   any of it. Review happens in the `design/` HTML, never in chat, and questions go to Robin one at
   a time. Silence is not approval. A session that wants to change anything else the user sees
   stops and asks Robin first, naming the file to open.
7. **Mocks keep full functionality.** A wireframe never draws an existing element simplified: the
   trend chart keeps its guides, axis, pace line and projection in every state it appears in.
8. **Comfortable is v1.6's layout.** Density = Comfortable keeps today's card layout and chart
   size; what changes for everyone, in both densities, is exactly the list Robin asked for on
   2026-09-17 — the status line (CCRM-72), the black room (CCRM-76), headlines without "used",
   "%" for "points", one row style for both cards and the guide labels inside the plot
   (CCRM-74). Nothing else moves under a user who never opens Appearance.
9. Tracker IDs carry their epic name on first use (CLAUDE.md §1).
10. **Delegation follows model-mesh (CLAUDE.md §4).** Design decisions and sequencing stay with the session and Robin; the §4 project gates bind every sub-agent.

## Progress

| Step | Item | Who | Gated on | Status |
|---|---|---|---|---|
| 1 | File CCRM-72/73, reverse the CCRM-3 ruling, land the cap history fields | session | — | ☑ |
| 2 | Wireframe: every CCRM-72 and CCRM-73 state, reviewed to approval | sub-agent draws · Robin approves | — | ☑ |
| 3 | Pure models: `CardLayout`, `UsageCache` keys, tests | Sonnet · orchestrator reviews | 2 | ☑ |
| 4 | UI build: compact cards, status line, 7d toggle, layout sheet, Density chips | Sonnet · orchestrator reviews | 2, 3 | ☑ |
| 5 | Fold 7 device pass: CCRM-72/73 + the passes v1.7 owes | orchestrator · phone over USB | 4 | ☑ |
| 6 | Release v1.7 | session · Robin at the phone | 5 | ☑ |

---

## Step 1 · File the items and land the forward-only data

**Who:** the session. Done 2026-09-17 in the planning session itself.

**Why first:** `HistoryStore` recorded only the pool percentages, so a Fable curve could not exist
until the store learned to write the caps — and nothing recorded before that day can be recovered.
Same lesson as CCRM-70 (Plan Fit)'s `pl`/`mc` tags: land the write path the moment it is decided.

**Done when:**
- ☑ CCRM-72 (Main Screen Redesign) and CCRM-73 (Model Cap Chart) filed in ROADMAP.md; CCRM-25
  (Card Layout), CCRM-35 (Layout Reset) and the CCRM-3 (Unified Theming) in-app ruling updated;
  CCRM-70 (Plan Fit) and CCRM-71 (Account Order) statuses corrected to Built.
- ☑ `HistoryStore.encode`/`parsePoint` with the `mc`/`mr` maps, `HistoryPoint.capPcts`/`capResets`,
  `Projection.capSamples`; `HistoryStoreTest` (5) and three new `ProjectionTest` cases; 421 green.
- ☑ This runbook replaces the dropped Play arc.

**Log:**
- 2026-09-17 — all three done in the planning session; committed with the Step 2 wireframe draft.

---

## Step 2 · Wireframe — every state, reviewed to approval

**Who:** a sub-agent draws `design/2026-09-17-main-screen-redesign.html`; the orchestrator reviews
it against ROADMAP.md before Robin sees it; Robin approves, one question at a time.

**Resume in a fresh session:**

```
Read CLAUDE.md, then RUNBOOK.md — Conventions and Step 2 — then ROADMAP.md items CCRM-72 (Main
Screen Redesign) and CCRM-73 (Model Cap Chart) in full, and open
design/2026-09-17-main-screen-redesign.html. If the file does not exist, brief a sub-agent to
draw it in the conventions of design/2026-09-16-plan-fit-and-account-order.html (same CSS
tokens, .phone frame, numbered h2 sections, a "For review" banner), with real dp numbers and
every state both items list, dark and light, cover (~360dp) and inner (~750dp) widths; the
chart in every state keeps its guides, axis, pace line and projection (Convention 7); the
screenshots in release/docs/src/shots/v16-main-*.png are what today looks like. Review the
result yourself first: every listed state present, Comfortable identical to v1.6 (Convention
8), the never-blank invariant drawn, the 7d toggle absent when there are no caps. Then hand it
to Robin: name the file, and ask your questions one at a time. On "change X", revise the file
(rev B, C …) and show it again. Do not write any UI code. When Robin says approved, record the
revision letter and date in CCRM-72's and CCRM-73's Status lines and close out per Convention 4.
```

**Done when:**
- ☑ Robin has said "approved" to a named revision of the wireframe.
- ☑ Every decision taken during review is written into CCRM-72 / CCRM-73 in ROADMAP.md, in the
  "Decided" style of CCRM-65 (Accounts Redesign).

**Log:**
- 2026-09-17 — rev A drafted by a sub-agent, reviewed by the orchestrator (seven fixes → rev B).
  Robin reviewed rev B: "All" not "All models", the two-column inner 7-day card, Free plan bare
  → rev C. Robin reviewed rev C: seven more asks and the transposed-chart idea, filed as
  CCRM-74–77 → rev D, drawn the same day.
- 2026-09-17 — Robin approved rev E: chart height default Medium, transposed chart as a toggle; everything else as drawn. Decisions recorded in CCRM-72–77.

---

## Step 3 · Pure models

**Who:** Sonnet builds; the orchestrator reviews the diff and runs the tests.

**Resume in a fresh session:**

```
Read CLAUDE.md, RUNBOOK.md (Conventions, Step 3), ROADMAP.md item CCRM-72 (Main Screen
Redesign) — the Model paragraph — and the approved wireframe named in its Status line. Read
data/ProfileRegistry.kt (the pure Companion pattern and its move function), data/UsageCache.kt
(per-account keys via k(), LEGACY_PROFILE_KEYS, the global pref getters), and
app/src/test/.../ProfileRegistryTest.kt. Build, Android-free: data/CardLayout.kt — a CardId
enum (SESSION, WEEKLY, CREDITS), data class CardLayout(order, hidden, more), Companion
move/hide/show/toMore/toMain/normalize/default and JSON encode/decode; normalize drops unknown
ids, restores missing ones at the end, and enforces that at least one card is shown and not
behind More. UsageCache: density()/setDensity ("comfortable" default), chartSize()/setChartSize (ui.ChartSize, default MEDIUM), chartOrientation()/setChartOrientation (ui.ChartOrientation, default ACROSS), layout(profile)/
setLayout, weeklyChart(profile)/setWeeklyChart (cap name or null), expanded(profile, card)/
setExpanded; add every new per-account key to LEGACY_PROFILE_KEYS. Tests: CardLayoutTest in the
style of ProfileRegistryTest's move block (moves, clamps, no-op returns the same instance,
invariant, unknown id, round trip), plus a SettingsMigration-style defaults test. No UI. Tests
and assembleDebug green; commit feat(CCRM-72): …; close out per Convention 4.
```

**Done when:**
- ☑ `CardLayout` and the `UsageCache` accessors exist with tests; nothing visible changed.

**Log:**
- 2026-09-17 — built by a Sonnet sub-agent, reviewed and committed by the orchestrator: `CardLayout` (32 tests), `DisplayPrefs` enums (11), `UsageCache` keys; 464 green.

---

## Step 4 · UI build

**Who:** Sonnet builds to the approved wireframe; the orchestrator reviews against it, state by
state, before the commit.

**Resume in a fresh session:**

```
Read CLAUDE.md, RUNBOOK.md (Conventions, Step 4), ROADMAP.md items CCRM-72 (Main Screen
Redesign) and CCRM-73 (Model Cap Chart), and the approved wireframe named in their Status
lines — every state. Read MainActivity.kt (ProfileScreen, TrendBlock, SubBar, the TopAppBar
actions), SettingsScreen.kt (the Appearance page lambdas, the inline FilterChip pattern,
ReorderAccountsSheet / ReorderRow / DragHandleIcon, RemoveAccountDialog), ui/AccountStatusLine.kt,
data/CardLayout.kt and data/Projection.kt (capSamples). Build, in this order, one commit each:
(1) the status line replacing the Refresh button and the two timestamp lines, both densities;
(2) Density chips in Appearance, hoisted in App like showOverPace, and the compact cards with
tap-to-expand — Comfortable must render exactly as before (Convention 8); (3) the 7-day chart
toggle — chips absent with no caps, the chart / pace readout / estimate line following the
selected series via Projection.capSamples, the cap's SubBar unchanged; (4) the layout sheet
from the top-bar ⋮ — extract the drag mechanics from ReorderAccountsSheet into a shared helper
rather than copying them — with show/hide switches, the Behind-More divider, the disabled switch
when the invariant would break, Reset layout with its confirm, and the More disclosure on Main;
cards keyed by CardId so a reorder never changes which account tab is showing; (5) CCRM-74
(Chart Polish): guide labels inside the plot and the plot at full card width so the now divider
sits under the bar's pace mark, Fmt.usageShort headlines, the 5-hour row as a SubBar, "%" for
"points" everywhere including Sparkline's paceLabel/pacePhrase; (6) CCRM-76 (Black Room): the
Claude room's dark surfaces become the neutral room's; (7) CCRM-75 (Chart Height): the
Small/Medium/Large chips, chartHeight taking the size, Sparkline's detail level dropping labels
at Small; (8) CCRM-77 (Transposed Chart) as a global Chart orientation chip row (Time across / Time down) under Chart height, default Time across; the Sparkline gains the transposed geometry drawn in wireframe §8½. Tests and assembleDebug green after each commit. Anything the wireframe did not draw: stop and ask Robin,
naming the state. Close out per Convention 4.
```

**Done when:**
- ☑ Every wireframe state is reachable in a debug build; Comfortable + no layout changes = v1.6.
- ☑ CCRM-72 / CCRM-73 / CCRM-74 / CCRM-75 / CCRM-76 / CCRM-25 / CCRM-35 and CCRM-77
  statuses read Built, Fold 7 pass pending.

**Log:**
- 2026-09-17 — four work packages (Sonnet: models, layout sheet; Opus: Sparkline, main screen)
  plus an Opus adversarial review that fixed eight findings and surfaced three design calls,
  all taken: the layout sheet lists only the cards the account has and the invariant counts
  those; folded cards start collapsed, session-only; Reset copy names no cards. 528 tests,
  assembleDebug and the signed assembleRelease green. Not yet on a device.

---

## Step 5 · Fold 7 device pass

**Who:** the orchestrator, with the phone over USB adb. Robin unlocks the phone; nobody stores the
pattern.

**Resume in a fresh session:**

```
Read CLAUDE.md, RUNBOOK.md (Conventions, Step 5), then the Status lines of CCRM-72, CCRM-73,
CCRM-65, CCRM-70, CCRM-71, CCRM-51 and CCRM-6 in ROADMAP.md and BUGS.md entry CCBG-24. Phone
over USB adb; keep the screen awake for the session and restore the timeout before you
disconnect; screencap with -d and the physical display id; crop only the status-bar band.
Install a release-signed build over the live install. Pass, cover screen then inner screen, dark
then light, on the Team Standard account (caps present) and the ChatGPT account (no caps, no
5h): every CCRM-72 and CCRM-73 wireframe state; CCRM-65's Accounts tab (Free notice, renewal
stopped, ChatGPT card, two columns); CCRM-70's Plan Fit block in both panes; CCRM-71's reorder
sheet and the tab that stays put; CCRM-51's status-bar ring at no data, empty rung and 100% if
reachable; the four-account tab strip. Captures to design/research/2026-09-xx-v17-device-pass/.
Fix what is wrong and re-run; if a fix changes an approved layout, stop and show the wireframe
change first. File anything deferred in BUGS.md with a severity. Set every verified item's
Status to Verified on the Fold 7 with the date; close out per Convention 4.
```

**Done when:**
- ☑ Every item above is Verified or has a filed CCBG with a severity.

**Log:**
- 2026-09-17 — run by the orchestrator over USB, cover then inner screen, dark and light, on the
  Team Premium and ChatGPT accounts. Verified: CCRM-72, 73, 74, 75, 76, 77, 25, 35, 65, 70, 71
  and 51. Two bugs found, fixed by sub-agents and re-verified the same evening (`9947b3d`): the
  chart's now divider followed the last poll, not the clock; a downward drag across the layout
  sheet's divider dismissed the sheet. Not observable: CCRM-6's four-account strip (three
  accounts on the phone). Captures in `design/research/2026-09-17-v17-device-pass/`.

---

## Step 6 · Release v1.7

**Who:** the session, following RELEASING.md; Robin confirms "Check for updates" on the phone.

**Resume in a fresh session:**

```
Read CLAUDE.md, RELEASING.md, RUNBOOK.md (Conventions, Step 6), and the Status lines of every
item Step 5 verified. versionCode 23 / versionName "1.7" are already set. Tests green; signed
APK. Docs: fresh main-screen shots (Comfortable and Compact, the 7d toggle on Fable, the layout
sheet) and the Accounts tab into release/docs/src/shots/ — full frame, never cropped to a card;
retire any shot showing the Refresh button; guide.html and brochure.html updated and rebuilt
with release/docs/build.sh, every changed page read in the PDF for clipping; USER-GUIDE.md
changelog; README feature bullets and PDF links. Release notes name CCRM-72, CCRM-73, CCRM-70,
CCRM-71, CCRM-65, CCRM-67, CCRM-68 and the fixed CCBGs, with CCBG-24 (Duet Label Clamp) as
known. Tag, push, gh release; Check for updates on the phone reports v1.7. Set every shipped
item's Status to Shipped v1.7; close out per Convention 4.
```

**Done when:**
- ☑ v1.7 published on GitHub, `releases/latest` resolves to it, the phone agrees.
- ☑ Every item in this arc reads Shipped v1.7 in ROADMAP.md.

**Prepared 2026-09-17, release held.** Robin is daily-driving the 2026-09-17 build for a day
and gives the go-ahead on 2026-09-18 or later. Everything but the last mile is done and
committed: ten `v17-*` screenshots (PNG for the guide, JPG for the README) in
`release/docs/src/shots/`; `release/docs/Cooldown-User-Guide-v1.7.pdf`, `Cooldown-Brochure.pdf`
and `Cooldown-whats-new-v1.7.png` rebuilt and every changed page render-checked; `README.md` and
`release/USER-GUIDE.md` updated (the guide header says *prepared 17 September 2026*);
`release/RELEASE-NOTES-v1.7.md` drafted for `gh release create --notes-file`.

**The last mile, on the go-ahead** (paste into a fresh session):

```
Read CLAUDE.md, RELEASING.md and RUNBOOK.md Step 6. Robin has given the go-ahead for v1.7.
(1) If Robin reports a bug from his day of use, fix it first through a sub-agent, test, and
re-run the affected device check. (2) In release/USER-GUIDE.md change "prepared 17 September
2026" to "released <today>"; if the guide.html footer or cover carries a date, match it and
rebuild with ./release/docs/build.sh (check page 1 and 3 renders). (3) ./gradlew
testDebugUnitTest assembleRelease — green, signed APK at app/build/outputs/apk/release/.
(4) Commit "v1.7 — the main screen redesigned", tag v1.7, push with tags. (5) gh release
create v1.7 app/build/outputs/apk/release/app-release.apk --title "Cooldown v1.7"
--notes-file release/RELEASE-NOTES-v1.7.md. (6) On the phone: Settings → More → Check for
updates reports v1.7; install it over the build from 2026-09-17. (7) Set every item in this
arc to Shipped v1.7 in ROADMAP.md, tick this step, log, commit, push.
```

**Log:**
- 2026-09-17 — prep complete (shots, PDFs, README, USER-GUIDE, release notes) via Sonnet and
  Haiku sub-agents, reviewed here; release deliberately not cut.
- 2026-09-25 — released. Robin reported no bugs from daily driving; guide header set to
  "released 25 September 2026", CCBG-29 (Refresh Swallowed) note reworded. Tests green, APK
  signed with the v1.6 certificate (SHA-256 8bc21a2a…), tag v1.7 at `f1c7789`, published via a
  draft so the asset was verified before it went latest (Astra: concerns, adopted).
  `releases/latest` → v1.7. Arc items set to Shipped v1.7.
