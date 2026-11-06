# Mobile behaviour — Android and iPhone/iPad

Product rules shared by the mobile family. Server facts and the numbers every client must agree
on (severity, pace, colour breakpoints, history, reset detection) are in
[../core/BEHAVIOR-SPEC.md](../core/BEHAVIOR-SPEC.md); presentation is each platform's own design.

---

## 1. Polling cadence

Core rules still apply: the 429 backoff, the 5-minute token pre-refresh margin, and one fetch at a
time (core API-CONTRACT §6).

**Android.**
- **Background interval:** a setting with presets **5, 15, 30 and 60 minutes**, default **15**,
  never below 5. Network required. Below 15 minutes the poll runs as a chain of one-shot jobs,
  because Android's periodic scheduler will not run more often than every 15 minutes.
- **Manual refresh floor:** **180 seconds** since the last successful fetch; a tap inside it is
  refused with the seconds remaining. A community-derived safe floor.

**iOS.** No reliable background polling. The app fetches when it is opened or comes to the
foreground; widgets refresh on WidgetKit's own budget (stale by minutes to hours, which the design
must show rather than hide). **Floor: 180 seconds since the last successful fetch, for manual and
automatic fetches alike** — app opens are frequent on a phone, so the automatic ones get the same
floor. An automatic fetch waits out the 429 backoff; a manual one goes through it, as on Android.

**Both.** A Claude account refused for its plan (core §10.1) is parked: later fetches read only
the profile endpoint until the plan changes. A renewal the token endpoint refuses (400–403) means
"sign in again"; a transient one is retried, and becomes "sign in again" after 6 hours of failing.

Source: `work/Polling.kt`, `data/UsageRepository.kt` (`MANUAL_MIN_INTERVAL_MS`, `doFetch`), `data/UsageCache.kt`
(`pollIntervalMinutes`), `SettingsScreen.kt` presets (Android); `ios/Packages/CooldownCore` `UsageRepository` (iOS).

---

## 2. Reset pings — the only alert

A reset is an **event**, so it is the one thing the mobile family posts as a notification. Every
other alert the desktop has (thresholds, pace, expiry, re-auth, stale data) is either a **state**
(§3) or not offered on mobile — Android retired them in CCRM-61 (Settings Diet).

- Per account and per window (**5h** and **Weekly**), each with its own mode:
  - **Off** — never.
  - **If busy** — only when the window that just closed peaked at **≥ 80%** (stored as `smart`).
  - **Always.**
- A ping posts even while the always-on notification is showing: its job is to reach someone
  who is not looking.
- Detecting the reset and recording the closed window always run, whatever the mode (core §8).

**iOS** cannot poll at the moment of reset, so it schedules the reset alert ahead of time from the
known reset instant and cancels or reschedules it after every fetch (in the app or in iOS's background
refresh). "If busy" is decided from the highest reading seen for that window by the last fetch. Same
defaults as Android; notification permission is asked when the first account is added.

Source: `alerts/Alerts.kt`, `alerts/ResetRollover.kt`, `data/UsageCache.kt` (`RESET_*`,
`SMART_RESET_MIN_PCT`) (Android).

---

## 3. Conditions — states, not notifications

A condition is true until something fixes it, so it is drawn where the usage is drawn and clears
itself; it never posts a notification of its own.

| Condition | When |
|---|---|
| Plan refusal | The usage endpoint refuses the account's plan (core §10.1) |
| Stale | No successful fetch for **6 hours** |
| Sign-in expiring | A known sign-in expiry is within **3 days** (both apps; was written as 7 until 2026-11-05, when the owner confirmed Android's 3) |
| Sign-in needed | An auth failure (core §10.0) |
| Update available | The GitHub-channel Android build only (CCRM-87 (Update Channel)) |

Android shows them as strips in the pinned notification (at most three, the rest folded to one
line). iOS shows them as a notice above the cards in the app, as the caption of a widget, the
label line of a Live Activity row and the Control Center value (wireframes rev B, CCRM-94 (iOS App)).

Source: `notify/Conditions.kt`, `alerts/Alerts.kt` (`STALE_DATA_MS`) (Android).

---

## 4. Surfaces

Listed here; each one's layout, sizes, states and copy as shipped are in [surfaces/](surfaces/README.md),
and changes still go through a wireframe first (CLAUDE.md §2).

| Android | iPhone and iPad |
|---|---|
| The app | The app (iPad: a pinned account on top, the selected one below) |
| Always-on pinned notification and its status-bar ring | — (iOS has no persistent notification) |
| Home-screen widgets | Home-screen widgets, plus a Large face |
| — | Lock-screen widgets |
| — (the Quick Settings tile went with CCRM-61 (Settings Diet)) | Control Center control |
| Reset-ping notifications | Reset alerts, scheduled ahead |
| — | Live Activity: two accounts' 5h and Weekly (lock screen; Dynamic Island on iPhone) |
| Share card | Share card (to decide with CCRM-94 (iOS App)) |

---

## 5. Headline window and pace wording — CCRM-96 (Weekly First), CCRM-97 (Pace Number)

**Status:** decided 2026-11-02; wireframes approved 2026-11-02 (`design/2026-11-02-weekly-first-pace.html`
Android, `-ios-weekly-first.html` iOS, `-pace-line-label.html` V1, `-vs-pace-display.html`); no client
implements it yet. Until one does, today's rule stands: the headline
is the session if present, else the weekly window.

**The Headline setting is per account:** `weekly` (default) · `fiveHour` · `auto`.

| Surface | Leads with |
|---|---|
| Pinned notification (each Duet half follows its own account), iOS Overview big number | `weekly` → Weekly · `fiveHour` → 5h · `auto` → 5h while core §6.2's `sessionBinding`, else Weekly |
| Status-bar gauge (one window, no room for a companion) | **superseded 2026-11-02** by the shape grammar (below): the Headline no longer picks the status-bar window |
| A new widget's window choice (stored per widget afterwards) | `weekly` and `auto` → Weekly · `fiveHour` → 5h |
| The app's default card order (a layout reordered in the sheet wins) | `fiveHour` → 5h card first · otherwise Weekly first |

An account with no session window always leads with Weekly, whatever the setting. Placed widgets
keep their stored window. Cases in [`fixtures/headline/cases.json`](fixtures/headline/cases.json).

**The 5h companion.** Where Weekly leads and the account has a session, the 5h figure rides along
as "5h 34%". While `sessionBinding` holds it takes the bar colour's step (core §5) and bold.

**Pace words** for core §6.1's bands:

| Band | Words |
|---|---|
| `onPace` | "On pace" |
| `roomToSpare` | "Room to spare" |
| `useBeforeReset` | "Use before reset" |
| `slowDown` | "Slow down" |
| `nearlyOut` | "Nearly out" |
| `spent` | no band: "Out until <reset>" |

Allowance: `perDay` → "≈11%/day" (rounded; below 1 → "<1%/day"); `hours` → "40% left, 6h" (hours
rounded; under 30 minutes "40% left, <1h"). The notification's expanded first line adds " to last" after a
per-day allowance only ("Slow down · ≈11%/day to last").
Tight form (notification, folded card): "Slow down · ≈11%/day". The signed number appears only in
the app card's detail: "Pace +21% · an even week is 39% by now" — **superseded 2026-11-02**: the signed
Pace is never printed; instead each app and Summary bar carries the pace-mark caption (below). On always-on surfaces the words carry no
colour; in the app the folded line keeps today's over-pace emphasis (Android: the band clause bold in
the orange step; iOS: the whole line red). The 5-hour session gets no Pace; its line is core §3's projection: folded "Resets in 2h 20m · on
track for ~69%", and open the existing "At this pace: ~69% when the window resets" line.

**Pace-mark caption** (in-app bars and Summary rows; `design/2026-11-02-pace-line-label.html` V1): one
line under the bar, starting just right of the pace mark (ending just left of it when short of room). It
shows what the figure does not: in Used or Left mode "60% pace mark · 10 under" / "· 20 over" / "· on it"
(points, `elapsed − used`, |d| < 1 is "on it"); in Vs pace mode "50% used · mark 60%". Under pace the gap
from the fill to the mark is drawn as a dashed outline; over pace the existing red slice is the gap. None
when the window is spent, has no reset clock or no reading. The notification's expanded header line 2
reads "10 under the pace mark (60%) · resets Fri".
Numbers (Step 2 of RUNBOOK-v2.0, 2026-11-02): the mark is `elapsedPct` rounded half up; the distance is
`|elapsedPct − usedPct|` rounded half up, "on it" below 1. Left mode states the mark in remaining terms
(`100 − mark`) and words the distance "10 to spare" under the mark, "20 over" past it. Vs pace mode's usage is
the Used-mode figure (truncated). Every floor and half-up rounding of a difference adds 1e-9 first, so binary
float edges (`2.3 − 0.3` = 1.9999999999999998) land on the decimal answer. Cases in [`fixtures/display/cases.json`](fixtures/display/cases.json) →
`caption`, `captionSide`. The notification's line 2 in the other modes (Fable judge, 2026-11-02): Left "10 to
spare · pace mark 40% · resets Fri" / "20 over · pace mark 40% · resets Fri"; under 1 point "On the pace mark (60%)
· resets Fri"; Vs pace "52% used (mark 39%) · resets Sun". The 5h companion always shows usage, bare in Left mode
("5h 66%"); under a 5h lead the companion is "Weekly 52%".

**Usage display: Vs pace** (CCRM-100 (Vs Pace Display), wireframe approved 2026-11-02): the third
value of the global Used/Left token. Figure = core §6.3's `vsPace` (rounded toward zero since 2026-11-06), signed ("+10%", "−20%", "0%");
falls back to the usage figure when spent (`usedPct ≥ 99.5`), with no reset clock, or with no reading.
**Since the shape grammar (rev D §7b, 2026-11-02) the figure is unsigned** — "10%", "20%", "0%" — and its colour
says the side: green under the mark, red over it, plain ink on it; the fallback usage figure keeps its severity
colour (fixture field `tone`). The notification's caption under the figure says "UNDER PACE" / "OVER PACE" /
"ON PACE"; the app's figures carry no "vs pace" word. Cases in [`fixtures/display/cases.json`](fixtures/display/cases.json) → `vsPaceFigure`.

**Since CCRM-104 (Pace Line Parity), 2026-11-06** (wireframe `design/2026-11-06-mobile-pace-line-wireframe.html`,
approved under Robin's delegation; the strings are core §6.3 "Printed form"), the rules above change as follows, and
this paragraph wins where they differ:
- **The figure is signed again** ("+19%", "−12%", "0%"), rounded toward zero, except inside a 5-hour ring (unsigned,
  colour only). The notification's "UNDER PACE / OVER PACE / ON PACE" caption is gone.
- **The pace line** replaces every Weekly band line (app cards, Summary line 3, the notification's collapsed sub and
  expanded line 1, widgets, iOS widgets and Live Activity): core's "6d 23h behind · Wed 12:30 PM". Drop order on a
  narrow column: the weekday, then the clock; the duration and word are never cut. The band words and the allowance
  stay only in alerts.
- **The notification's expanded line 2** is the pace-mark caption; where it does not fit, its distance clause alone
  ("13 over", "10 to spare", "On the mark"; Vs pace "52% used").
- **The press on a figure** shows the pace-mark caption for 3 s (the drawn caption underlined, or the ring's / one-line
  row's line swapped for it). The `markTime` readout words ("Pace catches up …", "1d 4h behind pace") are retired;
  the line now carries `markTime`. Vs pace caption words are core's: "52% used · 39% pace mark".
- **The Pace map** reads "ahead of pace" / "behind pace"; names on every dot, never numbers (core §6.3).

**Spent week gate** (CCRM-102 (Spent Week Gate), approved 2026-11-02): while the Weekly window is spent
(core §6.1), the 5h is shown disabled on every account — figure, bar and reset line at 45% opacity, no pace
caption, no projection — with one full-opacity note "Weekly limit reached — 5h can't be used until <weekly
reset, day and time>". The notification's expanded 5h panel row takes the note as its sub; a widget face
showing the 5h reads "Weekly spent".

**Shape grammar** (CCRM-103 (Hybrid Notification), frozen 2026-11-02 at rev D of
`design/2026-11-02-shape-grammar.html`; its open-questions table is the decided list). The 5-hour window is
always a hollow ring on the left, the 7-day windows always bars on the right; no "5h" label anywhere (screen
readers say "5-hour window, 34 percent").

- *Tap readout* (rev D §2b, C2–C5): a tap on a figure (a ring's figure included) swaps that figure's own line
  — a bar's caption, the ring's 5h line, a Summary row's line 3 — for 3 s with core §6.3's `markTime` in
  words: over the mark "Pace catches up Thu 5 Nov, 12:50 PM" (day, date and time in the Time format);
  under it "1d 4h behind pace" (`Xd Yh`, under a day `Xh Ym`, under an hour `Ym`, every part rounded down);
  within a point "On the pace mark". Spent, no reset clock or no reading: the tap does nothing. Same in
  every Usage display mode. Not in the notification. Cases in
  [`fixtures/display/cases.json`](fixtures/display/cases.json) → `markReadout`.
- *Usage end mark* (rev D §1e, C1): global `usageEndMark`, Appearance › Usage end mark: `round` (E0, the
  round cap) · `line` (E1, default: the fill ends square at the usage with a short line in the fill's
  colour) · `pace` (E2b: that line green under the ±5 on-pace band, red over it, ink inside it). An
  unknown or absent value reads as `line`; v1.9 does not read the key.
- *Status-bar icon* (Android only; rev D §4, §4b, C6–C8): per account `statusIcon`, `ring` (default) or
  `weeklyBar`, set in the account's ⋮ › Headline… sheet. With the ring: the 5h ring; a full 5h (truncated
  100) is a red cross alone until it resets; a spent week (core §6.1) greys the ring, outranking the
  cross. With the Weekly bar, or on a plan with no 5h whatever is stored: the Weekly as a small bar with its
  pace tick; a full Weekly, or a full 5h on a plan that has one, is the red cross. In a Duet the icon
  belongs to the account the "Status-bar icon shows" setting picks, and that account's choice draws it;
  "Whichever is higher" compares the window each account's glyph stands for (the 5h for the ring, the Weekly
  for the bar or a plan with no 5h).
  Cases in [`fixtures/status-icon/cases.json`](fixtures/status-icon/cases.json). An absent or unknown value
  reads as `ring`; v1.9 does not read the key.

**Model limits** (CCRM-99 (Model Limits Toggle)): per account `showModelLimits`, default true. False hides
the model-cap rows and chips on the 7-day card and the notification panel; nothing else changes.

## 6. The Summary tab — CCRM-98 (Summary Tab)

**Status:** decided 2026-11-02; wireframe approved (`design/2026-11-02-summary-tab.html` rev B); no client
implements it yet.

**When it exists:** with two or more accounts an **All** tab comes first in the account tabs (on iPad an "All
accounts" row tops the sidebar). Appearance › **Open on the summary** (default on) opens the app on it at launch;
off, launch opens the last account looked at, as today. The toggle is shown only with two or more accounts.

**The Weekly list, most room first.** Each account's Weekly window, in four groups:

1. **Eligible** — a reading, the week not spent (core §6.1, `usedPct ≥ 99.5`), and core §6.2's
   `sessionBinding` false. Ranked by core §6.1's Pace, lowest first (the most quota that would otherwise go
   unused); a tie goes to the earlier weekly reset. The top row is the one to use next; nothing else marks it.
   A week with a reading but no reset clock (not started) has no Pace and ranks after every clocked one.
2. **Held** by a binding 5h, soonest 5h reset first. The row says when it is back.
3. **Spent**, soonest weekly reset first.
4. **No reading**, in tab order.

A spent week is never held. Below the list, the **Pace map** plots each account with a reading at (its week gone,
its Weekly used); a spent account's dot carries an ×. Rows draw no pace pip (Robin, 2026-11-02: the bar above
already shows the pace); line 3 starts with the words. Rows use the §5 band words and allowance, the pace-mark
caption and the account's figure in the Usage display mode; the Pace still ranks in every mode. No combined or
summed figure (CCRM-6 (Multi-Account)), no Summary widget, no 5h on the map.

Cases in [`fixtures/summary/cases.json`](fixtures/summary/cases.json) → `ranking`, `tab`.

**Summary order** (Robin, 2026-11-02): Appearance › **Summary order**, next to Open on the summary and shown with it
(two or more accounts), global `summaryOrder`: **Your order** (`yours`, the default — Settings › Account order),
**Most room first** (`room`, the ranking above), **Resets soonest** (`resets`: the Weekly that resets next on top, ties
and accounts with no reset clock in your order, the clockless last), **Most used** (`used`: the highest Weekly % on
top, ties in your order, unread accounts last in your order). The list's header names the choice in lower case:
"your order", "most room first", "resets soonest", "most used". Every row keeps its group's words and styling (held,
spent, no reading) whatever the order; the Pace map is unaffected; widgets that list accounts keep their own order.
An absent or unknown value reads as `yours`; v1.9 does not read the key. Cases in
[`fixtures/summary/cases.json`](fixtures/summary/cases.json) → `order`.
