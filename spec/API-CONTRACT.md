# Anthropic API contract (undocumented)

Everything here is **reverse-engineered and unsanctioned**. None of it is published or
supported by Anthropic, and any of it can break without notice. Verified against Claude
Code 2.1.214 and a live Pro/Max/Team account; dates on each finding say when.

> **Posture.** These endpoints are reached with a consumer OAuth token, using Claude Code's
> registered client ID. Since CCM-72 [Foundation] (2026-09-20) the usage/profile endpoints get
> the app's own honest User-Agent, not Claude Code's — verified live that the endpoint does not
> gate on it (see §2). The client ID is still Claude Code's own, which is still impersonation of
> the official CLI in that one respect. It is why CCooldown is sideload/direct-download only and
> must never be submitted to the App Store, Play Store, or Mac App Store. Any new client
> inherits this constraint. See the README's risk notice.

---

## 1. Endpoints

| Purpose | Method + URL |
|---|---|
| Read usage | `GET https://api.anthropic.com/api/oauth/usage` |
| Read the plan tag | `GET https://api.anthropic.com/api/oauth/profile` |
| Authorize (browser) | `https://claude.com/cai/oauth/authorize` |
| Token exchange + refresh | `POST https://platform.claude.com/v1/oauth/token` |
| OAuth redirect target | `https://platform.claude.com/oauth/code/callback` |
| Start a 5-hour window ("ping") | `POST https://api.anthropic.com/v1/messages` |

Shared constants:

- **Client ID:** `9d1c250a-e61b-44d9-88ed-5944d1962f5e` (Claude Code's own)
- **Beta header:** `anthropic-beta: oauth-2025-04-20`

**2026 migration (verified against Claude Code 2.1.214's own `claude auth login`).** The
authorize endpoint moved to `claude.com/cai/oauth/authorize` and the callback host to
`platform.claude.com`. The legacy `claude.ai/oauth/authorize` still renders a consent page
but can no longer complete a grant — it fails at submit with `Invalid request format`. If
sign-in breaks in the future, re-capture the CLI's real URL before assuming anything else.

Source: `ApiClient.kt:26-32`, `OAuthSignIn.kt:24-30`.

---

## 2. The User-Agent rule — read this first

**Token endpoint: never a claude-code UA. Usage endpoint: our own honest UA — verified
2026-09-20 that it does not gate on User-Agent at all** (`ua-probe`, CCM-72 [Foundation]). The
two endpoints used to be documented as wanting *opposite* User-Agents on the theory that the
usage endpoint's rate-limited bucket depended on seeing `claude-code`; that theory was never
actually tested against the live endpoint until now, and it was wrong.

| Endpoint | User-Agent | Consequence of getting it wrong |
|---|---|---|
| `api.anthropic.com/api/oauth/usage` | **Send** `Cooldown/<version> (macOS)` — our own honest UA | None observed; verified not to gate on UA (see below) |
| `api.anthropic.com/api/oauth/profile` | Same as usage (§9) | Same |
| `platform.claude.com/v1/oauth/token` | **Must NOT** send a `claude-code` UA | Its WAF returns an opaque `429 rate_limit_error` |
| `api.anthropic.com/v1/messages` | Irrelevant — no gate | None |

> **Evidence, 2026-09-20 (`swift run ua-probe`, live probe, both signed-in profiles, `GET
> api.anthropic.com/api/oauth/usage` with `anthropic-beta: oauth-2025-04-20`):**
>
> ```
> Personal  claude-code (control)  status=200  parsed
> Personal  Cooldown/dev           status=200  parsed
> Personal  no User-Agent          status=200  parsed
> Work      claude-code (control)  status=200  parsed
> Work      Cooldown/dev           status=200  parsed
> Work      no User-Agent          status=200  parsed
> ```
>
> All six combinations returned `200` with a parseable body. The usage endpoint does not gate
> on User-Agent at all — the "send claude-code or get rate-limited" claim in earlier versions
> of this document was unverified and is retired. `APIClient.swift` now sends
> `Cooldown/<version> (macOS)` on both the usage and profile requests. Android reached the same
> conclusion first (CCRM-68) and already sends `Cooldown/<version> (Android)`; both clients now
> match. The old `claude-code/<version>` value is kept in `APIClient.claudeCodeUserAgent` only
> as `ua-probe`'s control arm — it is not sent by the shipping app.

The token endpoint's WAF **blocks** User-Agents that are `claude-code`, browser-like
(`Mozilla`), `curl`, or **empty**, and **allows** library UAs (`okhttp`, `axios`,
`anthropic-sdk-typescript`). The Android client simply omits the header and lets its HTTP
library send its own default.

> **Porting note.** "Send no User-Agent header" is not portable — it depends on your HTTP
> stack's default, and `URLSession` substitutes its own. Set an explicit library-shaped UA on
> the token request rather than trusting a platform default. An empty UA is known-blocked, so
> never clear the header.
>
> **Verified on macOS 2026-07-31:** an explicit `CCooldown/1.0` on the token endpoint is
> accepted — a real authorization-code exchange returned 200 from a Team account, and the app
> has been polling successfully since.
>
> That widens what we know about the gate. The 2026-07-20 probes showed *library* names
> (`okhttp`, `axios`) passing; a plain `name/version` string the WAF has never seen passes too.
> So the rule isn't an allow-list of known libraries — it rejects **recognisable** shapes
> (`claude-code`, browser-like `Mozilla`, `curl`) and **empty**, and lets other things through.

Verified 2026-07-20 by capturing Claude Code's own successful (200) exchange, which uses
axios, plus User-Agent isolation probes. Source: `ApiClient.kt:13-19, 84-92`.

The `claude-code` version string was historically a knob to bump occasionally to a recent real
Claude Code release (`ApiClient.kt:23`, current value `claude-code/2.1.214`). Since CCM-72
[Foundation] neither client sends it on the usage/profile endpoints any more — it survives on
the Mac side only as `APIClient.claudeCodeUserAgent`, `ua-probe`'s fixed control value.

---

## 3. Reading usage

```
GET https://api.anthropic.com/api/oauth/usage
Authorization: Bearer <access_token>
anthropic-beta: oauth-2025-04-20
User-Agent: Cooldown/<version> (macOS)
Content-Type: application/json
```

Timeouts used: 20s connect, 20s read.

### Response schema

Undocumented, carries transient experiment fields, and varies by plan. **Treat every field
as optional and ignore everything unrecognised.** Two shapes coexist for both windows and
credits; prefer the newer one and fall back.

**Windows — prefer `limits[]`, fall back to `five_hour` / `seven_day`.**

`limits[]` entries carry a server-computed percent and severity:

| Field | Meaning |
|---|---|
| `kind` | `session` (5-hour), `weekly_all` (7-day all models), `weekly_scoped` (per-model cap) |
| `percent` | 0–100. May be `null` |
| `resets_at` | ISO-8601 with offset, e.g. `2026-07-27T11:49:59.956769+00:00` |
| `severity` | `normal` / `warning` / `critical`. Empty string means absent |
| `scope.model.display_name` | Model name, for `weekly_scoped` only. Default to `"Model"` when absent |

The legacy blocks use a **different key name for the same number**: `five_hour.utilization`
and `seven_day.utilization`, already on a 0–100 scale (`30.0` means 30%, not 3000%). These
blocks carry no severity. `seven_day_opus` and other `*_opus` keys are frequently `null`.

**Model caps fall back too** (amended 2026-08-12, from Android's CCBG-8 — Mac CCM-45).
When the `limits` array produced no `weekly_scoped` caps, read the flat siblings
`seven_day_sonnet` and `seven_day_opus` — the same block shape as `seven_day` — naming them
`"Sonnet"` and `"Opus"`, in that order. Only those two keys: `seven_day_sonnet` is attested
by OpenQuota reading it, `seven_day_opus` is null-present in captured payloads; any other
sibling would be a guess. A `null` flat key must never materialise a cap. Without this
fallback a server-side rollback of the `limits` shape silently turns the caps into an empty
list — indistinguishable from an account that has no caps. Fixture:
[`fixtures/usage/legacy-model-caps.json`](fixtures/usage/legacy-model-caps.json).

**Credits (pay-as-you-go) — prefer `spend`, fall back to `extra_usage`.**

`spend` reports money in **minor units with an exponent** (`amount_minor: 599`,
`exponent: 2` → $5.99):

- `spend.used.amount_minor`, `spend.limit.amount_minor`
- `exponent` from `limit`, else `used`, else default `2`
- `currency` from `limit`, else `used`, else default `"USD"`
- `spend.severity`

`extra_usage` is the looser fallback: `used_credits` (a float in minor units),
`monthly_limit` (minor units), `decimal_places` (→ exponent), `currency`.

**A null limit means the cap is off, not that the credits are gone** (amended 2026-08-12,
from Android's CCBG-9 — the user can switch the monthly limit off in their account settings).
In both shapes — `spend.limit: null` and `extra_usage.monthly_limit: null` — keep the spend
and parse the limit as null. Report no credits only when there is neither a limit nor any
spend. Render rules for the uncapped case are in BEHAVIOR-SPEC §2. Fixture:
[`fixtures/usage/credits-uncapped.json`](fixtures/usage/credits-uncapped.json).

Ignore `spend.percent`: it is server-rounded. Compute from the money (see BEHAVIOR-SPEC §2).

**Parse failure rule.** If, after both fallbacks, there is no session window, no weekly
window, no model caps *and* no credits, the payload is unusable — return nothing rather than
an empty success. Any exception during parsing is also "unusable", never a crash.

Source: `Models.kt:63-155`, `ApiClient.kt:41-54`. Captured payload:
[`fixtures/usage/pro-with-credits.json`](fixtures/usage/pro-with-credits.json).

---

## 4. Sign-in — PKCE authorization code

The user's token family is minted **on the device**, so no other machine shares the refresh
token and nothing external can rotate it away. This is a deliberate design property: it
removes the "re-paste every day" failures of copying a desktop token. Do not move token
minting to a server in any future client.

### 4.1 Authorize URL

Build it **by hand**, in this exact parameter order and encoding. Both matter: a subtly
different request is accepted at the consent page and then rejected at grant-submit with
`Invalid request format`.

```
https://claude.com/cai/oauth/authorize
  ?code=true
  &client_id=<enc CLIENT_ID>
  &response_type=code
  &redirect_uri=<enc REDIRECT_URI>
  &scope=<enc SCOPE>
  &code_challenge=<enc BASE64URL(SHA256(verifier))>
  &code_challenge_method=S256
  &state=<enc state>
```

**Encoding is form-encoding, not URL-path encoding**: spaces become `+` and `:` becomes
`%3A`. A generic URL builder that emits `%20` for spaces will fail. Use the equivalent of
Java's `URLEncoder.encode(value, "UTF-8")`.

**`state` IS the verifier** — the same value, not an independent random string. This is what
Claude Code and opencode do. A separate shorter state (16 bytes) was the final deviation
that still drew `Invalid request format`. On-device, the marginal CSRF benefit does not
justify diverging from the request that works.

- **Verifier:** 32 random bytes, base64url, no padding.
- **Challenge:** SHA-256 of the verifier's US-ASCII bytes, base64url, no padding.
- **Scope — send this set verbatim:**

  ```
  org:create_api_key user:profile user:inference user:sessions:claude_code user:mcp_servers user:file_upload
  ```

  Only `user:profile` is actually needed for usage, but a **reduced set is rejected at grant
  time** with `Invalid request format`. It must match the client's registered set exactly.
  `org:create_api_key` is silently dropped on Team accounts that disable member API keys —
  harmless there, required for Pro/Max.

The verifier and state must be **persisted before the browser trip** and survive process
death, since the user leaves the app.

Source: `OAuthSignIn.kt:31-46, 66-90`.

### 4.2 What the user pastes back

The callback page displays a code. The user copies it into the app. The pasted string is
`code#state` — split on the first `#`:

- Before `#` → the authorization code
- After `#` → the returned state, which **must equal** the persisted state if non-empty;
  mismatch means the code is from a different sign-in attempt
- No `#` → treat the whole string as the code and skip the state check

Source: `UsageRepository.kt:191-201`.

### 4.3 Token exchange

```
POST https://platform.claude.com/v1/oauth/token
Content-Type: application/json
anthropic-beta: oauth-2025-04-20
(no claude-code User-Agent — see §2)

{"grant_type":"authorization_code","code":"…","state":"…",
 "client_id":"…","redirect_uri":"…","code_verifier":"…"}
```

**JSON body, not form-encoded** — this is what Claude's own SDK posts. `redirect_uri` must
match the one used at authorize time.

### 4.4 Refresh

```
POST https://platform.claude.com/v1/oauth/token
{"grant_type":"refresh_token","refresh_token":"…","client_id":"…"}
```

Same headers and same User-Agent rule.

### 4.5 Token response

- `access_token` — required; empty means failure even on HTTP 200
- `refresh_token` — on refresh this may be **absent**, meaning "keep the existing one". When
  present and different, the family has rotated
- `expires_in` — seconds. Convert to an absolute instant. `0`/absent → unknown expiry
- `subscriptionType` — `pro` / `max` / `team`, when present
- `account` — **observed, not guaranteed** (macOS 2026-08-07): an object carrying `uuid` and
  `email_address` on the authorization-code exchange. The Mac client reads it best-effort to
  detect a different account signing into a slot (CCM-40 [Accounts]) and **never deletes data
  on its absence** — if the field disappears or is renamed, that detection silently goes inert
  rather than destructive. Not read on refresh: renewal is same-account by construction.

Source: `UsageRepository.kt:203-232, 291-330`.

### 4.6 Token family lifetime

The authorization-code family is capped at roughly **30 days** from sign-in. The token
response does **not** carry this, so it is an estimate used to prompt re-sign-in — present
it as approximate, never as fact.

Rotation semantics differ by how the token arrived, and this distinction matters:

- **Native (on-device) sign-in:** rotation is healthy self-renewal. The ~30-day clock still
  runs from the original sign-in, so keep the estimate.
- **Pasted desktop token:** rotation means the family may have moved elsewhere. The exact
  expiry from the pasted JSON no longer applies — clear it.

Source: `OAuthSignIn.kt:41-44`, `UsageRepository.kt:318-329`.

---

## 5. Errors and what they actually mean

| Status | Endpoint | Meaning | Action |
|---|---|---|---|
| `401` | usage | Access token expired | Refresh **once**, retry the fetch once. Still 401 → auth failure |
| `403` | usage | `error.details.error_code == "oauth_not_allowed_for_organization"` — most often a Free-plan organisation, but an org admin can disable OAuth app access on *any* plan, so this code alone never names the plan | CCM-67 [Readout]: park — clear cached usage, stop polling usage, poll `/api/oauth/profile` instead (§9, same daily gate) to confirm which plan, if it's due this call |
| `403` | usage | Any other body, or one that doesn't parse as the refusal above | Generic HTTP error — `"HTTP 403"`, exactly like any other unrecognised status; never parked |
| `429` | usage | Genuine rate limiting | Exponential backoff (§6) |
| `429` | token | **Ambiguous** — either the WAF UA gate (§2) or a code/token the server won't accept | Do not read it as throttling |
| `400`–`403` | token | Refresh token is dead (revoked or rotated away) | Require re-auth |
| `200` + unparseable | usage | Schema shifted | Record "unrecognized response shape"; do not treat as success |

**The token endpoint 429s on dead tokens as an anti-enumeration measure.** That makes a
permanently dead refresh token look like a transient blip. Handle it by tracking the failure
streak: only after renewals have failed continuously for **6 hours** should the client
declare re-auth needed. Before that, keep retrying and report "will retry".

For diagnosability, surface the server's own `error.message` (or `error`) plus the code
length on a failed exchange — the HTTP status alone does not say why.

Source: `UsageRepository.kt:246-289, 332-350`.

---

## 6. Rate limits, backoff, and polling cadence

Reading usage does not consume quota, but the endpoint is not free to hammer.

- **Background poll interval:** a setting, `backgroundCheckMinutes` (CCM-178 [Readout]) — every
  **5** (default), 10, 15 or 30 minutes, app-wide, never below 5; any other stored value reads as
  5. Settings › General › "Checking usage" › "Background check". 5 minutes per account was
  CCM-175 [Readout]'s fixed value (was 15, and 5 only for the menu bar's account — CCM-164).
  Every signed-in account with Online on is covered, local ones included, and is skipped while
  it has a fetch under one interval old or a live reading under **5 minutes** old (the live
  window does not follow the setting) — so in practice an account with a working terminal status
  line is not polled while Claude Code runs. The interval counts from the last success and from
  the last attempt (`RefreshPolicy.Input.backgroundCheckMs`, used by the timed reason only). A
  change takes effect on the next one-minute tick and fires no check itself. The activity floor,
  the trailing fetch, the 3-minute open/switch floor and the manual floor do not follow it.
- **Activity floor:** 60 seconds since the later of the last success and the last attempt, per account (CCM-175 [Readout];
  was 300 s). A settled burst in an attached Claude Code folder fetches only the account that
  folder belongs to, never every account, and not while that account has a live reading
  fresher than 60 s. Respects the 429 backoff.
- **Trailing fetch (CCM-175 [Readout]):** a settle refused by the 60 s activity floor is not
  dropped — it arms one fetch for that account at the floor's end (the later of the last success
  and the last attempt, plus 60 s), so a burst's last reply is read within a minute rather than
  at the next 5-minute check. Coalesced: further settles while one is armed add nothing. Any
  fetch for that account that starts in the meantime (activity, the background check, a manual
  tap, a popover open) cancels it. When it fires it is decided again like a settle: the activity
  toggle, a fresh live reading, the 429 backoff and the automatic budget below all apply.
  `ActivityFetchPlan` (Core).
- **Automatic budget (CCM-175 [Readout]; Astra C1-1):** at most **30 automatic fetches per
  account in any rolling hour**, counted across activity settles, trailing fetches and the
  background check (CCM-177's turn-ended trigger joins it). Manual taps (footer, widget) and the
  view-driven checks (launch, popover opened, account switched, Usage pane shown) are neither
  counted nor limited — each already has its own floor and a person behind it, and background
  activity must never spend the check someone opened the popover for. The background check is
  never refused by the budget, so no account goes stale; instead a fast fetch (activity,
  trailing) is allowed only if every rolling hour would stay at or under 30 even with the
  background check then firing every 5 minutes — the setting's minimum, whatever interval is
  chosen (CCM-178 [Readout]), so a switch from 30 to 5 minutes cannot push an hour over 30. In non-stop use that is 23 fetches a minute
  apart (about 20 minutes), then the 5-minute cadence until older fetches age out of the hour.
  Attempt times are held in memory only; a relaunch starts every account's budget empty (the
  60 s floor still holds across a relaunch through the persisted last-attempt time).
  `AutomaticFetchBudget` (Core).
- **Manual refresh floor:** 60 seconds from the last check — the later of the last
  *success* and the last attempt, so a failed or 429'd attempt starts it too (CCM-165
  [Readout]). A manual tap inside that window is refused, not queued, and the popover draws
  the control disabled with a countdown. A tap past it still bypasses the 429 backoff.
- **Automatic check floor:** 180 seconds since the last *success*, for the launch check and
  the popover-open / account-switch / Usage-pane checks. Community-derived safe floor.
- **429 backoff:** `5 min × 2^n`, where `n` is the consecutive-429 count, capped at
  **60 minutes**. Reset the counter on success.
- **Access-token pre-refresh margin:** refresh proactively when the token expires within
  **5 minutes**, rather than waiting for a 401.
- **Serialise fetches.** All fetches share one mutex so two profiles or a manual tap and a
  background poll cannot race on token rotation. A rotated refresh token must be persisted
  immediately.

Source: `UsageCache.kt:78-95`, `UsageRepository.kt:35-42, 234-244`.

---

## 7. Starting a 5-hour window ("ping")

Findings from the CCRM-17 spike, 2026-07-30. **Verified working**, but the feature is not
shipped on any platform yet, and it is a deliberately different posture — it *consumes*
subscription inference rather than only reading telemetry. Ship it opt-in and off by
default, if at all.

Reading usage does **not** start a window; only a billed inference call does.

```
POST https://api.anthropic.com/v1/messages
Authorization: Bearer <access_token>
anthropic-beta: oauth-2025-04-20
anthropic-version: 2023-06-01
{"model":"<cheapest>","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}
```

What the spike established:

1. **`user:inference` is honoured for a third-party caller** — a real ping returned **HTTP
   200** with a normal `message` object, `stop_reason: max_tokens`. Unlike the `setup-token`
   route, which 403'd. The scope set in §4.1 already requests it, so no re-sign-in is needed.
2. **No User-Agent gate on this endpoint.** Bogus-bearer probes across `claude-code/2.1.214`,
   `okhttp/5.4.0`, curl-default and empty UAs all returned an identical clean
   `401 authentication_error` (`"OAuth access token is invalid."`) — i.e. every shape reached
   real auth validation rather than the WAF's opaque 429. `anthropic-beta` made no
   difference either way.
3. **The Claude Code system preamble is not required.** A plain body succeeded first try.
   Don't send it.
4. **Cost is invisible** — 8 input tokens, 1 output; `percent` read 55 both before and
   after, below reporting granularity.
5. **Windows do not round to the hour.** `resets_at` came back `09:20:00`, so boundaries
   follow the first message. A ping at 04:03 owns 04:03–09:03 from then on. Any UI must show
   the **actual** `resets_at`, never the idealised time the user configured.

Still unobserved: a window was already open during the spike, so "pinging with nothing open
actually starts one" is inferred, not demonstrated. Confirm on a cold morning before
shipping.

Source: the maintainer roadmap, CCRM-17.

---

## 8. When it breaks — re-verification order

The endpoints are undocumented, so assume periodic breakage. Check in this order; each step
is cheap and rules out a whole class of cause:

1. **Is it the User-Agent?** (§2) An opaque 429 on the token endpoint is a UA problem far
   more often than a rate-limit problem.
2. **Has the authorize URL moved?** Capture Claude Code's own `claude auth login` URL and
   diff it against §4.1 — parameter order and encoding included.
3. **Has the scope set changed?** A grant that fails at *submit* with `Invalid request
   format` points here or at §4.1.
4. **Has the usage schema shifted?** Capture a live payload, add it to
   `fixtures/usage/`, and see which expectations move.
5. **Re-run `ua-probe`** if the usage/profile endpoints start behaving oddly — CCM-72
   [Foundation] verified they don't gate on User-Agent as of 2026-09-20, but that could
   change; the probe is the fast way to re-check before assuming something else broke.

Whatever you learn, write it back into this file and add a fixture. That is the only reason
the next port will be cheap.

---

## 9. Reading the plan (CCM-68 [Accounts])

```
GET https://api.anthropic.com/api/oauth/profile
Authorization: Bearer <access_token>
anthropic-beta: oauth-2025-04-20
User-Agent: Cooldown/<version> (macOS)
Content-Type: application/json
```

Same host, headers and User-Agent rule as §2–§3 — the honest `Cooldown/<version> (macOS)` UA,
not `claude-code`.

**Read at most once a day per profile** (`UsageRepository.planTagRecheckMs`, 24h; a clock that
has gone backwards also reads as due, never as "not due for decades") — via the same fetch
path as usage, never a separate poller, and **at most once per `fetch()` call** even when more
than one call site would otherwise want a look: the profile is read on the first successful
usage fetch or refusal for this profile (`planTagCheckedAtMs == 0`), then daily, **forced**
(regardless of the daily gate) on a manual refresh or on sign-in's own validation fetch — the
two ways a user can *ask* to find out an upgrade already landed. A failed or unparseable read
leaves the stored tag alone rather than inventing one, but still stamps `planTagCheckedAtMs` on
any *completed* HTTP round trip (a non-200 or an unparseable body included) — a server hiccup
must not wedge the cadence into retrying every call. A thrown transport error (no response at
all) does not stamp it, since no round trip completed to date it.

A parked profile (§10.1) is the other reader: every fetch checks whether today's read is due,
and reads the profile *instead of* usage while it stays due-gated — within the day, nothing is
read at all, and the profile just stays parked.

Undocumented and unversioned like §3 — treat every field as optional. Captured shape
(redacted), mirrored under `contract/fixtures/profile/`:

```json
{
  "account": {
    "uuid": "…", "full_name": "…", "display_name": "…", "email": "…",
    "has_claude_max": false, "has_claude_pro": false, "created_at": "2026-04-12T11:12:43Z"
  },
  "organization": {
    "uuid": "…", "name": "…",
    "organization_type": "claude_team",
    "billing_type": "invoice",
    "rate_limit_tier": "default_claude_max_5x",
    "seat_tier": "team_tier_1",
    "has_extra_usage_enabled": false,
    "subscription_status": "active"
  },
  "application": { "uuid": "9d1c250a-…", "name": "Claude Code", "slug": "claude-code" },
  "enabled_plugins": []
}
```

**Fields read** (`PlanTag.parse`) — `organization.organization_type`,
`organization.seat_tier`, `organization.rate_limit_tier`, `account.has_claude_max`,
`account.has_claude_pro`. Everything else is ignored.

**Label mapping**, ported verbatim from Android's `ClaudePlan.label` (`ClaudePlan.kt:64-84`):

| `organization_type` | `seat_tier` | Label |
|---|---|---|
| `claude_free` | — | Free |
| `claude_pro` | — | Pro |
| `claude_max` | — | Max |
| `claude_enterprise` | — | Enterprise |
| `claude_team` | `null` | Team |
| `claude_team` | `"team_standard"` | Team Standard |
| `claude_team` | anything else | Team Premium |
| `null` | — | Max if `has_claude_max`, else Pro if `has_claude_pro`, else no plan (nil) |
| anything else | — | that type's name, `claude_` stripped, `_` → space, **only the first character of the whole string capitalized** — not each word (`"claude_something_new"` → `"Something new"`) |

**Free-plan detection.** `PlanTag.isBlockedPlan(plan)` is `plan == "Free"` — the one label
whose usage the endpoint refuses with the 403 in §5. Cheaper to learn from this endpoint than
from the 403 (CCM-67 [Readout]): a profile read costs nothing the usage endpoint would also
have charged, and it is what CCM-67 polls once the 403 has been seen once.

Source: `ApiClient.kt:39-50`, `data/source/ClaudePlan.kt`, `UsageRepository.kt:410-448,
505-542` (Android); `PlanTag.swift`, `UsageRepository.swift` (Mac).
