# Runbook — the dual-identity arc (v1.5 → v1.6)

Ordered, checkable steps to take Cooldown from v1.5 to v1.6: the settings diet, the two-account
always-on notification, the new icon and the two-rooms app. Every design decision is taken;
nothing here waits on a wireframe. The v1.5 runbook (multi-provider arc, all steps ticked) is in
git history at commit `e18cf32`.

**How this arc is run.** One **Fable** session is the orchestrator: it holds every decision,
writes the sub-agent briefs, reviews their output, runs the tests, commits, and ticks this file.
The labour goes to sub-agents picked by complexity: **Opus** for high (protocol, layout
constraints, the surgery where a wrong cut silently breaks a feature), **Sonnet** for medium
(ordinary implementation with tests, mechanical removals with judgement), **Haiku** for low
(copy sweeps, tracker status edits, screenshot plumbing). The orchestrator may do a piece itself
when it can do it better than a sub-agent. If the Fable session runs out of context or credits
mid-arc, a fresh Fable session resumes from this file: paste the *Resume* block of the first
unticked step.

**Where the detail lives.** This file says *what order, which tier, and what to paste*. The specs
are the roadmap items — [ROADMAP.md](ROADMAP.md), section *Dual identity and diet*: CCRM-60
(Dual Identity), CCRM-61 (Settings Diet), CCRM-62 (Duet Notification) — the two approved
wireframes `design/dual-identity-wireframe.html` and `design/settings-diet-wireframe.html`
(their *Decisions* tables are binding), and the removal footprint in
`design/research/2026-09-08-removal-audit.md`. Don't duplicate the specs here; if a spec turns
out wrong, fix it in ROADMAP.md and note it in the step's *Log* line.

## Conventions — every session reads this block first

1. **Start from the paste.** Each step has a fenced *Resume in a fresh Fable session* block. It
   tells the session what to read; it never needs an earlier conversation. Within a step the
   orchestrator briefs sub-agents with the decisions baked in, in parallel where the files don't
   overlap, and reviews every result before it lands.
2. **Tests:** `./gradlew testDebugUnitTest`. Green before a step closes. `./gradlew
   assembleDebug` must also compile; the removals in Step 1 touch the manifest and gradle.
3. **Commits:** straight to `main`, subject `feat(CCRM-NN): …` / `refactor(CCRM-NN): …` /
   `docs(CCRM-NN): …`, body naming any unrelated work riding along. Never stage
   `ccooldown-release.jks`, `keystore.properties`, `local.properties`.
4. **Close-out, in this order:** (a) satisfy every item in the step's *Done when* list;
   (b) set the roadmap item's **Status** line; (c) in this file, tick the step's box in the
   Progress table (☐ → ☑) and write one dated line after its **Log:**; (d) commit and push —
   the tick rides in the same commit as the work.
5. **Handover — the last thing every session does.** After the commit, the final message says
   what changed in one paragraph, anything Robin has to do himself before the next step (phone
   in hand, a screenshot to approve, the keystore), then the next step's *Resume* block copied
   from this file. If this step's outcome changes the next step, edit the next step's block in
   this file first, commit that too, then print the edited block.
6. **Wireframe gate.** Working agreement 2 is satisfied for this arc by the two approved
   wireframes. If a session wants to draw something they don't show, it stops and asks Robin
   first, in one AskUserQuestion, naming the file to open. Mocks of existing elements must keep
   every function the element has today (the trend chart keeps its guides, axis and projection).
7. **Removal rule.** Delete the `when`, not its arms; keep what the audit marks *must stay*
   (`Alerts.evaluate` as the per-poll pinned re-render, `checkReset` as the history writer,
   `BarRenderer`/`BarGeometry`/`RingGeometry`/`Sparkline`). Where `refreshWidgets()` was the only
   refresh on a settings chip, add `PinnedNotification.update` in its place (CCBG-14 (Stale
   Notification Theme)).
8. **Wording.** On the notification and any tight surface: "5h" and "Weekly". Tracker IDs carry
   their epic name on first use.

## Progress

| Step | Item | Sub-agent tier | Needs Robin | Status |
|---|---|---|---|---|
| 1 | CCRM-61 (Settings Diet) part 1 — the removals | Opus (alerts surgery) · Sonnet (widgets, tile, styles, glyphs) · Haiku (tracker statuses) | no | ☑ |
| 2 | CCRM-61 (Settings Diet) part 2 — four-tab Settings and the new rows | Sonnet | no | ☑ |
| 3 | CCRM-62 (Duet Notification) — two accounts, 5h/Weekly, clean ring with picker | Opus | one look at the shade | ☑ |
| 4 | CCRM-60 (Dual Identity) — icon, top-bar glyph, two rooms | Sonnet (rooms) · Opus (icon vectors) | approve the 48 dp icon render | ☑ |
| 5 | Device pass on the Fold 7 | Sonnet | phone in hand | ☑ |
| 6 | Release v1.6 — docs diet, screenshots, tag | Sonnet · Haiku (copy sweeps) | keystore, upload | ☐ |

Order matters: 2 needs 1 (the sections it rebuilds are gone), 3 needs 1 (styles and glyphs
gone), 4 needs nothing but is last so the icon lands on the final app; 5 needs 1–4; 6 needs 5.
Within Step 1 the three removal areas touch different files except `SettingsScreen.kt`,
`UsageCache.kt`, `AndroidManifest.xml` and `PinnedNotification.kt`: give those four files to one
agent at a time.

---

## Step 1 · CCRM-61 (Settings Diet) part 1 — the removals

**Tier:** Opus for `alerts/Alerts.kt`, `notify/Conditions.kt`, `UsageCache.kt`'s alert block and
the reset-ping per-account rework · Sonnet for the widget package, the Quick Settings tile, the
three pinned styles and three glyphs · Haiku for tracker status lines.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, then RUNBOOK.md — its Conventions block and Step 1 — then ROADMAP.md item
CCRM-61 (Settings Diet) in full, then design/research/2026-09-08-removal-audit.md in full (it
is the map of every touchpoint, with the four things in alerts/Alerts.kt that must stay).
You are the orchestrator: brief sub-agents, review, test, commit. Three removal areas, in this
order because they share four files: (A) Sonnet — widgets: the widget/ package,
ui/RingRenderer.kt, app/src/debug/**, the five res/xml/*_widget_info.xml, the manifest
receivers and config activity, the Glance lines in app/build.gradle.kts, WidgetRedrawWorker
and its scheduling in work/Polling.kt, updateAll/updateWidgets in MainActivity and
UsageRepository, WidgetPrefs and the two widget prefs in UsageCache, the refreshWidgets
parameter and its nine call sites in SettingsScreen (add PinnedNotification.update where it
was the only refresh), the eight widget strings, both widget tests; also the Quick Settings
tile: tile/UsageTileService.kt and its four manifest declarations. (B) Opus — alerts:
alerts/Alerts.kt edited down, keeping evaluate (ending in PinnedNotification.update),
checkReset's SessionLog/windowPeak/lastSeenWindowKey bookkeeping AND its reset-ping notify
branch with a per-account resetPingMode(profile, window), STALE_DATA_MS, notifId/MAX_KIND
(re-anchored)/cancelAllFor; delete threshold, pace, auth, stale, ping alerts, six channels
(keep reset_alerts and pinned_usage_v2; delete the six orphaned channels once at startup
with deleteNotificationChannel), UpdateNotification.maybePost and UpdateSkipReceiver, all of
ping/ and data/PingSchedule.kt, notif_alert*.xml, the fold machinery (FoldedEvent store,
StripRules, Conditions.foldedInto/revocable/nextExpiry, PinnedNotification.armExpiry,
ACTION_EXPIRE), the alert prefs listed in the audit (authAlertsEnabled goes and the re-auth
strip becomes unconditional), Projection's pace ladder with PaceTest, the exact-alarm and
boot permissions; the Settings "Notifications" section and its helper composables except
what Step 2 rebuilds. (C) Sonnet — styles and glyphs: in PinnedNotification delete the
when(style) wholesale so "big" is the only path, plus bigPicture/drawNumberTile/drawGauge and
drawStatusIcon's style parameter; in ui/UsageIcon.kt the pie/battery/number arms, clearCircle
and the PIE constants; pinnedStyle and pinnedIconStyle in UsageCache; the two chooser blocks in
SettingsScreen. Then Haiku: mark the superseded tracker entries listed in CCRM-61's Status
(ROADMAP.md and BUGS.md) with one dated line each. ./gradlew assembleDebug and
testDebugUnitTest green; fix ProfileRegistryTest's MAX_KIND anchor and any test that imported
a deleted class. Close per the Close-out rule (commit as refactor(CCRM-61): remove widgets,
tile, alerts, styles), then follow the Handover rule and print Step 2.
```

**Done when:**
- ☑ No `androidx.glance` import, no `widget/` package, no tile service, no `ping/` package.
- ☑ `Alerts.kt` ≈ 130–180 lines: `evaluate`, `checkReset` (with per-account reset ping),
  `STALE_DATA_MS`, `notifId`, `MAX_KIND`, `cancelAllFor`, nothing else.
- ☑ `PinnedNotification` has one style path (Huge number) and one glyph (Ring); the panel still
  re-renders on every poll.
- ☑ History screen still gains points at a window rollover (read `SessionLog` write path).
- ☑ Six old channels deleted on first launch after upgrade; `reset_alerts` and
  `pinned_usage_v2` remain.
- ☑ Tests green, tracker statuses updated, ticked, committed, pushed.

**Log:**
- 2026-09-09 — Done in one Fable session: Sonnet (widgets + tile), Opus (alerts), Sonnet (styles +
  glyphs) ran in sequence on the shared files, Haiku did the tracker lines in parallel. 67 files,
  −7 837 / +456 lines; tests 368 → 287 (the 81 lost are the deleted widget, pace, ping, strip and
  `UpdateGate.shouldNotify` tests). `Alerts.kt` is 231 lines total, 141 of code — the 130–180
  target was met on code, the KDoc pushes the total up. Deviations from the paste, all small:
  the per-account reset-ping key is `resetPing<Window>` (not `resetMode<Window>`, which the
  legacy unprefixed account would have shared with the global fallback); `ensureChannels` went
  private; `Conditions.forProfile` and `UsageIcon`'s mono path went too (no callers once the
  widgets and tile left); the Always-on toggle's subtitle lost its "all alerts fold into this
  panel" clause and the App log explainer lost "ping alarms" — copy that had become untrue, not
  design. `UpdateGate.trimNotes` is now uncalled but still tested; left for Step 6's docs diet
  to decide. The Notifications card now shows per-account 5h / Weekly reset-mode rows and the
  System settings link only; the Pinned card lost its style and glyph choosers with no
  replacement copy — Step 2 rebuilds both to the wireframe.

---

## Step 2 · CCRM-61 (Settings Diet) part 2 — four-tab Settings

**Tier:** Sonnet.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, then RUNBOOK.md — its Conventions block, Step 1's Log and Step 2 — then
ROADMAP.md item CCRM-61 (Settings Diet) "Settings become four swipeable tabs" and "Reset pings
survive", then open design/settings-diet-wireframe.html section 1 ("After: four swipeable
tabs", the four 410 dp mocks, the 750 dp Appearance mock, the three states) and its Decisions
table — approved; build to it. Brief one Sonnet agent to recast SettingsScreen.kt as a fixed
TabRow of four tabs (Accounts, Alerts, Appearance, More; tab padding 10 dp a side, scrollable
TabRow fallback noted in code) over a HorizontalPager, the pattern MainActivity.ProfileTabs
already uses, keeping the existing card composables and moving them into the tabs as drawn:
Accounts = account cards + Add account; Alerts = Always-on toggle, "Show accounts" as two chip
rows First and Second (Second has a None chip; drives cache.pinnedProfile and a new
pinnedSecondProfile), "Tapping a number opens", "Status-bar ring shows" (First / Second /
Whichever is higher, new pref), System notification settings link, then "Reset pings" per
account with 5h reset and Weekly reset mode chips (Off / If busy / Always); Appearance =
Theme, Time format, Usage display, Reset time, ONE "Show red past the pace mark" toggle (one
pref read by the app bars and the notification; migrate from paceOverInApp), Theme colour;
More = Polling, Usage credits (rendered only when an account reports credits), Updates,
Diagnostics, About, Debug once unlocked. Inner screen: a tab's content goes two-column as
Settings does today.
Step 1 already left, reuse rather than redo: `UsageCache.resetPingMode(profile, window)` /
`setResetPingMode(profile, window, mode)` (per-account key `resetPing<Window>`, falling back
to the legacy global key, then Smart/Always defaults) and a per-account `ResetModeRow(label,
profile, window, cache)` with Off / If busy / Always segmented buttons; `paceOverInApp` and
`paceOverOnNotification` both still exist and both must migrate into the one new pref; the
Pinned card has no style or glyph chooser any more and no explainer under "Show profile". Keep every explainer sentence the wireframe keeps; drop the ones it drops.
Update the guide screen's back navigation (Settings → Guide → back lands on the Accounts tab).
Tests green; add a unit test for the pref migration. Close per the Close-out rule (commit as
feat(CCRM-61): four-tab Settings), then follow the Handover rule and print Step 3.
```

**Done when:**
- ☑ Four tabs, swipe and tap, at 410 dp and 750 dp; no horizontal content fights the pager.
- ☑ New prefs: `pinnedSecondProfile`, `statusRingShows`, per-account `resetPingMode`, one
  `paceOverEverywhere` (name per the code's convention) with migration.
- ☑ Alerts tab fits one cover screen with two accounts.
- ☑ Tests green, Status updated, ticked, committed, pushed.

**Log:**
- 2026-09-09 — Done by one Sonnet agent, reviewed and finished by the orchestrator. Settings is a
  fixed `TabRow` (10 dp tab padding, `ScrollableTabRow` fallback noted in code) over a
  `HorizontalPager`; `Screen.SETTINGS` got its own branch in MainActivity because a pager cannot
  live inside the shared `verticalScroll` ContentColumn — each page is its own ContentColumn.
  New prefs: `pinnedSecondProfile(): Profile?` (None = absent; cleared on account removal and
  when First takes its account), `statusRingShows()` with `RING_FIRST/SECOND/HIGHER`, and
  `showOverPace()` replacing both `paceOverInApp` and `paceOverOnNotification` (migration is the
  pure `SettingsMigration.showOverPace`, 4 unit tests; the in-app value wins, the notification
  value is not consulted). Tests 287 → 291. Judgement calls: chip sub-labels "First" /
  "Second · optional" at labelMedium; the Settings TabRow uses the default indicator (no
  per-tab accent, so ProfileTabs' crossfade workaround was not copied); Usage credits visibility
  is recomputed when the account list changes, not on every poll; the account card was left
  unchanged — the wireframe's After caption says the cards "gained the percentage on their
  subtitle line" while its Row-by-row table says Keep/unchanged, and the paste said keep the
  card composables, so the conservative reading won (Robin to confirm at Step 5). Nothing on
  screen yet: the Alerts tab fit at 410 dp is arithmetic until the device pass. The pinned
  notification does not read `pinnedSecondProfile` or `statusRingShows` yet — that is Step 3.

---

## Step 3 · CCRM-62 (Duet Notification)

**Tier:** Opus (RemoteViews height limits and per-view intents are where a wrong guess costs a
device round-trip).

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, then RUNBOOK.md — its Conventions block, Steps 1–2 Logs and Step 3 — then
ROADMAP.md item CCRM-62 (Duet Notification) in full, then open
design/settings-diet-wireframe.html section 2 (collapsed option 1, the expanded layout, the
height budget, the status bar, the tap targets, the nine states) and its Decisions table —
approved; build to it, Huge number style only. Brief one Opus agent for
notify/PinnedNotification.kt and the two RemoteViews layouts: collapsed row with two halves
(mark 14 dp + label 12 sp clamped, 8 dp bar with tick, 30 sp figure trailing; condition dot;
today's single layout when Second is None), expanded with two header blocks ("Personal · 5h",
"ChatGPT · Weekly", 36 sp figures) over the panel; drawPanel's bar row recut to ~30 dp (label,
figure and reset on one line above the bar) and prefixed with the account label; strip cap 3 →
2 when a Second account is set; "5h"/"Weekly" wording everywhere on this surface (Fmt helpers
if needed); per-half setOnClickPendingIntent (Cooldown on that account's tab, or that
service's app), Refresh action unchanged. Status bar: ui/UsageIcon ring only, no weekly hub dot
(remove drawFlag and the weekly parameters), coloured by the shown account's accent below 80%
then the severity ladder; the shown account follows the "Status-bar ring shows" pref (First /
Second / higher 5h percentage). Alerts.evaluate's re-render must pass both accounts. Unit-test
the label clamp, the strip cap and the account selection. Steps 1–2 already left: the prefs
UsageCache.pinnedSecondProfile(): Profile? (null = None), statusRingShows() with
RING_FIRST/RING_SECOND/RING_HIGHER, showOverPace() (the one red toggle; paceOverOnNotification
is gone), resetPingMode(profile, window); UsageIcon.draw has no style or mono parameter any
more and PinnedNotification.drawStatusIcon has no style parameter; Conditions.panelFor has no
folded events and MAX_STRIPS is still 3; the Alerts tab's "Tapping a number opens" writes
pinnedTapTarget "app" / "provider" (legacy "claude" reads as "provider"). Install a debug build on the Fold 7
and show me the collapsed and expanded shade in one AskUserQuestion before closing. Close per
the Close-out rule (commit as feat(CCRM-62): two-account pinned notification), then follow
the Handover rule and print Step 4.
```

**Done when:**
- ☑ Collapsed content ≤ 56 dp with two accounts; expanded within the 256 dp cap with two
  weekly rows and one strip.
- ☑ One account or Second = None renders today's layout unchanged.
- ☑ Ring has no dot; colour follows the shown account; picker works including auto.
- ☑ Robin saw the shade on the phone. Tests green, Status updated, ticked, committed, pushed.

**Log:**
- 2026-09-10 — Built by one Opus agent 2026-09-09 evening, reviewed by the orchestrator, seen by Robin
  on the Fold 7 the same night (release-signed build over the live v1.4 install, Second = ChatGPT
  set by adb through the Alerts tab). `notify/Duet.kt` holds the pure rules (label clamp 72/56 dp,
  strip cap 3/2, ring account), 7 tests; `PinnedNotification` split into single / duet paths over
  one builder — the single layout is unchanged, including its "5-hour window" wording, per the
  wireframe's state 2. Two new layouts `notif_duet*.xml`, a 6 dp condition-dot drawable;
  `Conditions.panelFor(…, second)` prefixes every strip and caps at 2 in Duet mode; the ring lost
  `drawFlag`/`weeklyFlag` and their tests (294 tests). Findings from the device: everything as
  drawn; the compact panel row measures ≈45 dp not the table's 30 (the 13.5 sp line needs 16 dp
  and the tick overhangs), still inside the budget with two Weekly rows; collapsed bars are
  drawn at 156 dp nominal so the tick keeps its shape; tap request codes are per half
  (`NOTIF_ID+10/20+slot`). Robin's one change, decided on `design/spent-ring-wireframe.html`
  the next morning: at 100% the ring is smooth and carries an × in the hollow; the 12 o'clock
  post and `POST_*` are gone. Installed, but no window was at 100% by then — Step 5 must catch
  the cross live. Also noticed: the ring at 0% is the hairline alone (the existing no-usage
  rule), so a Second account that has not started shows no identity colour yet; accepted.

---

## Step 4 · CCRM-60 (Dual Identity) — icon, glyph, two rooms

**Tier:** Opus for the three adaptive-icon vectors and the About drawable · Sonnet for the top-bar
glyph and the two-rooms theming.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, then RUNBOOK.md — its Conventions block, Steps 1–3 Logs and Step 4 — then
ROADMAP.md item CCRM-60 (Dual Identity) in full, then open design/dual-identity-wireframe.html
section 2 (the chosen icon variant, named in the roadmap item's "Icon, decided" bullet), section
3 (two rooms, six states) and its Decisions table — approved; build to it. Two agents in
parallel: (1) Opus — the launcher icon: ic_launcher_foreground.xml, ic_launcher_background.xml
(the two-colour ground replaces the slate radial), ic_launcher_monochrome.xml per the mono rule,
and drawable/ic_launcher.xml for About, exactly the chosen variant's geometry; render the tile
at 48 dp light, dark and themed and show me the screenshot in one AskUserQuestion — wait for
approval before committing. (2) Sonnet — the app: the top bar's title led by the two
ProviderMark glyphs at 20 dp on the main screen only; the two rooms: a surface tint per selected
account's provider (Claude ivory #F5EFE8 light / #1B1715 dark; ChatGPT #F7F7F8 / #0D0D0D),
headline percentages on the window cards in FontFamily.Serif on Claude tabs and the default
sans on ChatGPT tabs, the tab indicator in the room accent with labels kept neutral (the
narrowest change to ProfileTabs' crossfade rule; keep the labels neutral as today). Nothing on
any card changes. Tests green (add a test for the room tokens). Close per the Close-out rule
(commit as feat(CCRM-60): new icon, top-bar glyph, two rooms), then follow the Handover rule
and print Step 5.
```

**Done when:**
- ☑ Robin approved the 48 dp icon render (light, dark, themed).
- ☑ Main top bar shows the two marks then "Cooldown"; Settings, History, Guide keep plain titles.
- ☑ Claude tab: ivory surface, serif figures; ChatGPT tab: neutral surface, sans; cards
  otherwise byte-identical in content.
- ☑ Tests green, Status updated, ticked, committed, pushed.

**Log:**
- 2026-09-10 — Opus (icon) and Sonnet (rooms) ran in parallel; the icon agent did not run gradle,
  the orchestrator compiled the merged tree (297 tests). Icon: the four vectors follow the
  wireframe's numbers literally (the agent diffed its SVG mirror against the wireframe's own tile
  symbol: 2 antialiasing pixels of 46 656); one idiom fix — the vertical tick capsule needs its arc
  sweeps written the other way round or the caps bulge inward and the 4.8-unit overhangs vanish.
  Robin approved the 48 dp render (light, dark, squircle, themed). Rooms: `Rooms.forProvider` is
  pure and tested; Material 3's plain `Card` reads `surfaceContainerHighest`, so the room's card
  tint is applied to every surfaceContainer token plus surfaceVariant on Main and History only;
  Claude serif reaches the 5-hour headline and every `SubBar` row (All models, per-model caps),
  not the credits card; the tab indicator now follows `colorScheme.primary`, labels neutral.
  The tabContentColor comment above the indicator still says only the mark keeps its tint —
  slightly stale, left alone. Nothing seen on the phone yet: Step 5 covers the six app states.

---

## Step 5 · Device pass on the Fold 7

**Tier:** Sonnet · **phone in hand**

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, then RUNBOOK.md — its Conventions block, Steps 1–4 Logs and Step 5 — then the
"States" lists in design/settings-diet-wireframe.html section 2 (nine notification states, the
Settings states) and design/dual-identity-wireframe.html section 3 (six app states). Build a
release-signed APK, install it over the live install on the Fold 7 via wireless adb (ports
rotate: find the port with dns-sd; keep the screen awake for the session and restore auto-off
before disconnecting; screencap needs -d, and the inner screen captures black while folded),
and walk every state with me one at a time, capturing a screenshot for each. Also confirm on
device: placed widgets and the Quick Settings tile are gone; the six old notification channels
no longer appear in system settings; a reset ping fires for an account with "Always" on its 5h
reset; the status ring switches account and colour under "Whichever is higher"; the ring at 100%
shows the smooth red ring with the × (design/spent-ring-wireframe.html); Settings tabs
swipe on both screens. Record each outcome as a table row at the foot of the wireframe it
belongs to, file defects as new CCBG items in BUGS.md with the next free number, update the
three items' Status lines. Close per the Close-out rule (commit as docs(CCRM-60/61/62): device
pass), then follow the Handover rule and print Step 6.
```

**Done when:**
- ☑ Every state marked seen or "not seen, because …". Nothing severe open (one Medium, CCBG-25).
- ☑ Ticked, committed, pushed.

**Log:**
- 2026-09-10 — Run by the orchestrator itself, no sub-agent (the labour was adb driving, not
  rendering). Release-signed v1.6 build installed over the live v1.5 install (four accounts kept);
  wireless adb in the morning, then the phone left the Wi-Fi and the pass finished over **USB
  debugging** from the office. Seen and passed by Robin: app states 1, 2 (410 and 750 dp), 3 in
  light without the 100%, 6; notification states 1, 2, 5 (for real — Product was in re-auth), 7,
  8, 9; Settings four tabs at both widths with swipe, More with Debug unlocked; launcher icon;
  widgets and tile gone; six channels deleted; ring switching under Whichever is higher. Not seen:
  app 4 (radios off severs adb; Robin skipped the manual variant), app 5 and the two thin Settings
  states (four live accounts), notification 3 (this ChatGPT account has a 5h window), 4 and the
  100% cross (no window filled), 6 (six-hour stale). Filed: CCBG-21 (Zero-Point Shading),
  CCBG-22 (Credits Rows For All), CCBG-23 (Mark Size Mismatch — Robin: after the release),
  CCBG-24 (Duet Label Clamp), all Low; **CCBG-25 (Idle Reset Silence), Medium** — the reset ping
  did not fire for an idle account because `checkReset` returns on a null `resetsAt`; fix it in
  v1.6.1 or before Step 6 if Robin prefers. Decisions taken at the phone: account cards stay
  without the percentage; the dual-identity wireframe's "600 dp cap" caption is stale (Main keeps
  the 760 dp column). Captures in `design/research/2026-09-10-device-pass/` (30 files, ~7.6 MB;
  shade crops quantised). Outcome tables sit at the foot of the three wireframes. The phone was
  left with dark mode on and its own 1-minute screen timeout.
- 2026-09-10, later — Robin reopened the icon before Step 6: on the phone the round-7 tile reads as
  the Indian flag. Round 8 (twelve abstract concepts) and round 9 (Pulse in seven size/shape
  variants, each between stand-ins for the real Claude and ChatGPT tiles) are in
  `design/app-icon-v2-wireframe.html`; decided **A2 Bleed + B1 ECG on charcoal**, then round 10 (square-first) raised it to
  **S2 Tall** with About on the launcher squircle; built into the four vectors the same afternoon
  (Opus, zero-pixel diff against the wireframe symbol). Also fixed and committed before Step 6, `5b10673`:
  CCBG-25 (Idle Reset Silence) via the pure `alerts/ResetRollover` (11 tests, 308 green; device
  verification pending) and CCBG-23 (Mark Size Mismatch), the blossom scaled 1.35× — Robin approved
  the before/after render. BUGS.md Open list is now Low-only.

---

## Step 6 · Release v1.6

**Tier:** Sonnet for the docs rewrite and the screenshot run · Haiku for the copy sweeps · **no
Robin in the loop** — the session signs and publishes itself. Robin's only act is to plug the
Fold 7 in over USB (USB debugging on, screen unlocked) before pasting.

**Preconditions the session checks first and stops on if missing** (one message saying which,
nothing else done): `adb devices -l` lists `RZCY70YN0LJ … usb:` (wireless is not accepted for
this step); `ccooldown-release.jks` and `keystore.properties` are present at the repo root
(gradle signs `assembleRelease` with them; never stage them); `gh auth status` is logged in as
robineam360 for `robineam360/Cooldown`; headless Chrome exists at
`/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` (the docs build and the PDF
page check need it).

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, RELEASING.md, then RUNBOOK.md — its Conventions block, Steps 1–5 Logs and Step 6
(its preconditions and this block) — then ROADMAP.md section "Dual identity and diet" for what
shipped. Run the whole v1.6 release end to end with no questions to Robin: every decision below
is taken, and the only reason to stop is a failed precondition or a red test, in which case say
exactly what failed and stop. Phone work goes over USB adb (screencap -d 4630946872173396372
for the cover screen, screencap -p to /sdcard then adb pull; keep the screen awake with
settings put system screen_off_timeout 1800000 and restore the value you read first before
you finish; if the phone locks, stop and ask Robin to unlock — the bouncer cannot be driven).
(1) Version: versionName "1.6", versionCode 21 in app/build.gradle.kts. (2) Green:
./gradlew testDebugUnitTest (308 tests or more) and assembleRelease; verify the APK's signer
with apksigner --print-certs matches the installed app's (SHA-256
8bc21a2aca81e5a09b239d1847822549f10775d76849f0e1948980ecd044f64f). (3) Install it over the
live install (adb install -r) and take the release screenshots yourself into
release/docs/src/shots/, replacing every retired one (widgets, tile, alert matrix, four
styles): the Pulse icon in the app drawer and on the About card; Main on the Pro tab (Claude
room) and on the ChatGPT tab (ChatGPT room), dark and light (cmd uimode night no / yes, restore
yes); Settings' four tabs at 410 dp; the pinned notification collapsed and expanded (cmd
statusbar expand-notifications, tap the chevron at 975,406, cmd statusbar collapse); the
status-bar ring. Confirm on those captures that the Pulse tile is what the drawer shows and
that the two provider marks in the top bar now read the same size (CCBG-23 (Mark Size
Mismatch)); if either is wrong, that is a stop. Reuse the Step 5 captures in
design/research/2026-09-10-device-pass/ only where a fresh one is impossible (the four-account
notification states). (4) Docs: rewrite README.md (no widgets, tile, alert matrix or four
styles; the always-on notification with two accounts, reset pings, four-tab Settings, the Pulse
icon; "not affiliated" notice unchanged; both PDF links to the v1.6 filenames); update
release/USER-GUIDE.md (version header + changelog); edit release/docs/src/guide.html and
brochure.html — remove the widget and alerts pages, add the Duet notification page and the
new icon, swap the screenshots — then ./release/docs/build.sh and render every changed PDF page
to PNG (headless Chrome or pdftoppm) and look at each for clipping, since .page boxes clip
silently. Docs are exempt from the wireframe gate (working agreement 2). (5) Release notes
(release/docs/src or the gh --notes-file, per RELEASING.md): what shipped (CCRM-60 (Dual
Identity), CCRM-61 (Settings Diet), CCRM-62 (Duet Notification)), that placed widgets and
Quick Settings tiles disappear on update, the fixes since the device pass (CCBG-25 (Idle
Reset Silence), CCBG-23), and the known issues CCBG-21 (Zero-Point Shading), CCBG-22 (Credits
Rows For All), CCBG-24 (Duet Label Clamp). (6) Ship: git add -A (the APK, keystore and
local.properties are gitignored — check git status shows none of them), commit "v1.6 —
CCRM-60/61/62 ship: two-account notification, settings diet, new icon", git tag v1.6, git push
&& git push --tags, then gh release create v1.6 app/build/outputs/apk/release/app-release.apk
--title "Cooldown v1.6" --notes-file <the notes>; verify with gh release view v1.6 that the
asset is attached. (7) On the phone open Settings → More → Check for updates and screenshot
the result: an installed v1.6 must report itself current against releases/latest. Then close
per the Close-out rule (set the three items' Status lines to Shipped v1.6 2026-09-xx, tick
Step 6, write its Log, commit that as docs(CCRM-60/61/62): v1.6 shipped, push), restore the
screen timeout, and say the arc is complete with the release URL.
```

**Done when:**
- ☐ README, USER-GUIDE.md, guide PDF, brochure PDF regenerated and every changed page eyeballed;
  no widget, tile or alert-matrix copy remains anywhere in `release/` or README.
- ☐ Fresh screenshots from the phone in `release/docs/src/shots/`, Pulse icon confirmed in the
  drawer, marks confirmed equal.
- ☐ Signed APK built, tag `v1.6` pushed, GitHub release published with the APK attached, update
  check on the phone confirmed; all by the session.
- ☐ Ticked, committed, tagged, pushed.

**Log:**
