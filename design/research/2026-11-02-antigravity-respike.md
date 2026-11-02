# CCRM-55 (Antigravity Account) respike — agy CLI, Mac, 2026-11-02

Reopened because the Antigravity CLI (`agy`) now shows Gemini usage limits outside the IDE, which the
2026-09-08 spike said a phone could never see
([2026-09-08-antigravity-spike.md](2026-09-08-antigravity-spike.md)). Tokens never printed; only lengths
recorded, the Cloud project id and email redacted. The probe scripts lived in the session scratchpad and are
summarised here, not kept.

## Verdict (updated after the proxy capture, same day)

**The data blocker is gone.** A plain HTTPS client with the user's Antigravity token gets **every window agy
shows**: Gemini 5h and weekly, Gemini 4 Argon weekly, and the Claude + GPT pool's 5h and weekly, with unrounded
fractions and anchored resets. The key is the User-Agent: Google returns the Gemini groups only when it contains a
versioned `antigravity/cli/<version>` product token. Body and endpoint are what this note already had.
**Sign-in on a phone is still the September auth blocker**, untouched by this spike.

| Window | agy on the Mac | Plain HTTPS with `antigravity/cli/<version>` UA |
|---|---|---|
| Gemini 5h (Flash + Pro pool) | ✅ | ✅ `gemini-5h` (also via `fetchAvailableModels` with any UA) |
| Gemini weekly | ✅ | ✅ `gemini-weekly` |
| Gemini 4 Argon weekly (own limit on Pro) | ✅ | ✅ `gemini-argon-wtu-weekly` |
| Claude + GPT 5h and weekly | ✅ | ✅ `3p-5h`, `3p-weekly` (any UA) |

The first four rounds below (written before the capture) only found Gemini 5h; they are kept as the record of
what does not work.

## What agy is

- `~/.local/bin/agy` 1.4.11, a 197 MB Go binary, a standalone CLI with no IDE installed. It runs the
  Antigravity language server **in-process** (`server.go: Language server will attempt to listen on host
  localhost`), and that server's quota manager fetches
  `POST https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary` (its log, `quota_manager.go`
  + `cache.go(retrieveUserQuotaSummary)`). So the numbers agy prints come from Google's cloud, not from a local file.
- Token: Keychain service `gemini`, a `go-keyring-base64:` blob with keys `auth_method`, `id_token_email`,
  `token` (access 261 chars, refresh 103 chars, ~1 h access lifetime). The client binary embeds the Antigravity
  installed-app client id `1071006060591-…` from the September research. `~/.gemini/oauth_creds.json` is a stale
  Gemini CLI token (client `681255809395-…`, expired), not agy's.
- `agy -p /usage` prints the summary in print mode without spending quota.

## The data is real now (agy)

Before → after two ~2,500-word prompts (Gemini 4 Argon High at 03:58Z, Gemini 3.8 Flash High at 04:02Z):

| Bucket | Before | After |
|---|---|---|
| Gemini weekly | 100%, reset 2026-11-05T16:51:10Z | unchanged: anchored to first use on 2026-10-29 16:51Z, **not** "now + 7 d" |
| Gemini 5h | 100%, reset = call time + 5 h (sliding) | 100%, reset **pinned to 09:02:04Z** = Flash call + 5 h |
| Gemini 4 Argon weekly | 100%, reset = call time + 7 d | **99%**, reset **pinned to 2026-11-09T03:58:44Z** = Argon call + 7 d |
| Claude + GPT 5h / weekly | 100%, sliding | 100%, sliding (unused) |

This is what September never saw: resets anchored to real first use and a fraction that moves. An unused window
still slides ("call time + window"), which is how to tell **not started** from **started**.

## Plain HTTPS, four rounds (`daily-cloudcode-pa` and `cloudcode-pa` agree throughout)

1. `retrieveUserQuotaSummary`, body `{}` or `{"project": <cloudaicompanionProject>}`, `User-Agent: antigravity`
   → 200 with **only** the "Claude and GPT models" group (`3p-weekly`, `3p-5h`). No Gemini groups at all, where
   September got all four pinned at 1. `loadCodeAssist` → `currentTier: free-tier`, `paidTier: g1-pro-tier`
   ("Google AI Pro"), `cloudaicompanionProject` present.
2. Variations, all no better: `forceRefresh` and `metadata` → 400 unknown field (the remote REST shape takes
   `project` only); versioned `antigravity/1.4.11 (…)` User-Agent and `X-Goog-Api-Client` → same 3p-only reply;
   `X-Goog-User-Project` → 403 (needs `serviceusage.services.use`). **`fetchAvailableModels`** with the project
   → 200, 20 models, each with `quotaInfo`: every Gemini model `remainingFraction 0.9976187`,
   `resetTime 2026-11-02T09:02:04Z` (the Gemini 5h pool, exact, unrounded, same anchor as agy); Claude / GPT models
   `1`, sliding; tab-completion models `1` with no reset. No `gemini-4-argon-*` model in the list.
3. agy's own `jetski/<version>` User-Agent → `loadCodeAssist` 200 with no tier, every quota RPC 404.
4. Under `jetski`, `loadCodeAssist` offers only `standard-tier` "Gemini Code Assist" with a user-defined GCP
   project: the enterprise product, not the consumer quota. Dead end.

What agy sends that unlocks the Gemini groups is unknown. Its cloud calls do not go through Go's default HTTP/2
transport (`GODEBUG=http2debug=2` logged only `play.googleapis.com` telemetry), so headers could not be read
without an intercepting proxy, which needs a trusted root certificate on the Mac (not done; owner's call).

## Proxy capture and replay (plan `2026-11-02-agy-proxy-capture-plan.md`, Astra-reviewed)

agy's call, captured once through mitmproxy with a session-only CA (trusted for minutes, then removed; token
redacted in memory, never written):

- `POST https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary`, HTTP/1.1, body
  `{"project": "<cloudaicompanionProject from loadCodeAssist>"}`, headers only `Authorization: Bearer …`,
  `Content-Type: application/json`, `Accept-Encoding: gzip` and
  `User-Agent: antigravity/cli/1.4.12 (aidev_client; os_type=darwin; arch=arm64; cl=1010700967; auth_method=consumer)`.
- `loadCodeAssist` body `{"metadata":{"ideType":"ANTIGRAVITY"}}`, same UA. agy also calls `fetchUserInfo`,
  `fetchAdminControls`, `fetchAvailableModels`, `listExperiments`.
- Response: three groups. Gemini Models (`gemini-weekly` 0.9984876, reset 2026-11-05T16:51:10Z; `gemini-5h`
  0.9976187, reset 2026-11-02T09:02:04Z), Claude and GPT models (`3p-weekly`, `3p-5h`, both 1, sliding), and
  Gemini 4 Argon (`gemini-argon-wtu-weekly` 0.98939896, reset 2026-11-09T03:58:44Z, group description: "For paid
  Pro plans, Argon quota is separate. Higher Argon quota (included as part of the larger Gemini quota bucket) is
  available on Ultra plans."). Used buckets carry a human `description` ("…it will fully refresh in 3 days,
  12 hours."); unused ones do not.

Replay from a plain Python client, no proxy, same token, project fetched live:

| User-Agent | Groups returned |
|---|---|
| agy's exact string | all three |
| `antigravity/cli/1.4.12 (aidev_client; os_type=darwin; arch=arm64)` | all three |
| `antigravity/cli/1.4.12` | all three |
| `antigravity/cli/1.4.12 (aidev_client; os_type=android; arch=arm64; auth_method=consumer)` | all three |
| same with `os_type=ios` | all three |
| `Cooldown/1.0 antigravity/cli/1.4.12` | all three |
| `antigravity/cli` (no version) | Claude + GPT only |
| `antigravity` | Claude + GPT only |

So the gate is a versioned `antigravity/cli/<v>` token anywhere in the UA; the OS does not matter, and an app can
name itself first. **ToS posture:** sending that token is presenting as Google's CLI. It sits with the "highest of
the three" note in the CCRM-55 entry and must be disclosed as plainly.

## What it means for CCRM-55 (Antigravity Account)

- **Data: unblocked.** A phone-only build can show all five windows from one call, given a token.
  The shape is three pools, one of them (Argon) weekly-only, which the `lanes` generalisation deferred in
  CCRM-53 (Provider Model) must hold.
- **Auth (unchanged, still hard):** the token comes from Antigravity's Google OAuth client, whose redirect is a
  localhost loopback. Options b (refresh-token paste) and c (loopback in a Custom Tab) from the CCRM-55 entry
  stand as written. agy embeds Go's `deviceauth.go` (`device_code`, `verification_url`), but Google's device
  flow does not allow the `cloud-platform` scope, so that is likely a vendored library, not a usable route.
  agy's binary also names `ANTIGRAVITY_IOS` / `ANTIGRAVITY_ANDROID_PRIMES`, so official mobile clients may
  exist; a mobile sign-in of Google's own would be the best auth lead.
- **Not a Mac relay:** nothing here depends on the Mac at run time; CCRM-59 (Antigravity Mac Relay) stays
  dropped.

## Next, if pursued

1. ~~Capture and replay agy's request~~ — done, above.
2. **Auth on a phone** — the one remaining blocker. Check whether an official Antigravity iOS/Android app exists
   and how it signs in; otherwise choose between option b (refresh-token paste) and c (loopback in a Custom Tab).
3. Then reopen CCRM-55 (Antigravity Account) as Planned, with the September states list plus **started vs not
   started** (sliding reset), **a weekly-only per-model pool** (Argon) and **Google's own description line**
   added to the wireframe.
