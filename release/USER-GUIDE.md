# Cooldown — Android app

**A personal Android app for your Claude and ChatGPT plan limits — for as many accounts as you use.**
Shows the 5-hour and weekly rolling windows for every signed-in account (including per-model caps like Fable and Spark), when each resets, how far into the week you are, and forecasts *when* you'll hit a limit at your current pace. Its home is the **always-on notification**: two accounts side by side in the shade, a ring in the status bar, and a ping the moment a window resets.

> Version **1.6** · released 10 September 2026 · sideloaded personal app, not on any store
> Download the APK from the [latest GitHub release](https://github.com/robineam360/Cooldown/releases/latest)
>
> **Note:** the screenshots and much of the walkthrough below date from the v1.3-era
> single-provider app. The sign-in flow and the reading of the cards are unchanged; the
> **v1.6 surfaces — the two-account notification, the four-tab Settings, the Pulse icon — are
> documented in the PDF guide**, which is the current one: `release/docs/Cooldown-User-Guide-v1.6.pdf`.
> The home-screen widgets, the Quick Settings tile and the threshold alerts described in older
> screenshots were **removed in v1.6**.

---

## What it looks like

| Usage history *(illustration)* | Editable profile names | Per-week history *(illustration)* |
|:---:|:---:|:---:|
| ![Usage history bars](screenshots/history-5h-dark.png) | ![Profile name fields](screenshots/profile-names.png) | ![Per-week history](screenshots/history-7d-dark.png) |

> The two **Usage history** images are **illustrations** showing a full week of data. History
> is recorded as each window closes, so a fresh install starts nearly empty and fills in over
> the following days — a real screenshot today would show only a bar or two.

| Sign in on this phone 🆕 | Authorize in the browser | Paste the code, done |
|:---:|:---:|:---:|
| ![Account cards with Sign in on this phone](screenshots/signin-settings.png) | ![Claude's consent page](screenshots/signin-consent.png) | ![Finish signing in step](screenshots/signin-finish.png) |

| Personal tab | Work tab |
|:---:|:---:|
| ![Personal tab](screenshots/app-tabs-personal.png) | ![Work tab](screenshots/app-tabs-work.png) |

| Settings — accounts | Settings — About | In-app token guide |
|:---:|:---:|:---:|
| ![Accounts and profile names](screenshots/settings-top-dark.png) | ![About, version 1.0](screenshots/settings-bottom-dark.png) | ![Get your token screen](screenshots/token-guide.png) |

> For the v1.6 surfaces — the two-account notification, the four-tab Settings, the Pulse
> icon — see the PDF guide, which carries current screenshots.

---

## 1 · Install (Galaxy Fold 7)

1. Download the APK from the [latest GitHub release](https://github.com/robineam360/Cooldown/releases/latest) onto the phone. (In-app, **Settings → More → Check for updates** links you straight there.)
2. Tap the APK. When Android warns about unknown apps, **allow installs from the app you opened it with**, then tap **Install**.
   *A Play Protect "scan app?" prompt may appear — scan or install anyway; it's your own app.*
3. Open **Cooldown** and **allow notifications** when asked (needed for the always-on notification and the reset pings).

**Updating from v0.13 onward:** just install the new APK over it — token, settings and history all survive (releases are signed with a permanent key). **Updating to v1.6:** any placed home-screen widgets and the Quick Settings tile disappear — they were removed; the always-on notification replaces them.

**One-time note for the v0.12 → v0.13 update:** v0.13 moves to a permanent signing key, so this single upgrade needs an **uninstall + reinstall** (Android blocks an in-place update when the signature changes). You'll lose the old on-device history and sign-ins once; re-sign in after installing. Every update *after* v0.13 is a normal in-place install.

---

## 2 · Connect your accounts

Every account signs in **on the phone** — there is exactly one way per service, and neither needs a computer.

### Claude: sign in on this phone

1. Open **⚙ Settings → Accounts** and tap **"Sign in on this phone"** on the account's card.
2. If you have more than one browser, a picker appears — **choose the browser where that account is already logged in to claude.ai** (e.g. Work in Chrome, Personal in Brave). That's how you control *which* account gets connected.
3. The browser opens Claude's sign-in. Log in if needed, then tap **Authorize**.
4. The page shows a **code** — tap **Copy**, switch back to the app, tap **Paste**, then **Finish sign-in**.
5. The card turns **Active**, gains its **plan tag** — Pro, Max, Team Standard or Team Premium — and usage loads.

**Paid plans only.** A **Free** account signs in fine, but Anthropic's usage endpoint refuses it (HTTP 403 `oauth_not_allowed_for_organization`). The card then reads *"Claude doesn't report usage for the Free plan — upgrade to Pro, Max or Team to see numbers here"*, the main screen offers **See Claude plans**, and the notification carries a red *No usage on the Free plan* strip. The app stops polling usage for that account and re-checks the plan instead, so an upgrade is picked up on its own. A Pro plan that lapses to Free lands in the same state.

The sign-in is minted on the phone and is yours alone — no computer shares it, so nothing can rotate it away. It self-renews for about **a month** (the card shows *"Sign-in expires around …"*); re-signing in is the same one-minute flow via **"Re-sign in"**.

### ChatGPT: sign in with a code

Tap **"+ Add account" → ChatGPT** (or **"Sign in with a code"** on an existing ChatGPT card). The sheet shows a short code and a countdown. Go to **auth.openai.com/codex/device** — with **Open in browser** on this phone, or on any other device — enter the code and approve. The sheet closes by itself and the card turns Active with its **Plus / Pro** tag. Nothing is copied from a computer, deliberately: reusing a desktop Codex token would sign that CLI out.

### Removed in the next update: the computer-token backup

Earlier versions kept a **"Use a computer token instead"** section under each Claude card (paste from clipboard, Scan QR, the in-app "How do I get my token?" guide). It is gone. Phone sign-in has carried every account since v0.12, and a pasted desktop token was the only way the desktop's token rotation could break the app.

---

## 3 · Reading the screens

The main screen has **one tab per signed-in account** — swipe horizontally (or tap the tab) to switch. Since v1.6 each tab is a *room*: Claude tabs sit on a warm ivory surface with serif headline figures, ChatGPT tabs on a cool neutral one; the cards are identical. Per account:

- **5-hour window card** — "X% used", the bar, and a split reset line:
  *left* `Resets in 4h 47m` · *right* `Resets at Thu 11:45 PM`
- **7-day window card** — three bars sharing one reset footer (they all reset together):
  - **All models** — your total weekly usage
  - **Fable** (and any other per-model cap the API reports)
- **The even-pace line** *(v1.1)*: each chart carries a diagonal from 0% at the window's start to 100% at its reset. **Below it** → you'll finish inside your limit. **Above it** → you're on course to run out early, and the overshoot shades amber. The readout says it in words too: *"33 points below even pace"*.
- **Burn-rate forecast:** once a window has ~20 minutes of history, each card grows a **sparkline** of that window's usage curve (solid = what actually happened, dashed = where it's heading) and a plain-words projection: *"At this pace: 100% at Thu 2:40 PM — 1h 20m before the reset"* in red when you're on course to hit the wall, or *"At this pace: ~62% when the window resets"* in grey when you're safe. The history is collected from the app's own polls and stays on the phone.

**Bar colors:** your chosen theme color normally → **yellow** above 80% → **orange** above 90% → a clear **warning-red** at 100%. The warning hues are deliberately vivid so they never blend into the muted Claude Orange theme. Only the bars shift color; text stays neutral.

### Usage history 🆕

Tap the **calendar icon** in the top bar to open **Usage history** — a scrollable list of bars, one per window, per profile:

- **5-hour mode** — one bar for every 5-hour session that had usage, newest first, each labelled with its day and start time (e.g. *Mon 09:15*). The bar length is the session's peak, coloured by the warning ladder, and **red when the session hit 100%**. A summary line shows *"N sessions · M maxed out"*, and the **‹ ›** arrows page back through earlier weeks.
- **7-day mode** — one bar per weekly window, so you can see how each week compared.

History is written as each window **closes**, so it fills in going forward — a fresh install starts nearly empty (just the current "now" session) and builds up over the following days.

### The always-on notification (v1.6: two accounts)

Keep an **always-on, silent notification** in your shade. Turn it on under **Settings → Alerts → Always-on notification** and pick a **First** and, optionally, a **Second** account under **Show accounts**.

- **Collapsed** — one row, two halves: each has its provider's mark, the account name, an 8 dp bar with the even-pace tick, and the 5-hour percentage in that half's colour (the account's own colour until 80%, then yellow / orange / red). An account with no 5-hour window shows its **Weekly** figure. One account, or Second = None, gives the single layout.
- **Expanded** — two header blocks (*"Personal · 5h"*, *"ChatGPT · Weekly"*) with bigger bars and reset lines, then the panel: each account's **Weekly** row, per-model caps, any condition strips, and one-tap **Refresh**. The wording is always **5h** and **Weekly**.
- **Tapping a half** opens Cooldown on that account's tab — or that service's own app (**Alerts → Tapping a number opens**).
- **Status-bar ring** — a small ring fills with the shown account's 5-hour window, drawn in that account's colour; solid red with an × at 100%. **Alerts → Status-bar ring shows** picks First / Second / Whichever is higher.
- **Conditions** — a broken sign-in, six hours of stale data, or an available update show as a dot after the name (collapsed) and a labelled strip (expanded). Nothing else posts separately.

Data refreshes automatically every **15 minutes** (configurable, minimum 5); every poll re-renders the notification.

---

## 4 · Reset pings 🔔

The one notification that still posts on its own (the threshold, pace, sign-in and stale-data *alerts* of earlier versions were removed in v1.6 — their information now lives inside the always-on notification as dots and strips).

For each account, under **Settings → Alerts → Reset pings**, set the **5h reset** and the **Weekly reset** to **Off / If busy / Always**. *If busy* pings only when that window had reached 80% before it reset — which kills most of the noise. Pings are prefixed with the account name, fire once per reset, and open the app on that account's tab. Since v1.6 an account left idle across the reset pings too (before, only an account that kept chatting did).

---

## 5 · Quick Settings tile — removed in v1.6

The per-account Quick Settings tiles and the home-screen widgets were removed in v1.6; any you had placed disappear on update. The always-on notification (§3) does their job from the shade, visible from inside any app.

---

## 6 · Settings reference

Settings is **four swipeable tabs** since v1.6 — **Accounts · Alerts · Appearance · More**. On the Fold's inner screen each tab lays out in two columns.

| Tab · Setting | What it does |
|---|---|
| **Accounts** | One card per account: **"Sign in on this phone"** (with a browser picker) or the paste / QR backup for Claude, **"Sign in with a code"** for ChatGPT. Once signed in: status chip, plan badge, last-checked with ↻, renew countdowns, Re-sign in / Clear. The card's ⋮ menu: Rename, Accent colour, Remove account. **"+ Add account"** at the bottom. |
| **Alerts · Always-on notification** | On/off, then **Show accounts**: a **First** chip row and a **Second · optional** one (None to run single). |
| **Alerts · Tapping a number opens** | Cooldown on that account's tab, or that service's app. |
| **Alerts · Status-bar ring shows** | First / Second / Whichever is higher. |
| **Alerts · System notification settings** | Opens Android's per-channel controls. |
| **Alerts · Reset pings** | Per account: **5h reset** and **Weekly reset**, each Off / If busy / Always. |
| **Appearance** | **Theme** System / Light / Dark · **Time format** 12-hour / 24-hour · **Usage display** Used / Left · **Reset time** Countdown / Clock time · **Show red past the pace mark** (one switch for the app and the notification) · **Theme colour**: Per provider (default), Material You, Claude Orange and twelve more. |
| **More** | **Polling** every 5 / 15 / 30 / 60 min · **Usage credits** per account (only shown for accounts with a credit budget) · **Updates** → **Check for updates** · **Diagnostics** → share your log · **About** (version, credits, **Share feedback** to robin@eam360.com; tap the version 7× to append a **Debug** section). |

Below the Refresh button the app shows **Last success** and **Last attempt** as "Thu 7:46 PM (12m ago)". A red status line appears only when something's wrong.

---

## 7 · Troubleshooting

| Symptom | Meaning / fix |
|---|---|
| A notification half is dimmed / "stale" strip | Polls for that account have failed for six hours — the last known numbers stay up, never a blank. Usually temporary; self-heals on the next successful poll. |
| **"Re-auth needed"** | The token and its refresh both failed. Tap **"Re-sign in"** on that card (§2). |
| **"Claude doesn't report usage for the Free plan"** | The account is on Claude's Free plan (or a Pro plan that lapsed). Anthropic's usage endpoint refuses Free organisations; the app names the plan and re-checks it on every poll. Upgrade to Pro, Max or Team, or remove the account. |
| **"Rate limited (429)"** status | The API asked us to back off; automatic retries with increasing delays (5 min → 1 h max). |
| **"Token refresh failed (HTTP 429)"** status | A *dead* refresh token, not real rate-limiting (Anthropic answers 429 for dead tokens). Fix: **"Re-sign in"** (§2). *(Historical note: through v0.11 the app itself could trigger a deterministic 429 on every renewal — fixed in v0.12, see §9.)* |
| My widgets / tile vanished | Expected on the v1.6 update — they were removed. Turn on the always-on notification (Settings → Alerts). |
| No reset ping arrived | Check notification permission, that the account's 5h / Weekly reset isn't Off, and that *If busy* isn't hiding a window that never reached 80%. |
| Anything else | Settings → More → **Diagnostics** shares the app log; the Debug section (7 taps on the version) shows the last raw response. |

---

## 8 · Privacy & good-to-know

- **No servers, no telemetry, no analytics.** The app talks only to `api.anthropic.com` (usage, and once a day the profile that names the plan), `platform.claude.com` (sign-in code exchange + token refresh), `claude.com` (the sign-in page in your browser), `auth.openai.com` (the ChatGPT device-code sign-in) and `chatgpt.com` (ChatGPT usage) — plus `api.github.com` only when you tap Check for updates.
- Token lives in **Android-Keystore-encrypted storage**; cached usage numbers contain nothing sensitive.
- **Backup (since v0.13):** Android Auto Backup now preserves your usage history and settings across a reinstall or a new phone. The **encrypted sign-in token is deliberately excluded** from backup (it's sealed to this device's Keystore and can't be restored anyway) — after a restore you simply sign in again.
- Polling is deliberately gentle (15-min default, 3-min manual floor, exponential backoff on 429s).
- Heads-up: reading either usage API with a consumer token outside the official clients is not covered by Anthropic's or OpenAI's consumer terms. Personal-use risk was accepted when this was built.

---

## 9 · Version history

- **1.6** — **Two accounts on one notification, Settings on a diet, the Pulse icon.** The always-on notification carries a **First** and a **Second** account side by side — mark, name, bar with the pace tick and a big figure per half — and, expanded, both headers with reset lines, the **Weekly** rows, per-model caps and Refresh; each half is its own tap target, and the expanded panel now draws at full width whatever it holds, so a warning strip can no longer scale the Weekly meters down with it (CCBG-26 (Panel Scaling)). The status-bar **ring** drops the weekly dot and wears the shown account's colour (**First / Second / Whichever is higher**), solid red with an × at 100%. **Reset pings** are per account and per window (5h / Weekly, Off / If busy / Always) and now fire for an account left idle across the reset (CCBG-25 (Idle Reset Silence)). Settings shrinks from 43 rows in 13 sections to **24 rows in four tabs** — Accounts, Alerts, Appearance, More — with one "Show red past the pace mark" switch for app and notification, and Usage credits only for accounts that have a budget. Every Claude card wears its plan — **Free, Pro, Max, Team Standard, Team Premium** — read from Anthropic's profile endpoint (CCRM-64 (Claude Plan Tag)), and a **Free** Claude account is told plainly that Claude reports no usage for it, on the card, the main screen and the notification, instead of "Anthropic's server errored" (CCBG-27 (Free Plan 403)). The **computer-token backup** — paste, Scan QR, the in-app token guide — is removed; signing in happens on the phone for both services (CCRM-63 (Token Import Removal)). New **Pulse** launcher icon (an ECG beat on charcoal — Claude terracotta, ChatGPT green, the red spike crossing a dashed even-pace ceiling); each tab is a **room** (ivory and serif for Claude, neutral for ChatGPT); the top bar leads with both marks, now the same size (CCBG-23 (Mark Size Mismatch)). **Removed:** all five home-screen widgets, the Quick Settings tile, the standalone threshold / pace / sign-in / stale / update notifications and their six channels, the Gauge / Number tile / Progress bar notification styles and the Pie / Battery / Number status-bar glyphs — placed widgets and tiles disappear on update. Built with **Claude Fable 5.1**, **Opus 5** and **Sonnet 5**.

- **1.5** — **ChatGPT accounts, and the app becomes just "Cooldown".** The app now tracks **ChatGPT** alongside Claude: pick the service from **"+ Add account"**, sign in with a **short code** at auth.openai.com (on the phone or any other device — no computer token to copy), and the account behaves exactly like a Claude one from there, with its own tab, widgets, tiles, alerts, history and pace chart. The name drops "Claude" and the launcher icon becomes a **three-sand hourglass**, one band per service; every account carries its **provider's own mark** on its card, its tab, the pinned notification and the widget picker. New **"Per provider"** theme, now the default — each account wears its own service's colour — with a per-account override under the card's **⋮ → Accent colour**. **A window an account doesn't have is no longer drawn at all** (no dash, no empty bar) — this applies to Claude accounts too, and a widget configured on a missing window says so in words. **Gemini** (Google Antigravity) is listed in the Add-account sheet but **greyed out**: its sign-in needs a callback only a desktop can answer. ChatGPT accounts show no "expires around" line, because OpenAI publishes no token lifetime and a wrong estimate is worse than none. The repo moved from `CCooldown` to `Cooldown`. Built with **Claude Opus 5** and **Sonnet 5**.

- **1.4** — **As many accounts as you use, a clock-hand gauge, and alerts that actually turn off.** Accounts moved to a proper registry — **"+ Add account"** for a third, fourth or more, each with its own sign-in, tab, widgets, tiles and alerts, renamed from its card's **⋮ menu**; a permanent internal slot keeps notification IDs and alarms stable across a rename. The always-on notification's status-bar icon gets a **rails gauge**: usage as a length against tick-mark rungs, with the pace mark redrawn as a **clock-hand needle** sweeping from 12 o'clock, shared by the Ring and Pie styles. **Fixed:** switching an alert off in Settings left its pinned-notification strip stuck on screen until its own lifetime ran out; "keep alerts in the shade for" was a one-time stamp frozen when the alert first fired, not the live rule it reads as. Built with **Claude Sonnet 5**.

- **1.3** — **A status-bar meter you can actually read, the weekly dot, and one alert surface.** The tiny status-bar icon was rebuilt around its real rendered size (~14 dp — measured on-device, not assumed): one large **pace-marked ring** for the 5-hour window, drawn **in your theme colour** and stepping to yellow/red near the limit, with a contrasting **pace line** at where you'd be at an even burn — each theme carries its own line colour, chosen never to clash with the warning ladder. New **weekly dot**: a dot appears in the ring's middle only when the **7-day window** deserves attention — grey = on even pace, yellow = above pace, red = spent; no dot means the week is fine. The dot uses the exact same pace verdicts as the in-app "above/on/below even pace" sentence, so the two can never disagree. **One alert surface:** with the pinned notification on, every alert for both profiles folds into its panel — nothing posts separately. **Display, your way:** a global **Used or Left** switch flips every numeric readout; your chosen reset form (**countdown or clock time**) leads on every surface; a **light/dark override** and follow-system time format. **When something breaks it says what to do:** typed failures ("Re-auth needed — paste a fresh token"), inferred numbers marked as inferred, and a shareable **app log** in Settings. The launcher icon became an **hourglass** — a tracker's identity. Smaller: **long-press the app icon** for Personal/Work/Refresh shortcuts; theme changes repaint the notification immediately. **Fixed:** light theme darkens the status-bar clock and icons (no more white-on-pink); a second notification can no longer hide the live meter. Built with **Claude Fable 5**.

- **1.2** — **Widget faces, pace everywhere, and a status bar that never lies.** Three new home-screen widgets — **Ring** (one window as a pace-marked ring), **Mini-Rings** (every window as battery-style rings) and **Pace** (the full pace story with an on-face 5h/7d toggle). The **even-pace tick** now sits on every bar and ring — in-app, widgets and the pinned notification — with the over-pace overshoot in red, gated per surface by three Settings toggles. New **pace alerts** warn on the projection, not just the percent: *Will run out*, *Cutting it close*, *Almost out*. A 6-hourly **automatic update check** notifies once per new version with a skip action; account cards gain quick links to **Anthropic status** and the **usage dashboard**, and the plan chip shows the rate-limit multiplier. **Notifications:** the app owns its notification group so a second alert no longer swaps the status-bar meter for the app icon; sign-in-expiry and stale-data fold into the pinned panel as tinted strips; event alerts expire on a configurable lifetime; the **app icon** became a closed ring that can't be read as a percentage. Smaller: browser **icons in the sign-in picker**, system **reduce-motion** honoured, **long-press a widget** to reconfigure it. **Fixed:** ring faces size from the face (legible at small sizes); model caps and credits survive unusual account payloads; the "left" figure reads the binding constraint. **Removed:** a hidden, off-by-default experiment that could start 5-hour windows on a schedule — it spent real quota from a third-party app, the wrong side of Anthropic's terms. Built with **Claude Fable 5**.
- **1.1** — **Credits, a readable chart, and a notification you can read at a glance.** Pay-as-you-go **usage credits** on the main screen and as a single-bar widget (per-profile switches, plus an opt-in for the tall widget); **four notification styles** — Gauge, Number tile, Progress bar and Huge number — so the pinned percentage is finally legible, with the collapsed row now showing the 5-hour window only; tapping the notification can open the **Claude app**; the **Quick Settings tile** shows the 5-hour reset as a countdown or a clock time and its icon fills as the window burns; a rebuilt **pace chart** — twice as tall, a dot per reading, threshold guides at 80/90/100% and an **even-pace line** with the overshoot shaded amber. **Fixed:** the chart and forecast almost never appeared (readings were matched to a window by an exact reset timestamp the server nudges forward on nearly every check — 561 distinct values across 672 readings, one matching); usage history was wiped on sign-out; the forecast fitted only the first and last reading. **Retired** the Days-elapsed bar. Built with **Claude Opus 5**.
- **1.0** — **First official release.** The v0.1–v0.14 beta, now official and shared with the team — everything below in one polished build: live 5-hour & 7-day windows for Personal + Work, usage history, pinned notification, granular alerts, burn-rate forecast, home-screen widgets & Quick Settings tiles, on-phone sign-in — private, no servers, backed up. Prototyped and built over a weekend by **Claude Fable 5**; the native sign-in Fable declined (on cybersecurity grounds) and all the docs finished by **Claude Opus 4.8**.
- **0.14** — **Feedback, updates & distribution.** Share feedback now opens an email to robin@eam360.com (was WhatsApp); new **Check for updates** in About queries the public GitHub Releases API and links to the latest release; the APK is no longer committed to the repo — it ships only as a GitHub Release asset.
- **0.13** — **History, pinned notification & finer alerts.** New **Usage history** screen: scrollable bars, one per 5-hour session (day + start time, red when it hit 100%) with a week pager, plus a per-week view — backed by a new on-device session log kept for a year. New optional **pinned notification**: an always-on, silent status readout with a status-bar gauge icon that fills with your 5-hour usage (Ring/Pie/Battery/Number styles), expanding to the 7-day and per-model bars with a Refresh action. **Alerts are now granular** — per-window threshold chips, reset pings set to Off/If-busy/Always, and a per-profile mute — instead of three blunt toggles. **Profile names are editable.** The 100% bar colour is now a true warning-red (previously an orange that clashed with the Claude Orange theme). And **Android Auto Backup** now preserves history + settings across reinstall/new-device (the encrypted token is excluded). Releases are now signed with a permanent key, so from here on updates install in place and keep your data — the one-time cost was this upgrade needing a reinstall.
- **0.12** — **Native sign-in release.** Each account card now has **"Sign in on this phone"**: a browser-based sign-in (the same PKCE flow Claude Code uses) that needs **no computer at all** — tap, authorize in the browser of your choice (picker included, so Work and Personal can live in different browsers), paste the code, done. Works for Pro/Max and Team accounts. The sign-in is minted on the phone, so no computer can rotate it away; it self-renews for ~30 days and the card shows *"Sign-in expires around …"* with the usual 7/3/1-day warnings before it lapses. This also fixed the chronic **HTTP 429** on token exchange/renewal: Anthropic's token endpoint sits behind a firewall that rejects requests identifying themselves as `claude-code` — the app no longer sends that identity on token calls (usage calls still require it). The computer-token paste/QR method remains as a backup under "Use a computer token instead", and the desktop-paste renewal may well be steadier now too (same 429 fix applies to renewals). *(0.11 was an internal build, never released.)*
- **0.10** — QR fixes & diagnostics. The in-app token guide's Windows QR command now extracts just the sign-in object and pipes it (the full credentials file can hold other logins and overflow a QR code's ~2.9 KB capacity; piping also avoids PowerShell 5.1 mangling quotes); Linux switched to a `jq` pipe for the same reason. Refresh failures now say *why* in the status line — e.g. "Token refresh failed (HTTP 429)" (a dead/rotated refresh token) vs. a network error — instead of a blind "token refresh failed". The in-app guide's "Re-pasting often?" section now covers the Mac Keychain variant of the dedicated-sign-in ritual (full commands in this guide, §2).
- **0.9** — Forecast & QR release. The app now keeps a local history of its own polls (8 days, on-phone only) and each window card shows a **sparkline + burn-rate forecast**: when you'll hit 100% at the current pace and how long before the reset that is, or the projected percent at reset when you're safe. **Per-model weekly caps** (e.g. Fable) now fire their own 90% alert. **"Scan QR"** on the account cards imports a token straight from a QR code rendered in the computer's terminal — no clipboard needed (in-app guide has the commands). Notifications now open the app on the alert's own profile tab. Quieter on the API: the Quick Settings tiles skip their refresh when data is under 3 minutes old, and manual refreshes now also evaluate alerts immediately instead of waiting for the next background poll.
- **0.8** — Token health release. Account cards now show a plan badge (Pro/Max/Team), an auto-renew countdown, the last auto-renewed time, and "sign-in valid until <date> · Xd Xh Xm to go" (read from the pasted JSON). New notifications: sign-in expiry early warning (7/3/1 days out) and a stale-data alert after 6+ hours of failed polls; the re-auth alert now also fires when renewals have failed continuously for 6+ hours (previously a dead token could look like a permanent "will retry"), and notifications self-dismiss when fixed. Polling section shows the next automatic check; cards show rate-limit backoff. Guide explains why re-pasting happens (token rotation by the source computer) and the dedicated-sign-in workaround.
- **0.7** — Renamed to **Claude Cooldown (CCooldown)** with a new cooldown-sweep launcher icon (adaptive + themed-icon support). Settings redesigned into grouped sections: account cards with one-tap "Paste from clipboard" (no more text box), token status chip, last-checked with check-now, added date and token tail; in-app "Get your token" guide (macOS/Windows/Linux + expiry explainer); poll-interval presets; token-expiry alert toggle; add-widget shortcuts; About with credits and WhatsApp feedback; hidden debug (7 taps on the version). "Starts when a message is sent" shown for windows that haven't begun. System back now navigates instead of exiting.
- **0.6** — Two profiles (Personal + Work) with swipeable tabs, per-profile tokens/cache/backoff/alerts; widget setup screen on placement (choose profile); new single-bar widget (choose any one bar); window-reset notifications scheduled at the exact reset moment; two Quick Settings tiles; alerts prefixed with the profile name.
- **0.5** — Redesigned per feedback: grouped 7-day card (All models / Fable / **Days elapsed** pacing bar), Claude-style bars (tint track + solid fill), bar-only color shift (yellow 80 / orange 90 / red 100), split reset rows with day + 12/24-hour time setting, 13 theme colors (Claude Orange default), widget refresh icon + bigger text, widget body tap opens app, "Last success/attempt (Xm ago)" lines. Fixed a widget truncation bug (Android's 10-child RemoteViews limit).
- **0.4** — Usage alerts (80/95% session, 90% weekly, re-auth), Quick Settings tile, auto-refresh on app open.
- **0.3** — Material You polish: system corner radius, dynamic colors, dark-mode-aware statuses.
- **0.2** — Large widget layout with exact reset date-times.
- **0.1** — First working app + widget.

## 10 · For future rebuilds (dev notes)

- Source: the git working copy at `~/Projects/Cooldown`, pushed to GitHub `robineam360/Cooldown`; single-module Android project
- Stack: Kotlin · Jetpack Compose · WorkManager · OkHttp · AGP 9.2.1 (built-in Kotlin 2.3.10) · min SDK 31, target 36. No Glance since v1.6 — the notification is plain RemoteViews.
- Build on the Mac (signed release): `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`. Signing reads the gitignored `keystore.properties` + `ccooldown-release.jks` at the repo root — **back those up; losing them means no more updates.** See `RELEASING.md`.
- RemoteViews gotcha: containers max out at 10 children — keep notification blocks wrapped in nested layouts.
- If Anthropic changes the undocumented response schema, the parser ignores unknown fields; if bars go blank, check the raw JSON in the debug view first.

*Built and verified with Claude Code; v1.6 on 11 Sep 2026.*
