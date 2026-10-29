# CCRM-93 (iOS Spike) — results

Plan: [PLAN.md](PLAN.md) step 2. Signing: the Sedin Technologies (EAM360) team through Robin's own Apple ID (Admin).

## Company account, before the first signed build (23 Oct: Robin checked; the seat re-read the Devices, App IDs and App Groups lists in the browser)
| Resource | Existed before the spike? |
|---|---|
| iPad Pro M4 (UDID `00008132-001C398E14D9001C`) as a registered device | no |
| App ids `com.robinrichardrajan.cooldown`, `com.robinrichardrajan.cooldown.widgets` | no |
| App Group `group.com.robinrichardrajan.cooldown` | no |

## Company account, created by the spike
23 Oct, confirmed in the portal. Cleanup deletes only these rows.
| Resource | Created by |
|---|---|
| Device "iPad Pro M4" (`00008132-001C398E14D9001C`), registered 2026/10/23 | `xcodebuild -allowProvisioningDeviceRegistration` |
| App ids `com.robinrichardrajan.cooldown`, `com.robinrichardrajan.cooldown.widgets` | `xcodebuild -allowProvisioningUpdates` |
| App Group `group.com.robinrichardrajan.cooldown` (enabled on both ids) | Xcode's Signing & Capabilities tab |
| Apple Development certificate "Robin Richard Rajan", expires 2027/10/23 | `xcodebuild` (key in this Mac's login keychain) |
| Xcode-managed development profiles for both ids | `xcodebuild` |

Lessons: a command-line build creates app ids but cannot register an App Group; the one-time fix is opening the
target's Signing & Capabilities tab in Xcode. Xcode then showed a stale "Revoke certificate" status although the
portal held exactly one development certificate whose key is on this Mac; it must **not** be revoked. The team's
Apple Distribution certificate needed Apple's WWDR G3 intermediate to show as valid.

## Results
| Fact | Simulator (23 Oct) | iPad Pro M4, iPadOS 27.0.1 |
|---|---|---|
| Signed install and launch | — | pass: app id `546C62BX57.com.robinrichardrajan.cooldown`, App Group and Keychain group entitlements present; installed and launched with devicectl, no trust prompt needed on a paid team |
| App Group container shared by app and widget | pass (simulator does not enforce entitlements) | **pass** — the widget shows the file and defaults the app wrote at 2:09:13 PM |
| Shared Keychain group | pass (simulator, placeholder team) | **pass** — the widget reads the app's Keychain item (`token 2:09:13 PM`) |
| Home-screen widget renders the app's data | not checked | **pass** — small and medium; timeline refreshed on its own (2:13 → 2:15 PM) |
| Lock-screen widgets (circular, rectangular) | not checked | **pass** — circular ✓ and rectangular "group ✓ · key ✓"; iPadOS 27 also placed the medium widget in the lock-screen column |
| Live Activities enabled / an activity starts | `areActivitiesEnabled` true on the iPad simulator | **pass** — enabled, `Activity.request` started one, and the lock screen shows "Spike · 5h 42%" with a live countdown (1:57:41 → 1:56:52). Live Activities are **not iPhone-only** |
| Control Center control | not checked | **pass** (placement) — "Cooldown" hourglass control added to Control Center; the tap-to-open was not separately reported |
| Development IPA exports from an archive | — | pass: `xcodebuild archive` + `-exportArchive` method `debugging`, 131 KB IPA in `ios/out/ipa/` (gitignored) |

## Verdict (23 Oct 2026)
**Pass.** Every surface in the plan works on the iPad Pro M4 under the Sedin team's development signing. Change to
the plan: the Live Activity is an iPad surface too, so it is no longer "built last, iPhone only". Evidence: Robin's
seven iPad screenshots in this session (not committed: they show his personal home screen).
