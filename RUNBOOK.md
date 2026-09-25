# Runbook — the v1.8 arc: Widgets, reborn

**Plan status: FROZEN 2026-09-25.** Cross-family review: Astra at xhigh. The seat-edited Fable draft drew blocking twice, so Robin chose a Fable redraft. The redraft drew blocking once (five findings, all adopted), then **concerns** (two, both adopted). See Step 1's Log.

Ordered, checkable steps from `main` as of 2026-09-25 (v1.7 shipped, versionCode 23) to a released
v1.8 that brings home-screen widgets back as a new suite. Items: CCRM-78 (Widgets Reborn) with
CCRM-79 (Ring Face), CCRM-80 (Number Face), CCRM-81 (Countdown Face) and CCRM-82 (Accounts Strip);
CCRM-83 (Ring Renderer); CCRM-84 (Faces Gallery). Ride-alongs: CCBG-24 (Duet Label Clamp), CCRM-14
(Clear History), CCRM-15 (Above-Pace Verification)'s synthetic series, and CCRM-24 (Share Card),
built last — the release does not wait for it.

Earlier arcs are in git history: v1.7 at `4f30dba`, the dropped Play Store arc at `b827dc5`, v1.6 at
`a7dba1b`, v1.5 at `e18cf32`.

**What is different.** Widgets left in v1.6 by CCRM-61 (Settings Diet) and come back on purpose. The
v1.8 section of [ROADMAP.md](ROADMAP.md) answers its four reasons and holds the ten rules (R1–R10) and
fourteen states (S1–S14) every face obeys. **This file never restates a rule or a number that lives in
a spec — it names the section.** If a paste block and a spec disagree, the spec wins and the paste
block is fixed in the same commit. **No widget code, renderer extraction or visible change lands
before Robin approves the wireframe in Step 2.**

**Five things the plan is built on**, each answering a review finding by construction:
1. **Forward-only release** (R9). An installed v1.8 has no downgrade path. Code is reversed with
   `git revert` until it is released; after that, recovery is v1.8.1 through Step 8's own path.
2. **A time is a claim** (R4). No face depends on a redraw arriving; the scheduler (R7) is best
   effort and claims nothing about delivery.
3. **Destructive tests touch disposable data only** (Step 7). Nothing irreversible runs on an account
   whose history Robin has not named as expendable, on the record, in that session.
4. **The phone is restored unconditionally** (Step 7's *Before* and *Always after* blocks); a resumed
   session's first act is the restore.
5. **The gate is by severity and deferrals are accepted explicitly** (Step 7 → 8); the release order
   is commit → build from it → tag and push → draft with `--verify-tag` → verify the asset → publish.

**How this arc is run.** Delegation follows the `model-mesh` skill (CLAUDE.md §4). The session owns
every decision and reviews every diff; whoever writes code runs the git gate and lands the code with
its Status line in one commit; the session ticks this file. The device pass and anything touching
signing stay with the session. A session that runs out mid-arc is resumed by pasting the *Resume*
block of the first unticked step — and, if Step 7 had begun, by running its *Always after* block first.

## Conventions — every session reads this block first

1. **Start from the paste.** Each step's fenced block needs no earlier conversation.
2. **Tests:** `./gradlew testDebugUnitTest` green and `./gradlew assembleDebug` compiling before a
   step closes.
3. **Commits:** straight to `main`; subjects like `feat(CCRM-80): …`, `fix(CCBG-24): …`; the body
   names anything unrelated that rides along. Never stage `ccooldown-release.jks`,
   `keystore.properties` or `local.properties`.
4. **Close-out, in order:** (a) every *Done when* item; (b) the roadmap/bug Status lines; (c) the
   Progress tick (☐ → ☑) and one dated line after **Log:**; (d) commit and push, the tick in the same
   commit as the work.
5. **Handover.** The session's last message: what changed in one paragraph, what Robin must do
   himself before the next step, and the next step's *Resume* block.
6. **Wireframe gate.** Step 2 draws every face × size × state (S1–S14) and every ride-along's visible
   change. Review happens in the `design/` HTML, never in chat; questions go to Robin one at a time;
   silence is not approval. A later step that wants to change anything the user sees stops and goes
   back to the wireframe, naming the file to open.
7. **Mocks keep full functionality.** Nothing existing is drawn simplified. Notification sections use
   the Huge-number style as their base.
8. **The permanent contract** is R9: provider class names and `widget_prefs` keys are fixed at Step 4
   and never renamed.
9. Tracker IDs carry their epic name on first use (CLAUDE.md §1). Two sub-agents never edit the same
   file at once.

## Progress

| Step | Item | Who | Gated on | Status |
|---|---|---|---|---|
| 1 | Plan: concept, Robin's ten answers, CCRM-78–84 filed, Astra ×2, Fable redraft, Astra round 3, freeze | session | — | ☑ |
| 2 | Wireframe: every face × size × state, ride-alongs, the recovery face, reviewed to approval | sub-agent draws · session reviews · Robin approves | 1 | ☑ |
| 3 | Pure layer: RingRenderer, Surfaces, WidgetFace, Transitions, WidgetPrefs — no visible change | builder · session reviews | 2 | ☑ |
| 4 | Widgets: four providers, config, two receivers, the alarm | builders · session reviews | 3 | ☐ |
| 5 | Ride-alongs: CCBG-24, CCRM-14, CCRM-15 synthetic series, CCRM-84 gallery | builders · session reviews | 3 | ☐ |
| 6 | Share card (CCRM-24), only if Step 2 said build — the release does not wait | builder · session reviews | 3 | ☐ |
| 7 | Fold 7 device pass, phone restored, release gate | session · phone over USB · Robin unlocks | 4, 5 | ☐ |
| 8 | Release v1.8 | session · Robin at the phone | 7's gate | ☐ |

---

## Step 1 · Plan, file, review, freeze

**Who:** the planning session, 2026-09-25.

**Done when:**
- ☑ The Fable concept was checked against the repo (the removal commit `d599b87`,
  `UsageIcon.railsGauge`, the 7-tap unlock, versionCode 23).
- ☑ Robin's ten answers are recorded in CCRM-78–82, CCRM-14, CCRM-15, CCRM-24 and CCBG-24.
- ☑ CCRM-78–84 are filed and the CCRM-61 (Settings Diet) reopening note is written.
- ☑ Astra rounds 1 and 2 (both blocking) are recorded, and the Fable redraft is adopted in full into
  ROADMAP.md and this file.
- ☑ Astra round 3 at xhigh on the redraft returns non-blocking — or every remaining finding is
  answered in the text and Robin accepts the residual on the record. Then **FROZEN** replaces DRAFT
  in the status line, with the date.

**Reversal:** documentation only — `git revert` of the planning commits.

**Log:**
- 2026-09-25 — Fable (mesh-expert) drafted the concept. The session checked it against the repo, and
  Robin answered Q1–Q10 one at a time. Q4 was changed from the recommendation to a per-widget
  Background toggle. Q6 became try-H:MM-else-H:MM:SS, with no military time. Q10 goes to the
  wireframe. CCRM-78–84 were filed and this runbook was written.
- 2026-09-25 — Astra xhigh round 1 **blocking** (5: no downgrade path, the destructive test on live
  history, the severity gate, unbounded alarms, the empty registry). All were adopted. Round 2
  **blocking** (4: release order, static relative times, a stale Step 4 instruction, phone-state
  restore). All were adopted, and adoption stopped per model-mesh §2.
- 2026-09-25 — Robin chose a **Fable redraft** (one spec in ROADMAP R1–R10 / S1–S14, with the runbook
  pointing at it). Astra xhigh on the redraft: **blocking** (5: restore recorded phone values rather
  than defaults, asset hash/signer/versionCode identity, unique PendingIntent identity per widget and
  action, re-arm on every schedule change, the wrapped Binder exception and the whole-map budget).
  All were adopted. Re-review: **concerns** (2: `onDisabled` is per provider, and the fallback is one
  bounded attempt). Both were adopted. Plan FROZEN.
- Open for Robin at Step 2, raised by the redraft: Q11 (an absolute "as of 9:10 PM" stamp on the
  larger faces), the synthetic marker's form, and the Countdown no longer following the Reset time
  chip (its absolute time is always shown). *All three were decided at Step 2 (rev D, 2026-09-25).*

---

## Step 2 · Wireframe — every face, size and state, reviewed to approval

**Who:** a sub-agent draws; the session reviews against ROADMAP.md before Robin sees it; Robin
approves, one question at a time.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 2; ROADMAP.md's v1.8 section in full — the rules
R1–R10, the states S1–S14, CCRM-78 (Widgets Reborn) to CCRM-84 (Faces Gallery); CCRM-14 (Clear
History), CCRM-15 (Above-Pace Verification), CCRM-24 (Share Card); BUGS.md CCBG-24 (Duet Label
Clamp). Open design/2026-09-17-main-screen-redesign.html for conventions (CSS tokens, .phone
frame, numbered h2 sections, a "For review" banner) and design/widget-wireframes.html for the
v1.5 prior art not to clone. Brief a sub-agent to draw design/2026-09-2x-widgets-reborn.html with
real dp/sp: (1) each face at each size — Ring 1×1/2×2, Number 2×1/4×1/4×2, Countdown 2×1/2×2,
Strip 4×1/4×2 — on the Fold 7 cover (4×2 = 363×168 dp) and inner (~118×100 dp cells, marked
unverified), dark and light, Solid and Transparent over a light and a dark wallpaper; (2) every
state S1–S14 on every face — including S6 reset passed, S13 the synthetic marker (propose its
form) and S14 the unavailable face — with what drops out at each narrow size; (3) Ring 2×2 at
100% both ways, × and "100%" (Q10); (4) the Countdown's forms: H:MM and H:MM:SS, MM:SS under one
hour, the negative past-zero count beside its clock time, the Weekly absolute form and its
last-24-h live form, the "~ runs out" line present and absent; (5) Number 4×1's absolute
sub-line and 4×2's control row — chips only for the windows the account has, the fixed tag for a
single-window account, the account cycler; (6) the config activity (Account, Window, Background)
and the reconfigure view; (7) picker names and previews (proposed: Ring / Number / Countdown /
All accounts); (8) Q11 — an absolute "as of 9:10 PM" stamp on the faces with room, drawn present
and absent, for Robin to keep or drop; (9) the ride-alongs: the CCBG-24 measured clamp on the
Duet collapsed row (Huge-number base, "ChatGPT 98%" fitting in Left mode), the CCRM-14 card ⋮
item and its confirm dialog, the CCRM-15 chip row, the tap-to-turn-off SYNTHETIC DATA banner and
the notification strip, the CCRM-84 gallery screen, and the CCRM-24 share card with its preview
dialog and ⋮ entry. Review it yourself first: every state on every face, nothing clones an app
card, no combined figure anywhere on the Strip, every time absolute or live (R4), every clock
time 12-hour (R2). Then name the file to Robin and ask one question at a time. On "change X",
revise (rev B, C …) and show again. No code. When Robin says approved, write the revision
letter, the date and every decision — Q10, Q11, the marker's form, the S14 wording, share card
build or defer — into the items' Status lines; close out per Convention 4.
```

**Done when:**
- ☑ Robin has said "approved" to a named revision — rev D, 2026-09-25.
- ☑ Every decision taken in review is written into CCRM-78–84, CCRM-14, CCRM-15, CCRM-24 and CCBG-24.
- ☑ The share card is marked *build* or *defer* — Robin's call: **build**, last, the open account only.

**Reversal:** documentation only.

**Log:**
- 2026-09-25 — A Sonnet sub-agent (mesh-builder) drew rev A of `design/2026-09-25-widgets-reborn.html`.
  The session's review found 19 defects before Robin saw it: false chronometers on the Countdown
  S4/S5/S6, "Wkly", Strip states applied to every ring, simplified Main and notification mocks, and
  others. The same agent drew rev B. Robin replaced the needle with a pace tick while viewing, then
  asked for a bolder tick, round arc ends, Solid / Gradient / Transparent, and the Number label and
  figure on one row. The session drew those itself as rev C, together with a layout pass; it stopped
  the sub-agent, which had resumed on a queued message and was editing the same file.
- 2026-09-25 — Robin answered one at a time: Q10 spent ×; Q11 keep the stamp where drawn; S13
  ribbon + dot; S14 two lengths; the Transparent soft shadow; share card build, the open account
  only; the Countdown ignores the Reset time chip; Countdown 2×1 Weekly "Sat 9:10 PM". He delegated
  the remaining fit calls to a fresh Fable judge (mesh-judge, model fable), which approved three
  and changed two (Strip cover rings recorded as Ø53 / Ø80, as drawn; the UNASSIGNED pill raised to
  8 sp). It also found the Weekly tag vanishing at 100%, which is now drawn. **Rev D approved by
  Robin, 2026-09-25**, and the decisions were written into CCRM-78–84, CCRM-14, CCRM-15, CCRM-24 and
  CCBG-24.
---

## Step 3 · Pure layer — no visible change

**Who:** a builder (the model-mesh ladder picks the rung); the session reviews the diff and runs the
tests.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 3; ROADMAP.md v1.8 rules R4, R7 and R10, CCRM-78
(Widgets Reborn) §Where, and CCRM-83 (Ring Renderer); the approved wireframe named in CCRM-78's
Status line. Read ui/UsageIcon.kt, ui/BarRenderer.kt, notify/PinnedNotification.kt,
alerts/Alerts.kt, work/Polling.kt and every PinnedNotification.update call site (grep — 13).
Pattern only: git show 530781f:app/src/main/java/com/robin/claudeusage/widget/WidgetFace.kt and
ui/RingRenderer.kt. Build, one commit each: (a) ui/RingRenderer.kt per CCRM-83, UsageIcon.draw
delegating at 24 dp, with a bitmap-equality test for every state the icon draws; (b)
notify/Surfaces.kt — refresh(context, cache) replacing all 13 call sites, its widget half and
arm() no-ops until Step 4; (c) widgets/WidgetFace.kt — the pure state table (S1–S14), the
buckets, render(face, bucket, state) returning one single-size RemoteViews, and bitmapBytes,
with WidgetFaceTest over every state × bucket and the R10 budget at both Fold 7 densities; (d)
widgets/Transitions.kt — nextTransitionAt per R7, tested for each of its three kinds, the
earliest-wins case, a past reset and the empty case; (e) widgets/WidgetPrefs.kt
(w<id>.account/.window/.bg/.v; an empty account is the unassigned state, R5). No manifest
entries, no providers, no layout the user can reach. Tests green; assembleDebug.
```

**Done when:**
- ☑ All five pieces are merged with tests; `UsageIcon` renders byte-identically at 24 dp.
- ☑ No `<receiver>` or `<activity>` for a widget is in the manifest; nothing the user sees changed.

**Reversal:** `git revert` of the step's commits.

**Log:**
- 2026-09-25 — Built by the session (model-mesh rung 1) in six commits: (a) `ui/RingRenderer.kt`, NEEDLE + rev D TICK, `UsageIcon.draw` a 24 dp call into it, `RingRendererTest` pixel-equal to a frozen pre-extraction copy over 16 states × both themes × three densities; (b) `notify/Surfaces.kt`, all 13 call sites moved, `SurfacesSeamTest`; (c) `widgets/WidgetFace.kt` — `FaceStates` (S1–S14), nine cover buckets, `render`, `bitmapBytes`, layouts generated by `tools/widget_layouts.py` — with `WidgetFaceTest` over every state × bucket × background × theme on API 31 and 36, and `Fmt.widgetClock` (R2); (d) `widgets/Transitions.kt`; (e) `widgets/WidgetPrefs.kt`. Robolectric 4.17 added test-only. A fresh Opus judge returned **concerns** (no blocking): `View.setAlpha` through RemoteViews, a doubled "Stale" on the Countdown, bars stretched by `fitXY` — all fixed in a follow-up commit. **Open for Step 4:** (1) the widget bar's pace tick — the wireframe draws 3 dp, 90% ink with a 1 dp halo, but CCRM-83 says `BarRenderer` is unchanged, so the bars carry the notification's tick until Robin picks one; (2) the Number figure is drawn in the severity colour (R3, the notification's Huge number) where the wireframe's mock is plain ink; (3) a signed-in account with no payload yet reads "No reading yet" (the notification's words), a state the wireframe does not draw; (4) the "as of" stamp gains its weekday only on a redraw, so after 24 h without one it can read as today (already dimmed Stale by then) — R7's three alarm kinds do not cover it; (5) both Fold 7 screens are recorded at 420 dpi, so R10 is tested at 2.625 (twice) plus 3.5 for headroom.

---

## Step 4 · Widgets

**Who:** builders, one face per agent where files do not overlap; the manifest,
`WidgetConfigActivity`, both receivers and `res/xml` belong to one agent (or the session merges
them). The session reviews each face against the approved wireframe, state by state.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 4; ROADMAP.md v1.8 rules R1–R10, states S1–S14,
and CCRM-78 (Widgets Reborn) to CCRM-82 (Accounts Strip) in full; the approved wireframe. Those
sections are the spec and this block is not — build exactly what they say: the four providers
with R9's class names, each composing its size map from WidgetFace.render; WidgetConfigActivity
per CCRM-78 §Config; WidgetActionReceiver and WidgetSystemReceiver per CCRM-78 §On-face controls
and R7; Surfaces.arm and the one alarm per R7, with R10's fallback; the Countdown per CCRM-81
(Countdown Face), recording in its Status line which chronometer form shipped and why; res/xml
info files with updatePeriodMillis 0 and previewLayout. One commit per face plus one for the
shared plumbing. Anything the wireframe does not show: stop and ask, naming the file. Tests
green; assembleDebug.
```

**Done when:**
- ☐ Four providers are in the manifest, each placeable in a debug build and matching the approved
  wireframe state by state, in an emulator or on the phone.
- ☐ Every trigger R7 names is wired and each was exercised once in the emulator; there is exactly
  one alarm, and it is cancelled when the last widget is removed.
- ☐ R10's fallback has tests that force it through `SafeUpdate` with each of the three exception
  shapes, and `bitmapBytes` is tested over each face's whole size map.
- ☐ Two Number 4×2 widgets on different accounts: a tap on either's chips or cycler changes only that
  widget (unique PendingIntent identity), and every config save, toggle, delete and restore re-arms
  the one alarm over all placed widgets. Removing the last Number while a Countdown stays keeps the alarm armed.
- ☐ CCRM-78–82 read Built, with the chronometer form recorded in CCRM-81.

**Reversal before release:** `git revert`. After release: R9 — forward-only, see Step 8.

**Log:**

---

## Step 5 · Ride-alongs

**Who:** builders. **One agent owns `SettingsScreen.kt`** (CCRM-14 and CCRM-15 both touch it);
another does CCBG-24; another the gallery. The session reviews.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 5; BUGS.md CCBG-24 (Duet Label Clamp);
ROADMAP.md CCRM-14 (Clear History), CCRM-15 (Above-Pace Verification), CCRM-84 (Faces Gallery)
and rule R8; the approved wireframe's ride-along sections. Build, one commit each: CCBG-24 —
measure the 30 sp bold figure with Paint.measureText in the device font and clamp the label to
the remainder, Duet.labelClampDp pure and tested; CCRM-14 — "Clear usage history…" in the
Accounts card ⋮ with the confirm dialog naming both stores, HistoryStore.clear +
SessionLog.clear for that profile, then Surfaces.refresh; CCRM-15 — data/SyntheticSeries per R8,
applied where UsageRepository hands out a snapshot, the Debug chip row, the tap-to-off banner,
the notification strip, the widget marker, and a unit test that no store changes while it is on;
CCRM-84 — the gallery per its entry. Tests green.
```

**Done when:**
- ☐ CCBG-24 reads Fixed, pending device verification; CCRM-14, CCRM-84 and CCRM-15's series read
  Built.
- ☐ Synthetic Off returns every surface to real data without a restart; `am kill` in the emulator
  does the same; the stores test passes.

**Reversal:** `git revert`.

**Log:**

---

## Step 6 · Share card (only if Step 2 marked it *build*)

**Who:** a builder; the session reviews.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 6; ROADMAP.md CCRM-24 (Share Card) and the
approved share-card section. Build share/ShareCard.kt at 4× (1440 px wide) from RingRenderer,
BarRenderer and a ChartBitmap adapted from git show 530781f:.../widget/ChartBitmap.kt; the
FileProvider (res/xml/share_paths.xml, cache-path "share/") with FLAG_GRANT_READ_URI_PERMISSION
on the chooser intent; every earlier file in cacheDir/share deleted before each render and the
folder emptied on app start; the render-then-preview dialog; ACTION_SEND; "Share snapshot" in
the Main ⋮ menu. Privacy: the profile label only — no email, no plan tier; nothing written
outside app cache. Tests green; one commit.
```

**Done when:**
- ☐ Built and matching the wireframe, or recorded as deferred to v1.8.1 without blocking Step 7.

**Reversal:** `git revert`.

**Log:**

---

## Step 7 · Fold 7 device pass

**Who:** the session, phone over USB adb. Robin unlocks the phone; nobody stores the pattern. Keep
the screen awake for the whole session and put auto-off back before disconnecting.

**Before anything else — record the phone's state** into
`design/research/2026-09-xx-v18-device-pass/phone-state-before.txt`:

```
adb shell settings get system screen_off_timeout
adb shell settings get global airplane_mode_on
adb shell settings get global auto_time
adb shell settings get global auto_time_zone
adb shell getprop persist.sys.timezone
adb shell date +%s
adb shell settings get global wifi_on
adb shell settings get global mobile_data
adb shell dumpsys deviceidle | grep -E "mForceIdle|mState"
adb shell dumpsys battery | grep -E "USB powered|AC powered"
```

Also note the host's `date +%s` beside the phone's, so a manual clock (auto_time 0) can be
restored to the same offset from real time.

The synthetic series is Off before the pass — a fresh install of the release build guarantees it.

**Always after — on success, failure or a cut-short session; a resumed session runs this first:**

```
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
adb shell cmd connectivity airplane-mode <enable|disable — the recorded airplane_mode_on>
adb shell svc wifi <enable|disable — the recorded wifi_on>
adb shell svc data <enable|disable — the recorded mobile_data>
adb shell cmd alarm set-timezone <the recorded persist.sys.timezone>
adb shell settings put global auto_time <recorded value>
adb shell settings put global auto_time_zone <recorded value>
# only if auto_time was 0: set the clock to host-now + the recorded phone−host offset
#   (adb shell cmd alarm set-time <ms>, or by hand in Settings if the shell refuses)
adb shell settings put system screen_off_timeout <recorded value>
```

Each value is the one **recorded**, never a default; a line whose recorded value was not captured
stops the session and asks Robin.

then, in the app, synthetic series Off (tap the banner if it shows); then re-run the *Before*
commands and diff against the file: every line matches (the phone's clock within 5 s of the
recorded offset from host time) before the cable comes out.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 7 in full — run the Always-after block first if
a previous session began this step; the Status lines of CCRM-78 (Widgets Reborn) to CCRM-84
(Faces Gallery), CCRM-14 (Clear History), CCRM-15 (Above-Pace Verification), CCRM-24 (Share
Card) and BUGS.md CCBG-24 (Duet Label Clamp). Record the Before state. Install the
release-signed build over the live install. Then: measure the real cell sizes on both screens
(dumpsys appwidget) and record them in CCRM-78; place every face at every size on cover and
inner; fold, unfold, resize; restart the launcher; check One UI's transparency, corner radius,
picker previews and configuration_optional; drive every state through the CCRM-15 chips and the
CCRM-84 gallery, confirming the widget marker and the banner-off path; confirm the chronometer
form, MM:SS under an hour and the negative count; confirm the status-bar ring is
pixel-equivalent to design/research/2026-09-17-v17-device-pass/24-main-statusbar.png (CCRM-83
(Ring Renderer)); check CCBG-24's "ChatGPT 98%" in Left mode; the share card if built.
Scheduling (R7): adb shell dumpsys battery unplug, then dumpsys deviceidle force-idle across a
real reset — record the alarm delay and the S6 face; reboot and confirm the alarm is re-armed
(dumpsys alarm | grep claudeusage); adb shell cmd alarm set-time / set-timezone (by hand in
Settings if the shell refuses) and confirm every chronometer and absolute time redraws; airplane
mode past STALE_DATA_MS and confirm the stale dim arrives on its own; adb shell am kill
com.robin.claudeusage and confirm the widgets survive and synthetic resets to Off — never am
force-stop, which puts the app in Android's stopped state and is not a defect. Note whether
Cooldown sits in One UI's sleeping-apps list. CCRM-14: run the destructive branch only on an
account Robin names as expendable in this session, on the record; otherwise verify the dialog
to Cancel and rely on the unit test plus a debug-build emulator run. Screencap with -d and the
physical display id; captures to design/research/2026-09-xx-v18-device-pass/. Fix and re-run; a
fix that changes an approved layout goes back to the wireframe first. File every deferral in
BUGS.md with a severity. Set verified items to Verified on the Fold 7 with the date. Run the
Always-after block. Close out per Convention 4.
```

**Done when:**
- ☐ Every item above is Verified or has a filed CCBG with a severity.
- ☐ The *Always after* block has run and the diff against `phone-state-before.txt` is empty.
- ☐ **Release gate:** no High-severity CCBG from this pass, or still open against an arc item, is
  unresolved — a High blocks Step 8 until fixed and re-verified. Every Medium and Low deferral is
  listed in the Log with Robin's words accepting it; silence is not acceptance.

**Reversal:** the *Always after* block restores the phone; code fixes are `git revert`.

**Log:**

---

## Step 8 · Release v1.8

**Who:** the session; Robin confirms "Check for updates" on the phone. **Irreversible once
published: an installed v1.8 cannot be downgraded (R9).**

**Resume in a fresh session:**

```
Read CLAUDE.md; RELEASING.md; RUNBOOK.md Conventions and Step 8; the Status lines Step 7
verified. Set versionCode 24 / versionName "1.8". Docs: widget shots on the home screen (full
frame, never cropped to a card); guide.html and brochure.html rebuilt with release/docs/build.sh
and every changed page read in the PDF; USER-GUIDE.md changelog; README bullets; release notes —
widgets are back as a new suite, v1.5 placements do not return so add the new ones from the
picker, widget-only users add Cooldown to One UI's Never sleeping apps, CCBG-24 (Duet Label
Clamp) fixed, Clear history, the share card if shipped. Replace RELEASING.md §4–5 with the six
lines below, so the order is written once from now on, and record there the **trusted signer
digest** — taken before this release from the published v1.7 asset (gh release download v1.7;
apksigner verify --print-certs) — which step 5 compares against. Then, strictly in this order:
  git add -A && git commit -m "v1.8 — widgets, reborn"                           # 1 release source
  ./gradlew assembleRelease                                                      # 2 built from that commit
  git tag v1.8 && git push && git push origin v1.8                               # 3 tag on it, both pushed
  gh release create v1.8 app/build/outputs/apk/release/app-release.apk \
     --draft --verify-tag --title "Cooldown v1.8" --notes-file <notes>           # 4 draft bound to the pushed tag
  gh release download v1.8 -p '*.apk' -D /tmp/v18                               # 5 the asset is the build:
     shasum -a 256 app/build/outputs/apk/release/app-release.apk /tmp/v18/app-release.apk  #   hashes equal
     apksigner verify --print-certs /tmp/v18/app-release.apk                     #   signer SHA-256 = RELEASING.md's trusted digest
     aapt dump badging /tmp/v18/app-release.apk | head -1                        #   package com.robin.claudeusage, versionCode 24, versionName 1.8
     # any mismatch: stop, gh release delete v1.8 (still a draft), nothing was published
  gh release edit v1.8 --draft=false                                             # 6 publish, last
Check for updates on the phone reports v1.8. Set shipped items to Shipped v1.8; close out per
Convention 4.
```

**Done when:**
- ☐ v1.8 is published, `releases/latest` resolves to it, and the phone agrees.
- ☐ Every arc item reads Shipped v1.8, or Deferred with a reason; RELEASING.md carries the six-line
  order.

**If it goes wrong mid-way:**
- After 1–3, no draft yet: nothing is public. Fix forward, or `git revert`; move the tag only if it
  must move (`git tag -d v1.8 && git push --delete origin v1.8`).
- After 4–5 with a bad asset: `gh release delete v1.8 --yes` — a draft was never public.
- **After 6 — two distinct recoveries.** *Withdraw distribution:* `gh release edit v1.8 --draft`, so
  `releases/latest` falls back to v1.7; installed phones are untouched. *Recover installed apps:*
  v1.8.1 (versionCode 25) per R9, keeping all four provider components, released through this step.

**Log:**

---

## Risks and partial states

- **Unverified until Step 7:** the inner-screen cell sizes; One UI's handling of
  `configuration_optional` and `previewLayout`; whether the chronometer can drop seconds (expected
  no → H:MM:SS); the real inexact-alarm delay under Doze; that `cmd alarm set-time` works from the
  adb shell on One UI; the host's exact bitmap cap.
- **Partial execution leaves:** after Step 3, inert code (no providers, no manifest entries); after
  Steps 4–6 without 7, unverified but unreleased code. Each step is independent commits, reversed with
  `git revert` in reverse order. Nothing outside git changes until Step 7 (phone state — the *Always
  after* block) and Step 8 (irreversible).
- **Not reversible:** R9's names and keys once released; an installed v1.8; CCRM-14 (Clear History)'s
  clear on whatever account it runs on.
- **Accepted by design:** a percentage on a face is a snapshot until the next redraw (R4); the
  notification's own relative reset line has the same trait and is outside this arc.
