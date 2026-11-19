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

Each provider has two versions:
- **Form version**: plain text, under 1,500 characters, for the form's message box. If the form has no subject field,
  leave the subject out; the first line carries it.
- **Email version**: fuller, for when a contact replies or gives an address.

Each text stands alone and is plain text, so it pastes without clean-up. No email address appears in any of them.

---

## 1. Anthropic

**Subject:** Approval request: read-only usage meter for Claude subscribers (no inference access)

### Form version

```text
For your partnerships or developer platform team: a request for approval to offer Claude.ai login in a third-party app.

EAM360 Cooldown is a free, open-source Android and iOS app that shows a person their own plan usage: the 5-hour and weekly windows, with a pace line. It is a read-only meter, not a client or harness. It asks only for the user:profile scope, so it has no inference access. It never sends prompts or uses quota.

EAM360's business is mobile apps for IBM Maximo. Cooldown is an exception. I built it for my own problem, with accounts at several AI providers: is each plan enough, or would an upgrade pay off? We do not plan to sell it or earn from it.

The user signs in on Anthropic's consent page, through Claude Code's public OAuth client. The app reads the usage and profile endpoints every 15 minutes. Tokens stay on the device. No server, ads or analytics. Every sign-in says "Unofficial integration: not affiliated with Anthropic".

Your Agent SDK documentation allows such a login when approved. We ask for that approval, or for a client or usage scope of our own. We will meet any conditions on rate, wording or the Claude mark, and remove Claude if you ask.

In public beta; Google Play and the App Store are next.
Source: https://github.com/robineam360/Cooldown
Privacy: https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
Screenshots: https://github.com/robineam360/Cooldown/tree/main/release/play/screenshots

Robin Richard Rajan, Product Manager, EAM360
```

### Email version

```text
Hello,

I'm Robin Richard Rajan, a product manager at EAM360. I'm writing to ask for Anthropic's approval for one narrow use of Claude.ai login: a read-only usage meter. It has no inference access, sends no prompts and uses no quota.

A word on who is asking. Our business is mobile apps for IBM Maximo, the enterprise asset management system. Cooldown is an exception to that: I built it recently to solve a problem I have myself, as someone with accounts at several AI providers, and that many people like me share. We have no intention of selling it or making money from it.

What the app is

EAM360 Cooldown is a free, open-source app for Android and iOS. More and more people hold accounts with several AI providers, and each provider shows its limits in a different place. Cooldown shows each account's 5-hour and weekly windows in one place, with a pace line, in the app, in a notification and in home-screen widgets. The user can see where they will land before the window resets, and whether their plan is the right size.

We think that is useful to Anthropic as well. Someone who sees that they reach the 5-hour window most days has a clear reason to move up a tier, and people who can see their limits have fewer questions about them.

What it does with Claude

The user signs in on Anthropic's own consent page. The app asks only for the user:profile scope and reads two things: the OAuth usage endpoint and the profile endpoint. It polls every 15 minutes by default and every 5 at the fastest, allows a manual refresh at most once in 3 minutes, and backs off on a 429. It identifies itself as Cooldown/<version>. Tokens stay in the phone's secure storage. We run no server, show no ads and collect no analytics. To reach the usage endpoint, the sign-in uses Claude Code's public OAuth client today, because we know of no other route.

What it does not do

- It does not send prompts, run models or use any quota.
- It does not share, pool or resell a subscription. Each person sees only their own account.
- It does not claim any connection to Anthropic. Every sign-in screen says "Unofficial integration: not affiliated with Anthropic", and the store listing will say the same.

What we are asking

Your Agent SDK documentation says third-party apps may offer Claude.ai login when previously approved. We would like to ask for that approval, in whichever form suits you:

1. Approval for this read-only, user:profile-only use as it stands; or
2. A client registered to Cooldown, or a usage-read scope, which we would move to straight away.

What we can commit to

- Any conditions you set: polling rate, wording, the scope, how we identify ourselves.
- Your brand guidelines. The app shows the Claude name and mark only to label the user's own account. If you prefer plain text, we will switch.
- If you ask us to remove Claude from the app, we will do so in the next release.
- All of the code is public, so each of these points can be checked.

Where it stands

Cooldown is in public beta on TestFlight and GitHub. We are about to publish it on Google Play and the App Store as "EAM360 Cooldown", and we would like to do that with your answer in hand.

Source: https://github.com/robineam360/Cooldown
Privacy policy: https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
Screenshots: https://github.com/robineam360/Cooldown/tree/main/release/play/screenshots

I am the contact for anything about this app, and I can send a test build or a short screen recording if that helps. If this belongs with another team, I would be grateful for a name.

Thank you for your time,

Robin Richard Rajan
Product Manager, EAM360
```

---

## 2. OpenAI

**Subject:** Sign in with ChatGPT for a read-only plan-usage meter (EAM360 Cooldown, open source)

### Form version

```text
We would like to adopt Sign in with ChatGPT, and one thing stops us: we need a way to read a user's own plan usage.

EAM360 Cooldown is a free, open-source Android and iOS app. It shows a person their usage across their AI plans: for ChatGPT, the Codex 5-hour and weekly windows with a pace line, so they can see whether Plus is enough or Pro would pay off. It is read-only: it never sends prompts or uses quota.

EAM360's business is mobile apps for IBM Maximo. Cooldown is an exception: I built it for my own problem, with accounts at several AI providers. We do not plan to sell it or earn from it.

Today the user signs in with the Codex CLI device code, and the app reads the Codex usage endpoint every 15 minutes. Tokens stay on the device. No server, ads or analytics. Every sign-in says "Unofficial integration: not affiliated with OpenAI".

We tried your open-source registration. The usage endpoint refuses its token (HTTP 401). So we ask:

1. Could it offer a scope or endpoint for a user's own usage windows? We would switch.
2. Until then, may we keep the current read-only route?

We will follow your brand guidelines and any limits you set, and remove ChatGPT if you ask. In public beta; Google Play and the App Store are next.
Source: https://github.com/robineam360/Cooldown
Privacy: https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
Screenshots: https://github.com/robineam360/Cooldown/tree/main/release/play/screenshots

Robin Richard Rajan, Product Manager, EAM360
```

### Email version

```text
Hello,

I'm Robin Richard Rajan, a product manager at EAM360. We would like to adopt Sign in with ChatGPT in our app, and I'm writing about the one thing that stops us: the app needs to read a user's own plan usage, and the Sign in with ChatGPT token cannot do that yet.

A word on who is asking. Our business is mobile apps for IBM Maximo, the enterprise asset management system. Cooldown is an exception to that: I built it recently to solve a problem I have myself, as someone with accounts at several AI providers, and that many people like me share. We have no intention of selling it or making money from it.

What the app is

EAM360 Cooldown is a free, open-source app for Android and iOS. More and more people hold accounts with several AI providers, and each provider shows its limits in a different place. Cooldown shows each account's usage windows in one place, with a pace line, in the app, in a notification and in home-screen widgets. For ChatGPT, that is the user's Codex 5-hour and weekly windows.

The user can see whether their plan is the right size. We think that helps OpenAI too: someone who sees that they reach the 5-hour window most days has a clear reason to move from Plus to Pro, and people who can see their limits have fewer questions about them.

How it works today

The user signs in with the Codex CLI's device-code flow on auth.openai.com. The app then reads one thing, the Codex usage endpoint (wham/usage). It polls every 15 minutes by default and every 5 at the fastest, and allows a manual refresh at most once in 3 minutes. It identifies itself as Cooldown/<version>. Tokens stay in the phone's secure storage. We run no server, show no ads and collect no analytics.

It is read-only. It never sends prompts, never uses quota and never shares a subscription between people. Every sign-in screen says "Unofficial integration: not affiliated with OpenAI".

What we tried

We registered through the open-source route for Sign in with ChatGPT and signed in with it. The sign-in works, but the usage endpoint refuses the token it issues (HTTP 401), so we stayed on the device code.

What we are asking

1. Could Sign in with ChatGPT offer a scope, or an endpoint, for a user's own plan-usage windows? We would switch to it as soon as it exists, and we are glad to test it early.
2. Until then, may Cooldown keep reading a user's own usage through the Codex device code? A short written OK would be enough.
3. Does a free, open-source app that a company publishes on the app stores qualify as open source for Sign in with ChatGPT?

What we can commit to

- Your brand guidelines. The app shows the ChatGPT name and mark only to label the user's own account. If you prefer plain text, we will switch.
- Any polling rate or wording you ask for.
- If you would rather we removed ChatGPT from the app, we will do so in the next release.
- All of the code is public, so each of these points can be checked.

Where it stands

Cooldown is in public beta on TestFlight and GitHub. We are about to publish it on Google Play and the App Store as "EAM360 Cooldown".

Source: https://github.com/robineam360/Cooldown
Privacy policy: https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
Screenshots: https://github.com/robineam360/Cooldown/tree/main/release/play/screenshots

I am the contact for anything about this app, and I can send a test build or the exact request and response from our spike if that helps.

Thank you,

Robin Richard Rajan
Product Manager, EAM360
```

---

## 3. Google

**Subject:** For the Antigravity / Gemini Code Assist team: supported way to read a user's own quota

### Form version

```text
Please pass this to the Antigravity / Gemini Code Assist product team. It is not a sales enquiry: we are an app developer asking for a supported way to read a user's own Antigravity quota.

EAM360 Cooldown is a free, open-source Android and iOS app. It shows a person their usage windows across their AI plans, with a pace line, so they can see whether Pro or Ultra would pay off. It is read-only: it never sends prompts or uses quota.

EAM360's business is mobile apps for IBM Maximo. Cooldown is an exception: I built it for my own problem, with accounts at several AI providers. We do not plan to sell it or earn from it.

We found no public quota API. So today the user signs in on Google's consent page through the Antigravity CLI's OAuth client, and the app reads the quota summary endpoint every 15 minutes, with the CLI's user agent, which the endpoint requires. We would rather identify as Cooldown. So we ask:

1. Is there a supported API for a user's own quota?
2. If not, could a client registered to Cooldown read it?
3. Until then, may we continue as we do now?

Tokens stay on the device. No server, ads or analytics. Every sign-in says "Unofficial integration: not affiliated with Google". We will remove Gemini if you ask.

Source: https://github.com/robineam360/Cooldown
Privacy: https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
Screenshots: https://github.com/robineam360/Cooldown/tree/main/release/play/screenshots

Robin Richard Rajan, Product Manager, EAM360
```

### Email version

```text
Hello,

This is a question for the Antigravity / Gemini Code Assist product team, and I would be grateful if you could pass it on if it has reached someone else. I'm Robin Richard Rajan, a product manager at EAM360. We are looking for a supported way for an app to read a user's own Antigravity quota, with that user's consent.

A word on who is asking. Our business is mobile apps for IBM Maximo, the enterprise asset management system. Cooldown is an exception to that: I built it recently to solve a problem I have myself, as someone with accounts at several AI providers, and that many people like me share. We have no intention of selling it or making money from it.

What the app is

EAM360 Cooldown is a free, open-source app for Android and iOS. More and more people hold accounts with several AI providers, and each provider shows its limits in a different place. Cooldown shows each account's usage windows in one place, with a pace line, in the app, in a notification and in home-screen widgets. For Gemini, that is the user's own Antigravity quota windows.

The user can see whether their plan is the right size. We think that helps Google too: someone who sees that they reach their quota most days has a clear reason to move to Pro or Ultra, and people who can see their limits have fewer questions about them.

How it works today

We could not find a public API for this quota, so the app takes the only route we know. The user signs in on Google's own consent page, through the Antigravity CLI's OAuth client. The app then reads one thing, the quota summary endpoint, and sends the Antigravity CLI's user agent, because the endpoint answers only to that. It polls every 15 minutes by default and every 5 at the fastest, and allows a manual refresh at most once in 3 minutes. Tokens stay in the phone's secure storage. We run no server, show no ads and collect no analytics.

It is read-only. It never sends prompts, never uses quota and never shares a subscription between people. Every sign-in screen says "Unofficial integration: not affiliated with Google".

We would much rather do this under our own name: a client registered to Cooldown, a user agent that says Cooldown, and an endpoint you are happy for us to call. That is the reason for this message.

What we are asking

1. Is there a supported API, or one planned, for a user's own Antigravity or Gemini quota?
2. If not, could a client registered and verified for Cooldown be allowed to read it? We will go through whatever verification you require.
3. Until one of those exists, may we continue as we do now, and on what conditions?

What we can commit to

- A move to the supported route as soon as you name one.
- Your brand guidelines. The app shows the Gemini name and mark only to label the user's own account. If you prefer plain text, we will switch.
- Any polling rate or wording you ask for.
- If you would rather we removed Gemini from the app, we will do so in the next release.
- All of the code is public, so each of these points can be checked.

Where it stands

Cooldown is in public beta on TestFlight and GitHub. We are about to publish it on Google Play and the App Store as "EAM360 Cooldown".

Source: https://github.com/robineam360/Cooldown
Privacy policy: https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md
Screenshots: https://github.com/robineam360/Cooldown/tree/main/release/play/screenshots

I am the contact for anything about this app, and I can send a test build or a short screen recording if that helps.

Thank you,

Robin Richard Rajan
Product Manager, EAM360
```
