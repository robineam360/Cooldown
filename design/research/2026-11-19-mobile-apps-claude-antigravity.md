# How phone apps sign in to Claude and Antigravity (2026-11-19)

Follow-up to [2026-11-19-provider-signins-precedent.md](2026-11-19-provider-signins-precedent.md). Robin asked for real phone apps, not
guesses.

**How this was gathered.**
- Three read-only Sonnet researchers searched GitHub. One covered Android apps that sign in to Claude, one covered
  iPhone and cross-platform apps that sign in to Claude, and one covered phone apps that sign in to Antigravity.
- They also read the support and privacy pages behind the closed-source App Store apps.
- Clones are in `~/Projects/_research/mobile/`. They were read with grep only, never built.
- The seat re-checked the key lines in the clones itself, plus three store listings: OpenMinis on the App Store,
  Headroom on Play, and Usage Limits Viewer's release policy.
- GitHub code search hit its rate limit partway through, so coverage is good but not complete.

## Claude: what the phone apps do

No phone app has its own OAuth client, and none uses a sanctioned route. Every app that signs in on the phone does
it one of three ways.

| App | Where it is | Sign-in on the phone | Scope | User-Agent |
|---|---|---|---|---|
| **OpenMinis** (6,000 GitHub stars) | **App Store, live** (updated 2026-11-14) | **Claude Code's client ID**, PKCE, loopback on `localhost:54545` (`ClaudeOAuthManager.swift:23-24`) | Full Claude Code set | Spoofs `claude-cli/2.1.280` |
| **Headroom** | **Google Play, live** ("Headroom: AI Usage Limits"); iOS on TestFlight only | **Claude Code's client ID**, loopback, plus Anthropic's own code-paste page `platform.claude.com/oauth/code/callback` | Full set + `user:plugins` | Spoofs `claude-cli/<v>` |
| **UsageNow** | **App Store, live** | "service's own sign-in page" plus a local listener. Inference: Claude Code's client | Unknown | Unknown |
| **Runlow** | **App Store, live** | **Pasted claude.ai session key** (cookie) | n/a | Unknown |
| **CUStats Go** | **App Store, live** | **Pasted session token** (cookie) | n/a | Unknown |
| Tempo For Claude, CLI Pulse | App Store, live | **No sign-in on the phone.** A Mac app syncs the numbers through iCloud or the developer's cloud | n/a | n/a |
| Usage Limits Viewer (iOS and Android) | GitHub only | Claude Code's client, loopback `localhost:54545` | Full set | Honest |
| android-claude-usage (timofeykhodykin) | GitHub only | Claude Code's client, Custom Tab, loopback or Anthropic's paste page | **`user:profile` only** | Honest |
| claude-pulse-android, CodexMeter, Codex-Meter, ward-pulse, gdevenyi | GitHub only | Claude Code's client (paste page, loopback or in-app WebView) | Full set or a large part of it | Most spoof `claude-cli` or `claude-code` |
| claude-usage-android (drknowhow) | GitHub only | WebView login that captures the `sessionKey` cookie | n/a | WebView default |

**Enforcement found: none.**
- No issue, commit or doc mentions an Anthropic block, a store rejection or a removal.
- The breakages on record are technical. Cloudflare challenges on the old `console.anthropic.com` host (OpenMinis
  #360), and model access gated on the claimed Claude Code version (OpenMinis #301 and #423). Those gates are why the
  other apps spoof the User-Agent.

**One app holds back on purpose.** Usage Limits Viewer's `docs/release.md:97` says: "Before public distribution,
obtain provider permission … borrowed application identities are outside project policy." That is the same position
we took on 2026-11-18.

**Where Cooldown would stand.** Cooldown already uses Claude Code's client, Anthropic's own sign-in page and code
page, and an honest `Cooldown/<ver>` User-Agent. With the scope narrowed to `user:profile`, it would be the most
conservative Claude app on either store:
- It would ask for less than OpenMinis or Headroom.
- It would not pretend to be the CLI.
- It would never touch a claude.ai cookie, unlike Runlow and CUStats Go.

The rule on Anthropic's page still covers it. What has changed is that this point is now a question of risk, not a
question of whether a route exists.

## Antigravity: what the phone apps do

| App | Where it is | Client | User-Agent |
|---|---|---|---|
| Usage Limits Viewer | GitHub only, "not in the app stores yet" | **Antigravity's client ID and secret** (`ProviderEndpoints.kt:139`), loopback | Spoofs `antigravity/hub/2.25.0 … os_type=darwin` |
| quota-widget (657kbps) | GitHub releases | Same client and secret (`AntigravityClient.kt:436`), WebView | Spoofs `antigravity/hub/2.32.1` |
| CodexMeter, QuotaRabbit | GitHub only | Same client (`AppContainer.kt:360`) | Spoofs `antigravity/hub/2.9.1` |
| Codex-Meter (BenItBuhner) | GitHub only | The client is injected at build time. Inference: Antigravity's | Not checked |
| CodexBar-android | GitHub | Gemini CLI's route on `cloudcode-pa` | Its own |

- **No phone app with Antigravity sign-in was found on Play or the App Store.**
- Every app uses Antigravity's own client and secret, and pretends to be Antigravity. Only quota-widget warns that
  "Google may flag related accounts".
- Usage Limits Viewer's comment says the quota scopes are "bound to this specific client", so a self-registered
  client can't read them. That is the author's claim, not verified.
- No official API for a consumer's Gemini or Antigravity quota was found. docs.cloud.google.com/gemini/docs/quotas
  lists the limits but gives no way to read them.
- Google's terms ban this use and name account suspension as the penalty. Google enforced this in February 2026.

## Options for Robin to decide

**Claude**
1. **Store release with the narrowed scope.** Sign-in stays on Anthropic's pages, the User-Agent stays honest, and
   the request to Anthropic goes out at the same time.
   - Precedent: OpenMinis (App Store) and Headroom (Play) do more with the same client, and both are live.
   - Risk: the rule text is explicit, and the publisher is EAM360's organisation account. A removal counts against
     every EAM360 app.
2. **The same, but published from a personal developer account.** This is how the store precedents are published.
   EAM360's account stays out of it.
3. **Hold.** Ship through TestFlight and the GitHub APK only, until Anthropic answers.

**Gemini (Antigravity)**
1. **Keep Gemini out of the store builds.** Keep it in the GitHub APK and TestFlight builds with a plain warning that
   Google may suspend the account. This is the seat's recommendation: no store precedent exists, and the penalty
   falls on the user's own Google account.
2. **Remove Gemini everywhere** until Google answers.

**Both**
- **The fallback Robin named.** TestFlight plus the GitHub APK only, with no Play or App Store release. This removes
  the store-policy risk.
- **Inference:** a public TestFlight link still goes through Beta App Review under the same guidelines. So it lowers
  the exposure to the EAM360 account but does not remove it.
- It does not change the risk to users' own accounts. Only removing Gemini changes that.

**Seat's recommendation:**
- **Claude: option 1**, with the scope narrowed first. The evidence shows two stores accepting the same method, done
  more aggressively, for months, with no enforcement. Narrowing the scope removes the one thing that makes a usage
  meter look like a harness.
- **Gemini: option 1.**
- This needs Robin's call because of the EAM360 account. Option 2 is the safer variant if that account matters more
  than the listing.

## Spike: Claude sign-in with `user:profile` only (2026-11-19)

The script copied Cooldown's Android request (`OAuthSignIn.begin`, `ApiClient`) exactly: authorize at
`claude.com/cai/oauth/authorize`, Anthropic's code-paste page, state equal to the verifier, and token calls at
`platform.claude.com/v1/oauth/token`. Only the scope changed. Robin signed in twice. Both runs used the same Team premium
seat: `claude_team`, `team_tier_1`, `default_claude_max_5x`.

| Step | Result |
|---|---|
| Consent and token exchange | HTTP 200. Anthropic granted **`user:profile` only**. Refresh token issued. Lifetime 8 h |
| `/api/oauth/usage` | HTTP 200. `five_hour`, `seven_day`, `extra_usage`, `limits`, `spend` and `weekly_scoped_shares` were all present |
| `/api/oauth/profile` | HTTP 200. `organization_type`, `rate_limit_tier` and `seat_tier` were present, with the account fields |
| Refresh | HTTP 200. The scope was still `user:profile`, and usage with the refreshed token returned 200 |

**Conclusion:** the July 2026 note in `OAuthSignIn.kt:30` ("a reduced set was rejected") was really the old
`claude.ai/oauth/authorize` endpoint failing, not the scope. Nothing Cooldown shows depends on the wider scopes.

**Not tested:** a personal Pro or Max account. The endpoints are the same, so the Fold 7 device pass should check one
before release.

**Change:**
- Set `SCOPE = "user:profile"` in `data/OAuthSignIn.kt:35` and fix the comment above it.
- Make the same change to the iOS `OAuthSignIn.swift` scope and to the `ContractTests.swift:467` expectation.
- Old tokens keep their wide scope until the user signs in again.
