# Play Console — Data safety answers (play build)

Drafted 2026-10-01 for RUNBOOK Step 6, CCRM-89 (Privacy Policy). This file answers **the play
build only**: it has no GitHub update check (CCRM-87 (Update Channel)). Every answer below is taken
from the code at the commit that adds this file. [docs/privacy.md](../../docs/privacy.md) is the
plain-language version and must agree with it.

**Rule used:** where Play's definition is unclear, this file takes the **maximal honest answer**, so
it declares more rather than less, and marks the line **re-read on the day**. Whoever fills the form
in the Console re-reads those lines against Play's help text as it reads that day
(<https://support.google.com/googleplay/android-developer/answer/10787469>).

Play's definitions, as read on 2026-10-01:
- **Collection** means "transmitting data from your app off a user's device".
- **Sharing** means transferring collected data to a third party. A transfer the user starts with a
  specific action, where they reasonably expect it, is exempt.
- **On-device-only** data is not declared. Data that is end-to-end encrypted so the developer
  cannot read it is not declared.

## What leaves the device, from code

| Data | Leaves to | Code |
|---|---|---|
| OAuth access token and refresh token, the one-time sign-in code and PKCE value | Anthropic (`platform.claude.com`, `api.anthropic.com`) | `data/ApiClient.kt`, `data/OAuthSignIn.kt` |
| OAuth tokens, the device code, and the ChatGPT account ID (`ChatGPT-Account-Id` header) | OpenAI (`auth.openai.com`, `chatgpt.com`) | `data/source/ChatGptSource.kt`, `data/CodexDeviceSignIn.kt` |
| The access token, on a GET to a path the user types, only when they tap Probe in the hidden Debug section (7 taps on the version); the response is shown, never stored | Anthropic (`api.anthropic.com`, `claude.ai`) or OpenAI (`chatgpt.com`), allowlisted | `data/ApiClient.kt` (`probe`), `SettingsScreen.kt` (EndpointProbe) |
| A crash report: time, exception class, frames, app version and flavor, Android version, manufacturer, model; for an ANR, the system's thread dump (thread names, build fingerprint). No message; all scrubbed. | Only through the share sheet, to the app the user picks | `diag/CrashReport.kt`, `diag/CrashStore.kt` |
| The diagnostics log: timestamps, account keys, status codes, outcomes | Only through the share sheet | `diag/AppLog.kt`, `SettingsScreen.kt` (AppLogCard) |
| A share card PNG: one account's label and usage | Only through the share sheet | `share/ShareCard.kt` |
| The feedback email | The user's own mail app (`mailto:`), written and sent by the user | `SettingsScreen.kt` (AboutCard) |

**Never leaves the device:** the account email (received from the provider and shown in Settings
only), the usage numbers and history, settings, the `store_held_changes` record of CCBG-50 (Degraded
Store Notice) (account keys, sign-in or sign-out, and times, never a token), and the
installed-package checks done through `<queries>`.

**No SDK collects anything:** the dependencies are AndroidX (Compose, Activity, WorkManager,
security-crypto, browser) and OkHttp. There is no analytics, ads, crash-reporting or Firebase
library.

## The form, question by question

### Data collection and security

**Does your app collect or share any of the required user data types?** — **Yes.**

**Is all of the user data collected by your app encrypted in transit?** — **Yes.** Every endpoint is
`https://`. The manifest sets no `usesCleartextTraffic` and no network security config, so Android
blocks cleartext by default for an app targeting API 28 or later (targetSdk is 36). **Re-read on the
day:** crash logs and diagnostics are declared collected only because the user may email them, and
that hop runs through the user's own mail app, not this app. If Play counts that hop, the honest
answer is still Yes for everything the app itself sends.

**Which of the following methods of account creation does your app support?** — **None: "My app
does not allow users to create an account".** Users sign in to accounts that already exist at
Anthropic or OpenAI, and the app creates no account of its own. **Re-read on the day:** if the form
counts "log in with an existing third-party account" as account creation, choose OAuth and give the
policy's *Deleting your data* section as the deletion route: the developer holds no account data.

**Do you provide a way for users to request that their data is deleted?** — **Yes.** Remove account
deletes that account's sign-in, cache, history and settings on the phone; it is not offered for the
last account, so uninstalling or clearing the app's storage is the full delete, and the policy says
so. The developer holds no copy. Anything a user emailed can be deleted on request to
robin@eam360.com.

### Data types

Answer **Yes** to these and **No** to every other type.

#### Personal info → User IDs — **collected**

- **Why declared:** the OAuth tokens identify the user's account at the provider, and the ChatGPT
  account ID is a user identifier sent as a header. Both leave the device, to the provider.
  **Re-read on the day:** Play has no "authentication token" type, and User IDs is the nearest
  honest fit.
- **Collected or shared?** **Collected: yes. Shared: yes, with Anthropic and OpenAI.** This is the
  maximal answer. The user-initiated exemption arguably covers it, since the user signs in to that
  service in the app. But the poll repeats unattended every 15 minutes, so this file declares it.
  **Re-read on the day.**
- **Processed ephemerally?** **No.** The tokens are stored on the device (encrypted) and reused.
- **Required or optional?** **Required.** The app does nothing without a sign-in.
- **Purposes:** **App functionality** (to "authenticate users" and read their usage) and **Account
  management** (to keep the sign-in renewed).

#### App info and performance → Crash logs — **collected (optional)**

- **Why declared:** a crash report reaches the developer when the user shares it, usually by email.
  The app hands it to another app on the device only at the user's tap. **Re-read on the day:** a
  user-initiated share through Android's share sheet may not count as collection at all, and this
  file declares it anyway.
- **Shared:** **No.** It goes only where the user sends it, after a specific user action
  (the user-initiated exemption).
- **Processed ephemerally?** **No.**
- **Required or optional?** **Optional.** The user chooses whether to share.
- **Purposes:** **Analytics**, in Play's sense of "app health, bug diagnosis, performance
  improvements".

#### App info and performance → Diagnostics — **collected (optional)**

- The diagnostics log, shared the same way from Settings → Diagnostics. It holds status codes,
  timestamps and account keys, and never tokens or response bodies.
- **Shared:** No. **Ephemeral:** No. **Optional.** **Purpose:** Analytics. **Re-read on the day**,
  as for Crash logs.

#### Personal info → Email address — **not collected**

- The app receives the email from the provider (Claude's `/api/oauth/profile`, ChatGPT's id_token
  and usage body) and keeps it on the device. It never sends it anywhere. A feedback email is sent
  by the user's own mail app, from an address the user picks.
- **Re-read on the day:** Android Auto Backup copies `usage_cache` (which holds the email and the
  raw usage response, with OpenAI's user and account IDs) and any in-progress sign-in values
  (`oauth_pending`, `device_pending`) to the user's Google backup. That is a system service: Google runs it, it is
  end-to-end encrypted when the phone has a screen lock, and the developer cannot read it. This file
  does not count it as collection. If Play's text then counts system backup as collection, declare
  Email address and User IDs as collected, optional, for App functionality, and note that tokens are
  excluded from backup (`res/xml/backup_rules.xml`).

#### Device or other IDs — **not collected**

- No Android ID, advertising ID, IMEI or install ID is read or sent. The crash report's manufacturer
  and model name a device model, not a device, and they are covered under Crash logs.

#### Everything else — **No**

Location, financial info, health and fitness, messages, photos and videos, audio, files and docs,
calendar, contacts, app activity, web browsing: none is collected. **App activity → Installed
apps:** the `<queries>` checks for the three provider apps and for browsers run on the device only
and are never sent, so the answer is No.

## What the store page will say

Roughly, from the answers above: "Data shared: personal info (user IDs). Data collected: personal
info (user IDs), app info and performance (crash logs, diagnostics). Data is encrypted in transit.
You can request that data be deleted." Check the preview in the Console matches this before
saving.
