# CCRM-55 (Antigravity Account) respike — agy CLI, Mac, 2026-11-02

Reopened because the Antigravity CLI (`agy`) now shows Gemini usage limits outside the IDE, which the
2026-09-08 spike said a phone could never see
([2026-09-08-antigravity-spike.md](2026-09-08-antigravity-spike.md)). Tokens never printed; only lengths
recorded, the Cloud project id and email redacted. The probe scripts lived in the session scratchpad and are
summarised here, not kept.

## Verdict

**Partly reopened.** A plain HTTPS client with the user's Antigravity token, which is what a phone would be, now
gets **real, moving Gemini 5-hour data** and the Claude + GPT pool's 5-hour and weekly windows. It does **not** get
the Gemini weekly window or the new Gemini 4 Argon weekly limit; only agy does, and four rounds of request-shape
guessing did not find what agy sends differently. Sign-in on a phone is still the September auth blocker,
untouched by this spike.

| Window | agy on the Mac | Plain HTTPS (phone-shaped) |
|---|---|---|
| Gemini 5h (Flash + Pro pool) | ✅ real | ✅ real, via `fetchAvailableModels` per-model `quotaInfo` |
| Gemini weekly | ✅ real | ❌ not returned |
| Gemini 4 Argon weekly (own limit, like Claude's per-model weekly) | ✅ real | ❌ Argon models absent from the list |
| Claude + GPT 5h and weekly | ✅ | ✅ via `retrieveUserQuotaSummary` |

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

## What it means for CCRM-55 (Antigravity Account)

- **Data:** a phone-only build could show Gemini 5h (from `fetchAvailableModels`, pooled across the Gemini
  models) and Claude + GPT 5h + weekly. It could not show Gemini weekly or Argon weekly, the two that
  matter most to a Pro user, until agy's request is captured.
- **Auth (unchanged, still hard):** the token comes from Antigravity's Google OAuth client, whose redirect is a
  localhost loopback. Options b (refresh-token paste) and c (loopback in a Custom Tab) from the CCRM-55 entry
  stand as written. agy embeds Go's `deviceauth.go` (`device_code`, `verification_url`), but Google's device
  flow does not allow the `cloud-platform` scope, so that is likely a vendored library, not a usable route.
  agy's binary also names `ANTIGRAVITY_IOS` / `ANTIGRAVITY_ANDROID_PRIMES`, so official mobile clients may
  exist; a mobile sign-in of Google's own would be the best auth lead.
- **Not a Mac relay:** nothing here depends on the Mac at run time; CCRM-59 (Antigravity Mac Relay) stays
  dropped.

## Next, if pursued

1. Capture agy's `retrieveUserQuotaSummary` request (headers and body) once through an intercepting proxy, then
   replay it from a plain client. If it replays, all five windows are phone-reachable.
2. Check whether an official Antigravity iOS/Android app exists and how it signs in.
3. Only then reopen CCRM-55 (Antigravity Account) as Planned, with the September states list plus **started vs
   not started** (sliding reset) and **per-model limit** (Argon) added to the wireframe.
