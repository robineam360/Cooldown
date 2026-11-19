# Provider terms and store policy for Cooldown's sign-ins (2026-11-18)

> **Verdicts superseded 2026-11-19** by [2026-11-19-provider-signins-precedent.md](2026-11-19-provider-signins-precedent.md)
> (what comparable apps do; Claude hold, ChatGPT change method, Gemini hold).

Prompted by the Play submission gate (RUNBOOK Step 8 log): the judge and Sol both returned `blocking`, the judge on a
Play policy risk. Four read-only research agents (Sonnet), one per provider plus one on store policy, read the code and
the providers' current pages. Quotes are short; "inference" marks what no page states. Android and iOS sign in the same
way (iOS: `OAuthSignIn.swift:14`, `DeviceCodeSignIn.swift:24`, the Antigravity path).

## How each sign-in works today

| Provider | Client the app presents | Endpoint read | User-Agent |
|---|---|---|---|
| Claude | Claude Code's OAuth client ID (`ApiClient.kt:51`) | `api.anthropic.com/api/oauth/usage`, `/profile` (undocumented) | `Cooldown/<ver>`, honest |
| ChatGPT | Codex CLI's OAuth client ID (`ChatGptSource.kt:40`) | `chatgpt.com/backend-api/wham/usage` (undocumented) | `Cooldown/<ver>`, honest |
| Gemini | Antigravity CLI's client ID **and secret** (`AntigravitySource.kt:33,55`) | `daily-cloudcode-pa…/v1internal:retrieveUserQuotaSummary` (internal) | `antigravity/cli/<v>` appended (`:51`), required by Google |

## What the providers say

- **Anthropic — high risk.** code.claude.com/docs/en/legal-and-compliance, "Authentication and credential use": third
  parties may not "offer Claude.ai login into their own applications", may not "collect, store, or intermediate
  Claude.ai credentials or session tokens", and sign-in "must complete through Anthropic's own flow"; enforcement may
  come "without prior notice". Press reports server-side blocks from Jan 2026 and a Feb 2026 clarification
  (not verified first-hand). No official API exposes subscription 5h/weekly usage. Route named: anthropic.com/contact-sales.
- **Google — high risk.** Google APIs ToS §2(c): "You will not misrepresent or mask … your API Client's identity";
  OAuth policies act against apps that "misrepresent their identity". Antigravity's own terms reportedly bar third-party
  tools from using its OAuth, with users' accounts suspended (forum reports, not read first-hand). No documented quota
  API found; registering Cooldown's own client may not be accepted by the internal endpoint (untested).
- **OpenAI — medium risk.** Use of the Codex client ID is unaddressed, not permitted; the Terms of Use page could not be
  fetched (403), and snippets list "automated or programmatic" extraction among prohibited uses. An official route
  exists: Sign in with ChatGPT (own client ID; open-source apps self-register, paid/hosted apps use an interest form),
  but it does not document a plan-usage read.

## What the stores say

- **Google Play**, Device and Network Abuse: a listed violation is accessing "a service or API in a manner that violates
  its terms of service" — any provider, not only Google. Impersonation: "Don't imply that your app is related to or
  authorized by someone that it isn't." Enforcement: rejection is harmless; removal and suspension count against the
  account; repeated strikes can terminate it and "any related Google Play developer accounts".
- **Apple**, 5.2.2: an app using a third-party service must be "specifically permitted to do so under the service's
  terms of use. Authorization must be provided upon request." Also 5.2.1 and 4.1(c) on names and brands.
- **Precedent:** "AI Usage: Claude, Codex & more" is on Play; Runlow, UsageNow, AI Watchman and CUStats Go on the App
  Store. Listing is not proof of permission, and their sign-in method is unknown.

## Risks beyond the stores

The provider can block the client or the user, on any channel (GitHub, TestFlight, Play, App Store). Anthropic's and
Google's reported enforcement lands on the **user's own account**, so colleagues installing the app carry that risk.
