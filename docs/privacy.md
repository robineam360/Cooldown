# Cooldown privacy policy

**Effective 1 October 2026 (Cooldown 1.9).** This policy covers both builds of the Android app
Cooldown (package `com.robin.claudeusage`): the one downloaded from GitHub and the one installed
from Google Play. Where they differ, it says so.

Cooldown is made by Robin Richard Rajan, an individual developer. Questions about this policy go to
<robin@eam360.com>.

## The short version

- Cooldown has **no servers**. The developer receives nothing from the app: no account details, no
  usage numbers, no analytics, no crash reports, no identifiers.
- Your sign-ins stay **on your phone**, encrypted with a key held by the Android Keystore. The app
  sends them only to the service they belong to: Anthropic for a Claude account, OpenAI for a
  ChatGPT account.
- There are no ads, no analytics and no tracking, and no third-party SDK that collects data. The
  app is built on Google's AndroidX and Kotlin libraries and the OkHttp network library.
- A crash report or a diagnostics log leaves your phone only if **you** tap Share and pick where it
  goes.

## What the app talks to, and what it sends

Every connection uses HTTPS. The app makes the requests below and no others.

| Service | Hosts | Why | What is sent |
|---|---|---|---|
| Anthropic (Claude accounts) | `claude.com` and `platform.claude.com`, opened in your browser | Signing in | You sign in on Anthropic's own pages, in your browser. The app never sees your password. |
| | `platform.claude.com` | Finishing the sign-in, and renewing it | The one-time code you paste and the PKCE value that proves this phone started the sign-in; later, the refresh token |
| | `api.anthropic.com` | Reading your usage; and reading your plan and email, once a day (hourly while the email is not yet known) | The access token |
| OpenAI (ChatGPT accounts) | `auth.openai.com` | Device-code sign-in, and renewing it | The app's client ID; while you approve, the device code it was given; then the authorisation code OpenAI issued; later, the refresh token |
| | `chatgpt.com` | Reading your usage | The access token and your ChatGPT account ID, which that endpoint requires |
| GitHub (**GitHub build only**) | `api.github.com` | Checking whether a newer release exists | A request for the latest release. No account, token or identifier is sent. |

Every request carries the header `User-Agent: Cooldown/<version> (Android)`, except the GitHub
check, which sends `Cooldown-android`, and Anthropic's token endpoint (sign-in and renewal), which
receives the network library's own name (`okhttp/<version>`). Like any internet connection, each
request reveals your IP address to the service that receives it. What those services do with it is
covered by their own privacy policies: Anthropic's, OpenAI's and GitHub's.

**The Google Play build makes no GitHub request at all.** Play updates the app, so the update check
is not in that build.

**A developer tool, hidden by default.** Settings has a Debug section, which appears only after you
tap the version on the About card seven times. In the builds published on GitHub and Google Play it
sends nothing. The developer's own debug builds add an *Endpoint probe* there, which sends a
read-only GET with an account's access token to a path typed on `api.anthropic.com`, `claude.ai` or
`chatgpt.com`; no published build contains it. Nothing in the Debug section runs unless you tap it.

The app is read-only. It never sends prompts, never spends quota and never calls an endpoint that
would use up a credit.

Links you tap in the app, such as a service's status page, open in your browser. The app sends
nothing with them.

## What is stored on your phone

Everything below lives in the app's private storage, which other apps cannot read.

- **Sign-ins.** For each account: the access token, the refresh token, the token's expiry time, when
  it was added, its last four characters (so a support log can tell two sign-ins apart without
  showing the token) and, for ChatGPT, the account ID. They are kept in `EncryptedSharedPreferences`
  (AES-256-GCM, with the key in the Android Keystore). This encrypted store is **excluded from
  Android backup and device transfer**, so a new phone signs in again.
- **While a sign-in is in progress:** the one-time values needed to finish it (the PKCE value for
  Claude; the device code and the short user code for ChatGPT). They are deleted when the sign-in
  finishes or you cancel it. A sign-in you walk away from keeps them until the next sign-in, Cancel
  or Clear.
- **Account details:** the name you gave each account, its service, its plan (for example "Pro" or
  "Plus"), and the **email address** the service reports for that login. The email is shown only in
  Settings → Accounts, an account's Details and the Rename dialog. It never appears on the
  notification, the widgets or a share card, and is never written to the diagnostics log.
- **Usage:** the latest usage response from each service, the time it was fetched, and the status of
  recent requests. A ChatGPT usage response also contains that account's email, user ID and account
  ID, because OpenAI includes them. The app does not use those fields. They are removed before the
  stored response is shown in the Debug section or written to the log.
- **History:** usage samples for about the last 8 days, and one line per finished window (its peak
  percentage, whether it hit the limit and the plan) for up to a year.
- **Settings:** your choices for the notification, the widgets, the alerts, polling, the theme and
  the layout.
- **Changes made while the secure store was locked.** Rarely, Android's Keystore cannot open the
  sign-in store when the app starts. The app then keeps any sign-in or sign-out you make **in memory
  only**, and says on screen that it lasts only until the app restarts. So that a later start can
  tell you what was dropped, it writes a small record in ordinary app settings
  (`store_held_changes`). The record holds, for each affected account, the account's internal key (a
  short ID such as `p3`, never its name or email) and whether its last change was a sign-in or a
  sign-out; the time of the first change; and a random ID for that run of the app, so that the run
  does not read back its own record. If the store had to be reset, it also holds the time of the
  reset. **It never holds a token.** At the first start where the store opens again, each change
  that the restored store undid becomes an item on an on-screen notice, and the rest of the record
  is deleted. The notice's items are deleted when you dismiss it, and an account's entries are deleted when you
remove that account. A reset is reported straight away
  on its own notice, deleted the same way.
- **Diagnostics log** (`app-log.txt`, in the app's own folder on shared storage, which other apps
  cannot read on Android 12 and later): what the app did and when, with each account's internal
  key, the request status codes, and the outcomes. **It never contains tokens, authorisation
  headers or sign-in codes,** and no response bodies, with one exception: a Debug-section button
  that logs a usage response, which removes emails and account identifiers first. The log keeps
  roughly the latest 600 lines. You can read, share or clear it in Settings → Diagnostics, which
  appears after you tap the version on the About card seven times.
- **Crash reports.** If the app crashes or stops responding, it saves a report in storage that is
  never backed up. A report holds the time, the error's type, the code locations involved, the app
  version and build (GitHub or Play), the Android version, and the phone's manufacturer and model.
  When the app stopped responding, Android's own report of every thread is included, with the
  thread names and the phone's build details. **Error messages are left out,** because they can
  contain personal text, and every report is scrubbed for anything that looks like a token, a key or
  an email address. At most 8 reports are kept, for at most 30 days.
- **A share card**, if you make one: a picture of one account's usage, with the account's name and
  nothing else that identifies it. Only the latest one is kept, in the app's cache.

## Android backup

Cooldown uses Android's standard Auto Backup, which can copy app data to your Google account's
backup so that a new phone keeps your settings and history. Google runs this backup, not the
developer, and the developer cannot read it. It includes your settings, account names and emails,
the latest usage responses (for ChatGPT these contain OpenAI's user and account IDs), usage
history, the diagnostics log, the `store_held_changes` record, and any sign-in still in progress.
It **never includes the encrypted sign-in store or crash reports**. You can turn backup off in your
phone's settings, under Google → Backup.

## What leaves your phone only when you choose

- **Share report** (on a crash notice, or in Diagnostics → Crash reports) and **Share** on the
  diagnostics log open Android's share sheet. The text goes to the app you pick, such as your email
  app. Nothing is sent until you pick one.
- **Share feedback** opens your email app with a message addressed to the developer. You see and
  send it yourself.
- **A share card** goes wherever you send it through the share sheet.

If you send the developer a crash report, a log or an email, it is used only to answer you and fix
the problem. It is not sold or shared, and you can ask for it to be deleted at <robin@eam360.com>.

## What is never collected

The app does not collect your prompts or conversations, contacts, location, photos, files, the list
of apps on your phone, advertising IDs or device IDs. The app does check whether the Claude,
ChatGPT and Gemini apps and any browsers are installed, so that it can open them for you. That
check stays on the phone.

## Permissions

- **Internet:** to reach the services above.
- **Notifications:** for the always-on notification, reset pings and alerts.
- **Foreground service (special use):** keeps the always-on notification up to date while you have
  it switched on.
- **Run at startup:** restarts the always-on notification and the widgets after a reboot.
- **Keep awake** and **view network connections:** added by Android's scheduling library
  (WorkManager), so a background refresh can finish and waits for a connection.

## Deleting your data

- **Remove account** (Settings → Accounts → an account's ⋮ menu) deletes that account's sign-in,
  cached usage, email, history and settings from the phone. It is not offered for your last
  account, and not while the secure store is locked: the app says so and asks you to try again
  later.
- **Clear** deletes only the sign-in. The account's name, email and last usage reading stay until
  you remove the account.
- **Uninstall the app**, or clear its storage in Android's settings, to delete everything, including
  your last account.
- Removing an account here does not end the sign-in on the service's side. To revoke the sign-in
  itself, sign out of your sessions in your Claude or ChatGPT account settings.
- The developer holds no copy of your data, so there is nothing else to delete. The one exception is
  anything you emailed yourself, and you can ask for that to be deleted.

## Children

Cooldown is not directed at children. It works only with accounts on services that set their own
minimum ages.

## Changes to this policy

A change to this policy is published here, in the app's public repository, and its history is
visible on GitHub. The date at the top changes with each revision. A change that lets more data
leave the phone would be named in the release notes of the version that makes it.

Cooldown is unofficial. It is not affiliated with, endorsed by or supported by Anthropic, OpenAI or
Google.
