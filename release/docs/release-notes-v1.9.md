# Cooldown v1.9

Steadier underneath: a crash report stays on your phone until you share it, the sign-in store recovers instead of crashing, and a privacy policy sits on the About card. Install over v1.8 — settings, history, widgets and accounts all survive.

## What's new

**Crash reports that stay on your phone** [CCRM-85 (Crash Capture)]
- If Cooldown crashes or stops responding, the next time you open it a card under the top bar says so — "Cooldown crashed today at 6:17 PM"
- **Share report** sends it wherever you pick. The card goes once you pick an app; backing out of the share sheet keeps it. **Not now** puts it away
- A report holds the error's type, the code locations, and the app, Android and phone versions. Error messages are left out, and it is scrubbed for anything that looks like a token or an email
- Nothing is sent on its own: there is no crash service. Behind the 7-tap unlock, **Diagnostics → Crash reports** keeps reports for 30 days, with Share and Delete

**A steadier sign-in store**
- If Android's secure storage breaks for good, Cooldown no longer crashes at every launch. It clears the unreadable sign-ins, says so, and you sign in again [CCBG-46 (Keystore Wedge)]
- If storage only stops responding, an amber notice says so on the main screen and in Settings → Accounts, with **Try again**. Your saved sign-ins stay on the phone and come back once storage answers. A sign-in or sign-out made meanwhile lasts until the app restarts, and a one-time card afterwards tells you which [CCBG-50 (Degraded Store Notice)]

**Privacy**
- A **Privacy policy** row in Settings → More → About, under Share feedback. The policy lists every host the app talks to and everything it keeps [CCRM-89 (Privacy Policy)]
- Cooldown no longer holds the permission to see every app installed on the phone; the sign-in browser picker still lists your browsers [CCRM-88 (Scoped Queries)]

## Fixed

- CCBG-35 (Tab Clip) — Settings tab titles are never cut off on a narrow phone or at a large font; the row scrolls instead
- CCBG-45 (Single Panel Wording) — the one-account notification says "5h" and "Weekly", like the two-account one
- CCBG-48 (Reconfigure Label) — a widget's settings opened later say "Save changes", not "Add widget"
- CCBG-52 (Boot Pin Delay) — after a reboot the always-on notification comes back promptly, not minutes later

## Install

- Download the APK from [releases/latest](https://github.com/robineam360/Cooldown/releases/latest)
- Read the [Cooldown User Guide v1.9](https://github.com/robineam360/Cooldown/blob/main/release/docs/Cooldown-User-Guide-v1.9.pdf)
- [Privacy policy](https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md)
