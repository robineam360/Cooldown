REVIEW: full · plan=v20-sample-mode

Scope and threat model: a design review of one wireframe section before any code is built — a reviewer "sample data" mode for an Android usage-tracker app (it shows the usage limits of the user's own Claude, ChatGPT and Gemini accounts), so a store reviewer can use the app with no accounts. It ports an approved iOS mode. The risks: real user data or settings changed or shown as made-up (or the reverse), a way into the mode with no way out, a surface nobody drew, and a store-review answer the build does not satisfy. Find missing states, leaks between sample and real data, and contradictions.

## Owner's decision (settled)
Port the iOS sample mode to Android: guarded so it saves nothing (display settings included), with sample accounts for all three providers; wireframe the Android delta (entry, indicator, exit, every surface touched).

## The iOS mode being ported (shipped)
Entered from the empty screen and the Add account sheet ("Try with sample data" / "Three made-up accounts to look around. Nothing is saved or sent."). It swaps the app model for built-in demo accounts with temporary storage, no keychain, nothing saved or sent, and swaps back on Exit, leaving real accounts untouched. A purple banner "● Sample data — made-up accounts" with "Exit" sits above every tab page. Settings rows read "Sample". The share card stays, with a "● SAMPLE DATA" band. Reset alerts, widgets, Live Activity and the Lock Screen section are excluded.

## The Android design
Rule: a process-wide flag held in memory only (as the existing debug-only synthetic series is). While it is on, the app's screens read three made-up accounts — Teams (Claude, Max), Personal (ChatGPT, Plus), Lab (Gemini, Pro) — with made-up readings and history. Every setting read and write goes to an in-memory copy seeded from the user's own. Nothing is written (token, account, history, display setting) and nothing is fetched or sent.

Entry: (1) the first screen with no account gets a text button "Try with sample data" plus its caption under the existing "No Claude account yet" card; (2) the Add account sheet gets a last row under a divider, the only entry when real accounts exist. There is no confirmation; entering lands on the All (Summary) tab.

Indicator: iOS's banner in the app-wide notice slot above the tab strip, on every tab including All, and as the first item of Settings and History. 13 sp; at large font " — made-up accounts" drops, while "● Sample data" and "Exit" never drop. The estimated width (265 dp) fits phones of 320 dp and up at font scale 1.0. Capped at 720 dp on the inner screen. The debug synthetic banner is separate and cannot be on at the same time (Diagnostics is hidden in the sample).

Leaving: "Exit" on the banner, or "Exit sample data" in Settings › Accounts (replacing "+ Add account" and "Reorder"), returns to the real data on the tab the user was on, or to the first screen. Process death, force-stop, reboot or update → the next launch is real; nothing needs cleaning. Rotation, fold, theme switch and backgrounding keep the sample. Opening the app from the notification, a widget, a shortcut or a reset ping that names a real account exits the sample first. System back leaves the app as usual.

Surfaces: tabs, account tile, credits card, charts and History are unchanged layouts drawing the three accounts. The status line reads "Sample data · nothing is fetched" (not a button; no fetch-error notice). The Summary, Pace map, filter, sort, chips and callout work as designed elsewhere; two sample accounts are tagged, so the filter shows. The share card and snapshot stay, with a "● SAMPLE DATA" band. Settings › Accounts cards show the word "Sample" and no e-mail, status or actions; the menu keeps Rename, Tag, Accent colour and Headline (in memory, dropped on Exit) and hides Details, Clear usage history and Remove account. Settings › Alerts becomes one card: "Off while the sample is open — the always-on notification, the status-bar icon, reset pings and widgets show real accounts only." Appearance works, with the caption "Changes here apply to the sample and are dropped when you exit." Polling, the credits switches and Diagnostics are hidden.

Notification and widgets (recommended, open for the owner): untouched — they keep the real accounts, or nothing with none, because the sample is applied to the app's screens and not at the cache seam they read. Alternatives: (B) they show the sample, which asks for the notification permission and starts the foreground service on entry; (C) A plus a one-off non-ongoing sample notification from Settings › Alerts. The store-console note currently says "the notification and the widgets show sample accounts", which is false under A, so the note is to be reworded: the notification and widgets need a real account and are shown in the existing foreground-service video.

Open questions for the owner: notification/widgets A/B/C; whether iOS swaps one of its two Claude sample accounts for Gemini to match; the first-screen entry as a text button (drawn) or a full first-launch screen.

## Spec changes named
Mobile behaviour spec: a new "Sample mode" rule. app.md: notice slot, status line, first screen, Add account, Settings sections, share band words. notification.md and widgets.md: one line each saying the sample never reaches them. No core change. Store notes app-access.md and the console steps get reworded.

## Unverified
Banner widths are estimates; the first screen at font scale 2.0 and the notice slot at compact height need a device; the in-memory settings layer is assumed feasible (the write paths of the settings and cache stores were not read).

## Tests / gates
Owner's approval, one question at a time. At build: unit tests that entering and exiting writes nothing (registry, cache, history, preferences byte-identical), that process death returns real data, and that notify/, widgets/, alerts/ and polling never read the sample; a device pass with real accounts present.
