# CCRM-55 (Antigravity Account) spike — terminal, Mac, 2026-09-08

Ran the four steps in CCRM-55 (Antigravity Account)'s "The spike" bullet against a real,
signed-in Antigravity install. Tokens redacted throughout — only lengths recorded, never
values, per the runbook's rule.

## Setup

- Antigravity 2.12.2, signed in, running normally on this Mac before the spike started.
- Refresh token read from the macOS Keychain, service `gemini`, account `antigravity`. The
  stored blob was `go-keyring-base64:`-wrapped JSON shaped `{"token": {"access_token": ...,
  "refresh_token": <103 chars>, "expiry": ...}, "auth_method": ...}` — one level deeper than
  the research doc's `token_from_value` top-level guess, under a `"token"` key. Worth folding
  back into `design/research/2026-09-06-openquota-antigravity.md` §1b if this app ever needs
  to parse the same blob.
- Client id/secret: the id from the research doc, the secret (redacted here too, same as the
  research doc, for GitHub push protection — full value pulled fresh from
  `deviffyy/OpenQuota`'s `client.rs` at the commit the research doc already cites,
  `GOOGLE_CLIENT_SECRET_PARTS`) starting `GOCSPX-`. Public, installed-app value per the
  source's own comment; not a per-user secret.
- Refreshed a 258-char access token from `https://oauth2.googleapis.com/token` with Antigravity
  and `agy` **fully quit** (`osascript -e 'quit app "Antigravity"'` + `pkill -f agy`, confirmed
  by an empty `ps aux | grep -i antigrav`).
- **Correction to the spike bullet:** the `{"metadata": {"ideName": "antigravity", ...}}` body
  in CCRM-55's spike step 3 is the **local** `127.0.0.1` language-server RPC's body (research
  doc §2, "Local RPC"). The **remote** `cloudcode-pa.googleapis.com` REST endpoint used here
  takes an empty body, `{}`, per the same research doc's §2 "Remote HTTPS RPCs" section. The
  `metadata` body against the remote host 400s with `Unknown name "metadata"`.

## Call 1 — cold, no prior Gemini usage this session

`POST https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary`, body `{}`,
`Authorization: Bearer <redacted, 258 chars>`, `User-Agent: antigravity`:

```json
{
    "groups": [
        {
            "buckets": [
                { "bucketId": "gemini-weekly", "displayName": "Weekly Limit Remaining", "window": "weekly", "resetTime": "2026-09-15T07:25:15Z", "remainingFraction": 1 },
                { "bucketId": "gemini-5h", "displayName": "Five Hour Limit Remaining", "window": "5h", "resetTime": "2026-09-08T12:25:15Z", "remainingFraction": 1 }
            ],
            "displayName": "Gemini Models",
            "description": "Models within this group: Gemini Flash, Gemini Pro"
        },
        {
            "buckets": [
                { "bucketId": "3p-weekly", "displayName": "Weekly Limit Remaining", "window": "weekly", "resetTime": "2026-09-15T07:25:15Z", "remainingFraction": 1 },
                { "bucketId": "3p-5h", "displayName": "Five Hour Limit Remaining", "window": "5h", "resetTime": "2026-09-08T12:25:15Z", "remainingFraction": 1 }
            ],
            "displayName": "Claude and GPT models",
            "description": "Models within this group: Claude Opus, Claude Sonnet, GPT-OSS"
        }
    ],
    "description": "Within each group, models share a weekly limit and a 5-hour limit. Quota is consumed proportionally to the cost of the tokens. Thus, limits will last longer with shorter tasks or using more cost-effective models. The 5-hour limit smooths out aggregate demand to fairly distribute global capacity across all users, while your weekly limit is tied directly to your individual tier."
}
```

Identical shape (differing only in `resetTime`, which trailed the wall clock by the same
offset) came back from `daily-cloudcode-pa.googleapis.com` in the same call.

## Call 2 — after sending one real "hi" message to Gemini in Antigravity

Reopened Antigravity, sent one short prompt to a Gemini model, waited for the reply, quit
Antigravity and `agy` again, then repeated the same `cloudcode-pa` call ~3 minutes later
(same access token, still valid):

```json
{
    "groups": [
        {
            "buckets": [
                { "bucketId": "gemini-weekly", "displayName": "Weekly Limit Remaining", "window": "weekly", "resetTime": "2026-09-15T07:28:17Z", "remainingFraction": 1 },
                { "bucketId": "gemini-5h", "displayName": "Five Hour Limit Remaining", "window": "5h", "resetTime": "2026-09-08T12:28:17Z", "remainingFraction": 1 }
            ],
            "displayName": "Gemini Models",
            "description": "Models within this group: Gemini Flash, Gemini Pro"
        },
        {
            "buckets": [
                { "bucketId": "3p-weekly", "displayName": "Weekly Limit Remaining", "window": "weekly", "resetTime": "2026-09-15T07:28:17Z", "remainingFraction": 1 },
                { "bucketId": "3p-5h", "displayName": "Five Hour Limit Remaining", "window": "5h", "resetTime": "2026-09-08T12:28:17Z", "remainingFraction": 1 }
            ],
            "displayName": "Claude and GPT models",
            "description": "Models within this group: Claude Opus, Claude Sonnet, GPT-OSS"
        }
    ],
    "description": "..."
}
```

## Verdict

**Placeholder, not real quota.** All four buckets read `remainingFraction: 1` both before and
after genuine Gemini usage in the same session, and every `resetTime` moved forward by exactly
the ~3 minutes elapsed between the two calls (`12:25:15Z` → `12:28:17Z`) rather than staying
anchored to a fixed window boundary — the tell of a server-side "you're fine, ask again in five
hours from *now*" stub rather than a tracked usage window, which by definition only moves at
whole window rollovers. This matches CodexBar's warning in CCRM-55 (Antigravity Account)
exactly: a token minted and used outside a live Antigravity session gets an availability-shaped
payload, not live quota. **CCRM-55 (Antigravity Account) stays blocked** on the data blocker (not
just the auth blocker); the Mac-relay route — filed as CCRM-59 (Antigravity Mac Relay) — becomes
the only route this app can take for real Gemini fractions, since it is the one path that can
present the local language-server's `RetrieveUserQuotaSummary` (§2 "Local RPC" in the OpenQuota
research doc) rather than this stubbed remote one.
