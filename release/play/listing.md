# Google Play store listing — draft copy

This is the draft copy for the Play Console's **Main store listing**, written for RUNBOOK Step 6,
CCRM-90 (Listing Pack). **Nothing here is published** until CCRM-66 (Play Store Launch), which
is Robin's decision; v1.9 only goes as far as the Console account and the internal-testing track.
Every limit below is Play's character limit as of drafting: **re-read it on the day** in the
Console before pasting, because Play changes them. Counts were measured with Python `len()` on the
exact text (an em dash counts as one character). Provider names appear only as nominative use
(which accounts the app works with), never as the app's own name, icon or graphic.

**Rewritten for v2.0 on 2026-11-11** (RUNBOOK Step D1), row by row against
[V2.0-ASSET-CHECK.md](V2.0-ASSET-CHECK.md). It describes the app as it will be once the v2.0 feedback
items, CCRM-105 to CCRM-113 (Duet Stack to Sample Port), are built; **read every claim against the
v2.0 release build before pasting** (Step 5 shoots the same build).

| Asset-check row | Verdict | What changed | Count / limit |
|---|---|---|---|
| Short description | Rewrite | Weekly leads, pace added (§2) | 75 / 80 |
| Full description, opening line | Wrong for v2.0 | Names Gemini | in the full text |
| "Good to know" Gemini line | Wrong for v2.0 | The greyed-out line is gone; Gemini's sign-in is under *Sign-in*, and one line says what a Gemini account shows | in the full text |
| "What it shows" | Incomplete | Weekly first with the 5h ring; the pace mark and the pace line naming the reset (CCRM-108 (Reset Named)); the Vs pace figure; the day grid (CCRM-107 (Day Grid)); per-model limits and their switch; a new *Summary* section with the Pace map | in the full text |
| Always-on notification | Wrong for v2.0 | Weekly bar and figure, 5h ring, two accounts at once (CCRM-103 (Hybrid Notification), CCRM-105 (Duet Stack)) | in the full text |
| Home-screen widgets | Check | The fourth face is the Strip, as the picker names it | in the full text |
| Several accounts | Extend | Gemini, the Personal/Work tag and the Summary filter (CCRM-110 (Account Tags)) | in the full text |
| Sharing | Add | One account's card or the Summary snapshot (CCRM-112 (Summary Snapshot)) | in the full text |
| — | New | *Try it first*: the sample mode, CCRM-113 (Sample Port) | in the full text |
| Privacy, recipients | Wrong for v2.0 | Each token goes only to its own service: Anthropic, OpenAI or Google | in the full text |
| Full description, whole | — | Measured after the rows above | 3,930 / 4000 |

Kept as they were: the app name, "Please read: this app is unofficial", the account-risk line, category,
contact, website and the privacy policy URL (the policy itself gains the Google rows in the same
commit as this rewrite). The text dropped to fit 4000: the second opening paragraph, the Compact /
Comfortable sentence and the expanded notification's per-model rows.

## 1. App name (limit 30)

None of these contains Claude, ChatGPT, OpenAI, Anthropic, Gemini or Google (checked).

| | Name | Chars |
|---|---|---|
| **Recommended** | `Cooldown — AI usage limits` | 26 |
| Alternative A | `Cooldown: AI Usage Limits` | 25 |
| Alternative B | `Cooldown — Usage Limit Meter` | 28 |

## 2. Short description (limit 80)

No provider names in any of these (checked). v2.0: the weekly window leads, CCRM-96 (Weekly First).

| | Text | Chars |
|---|---|---|
| **Recommended** | `See your weekly and 5-hour AI usage limits, your pace, and when they reset.` | 75 |
| Alternative A | `Weekly and 5-hour AI usage limits, your pace and resets, at a glance.` | 69 |
| Alternative B | `Your AI usage limits on a notification, widgets and one summary page.` | 69 |
| v1.9 (replaced) | `See your 5-hour and weekly AI usage limits, and when they reset.` | 64 |

## 3. Full description (limit 4000)

**3,930 characters** for v2.0 (measured on the text inside the block, trailing newline excluded; blank
lines count). Plain text with "•" bullets, no markdown inside the copy. Every claim is taken from the
approved v2.0 designs and ROADMAP.md; it deliberately omits the GitHub update check (the Play build has
none), the build story and version history. Gemini is named only as an account the app reads.

```text
Cooldown shows how much of your AI usage limits you have used, and when they come back, for your Claude, ChatGPT and Gemini accounts, in places you can see without opening anything.

What it shows
• The weekly window first, with the 5-hour window beside it as a small ring: how much is used, and when each one resets
• Pace: a mark on every bar shows where even use would be by now, and one line says how far ahead or behind you are and when the window resets, such as "6d 23h behind · resets Wed 12:30 PM"
• A big figure for what you have used, what is left, or your distance from the pace mark: green with room to spare, red when ahead of it
• Charts with a column for every day of the week, and a forecast of where the window will end, from a local history of your readings
• Per-model weekly limits where your plan has them, with a switch to hide them
• Usage history by 5-hour session and by week, and a Plan Fit view that says whether your plan fits how you use it
• Pay-as-you-go usage credits, where your plan has them

Summary
With two or more accounts, the app opens on All: every account's week on one page, ranked by the most room for the time left, or in your own order. The Pace map plots each account against its own week; tap a dot for its details.

Always-on notification
A silent notification shows the weekly window as a bar with the pace tick and a big figure, and the 5-hour window as a ring beside it. It can carry two accounts at once. Expand it for reset times and a one-tap refresh. A small ring in the status bar fills as your 5-hour window does.

Reset pings
For every account, choose Off, If busy or Always for a ping when the 5-hour window resets, and again for the weekly window.

Home-screen widgets
Four faces: Ring, Number, Countdown and Strip, which carries up to four accounts. Each fills whatever size you place it at.

Several accounts
Mix as many Claude, ChatGPT and Gemini accounts as you use. Each has its own tab, colour and pings, and a Personal or Work tag filters the Summary. Light and dark themes, 15 theme colours and Material You.

Sharing
Share one account's usage, or a snapshot of the whole Summary, as an image. Account names are shown; email addresses never are.

Try it first
Tap "Try with sample data" on the first screen to see every tab with three made-up accounts. Nothing is fetched, sent or saved.

Sign-in happens on the phone
• Claude: sign in through your browser, then paste the code the page shows
• ChatGPT: type a short code at auth.openai.com on any device
• Gemini: sign in on Google's own page, then paste the code it shows
No computer and no copied token are needed.

Good to know
• Claude accounts need a Pro, Max or Team plan. A Free account can sign in, but Anthropic's usage endpoint refuses it, and the app says so.
• Gemini accounts show the usage windows that Google's Antigravity reports for your Google account.
• The app only reads usage. It never sends prompts and never spends your quota.

Privacy
• Your sign-in tokens stay on your device, encrypted with the Android Keystore
• Each one is sent only to its own service: Anthropic, OpenAI or Google
• No servers, no analytics, no ads, and no tracking or crash-reporting SDKs
• Crash reports stay on your phone until you choose to share them

Free and open source. The code is at github.com/robineam360/Cooldown

Please read: this app is unofficial
Cooldown is an independent community project. It is not affiliated with, endorsed by or supported by Anthropic, OpenAI or Google. "Claude" is a trademark of Anthropic, PBC. "ChatGPT" is a trademark of OpenAI. "Gemini" and "Antigravity" are trademarks of Google LLC. Provider names are used only to describe which accounts the app works with.

Account risk: Cooldown reads undocumented usage endpoints using your own sign-in. The providers have not sanctioned this, so they could revoke the sign-in or flag the account. Use it at your own discretion.
```

## 4. Category and contact details

- **Category:** Tools. It is a small utility that sits in the notification shade and on the home
  screen; Productivity implies task or work management, which it is not.
- **Tags:** pick from Play's own tag list in the Console on the day (it is a fixed list);
  usage tracking, widgets and notifications are the closest fits.
- **Contact email:** robin@eam360.com
- **Website:** https://github.com/robineam360/Cooldown
- **Privacy policy URL:** https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
  (the policy file is CCRM-89 (Privacy Policy) work; check the URL resolves before submitting)

## 5. Graphics checklist

- [ ] **App icon, 512×512 PNG** — export from the Pulse launcher icon (the ECG beat on charcoal).
  No provider marks on it.
- [x] **Feature graphic, 1024×500** — `release/play/feature-graphic.png` (24-bit RGB, no alpha),
  variant B of `design/2026-10-01-play-feature-graphic.html`, approved by Robin on 2026-10-01. No
  provider mark in it either.
- [ ] **Phone screenshots, 2 minimum, 4–8 recommended.** Candidates in `release/screenshots/`
  (`token-guide.png` is excluded: it shows a removed feature):
  1. `hero.png` — the README hero, if it works as a plain phone shot
  2. `app-tabs-personal.png` — main screen, an account tab
  3. `app-tabs-work.png` — main screen, a second account tab
  4. `chart-above-pace-work-fold-inner.png` — chart above the even-pace line
  5. `chart-wide-fold-inner.png` — wide chart
  6. `history-5h-dark.png` — usage history, 5-hour sessions
  7. `history-7d-dark.png` — usage history, weekly pane
  8. `settings-top-dark.png` — Settings
  Caveat: these files date from 7 Aug (early v0.x) and the Pulse-era app has changed since; some
  may no longer match the current UI. **Robin's newer full-frame 1080×2371 shots should replace them
  wherever he has them** (the v1.7/v1.8 shots in `release/docs/src/shots/` cover the main screen,
  the always-on notification, widgets, Accounts and Plan Fit). Play shows the first ones
  most prominently, so lead with the main screen and the notification.
- A provider logo that appears inside the app's own UI in a screenshot is fine (it is the real
  UI). No screenshot may be framed, captioned or arranged to suggest endorsement by a provider.
- [ ] **7-inch and 10-inch tablet screenshots** — optional.

## 6. Release notes on Play — the closed track and production (limit 500)

Pasted at CONSOLE-STEPS.md step 26 (and again for production). Plain text, the app only. Written
2026-11-11 (RUNBOOK Step D1) from `release/docs/release-notes-v2.0.md`; **420 characters**. Re-read
Play's limit on the day.

```text
Cooldown 2.0, the first release on Google Play.
• Gemini accounts, beside Claude and ChatGPT
• The weekly limit leads, with the 5-hour window as a ring and a pace line that says when it resets
• A Summary page with every account and a Pace map
• An always-on notification that carries two accounts, and four home-screen widgets
• Personal and Work tags, and a shareable snapshot
• Try with sample data, no account needed
```
