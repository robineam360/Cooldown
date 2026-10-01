# Google Play store listing — draft copy

This is the draft copy for the Play Console's **Main store listing**, written for RUNBOOK Step 6,
CCRM-90 (Listing Pack). **Nothing here is published** until CCRM-66 (Play Store Launch), which
is Robin's decision; v1.9 only goes as far as the Console account and the internal-testing track.
Every limit below is Play's character limit as of drafting: **re-read it on the day** in the
Console before pasting, because Play changes them. Counts were measured with Python `len()` on the
exact text (an em dash counts as one character). Provider names appear only as nominative use
(which accounts the app works with), never as the app's own name, icon or graphic.

## 1. App name (limit 30)

None of these contains Claude, ChatGPT, OpenAI, Anthropic, Gemini or Google (checked).

| | Name | Chars |
|---|---|---|
| **Recommended** | `Cooldown — AI usage limits` | 26 |
| Alternative A | `Cooldown: AI Usage Limits` | 25 |
| Alternative B | `Cooldown — Usage Limit Meter` | 28 |

## 2. Short description (limit 80)

No provider names in any of these (checked).

| | Text | Chars |
|---|---|---|
| **Recommended** | `See your 5-hour and weekly AI usage limits, and when they reset.` | 64 |
| Alternative A | `Always-on usage meter and reset pings for your AI chat limits.` | 62 |
| Alternative B | `Track AI usage windows with a notification, status-bar ring and widgets.` | 72 |

## 3. Full description (limit 4000)

**3344 characters** (measured on the text inside the block, trailing newline excluded; blank lines
count). Plain text with "•" bullets, no markdown inside the copy. Every claim is taken from
README.md; it deliberately omits the GitHub update check (the Play build has none), the build
story and version history.

```text
Cooldown shows how much of your usage limits you have used, and when they come back, for your Claude and ChatGPT accounts.

If you work in Claude chat, Cowork or the ChatGPT apps, there is usually no built-in way to see how close you are to the wall. Cooldown puts that number where you can see it without opening anything.

What it shows
• The 5-hour and weekly windows for each account: percent used and when each one resets
• Pace: a mark on every bar and an even-pace line on every chart, so you can tell whether you will finish inside your limit
• A forecast in plain words, such as the time you are on track to reach 100%, built from a local history of your own readings
• Usage history: a bar per 5-hour session, week by week, and a Plan Fit view that reads your closed weeks and says whether your plan fits how you use it
• Pay-as-you-go usage credits, where your plan has them

Always-on notification
A silent notification can carry two accounts side by side. Each half shows the account's name, a bar with the even-pace tick and a big percentage that changes colour as you approach the limit. Expand it for reset times, the weekly rows and a one-tap refresh. A small ring in the status bar fills as your window does.

Reset pings
For every account, choose Off, If busy or Always for a ping when the 5-hour window resets, and again for the weekly window.

Home-screen widgets
Four faces: Ring, Number, Countdown and All accounts. Each one fills whatever size you place it at.

Several accounts
Add as many accounts as you use and mix Claude and ChatGPT freely. Each account has its own tab, its own colour and its own pings. The main screen can fold to one line per card (Compact) or open every chart (Comfortable). Light and dark themes, 15 theme colours and Material You are included.

Sign-in happens on the phone
• Claude: sign in through your browser, then paste the code the page shows
• ChatGPT: type a short code at auth.openai.com on any device
No computer and no copied token are needed.

Good to know
• Claude accounts need a Pro, Max or Team plan. A Free account can sign in, but Anthropic's usage endpoint refuses it, and the app says so instead of showing numbers it cannot get.
• Gemini (Antigravity) is listed in the app but greyed out, because its sign-in cannot be completed on a phone today.
• The app only reads usage. It never sends prompts and never spends your quota.

Privacy
• Your sign-in tokens stay on your device, encrypted with the Android Keystore
• They are sent to Anthropic and OpenAI, and nowhere else
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
- [ ] **Feature graphic, 1024×500** — `design/2026-10-01-play-feature-graphic.html`. No provider
  mark in it either. **Needs Robin's approval** before it is uploaded (CCRM-90 (Listing Pack) is not
  done until he approves it).
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
