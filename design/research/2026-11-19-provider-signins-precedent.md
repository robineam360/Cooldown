# Cooldown's sign-ins against what other apps do (2026-11-19)

Supersedes the verdicts in [2026-11-18-provider-terms-store-review.md](2026-11-18-provider-terms-store-review.md).
That doc's facts on Cooldown's own code and the store policies still stand.

**Question.** Robin thinks the 2026-11-18 finding overstates the risk. This doc tests it against how real coding
clients and usage trackers use a Claude, ChatGPT or Gemini subscription sign-in.

**Method.**
- Four read-only Sonnet researchers worked in parallel: one each for Claude, ChatGPT, Gemini, and usage trackers.
- They shallow-cloned the projects into `~/Projects/_research/` and only grepped them. Nothing was built or run.
- The seat read the three provider pages itself today: Anthropic's legal page, Antigravity's terms, and OpenAI's
  Sign in with ChatGPT docs.
- The seat spot-checked the key code claims in the clones: T3 Code, Cline, Roo Code, Zed, Crush, Kilo Code and
  Usage4Claude.
- **"Secondary"** means press or blog text not checked at source. **"Inference"** means no source states it.

**Short answer.** Robin is right on one point. No provider has acted against a **read-only usage meter**: every
enforcement found was aimed at prompt traffic. But the other apps do not get around the rules. They fall into
three groups:
- **Desktop apps** read the token the official CLI already minted on the same machine. They run no sign-in of
  their own, which a phone cannot copy.
- **Clients that moved to an official route.** This now exists for ChatGPT only.
- **Apps that borrow a client and are tolerated.** These are ChatGPT-side apps, or apps on GitHub or small App
  Store listings. No published rule covers them.

The written rules have also become explicit since early 2026:
- Anthropic now names exactly what Cooldown does.
- Antigravity's terms now name it too, and Google enforced them with account suspensions.

## Claude

| Project | Client presented | Endpoint | CLI subprocess? | User-Agent | Anthropic action |
|---|---|---|---|---|---|
| T3 Code | None of its own | Chat: official `claude` CLI and Agent SDK. Meter: `/api/oauth/usage` (`claudeResetCredits.ts:216`) | Yes, for chat | Meter: `claude-cli/<v> (external, cli)` | None found |
| Cline, Zed | None | Agent SDK | Yes (SDK, ACP) | The CLI's | None. Cline reworked an older direct path (secondary) |
| opencode | Claude Code's, until removed | Gone. Now an Agent SDK provider | Now yes | Spoofed Claude Code (secondary) | **Blocked 2026-01-09. OAuth removed after "anthropic legal requests"** (The Register, 2026-02-20) |
| Crush | Removed | None | n/a | n/a | Dropped the path (code removes stored tokens) |
| Roo Code | Had its own OAuth flow | Unverified | n/a | n/a | Reworked (secondary) |
| aider | API key only | n/a | n/a | n/a | n/a |
| CodexBar (Mac) | Claude Code's, used to **refresh the CLI's own token** | `/api/oauth/usage` | Falls back to `claude` | Spoofs `claude-cli/<v>` | None found |
| Claude Usage Tracker (Mac) | Pasted session cookie, or the CLI's token | `claude.ai/api/organizations/…` | No | Safari UA, or `claude-code/2.1.5` | Changelog 3.0.2 (2026-03-10): `/api/oauth/usage` "disabled" for it. Inference: it was blocked or rate-limited |
| Usage4Claude (Mac, GitHub) | **Claude Code's, with its own PKCE sign-in.** This is the closest match to Cooldown | `/api/oauth/usage`, `/profile` | No | Not set | None found. It asks for scope `user:profile` only |
| Store apps: CUStats Go, Runlow, UsageNow | Pasted session key, or not documented | Not documented | No | Unknown | All live today |

**What Anthropic says.** The seat read code.claude.com/docs/en/legal-and-compliance today:
- Anthropic "does not permit third-party developers to offer Claude.ai login into their own applications".
- Developers "may not collect, store, or intermediate Claude.ai credentials or session tokens".
- OAuth is "designed to support ordinary use of Claude Code and other native Anthropic applications".
- Enforcement may come "without prior notice". Questions go to sales.

The Agent SDK overview (code.claude.com/docs/en/agent-sdk/overview) says "Unless previously approved, Anthropic
does not allow third party developers to offer claude.ai login". So approvals exist.

Consumer Terms §3.7 bans automated access "except … where we otherwise explicitly permit it".

No page separates a read-only meter from a client that sends prompts. The 429s users report on
`/api/oauth/usage` are rate limits, not blocks.

**Verdict: Hold** on Play and the App Store until Anthropic answers.
- Cooldown does exactly what the page names. It offers Claude.ai login inside its own app, and it stores the
  tokens.
- Apple 5.2.2 needs the provider's terms to "specifically permit" the use, with proof on request. Here the
  provider's terms specifically forbid it.
- The comparable apps escape this in one of two ways. T3 Code and CodexBar mint no token: the user's own CLI does.
  The other apps are not on a store under a company account.
- The Play account belongs to EAM360's organisation. A removal counts against every EAM360 app, so a small
  chance of a costly outcome rules.
- Enforcement against meters so far: none found. A store listing, which is the part on hold, is the riskier
  channel.

**Change to make whatever the answer: narrow the scope.**
- Cooldown asks for Claude Code's full scope set. That set includes `user:inference`, `user:file_upload` and
  `org:create_api_key` (`data/OAuthSignIn.kt:35`, iOS `OAuthSignIn.swift`).
- As a result, a stolen Cooldown token could send prompts.
- Usage4Claude reads the same endpoints with `user:profile` only.
- Spike: sign in with `user:profile` alone. Then check that `/api/oauth/usage` and `/profile` still answer, and
  that Pro, Max and Team plan names still parse.
- If so, ship it. Update the iOS `ContractTests.swift:467` expectation too.
- This does not fix the policy problem. It makes the request to Anthropic easier to grant: "read-only, profile
  scope, no inference".

## ChatGPT

| Project | Client presented | Endpoint | CLI subprocess? | UA / originator | OpenAI action / permission |
|---|---|---|---|---|---|
| opencode | **Its own, via Sign in with ChatGPT dynamic registration** (`dynamic_agent_client`) | `api.openai.com/v1` Responses | No | Its own | Named open-source launch partner (secondary, 2026-10-02) |
| T3 Code | Codex app-server's | App-server. Its usage poller reads `backend-api/wham` | Yes | The CLI's | Launch partner (secondary) |
| Cline | **Codex's** (`oauth.ts:22`) | `backend-api/codex` | No | `originator=cline` | None found |
| Roo Code | **Codex's** (`oauth.ts:20`) | `backend-api/codex`, **`wham/usage`** (`rate-limits.ts`) | No | `roo-code/<v>` | None found |
| Zed | **Codex's** (`openai_subscribed.rs:38`) | `backend-api/codex` | No | `originator=zed` | None found |
| Crush | **Codex's** (`oauth.go:29`) | `backend-api/codex` | No | `originator=crush` | None found |
| Kilo Code | Codex's, in an inherited plugin | `backend-api/codex`, **`wham/usage`** (`provider-usage/codex.ts`) | No | `kilo/<v>` | Commercial launch partner (secondary) |
| CodexBar, Claude Usage Tracker, Usage4Claude | Codex's (refresh or own sign-in) | `wham/usage` | No | Some send `codex-cli` | None found |
| CUStats Go (App Store, $8.99, since 2026-01-22) | **Codex device code**, so very likely Codex's client. The binary was not inspected | Not documented | No | Unknown | **Live today, updated 2026-10-14** |

**What OpenAI says.** The seat read developers.openai.com/siwc today:
- Sign in with ChatGPT has a documented path for "open-source and locally hosted apps".
- On first sign-in the app sends `client_id=dynamic_agent_client` and gets "an issued client_id … bound to the
  authenticated user". It needs a persisted opaque `ext_agent_host_id` and a loopback callback on `127.0.0.1`.
- The scopes are `openid profile email`, plus `offline_access resource.invoke chatgpt.tokens.use.direct` for plan
  use.
- Paid or remotely hosted apps go through an interest form instead.
- The docs describe **no plan-usage read**. They tell apps to link to "ChatGPT Settings → Usage".
- The quickstart says plan usage is "available to all open-source partners" (from the ChatGPT researcher's fetch).

Nothing published bans reuse of the Codex client ID. Five large open-source clients reuse it with no action found,
and Roo Code and Kilo Code read `wham/usage` exactly as Cooldown does. One blog claims OpenAI bars the official
token from `backend-api`. That is secondary and not found on an OpenAI page.

**Verdict: Change the method.**
- The risk today is low: tolerated in practice, and a store app (CUStats Go) does it.
- But an official identity now exists, and Cooldown is a free, open-source app that runs on the device, which is
  the case the docs describe.
- With its own registered client, Cooldown can answer Apple's "authorization upon request" with OpenAI's own docs
  for the sign-in half.
- The usage read stays undocumented either way.

Code changes. Do not make them before the spike passes.
- **Spike first.** Register through the open-source dynamic flow with identity scopes only. Then call `wham/usage`
  with the issued token, with and without the plan-use scopes.
  - If both are rejected, the token is bound to `api.openai.com/v1`. In that case keep the Codex device code. This
    reverses the verdict to **Ship as is** for ChatGPT, on Robin's word.
- **Android** `data/source/ChatGptSource.kt:40` and its sign-in UI:
  - Replace the device-code flow with authorization code plus PKCE against `auth.openai.com/api/accounts/authorize`.
  - Use a `127.0.0.1:<port>` loopback listener with a Custom Tab.
  - Send `dynamic_agent_client` on first sign-in, then persist the issued `client_id` per account.
  - Persist `ext_agent_host_id` as `urn:uuid:<v4>`, created once per install.
  - Validate the ID token, and refresh with the issued `client_id`.
- **iOS** `DeviceCodeSignIn.swift`: the same flow. Use an `NWListener` on `127.0.0.1`, opened with
  `ASWebAuthenticationSession`. The sheet runs in-process, so the listener stays alive (inference; test it).
- Both platforms:
  - Follow the SIWC UI guidelines: the approved button format, and the "Manage usage" link.
  - Wireframe the sign-in sheet change first (CLAUDE.md §2).
  - Update `spec/core` with the new flow before either client.

## Gemini (Antigravity)

| Project | Client presented | Endpoint | CLI subprocess? | User-Agent | Google action |
|---|---|---|---|---|---|
| Zed | None | Via the official `gemini` CLI | **Yes (ACP)** | The CLI's | None. This is the supported shape |
| T3 Code | The token minted by the real Antigravity CLI | Agent via ACP. Meter: **`retrieveUserQuotaSummary`** (`antigravityUsageLimits.ts:326`) | Agent yes, meter direct | **Spoofs `antigravity/hub/2.24.0`** | None found for T3 Code |
| opencode-antigravity-auth (plugin) | **Antigravity's client ID and secret** | `cloudcode-pa`, `daily-cloudcode-pa` | No | Spoofs `antigravity/hub` | README warns "Google may restrict accounts". **Users suspended** |
| CLIProxyAPI | **Antigravity's ID and secret** (public in its code) | Same | No | Spoofs `antigravity/hub/<v>` | Same pattern as the bans |
| CodexBar | Gemini CLI's (read from the installed package). Antigravity's stored credentials | `retrieveUserQuota`, `loadCodeAssist` | No | Spoofs `antigravity/hub` for Antigravity | None found |
| opencode core, Cline, Roo Code, Crush | No Google subscription path found | n/a | n/a | n/a | n/a |

**What Google says.** The seat read antigravity.google/terms today:
- "Using third party software, tools, or services to access the Service (e.g. using OpenClaw with Antigravity
  OAuth) is a breach of this Agreement."
- This "may be grounds for suspension or termination of your Antigravity and/or Gemini CLI accounts".

Gemini CLI's own terms (`docs/resources/tos-privacy.md`, as fetched by the researcher) call third-party use of its
OAuth "a violation of applicable terms and policies".

From mid-February 2026, paid Pro and Ultra users reported 403 "Terms of Service" suspensions after using OpenCode
or OpenClaw through Antigravity OAuth. Sources: discuss.ai.google.dev threads, and piunikaweb (2026-02-24). Google
reportedly restored accounts on 2026-02-27 (secondary).

No ban from a quota-only read was found. That is absence of evidence: the reports concern prompt traffic.

**Verdict: Hold.** Ship v2.0 store builds **without Gemini**.
- This is the clearest prohibition of the three, and the only one with confirmed enforcement on users' own
  accounts.
- Cooldown matches the named pattern on every point: Antigravity's client ID **and secret**, and a spoofed
  `antigravity/cli` User-Agent.
- No compliant change exists. Gemini CLI's client is barred by its own terms, and no public quota API exists.
- On GitHub, the Gemini source should carry a plain warning that Google may suspend the account.

Code changes for the store builds:
- **Android:** gate the Antigravity source and sign-in out of the `play` flavor.
  - Files: `GeminiSignIn.kt`, `data/AntigravitySignIn.kt`, `data/source/AntigravitySource.kt`, and the provider
    lists in `SettingsScreen.kt` and `ui/Rooms.kt`.
  - Leave `ANTIGRAVITY_CLIENT_SECRET` empty in the `play` `buildConfigField` (`app/build.gradle.kts:32`), so the
    secret is not in the AAB.
- **iOS:** the same gate in the App Store configuration (`Antigravity.swift` and its sign-in entry). Keep the
  secret out of the archive.
- `spec/mobile`: record that the store channel carries Claude and ChatGPT only, and update the store listings and
  screenshots that show Gemini.
- These changes are visible: Gemini disappears from Settings on store builds. Wireframe the empty slot first
  (CLAUDE.md §2).

## The permission requests

`release/outreach/2026-11-18-provider-permission-requests.md`: **still send all three, reworded.** Nothing found
replaces a written yes for Claude or Gemini.

- **Anthropic:** send.
  - Quote the Agent SDK's "unless previously approved".
  - Ask for that approval for a **read-only sign-in with scope `user:profile` only**, no inference, through
    Anthropic's own consent page.
  - Ask whether a usage-read scope or a registered client is possible.
- **OpenAI:** reword. The question is no longer "may we register": the open-source flow needs no approval.
  - Say Cooldown is moving to Sign in with ChatGPT's open-source dynamic registration.
  - Ask (1) whether a free open-source app published on the stores by a company qualifies as "open-source", and
    (2) whether a scope or endpoint exists for a user's own plan-usage windows. `wham/usage` is not documented.
- **Google:** send, with the expectation of a no.
  - Cite the terms' clause and say we have taken Gemini out of the store builds meanwhile.
  - Ask only whether a supported quota API, or a registered client, can be allowed.

## For the release session

- The store hold stands for Claude and Gemini.
- ChatGPT alone would not be a v2.0 worth shipping to the stores.
- **Inference:** TestFlight's public link is App Store distribution under Apple's guidelines too. Whether to keep
  it open is the release session's call.
- Nothing was submitted or sent from this session.
