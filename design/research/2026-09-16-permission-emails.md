# Permission emails — Anthropic and OpenAI

RUNBOOK.md Step 1 of CCRM-66 (Play Store Launch). Drafted 2026-09-16, revised the same day.
**Robin sends these from his own address; nothing here is sent by a session.**

**Send order, decided 2026-09-16:** the honest User-Agent (CCRM-68 (Honest Agent)) ships *before*
either email goes out, so each email describes what the app does today rather than promising a
change. Nothing here is sent until that is on `main`.

**Whether to send the Anthropic one at all is an open decision — read "Does a refusal cost us
GitHub?" below before sending it.**

---

## Read this before you send

The research behind these drafts changed what they had to say. Anthropic's position is **not**
an open question any more, and the email is written accordingly.

### Anthropic has already published a "no" that covers Cooldown

Anthropic's Claude Code **Legal and compliance** page
(<https://code.claude.com/docs/en/legal-and-compliance>), under *Authentication and credential
use*, says verbatim:

> **Developers** building products or services that interact with Claude's capabilities,
> including those using the Agent SDK, should use API key authentication through Claude Console
> or a supported cloud provider. **Anthropic does not permit third-party developers to offer
> Claude.ai login into their own applications, or to route requests through Free, Pro, or Max
> plan credentials on behalf of their users. Moreover, developers may not collect, store, or
> intermediate Claude.ai credentials or session tokens — sign-in to a Claude account must
> complete through Anthropic's own flow.**

and:

> Anthropic reserves the right to take measures to enforce these restrictions and may do so
> without prior notice.

Backing it, Consumer Terms of Service (last updated 2025-10-08), **§3.7**:

> Except when you are accessing our Services via an Anthropic API Key or where we otherwise
> explicitly permit it, to access the Services through automated or non-human means, whether
> through a bot, script, or otherwise.

and **§2**: *"You may not share your Account login information, Anthropic API key, or Account
credentials with anyone else."*

Cooldown's Claude path matches the prohibited description on its face: it is a third-party app
offering Claude.ai login inside itself, and it stores the resulting OAuth token on the device.
The one carve-out in the same document — *"Nor does it prevent an end user from signing in to the
**unmodified Claude Code binary** with their own Claude subscription"* — does not reach Cooldown,
which is a separate app reusing Claude Code's public client id. There is **no read-only carve-out**
in the published text; the rule is written about the authentication *method*, not about what the
token is then used for.

**So there is no framing of the Claude side that is inside Anthropic's terms today.** The email
below does not try to invent one. It discloses plainly, explains why the app was built, and asks
for the only two things that could make it legitimate: a sanctioned client, or a documented
endpoint.

### What this means for the arc, before you send

RUNBOOK.md Convention 7 says a written no stops the arc for that provider. Given the page above,
**asking Anthropic is more likely to produce a written no than silence.**

### Does a refusal cost us GitHub?

This is the question that decides whether the Anthropic email is worth sending at all, because
Robin's stated priority (2026-09-16) is the opposite of the runbook's assumption: **the app
existing on GitHub matters; the Play listing is a convenience he is willing to drop.**

Not legal advice — a lawyer is the right answer if certainty is needed. But the shape:

- **Anthropic's terms bind Robin as a user of the service.** They govern access to Claude, not
  the existence of a repository. A "no" to a permission question does not itself create a
  takedown right over the repo.
- **GitHub removes repositories on DMCA (copyright) or its Acceptable Use Policy.** The code is
  Robin's own; no Anthropic source is copied, and a client id is a short identifier, not a
  copyrightable work. There is no obvious hook.
- **Real enforcement here is technical, not legal:** block the client id, gate on User-Agent,
  flag or revoke accounts. That breaks the app whether or not the repo exists — and it is
  already live, since Anthropic has enforced server-side since April 2026.
- **What a written no does change:** continued public distribution stops being ambiguous and
  becomes knowing. That matters if anything ever escalates, and it matters for Robin's own
  account.

**Consequence: asking Anthropic is net negative for the goal Robin actually has.** Silence today
is ambiguity that costs nothing. The email converts it into a documented refusal that protects
nothing — it neither secures the repo nor prevents technical enforcement.

**Recommendation, revised 2026-09-16:**

1. **Ship the honest User-Agent regardless** (CCRM-68 (Honest Agent)). It is the least defensible
   thing in the repo and plausibly the most likely to trip enforcement.
2. **Do not send the Anthropic email** while the GitHub repo is the thing being protected. Keep
   README.md's disclosure honest so anyone installing chooses knowingly.
3. **Decide Play separately.** If Robin still wants a listing, a **ChatGPT-only Play build** needs
   only the OpenAI email — low risk, since OpenAI has drawn no line — and Claude stays on GitHub.

The Anthropic draft below is kept, finished and ready, for the case where Robin decides he wants
the answer on the record anyway. It is not the default path.

### OpenAI is a genuinely weaker case against us, but not a permission

OpenAI has published no equivalent clarification. Its Terms of Use still carry generic
prohibitions on programmatic extraction and reverse engineering that a lawyer could point at,
but OpenAI has not drawn Anthropic's bright line, and the ChatGPT path already sends an honest
`User-Agent: Cooldown/<version> (Android)`. Treat this as de facto tolerance, not consent — it
can change without notice.

### Recipient routes — confirm each one the day you send

| Provider | Route | Status |
|---|---|---|
| Anthropic | <https://www.anthropic.com/contact-sales> — a **web form, not an email** | **Verified.** The Legal and compliance page names this itself: *"For questions about permitted authentication methods for your use case, please contact sales."* The form's own copy is enterprise-oriented; send it anyway, since it is the route Anthropic designates. |
| Anthropic (trademark only) | marketing@anthropic.com | Verified on the Trademark Guidelines, but it presupposes *"an existing business relationship"*. Secondary. |
| Anthropic (fallback) | support@anthropic.com | General product support; use only if the form route dead-ends. |
| OpenAI | legal@openai.com | **Unverified — check before sending.** Reported to be published on <https://openai.com/brand/>, but that page returns 403 to automated fetches, so this was not confirmed first-hand. Open it in a browser and read the address off the page yourself. |
| OpenAI (branding) | partnercomms@openai.com | Same caveat — confirm on the page. |

Do **not** send to a security disclosure route (HackerOne, disclosure@anthropic.com, Bugcrowd).
This is not a vulnerability report.

---

## Email 1 — Anthropic

**To:** `__________` (the contact-sales form at anthropic.com/contact-sales, unless you find a
better route on the day)
**Subject:** Permission question: a read-only Claude usage app using the Claude Code OAuth client

Hello,

I've built a small open-source Android app called Cooldown that shows a person their own Claude
usage — the 5-hour and weekly windows — on their phone. It's MIT licensed and public:
https://github.com/robineam360/Cooldown

I want to be straightforward about how it works, because I think it conflicts with your Legal and
compliance page. The user signs in on their phone through your OAuth flow using the same public
client id the Claude Code CLI ships with; the token is stored in Android Keystore-backed storage
on the device and sent only to your endpoints. There is no server of mine, no analytics, and the
app never sends prompts or spends quota — it only reads `/api/oauth/usage`. It identifies itself
honestly as `Cooldown/<version> (Android)`; an earlier version borrowed the CLI's User-Agent, and
I've removed that.

I built it because I run separate work and personal Claude accounts. Both Claude and the CLI do
warn me, but only once I'm nearly at the limit — by then the useful decisions are gone. Seeing the
curve on my phone through the day lets me plan which account to use for what, and stop losing an
afternoon to a limit I could have worked around. It's marked clearly as unofficial and unaffiliated.

I'd like to list it on Google Play. Do you object? If there's a way to register a client of my
own, or a supported endpoint for a user reading their own usage, I'd far rather do that. If the
answer is no, I'll remove Claude support.

Thank you,
Robin Richard Rajan

---

## Email 2 — OpenAI

**To:** `__________` (legal@openai.com if you can confirm it on openai.com/brand)
**Subject:** Permission question: a read-only ChatGPT usage app using the Codex CLI OAuth client

Hello,

I've built a small open-source Android app called Cooldown that shows a person their own ChatGPT
usage limits on their phone. It's MIT licensed and public:
https://github.com/robineam360/Cooldown

How it works, plainly: the user signs in on their own phone through your device-code flow, using
the public client id the Codex CLI ships with, because that's the client the device flow is
registered for. The token is stored in Android Keystore-backed storage on the device and sent
only to your endpoints — there's no server of mine and no analytics. The app sends an honest
`User-Agent: Cooldown/<version> (Android)`, it is read-only, and it deliberately avoids the one
endpoint that would spend a credit. It reads an internal usage endpoint
(`chatgpt.com/backend-api/wham/usage`), which I know isn't documented for this.

I built it because I run separate work and personal accounts. ChatGPT does warn me, but only
once I'm nearly at the limit — by then the useful decisions are gone. Seeing the curve on my phone
through the day lets me plan which account to use for what. It's marked clearly as unofficial and
unaffiliated, and it shows the ChatGPT mark only next to the account it identifies.

I'd like to list it on Google Play. Do you object, or would you want anything changed? If there's
a route to register a client of my own, I'd prefer that.

Thank you,
Robin Richard Rajan

---

## Log

- Anthropic — sent: `____` · route: `____` · 30-day window ends: `____` · reply: `____`
- OpenAI — sent: `____` · route: `____` · 30-day window ends: `____` · reply: `____`

Any reply gets quoted verbatim here, and its consequence written into RUNBOOK.md Convention 7's
terms: proceed, proceed without that provider, or stop.
