<!--
DRAFT, RUNBOOK Step D1 (2026-11-11). Written before CCRM-105…113 are built: every line is checked
against the v2.0 release build before it is published (Step 4 finalises, Step 7 publishes). To fill:
the TestFlight public link and the iOS build number (Q1: link in the notes, no IPA asset), the
"Fixed" list (only defects a v1.9 user could see), and the guide link once release/docs/build.sh has
written the v2.0 PDF. This comment is invisible on GitHub; delete it when the draft is final.
-->
# Cooldown v2.0

Cooldown now reads Gemini as well as Claude and ChatGPT, leads with the weekly limit and your pace, puts every account on one Summary page, and comes to iPhone and iPad. Install over v1.9 — settings, history, widgets and accounts all survive.

## What's new

**Gemini accounts** [CCRM-55 (Antigravity Account)]
- **+ Add account → Gemini**: sign in on Google's own page in your browser, then paste the code it shows
- A Gemini account gets the same cards, charts, notification, widgets and pings as any other, with its plan on the card
- If Google changes what it returns, the app says so and keeps your last reading — never a made-up 0% or 100%

**Weekly first, and your pace** [CCRM-96 (Weekly First), CCRM-97 (Pace Number), CCRM-104 (Pace Line Parity)]
- The weekly window heads every card, the notification and the widgets; the 5-hour window sits beside it as a small ring
- Each account's **Headline** (Weekly, 5-hour or Auto) puts the 5-hour window first again if you prefer it. Updating from v1.9, a one-time notice lets you keep Weekly or go back to 5h first
- One pace line under every window says how far ahead or behind the pace mark you are, and when the window resets: "6d 23h behind · resets Wed 12:30 PM" [CCRM-108 (Reset Named)]
- **Usage display → Vs pace** shows the big figure as your distance from the pace mark: green with room to spare, red when you are ahead of it [CCRM-100 (Vs Pace Display)]
- While the week is spent, the 5-hour window is greyed out with a note saying when it can be used again [CCRM-102 (Spent Week Gate)]

**The Summary** [CCRM-98 (Summary Tab)]
- With two or more accounts the app opens on **All**: every account's week on one page, ranked by the most room for the time left. A sort menu also offers your own order, resets soonest and most used [CCRM-110 (Account Tags)]
- The **Pace map** plots each account against its own week. **Figures**, **Trails** and **Projection** switch its layers; tap a dot for a callout, tap again to open the account [CCRM-111 (Map Layers)]. An account at 0% this week leaves the map, and the line under it counts them [CCRM-109 (Quiet Accounts)]
- Tag an account **Personal** or **Work**, then filter the Summary by either [CCRM-110 (Account Tags)]
- **Share snapshot** sends the whole Summary as one image. Account names are shown; email addresses never are [CCRM-112 (Summary Snapshot)]

**Charts**
- Every 7-day chart and the Pace map have a column per day of your week, so the even-pace line runs through the grid; 5-hour charts have one per hour [CCRM-107 (Day Grid)]
- The 7-day chart's "All" chip now reads **7d** [CCRM-106 (7d Chip)]
- **Show model limits** on an account's page hides its per-model weekly limits [CCRM-99 (Model Limits Toggle)]

**The always-on notification** [CCRM-103 (Hybrid Notification)]
- The weekly window is the bar and the big figure; the 5-hour window is a ring on the left
- With two accounts, two rings on the left and two weekly bars stacked on the right [CCRM-105 (Duet Stack)]
- The status bar shows the 5-hour ring, or the weekly bar for a plan with no 5-hour window
- The home-screen widgets follow the same shapes: the 5-hour window as a ring, the week as a bar

**Try with sample data** [CCRM-113 (Sample Port)]
- On the first screen, or at the bottom of **+ Add account**: three made-up accounts, one per service, on every tab. Nothing is fetched, sent or saved, and **Exit** on the violet banner takes you back. The notification, the widgets and the pings stay on your real accounts

**A new icon** [CCRM-101 (Icon v3)]
- The launcher icon is now about the weekly limit, in the colours of all three services

**On iPhone and iPad** [CCRM-94 (iOS App)]
- Cooldown 2.0 for iPhone and iPad is a public beta on TestFlight: **<TestFlight public link>** (build **<n>**). It reads the same three services and has the Summary, home-screen and lock-screen widgets, reset alerts, a Live Activity and a Control Center control
- There is no iPhone file to download here: TestFlight installs it

## Privacy

- A Gemini account's sign-in and usage requests go to Google, and nowhere else. The [privacy policy](https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md) lists every host, now with Google's
- Nothing else changed: no servers, no analytics, and crash reports stay on your phone until you share them

## Fixed

- <only defects a v1.9 user could see; filled from BUGS.md at the release step>

## Install

- Android: download the APK from [releases/latest](https://github.com/robineam360/Cooldown/releases/latest)
- iPhone and iPad: join the beta at **<TestFlight public link>**
- Read the [Cooldown User Guide v2.0](https://github.com/robineam360/Cooldown/blob/main/release/docs/Cooldown-User-Guide-v2.0.pdf)
- [Privacy policy](https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md)
