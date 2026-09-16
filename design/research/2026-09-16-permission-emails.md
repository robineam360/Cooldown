# Permission emails — Anthropic and OpenAI

RUNBOOK.md Step 1 of CCRM-66 (Play Store Launch). Drafted 2026-09-16. **Robin sends these from
his own address; nothing here is sent by a session.** Fill in the recipient field on each, send,
then write the dates into Step 1's Log line and the 30-day silence window (send date + 30).

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
**asking Anthropic is more likely to produce a written no than silence.** That is a real cost and
you should choose it deliberately:

- **Send anyway** (recommended). The repo is public, the README already says the app impersonates
  the CLI, and a Play listing puts it in front of exactly the people who enforce this. Asking is
  the honest move and it is also the safer one — being told no privately is better than being
  enforced against publicly, and the reply is the record of good faith the runbook wants.
- **Ship Play with ChatGPT only**, and keep Claude in the GitHub build. Clean, and it respects the
  published rule without needing anyone's permission.
- **Don't ask.** Not recommended. It doesn't make the rule go away, and it forfeits the chance
  that they say "use this instead."

There is also a smaller, separate thing to fix regardless of the answer: Cooldown sends
`User-Agent: claude-code/<version>` to Anthropic's undocumented `/api/oauth/usage`, which
README.md:37-45 already calls impersonating the CLI. **Whatever else is decided, stop sending a
false User-Agent before the app goes on a store.** It is the single hardest fact to defend, it
is trivially fixable, and the email says it will stop.

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
app never sends prompts or spends quota — it only reads `/api/oauth/usage`. It also currently
identifies itself as `claude-code/<version>`, which I should not have done; I'm changing it to an
honest Cooldown User-Agent regardless of your answer.

I built it because I run separate work and personal Claude accounts and kept hitting limits
mid-task with no warning. It's marked clearly as unofficial and unaffiliated.

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

I built it because I run separate work and personal accounts and kept hitting limits mid-task
with no warning. It's marked clearly as unofficial and unaffiliated, and it shows the ChatGPT
mark only next to the account it identifies.

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
