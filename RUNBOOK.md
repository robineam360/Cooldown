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
| 1 | CCRM-61 (Settings Diet) part 1 — the removals | Opus (alerts surgery) · Sonnet (widgets, tile, styles, glyphs) · Haiku (tracker statuses) | no | ☐ |
| 2 | CCRM-61 (Settings Diet) part 2 — four-tab Settings and the new rows | Sonnet | no | ☐ |
| 3 | CCRM-62 (Duet Notification) — two accounts, 5h/Weekly, clean ring with picker | Opus | one look at the shade | ☐ |
| 4 | CCRM-60 (Dual Identity) — icon, top-bar glyph, two rooms | Sonnet (rooms) · Opus (icon vectors) | approve the 48 dp icon render | ☐ |
| 5 | Device pass on the Fold 7 | Sonnet | phone in hand | ☐ |
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
- ☐ No `androidx.glance` import, no `widget/` package, no tile service, no `ping/` package.
- ☐ `Alerts.kt` ≈ 130–180 lines: `evaluate`, `checkReset` (with per-account reset ping),
  `STALE_DATA_MS`, `notifId`, `MAX_KIND`, `cancelAllFor`, nothing else.
- ☐ `PinnedNotification` has one style path (Huge number) and one glyph (Ring); the panel still
  re-renders on every poll.
- ☐ History screen still gains points at a window rollover (read `SessionLog` write path).
- ☐ Six old channels deleted on first launch after upgrade; `reset_alerts` and
  `pinned_usage_v2` remain.
- ☐ Tests green, tracker statuses updated, ticked, committed, pushed.

**Log:**

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
Settings does today. Keep every explainer sentence the wireframe keeps; drop the ones it drops.
Update the guide screen's back navigation (Settings → Guide → back lands on the Accounts tab).
Tests green; add a unit test for the pref migration. Close per the Close-out rule (commit as
feat(CCRM-61): four-tab Settings), then follow the Handover rule and print Step 3.
```

**Done when:**
- ☐ Four tabs, swipe and tap, at 410 dp and 750 dp; no horizontal content fights the pager.
- ☐ New prefs: `pinnedSecondProfile`, `statusRingShows`, per-account `resetPingMode`, one
  `paceOverEverywhere` (name per the code's convention) with migration.
- ☐ Alerts tab fits one cover screen with two accounts.
- ☐ Tests green, Status updated, ticked, committed, pushed.

**Log:**

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
the label clamp, the strip cap and the account selection. Install a debug build on the Fold 7
and show me the collapsed and expanded shade in one AskUserQuestion before closing. Close per
the Close-out rule (commit as feat(CCRM-62): two-account pinned notification), then follow
the Handover rule and print Step 4.
```

**Done when:**
- ☐ Collapsed content ≤ 56 dp with two accounts; expanded within the 256 dp cap with two
  weekly rows and one strip.
- ☐ One account or Second = None renders today's layout unchanged.
- ☐ Ring has no dot; colour follows the shown account; picker works including auto.
- ☐ Robin saw the shade on the phone. Tests green, Status updated, ticked, committed, pushed.

**Log:**

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
- ☐ Robin approved the 48 dp icon render (light, dark, themed).
- ☐ Main top bar shows the two marks then "Cooldown"; Settings, History, Guide keep plain titles.
- ☐ Claude tab: ivory surface, serif figures; ChatGPT tab: neutral surface, sans; cards
  otherwise byte-identical in content.
- ☐ Tests green, Status updated, ticked, committed, pushed.

**Log:**

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
reset; the status ring switches account and colour under "Whichever is higher"; Settings tabs
swipe on both screens. Record each outcome as a table row at the foot of the wireframe it
belongs to, file defects as new CCBG items in BUGS.md with the next free number, update the
three items' Status lines. Close per the Close-out rule (commit as docs(CCRM-60/61/62): device
pass), then follow the Handover rule and print Step 6.
```

**Done when:**
- ☐ Every state marked seen or "not seen, because …". Nothing severe open.
- ☐ Ticked, committed, pushed.

**Log:**

---

## Step 6 · Release v1.6

**Tier:** Sonnet for the docs rewrite and RELEASING.md flow · Haiku for the copy sweeps · **Robin
signs and uploads**

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, RELEASING.md, then RUNBOOK.md — its Conventions block, Steps 1–5 Logs and Step 6
— then ROADMAP.md section "Dual identity and diet" for what shipped. Prepare v1.6: bump
versionName to 1.6 and versionCode to 21; rewrite README.md (no widgets, no tile, no alert
matrix, no four styles; the always-on notification with two accounts, reset pings, four-tab
Settings, the new icon; "not affiliated" notice unchanged); regenerate the user guide,
brochure and hero from release/docs/src per RELEASING.md, removing the widget and alerts pages
and adding the Duet notification page; replace the retired screenshots with the Step 5
captures; write the release notes, saying plainly that placed widgets and tiles disappear on
update. Stop before signing and uploading — I do those — and tell me the exact commands. After I
confirm the release is published, close per the Close-out rule (commit as "v1.6 — CCRM-60/61/62
ship: two-account notification, settings diet, new icon", tag), then say the arc is complete.
```

**Done when:**
- ☐ README, guide PDF, brochure PDF regenerated and reviewed; no widget, tile or alert-matrix
  copy remains anywhere in `release/` or README.
- ☐ Signed APK built and GitHub release published by Robin; update check confirmed on the phone.
- ☐ Ticked, committed, tagged, pushed.

**Log:**
