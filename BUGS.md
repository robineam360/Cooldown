# Bugs

Tracked defects for Claude Cooldown. Each has a stable ID (`CCBG-N`) — use it in
commits. IDs never change or get reused; only status moves. Feature work lives in
[ROADMAP.md](ROADMAP.md) (`CCRM-N`).

**Status legend:** `Open` · `In progress` · `Fixed` · `Won't fix`
**Severity:** `High` (data loss / crash / wrong numbers) · `Medium` · `Low`

---

## Open

### CCBG-27 · Free Plan 403 — a Free Claude account reads as "Anthropic's server errored"
- **Status:** Fixed (2026-09-11) — built, unit-tested (`ClaudePlanTest`, 5 cases; `ErrorKindTest`
  extended; 313 green) and **seen on the Fold 7 the same day**: the Free card wears a red "Free"
  chip and the line "Claude doesn't report usage for the Free plan — upgrade to Pro, Max or Team to
  see numbers here."; the Free tab shows no cards, the notice *"Claude doesn't report usage on this
  plan — it needs a paid plan (Pro, Max or Team). HTTP 403 · free plan"* with **See Claude plans**;
  the expanded notification carries the red `Free: No usage on the Free plan` strip at full width
  (`design/research/2026-09-11-free-plan/`). Seen along the way: Anthropic answered the account's
  usage polls with **429** for ten minutes after the first 403, which is why the poll gate keys on
  the stored plan and not only on the last failure kind.
- **Severity:** Medium (wrong diagnosis on the main screen — "usually theirs, usually brief" for a
  permanent condition — and yesterday's numbers drawn crisply as if live; also the state every
  new Free sign-in lands in)
- **Symptom:** **Reported by Robin, 2026-09-11.** His personal Pro plan lapsed to Free (the login
  is fine). Every poll since reads `Error: HTTP 403` in Diagnostics; the Free tab kept showing the
  last Pro reading (38% / 88%) with "Resets any moment", and the error notice said *"Anthropic's
  server errored — usually theirs, usually brief. HTTP 403 · Check Anthropic status"*.
- **Cause, confirmed by probe:** `/api/oauth/usage` answers **403** for a Free organisation with
  `{"error":{"type":"permission_error","message":"OAuth authentication is currently not allowed for
  this organization.","details":{"error_code":"oauth_not_allowed_for_organization"}}}`, while
  `/api/oauth/profile` answers 200 with `organization.organization_type: "claude_free"`,
  `subscription_status: "canceled"`. `ClaudeSource.isAuthFailure` is 401-only (right — the token is
  valid), so the 403 fell through to the generic `HTTP 403` → `ErrorKind.SERVER` branch. The retained
  last-good snapshot, correct for every transient failure, is wrong here: an account with no windows
  has no number to be stale *from*.
- **Fixed by:** a new `ErrorKind.PLAN` (severe; title "Claude doesn't report usage on this plan — it
  needs a paid plan (Pro, Max or Team)", short "no usage on this plan"). `doFetch`: a Claude 403 whose
  body carries `oauth_not_allowed_for_organization` — or whose profile reads Free — reads the profile
  to name the plan, **clears the cached usage** (`UsageCache.clearUsage`, history untouched), stores
  `HTTP 403 · free plan` with kind PLAN, and returns "Signed in, but Claude doesn't report usage on the
  Free plan — Pro, Max or Team is needed." (which is also what a fresh Free sign-in shows on its card).
  While an account is in that state polls read the **profile instead of usage** — no 403/429 churn,
  and an upgrade is noticed the moment the profile stops saying Free. Surfaces: the main-screen notice
  offers "See Claude plans" (claude.ai/upgrade) instead of the status page and drops "try Refresh now";
  the account card says it under the chip; the notification carries a red `No usage on the Free plan`
  strip in place of the stale one (`Conditions.plan`), and the half's dot keys on it.
- **Where:** `data/ErrorKind.kt`, `data/source/ClaudePlan.kt` (new, with `isPlanRefusal`),
  `data/UsageRepository.kt` (`doFetch`, `refreshClaudePlan`), `data/UsageCache.kt` (`clearUsage`,
  `planCheckedAt`), `data/QuickLinks.kt` (`plansUrl`), `notify/Conditions.kt`, `MainActivity.kt`
  (`ErrorNotice`), `SettingsScreen.kt` (account card). Probe captures (they carry the account e-mail,
  so **not** committed): scratchpad only; the anonymised shapes are in `ClaudePlanTest`.

### CCBG-26 · Panel Scaling — a condition strip shrinks the whole expanded Duet panel, Weekly meters included
- **Status:** Fixed (2026-09-10) — option A of `design/duet-panel-fit-wireframe.html`, approved by
  Robin the same day: the panel is native RemoteViews rows (`notif_panel_duet.xml` /
  `notif_panel_single.xml`, filled by `fillPanel`), so it can only ever be full width; on the Duet a
  strip is one line of its `short` text, the cap is 1 with a "+ n more" tail, and model caps leave the
  Duet panel. `DuetTest` updated (308 green) · **verified on the Fold 7, 2026-09-11**: with the
  `Free: Stale — nothing since 4:39 pm` strip up, the strip and both Weekly rows drew at the header
  bars' full width (`design/research/2026-09-11-free-plan/notif-strip-full-width.png`).
- **Severity:** Medium (the Weekly meters — the panel's reason to exist — become ~60% size and
  narrower than the header bars whenever a strip is up; it is also the state in the README's,
  the User Guide's and the brochure's expanded-notification screenshot)
- **Symptom:** **Reported by Robin, 2026-09-10**, from the shipped v1.6 screenshot
  `release/docs/src/shots/v16-notif-expanded.png`: with two strips up (Product: Sign-in stopped
  working · Product: Usage data is stale) the strips and both Weekly rows are drawn at about 85%
  of the header bars' width, with correspondingly smaller text. Without a strip the same rows are
  full width. The device pass already had it unnoticed: `notif-5-second-signin-expanded.png`
  (one strip, three rows) is at 0.89×.
- **Cause:** `drawPanel` renders the strips and rows as one bitmap at a nominal 340 dp width and
  whatever height the content needs; `notif_duet_expanded.xml` shows it in an `ImageView` with
  `adjustViewBounds` + `fitStart`. Android caps the expanded custom view, so when the bitmap is
  taller than the room left under the two header blocks (~136 dp on the Fold 7, ~120 on stock)
  the `ImageView` scales it down uniformly to fit the height and the width follows. Two things
  compound it: the CCRM-62 (Duet Notification) height table priced a strip at 44–59 dp (title
  only / one-line sub) but `drawPanel` wraps the detail to two lines, so a real strip is 75 dp;
  and the bar budget (`4 − strips`) still draws both Weekly rows beside two strips although the
  same table said "at 2 strips the panel is full, no bar fits". Nothing measures the height, so
  nothing gives way. The single layout (`notif_big_number_expanded.xml`) has the same bitmap and
  the same `fitStart`, and overflows at two two-line strips plus its 71 dp Weekly row.
- **Where:** `notify/PinnedNotification.kt` (`drawPanel`, `PANEL_WIDTH_DP`, the Duet
  `barBudget`), `res/layout/notif_duet_expanded.xml` and `notif_big_number_expanded.xml` (the
  panel `ImageView`), `notify/Duet.kt` (`maxStrips`), `notify/Conditions.kt` (`MAX_STRIPS`).
- **Fix, proposed:** render the panel as native RemoteViews rows (TextViews at true sp, bar
  bitmaps in `fitXY` ImageViews — the technique the header blocks already use) so it can only
  ever be full width, and cut content to a fixed ~120 dp budget instead of scaling. What is cut
  is the decision the wireframe asks: option A keeps both Weekly rows and reduces a Duet strip to
  one line using its existing `short` text (cap 1, "+ n more" inline); option B keeps the
  two-line strip and drops Weekly rows. Model caps leave the Duet panel under either. Visible
  change to an approved layout, so it waits for the wireframe's approval.

### CCBG-24 · Duet Label Clamp — a seven-character label ellipsizes beside a three-character figure
- **Status:** Open (2026-09-10)
- **Severity:** Low (the mark and the accent still identify the provider; the label only has to
  tell two accounts apart)
- **Symptom:** **Observed on the Fold 7, 2026-09-10**, during the CCRM-60/61/62 device pass
  (RUNBOOK.md Step 5), on the CCRM-62 (Duet Notification) collapsed row. With the figure at
  `2%` the first half reads `ChatGPT 2%` in full. Switch Usage display to Left and the same
  half reads `ChatG… 98%`: the label lost two characters to a one-character-wider figure. The
  wireframe's state 1 shows `ChatGPT 63%` fitting, so a three-character figure was meant to
  leave the 72 dp clamp intact. Capture: `design/research/2026-09-10-device-pass/notif-7-left-collapsed.png`.
- **Cause, probable:** `Duet.labelClampDp` only steps down (72 → 56 dp) at four characters;
  the row itself is `wrap_content` figure trailing a `0dp`-weight label, so on this device the
  30 sp bold `98%` in Samsung's font is wider than the ~50 dp the arithmetic in `Duet.kt`
  allows for, and the label column absorbs the difference and ellipsizes early.
- **Where:** `notify/Duet.kt` (`labelClampDp` and the width comment above it),
  `res/layout/notif_duet.xml` (the half's label / figure widths).
- **Fix options:** measure the figure with `Paint.measureText` at 30 sp and clamp the label to
  whatever is left, instead of a two-step table; or drop the figure to 28 sp for three
  characters. Either is a visible change to an approved layout, so the fix goes through the
  Duet wireframe first.

### CCBG-22 · Credits Rows For All — "Usage credits" lists a toggle for every account, not only those with credits
- **Status:** Open (2026-09-10)
- **Severity:** Low (a row too many; nothing wrong is shown on a card)
- **Symptom:** **Observed on the Fold 7, 2026-09-10**, during the Step 5 device pass, Settings →
  More. The Usage credits card's own explainer says the section "only appears for accounts that
  actually have a credit budget", yet it lists **Show for Pro, Teams, Product and ChatGPT** — all
  four accounts, including the ChatGPT Plus account, which has no credit budget. The
  settings-diet wireframe draws one row, "Show for Personal", with the note "(only if an account
  reports credits)". Capture: `design/research/2026-09-10-device-pass/settings-more-410.png`.
- **Cause:** `SettingsScreen.kt` gates the **section** on
  `profiles.any { credits?.isReportable == true }` and then loops `for (profile in profiles)`
  regardless. CCRM-61 (Settings Diet) Step 2 moved the card into the More tab and made the
  section conditional but kept the unconditional loop.
- **Where:** `SettingsScreen.kt`, the `showCredits` / `pollingUpdatesCredits` block (≈ lines
  669–705).
- **Fix:** filter the loop to profiles whose snapshot reports credits; when that leaves one
  account, the row still needs its label (two Claude plans can both report credits). Copy is
  already right; no wireframe needed.

### CCBG-21 · Zero-Point Shading — a 5-hour chart at 0% paints the whole above-pace region red
- **Status:** Open (2026-09-10)
- **Severity:** Low (a wash, not a wrong number; but it is the exact state the code comment says
  the wash must not appear in)
- **Symptom:** **Observed on the Fold 7, 2026-09-10**, during the Step 5 device pass, on the
  ChatGPT tab (cover and inner screen). The 5-hour window at **0%** with a single sample at the
  start of the window draws the full triangle above the even-pace line in the red wash, with the
  caption reading "5 points below even pace" and later "On even pace". The Pro tab's 5-hour chart
  at 11% below pace shows no wash. Robin asked for it filed. Captures:
  `design/research/2026-09-10-device-pass/app-2b-chatgpt-room-dark-410.png` and
  `app-2-chatgpt-room-dark-750.png`.
- **Cause, probable:** `Sparkline.kt` draws the wash when `atOrAbovePace`; at the first sample
  of a fresh window the observed 0% equals the even-pace 0%, so "at pace" is true and the wash
  arrives on the safest state there is — the case the comment above the `if` says it was made
  to avoid. Pre-dates this arc (CCRM-43 (Bar Pace Marks) / CCRM-20 (Wide Chart) era); the
  ChatGPT account's fresh window made it visible.
- **Where:** `ui/Sparkline.kt`, the `abovePace` / `atOrAbovePace` block (≈ lines 311–324) and
  wherever `atOrAbovePace` is computed.
- **Fix:** require strictly above pace, or above pace by ≥ 1 point, or a non-zero observed
  value, before drawing the wash. Pure logic; restores the approved design, so no wireframe.

### CCBG-15 · Amber Ladder Blindness — the Amber theme's accent is the yellow warning rung
- **Status:** Open
- **Severity:** Low (one theme, and the orange/red rungs still land)
- **Symptom:** Noticed 2026-08-21 during CCRM-50 (Weekly Flag) design. The Amber
  theme's accent pair (#F9A825 / #FDD663) is **identical** to `Palette.barColor`'s
  >80 yellow rung, so for an Amber user the first ladder step is invisible on every
  coloured surface — a bar or ring at 46% and at 84% are the same colour. The >90
  orange and ≥100 red rungs still read.
- **Where:** `Palette.barColor` vs the `Amber` entry in `Palette.options` — palette-wide,
  affecting the in-app bars, the widgets, the notification gauge and the status-bar glyph.
- **Fix options, undecided:** shift Amber's accent off the rung colour (visible change
  for Amber users, needs a wireframe per working agreement 2), or give the yellow rung a
  distinguishing shape on Amber only (inconsistent), or document it as a known trait of
  choosing Amber. Not a rider on any current work.

### CCBG-3 · Credits Visibility — credits card ignores extra-usage being switched off
- **2026-09-09:** Re-scoped to the in-app credits card — the widget credits bar is removed by CCRM-61 (Settings Diet).
- **Status:** Open
- **Severity:** Low (misleading display, no data loss) — and possibly unreachable
- **Symptom:** Suspected, not observed. The credits card renders whenever
  `limit > 0`, so an account that has *switched extra usage off* while keeping a
  non-zero monthly limit would still be shown a credits bar as though it were live.
- **Detail:** the payload carries `spend.enabled` and `extra_usage.is_enabled`, and
  `UsageParser` reads neither — see `creditsFrom()` in
  [Models.kt](app/src/main/java/com/robin/claudeusage/data/Models.kt). The render gate is
  the `limitMinor > 0` check in `MainActivity`, `UsageWidget` and `BarWidget`.
- **Why it's still open rather than fixed:** both flags read `true` on every account we
  can see, so the disabled-with-a-limit state has never been observed. Gating on a flag
  whose behaviour we can't verify risks hiding the card from people who should see it —
  the worse failure. Fix it when someone can actually produce the state.
- **Fix when confirmed:** treat `enabled == false` as "no credits" in `SpendCredits`, and
  add a `UsageParserTest` case with the real payload for that state.

---

## Fixed

### CCBG-20 · Pinned Identity Loss — the Progress-bar style names no account when collapsed
- **Status:** Fixed by removal (2026-09-09) — CCRM-61 (Settings Diet) deleted the Progress-bar style; Huge number is the only style.
- **Severity:** Medium (the collapsed row is the always-visible one, and with four accounts
  across two providers it does not say which account it is reporting)
- **Symptom:** **Observed on the Fold 7, 2026-09-06**, during the CCRM-56 (Provider Identity)
  device pass, with the pinned notification pointed at the ChatGPT account. Under the
  **Progress bar** style the collapsed row reads only `9% · resets in 6m` — neither the
  account label nor the window name appears. The other three styles all lead with
  `ChatGPT · 5-hour window`. Expanding the notification recovers the identity, so the loss is
  collapsed-only.
- **Cause:** in `PinnedNotification`, `title` is `"$pctShort · $resetShort"` for this style
  alone, because `setProgress()` occupies a row and the shade drops the content-text line
  when collapsed — so the title is made to carry the number *and* the reset. The identity is
  then put on `baseText` (`"$label · $headlineName"`), which is exactly the line the same
  comment acknowledges the shade drops. The identity therefore has no collapsed slot at all.
  A second path makes it worse: `collapsedText` gives its one line to the highest-priority
  strip when one exists and only falls back to `baseText` otherwise, so even where the line
  does survive, any live alert displaces the identity.
- **Why it did not show before:** with a single Claude account `9% · resets in 6m` was
  unambiguous. It became a defect when accounts multiplied (CCRM-6 (Multi-Account)) and again
  when providers did (CCRM-53 (Provider Model)). Pre-dates this arc; the provider-mark
  question in CCRM-54 (ChatGPT Account) surfaced it.
- **Where:** `notify/PinnedNotification.kt` — the `title` / `baseText` / `collapsedText` block
  (the `progress` branch), and the `"progress"` arm of the style `when`.
- **Fix options, undecided:** shorten the title to `"$label · $pctShort"` and let the reset go
  to the dropped line (the reset is also in the expanded panel); or give this style a custom
  RemoteViews row like `big` has, which would also give it the provider mark and close the
  build note in CCRM-54's status at the same time. Either is a visible change and needs a
  wireframe per working agreement 2.

### CCBG-19 · Fixture Unreachable — the debug faces activity cannot coexist with the install it must be compared against
- **Status:** Won't fix (2026-09-09) — superseded: CCRM-61 (Settings Diet) deleted the debug faces harness with the widgets.
- **Severity:** Medium (no wrong number ships, but it guarantees a class of visual state
  is never observed — the exact failure CCRM-15 (Above-Pace Verification) exists to remember)
- **Symptom:** **Found during the CCRM-56 (Provider Identity) device pass, 2026-09-06.**
  CCRM-56's own device-pass bullet asks for "a Claude account with the 7-day card hidden
  (simulate with a fixture in the debug faces activity)", and CCRM-54 (ChatGPT Account) asks
  for "a Ring widget on the absent window". Both are reachable only through
  `DebugFacesActivity`. RUNBOOK.md Step 5 mandates the opposite build: release-signed,
  installed **over the live install**. The two cannot both be satisfied on one phone, so
  both states went unobserved.
- **Cause:** `DebugFacesActivity` lives in the `debug` source set
  (`app/src/debug/java/com/robin/claudeusage/debug/DebugFacesActivity.kt`) and
  `app/build.gradle.kts` declares **no `applicationIdSuffix`** for the debug build type —
  its `buildTypes` block configures `release` only. Debug and release therefore share
  `applicationId com.robin.claudeusage` while being signed by different keys, so installing
  one uninstalls the other. On this device that would destroy four live accounts, their
  tokens, a year of history, the placed tiles and the widget bindings.
- **Second half:** the absent-window states are not reachable from live data either. The
  sentence only renders when a payload arrived and omitted that window
  (`absentWindowMessage`, `WidgetFace.kt:166`), and all four accounts on the device — the
  three Claude ones and ChatGPT `p5` — report both windows. The fixture is the only path.
- **Where:** `app/build.gradle.kts` `buildTypes` (no debug suffix);
  `app/src/debug/AndroidManifest.xml`; the device-pass bullets of CCRM-56 (Provider
  Identity) and CCRM-54 (ChatGPT Account).
- **Fix options, undecided:** add `debug { applicationIdSuffix = ".debug" }` so the harness
  installs alongside the real app (widget/tile component names shift for debug only; the
  release `applicationId` is untouched, so CLAUDE.md's package rule is not engaged) — or
  move the faces harness behind a hidden gesture in the release build, or accept a
  second device. Until one lands, every widget face state that needs a fixture is
  permanently unobservable on the phone the app actually runs on.

### CCBG-23 · Mark Size Mismatch — the Claude mark renders larger than the ChatGPT mark
- **Status:** Fixed (2026-09-10) — scaled the ChatGPT blossom 1.35x about its own centre inside
  the shared 24x24 viewport so its ink extent optically matches the Claude sunburst's.
- **Severity:** Low (cosmetic, but it is on every surface: top bar, tab strip, Settings chips,
  notification halves and headers)
- **Symptom:** **Noticed by Robin on the Fold 7, 2026-09-10**, during the Step 5 device pass,
  first on the pinned notification and then everywhere the two `ProviderMark`s sit side by side:
  the Claude sunburst reads visibly bigger than the OpenAI blossom at the same nominal size.
  `uiautomator` confirms both marks occupy identical 53 px boxes (20 dp) in the top bar, so the
  difference is inside the vectors, not the layout.
- **Cause:** `ic_provider_claude.xml` fills its viewport to the edge while
  `ic_provider_chatgpt.xml` carries internal padding (the blossom's bounding box is smaller than
  its viewport), so at equal box sizes the Claude glyph has more ink. Pre-dates this arc; CCRM-56
  (Provider Identity) introduced both vectors.
- **Measured:** rasterised both vectors (Chrome headless, 1000 px per 24-unit viewport) and took
  the ink bounding box. The sunburst's spikes touch all four edges — 100% x 100% of the
  viewport. The blossom's ink filled only 69.4% x 68.8%.
- **Fixed by** scaling only `ic_provider_chatgpt.xml`: added `android:scaleX`/`scaleY="1.35"`
  with `android:pivotX="10.005"` `android:pivotY="10.0"` (the original artwork's own centre) to
  the vector's existing `<group>`, leaving `translateX`/`translateY` and the path data verbatim.
  1.35x lands the blossom's ink at 93.8% x 92.8% of its viewport — inside a deliberately-chosen
  92-95% target band rather than 100%, because a solid blossom reads *larger* than a spiky
  sunburst at an equal bounding box (the spikes are mostly negative space), so equal optical
  weight needs a slightly smaller box than the sunburst's. The vector's `android:width`/`height`
  and `viewportWidth`/`viewportHeight` are unchanged, so intrinsic size — and every consumer that
  sizes off it — is unaffected.
- **Checked:** `ui/ProviderMark.kt` (Compose `Image` with an explicit `Modifier.size`),
  `notify/PinnedNotification.kt` (`RemoteViews.setImageViewResource` into a fixed 14dp `ImageView`
  in `notif_duet.xml`/`notif_duet_expanded.xml`/`notif_big_number.xml`/`notif_big_number_expanded.xml`)
  — both size the drawable to a fixed box regardless of intrinsic size, so nothing else needed to
  change. `ui/UsageIcon.kt` does not reference either provider mark. `ic_provider_gemini.xml` (the
  Gemini spark) fills 99.8% x 99.9% of its viewport already — left alone, per the fix options.
  The launcher icon vectors (`ic_launcher_*`) were not touched; they have their own geometry.
- **Where:** `res/drawable/ic_provider_chatgpt.xml` (only file changed);
  comparison render at `design/research/2026-09-10-device-pass/mark-balance-before-after.png`.

### CCBG-25 · Idle Reset Silence — a reset ping never fires while the account is idle across the reset
- **Status:** Fixed (2026-09-10) — an idle poll with no window now rolls the window over on
  the stored key, pings once, and clears it · unit-tested (`ResetRolloverTest`, 11 cases; 308
  green) · **not yet verified on a device** — confirming it means letting a 5h window expire
  unused on the Fold 7 with the ping set to Always and watching `reset_alerts` fire.
- **Severity:** Medium (the reset ping is the one standalone notification left after CCRM-61
  (Settings Diet), and its headline case — you hit the limit, stop, and wait for the window to
  come back — is exactly the case that stays silent)
- **Symptom:** **Observed on the Fold 7, 2026-09-10**, during the CCRM-60/61/62 device pass
  (RUNBOOK.md Step 5). Pro's 5h reset ping was set to **Always** at 08:20; its 5-hour window
  reset at 10:00; polls ran every 15 minutes on mobile data through 11:30. No notification was
  posted on `reset_alerts` (`dumpsys notification` lists only the pinned one; the channel exists
  and is enabled). At 11:31 the Pro card read `0% used · Starts when a message is sent`.
- **Cause:** `Alerts.checkReset` opens with `val key = window?.resetsAt?.toEpochMilli() ?: return`.
  After a 5-hour window expires with no new message, the Claude usage payload reports **no active
  session window**, so `resetsAt` is null and the function returns before the rollover test
  (`lastSeen != 0 && !sameWindow(lastSeen, key)`). The rollover is only noticed on the first poll
  of the *next* window — i.e. after the user has already sent a message — and the ping then says
  "Usage is back at 3%", late and pointless. There is no alarm-based scheduler; the ping rides on
  the poll. Pre-dates this arc (the guard is from the CCBG-4 (Alert Dedup) era) but was masked
  while threshold and pace alerts existed.
- **Fixed by** the plan the entry already named, with the decision lifted out of Android so it
  can be tested: `ResetRollover.decide(...)` is a pure function — the pattern `notify/Duet.kt`
  already uses — and `Alerts.checkReset` is now only the plumbing that reads the prefs, writes
  back what the decision returns, and posts.
  - The **idle arm**: `windowKey == null` with a stored `lastSeenKey` whose instant has passed
    is the rollover. It records the closed window to `SessionLog` with the stored peak exactly
    as the live arm does, pings under the same Always / If busy rule, and then **clears** the
    stored key and peak — so it fires once and the next real window starts clean.
  - The idle ping's body is **"Usage is back at 0%."** with **no "Next reset …" clause**: with
    no window there is no instant to name (the next one starts when a message is sent), and a
    guess would be a lie. The title keeps the tight-surface `"5h reset"` / `"Weekly reset"`.
  - **Silent cases kept silent:** a fresh install (`lastSeenKey == 0`) and a stored key still in
    the future — a payload that drops the window transiently before the reset — return no ping,
    no log, and write nothing back, so neither the key nor the peak is disturbed.
  - The **live-window arm is unchanged** byte for byte, including the CCBG-4 (Alert Dedup)
    proximity test, the peak accumulation, and the "Next reset …" clause. Weekly is unaffected
    in practice — it always carries a `resets_at` — but it goes through the same code and has
    its own test.
  - `evaluate` already passed a null window straight through, and `resetTimeout(null)` already
    fell back to its 60-minute floor, so neither needed changing.
- **Where:** [ResetRollover.kt](app/src/main/java/com/robin/claudeusage/alerts/ResetRollover.kt)
  (new — the whole decision),
  [Alerts.kt](app/src/main/java/com/robin/claudeusage/alerts/Alerts.kt) (`checkReset`, now thin),
  [ResetRolloverTest.kt](app/src/test/java/com/robin/claudeusage/alerts/ResetRolloverTest.kt).

### CCBG-16 · Stale Strip Label — a folded event keeps the account name it fired under
- **2026-09-09:** Moot — the fold machinery this fixed is removed by CCRM-61 (Settings Diet); the fix was never device-verified.
- **Status:** Fixed (2026-08-27) · unit-tested (`StripRulesTest`, 4 cases; 255 green) ·
  **not yet verified on a device** — confirming it means renaming an account on the Fold 7
  while one of its strips is still in the panel, and watching the strip follow.
- **Severity:** Low (cosmetic; the numbers and the routing are right)
- **Symptom:** **Observed on the Fold 7, 2026-08-21**, during the CCRM-6 (Multi-Account)
  device pass. The pinned panel's header read `Personal · 5-hour window` while the folded
  event strip below it read **`Pro: 7-day window will run out early`** — "Pro" being what
  that same account was called when the event fired, minutes earlier. Both the collapsed
  row and the expanded panel show the old name.
- **Cause:** `UsageCache.FoldedEvent` stores `title` and `detail` as **finished text**,
  composed at fire time by the alert copy (`"$label: $windowLabel window will run out
  early"`). A rename updates the registry, so every *live* read of `profileLabel` follows
  it — but the strings already sitting in the `foldedEvents` pref are frozen. The strip is
  not re-derived, so it cannot follow.
- **Where:** `Alerts.foldEvent` / `UsageCache.FoldedEvent` (the store), rendered by
  `Conditions.panelFor`. Note this is **not** the CCRM-6 label-prefix path
  (`Condition.labelled`), which reads `cache.profileLabel` live and is correct — the two
  look identical in the panel, which is what makes this confusing to read.
- **Pre-dates CCRM-6**, which is why it is filed separately: the frozen-text design came in
  with CCRM-44 (One Surface). But CCRM-6 made renaming a one-tap action on the account card
  and gave accounts default names people will want to change, so a state that was
  near-unreachable is now easy to hit.
- **Fixed by option A — the profile key travels with the event, the name is composed at
  draw time.** Chosen over re-titling stored events on rename (which has to string-match
  the old label out of the middle of a finished sentence — and the old label is the very
  thing the rename destroys) and over accepting it (events are short-lived, but CCRM-6
  (Multi-Account) made renaming a one-tap action, so the window is easy to hit).
  - `UsageCache.FoldedEvent` gains **`profileKey`**, and `title` is now the alert sentence
    **without** the account-name prefix. The alert copy in `Alerts` composes unprefixed
    titles; the **standalone notification path prefixes them at post time**, where nothing
    else names the account, so that copy is byte-identical to before.
  - The composition rule is a pure function, `StripRules.stripTitle`, beside the two rules
    CCBG-17 (Strip Revocation) and CCBG-18 (Strip Lifetime Stamp) already keep there — the
    same reason: it is the part that can be wrong. `Conditions.panelFor` calls it with the
    live `profileLabel` of the owning profile, giving events the identical `"label: title"`
    shape `Condition.labelled` gives the live fault strips.
  - **No migration.** `profileKey` doubles as the record's version marker: absent means the
    title is pre-fix finished text, drawn as it stands rather than prefixed twice, and it
    ages out through `alertLifetime` within one lifetime at most.
- **Deliberately unchanged:** the shown profile's own event strips still carry their account
  name, so a single-account panel reads exactly as it did — this fix restores an approved
  design rather than changing one. Whether that prefix should be *dropped* for the shown
  profile, to match its unprefixed fault strips and the header that already names it, is a
  visible change and therefore a separate, wireframe-gated call.
- **Where:** [UsageCache.kt](app/src/main/java/com/robin/claudeusage/data/UsageCache.kt)
  (`FoldedEvent` + its JSON),
  [StripRules.kt](app/src/main/java/com/robin/claudeusage/notify/StripRules.kt)
  (`stripTitle`),
  [Conditions.kt](app/src/main/java/com/robin/claudeusage/notify/Conditions.kt)
  (`panelFor`), [Alerts.kt](app/src/main/java/com/robin/claudeusage/alerts/Alerts.kt)
  (unprefixed copy, `foldEvent` stores the key).

### CCBG-18 · Strip Lifetime Stamp — "keep alerts for" is frozen at fire time and never re-read
- **2026-09-09:** Moot — the fold machinery this fixed is removed by CCRM-61 (Settings Diet); the fix was never device-verified.
- **Status:** Fixed (2026-08-26) · unit-tested (`StripRulesTest`); **not yet verified on
  a device** — the reported strips were stamped days out under `auto`, so confirming the
  fix means watching a fresh strip leave on time on the Fold 7.
- **Severity:** Medium (an alert the user has time-limited stays on screen for days)
- **Symptom:** **Observed on the Fold 7, 2026-08-26.** "Keep alerts in the shade for" is
  set to **15m**, yet two pace strips — `Product: 7-day window will run out early` and
  `Pro: 7-day window will run out early` — had been sitting in the pinned panel far
  longer than that, and were still there. Nothing the user can do in Settings shifts them.
- **Cause:** `FoldedEvent.expiresAt` is computed **once**, at fold time, from whatever
  `alertLifetime()` read at that moment (`Alerts.eventTimeout`). The store is then pruned
  against that frozen stamp (`UsageCache.foldedEvents`). Changing the chip afterwards
  changes nothing for events already folded. The setting reads like a display rule — "keep
  alerts in the shade for this long" — but behaves like a one-time stamp.
  The default is `auto`, and under `auto` a 7-day-window event is stamped to live **until
  that window resets**. So the strips the user was looking at were folded under `auto`,
  given a multi-day expiry, and are unreachable by the 15m they later chose.
- **Second half of the same defect:** expiry is only pruned **lazily, on read**, and
  nothing schedules a redraw when a strip is due to go. The panel re-renders on a poll
  (`UsageRepository`), at the end of `Alerts.evaluate`, or on a Settings change — so even a
  correctly-stamped 15m strip survives until the next poll, up to a further 15 minutes.
- **Where:** `Alerts.eventTimeout` / `Alerts.foldEvent` (the stamp),
  `UsageCache.foldedEvents` (the prune), `PinnedNotification.update` (no expiry alarm).
- **Fixed by** making the lifetime a **read-time** rule. `foldedEvents` computes an effective
  expiry of `min(stamped expiresAt, firedAt + alertLifetime)` — it can only ever *shorten*,
  so `auto` keeps the stamped window-reset ceiling and the deliberate 30-minute reset strip
  (`RESET_STRIP_MS`) is never *extended* by a longer chip. Persistent pruning still uses
  the hard stamp, so flipping back to `auto` doesn't find the events deleted. Then arm a
  one-shot alarm at the earliest live strip's effective expiry so it actually leaves on
  time rather than at the next poll.
- **Related:** [CCBG-17](#ccbg-17--strip-revocation--turning-an-alert-off-doesn-t-retract-strips-already-folded),
  found in the same session and the same store; both are consequences of CCRM-44
  (One Surface) treating a folded strip as a finished artefact rather than a live
  derivation. Same root as CCBG-16 (Stale Strip Label).

### CCBG-17 · Strip Revocation — turning an alert off doesn't retract strips already folded
- **2026-09-09:** Moot — the fold machinery this fixed is removed by CCRM-61 (Settings Diet); the fix was never device-verified.
- **Status:** Fixed (2026-08-26) · unit-tested (`StripRulesTest`); **not yet verified on
  a device**.
- **Severity:** Medium (the app shows alerts the user has explicitly switched off)
- **Symptom:** **Observed on the Fold 7, 2026-08-26.** Every notification toggle off —
  "Pace alerts" off, its three milestones off, Personal/Work/Product alerts all off, only
  the always-on notification on — and the pinned panel still showed two pace alerts:
  `Product: 7-day window will run out early` and `Pro: 7-day window will run out early`.
- **Cause:** with the pinned notification on, `Conditions.foldedInto` is true and alerts
  are written to the `foldedEvents` pref instead of being posted (`Alerts.foldEvent`). The
  panel then re-renders them from that store on every draw (`Conditions.panelFor`). They
  are not notifications; they are persisted records of past ones.
  *Generation* is gated correctly — `Alerts.evaluate` checks `paceAlertsEnabled()` before
  `checkPace` and returns early on `!profileAlertsEnabled(profile)` — so no new strips are
  being made. But `setPaceAlertsEnabled` / `setProfileAlertsEnabled` only write a boolean.
  Nothing clears the store, and nothing consults those toggles at draw time, so strips
  folded while a toggle was on outlive it — for as long as CCBG-18 (Strip Lifetime Stamp)
  lets them, which under `auto` on a 7-day window is days.
- **Applies to every folded kind**, not just pace: threshold strips survive their
  thresholds being deselected, reset strips survive their ping mode going to Off, and any
  profile's strips survive that profile's alerts being switched off.
- **Where:** `Conditions.panelFor` (draws unconditionally from the store),
  `UsageCache.setPaceAlertsEnabled` / `setProfileAlertsEnabled` (write-and-forget).
- **Fixed by** filtering at **draw** time, not only at fire time. `panelFor` drops an event whose
  governing toggle is now off — `pace.*` under `paceAlertsEnabled()`, `reset.<window>`
  under that window's ping mode, `sessionAlert`/`weeklyAlert`/`modelAlert.*` under a
  non-empty threshold set, and everything under the owning profile's
  `profileAlertsEnabled`. Clearing the store on toggle-off would work too, but filtering is
  reversible: switch a toggle back on and a still-live strip returns, which is what a
  toggle should mean. Which *threshold* fired isn't recorded on the event, so a strip for a
  single deselected rung is only caught when the whole set empties — the honest limit of
  this fix, and not worth widening the stored record for.
- **Related:** [CCBG-18](#ccbg-18--strip-lifetime-stamp--keep-alerts-for-is-frozen-at-fire-time-and-never-re-read).
- **Also changed:** every alert control in Settings' Notifications card now redraws the
  pinned panel on change (`refreshPanel()` / the two segmented rows), the way the theme
  swatches were taught to by CCBG-14 (Stale Notification Theme). Without it both fixes were
  correct but invisible until the next poll.
- **Where the rules live now:** `notify/StripRules.kt` — the lifetime arithmetic and the
  kind-to-toggle mapping as pure functions, apart from the preference reads in `Conditions`
  and `UsageCache`, following the `Projection.paceStep` split. `Alerts.eventTimeout` shares
  the same lifetime table, so a standalone notification's `setTimeoutAfter` and a folded
  strip can never disagree about what "15m" means.

### CCBG-14 · Stale Notification Theme — theme colour changes don't reach the pinned notification until the next poll
- **Status:** Fixed (2026-08-21) · **verified live on the Fold 7 the same day, v1.3
  build**: tapping the Blue swatch repainted the status-bar glyph's pace line to the
  Blue theme's spring-green partner within a second, and tapping Claude Orange flipped
  it straight back — no poll either way. (The same moment verified the ladder override
  live: the 5-hour had just crossed 80, and the fill stayed ladder-yellow through both
  theme changes, exactly as CCRM-49 (Glyph Legibility) specifies.)
- **Severity:** Low (cosmetic staleness, self-heals on the next poll)
- **Symptom:** Observed on the Fold 7, 2026-08-21, during CCRM-50 (Weekly Flag)
  verification. Picking a new theme colour in Settings recolours the app and the
  widgets immediately (`refreshWidgets()`), but the pinned notification — its gauge,
  bars and the CCRM-49 (Glyph Legibility) status-bar glyph — keeps the old theme until
  the next poll redraws it, up to 15 minutes later.
- **Where:** the theme-swatch `onClick` in `SettingsScreen` called
  `setThemeColorName` + `refreshWidgets()` but not `refreshPinned()`, which the
  status-bar icon style chips right next to it already call.
- **Fix:** add `refreshPinned()` alongside `refreshWidgets()` in the swatch click.

### CCBG-13 · Light Status Bar — status-bar icons stay white on the light theme's pale background
- **Status:** Fixed (2026-08-19) · **verified on the Fold 7, 2026-08-21** — system set to
  light mode, app open: dark clock and dark system icons on the pale bar, main screen.
  (The same pass verified CCRM-49/CCRM-50's light-mode icon rendering — light accent
  fill, the darker light-mode pace partner, light yellow flag dot — all sampled exact.)
- **Severity:** Low (poor contrast, nothing wrong with the data)
- **Symptom:** Observed on the Fold 7 (outer screen), 2026-08-18, during the release
  device pass. With the device in light theme, the app's screens draw their pale
  background edge-to-edge but the system status bar keeps **white** clock and icons,
  so the top row is white-on-pink and barely legible. Dark theme is fine (white on
  dark). The launcher and other apps flip to dark status-bar icons in light mode,
  so this is ours: the activity never sets `isAppearanceLightStatusBars` (or the
  windowLightStatusBar theme attribute) for the light palette.
- **Where:** every in-app screen — main, Settings, guide (screenshots from the
  2026-08-18 pass: main and Settings both show it).
- **Fix — one theme attribute, not code.** `android:windowLightStatusBar = true` in
  `values/themes.xml`; `values-night` keeps the platform default (light icons on
  dark). Both activities inherit `Theme.ClaudeUsage` from the manifest's application
  entry, so MainActivity and WidgetConfigActivity are covered in one place, with no
  per-activity code and nothing for a future activity to forget. When CCRM-29
  (Display Mode) adds the in-app light/dark override it must also set
  `isAppearanceLightStatusBars` programmatically from the *resolved* mode — a forced
  theme diverges from the system theme the XML attribute follows.

### CCBG-12 · Status Icon Swap — a second notification replaces the live meter with the app icon
- **2026-09-09:** Superseded in part — every standalone alert except the reset ping is removed by CCRM-61 (Settings Diet); a reset ping still posts as a second notification, by decision.
- **Status:** Fixed (built 2026-08-14 · verified on the Fold 7 by the user, 2026-08-18,
  release-signed build, two-notification state exercised)
- **Severity:** Medium (misleading display — the status bar shows a percentage that
  isn't the user's)
- **Symptom:** Observed on the Fold 7, 2026-08-14, by the user. While the CCRM-17
  (Window Pings) pinned notification is the app's *only* notification, the status bar
  carries the live meter drawn by `UsageIcon`. The moment a second notification from
  the app exists — a sign-in expiry warning, a pace alert, an update notice — the
  status-bar icon becomes the launcher icon instead. That icon is the CCRM-42 (App Icon)
  reset ring, whose arc sweeps 260° ≈ **72%**, so a user at 9% reads their status bar
  as nearly three-quarters spent. The alert itself may be hours old and long since
  irrelevant; the wrong reading persists for as long as it sits in the shade.
- **Cause — the OS discards every small icon we set.** With two or more ungrouped
  notifications from one package, the system creates its own summary record
  (`flags=…GROUP_SUMMARY|AUTOGROUP_SUMMARY`) and gives it `mipmap/ic_launcher`; the
  status-bar slot follows that summary, not any notification we posted. Confirmed on
  device: the app's live record carries `icon=Icon(typ=RESOURCE pkg=com.robin.claudeusage
  id=0x7f0b0000)`, which `aapt2` resolves to `mipmap/ic_launcher`. So neither the
  `IconCompat.createWithBitmap` meter in
  [PinnedNotification.kt](app/src/main/java/com/robin/claudeusage/notify/PinnedNotification.kt)
  nor the `R.drawable.ic_stat_bars` on the alerts in
  [Alerts.kt](app/src/main/java/com/robin/claudeusage/alerts/Alerts.kt) is consulted —
  changing either one fixes nothing.
- **It triggers at two notifications, not four.** AOSP's autogroup threshold is 4;
  One UI 8 / Android 16 aggregates at 2. A single lingering sign-in-expiry alert is
  therefore enough to hold the status bar wrong all day.
- **Verified fix — post our own group summary.** Measured with a standalone probe app
  built for this (`com.robin.iconprobe`, since a debug build of this app would have
  replaced the user's install), using three shapes readable at 24 dp: launcher =
  triangle, alert = square, ongoing = ring bitmap. Ongoing alone → ring. Ongoing + 1
  alert → triangle. Ongoing + 3 alerts → triangle. Ongoing + 3 alerts **with our own
  summary** → ring. A summary we post is `GROUP_SUMMARY` *without* `AUTOGROUP_SUMMARY`,
  which suppresses the system's and hands the slot back to our bitmap.
- **The cost, which is what needs deciding.** Grouping takes the pinned notification out
  of the top-level shade. With children present, One UI collapses the app to one row
  showing the *newest child* plus a count badge, and the meter row is only visible once
  expanded — this held whether the summary was separate or the pinned notification
  itself. A summary with no children renders as a normal row, so the cost lands only
  while an alert is pending, which is exactly when today's behaviour is already wrong.
  Pairing the fix with `setTimeoutAfter` on alerts shrinks that window.
- **Built — the one-notification path, plus the icon.** Four parts:
  1. **Icon** — `ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml` go from a 260°
     arc to a closed ring with a filled bore dot (option B). A closed ring has no origin
     and the filled bore inverts the meter's empty one, so the mark can't be read as a
     percentage at any fill. Revises CCRM-42 (App Icon).
  2. **Conditions folded** — new `notify/Conditions.kt` derives the sign-in-expiry and
     stale-data conditions as data. `Alerts` stops posting them *only* when the pinned
     notification is on **and** showing that profile (`Conditions.foldedInto`); otherwise
     they post exactly as before, since folding into a surface that isn't there would
     delete them. The panel draws them as tinted strips above the bars; the collapsed row
     shows the condition in place of the reset line; a stale reading fades its number and
     bar in the `big` style.
  3. **Event timeout** — `alertLifetime` preference (15m / 30m / 1h / auto, default auto)
     plus `Alerts.eventTimeout`, applied to reset, threshold and pace alerts.
  4. **Type scale** — alerts previously rendered at the platform's 14 sp beside the pinned
     notification's 13/12 sp custom view. New `notif_alert.xml` /
     `notif_alert_expanded.xml` match it, used only while the pinned notification is on
     and set to `big` — the other styles use platform sizes, so alerts do too.
- **Deliberately not done:** the update notice keeps no timeout. It posts "once per version,
  ever", so expiring it would silently demote that to "once per version, for an hour, then
  never". Giving it one needs the CCRM-28 (Update Check) gate to re-post on a later check.
- **Residual, by design:** re-auth and window-not-started still post, so the swap still
  happens while either is live. With the icon no longer reading as a gauge that is a
  cosmetic swap rather than a misleading one.
- **Wireframes** per working agreement 2 — approved before build:
  `design/notification-grouping-wireframe.html` (own the group) and
  `design/notification-one-surface-wireframe.html` (fold conditions into the pinned
  panel, time out events — the approved one), and `design/app-icon-options-wireframe.html`
  for the icon, where option B was chosen.
- **Decided so far (2026-08-14), on the one-notification path:** the collapsed row drops the
  reset line while a condition is live (reset stays in the expanded panel); the event timeout
  becomes a setting — 15m / 30m / 1h / Auto, defaulting to Auto, where Auto means "until the
  window the alert is about resets"; the condition strip borrows the panel's existing type
  scale (13.5 dp label, 12 dp sub) and separates itself with a 13%-alpha tint rather than a
  border.
- **Why the icon change earns its blast radius:** it is what makes the swap *misleading*
  rather than merely wrong, and it holds whichever notification approach is in play — so a
  future OS change that reintroduces the swap costs a cosmetic icon, not a false reading.
- **Note on the alternative's coverage:** folding cannot cover `auth_alerts` re-auth or
  `ping_alerts`, both deliberately `IMPORTANCE_HIGH` — the ping's comment says finding
  out late "is the failure this feature exists to avoid". Those keep posting, so that
  path leaves the defect live in exactly those two cases.

### CCBG-11 · Ring Face Clutter — four stacked lines crowd the small ring's bore
- **2026-09-09:** Superseded — the Ring widget is removed by CCRM-61 (Settings Diet).
- **Status:** Fixed (2026-08-13) · verified on the Fold 7, 2026-08-19
- **Severity:** Low (legibility, no wrong numbers)
- **Symptom:** Observed on the Fold 7 outer screen, 2026-08-13, by the user: the
  CCRM-39 (Ring Widget) face reads as cluttered. Four lines stack inside the ring's
  bore — profile name, percentage, countdown, exact reset — and the fourth
  ("today at 9:10 pm") runs nearly wall to wall inside the bore, so the ring's inner
  edge crowds the text on both sides. Evidence:
  `release/screenshots/widget-ring-clutter-fold-outer.png`.
- **Detail:** the four lines are fixed regardless of the widget's size, so the bore
  has to hold all of them however small it gets; nothing drops out as the face
  shrinks. The exact reset stamp is the weakest of the four — the countdown above it
  already answers "when", and the in-app card and the notification both carry the
  exact time — which makes it the obvious candidate to drop, move under the ring, or
  gate on available height.
- **Not a regression from CCRM-43 (Bar Pace Marks):** the pace tick is drawn on the
  ring's stroke, outside the bore, and doesn't touch the text stack.
- **Worse than reported, at the size that matters.** The provider declares this widget
  **2×2, minimum 110×110 dp** (`ring_widget_info.xml`), so small is the intended shape and
  the 178×240 placement is the outlier. At 110×110 the bore holds about 51 dp of text and
  the stack needs about 71 — it doesn't crowd, it **overflows**, with the reset line
  crossing the ring stroke. Nobody had looked at the face at its own declared size.
- **Fixed — the bore holds the percentage and nothing else.** Profile, countdown and exact
  reset all moved outside the ring, which is what frees the ring to reach the edge of the
  face and stay legible small. [WidgetFace.kt](app/src/main/java/com/robin/claudeusage/widget/WidgetFace.kt)'s
  new `ringFaceLayout` picks a size class from the face: 110×110 → an 81 dp ring, 21 sp
  number, countdown under it; 150×150 → 108 dp with the account name above; 178×240 →
  140 dp (**capped** — past that a solo ring is a poster, not a gauge) with the exact reset
  as a second line under; wide and short → the lines in a column beside the ring. Pinned by
  `WidgetFaceTest`. Wireframe rev B approved 2026-08-13.

### CCBG-10 · Mini-Rings Emptiness — two rings marooned in a mostly empty face
- **2026-09-09:** Superseded — the Mini-rings widget is removed by CCRM-61 (Settings Diet).
- **Status:** Fixed (2026-08-13) · verified on the Fold 7, 2026-08-19
- **Severity:** Low (wasted space, no wrong numbers)
- **Symptom:** Observed on the Fold 7 outer screen, 2026-08-13, by the user: on an
  account with only two windows, the CCRM-40 (Mini-Rings Widget) face is mostly empty
  — two small rings sit in wide gutters with a dead band of roughly a third of the
  height beneath them. Evidence:
  `release/screenshots/widget-mini-rings-empty-fold-outer.png`.
- **Detail:** two independent causes, both in `widget/MiniRingsWidget.kt`. The ring is
  drawn at a **fixed 56 dp** (`ringBitmap(context, 56f, 5.5f, …)`) no matter how much
  room the face has; and the columns divide the **full width** between however few
  rows `windowRows` returns, so two rings on a 4×2 face spread to the quarter points
  and leave the middle empty. An account with four windows fills the same face
  reasonably, which is why this didn't show up in the wireframe.
- **It also overflows at its own declared minimum.** At 250×110 dp — what
  `mini_rings_widget_info.xml` asks for — a fixed 56 dp ring plus both text lines don't fit,
  so the countdown is clipped. The emptiness and the clipping are the same bug seen from
  two ends: a ring that ignores the face.
- **Fixed — the ring follows the space, and the row stops spreading.** `miniRingsLayout` in
  [WidgetFace.kt](app/src/main/java/com/robin/claudeusage/widget/WidgetFace.kt) computes the
  diameter from the column width and row height (ceiling **88 dp**, floor 36), scales the
  stroke and the percentage with it, and caps the column so one or two rings centre as a
  group instead of sitting at the quarter points. On a short face the countdown drops
  first, then the title — the ring never gives way. The row cap also drops **four → three**
  (`windowRows(data, max = 3)`), which is what allows a ceiling that high; the other caller
  keeps four. On the reported face the ring goes 56 → 88 dp. Pinned by `WidgetFaceTest`.
  Wireframe rev B approved 2026-08-13.

### CCBG-8 · Sonnet Cap Fallback — model caps vanish silently if the `limits` array is absent
- **Status:** Fixed (2026-08-07)
- **Fix as specced below**, plus one extension the captured payloads justify:
  when `limits` yields no caps, `UsageParser.parse()` now falls back to **both**
  attested flat siblings — `seven_day_sonnet` (OpenQuota reads it) *and*
  `seven_day_opus`, which is not a guess: it appears (as `null`) in our own 2026-07-27
  captured payload, so the field exists in the schema. No other `seven_day_*` model
  field is attested anywhere, and none was added.
- **Tests, which were the point:** a flat-fields-only payload (the older schema — what a
  server-side rollback would send) must yield both caps with their percents, and
  `realPayload` — `limits` present, `seven_day_opus: null` — must keep an empty cap
  list, pinning that a null flat field never materialises a cap. 85 tests, 0 failures.
- **Severity was:** Open · **Low** · latent, never observed
- **Found:** 2026-08-04, reading OpenQuota's Claude mapper (`src-tauri/src/providers/claude/
  mapper.rs`) alongside ours during the CCRM-21…38 review. Not a live failure — a gap in the
  fallback path.
- **What's wrong:** `UsageParser.parse()` reads the `limits` array first and derives the
  session window, the weekly window and every `weekly_scoped` model cap from it. When
  `limits` is missing it falls back to the flat sibling fields — but only two of them:

  ```
  if (session == null) session = windowFrom(root.optJSONObject("five_hour"))
  if (weekly == null)  weekly  = windowFrom(root.optJSONObject("seven_day"))
  ```

  There is no fallback for **`seven_day_sonnet`**, which OpenQuota reads directly as its
  Sonnet window. So if `limits` ever disappears from the payload, the two main bars degrade
  correctly and the model caps **silently become an empty list** — no cap rows on the main
  screen, no cap alerts, and no error anywhere, because `UsageData` with a session and a
  weekly window is a perfectly valid parse.
- **Why it matters despite being latent:** the schema is undocumented and carries transient
  experiment fields — the parser's own doc comment says so. `limits` is the newer shape; the
  flat fields are the older one. A rollback on Anthropic's side is exactly the scenario the
  fallback exists for, and it's the scenario where this half-works. The failure is also the
  hardest kind to notice: a *missing* row reads as "this account has no model caps", which is
  a legitimate state for some accounts.
- **Fix:** add `if (caps.isEmpty()) windowFrom(root.optJSONObject("seven_day_sonnet"))` as a
  `ModelCap("Sonnet", …)`, and cover it in `UsageParserTest` with a payload that has the flat
  fields and no `limits` — the test matters more than the two lines, since the whole point is
  that nothing currently proves the fallback path is complete.
- **Check while in there:** whether any other `weekly_scoped` model has a flat sibling field
  (an Opus- or Fable-shaped `seven_day_*`). Only `seven_day_sonnet` is confirmed to exist, via
  OpenQuota reading it; the rest is unknown and shouldn't be guessed at.

### CCBG-6 · Credits Denominator — the bar measures the monthly limit, not the balance that would actually stop you
- **Status:** **Won't fix (2026-08-07)** — until the server populates `spend.balance`.
  The one open question is answered, and the answer is no: **our subscription OAuth
  bearer does not authenticate on `claude.ai/api/…`.** Probed on-device through the
  Settings → Debug endpoint probe, both profiles, same result:
  `GET https://claude.ai/api/organizations` → **HTTP 403**,
  `{"type":"error","error":{"type":"permission_error","message":"Invalid authorization",
  "details":{"error_visibility":"user_facing","error_code":"account_session_invalid"}}}`
  (request ids `req_011CdoXhGxQMXVmApE7kF1Kt` Personal, `req_011CdoXw5TjibmHrYrm1dUG6`
  Work). That is a clean JSON auth rejection from the API itself — not a WAF shape, so
  no UA-variant retry applies; the endpoint wants a claude.ai browser *session*
  (`account_session_invalid`), which we don't have and shouldn't fake. `/api/bootstrap`
  returned 200 with anonymous content (feature-gate config), consistent with a
  session-gated API. This was the outcome the entry named as possible.
- **Shipped anyway — the inert remnant, so the fix arms itself** (2026-08-07):
  `SpendCredits.balanceMinor: Long?` parsed from `spend.balance` (null in every payload
  ever captured; absence ≠ zero), and `bindingRemainingMinor` =
  `min(monthly remainder, balance)` — the balance alone when uncapped, the remainder
  alone while the balance is absent. Every "left" figure (`MainActivity` card,
  `UsageWidget` large bucket, `BarWidget` credits mode) now reads the binding figure.
  With no balance it equals `remainingMinor` in every state, so **today's rendering is
  unchanged — pinned by test, not by inspection**; the day the server populates
  `spend.balance`, the binding constraint lights up without an app update. The balance
  *line* from the agreed display fix is deliberately **not** built — it would be a
  visual state that cannot be observed on any device, which is the CCRM-15 (Above-Pace
  Verification) failure mode. 6 new `UsageParserTest` cases (83 total, 0 failures)
  cover both captured payloads' null balance, binding in all four states, and a
  hypothetical populated `spend.balance`.
- **Reopen when:** `spend.balance` stops being null on `/api/oauth/usage` (the parser
  and binding maths are already live — what remains then is the balance line, per the
  agreed three-row display fix below), or Anthropic exposes the balance to OAuth
  clients some other way.
- **Severity:** Medium, and it worsens on its own — see the divergence note below
- **Symptom:** The credits meter always reads `$X / $100.00`, because the denominator is
  the *monthly spend limit*. The Claude app's own Usage tab shows a third figure we don't
  read at all — a **Balance** — and it is the number that actually runs out.
- **Confirmed against the Claude Android app, 2026-08-03.** Its Usage → Credits section
  showed three rows where we show one:

  | Row | Value |
  | --- | --- |
  | This month | `$2.97 of $100.00 spent` |
  | Monthly spend limit | `100 credits` |
  | Balance | `91.04 credits` |

- **What the arithmetic settles:** our captured 2026-07-27 payload had `used` = `$5.99`,
  and August's spend is `$2.97`. `100 − 5.99 − 2.97 = 91.04` **exactly**. So the balance
  is a **cumulative pot that does not reset monthly**, while `spend.used` / "This month"
  does. The monthly limit caps a month; the balance is the money that exists. Real
  headroom is `min(monthly limit − used, balance)` — today `min(97.03, 91.04)`.
- **Why it gets worse rather than staying cosmetic:** the balance only ever decreases
  while the monthly counter resets, so the two diverge monotonically. At a balance of 8
  credits our card would still read `$2.97 of $100.00 · 3% used · $97.03 left` — a full
  bar's worth of headroom that isn't there. The bug is mildest on the day it's filed.
- **The blocker, established 2026-08-03 and not a guess.** Both profiles' raw responses
  were captured minutes after the screenshot above. **`"balance":null` on both.** The
  Personal payload pins itself to that exact screenshot — `spend.used.amount_minor: 297`
  = the `$2.97` row, `limits[weekly_all].percent: 24` = the `24% used` row — so this is
  the same account in the same state, showing `91.04 credits` in the Claude app while
  this endpoint reports no balance at all.
  - So it is **not** a shape or denomination question, and not a field we failed to
    parse: the number is absent from the response. The Claude app sources it elsewhere.
  - `can_purchase_credits: false` and `auto_reload: null` on both profiles, so the pot
    is **granted, not purchased** — `balance`/`auto_reload` being null is evidently about
    the *purchase* flow, not about whether a balance exists.
  - Also captured: a batch of new all-null experiment windows (`seven_day_cowork`,
    `nimbus_quill`, `cinder_cove`, `amber_ladder`, …). The parser ignores unknown keys,
    so they cost nothing — but none of them carries a balance either.
- **Rejected: deriving it locally.** `cap.credits − Σ(monthly spend)` would need a spend
  history we don't have, and would be silently wrong for any install that starts
  mid-life — which is every install. A wrong balance is worse than no balance line.
- **Source located 2026-08-04 — the Claude app uses a different API family entirely.**
  Method: pulled `base.apk` from the phone over wireless adb (Claude `1.260721.20`,
  `com.anthropic.claude`) and string-searched `classes*.dex`, the same technique
  [ApiClient.kt](app/src/main/java/com/robin/claudeusage/data/ApiClient.kt) records for
  the token endpoint. Findings:
  - **Zero `api/oauth/` literals in the whole dex.** The app never calls the endpoint we
    call, so there is no OAuth-side sibling to find — that search is closed.
  - Base URL `https://api.claude.ai`; the only usage path is the Retrofit template
    **`organizations/{orgId}/usage`**. Comparable templates
    (`organizations/{organization}/chat_conversations`) match known `claude.ai/api/…`
    URLs, so the full shape is `https://api.claude.ai/api/organizations/{uuid}/usage`.
  - Models: `com.anthropic.claude.api.usage.UsageResponse`,
    `com.anthropic.claude.api.common.SpendSummary`,
    `com.anthropic.claude.api.common.Credits`.
  - Serialized names in the dex include **`balance_credits`** and **`granted_credits`**,
    plus `free_credits_status`, `amount_minor`, `exponent`, `org_spend_cap_reached`,
    `out_of_credits`. `granted_credits` corroborates the granted-pot reading above.
  - **Caveat on the field names:** the dex string table is sorted, so adjacency proves
    nothing about which class owns which name. `balance_credits_after` reads like
    purchase analytics, not a response field. Treat the list as candidates.
  - Also present: `organizations/{organization_uuid}/prepaid/iap/android` — Play-billing
    credit purchase, i.e. the flow the support article's "Add funds" describes.
- **`api.claude.ai` does not exist — the APK constant is dead.** First probe run
  (2026-08-04) returned `HTTP 0 · Unable to resolve host "api.claude.ai": No address
  associated with hostname`. Confirmed off-device: `dig api.claude.ai` returns **no A
  record**, while `claude.ai` and `api.anthropic.com` both resolve — to the same address,
  `160.79.104.10`. So it isn't DNS filtering on our network; that hostname has no
  addresses at all, and the live origin is **`claude.ai`**. `ApiClient.ProbeHost.CLAUDE_AI`
  now points there, pinned by `ProbePathTest`.
- **The question that decided it** (answered 2026-08-07 — see the status above): does our
  subscription OAuth bearer authenticate against `claude.ai/api/…`? It was proven only on
  `api.anthropic.com` with `anthropic-beta: oauth-2025-04-20`, and claude.ai rejects it.
- **Probe control verified:** `/api/oauth/usage` on `api.anthropic.com` returns 200 through
  the probe, so the plumbing is sound and any non-200 elsewhere is a real answer.
- **Dead ends, so nobody repeats them:** release logcat carries no request URLs; there is
  no DevTools socket to attach to; and `run-as` is refused because the installed build is
  release-signed — so the token cannot be read off the device (by design).
- **Where the denominator comes from:** `spend.limit.amount_minor` in `creditsFrom()`
  ([Models.kt](app/src/main/java/com/robin/claudeusage/data/Models.kt)), fed into
  `SpendCredits.percent` / `remainingMinor` and read by the card in `MainActivity`, the
  large bucket of `UsageWidget`, and `BarWidget`. `extra_usage.monthly_limit` reports the
  same figure on the fallback path — this is the user-set monthly extra-usage cap.
- **Fields in play:**
  - `spend.balance` — the one we want. `null` on both profiles, even with a live balance.
    **Parsed as of 2026-08-07** (the inert remnant above), waiting for the server.
  - `spend.cap` — `{"money": null, "credits": {"amount_minor": 10000, "exponent": 2}}`.
    `cap.credits` = `10000` lines up with the app's **`100 credits`** monthly-limit row,
    while `spend.limit` = `10000` lines up with **`$100.00`**. So credits and money are
    1:1 on this account but reported through different fields — don't collapse them.
- **Fix, decided (agreed 2026-08-03) — do NOT fold the balance into the denominator.** The Claude app shows
  three separate rows rather than one derived ratio, and it's right to: `used / (used +
  balance)` mixes a monthly counter with a cumulative pot and would print a `$94.01`
  that appears nowhere in the payload. Instead:
  - Keep the bar against the monthly limit — that *is* a true monthly ratio, and it
    matches the upstream "This month" row.
  - Add a balance line, and make the trailing "left" figure report **whichever
    constraint binds**: the balance when it is below the monthly remainder, the monthly
    remainder otherwise. The misleading part today is the `left` number, not the bar.
  - Parse into `SpendCredits.balanceMinor: Long?` so absence stays distinguishable from
    zero, and keep `remainingMinor` as-is (`limit − used`) so nothing silently changes
    meaning under existing callers — `MainActivity`, `UsageWidget`, `BarWidget`.
  - Pin it with a `UsageParserTest` case on the real captured payload, asserting both
    the non-null balance and the binding-constraint choice.

### CCBG-9 · Credits Vanish — the whole credits section disappeared when the account had no monthly limit
- **Status:** Fixed (2026-08-04)
- **Severity:** Medium (a whole section silently gone, and a widget asserting something
  false) — no data loss
- **Symptom:** The Usage credits card was **absent from the main screen** despite $2.97 of
  spend, and a `BarWidget` set to *Usage credits* read **"No credits · This account has no
  credit budget"** — untrue: spend was live, `spend.enabled` was `true`, and the Claude app
  showed a 91.04-credit balance.
- **Trigger, confirmed by the user:** they **switched the monthly spend limit off**
  (unlimited). The payload then returns `spend.limit: null`, `spend.cap: null` and
  `extra_usage.monthly_limit: null` while still reporting `spend.used.amount_minor: 297`.
  So this is a **user-reachable setting**, not a server glitch — anyone who turns the cap
  off loses the whole section.
- **Root cause — both branches of `creditsFrom()` bailed on a null limit**
  ([Models.kt](app/src/main/java/com/robin/claudeusage/data/Models.kt)):
  `optJSONObject("limit")` returns null for a JSON `null`, so the preferred `spend` branch
  was skipped even though `used` parsed fine; the fallback then hit
  `if (extra.isNull("monthly_limit")) return null`. `UsageData.credits` came back null and
  every consumer's `limitMinor > 0` render gate failed. Verified before fixing by parsing
  the captured payload: windows fine (`session 15.0`, `weekly 31.0`), `credits` null.
- **The bad assumption:** CCRM-1 (Credits Display)'s visibility rule, "render whenever
  `limit > 0`", equated *having a cap* with *having credits*. An uncapped account has
  credits and no cap.
- **Fix — the limit is optional data, never the existence test.**
  - `SpendCredits.limitMinor` is now `Long?`. `null` (no cap) and `0` (capped at zero) are
    different states and no longer render alike.
  - `percent`, `percentDisplay` and `remainingMinor` are **nullable, and null when
    uncapped**. Nothing is synthesised — a 0% bar would draw empty and read as "plenty of
    headroom" against a ceiling that doesn't exist.
  - New `isReportable` (`hasLimit || usedMinor > 0`) replaces `limitMinor > 0` as the
    render gate in `MainActivity`, `UsageWidget` and `BarWidget`. Accounts with no cap and
    no spend stay hidden — deliberately conservative, since they can't be told from a
    no-credits account without trusting `spend.enabled`, still unverified (CCBG-3
    (Credits Visibility)).
  - **Uncapped rendering:** the card reads `Usage credits · $2.97 spent · No cap` with
    **no bar**, and the trailing line "No monthly spend limit — credits cover you when you
    hit your plan limits". `UsageWidget`'s large bucket swaps its bar for a plain
    `Credits · $2.97 spent · no cap` row; `BarWidget` drops the bar, promotes the amount
    into the headline slot, and its sub-row reads `no monthly cap`.
- **Tests:** 3 new `UsageParserTest` cases pinning the captured 2026-08-04 payload — that
  credits survive a null limit, that no percentage or remainder is invented, and that the
  `extra_usage` fallback behaves identically. Two existing cases updated for the nullable
  contract. `./gradlew testDebugUnitTest` → 77 tests, 0 failures.
- **Found via:** the CCBG-6 (Credits Denominator) endpoint probe's *control* request. The
  probe was built to chase the balance and caught a live defect in its first payload
  instead.
- **Consequence for CCBG-6 (Credits Denominator):** with the cap off, the balance is now
  the **only** ceiling that exists — so the missing balance line is the whole story on this
  account, not a refinement.
- **Device verification:** the uncapped **card** was seen on the phone 2026-08-07 while
  verifying CCBG-6 (Credits Denominator)'s remnant build — `Usage credits · $10.75 spent ·
  No cap`, with the no-limit trailing line, exactly as designed. The two **widget**
  layouts (UsageWidget large row, BarWidget credits mode) are still unobserved; CCRM-15
  (Above-Pace Verification) exists because a visual state shipped unobserved — so look at
  them when one is next placed.

### CCBG-7 · Chart Label Collision — RETRACTED, never a bug
- **Status:** **Invalid — retracted 2026-08-04, same day it was filed.** Kept because IDs
  are never reused, and because the way it was filed is the lesson.
- **Claimed:** that a window projected to 100% drew its `~100%` label on top of the
  `100%` threshold label in the right-hand gutter.
- **Why it cannot happen.** The gutter labels are drawn at `plotRight + 3.dp`
  ([Sparkline.kt:325](app/src/main/java/com/robin/claudeusage/ui/Sparkline.kt#L325)), and
  the projection label's x is clamped to `coerceIn(0f, plotRight - width)`
  ([Sparkline.kt:418](app/src/main/java/com/robin/claudeusage/ui/Sparkline.kt#L418)), so
  its right edge can never pass `plotRight`. The two are separated by construction, in
  every state — not just the one that was inspected.
- **How it got filed:** from a screenshot downscaled to about 90% of its width, in which
  the two labels sit close together and read as overlapping. At full resolution they are
  plainly apart. The lesson is narrow and worth keeping: **verify a pixel-level claim at
  pixel resolution**, and prefer the geometry in the source over a reading of an image.
- **What is actually there**, at much lower severity and not filed as a bug: the `~100%`
  label is drawn across the even-pace diagonal and its own projection dashes, so the
  glyphs have dashed lines running through them. Legible, but not clean. A
  surface-coloured backing behind the text — the same treatment CCRM-20's tap callout
  already uses — would settle it if it ever becomes worth doing.

### CCBG-5 · Ping Verification — a working ping is reported as a failure
- **2026-09-09:** Superseded — window pings are removed by CCRM-61 (Settings Diet).
- **Status:** Fixed (2026-07-31)
- **Severity:** High (reported the opposite of what happened, and turned one ping into
  several)
- **Symptom:** observed on the Fold 7, 2026-07-30. **Test ping now** on Personal, from a
  genuinely cold state, reported `Ping sent but no window opened — nothing scheduled`.
  The ping had in fact worked — the 5-hour card later showed a live window.
- **Second symptom, worse than the first:** because "no window seen" counted as failure,
  it entered the *send*-retry backoff and pinged again at 1/3/8 minutes. The 2026-07-30
  run sent roughly four pings where one was wanted, and only recorded success at 8:13 pm
  once the window finally became visible. The false report was cosmetic; the ping storm
  was not.
- **Root cause: the usage endpoint lags the inference.** `sendWindowPing` sends the
  ping, immediately re-reads usage, and treats "no window in the response" as failure.
  But the window is not visible that soon.
  - **It is not rate limiting.** That was my first guess and the evidence refutes it:
    the main screen read `Last success: Thu 8:08 pm`, so the post-ping fetch *succeeded*
    — it just still showed no window. (A 429 did appear at 8:09, but that was a later,
    separate refresh triggered by returning to the main screen.)
  - Timeline: 8:06 pm no window · 8:08 pm ping `200`, successful usage read still shows
    no window · 8:41 pm window present, `[7:59 pm → 12:59 am]`.
  - **Measured lag:** invisible seconds after the ping; visible by 8:13 pm, i.e. within
    about **five minutes**. (An earlier reading put it at "no more than 33 minutes"; the
    8:13 pm success narrows it.) The 15s settle in the spike script would not have been
    reliably enough.
- **Consequence: synchronous verification cannot work at all.** Any read taken straight
  after a ping may legitimately show nothing, so "no window yet" carries no information
  about whether the ping succeeded.
- **Fix — send and verify are now separate operations.**
  - `UsageRepository.sendWindowPing()` reports only the *send* outcome (`Sent`,
    `AlreadyOpen`, or a real failure). It records the pre-ping `resets_at` and stamps
    the send time, then stops.
  - `UsageRepository.verifyWindowPing()` runs later from its own alarm
    (`PingScheduler.ACTION_VERIFY`) at +90s, with one re-check at +4min —
    `VERIFY_DELAY_MS`, `VERIFY_RETRY_MS`, `MAX_VERIFY_ATTEMPTS`. Together they reach
    past the observed lag, and a test pins that they do.
  - **`NotYet` / `GaveUp` are not failures and never notify.** Only send failures
    (non-200, auth, network) retry or raise `notifyPingFailed`.
  - A refresh that fails during verification yields `NotYet`, not evidence — the old
    code ignored its own `FetchResult` entirely.
  - **`PingSchedule.tooSoonToSend` / `MIN_SEND_INTERVAL_MS` (10 min)** is a hard floor
    between sends regardless of what the rest of the logic concludes. This is the
    backstop against the storm: worst case is one wasted ping, never a burst.
  - **On giving up, the window counts as started.** Every observation says pings work,
    and the send floor already prevents a burst, so miscounting one window is far
    cheaper than re-pinging in a loop.
- **Also fixed:** the settings status row never refreshed — it read `pingLastResult`
  during composition with nothing to trigger recomposition when the alarm wrote it, so
  it sat on a day-old result while a ping came and went. Now polls a revision counter.
- **Also:** the toggle's subtitle now says that turning it on starts a window
  immediately, which is what it has always done — both 2026-07-30 pings were fired by
  `rearm()` on enable, not by the Test button.
- **Tests:** 4 cases in `PingScheduleTest` (16 → 20) covering the send floor and an
  assertion that the verification window both covers the observed lag and finishes
  inside the send floor. 60 tests, 0 failures.
- **Found via:** the first on-device run of CCRM-17 (Window Pings).
- **Not yet device-verified:** the deferred path has not run on the phone.

### CCBG-4 · Alert Dedup — threshold alerts re-fired every poll because `resets_at` isn't stable
- **2026-09-09:** Superseded in part — threshold alerts are removed by CCRM-61 (Settings Diet); the `sameWindow` proximity test survives in `checkReset` for the reset pings.
- **Status:** Fixed (2026-07-30)
- **Severity:** High (repeat notifications; the dedup that existed didn't work)
- **Symptom:** once a window was past its lowest threshold (80% session, 90% weekly),
  the usage alert re-fired on **every poll** — every 15 min on the default interval —
  instead of once per window. `notify()` sets no `setOnlyAlertOnce`, so each repeat
  re-alerted with sound rather than quietly updating in place.
- **Root cause:** the same server-side `resets_at` drift as [CCBG-2](#ccbg-2--trend-chart-binding--trend-chart-and-projection-almost-never-appeared),
  in a consumer that fix didn't touch. `checkThresholds` used `resetsAt.toEpochMilli()`
  as window identity and compared it **exactly**:

  ```kotlin
  if (cache.alertKey(profile, keyName) != windowKey) {
      cache.setAlertState(profile, keyName, windowKey, 0)   // wipes "already notified"
  }
  val alreadyNotified = cache.alertThreshold(profile, keyName)   // reads back 0
  ```

  The key differed on every poll, so the state was wiped every poll, `alreadyNotified`
  was always 0, and `crossed` re-selected the lowest threshold already passed.
  Confirmed on the Work account 2026-07-30 — five polls 60s apart inside one unchanged
  window returned five distinct values:

  ```
  2026-07-30T09:19:59.913124+00:00  -> 1785403199913
  2026-07-30T09:19:59.625243+00:00  -> 1785403199625
  2026-07-30T09:20:00.333280+00:00  -> 1785403200333
  2026-07-30T09:20:00.950515+00:00  -> 1785403200950
  2026-07-30T09:20:00.698040+00:00  -> 1785403200698
  ```
- **Fix — tolerance, NOT truncation.** New `Projection.sameWindow(a, b, windowLengthMs)`
  reusing the existing `windowLength / 4` tolerance, so both consumers of `resets_at`
  now share one definition of window identity. `Alerts.checkThresholds` and
  `checkReset` take a `windowLengthMs` and compare through it;
  `Projection.SESSION_MS` / `WEEKLY_MS` are now the single source of truth for the
  lengths the tolerance derives from (`MainActivity` and `SettingsScreen` had their own
  copies — one a `Duration` pair, the other bare literals).
  - **Truncation was the tempting fix and is wrong:** the measured drift straddles the
    `09:20:00` boundary, so minute-truncation still flips between `09:19` and `09:20` —
    unstable less often, which is worse than never, because it looks fixed. Pinned by a
    regression test.
  - **Same-window re-anchoring:** on a matching window the stored key is refreshed to
    the newest reading (threshold preserved), so each comparison spans a single poll
    interval and the slide can't accumulate past tolerance over a 7-day window.
  - **Rejected: `setOnlyAlertOnce`.** Floated as belt-and-braces in the original
    report, but both thresholds share one notification id, so it would have muted the
    legitimate 80% → 95% escalation. The tolerance fix is sufficient on its own.
- **Also fixed:** the narrow `checkReset` race — a poll landing within ~1s of a true
  boundary could see drift push `lastSeen` into the past and spuriously log a closed
  window plus a reset ping. CCBG-2 audited `checkReset` and correctly cleared it for the
  *steady-state* case (the `isBefore(Instant.now())` guard holds while a window is open)
  but not the boundary case, and didn't audit `checkThresholds` at all.
- **Tests:** 5 cases in `ProjectionTest` (14 → 19), including all-pairs over the five
  real measured values and an explicit assertion that minute-truncation would have
  split them. `./gradlew testDebugUnitTest` → 31 tests, 0 failures.
- **Found via:** the [CCRM-17](ROADMAP.md) ping spike, which snapshotted usage either
  side of an inference call and showed `resets_at` moving when nothing had changed.
- **Rule going forward:** never compare `resets_at` with `==`, and never normalise it by
  truncation. Go through `Projection.sameWindow`.

### CCBG-2 · Trend Chart Binding — trend chart and projection almost never appeared
- **Status:** Fixed (2026-07-29)
- **Severity:** High (a whole feature silently dead, and it looked like flakiness)
- **Symptom:** The burn-rate sparkline and the "At this pace…" line showed up
  seemingly at random — present on Personal, absent on Work, then gone from both —
  despite ~8 days of recorded history for each profile.
- **Root cause:** `Projection.sessionSamples`/`weeklySamples` bound history points to
  a window by **exact `resets_at` equality**. The server slides that timestamp
  forward on nearly every poll: the on-device diagnostic measured **561 distinct
  7-day `resets_at` values across 672 history points**, with only **1** matching the
  live window. A chart needs 2. So it was broken continuously; the times it did
  render were luck — two consecutive polls happening to report an identical value.
- **Fix:** bind by proximity instead. A point belongs to the window when
  `|point.resetAt − window.resetAt| ≤ windowLength / 4`. Unambiguous because a
  *genuine* reset moves `resets_at` by a full window length — four times the
  tolerance — so the previous window can never leak into the current one. This also
  rescues the ~1,300 points already on disk, which normalising on write would not.
- **Checked and ruled out:** the same drift could have fired false reset pings and
  written bogus `SessionLog` records via `Alerts.checkReset`, since it treats
  `lastSeen != key` as a rollover. It's saved by the
  `Instant.ofEpochMilli(lastSeen).isBefore(Instant.now())` guard — during drift the
  old reset time is still in the future. History bars and reset pings were unaffected.
- **Also:** with binding fixed, a window carries hundreds of points, so
  `estimate()`'s first-to-last slope became the weak link (one early burst set the
  pace for the whole window). It's now a least-squares fit over every sample, still
  anchored on the latest reading so the dashed tail meets the point the chart draws.
- **Diagnostic kept:** Settings → Debug → **Trend samples** reports bound vs distinct
  counts per window. `distinct` above 1 for a live window is the signature of drift.

### CCBG-1 · History Retention — usage history is wiped on sign-out / re-auth
- **Status:** Fixed (2026-07-27) — took the preferred option below: dropped
  `historyStore.clear(profile)` from `clearCredentials()`. Both stores are now
  untouched by the credential lifecycle, so `HistoryStore` and `SessionLog` stay
  consistent, and stale points age out via the existing 8-day prune.
- **Follow-up:** nothing clears history any more — `HistoryStore.clear()` and
  `SessionLog.clear()` are both callerless. A genuine account switch needs its own
  explicit affordance rather than re-coupling it to sign-out; filed as
  [CCRM-14](ROADMAP.md).
- **Severity:** High (silent data loss)
- **Symptom:** After clearing credentials and signing back in, the accumulated usage
  history/trend for that profile is gone.
- **Root cause:** `clearCredentials()` unconditionally deletes the history file:
  [UsageRepository.kt:71](app/src/main/java/com/robin/claudeusage/data/UsageRepository.kt#L71)
  calls `historyStore.clear(profile)`. But history is stored per **profile slot** in
  `filesDir` ([HistoryStore.kt](app/src/main/java/com/robin/claudeusage/data/HistoryStore.kt)),
  keyed by `profile.key` — it isn't tied to the token/account at all. So clearing
  credentials to re-authenticate the *same* account needlessly destroys local trend data.
- **Fix — decide the intended contract, then:**
  - **Preferred:** decouple history from the credential lifecycle. Drop
    `historyStore.clear(profile)` from `clearCredentials()`. On re-sign-in to the same
    slot, history simply continues (windows self-identify by `resetsAt`, so stale points
    age out on their own via the 8-day prune).
  - **If we want a clean slate only on a genuine account switch:** keep history on
    "Re-sign in", and clear it *only* when the newly signed-in account differs from the
    previous one (compare an account/subscription identifier), not on every clear.
  - Note the inconsistency: `clearCredentials()` does **not** clear `SessionLog` (the
    long-term per-window peak log), only `HistoryStore`. So after a clear, the history
    *bars* may survive while the raw sparkline is gone. Whatever contract we pick,
    apply it to both stores so they stay in sync.
- **Note:** also relevant to CCRM-6 (multi-account) — a dynamic profile registry should
  define history ownership per stable key from the start.
