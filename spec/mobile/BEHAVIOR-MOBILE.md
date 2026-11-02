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
| Sign-in expiring | A known sign-in expiry is within **7 days** |
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

**Status:** decided 2026-11-02, wireframe `design/2026-11-02-weekly-first-pace.html` (rev A,
Android approved 2026-11-02; iOS rev B pending); no client implements it yet. Until one does, today's rule stands: the headline
is the session if present, else the weekly window.

**The Headline setting is per account:** `weekly` (default) · `fiveHour` · `auto`.

| Surface | Leads with |
|---|---|
| Pinned notification (each Duet half follows its own account), iOS Overview big number | `weekly` → Weekly · `fiveHour` → 5h · `auto` → 5h while core §6.2's `sessionBinding`, else Weekly |
| Status-bar gauge (one window, no room for a companion) | as above, except `weekly` behaves as `auto` |
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

Allowance: `perDay` → "≈11%/day" (rounded; below 1 → "<1%/day"); `hours` → "40% left, 6h".
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

**Usage display: Vs pace** (CCRM-100 (Vs Pace Display), awaiting its wireframe's approval): the third
value of the global Used/Left token. Figure = `floor(elapsedPct − usedPct)`, signed ("+10%", "−20%", "0%");
falls back to the usage figure when spent (`usedPct ≥ 99.5`), with no reset clock, or with no reading.

**Model limits** (CCRM-99 (Model Limits Toggle)): per account `showModelLimits`, default true. False hides
the model-cap rows and chips on the 7-day card and the notification panel; nothing else changes.
