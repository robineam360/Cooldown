# Play Console — App access note for reviewers

Drafted 2026-10-01 for RUNBOOK Step 6, CCRM-90 (Listing Pack). It goes into **App content → App
access**.

## The answer

**"All or some functionality in my app is restricted"** — every function needs a sign-in to a
provider account. Cooldown creates no account of its own: it reads the usage limits of an account
the user already has at Anthropic (Claude) or OpenAI (ChatGPT).

## The reviewer instructions (draft)

```text
Cooldown shows the usage limits of the user's own Claude or ChatGPT account. It has no account of
its own, and nothing works until one provider account is signed in.

To sign in:
- ChatGPT (any paid plan): Settings → Accounts → "+ Add account" → ChatGPT. The app shows a short
  code. Open auth.openai.com/codex/device in any browser, sign in with the ChatGPT account below,
  enter the code and approve. The account appears with its numbers.
- Claude (Pro, Max or Team plan; a Free plan signs in but the provider returns no numbers):
  Settings → Accounts → "Sign in on this phone". Sign in on claude.ai with the account below, tap
  Authorize, copy the code shown, return to the app and tap Paste → Finish sign-in.

Then: Settings → Alerts → Always-on notification shows the foreground-service notification.

Account: <see below — Robin decides>
```

## Robin's decision: the sample mode (2026-11-10)

**Decided by Robin, 2026-11-10: "Port sample mode".** The Android app gets the iPhone app's
sample-data mode, built with the v2.0 feedback items (item 9), so the reviewer needs no
credentials: the answer becomes "tap *Try with sample data* on the first screen". Rewrite the
instructions above on the v2.0 build once the Android wireframe is approved and built
(CONSOLE-STEPS.md step 12).

The options considered before, kept for the record (no longer the plan):

1. **A dedicated paid test account** (for example a ChatGPT Plus account made only for review, with
   2-step verification off, or with a code route Play accepts). It costs a subscription, and the
   provider's terms on shared accounts apply.
2. **No credentials, plus a video:** explain that sign-in uses the reviewer's own provider account,
   and attach a screen recording of the whole flow. Play's help says this may be accepted when
   credentials cannot be given, but it risks a rejection for "insufficient access".
3. **Both:** a test account and the video.

**Re-read on the day:** Play's App access options and what it accepts for OAuth-only apps.

Never put the credentials in this repo. They go only into the Console field.
