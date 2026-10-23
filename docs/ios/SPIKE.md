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
| App Group container shared by app and widget | pass (simulator does not enforce entitlements) | pending |
| Shared Keychain group | pass (simulator, placeholder team) | pending |
| Home-screen widget renders the app's data | not checked | pending |
| Lock-screen widgets (circular, rectangular) | not checked | pending |
| Live Activities enabled / an activity starts | `areActivitiesEnabled` true on the iPad simulator | pending |
| Control Center control | not checked | pending |
| Development IPA exports from an archive | — | pass: `xcodebuild archive` + `-exportArchive` method `debugging`, 131 KB IPA in `ios/out/ipa/` (gitignored) |
