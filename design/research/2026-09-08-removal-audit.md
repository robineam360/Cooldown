# Removal audit — widgets, standalone alerts, retired notification styles (2026-09-08)

Read-only audit run ahead of the proposed CCRM-61 (Settings Diet) and CCRM-62 (Duet
Notification), so the build sessions know what each removal actually touches. Condensed from a
sub-agent's `grep -n` sweep at HEAD (uncommitted CCRM-55 (Antigravity Account) drop edits in
`SettingsScreen.kt` and `ContractCopyTest.kt` were in the tree). Line numbers drift after the
first edit; use them as anchors, not gospel.

## Totals

| Area | Deleted (lines) | Edited (lines touched) |
|---|---|---|
| 1 · Widgets | ≈ 3 400 | ≈ 320 |
| 2 · Standalone alerts | ≈ 1 900 (≈ 2 300 if the fold store goes too) | ≈ 700 |
| 3 · Retired pinned styles + glyphs | ≈ 200 | ≈ 110 |

Highest tracker IDs at audit time: CCRM-59, CCBG-20. Next free: CCRM-60 (claimed by the
`design/dual-identity-wireframe.html` proposal, to be filed), CCBG-21.

## 1 · Widgets

**Delete outright:** the whole `widget/` package (9 files, 2 573 lines), `ui/RingRenderer.kt`
(only `widget/FaceBits.kt` and the debug faces harness use it), `app/src/debug/**`
(`DebugFacesActivity` + debug manifest), the five `res/xml/*_widget_info.xml`, both tests under
`app/src/test/.../widget/` (`WidgetFaceTest`, `AbsentWindowTest`).

**Must stay (looks widget-shaped, is not):** `ui/BarRenderer.kt` (pinned notification bar),
`ui/BarGeometry.kt` (in-app + panel bars), `ui/RingGeometry.kt` (status-bar ring glyph via
`UsageIcon`), `ui/Sparkline.kt` (in-app chart). `ui/BarRenderer.kt`'s KDoc leads with "Glance
widget faces" but the pinned notification calls it.

**Edit:** `AndroidManifest.xml` (config activity + five receivers, 60 lines);
`res/values/strings.xml` (8 widget strings; only `app_name` survives); `app/build.gradle.kts`
(both `androidx.glance` lines); `work/Polling.kt` (`WidgetRedrawWorker`, redraw scheduling,
`armResetRedraw`); `MainActivity.kt` (`refreshWidgets` lambda, five `updateAll` calls, imports,
and the prose at ~line 404 claiming "both-at-once already has a better home … the widgets");
`data/UsageRepository.kt` (`updateWidgets()` + seven call sites, `WidgetPrefs.repointFrom`);
`data/UsageCache.kt` (`creditsOnWidgets`, `paceOverOnWidgets`, the whole `WidgetPrefs` class
backed by the separate `widget_prefs` file); `SettingsScreen.kt` (`refreshWidgets` parameter
threaded through nine unrelated controls, "Show on widgets", "On widgets", the Widgets section).

**Risk — theme/time chips only refresh via `refreshWidgets()`** at two sites. Removing the
lambda there must add `PinnedNotification.update`, or CCBG-14 (Stale Notification Theme)
reopens.

**Docs:** README (title line, features, two screenshot cells, install step 3, stack line,
changelog), `release/USER-GUIDE.md` (title says "Android Widget", section 197–210), guide.html
(whole "PAGE 7 · WIDGETS"), brochure.html, hero.html (embeds a widget shot; re-shoot `hero.png`),
five `release/screenshots/widget-*.png` and their `release/docs/src/shots/` mirrors,
`CLAUDE.md` working agreement 2 ("both widget providers"), `.github/CONTRIBUTING.md:24`.

**Tracker:** CCRM-39 (Ring Widget), CCRM-40 (Mini-Rings Widget), CCRM-41 (Pace Widget), CCRM-4
(Widget Quick-Edit), CCRM-13 (Chart Widget) → Dropped/removed. CCRM-3 (Unified Theming) phase 2
moot. CCRM-43 (Bar Pace Marks) loses its widget arm. CCBG-19 (Fixture Unreachable) → superseded
(the harness goes). CCBG-11 (Ring Face Clutter), CCBG-10 (Mini-Rings Emptiness) → superseded.
CCBG-3 (Credits Visibility) re-scoped to the in-app card. CCRM-18 (Mac Desktop Widget) is the
Mac repo, unaffected.

## 2 · Standalone alerts

**Central finding: `alerts/Alerts.kt` is an edit down to ≈ 130 lines, not a delete.** Four
things ride inside it that survive:

1. `Alerts.evaluate` ends with `PinnedNotification.update` and is the *only* per-poll re-render
   of the pinned notification (five callers in `UsageRepository`). Cut it and the pinned panel
   freezes.
2. `checkReset` writes `SessionLog` on rollover and maintains `windowPeak` /
   `lastSeenWindowKey`. It is the History screen's data source. Only its `resetPingMode` gate
   and notify/fold branch go.
3. `Alerts.STALE_DATA_MS` feeds the surviving stale strip in `notify/Conditions.kt`.
4. `notifId` / `MAX_KIND` / `cancelAllFor` are used by `removeProfile` and asserted by
   `ProfileRegistryTest`. `MAX_KIND` is anchored to the pace kind and must be re-anchored.

**Delete outright:** `notify/UpdateSkipReceiver.kt`, all of `ping/` (three files),
`data/PingSchedule.kt` + `PingScheduleTest.kt`, `res/layout/notif_alert*.xml`.

**Edit:** `notify/UpdateNotification.kt` keeps `autoCheck` (sole writer of
`latestKnownVersion`, which the update strip reads) and `installedVersion`; `maybePost` and the
skip action go. `AndroidManifest.xml`: three receivers, the exact-alarm and boot permissions
(safe: `PinnedNotification.armExpiry` is inexact). `UsageCache.kt`: ≈ 310 lines of alert,
threshold, pace, ping and dedup prefs; `LEGACY_PROFILE_KEYS` and `clearProfile` need matching
surgery. `SettingsScreen.kt`: the Notifications section (130 lines) plus `ThresholdChipsRow`,
`AlertLifetimeRow`, `ResetModeRow`, the dormant `WindowPingsSection` and its helpers (≈ 413
lines). `UsageRepository.kt`: `PingResult`, `VerifyResult`, `sendWindowPing`,
`verifyWindowPing`. `ApiClient.sendPing`. `UpdateGate.shouldNotify`.

**Reset pings survive (review 2026-09-09).** The reset alert is the one standalone notification
kept, per account: `checkReset`'s notify branch and the `reset_alerts` channel stay, `resetPingMode`
becomes a per-account pref, and the ping posts even while the always-on notification is on. Its
fold branch still goes with the fold machinery below.

**Condition strips survive** (derived live, not posted): re-auth, stale, expiry, update
available. **Folded event strips die** (threshold, pace, reset) because `Alerts.foldEvent` is
their only writer. That leaves ≈ 400 lines of fold machinery dead if kept: the `FoldedEvent`
store in `UsageCache`, `notify/StripRules.kt`, `Conditions.foldedInto/revocable/nextExpiry`,
`PinnedNotification.armExpiry`, the `ACTION_EXPIRE` arm of `PinnedRefreshReceiver`,
`StripRulesTest`. **Decision (2026-09-08): delete the fold machinery too.** CCBG-16/17/18 fixes
were never device-verified and become moot.

**Two prefs read in two places:** `authAlertsEnabled` also gates the surviving re-auth strip →
make the strip unconditional and drop the pref. `healthAlertsEnabled` was never consulted by the
stale strip → drop with its toggle. `Projection`'s pace ladder (`paceSeverity`, `paceSatisfied`,
`paceStep`, `PaceMilestone`) has no consumer once `checkPace` goes → delete with `PaceTest` and
the matching `ProjectionTest` cases; the in-app pace sentence uses `Projection.estimate`, which
stays.

**Orphaned channels:** deleting `ensureChannels` leaves six channels (`reset_alerts` stays) visible in system
settings on upgraded installs. Call `deleteNotificationChannel` for each once at startup.
`pinned_usage_v2` is untouched. No test asserts a channel name.

**Docs:** README features + changelog, `release/USER-GUIDE.md` section "4 · Usage alerts"
(213–240), guide.html "PAGE 8 · ALERTS + TILES", brochure/hero chips, screenshots
`settings-alerts.png`, `notifications.png`, `check-updates-dark.png`.

**Tracker:** CCRM-21 (Pace Alerts), CCRM-17 (Window Pings) → Dropped/removed. CCRM-44 (One
Surface) → superseded (condition strips survive). CCRM-28 (Auto Update Check) → partially
superseded. CCRM-5 (Per-Profile Notification) → re-scoped by CCRM-62 (Duet Notification).
CCBG-4 (Alert Dedup), CCBG-5 (Ping Verification), CCBG-12 (Status Icon Swap), CCBG-16/17/18 →
superseded.

## 3 · Retired pinned styles and status-bar glyphs

No file deletes. `notify/PinnedNotification.kt`: delete the `when (style)` wholesale (not arm by
arm: the `else` arm is *gauge*, so pruning arms strands `"gauge"` installs with no big style),
`bigPicture`, `drawNumberTile`, `drawGauge`; `drawStatusIcon` loses its style parameter (≈ 134
lines). `ui/UsageIcon.kt`: the battery and number arms, every `pie` branch in `railsGauge`,
`clearCircle`, the PIE constants (≈ 74 lines); keep `RIM`, used by the ring. `UsageCache.kt`:
`pinnedStyle` and `pinnedIconStyle` (24 lines). `SettingsScreen.kt`: the Notification style
block and the Status-bar icon block (≈ 76 lines; rewrite the ring explainer to one sentence).
`tile/UsageTileService.kt`: drop the `pinnedIconStyle()` read at ~line 111; the tile always
takes the monochrome path. No test touches any of this.

**Docs:** USER-GUIDE and guide.html name the four styles and the four glyphs in several places;
`shots/v11-notif-styles.png` dies; `pinned-collapsed.png` / `pinned-expanded.png` may show the
Gauge style and need re-shooting. `MAC-GAUGE-HANDOVER.md` is a historical cross-repo record;
annotate, do not edit.

**Tracker:** CCRM-3 (Unified Theming) phase 1 superseded. CCRM-48 (Status-Bar Gauge), CCRM-49
(Glyph Legibility), CCRM-51 (Rails Gauge) partially superseded (pie half dies unverified).
CCRM-2 (Notification Tap Target), CCRM-11 (Tile Reset Time) survive. CCRM-50 (Weekly Flag) →
superseded 2026-09-09: the status-bar ring loses its weekly hub dot and gains a First / Second /
higher-of-two account picker.
CCBG-20 (Pinned Identity Loss) → resolved by removal (it is the Progress-bar style's bug).
CCBG-15 (Amber Ladder Blindness) stays Open.

## Cross-cutting

- `ContractCopyTest` slices `SettingsScreen.kt` as raw text between `"Unofficial.` and the next
  `labelSmall`; large deletions above the disclaimer are safe, anything between the anchors is
  not.
- Android Auto Backup carries every orphaned pref to new devices; harmless, but `clearProfile`
  must not try to clean keys that no longer exist.
- `RUNBOOK.md` Step 5 bullets (Huge number first, the Gauge/Number-tile findings, the skipped
  widget row) become superseded once built; mark them, do not delete.
