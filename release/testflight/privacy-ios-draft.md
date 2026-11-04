# iOS section for docs/privacy.md — DRAFT, not merged

Text to add under the Android policy once Robin approves (the policy covers the Android builds only
today; its effective date and title change with the merge). Everything below was read from the code
on 2026-11-04; re-check it against the build that is submitted.

## Cooldown for iPhone and iPad

Cooldown for iPhone and iPad (bundle id `com.robinrichardrajan.cooldown`) follows the same short
version as Android: no servers, no analytics, no tracking, no third-party SDKs. The developer receives
nothing from the app.

**What the app talks to.** HTTPS only, to the services you sign in to:

| Service | Hosts | Why |
|---|---|---|
| Anthropic (Claude) | `claude.com`, `platform.claude.com` (sign-in, in your browser), `api.anthropic.com` | Signing in, renewing, reading usage, plan and email |
| OpenAI (ChatGPT) | `auth.openai.com`, `chatgpt.com`, `api.openai.com` | Device-code sign-in, renewing, reading usage |
| Google (Gemini) | `accounts.google.com`, `oauth2.googleapis.com`, `www.googleapis.com`, `daily-cloudcode-pa.googleapis.com` | Signing in with Google's own page, renewing, reading usage and your Google account email |

Status pages (`status.anthropic.com`, `status.openai.com`, `status.cloud.google.com`) open in your
browser only when you tap a link. Requests carry `User-Agent: Cooldown/<version> (iOS)` and reveal
your IP address to the service that receives them.

**What is stored on your device.**
- *Sign-ins* — in the iOS Keychain, in an access group shared only with Cooldown's own widget
  extension, marked "this device only": not synced to iCloud and not carried in a backup or to a new
  device, which signs in again.
- *Account details, the latest usage readings, history (about 8 days of samples; a year of finished
  windows) and layouts* — in the app's private App Group container, readable only by Cooldown and its
  widgets. Standard iOS device backups can include it.
- *Settings* — in the app's own preferences.

**What leaves the device only when you choose.** A share card goes where you send it through the
share sheet. Nothing else.

**Permissions.** Notifications (reset alerts), Live Activities, and background app refresh. No
location, contacts, camera, photos or advertising identifier. The app declares no tracking.

**Deleting your data.** *Remove* in Settings deletes an account's sign-in, cached usage, history and
settings from the device. Deleting the app removes everything. Removing an account does not end the
session on the service's side; sign out of your sessions in your Claude, ChatGPT or Google account
settings to revoke it.

**Apple's App Privacy answers** (App Store Connect → App Privacy): *Data Not Collected*; no tracking.
