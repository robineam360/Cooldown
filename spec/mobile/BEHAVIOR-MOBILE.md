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
must show rather than hide). The exact floors are set with CCRM-94 (iOS App) and must not be
tighter than Android's 180-second manual floor.

Source: `work/Polling.kt`, `data/UsageRepository.kt` (`MANUAL_MIN_INTERVAL_MS`), `data/UsageCache.kt`
(`pollIntervalMinutes`), `SettingsScreen.kt` presets (Android).

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
known reset instant and cancels or reschedules it when a fetch shows the window moved. "If busy"
is decided from the peak known when the app last fetched — the exact behaviour is settled with
CCRM-94 (iOS App).

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

Listed, not specified — each platform designs its own (CLAUDE.md §2 wireframes).

| Android | iPhone and iPad |
|---|---|
| The app | The app (iPad: a pinned account on top, the selected one below) |
| Always-on pinned notification and its status-bar ring | — (iOS has no persistent notification) |
| Home-screen widgets | Home-screen widgets, plus a Large face |
| — | Lock-screen widgets |
| Quick Settings tile | Control Center control |
| Reset-ping notifications | Reset alerts, scheduled ahead |
| — | Live Activity: two accounts' 5h and Weekly (lock screen; Dynamic Island on iPhone) |
| Share card | Share card (to decide with CCRM-94 (iOS App)) |
