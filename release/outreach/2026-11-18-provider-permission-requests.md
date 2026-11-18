# Permission requests to the three providers (drafts, 2026-11-18)

Robin's decision, 2026-11-18: **ask the providers first** before submitting to Google Play or the App Store
(`design/research/2026-11-18-provider-terms-store-review.md`). Robin sends each message himself; nothing here is sent
by a session. Replies are logged in RUNBOOK Step 8.

| Provider | Where to send | Why there |
|---|---|---|
| Anthropic | https://www.anthropic.com/contact-sales | The only route Claude Code's legal page names for questions about permitted authentication |
| OpenAI | The Sign in with ChatGPT interest form, linked from https://developers.openai.com/siwc | OpenAI's official route for third-party apps signing in with ChatGPT |
| Google | Google Cloud sales contact (https://cloud.google.com/contact), asking to be routed to the Antigravity / Gemini Code Assist team | No dedicated channel found; public forums are not the place for this |

---

## 1. Anthropic

**Subject:** Permission request: read-only usage meter for Claude subscribers (EAM360 Cooldown)

Hello,

I'm Robin Richard Rajan, product manager at EAM360. We've built **EAM360 Cooldown**, a free Android and iOS app that
shows Claude Pro, Max and Team subscribers their own 5-hour and weekly usage, with a pace line and reset times, in a
notification and home-screen widgets. It is read-only: it never sends prompts, never spends quota and never works
around a limit. It helps people plan their usage and see when an upgrade would pay off.

Today the app reads `api.anthropic.com/api/oauth/usage` after the user signs in through Anthropic's own page, using
Claude Code's OAuth client. Your Claude Code legal and compliance page says third-party apps may not offer Claude.ai
login, so we have held our Google Play and App Store submissions until we hear from you.

Could Anthropic either permit this read-only use, or point us to a supported way to show a subscriber their own usage
windows, for example an OAuth client registered to our app with a usage-read scope? We're glad to share the source
(it is open source), the privacy policy and a demo.

Thank you,
Robin Richard Rajan, EAM360 · robin@eam360.com

## 2. OpenAI

**Subject:** Sign in with ChatGPT: read-only plan-usage meter (EAM360 Cooldown)

Hello,

EAM360 has built **EAM360 Cooldown**, a free, open-source Android and iOS app that shows ChatGPT Plus and Pro users
their own Codex usage windows (5-hour and weekly), with reset times, in a notification and widgets. It is read-only:
no prompts, no quota spent, no limits worked around.

Today it signs in with the Codex CLI's device-code flow and reads `chatgpt.com/backend-api/wham/usage`. Before we
publish on Google Play and the App Store, we'd like to move to a supported route. Could we register the app through
Sign in with ChatGPT, and is there (or could there be) a scope or endpoint for a user's own plan-usage limits? If the
current approach is acceptable meanwhile, a short written confirmation would help with store review.

Thank you,
Robin Richard Rajan, EAM360 · robin@eam360.com

## 3. Google

**Subject:** Permission request: read-only Antigravity / Gemini quota meter (EAM360 Cooldown)

Hello,

EAM360 has built **EAM360 Cooldown**, a free, open-source Android and iOS app that shows people their own Gemini usage
windows from Antigravity, alongside Claude and ChatGPT, so they can plan their work and see when an upgrade helps. It
is read-only: it never sends prompts or spends quota.

The only source we found for this data is the Cloud Code endpoint the Antigravity CLI uses
(`retrieveUserQuotaSummary`), which answers only to that CLI's OAuth client. We don't want to present another
product's identity, so we have held our Google Play and App Store submissions. Could you tell us whether there is a
supported API for a user's own Antigravity or Gemini quota, or whether an OAuth client registered and verified for our
app could be allowed to read it? Please route this to the right team if it isn't yours.

Thank you,
Robin Richard Rajan, EAM360 · robin@eam360.com
