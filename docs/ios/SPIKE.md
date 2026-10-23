# CCRM-93 (iOS Spike) — results

Plan: [PLAN.md](PLAN.md) step 2. Signing: the Sedin Technologies (EAM360) team through Robin's own Apple ID (Admin).

## Company account, before the first signed build (Robin, from Certificates, Identifiers & Profiles)
| Resource | Existed before the spike? |
|---|---|
| iPad Pro M4 (UDID `00008132-001C398E14D9001C`) as a registered device | pending |
| App ids `com.robinrichardrajan.cooldown`, `com.robinrichardrajan.cooldown.widgets` | pending |
| App Group `group.com.robinrichardrajan.cooldown` | pending |

## Company account, created by the spike
Filled in after the first signed build. Cleanup deletes only these rows.

## Results
| Fact | Simulator (23 Oct) | iPad Pro M4, iPadOS 27.0.1 |
|---|---|---|
| App Group container shared by app and widget | pass (simulator does not enforce entitlements) | pending |
| Shared Keychain group | pass (simulator, placeholder team) | pending |
| Home-screen widget renders the app's data | not checked | pending |
| Lock-screen widgets (circular, rectangular) | not checked | pending |
| Live Activities enabled / an activity starts | `areActivitiesEnabled` true on the iPad simulator | pending |
| Control Center control | not checked | pending |
| Development IPA exports from an archive | — | pending |
