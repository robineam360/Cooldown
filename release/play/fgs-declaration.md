# Play Console — foreground service declaration (`specialUse`)

Drafted 2026-10-01 for RUNBOOK Step 6, CCRM-90 (Listing Pack). It is pasted into **App content →
Foreground service permissions** when the AAB is first uploaded (RUNBOOK Step 9). Seeded from
`AndroidManifest.xml`. The decision and its risk are in CCRM-67 (Pin Service).

## What the manifest declares

- `android.permission.FOREGROUND_SERVICE` and `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`.
- One service, `.notify.PinnedService`, `android:foregroundServiceType="specialUse"`, not exported.
- `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`: **"Persistent usage-window readout the user turned on in
  Settings"**.

## The declaration text

**Which feature uses the service?**

```text
The always-on usage notification. When the user switches on Settings → Alerts → Always-on
notification, Cooldown keeps one notification in the shade and a ring in the status bar that show
how much of their Claude or ChatGPT usage window (5-hour and weekly) they have used, and when it
resets. The service runs only while that switch is on and stops as soon as the user turns it off.
```

**What does the user experience if the task is deferred or interrupted?**

```text
The readout goes stale or disappears. The user relies on it to see, at a glance and without
opening the app, whether they are about to hit their limit in the middle of their work. A stale
figure is worse than none, because it tells them they have room when they do not. Before this was a
foreground service, Android ranked the notification below silent ones and stopped refreshing it
when the app was in the background, which is the bug this fixed.
```

**Why do the other foreground service types not fit?**

```text
None of the typed categories describe a user-enabled, persistent status readout. dataSync is
capped at 6 hours in 24 on Android 15 and later, after which the system stops the service, so the
readout would die every day until the user reopened the app. The service does no media, location,
health, connected-device, phone-call or camera work. Each refresh is one small HTTPS request to the
user's own provider, on the polling interval the user sets (15 minutes by default, 5 at the least).
```

**Video:** a short screen recording on the Fold 7 showing the switch turned on in Settings, the
notification and the status-bar ring appearing, a refresh, and the switch turned off with the
notification gone. It is recorded in RUNBOOK Step 7 and uploaded unlisted. Its link goes here:
`<video URL — Step 7>`.

## If review pushes back

CCRM-67's recorded fallback: move to `dataSync` with a watchdog that restarts the service when the
6-hour cap stops it. That is a code change and a new release, not a form edit, and it would be
planned as its own item.
