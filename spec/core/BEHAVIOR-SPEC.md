# Behaviour specification

Product rules that must be identical on every platform. Where a client's native idiom
conflicts with a rule here, the rule wins — these are the things that make two CCooldown
installs agree with each other.

Presentation is explicitly **out of scope**: layout, typography, widget sizes, menu-bar vs
home-screen. Only numbers, thresholds, timing, and wording semantics are pinned.

Extracted from the Android client at v1.1, the reference implementation.

---

## 1. Windows and severity

Three window types, all reported as a percent 0–100 plus a reset instant:

- **Session** — the 5-hour window. Length `5h` = 18,000,000 ms.
- **Weekly** — the 7-day all-models window. Length `7d` = 604,800,000 ms.
- **Model caps** — per-model 7-day windows (e.g. an Opus cap). Same length as weekly.

**Severity** (drives text/icon emphasis) uses the server's value when present, otherwise
falls back to the percent:

| Severity | Condition |
|---|---|
| `CRITICAL` | server says `critical`, **or** percent **> 90** |
| `WARNING` | server says `warning`, **or** percent **≥ 70** |
| `NORMAL` | otherwise |

Note the asymmetry: `> 90` is strict, `≥ 70` is inclusive. A null percent counts as 0.

> **Trap:** severity thresholds (70/90) are **not** the bar-colour breakpoints (80/90/100).
> They are two independent scales and must not be unified. See §5.

Source: `Models.kt:9-20`.

### 1.1 Window classification and reset — Provider

CCM-76 [Accounts]: both providers report a session (5-hour) and a weekly (7-day) window, but
read the response differently. `ChatGPTUsageParser` produces the exact same `UsageData` shape
`UsageParser` does — every rule elsewhere in this document applies to both providers equally
except where this table says otherwise.

| Rule | Claude | ChatGPT |
|---|---|---|
| Classified by | `limits[].kind` (`session` / `weekly_all` / `weekly_scoped`), or the legacy `five_hour`/`seven_day`/`seven_day_<model>` siblings when `limits` is absent | `limit_window_seconds` (18000 → session, 604800 → weekly), **never** by slot (`primary_window`/`secondary_window`) — OpenAI suspended the 5-hour limit for Plus/Pro/Business on 2026-07-12, so a weekly-only account puts its weekly limit in `primary_window` with no `secondary_window` at all |
| Reset field | `resets_at`, ISO-8601 with offset | `reset_at` (epoch **seconds**) preferred; `reset_after_seconds` + the current time as fallback |
| Phantom / not-started window | An omitted `resets_at` (never sent) | CCBG-30: OpenAI always sends a reset time, even for an untouched window — `used_percent == 0` **and** `reset_after_seconds >= limit_window_seconds - 1` (both required, and both fields must be **present** to test — an absent `used_percent` is never treated as 0, and the rule only ever fires off `reset_after_seconds`, never a length derived from `reset_at`) reads as "not started", `resetsAtMs` comes back `nil` |
| Model caps | `limits[].kind == "weekly_scoped"`, name from `scope.model.display_name` | `additional_rate_limits[].limit_name`; the cap's own window is picked from that entry's `primary_window`/`secondary_window` by length (604800), same classify-by-length rule as the top level |
| Missing/empty payload | No `limits`, no legacy siblings, no caps, no credits → `UsageParser.parse` returns `nil` (unusable) | No `rate_limit` key at all → `ChatGPTUsageParser.parse` returns an **empty, non-nil** `UsageData` — tolerant parsing, not a failure. `nil` is reserved for input that isn't valid JSON at all |

**What the fetch path must do with an empty result is the same either way, even though the
parsers disagree about `nil` vs. non-nil.** `UsageData.isEmpty` (`session == nil && weekly ==
nil && modelCaps.isEmpty && credits == nil`) is the single test a ChatGPT fetch path must run
on a parsed 200: `isEmpty == true` is treated exactly like Claude's `parse` returning `nil` —
`.invalidResponse`, keeping the last-good reading on screen, **never** a fresh, confidently
blank 0% success. Without this, a 200 that happened to carry no `rate_limit` (a transient
upstream shape, not "you have zero usage") would read as an ordinary success: `fetchedAtMs`
stamped, `lastStatusKind` cleared, and a signed-in-looking panel with nothing in it and no
notice explaining why (see the CCM-76 Core review, finding 3). This mapping is a seam pinned
here for whichever fetch path implements it; nothing in `UsageRepository` calls
`ChatGPTUsageParser` yet (Phase 1 is Core-only, §9.6).

Source: `UsageParser.swift` (Claude), `ChatGPTUsageParser.swift` (ChatGPT); cases in
`fixtures/usage/` and [`fixtures/chatgpt/`](fixtures/chatgpt/README.md).

---

## 2. Usage credits (pay-as-you-go)

Credits are **money**, not a rate limit, and are handled differently from windows.

- **Having a cap and having credits are different facts** (amended 2026-08-12, from Android's
  CCBG-9). A null limit beside real spend means the user switched the cap **off**, not that
  the credits vanished. The limit is nullable; a null limit is distinct from a zero limit.
- **Percent is computed from the money**, not taken from the server's rounded integer:
  `used_minor × 100 ÷ limit_minor`. When there is no positive limit — null *or* ≤ 0 —
  percent, percent-display and remaining are all **null, never a synthesised 0**: a 0% bar
  reads as headroom against a ceiling that doesn't exist.
- **Displayed percent rounds, it does not truncate.** Window bars truncate (`toInt()`), but
  money is precise: $5.99 of $100 must read **6%**, not 5%. So `0.51%` → `1%`, `99.99%` →
  `100%`.
- **Remaining is clamped at zero**, but **percent is not clamped** — spending $120 of a $100
  budget yields remaining `$0.00` and percent `120.0`.
- **The render gate is `hasLimit OR used > 0`** (`isReportable`) — a cap *or* actual spend
  earns the row; an account with neither must not see an empty one. The old rule, "hide when
  `limit_minor ≤ 0`", silently deleted an uncapped account's spend. Uncapped accounts render
  the spend plus **"No cap"**, with **no bar and no percent**.

### 2.1 Credits shape — Provider

CCM-76 [Accounts]: the two providers' credits are different *kinds* of fact, mapped onto the
same `SpendCredits` type additively — `balanceMinor`/`unlimited` are `nil`/`false` for every
Claude reading, and `usedMinor`/`limitMinor` are meaningless `0`/`nil` placeholders on a
ChatGPT reading.

| | Claude | ChatGPT |
|---|---|---|
| Shape | Spend **against a cap** — `usedMinor`/`limitMinor`, a percent computed from the two | A standing **balance** — `balanceMinor` only, no used/limit/percent at all |
| Presence gate | A `spend`/`extra_usage` block with a limit or nonzero spend | `has_credits == true`; `false` (or absent) → no `SpendCredits` at all |
| "Not worth reporting" signal | A null `limitMinor` beside zero spend (`isReportable == false`) | The `unlimited` flag — `isReportable == false` regardless of the balance |
| Render | "$5.99 of $100.00" or "$5.99 spent, no cap" | "$12.40 balance" — no bar, no percent, no used/limit copy |

Source: `Models.kt:26-56` (Claude), `ChatGPTUsageParser.creditsFrom` (ChatGPT).

### Money formatting

`symbol + amount`, where:

- `amount = minor_units ÷ 10^exponent`, `exponent` clamped to `0…6`
- Formatted with **exactly `exponent` decimal places** and thousands grouping
- Symbol resolved from the ISO currency code using **US locale conventions**; if the code is
  unrecognised, fall back to `"<CODE> "` (code plus a space)

Examples that must hold: `599/2/USD` → `$5.99`; `10000/2/USD` → `$100.00`; `9401/2/USD` →
`$94.01`; `957/2/USD` → `$9.57`.

Source: `Models.kt:26-56`, `Palette.kt:113-122`.

---

## 3. Burn-rate projection

For one window instance, estimate when usage will hit 100%.

**Inputs:** `(timestampMs, percent)` samples for the current window, any order, plus the
window's reset instant.

**Refuse to project** — return nothing — when any of these hold:

- Fewer than 2 samples
- Observed span < **20 minutes**
- Observed movement (last − first percent) < **1.0** percentage point
- The fitted rate is ≤ 0, non-finite, or the fit comes out flat/negative even though the
  endpoints rose (a burst followed by a plateau, or a decline)

"No honest projection to make" is a first-class outcome. Never show a guess instead.

**The rate is a least-squares fit over every sample**, not the first-to-last slope. With a
full window of history, one early burst followed by idle time would otherwise set the pace
for the rest of the window. Degenerate case: if the x-variance is zero, fall back to the
endpoint slope.

**The projection is anchored on the latest reading**, not on the fitted line, so the dashed
forecast tail joins the point the chart actually draws.

**Outputs:**

- `ratePctPerHour` = fitted rate × 3,600,000
- `hitsLimitAtMs` = `lastSampleTime + (100 − lastPercent) ÷ rate`, but **only if that lands
  between now and the reset**; otherwise nothing (the window resets before the limit is hit)
- `pctAtReset` = `lastPercent + rate × msUntilReset`, **capped at 100**

Cases in [`fixtures/projection/cases.json`](fixtures/projection/cases.json).

Source: `Projection.kt:14-88`.

---

## 3.1 Pace severity — naming where the window is heading

A percentage says where you are; this says where you end up. One ladder, shared by the pace
readout, the chart's notes and (Android already, Mac in CCM-19) the projection alerts — which
is what stops a client warning in words while its own chart draws something calmer.

**Inputs:** measured `usedPct`, `elapsedMs` into the window, the window length, and §3's
estimate (which may be absent).

**Two floors first, ahead of every verdict:**

| Floor | Rule | Reason |
|---|---|---|
| Young window | `elapsedMs < max(windowLength ÷ 100, 60_000)` | A 5-hour window at 2% after four minutes fits to 150%. Also the honest answer to post-reset carryover: the server can report the *previous* window's percent for a poll or two, and a one-minute-old window reading 100% is far more likely to be that than a genuinely exhausted one. |
| Minimum usage | `usedPct < 5.0` | Elapsed enough, but a few percent of a window extrapolates to noise. |

Then, first match wins:

| Severity | Rule |
|---|---|
| `spent` | `usedPct ≥ 99.5` (the same "exhausted" line §7.2 uses) |
| `runningOut` | `estimate.hitsLimitAtMs != nil` |
| `close` | `estimate.pctAtReset ≥ 85.0` — **inclusive** |
| `healthy` | otherwise |

**An absent estimate can still be `spent`, never `close` or `runningOut`.** No projection, no
verdict about the future — but "nothing left" needs no forecast to assert.

**No verdict is a stated outcome, with its reason.** Three suppressions —
`youngWindow` / `tooLittleUsage` / `noProjection` — and a client must phrase them
**differently**: a reader shown "too early in this window" four hours in would rightly call
it a bug. A client is free to word them as it likes, but the reason must reach the wording
layer rather than collapsing to a bare nil.

**The severity is not the pace verdict.** §5's dead zone and the above/below verdict are
about the even-pace diagonal — where you are *now* relative to even burn. The severity is
about the destination. Both exist, and neither replaces the other.

**A surface must not draw a forecast it refuses to name.** Where a severity is suppressed the
chart's projection tail is withheld too, and the note says which silence it is.

**Permitted client divergence, recorded so it isn't read as a bug:** with no estimate and
usage below the spent line, Android's `paceSeverity` returns `HEALTHY` while the Mac returns
`untracked(.noProjection)`. Theirs feeds alerts, where both answers mean "fire nothing";
the Mac's feeds copy, which has to tell the two apart. No alerting behaviour differs.

Cases in [`fixtures/projection/cases.json`](fixtures/projection/cases.json) → `severity`.

Source: `Projection.kt:105-160` (Android), `Projection.swift` "Pace severity" (Mac).

---

## 4. Binding samples to a window (CCBG-2)

A window is identified by its `resets_at` — but **never match it by equality.** The server
slides that timestamp forward on nearly every poll: one live 7-day window was measured
producing **561 distinct `resets_at` values across 672 samples**. Exact matching bound 1–2
samples out of hundreds, which silently killed the trend chart.

**Match by proximity instead.** A sample belongs to the window when
`|sample.resetsAt − window.resetsAt| ≤ windowLength ÷ 4`.

A quarter of the window is deliberately generous for drift while staying unambiguous: a
*genuine* reset moves `resets_at` by a full window length — four times the tolerance — so the
previous window can never leak into the current one.

Two further rules:

- **`resetsAt == 0` means "window not started"**, not "resets at the epoch". Never bind it.
- Samples with a null percent are skipped, not treated as zero.

Source: `Projection.kt:26-38, 90-131`.

---

## 5. Bar colour breakpoints

Usage bars use the user's theme colour normally, and escalate:

| Percent | Colour role | Light | Dark |
|---|---|---|---|
| `≥ 100` | red | `#C62828` | `#FF5252` |
| `> 90` | orange | `#F57C00` | `#FFA726` |
| `> 80` | yellow | `#F9A825` | `#FDD663` |
| otherwise | theme accent | — | — |

The warning hues are deliberately vivid (saturated orange, true red) so they never blend
into the muted terracotta of the default Claude Orange theme. A null percent counts as 0.

Any platform may re-map these to its own semantic colour system, but the **breakpoints
(80/90/100) and their strict/inclusive boundaries must not change** — two installs showing
different colours at 90.5% is a bug.

### Accent themes

Twelve named accents, each with a light and a dark variant, plus one platform-dynamic
pseudo-option. Every client ships the same twelve so the apps look like one product.

| Theme | Light | Dark |
|---|---|---|
| Claude orange (default) | `#D97757` | `#E59980` |
| Blue | `#1A73E8` | `#8AB4F8` |
| Indigo | `#3949AB` | `#9FA8DA` |
| Cyan | `#00ACC1` | `#4DD0E1` |
| Teal | `#00897B` | `#4DB6AC` |
| Green | `#188038` | `#81C995` |
| Amber | `#F9A825` | `#FDD663` |
| Deep orange | `#E64A19` | `#FF8A65` |
| Red | `#D93025` | `#F28B82` |
| Pink | `#D81B60` | `#F48FB1` |
| Purple | `#8E24AA` | `#CE93D8` |
| Brown | `#6D4C41` | `#BCAAA4` |

The dynamic option follows the OS: **Material You** on Android, the **system accent colour**
on macOS. It has no fixed value — each client resolves it at draw time.

### Always-on surfaces stay monochrome until 80%

Android's status-bar gauge icon and the Mac's menu-bar item are both system chrome, and both
platforms expect an image that adapts to the bar's own tint. So they render monochrome below
the first breakpoint and take colour only past it — **colour appearing at all is the
signal**. A client setting may disable colouring entirely; none may make it constant.

### The pace dead zone

Usage must sit more than **3.0 percentage points** from the even-pace line (§6) before it
counts as above or below it. Shared by the chart's wash and the pace readout so the two can
never disagree — without it, the verdict and its colour flip on every poll while usage
hovers on the line.

Source: `Palette.kt:22-63, 192-201`.

---

## 6. The even-pace line

Every chart draws the diagonal from 0% at the window's start to 100% at its reset. Staying
below it means finishing inside the limit; crossing it shades the overshoot amber.

`elapsedPercent = (windowLength − msUntilReset) ÷ windowLength × 100`, clamped to `0…100`.
Undefined when there is no reset instant or the window length is non-positive.

Source: `Palette.kt:192-201`.

How each platform draws the line, the overshoot and a spent window is presentation and lives in
that platform's design, not here.

---

## 7. History

Two separate stores with different retention. Keep them separate; they answer different
questions.

### 7.1 Raw samples — for the trend chart

One record per successful fetch: timestamp, session percent, session `resets_at`, weekly
percent, weekly `resets_at`.

- **Retention: 8 days**, so the 7-day window always has a full curve. Prune on every write.
- Writes must be **atomic** (temp file + rename), not appends — at 15-minute polls this is a
  few hundred short lines, so rewriting the pruned file is cheap and a torn append is not
  worth risking.
- History is **best-effort**: a failed write costs one data point and must never surface as
  an error.

### 7.2 Closed windows — for the history bars

One compact record per window that finished: kind (`session`/`weekly`), the window's
`resets_at` (its identity), peak percent, and whether it hit the limit.

- **Retention: 366 days.**
- **Idempotent:** writing the same (kind, `resets_at`) twice is a no-op.
- **`hitLimit` is `peak ≥ 99.5`**, not `== 100`.

### 7.3 Bars

- **A bar's start is derived, not stored:** `start = resetsAt − windowLength`.
- The still-open window is merged in as a "current" bar and **de-duplicated against the log
  by reset time** — a logged record with the same reset must not double up.
- Bars sort **newest first** by start time.
- Weeks group session bars by the **Monday of the calendar week the session started in**,
  in the **user's local zone**, newest week first. (Monday = ISO day 1.)

### 7.4 Clearing credentials must not clear history

Signing out is nearly always a re-sign-in of the *same* account, and wiping history made
that silent data loss (CCBG-1). Points self-identify by their window's `resets_at`, so
genuinely stale data ages out through the 8-day prune on its own.

Cases in [`fixtures/history/cases.json`](fixtures/history/cases.json).

Source: `HistoryStore.kt`, `SessionLog.kt`, `HistoryStats.kt`, `UsageRepository.kt:78-96`.

---

## 8. Reset detection

Which alerts a client posts — threshold warnings, reset pings, pace or expiry alerts — and how,
is a family feature: see `spec/mobile/` and the desktop repo's `spec/desktop/`. What every client
shares is the window bookkeeping, because history (§7) is built from it and two installs must
agree on when a window closed and how high it went.

**Detecting a reset:** the stored window key is non-zero, differs from the current key, and
lies in the past. On reset: write the closed window to the log (identity = the old key, peak
= what was accumulated), then re-seed the peak from the current percent. Otherwise the peak
accumulates as `max(storedPeak, currentPercent)`. Detection always runs, whether or not any
alert is enabled. Window identity is the window's `resets_at` (§1).

Source: `ResetRollover.kt` (Android).

---

## 9. Profiles

Two slots today, `personal` and `work`, each with a **user-editable label**. All persisted
keys, notification IDs, and cache entries are namespaced by the profile's stable key — never
by its label.

Notification IDs use fixed kinds 1–7 plus a per-model base of 10, offset by **+100** for the
work profile.

> **Forward warning.** CCRM-6 intends to replace the fixed two-slot enum with a dynamic
> profile registry (stable string keys + user labels). A new client should model profiles as
> **a list keyed by string from the start** rather than hard-coding two, so it doesn't inherit
> the migration. The `+100` ID scheme is Android-specific and need not be reproduced.

### 9.1 Visibility and the default profile

The common case is **one** account, not two — someone signs in to Work only and never
touches Personal. So:

- **A profile with no credentials is hidden entirely.** Never render an empty tab or blank
  bars for it.
- **The switcher disappears when fewer than two profiles are signed in.** A control with one
  option is worse than no control.
- **A user-set default decides which profile opens first.** If that profile isn't signed in
  — or names one that no longer exists — fall back to the first visible profile rather than
  opening on a blank tab.
- **When nothing is signed in, show a sign-in prompt**, not zeroed-out windows. Zero percent
  and "no data" are different states everywhere in this spec.

Source: `Profile.kt`, `Alerts.kt:274-275`; macOS `ProfileSelection.swift`.

### 9.2 The plan tag — CCM-68 [Accounts]

Each profile carries a plan tag read from `/api/oauth/profile` (API-CONTRACT §9): Free, Pro,
Max, Team Standard, Team Premium, Enterprise, or a raw passthrough for a shape not yet seen.
Read at most once a day per profile, and at most once per fetch — via the same fetch path as
usage, never a separate poller — on the first successful usage fetch or refusal, then daily,
forced (ahead of the daily gate) by a manual refresh or sign-in's own validation fetch. It
rides alongside the existing `rate_limit_tier`-derived multiplier (CCM-48): a parseable tier
still only ever *appends* to a known plan ("Max · 5x"); it is never invented, and a tag with
no plan is never shown as one.

`PlanTag.isBlockedPlan(tag)` — true exactly when the tag is `"Free"` — decides whether a 200
usage reading contradicts what the profile just said (§10.1's "a 200 next to Free parks
instead of showing" case). It is **not** what gates polling or alerting for a parked profile,
though: `ProfileState.usageRefused` is, because an org admin can disable OAuth app access on
any plan, not only Free, and a Pro/Max/Team/Enterprise account parked for that reason must
stop polling and alerting exactly the same way a Free one does.

Cases in [`fixtures/profile/`](fixtures/profile/README.md).

Source: `data/source/ClaudePlan.kt`, `UsageRepository.kt:515-542` (Android); `PlanTag.swift`
(Mac).

#### Plan tag source — Provider (CCM-76 [Accounts])

| | Claude | ChatGPT |
|---|---|---|
| Read from | A separate call, `/api/oauth/profile` — `organization_type` / `seat_tier` / `account.has_claude_max`/`has_claude_pro` | The **same** usage response's own `plan_type` field — no separate call |
| Mapping | Free/Pro/Max/Team Standard/Team Premium/Enterprise, or the raw organisation type capitalized once | `"plus"` → "Plus", `"pro"` → "Pro", `"team"`/`"business"` → "Team", `"free"` → "Free", anything else → capitalised raw |
| Entry point | `PlanTag.parse` | `PlanTag.fromChatGPT` / `ChatGPTUsageParser.planTag` |

Cases in [`fixtures/chatgpt/`](fixtures/chatgpt/README.md).

### 9.3–9.5 — desktop layer

The account registry (§9.3), discovered Claude Code credentials (§9.4) and "which account the
menu bar shows" (§9.5) are desktop-only. They live in the desktop repo's `spec/desktop/`,
not in this core spec; the numbers are kept so references stay stable.

### 9.6 ChatGPT provider — read-only guarantee — CCM-76 [Accounts]

Every client reading ChatGPT usage, whatever its credential source, follows these rules (how a
desktop client discovers a local Codex login is in the desktop layer). The client calls exactly one
endpoint, GET only — `https://chatgpt.com/backend-api/wham/usage` — and no other ChatGPT
endpoint is ever called. The request neither sends nor stores cookies — `chatgpt.com` is a
browser domain, and a default HTTP stack would both attach any cookie already in its shared
store and persist any `Set-Cookie` the response sends back.

**Persisted state must not carry the response's identity fields.** Claude's usage body never
sends `email`/`user_id`/`account_id`; ChatGPT's does. Any client that saves a response body to
disk (a last-good copy restored on relaunch, a debug capture) strips those three keys first —
never the raw body.

Source: `ChatGptSource.kt` (Android); `ChatGPTCredentials.swift`,
`APIClient.fetchChatGPTUsage` (Mac).

---

## 10. Fetch outcomes

Every fetch resolves to exactly one outcome, and the user-visible status line reflects it:

| Outcome | When |
|---|---|
| Success | 200 and the payload parsed |
| Too soon | Manual refresh inside the 60 s floor (from the last check, success or not — CCM-165 [Readout]); automatic checks inside 180 s of the last success |
| Backed off | Inside the 429 backoff window (background only; a manual tap bypasses it) |
| Rate limited | 429 received; backoff bumped |
| Auth needed | Refresh failed terminally, or the 6-hour stuck-refresh streak elapsed |
| No credentials | No token for this profile |
| Network | A thrown transport failure (`URLError`, DNS, TLS, …) — CCM-69 [Panel] |
| Server | Any HTTP status not otherwise recognised (usually 5xx) — CCM-69 [Panel] |
| Invalid response | 200, but the body doesn't parse — the schema moved — CCM-69 [Panel] |
| Error | The internal fallback for anything none of the above categorise |
| Plan | Parked — the endpoint refused this app's OAuth access, most often for Free — §10.1 |

A manual refresh **may** bypass the backoff gate; a background poll may not.

Source: `UsageRepository.kt:17-26, 234-289`.

### 10.0 The error taxonomy — CCM-69 [Panel], port of Android's `ErrorKind.kt`

`FetchOutcome` (raw outcome) and the taxonomy (what the user sees) are deliberately separate:
`ErrorTaxonomy.kind(for:)` reduces the outcome to one of seven **kinds** — `auth`,
`rateLimited`, `network`, `server`, `invalidResponse`, `internal`, `plan` — and
`ErrorTaxonomy.notice(forKind:lastStatus:)` supplies the fixed, per-kind copy every surface
reads: a `title` that **names the fix**, a `shortLabel` (≤24 characters, for the popover's
compact pill and a widget caption), an `evidence` line (the per-instance detail — an HTTP
code, a raw error description — reused verbatim from `ProfileState.lastStatus`, which
`UsageRepository.fetch` already stamps with exactly this string at each of these outcomes), a
`tone` (`red`/`amber`/`quiet`), and an `action` (`openSettings`/`checkStatus`/
`seeClaudePlans`/`checkForUpdates`/`none`) that the view maps to a button or link, never a raw
string it decides how to route itself.

| Kind | Title (names the fix) | Tone | Action |
|---|---|---|---|
| `auth` | Sign-in stopped working — re-sign in from Accounts. | red | Go to Accounts |
| `rateLimited` | Rate limited — backing off, retries on its own. | amber | none (self-resolving) |
| `network` | Couldn't reach Anthropic / OpenAI (the account's service, CCM-126 [Readout]) — check your connection, or see if it's them. | amber | Check status |
| `server` | Anthropic's / OpenAI's server errored — usually theirs, usually brief. | amber | Check status |
| `invalidResponse` | The server answered in a shape this app doesn't know — an app update may be needed. | amber | Check for updates |
| `internal` | Something unexpected went wrong in the app. | red | none |
| `plan` | `FetchOutcome.parkedCopy(planTag:)` — §10.1's own copy | quiet | Free → See Claude plans; else none |

`network` and `server` are deliberately both amber, matching Android — only `auth` and
`internal` are red on either platform; a network hiccup and a 5xx are both transient and
neither is the user's fault or the app's bug, so splitting them by colour would invent a
severity distinction the copy doesn't support. `plan` keeps the already-shipped quiet
secondary-text styling (§10.1) rather than joining the amber/red pill — it's the one kind that
isn't a fetch *failure*, an accurate, correctly identified terminal state.

**`stale` is not a kind.** It's an overlay computed from `fetchedAtMs` age at render time
(`ErrorTaxonomy.staleNotice`), independent of whatever the last fetch's outcome was — a profile
can be stale *and* currently rate-limited, and folding staleness into `FetchOutcome` would
force one outcome to represent two independent facts. The stale pill/notice wins the display
only when there's no sharper-typed error to show; an active kind (rate-limited included, since
that notice already explains why the reading is aging) always takes precedence. The threshold
is the same `RateLimits.stuckRefreshMs` (six hours) `WidgetContent.FaceState.stale` already
uses, so the two can never disagree about "old".

**`ProfileState.lastStatusKind`** persists the kind alongside `lastStatus` so it survives a
relaunch. A subtlety the CCM-67/68 review of `d8f767d` surfaced: `usageRefused` is a sticky
flag `park()` clears only once a usage fetch actually succeeds again, so it can still read
`true` on the very call a fresh auth failure has already superseded the park (the proactive
renewal check runs *before* the parked-profile branch in `UsageRepository.fetch`). Auth always
wins that race — a `.auth`-kind `lastStatusKind` must never be masked by the quiet parked
line — and `PopoverBuilder.build`'s `isParked` encodes exactly that: `usageRefused &&
lastStatusKind != .auth`.

How each platform shows a notice — panel, widget caption, menu-bar glyph, pinned-notification
strip — is presentation, in that platform's design.

Source: `ErrorKind.kt`, `MainActivity.kt:826` (Android); `ErrorTaxonomy.swift`,
`PopoverContent.swift`, `UsagePane.swift`, `WidgetContent.swift` (Mac).

### 10.0.1 Request shape and auth-failure codes — Provider (CCM-76 [Accounts])

| | Claude | ChatGPT |
|---|---|---|
| Endpoint | `GET https://api.anthropic.com/api/oauth/usage` | `GET https://chatgpt.com/backend-api/wham/usage` |
| Auth-failure codes | 401 only (403 with `oauth_not_allowed_for_organization` is the separate `plan`/parked outcome, §10.1 — any other 403 falls into the generic HTTP bucket) | 401 **or** 403 → `.authNeeded`, both fold into the same auth kind — ChatGPT has no parked/plan-refusal outcome to carve 403 out for |
| Rate limit code | 429 → `.rateLimited` | 429 → `.rateLimited` — unchanged |
| `User-Agent` | `Cooldown/<version> (<platform>)` (CCM-72) | Same value, same rule — no provider-specific User-Agent |
| Account-id header | None | `ChatGPT-Account-Id: <account_id>` sent **only** when the account id is non-empty — never an empty header |
| Extra headers | `anthropic-beta`, `Content-Type: application/json` | `Accept: application/json` |
| Cookies | N/A (no cookie-bearing domain) | `httpShouldHandleCookies = false` — `chatgpt.com` is a browser domain; the request carries its own bearer token and must neither send nor accept cookies (§9.6) |

Source: `APIClient.swift` (`fetchUsage`, `chatGPTUsageRequest`), `ChatGPTFetchOutcome.classify`.

### 10.1 The `plan` outcome — "parked" — CCM-67 [Readout], port of Android's CCBG-27

Anthropic's usage endpoint answers HTTP 403 `oauth_not_allowed_for_organization` when this
app cannot read usage for the account — Free-plan organisations always, but the same code
also covers an org admin disabling OAuth app access on a *paid* plan, which the code alone
cannot distinguish from Free. Parking must therefore key on the refusal itself
(`ProfileState.usageRefused`), never on `PlanTag.isBlockedPlan(planTag) == "Free"` — a
non-Free tag does not mean the app can read usage, and using it as the gate is precisely what
let a permanently-refused non-Free account re-hit the usage endpoint (and re-403) on every
single poll forever, before this was fixed.

A profile becomes (or stays) parked whenever a fetch confirms the refusal — the 403 itself,
or, for a genuine contradiction, a 200 usage reading landing next to a profile that just said
Free (Anthropic 403s Free organisations, so a 200 beside a Free tag is distrusted rather than
shown). Parking:

- **Clears cached usage for that profile** — the current reading, whatever the popover would
  otherwise keep showing as last-good, **and the window-tracking slices** (`windowPeak`,
  `lastSeenWindowKey`) that would otherwise read the first post-recovery fetch as a bogus
  rollover of a window parking never really closed, firing a false reset ping and logging a
  session record that never happened. Mirrors what `completeSignIn`'s account-mismatch wipe
  clears, for the same reason. **History files are untouched** — this is a live-reading clear,
  not a data loss, and the trend picks back up unbroken the moment usage resumes.
- **Stops polling the usage endpoint for that profile.** Every fetch instead checks whether
  today's plan-tag read is due (§9.2's cadence); within the day, nothing is read at all and
  the profile just stays parked. The moment a due read no longer reads as blocked, the usage
  fetch is attempted again in that same call — which is what actually proves recovery, since a
  non-Free tag alone doesn't rule out the org still refusing the app.
- **Carries copy chosen from the resolved plan**, not a fixed string: Free gets
  *"Claude doesn't report usage on the Free plan — upgrade to Pro, Max or Team."*
  (`FetchOutcome.freePlanCopy`); any other confirmed plan names itself, *"Claude isn't
  allowing this app to read usage for the `<Plan>` plan."*; a plan that couldn't be confirmed
  this call says so honestly rather than guessing, *"Claude isn't allowing this app to read
  usage for this account's plan."* (`FetchOutcome.parkedCopy(planTag:)` on Mac.)
- **Suppresses reset pings, threshold alerts and pace alerts**, gated on `usageRefused` — not
  the tag — for the same reason parking itself is: there is no window to ping, threshold, or
  project against. **Withdraws a standing stale notification** on the transition into this
  state rather than leaving it to linger through a poll that will never come, and suppresses
  new ones going forward — the absence is the plan (or the refusal), not a broken poll, so
  staleness must not say so twice (Android's `Conditions.kt` `plan()`, `ErrorKind.PLAN`).

An HTTP 403 whose body doesn't match the refusal's error code is **not** parked — it lands in
the generic HTTP error bucket exactly like any other unrecognised status.

The state is **per profile**, keyed the same as everything else in §9, and is terminal until
the refusal stops — there is no timeout or retry-count escape.

The copy only ever names a plan when `ProfileState.planTagResolvedAtMs` is at or after
`parkedAtMs` — i.e. the tag on record was **successfully read** (a 200 that parsed) during or
after this park began. This is deliberately a separate stamp from `planTagCheckedAtMs`, which
marks any *completed* round trip including a failed one: comparing against `planTagCheckedAtMs`
let a re-check that merely *attempted* and failed (a 500, an unparseable body) read as a fresh
confirmation and resurrect a stale plan name — found in review of `d8f767d`, fixed alongside
CCM-69 [Panel].

The widget's small/medium faces get their own `WidgetContent.FaceState.parked(message:)` — the
same `FetchOutcome.parkedCopy(planTag:)` string the popover/Usage-pane show, read straight off
the snapshot's `lastStatus`. `WidgetContentBuilder.build` checks
`profile.lastStatusKind == .plan` **ahead of** the `hasPercent` branch: `park()` clears the
cached percent, so without that ordering a parked profile fails `hasPercent` first and lands on
`.noData`'s fixed "No data yet · Waiting for first fetch" — indistinguishable from a fresh
sign-in that has never fetched at all (the CCM-67 review finding CCM-69 [Panel] fixes).

Cases in [`fixtures/profile/403-plan-refusal.json`](fixtures/profile/README.md).

Source: `UsageRepository.kt:410-448, 476-497`, `UsageCache.kt:629-637` (`clearUsage`),
`notify/Conditions.kt:195-208`, `data/ErrorKind.kt:33-38` (Android); `UsageRepository.swift`,
`AlertEngine.swift`, `ProfileState.swift` (Mac).

---

## 11. Ledger — desktop layer

Reading local Claude Code transcripts and Codex logs, pricing them and bucketing the result is
desktop-only (a phone has no such files). This section lives in the desktop repo's
`spec/desktop/`.
