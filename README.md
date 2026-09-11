<p align="center">
  <img src="release/screenshots/hero.png" alt="Cooldown — your Claude and ChatGPT usage limits, always in your Android shade" width="100%">
</p>

# Cooldown

**Cooldown** — an Android app that shows how much of your **Claude** and **ChatGPT** usage
limits you've burned through, and when your 5-hour and weekly windows reset. Its home is an
**always-on notification** that carries **two accounts side by side**, with a **ring in the
status bar** that fills as your window does, and a **ping the moment a window resets**. Built
for people who live in these tools and keep hitting the wall mid-thought — especially in
**Claude chat, Cowork and the ChatGPT apps**, where (unlike Claude Code) there's no built-in way
to see how close you are.

Every account is a tab, a half of the notification, and its own pair of reset pings — mix
Claude and ChatGPT accounts freely, as many as you use. **Gemini (Google Antigravity) is listed
but greyed out**: its sign-in can't currently be completed on a phone, and the app says so
rather than pretending otherwise.

**Claude accounts need a paid plan — Pro, Max or Team.** A Free account can sign in, but
Anthropic's usage endpoint refuses it (HTTP 403), so the app says so on the account card instead of
showing numbers it can't get. ChatGPT accounts report their plan (Plus, Pro, …) alongside.

**📱 Android only (for now)** — an iOS version is on the roadmap. iPhone folks, watch this space.

📄 **Docs:** [User Guide (PDF)](release/docs/Cooldown-User-Guide-v1.6.pdf) — the full
install → sign-in → notification walkthrough · [Brochure (PDF)](release/docs/Cooldown-Brochure.pdf) —
a 2-page overview.

> ⚠️ **Unofficial.** This is a personal community tool. It is not affiliated with, endorsed
> by, or supported by **Anthropic, OpenAI or Google**. "Claude" is a trademark of Anthropic,
> PBC. "ChatGPT" is a trademark of OpenAI. "Gemini" and "Antigravity" are trademarks of
> Google LLC.

> 🚩 **Please read before you install — this is unofficial and carries some account risk.**
>
> **Claude.** To read your usage, Cooldown signs in with the **same OAuth client as the Claude
> Code CLI** (identical client ID and scopes) and, when it fetches usage, identifies itself as
> `claude-code/<version>` against an **undocumented** endpoint
> (`api.anthropic.com/api/oauth/usage`) — deliberately, because without the CLI's identity
> the request is routed to an aggressively rate-limited bucket. In other words, it
> **impersonates the official CLI**. This is **not sanctioned by Anthropic**, and their
> consumer terms restrict using consumer OAuth tokens in third-party tools. Installing means
> accepting that Anthropic could **revoke your token or flag your account** for an unusual
> traffic pattern.
>
> **ChatGPT.** The same posture, with one honest difference. Cooldown mints **its own token**
> through OpenAI's device-code sign-in — you type a short code at `auth.openai.com`, and the
> phone never sees a token copied from a computer. It does so using the **Codex CLI's client
> ID**, because that is the client the device flow is registered for, and it reads usage from
> an **undocumented** endpoint (`chatgpt.com/backend-api/wham/usage`). Unlike the Claude path
> it does **not** disguise itself: it sends an honest `User-Agent: Cooldown/<version>
> (Android)`, because OpenAI's endpoint doesn't penalise an unfamiliar client. It is still an
> internal endpoint reached with a consumer token by a tool OpenAI has not sanctioned, so the
> same caveat applies — **OpenAI could revoke the token or flag the account**.
>
> What lowers the practical risk on both: the app is **read-only** (it never sends prompts or
> spends quota — and it deliberately never calls OpenAI's one endpoint that *would* spend a
> credit), signs in **once per install** (no repeated logins, no CLI subprocess), polls on a
> slow interval, and is shared privately, not on any store. Use it at your own discretion.

## What it does

- **Two accounts on one always-on notification** 🆕 — a silent notification that carries a
  **First** and a **Second** account side by side: each half has its provider's mark, the
  account's name, a bar with the even-pace tick and a big percentage in that half's warning
  colour. Expand it for both headers with their reset lines, the **Weekly** rows, per-model
  caps and a one-tap Refresh. Each half is its own tap target — Cooldown on that account's
  tab, or the service's own app. One account, or Second set to None, gives the single layout
- **A status-bar ring that says whose it is** 🆕 — a tiny ring fills with the shown account's
  5-hour window, drawn in that account's own colour (Claude terracotta, ChatGPT green) until it
  climbs to yellow, orange and red; at 100% it goes solid red with an ×. Choose **First**,
  **Second** or **Whichever is higher**
- **Reset pings, per account** 🆕 — for each account a **5h reset** and a **Weekly reset**
  ping, each Off / If busy / Always. They post as real notifications even with the always-on
  one running — and fire for an account left idle across the reset, not just one that kept
  chatting
- **Claude *and* ChatGPT accounts, side by side** — pick the service when you add an account;
  each one carries its provider's mark on every surface and its brand colour as its default
  accent, which you can override per account. Gemini (Antigravity) is greyed out until its
  sign-in works on a phone
- **Two rooms** 🆕 — a Claude tab sits on a warm ivory surface with its headline figures in a
  serif; a ChatGPT tab is cool and neutral in the default sans. The cards themselves are
  identical, and the top bar leads with both marks
- **One way to sign in, on the phone** 🆕 — a Claude account signs in through its browser
  sign-in page and a ChatGPT account with a **short code** you type at `auth.openai.com` on any
  device. No computer, no copied token — the paste / QR backup method is gone
- **A plan tag on every card** 🆕 — **Free, Pro, Max, Team Standard** or **Team Premium** beside
  a Claude account, **Plus / Pro** beside ChatGPT. A Free Claude account is told plainly that
  Claude reports no usage for it, in the app and as a strip on the notification
- **Settings in four swipeable tabs** 🆕 — **Accounts · Alerts · Appearance · More**. 24 rows
  instead of 43; one "Show red past the pace mark" switch covers the app and the
  notification; Usage credits appears only for accounts that have a credit budget
- **Pace chart + burn-rate projection** — every reading plotted with threshold guides and a
  forecast tail, plus a plain-words verdict ("At this pace: 100% at 2:40 PM — 1h 20m before
  the reset"), built from a local history of your own polls
- **An "even pace" line on every chart, a tick on every bar** — the diagonal from 0% at the
  window's start to 100% at its reset. Stay below it and you'll finish inside your limit;
  cross it and the overshoot shades red
- **Usage history** — a scrollable bar per 5-hour session, week by week (and a per-week view
  across weeks), so you can see how many sessions you ran and which ones hit 100%
- **Multi-account with editable names** — Personal and Work to start, "+ Add account" for as
  many more as you use, on either service; each gets its own swipeable tab and renames from
  its account card. A window your account doesn't have is simply not shown — no placeholder,
  no dash
- **Pay-as-you-go usage credits** — spent, total and what's left, as a card on the main
  screen; hidden for plans without a credit budget. ChatGPT accounts show a plain
  **"$12.40 balance"** instead, and nothing at all on an unlimited plan
- **15 theme colours** including Material You dynamic colour and **Per provider** (the
  default — each account wears its own service's colour), full light/dark support; usage bars
  shade yellow, orange, then a clear warning-red as you approach 100%
- **The Pulse icon** 🆕 — an ECG beat on charcoal: Claude terracotta in, ChatGPT green out,
  the red spike crossing a dashed even-pace ceiling. No trademarks on the tile

**Gone in v1.6:** the home-screen widgets, the Quick Settings tile, the standalone threshold
and pace alerts, and the three other notification styles and status-bar glyphs. The
notification does their job in one place. **Placed widgets and tiles disappear when you
update** — nothing else to do.

## Screenshots

<table>
  <tr>
    <td align="center" width="33%"><img src="release/docs/src/shots/v16-notif-collapsed.jpg" width="300" alt="Lock screen: the always-on notification collapsed, two accounts on one row"><br><sub><b>Two accounts on one notification</b> 🆕 — collapsed: mark, name, bar with the pace tick, the 5-hour figure</sub></td>
    <td align="center" width="33%"><img src="release/docs/src/shots/v16-notif-expanded.jpg" width="300" alt="Lock screen: the notification expanded, both headers and the Weekly rows"><br><sub><b>Expanded</b> 🆕 — both headers with reset lines, the Weekly rows, one-tap Refresh</sub></td>
    <td align="center" width="33%"><img src="release/docs/src/shots/v16-main-claude-dark.jpg" width="300" alt="Main screen, a Claude account: 5-hour and 7-day windows with pace charts"><br><sub><b>The app</b> — a Claude account in its room: bars, the even-pace line, the burn-rate projection</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="release/docs/src/shots/v16-main-chatgpt-dark.jpg" width="300" alt="Main screen, a ChatGPT account"><br><sub><b>Same cards, other room</b> 🆕 — a ChatGPT account</sub></td>
    <td align="center"><img src="release/docs/src/shots/v16-main-teams-credits.jpg" width="300" alt="Main screen, a Team account with the usage-credits card"><br><sub><b>Usage credits</b> — a Team account with a credit budget</sub></td>
    <td align="center"><img src="release/docs/src/shots/v16-main-claude-light.jpg" width="300" alt="Main screen, light theme"><br><sub><b>Light theme</b> — the ivory room by day</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="release/docs/src/shots/v16-history-5h.jpg" width="300" alt="Usage history, one bar per 5-hour session"><br><sub><b>Usage history</b> — a bar per 5-hour session, week by week</sub></td>
    <td align="center"><img src="release/docs/src/shots/v16-history-7d.jpg" width="300" alt="Usage history, one bar per week"><br><sub><b>Week by week</b> — the 7-day window's peaks</sub></td>
    <td align="center"><img src="release/docs/src/shots/v16-accounts.jpg" width="300" alt="Settings, Accounts tab with Free, Team Standard and Team Premium plan tags"><br><sub><b>Settings · Accounts</b> 🆕 — a plan tag per card: Team Standard, Team Premium, Plus</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="release/docs/src/shots/v16-chatgpt-code.jpg" width="300" alt="ChatGPT sign-in sheet with a device code"><br><sub><b>Sign in to ChatGPT</b> — a short code, typed on this phone or any other device</sub></td>
    <td align="center"><img src="release/docs/src/shots/v16-settings-alerts.jpg" width="300" alt="Settings, Alerts tab"><br><sub><b>Settings · Alerts</b> 🆕 — the notification's two accounts, the ring, reset pings</sub></td>
    <td align="center"><img src="release/docs/src/shots/v16-about-icon.jpg" width="300" alt="About, with the Pulse icon"><br><sub><b>The Pulse icon</b> 🆕 — in About</sub></td>
  </tr>
</table>

<p align="center"><img src="release/docs/src/shots/v16-statusbar-ring.png" width="300" alt="The status-bar ring beside the clock"><br><sub><b>The status-bar ring</b> 🆕 — fills with the shown account's 5-hour window, in that account's own colour</sub></p>

All screenshots are from a real device (a Galaxy Z Fold 7's cover screen) on the current build; the
two notification shots are the lock screen, which is where it lives. **Usage history** fills in over
time as your windows close.

## Get started — about 2 minutes, no computer needed

All you need is your Android phone.

**1. Install the app.** Grab the APK from the
[latest release](../../releases/latest) onto your phone and open it (allow "install
unknown apps" if your phone asks).

**2a. Sign in to Claude.** In the app: **Settings → Accounts → tap "Sign in on this phone"**
on the account card. Your browser opens Claude's sign-in — log in (Pro, Max, and Team
accounts all work), tap **Authorize**, copy the code the page shows, hop back to the app,
and tap **Paste → Finish sign-in**. Usage loads immediately — on a **Pro, Max or Team** plan. A Free
account signs in but gets no numbers: Anthropic's usage endpoint refuses it, and the card says so.

**2b. Sign in to ChatGPT.** Tap **"+ Add account" → ChatGPT**. The app shows a short code and
a countdown. Go to **auth.openai.com/codex/device** — on this phone with the **Open in
browser** button, or on your laptop, whichever is easier — type the code, and approve. The
sheet closes by itself and the new tab appears with your numbers. Nothing is copied from a
computer; the phone mints its own sign-in.

Tracking more than one account? Tap **"+ Add account"** as many times as you like and mix
the two services freely — if you keep each account logged in to claude.ai in a
different browser, the built-in browser picker lets you route each Claude sign-in accordingly
(e.g. Work in Chrome, Personal in Brave).

**Gemini?** The third row in the sheet is greyed out. Google's Antigravity sign-in can't be
completed on a phone today; the app says so rather than hiding the option.

**3. Turn on the notification.** **Settings → Alerts → Always-on notification**, then pick a
**First** and, if you like, a **Second** account under **Show accounts**. Set each account's
**5h reset** and **Weekly reset** pings to Off / If busy / Always on the same tab.

A Claude sign-in renews itself for about a month, then the app reminds you before it
lapses — re-signing in is the same one-minute flow. A ChatGPT sign-in has no fixed expiry we
can predict, so the app doesn't guess one; it tells you if a refresh ever fails.

## How it works

Each account carries which service it belongs to, and the app swaps in that service's fetcher
behind one shared interface. Everything above that — tabs, the notification, reset pings,
history — never learns the difference.

**Claude.** The app signs in with the **same OAuth client as the Claude Code CLI** — the
identical client ID and scopes (`org:create_api_key user:profile user:inference
user:sessions:claude_code user:mcp_servers user:file_upload`) — via browser-based PKCE
against `claude.com` / `platform.claude.com`. To read usage it calls an **undocumented**
endpoint (`api.anthropic.com/api/oauth/usage`) and sends `User-Agent: claude-code/<version>`
on that call, because without the CLI's identity the request is routed to an aggressively
rate-limited bucket. Once a day it also reads `api/oauth/profile` on the same host, which names
the organisation's plan — that is where the card's **Free / Pro / Max / Team** tag comes from, and
how a Free account is recognised: the usage endpoint answers it with **403
`oauth_not_allowed_for_organization`**, so the app stops polling usage for that account and
re-reads the profile instead until the plan changes. In short, it presents itself to Anthropic as the official CLI — see the
⚠️ install warning near the top for what that means for your account.

**ChatGPT.** Sign-in is OpenAI's **device-code flow**, the one the Codex CLI uses: the app
asks `auth.openai.com` for a short user code, you approve it in a browser anywhere, and the
app exchanges the result for its own token. It uses the **Codex CLI's client ID** — that is
the client the flow is registered for — but sends an **honest** `User-Agent:
Cooldown/<version> (Android)`, not a disguise: OpenAI's endpoint doesn't punish an unfamiliar
client the way Anthropic's does, so there's no reason to pretend. Usage comes from the
**undocumented** `chatgpt.com/backend-api/wham/usage`. The app **never** imports a token from
a desktop `auth.json` — reusing that token family would trip OpenAI's rotation detection and
sign you out of your own CLI — and it **never** calls the one nearby endpoint that would spend
a credit.

Both poll on the same battery-friendly interval (15 minutes by default, configurable). Every
poll re-renders the always-on notification; a reset ping is scheduled at the known reset
moment, and an idle account's window is rolled over on the first poll after it.

**Privacy:** your tokens stay on your device, encrypted with the Android Keystore
(EncryptedSharedPreferences, AES-256-GCM). They are sent to Anthropic's and OpenAI's APIs and
nowhere else. There are no servers, no analytics, no third-party network calls. Diagnostics
logs record status codes only — never tokens, headers or response bodies.

## Build from source

Requirements: JDK 17, Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

Stack: Kotlin, Jetpack Compose (Material 3), WorkManager for polling, OkHttp. The
notification is plain RemoteViews. No other dependencies.

## Fair-use notes

- **Both** usage endpoints are undocumented (so is Anthropic's `oauth/profile`, read for the plan
  tag); the parsers are deliberately lenient and this app may break without notice if Anthropic or
  OpenAI changes a payload.
- **Free Claude plans are refused** by Anthropic's usage endpoint. The app doesn't work around
  that — it names the plan and waits for it to change.
- The app only **reads** usage data — it never sends prompts or consumes your quota
  (checking your usage does not count as usage). On the ChatGPT side it deliberately declines
  two things the endpoint would allow: the header that reserves capacity, and the call that
  spends a credit.
- The app presents the Claude Code CLI's OAuth client identity **and User-Agent** to reach
  Anthropic's usage endpoint (see [How it works](#how-it-works)); this is not sanctioned by
  Anthropic. On OpenAI's side it uses the Codex CLI's client ID but identifies itself honestly
  as Cooldown; this is not sanctioned by OpenAI either.
- Anthropic's and OpenAI's consumer terms restrict the use of consumer OAuth tokens in
  third-party tools. Installing accepts some risk of token revocation or account flagging.
  This project exists for personal/educational use — use it at your own discretion.
- Polling is rate-limit-friendly (default 15 min, hard floor of 5 min).

## Feedback

Found a bug or want a feature? Open an issue here, or use "Share feedback" in the app's
About section (it emails <robin@eam360.com>).

## Version history

A quick, plain-English tour of what each update added (newest first). The full technical
changelog lives in the [User Guide](release/docs/Cooldown-User-Guide-v1.6.pdf).

- **1.6** — **Two accounts on one notification, Settings on a diet, the Pulse icon.** The
  always-on notification now carries a **First** and a **Second** account side by side, with
  both headers and the Weekly rows when expanded — the expanded panel now draws at full width
  whatever it holds, so a warning strip can no longer scale the Weekly meters down with it
  (CCBG-26 (Panel Scaling)); the status-bar ring drops its weekly dot and wears the shown
  account's colour (First / Second / Whichever is higher), solid red with an × at 100%.
  **Reset pings** are per account and per window, and fire for an idle account too.
  Settings shrinks to **four tabs** — Accounts, Alerts, Appearance, More. Every Claude card
  wears its plan — **Free, Pro, Max, Team Standard, Team Premium** — read from Anthropic's
  profile endpoint (CCRM-64 (Claude Plan Tag)), and a **Free** account is told plainly that
  Claude reports no usage for it, instead of "Anthropic's server errored" (CCBG-27 (Free Plan
  403)). The **computer-token backup** (paste / Scan QR and the in-app token guide) is
  removed: signing in happens on the phone, for both services (CCRM-63 (Token Import
  Removal)). A new **Pulse** launcher icon, and each tab is a **room**: ivory and serif for
  Claude, neutral for ChatGPT. **Removed:** the home-screen widgets, the Quick Settings tile,
  the standalone threshold and pace alerts, three notification styles and three status-bar
  glyphs — placed widgets and tiles disappear on update. Fixed: the two provider marks in the
  top bar now render the same size.
- **1.5** — **ChatGPT accounts.** The app is now just **Cooldown**, and tracks Claude *and*
  ChatGPT side by side — add either from "+ Add account", sign into ChatGPT with a short code
  at auth.openai.com (no computer, no copied token), and every tab and alert works the same
  for both. Each service's own mark on every surface; a **Per provider** theme where each
  account wears its own colour, overridable per account. A window an account doesn't have is
  now hidden rather than drawn as a dash. Gemini (Google Antigravity) is listed but greyed
  out — its sign-in can't finish on a phone yet.
- **1.4** — **Multi-account** — "+ Add account" for a third, fourth, or more, each with its own tab and alerts. A new clock-hand pace needle on the always-on notification's status-bar icon. Fixed: alerts switched off in Settings now retract their pinned-notification strip instead of leaving it stuck.
- **1.3** — The status-bar icon rebuilt around its real size, with a theme-coloured pace ring and a dot for when the 7-day window needs a look; every alert for every profile now folds into one pinned-notification panel; new hourglass launcher icon; Used-or-Left and countdown-or-clock display switches.
- **1.2** — Three new widget faces (Ring, Mini-Rings, Pace — since retired); pace marks on every bar and ring; **pace alerts** on the projection, not just the percent (since retired); automatic update checks.
- **1.1** — **Usage credits**, four pinned-notification styles (three since retired), and a rebuilt **pace chart** with an even-pace line and threshold guides.
- **1.0** — First official release — everything below, polished into one build and shared with the team.
- **0.14** — Feedback now opens an email (was WhatsApp); added a **Check for updates** button; the app downloads from GitHub Releases.
- **0.13** — The big one: **usage history**, an always-on **pinned notification**, and **finer-grained alerts**. Profiles are renameable, and your history & settings now survive a reinstall.
- **0.12** — **Sign in right on your phone** — no computer needed. Also fixed a recurring sign-in error.
- **0.11** — Internal build (never released).
- **0.10** — Clearer sign-in error messages and easier token setup.
- **0.9** — **Burn-rate forecast** (when you'll hit the limit at your current pace) + sign in by scanning a QR code.
- **0.8** — Smarter heads-ups when your sign-in is about to expire or the data goes stale.
- **0.7** — Renamed to **Claude Cooldown**, new icon, tidier Settings, in-app help.
- **0.6** — Added the **Work** profile alongside Personal, plus Quick Settings tiles (since retired).
- **0.5** — Big visual refresh — cleaner bars, the pacing indicator, and 13 theme colors.
- **0.4** — First usage alerts and a Quick Settings tile.
- **0.3** — Material You theming and dark-mode polish.
- **0.2** — Bigger widget with exact reset times.
- **0.1** — First working app + widget.

## Credits

Made by **Robin Richard Rajan**, built with [Claude Code](https://claude.com/claude-code) 🧡 — the
app was prototyped and **built in a weekend by Claude Fable 5**, with the native sign-in and the
docs finished by **Claude Opus 4.8** (Fable declined the OAuth handshake on cybersecurity grounds),
ChatGPT accounts by **Claude Opus 5** and **Sonnet 5**, and the v1.6 arc run by **Claude Fable 5.1**.

Licensed under the [MIT License](LICENSE). See [RELEASING.md](RELEASING.md) for the
update workflow.
