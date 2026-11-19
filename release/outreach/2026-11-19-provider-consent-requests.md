# Consent requests to the three providers (reworded, 2026-11-19)

These drafts supersede `2026-11-18-provider-permission-requests.md`. They follow Robin's 2026-11-19 decisions (see
`design/research/2026-11-19-provider-signins-precedent.md` and `2026-11-19-mobile-apps-claude-antigravity.md`):
- Claude signs in with the `user:profile` scope only.
- ChatGPT keeps the Codex device code. The Sign in with ChatGPT spike returned 401 on the usage endpoint.
- Gemini ships in every build.
- Every sign-in shows an "Unofficial integration" note.

Robin sends each one himself, with his work address in the form's email field. Replies are logged in RUNBOOK Step 8.

| Provider | Where it goes | Attachments |
|---|---|---|
| Anthropic | Contact form at https://www.anthropic.com/contact-sales. This is the route Claude Code's legal page names. | Text only, so screenshots go in as links |
| OpenAI | Sign in with ChatGPT interest form, linked from https://developers.openai.com/siwc | Links |
| Google | https://cloud.google.com/contact, asking for the Antigravity / Gemini Code Assist team | Links |

Links used in all three:
- Source: https://github.com/robineam360/Cooldown
- Privacy policy: https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
- Screenshots: https://github.com/robineam360/Cooldown/tree/main/release/play/screenshots

---

## 1. Anthropic

**Subject:** Approval request: read-only usage meter for Claude subscribers (EAM360 Cooldown, open source)

Hello,

I'm Robin Richard Rajan, a product manager at EAM360. We have built **EAM360 Cooldown**, a free, open-source Android and
iOS app. It shows people their own subscription usage across Claude, ChatGPT and Gemini in one place.

**Why we built it.** Many people now pay for more than one AI subscription and switch between them during the day. Each
provider shows its limits in a different place, so it is hard to see at a glance how much of each plan is left. It is
also hard to tell whether a plan is the right size: whether to upgrade, or whether they are paying for more than they
use. Cooldown shows the 5-hour and weekly windows with a pace line, in the app, a notification and home-screen widgets.
The user can see where they will land, and when an upgrade would pay off.

**What it does with Claude**
- The user signs in on Anthropic's own consent page. The app requests only the `user:profile` scope, so it has no
  inference access.
- It reads the OAuth usage and profile endpoints, nothing else.
- It never sends prompts, never uses quota and never works around a limit.
- It polls every 15 minutes by default (5 at the fastest), with a 3-minute floor on manual refresh, and it backs off on
  429.
- Its User-Agent is honest: `Cooldown/<version>`.
- Tokens stay in the phone's secure storage. We run no server and collect no analytics.

To reach that endpoint, the sign-in uses Claude Code's public OAuth client today. Every sign-in screen in the app says
"Unofficial integration: not affiliated with Anthropic". Your Agent SDK documentation allows third-party Claude.ai login
when "previously approved".

**Our request:**
- Approval for this read-only, `user:profile`-only use;
- or, if you prefer, a client registered to Cooldown or a usage-read scope, and we will move to it.

We will follow any conditions you set, including rate limits, wording, or the use of your name and logo. The app shows
the Claude mark only to label a user's own account. If you ask us to remove Claude, we will.

The app is in public beta on TestFlight and GitHub, and we plan to publish it on Google Play and the App Store as
"EAM360 Cooldown".
- Source: https://github.com/robineam360/Cooldown
- Privacy: https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
- Screenshots: https://github.com/robineam360/Cooldown/tree/main/release/play/screenshots

A written answer, or the name of the right contact, would help us a great deal.

Thank you,
Robin Richard Rajan
Product Manager, EAM360

---

## 2. OpenAI

**Subject:** Consent request: read-only ChatGPT plan-usage meter (EAM360 Cooldown, open source)

Hello,

I'm Robin Richard Rajan, a product manager at EAM360. We have built **EAM360 Cooldown**, a free, open-source Android and
iOS app. It shows people their own subscription usage across ChatGPT, Claude and Gemini in one place.

**Why we built it.** Many people now pay for more than one AI subscription. Each provider shows its limits in a
different place, so it is hard to see how much of each plan is left, or whether a plan is the right size. For ChatGPT,
Cooldown shows a user's Codex 5-hour and weekly windows with a pace line, in the app, a notification and widgets. That
helps them decide whether Plus is enough or Pro would pay off.

**How it works today**
- The user signs in with the Codex CLI's device-code flow on auth.openai.com.
- The app reads only the Codex usage endpoint (`wham/usage`).
- It never sends prompts, never uses quota and never works around a limit.
- It polls every 15 minutes by default, with a 3-minute floor on manual refresh.
- Its User-Agent is honest: `Cooldown/<version>`.
- Tokens stay on the phone. We run no server and collect no analytics.
- Every sign-in screen says "Unofficial integration: not affiliated with OpenAI".

We tried to move to Sign in with ChatGPT's open-source registration. The token it issues is refused (HTTP 401) by the
usage endpoint, so we could not.

**Our questions:**
1. May Cooldown keep reading a user's own usage this way? A short written OK would help with store review.
2. Or could Sign in with ChatGPT offer a scope or endpoint for a user's own plan-usage windows? We would switch to it.
3. Does a free, open-source app that a company publishes on the stores count as "open-source" for Sign in with ChatGPT?

We will follow your branding guidelines and any rate limits or wording you ask for. If you would rather we stopped, we
will.
- Source: https://github.com/robineam360/Cooldown
- Privacy: https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
- Screenshots: https://github.com/robineam360/Cooldown/tree/main/release/play/screenshots

Thank you,
Robin Richard Rajan
Product Manager, EAM360

---

## 3. Google

**Subject:** Consent request: read-only Antigravity / Gemini quota meter (EAM360 Cooldown, open source)

Hello, and please route this to the Antigravity or Gemini Code Assist team if it isn't yours.

I'm Robin Richard Rajan, a product manager at EAM360. We have built **EAM360 Cooldown**, a free, open-source Android and
iOS app. It shows people their own subscription usage across Gemini, Claude and ChatGPT in one place.

**Why we built it.** People who pay for several AI plans find it hard to see how much of each is left, or whether a plan
is the right size. For Gemini, Cooldown shows the user's own Antigravity quota windows with a pace line, so they can see
when Pro or Ultra would pay off.

**How it works today.** We found no public quota API, so the app works like this:
- The user signs in on Google's own consent page, using the Antigravity CLI's OAuth client.
- The app calls the quota summary endpoint.
- It sends the Antigravity user agent, which the endpoint requires.

It only reads quota:
- It never sends prompts, never uses quota and never works around a limit.
- It polls every 15 minutes by default, with a 3-minute floor on manual refresh.
- Tokens stay on the phone. We run no server and collect no analytics.
- Every sign-in screen says "Unofficial integration: not affiliated with Google".

We know Google's API terms ask clients not to mask their identity, and we would much rather do this properly.

**Our request:**
1. Is there a supported API for a user's own Antigravity or Gemini quota?
2. Could a client registered and verified for Cooldown be allowed to read it?
3. If neither, may we continue as we do now, and on what conditions?

If you ask us to change or remove the Gemini sign-in, we will.
- Source: https://github.com/robineam360/Cooldown
- Privacy: https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
- Screenshots: https://github.com/robineam360/Cooldown/tree/main/release/play/screenshots

Thank you,
Robin Richard Rajan
Product Manager, EAM360
