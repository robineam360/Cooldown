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

## Call 3 — decisive round: a real, expensive task, checked against the IDE's own local view

Robin's own screenshots of Antigravity's **Models & Usage** panel (which reads the local
language-server RPC, `127.0.0.1`, in-session — the "richest source" per the research doc), one
before and one after a real task ("do a web search and get me the updated take on agents in no
less than 4000 words," a genuinely expensive Gemini Pro request):

| | Gemini Weekly | Gemini 5h |
|---|---|---|
| Before | 100% | 100% |
| After the 4000-word task | 99% | 98% |

So the **local, in-IDE view moved** — real consumption happened and Antigravity's own UI
correctly reflects it. Immediately after, quit Antigravity and `agy` again and repeated the
identical remote call from Call 1/2 (fresh refresh, same terminal):

```json
{
    "groups": [
        {
            "buckets": [
                { "bucketId": "gemini-weekly", "displayName": "Weekly Limit Remaining", "window": "weekly", "resetTime": "2026-09-15T09:25:47Z", "remainingFraction": 1 },
                { "bucketId": "gemini-5h", "displayName": "Five Hour Limit Remaining", "window": "5h", "resetTime": "2026-09-08T14:25:47Z", "remainingFraction": 1 }
            ],
            "displayName": "Gemini Models",
            "description": "Models within this group: Gemini Flash, Gemini Pro"
        },
        {
            "buckets": [
                { "bucketId": "3p-weekly", "displayName": "Weekly Limit Remaining", "window": "weekly", "resetTime": "2026-09-15T09:25:47Z", "remainingFraction": 1 },
                { "bucketId": "3p-5h", "displayName": "Five Hour Limit Remaining", "window": "5h", "resetTime": "2026-09-08T14:25:47Z", "remainingFraction": 1 }
            ],
            "displayName": "Claude and GPT models",
            "description": "Models within this group: Claude Opus, Claude Sonnet, GPT-OSS"
        }
    ]
}
```

Call made at `2026-09-08T09:25:44Z` (captured via `date -u` immediately before the request):
`gemini-5h`'s `resetTime` is `09:25:44Z + ~5h0m3s`, `gemini-weekly`'s is `+ ~7 days 0h0m3s` —
both still exactly "call time + fixed window," with zero relationship to the 1-2% just spent
seconds earlier and visible in the same account's own IDE.

## Verdict

**Confirmed, not just suspected: the remote endpoint never carries real Gemini usage, full
stop.** A real, substantial task moved Antigravity's own local usage view (100%→98%/99%), but
the exact same account, queried the same way as before through
`cloudcode-pa.googleapis.com:retrieveUserQuotaSummary`, still returned all four buckets pinned
at `remainingFraction: 1` with `resetTime` computed as "call time + fixed duration" — unrelated
to any real window boundary or any real consumption. This is not a slow-to-update cache or a
threshold effect a bigger prompt could surface; it is a hard split between two different data
sources: the **local** `127.0.0.1` language-server RPC (real, and the one Antigravity's own UI
reads) and the **remote** cloud RPC (permanently a stub for any caller without a live IDE
session next to it). A phone can never be that caller.

**Decision (Robin, 2026-09-08): drop CCRM-55 (Antigravity Account).** Even setting the data
result aside, routing around it with a Mac relay (CCRM-59 (Antigravity Mac Relay)) would make
the phone app's Gemini data depend on a Mac being awake and running Antigravity — which fails
this app's own standalone requirement on its own terms. Both items are marked Dropped in
ROADMAP.md; their IDs are retained, never reused, per CLAUDE.md.
