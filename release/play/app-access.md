# Play Console — App access note for reviewers

Drafted 2026-10-01 for RUNBOOK Step 6, CCRM-90 (Listing Pack). It goes into **App content → App
access**.

## The answer

**"All or some functionality in my app is restricted"** — every function needs a sign-in to a
provider account. Cooldown creates no account of its own: it reads the usage limits of an account
the user already has at Anthropic (Claude), OpenAI (ChatGPT) or Google (Gemini). **From v2.0 the
reviewer needs no credentials:** the sample mode, CCRM-113 (Sample Port), shows every tab of the app
with three made-up accounts (wireframe `design/2026-11-10-v20-feedback-wireframes.html` §9). Rewritten
2026-11-11 for RUNBOOK Step D1; **check every step below on the v2.0 release build before pasting**
(the labels are the wireframe's; the build is Lane A's Step A5).

In the Console, add one set of instructions with **no username and no password** (Play's form
allows that when no credentials are needed; **re-read on the day** whether it still does, and what
its length limit is). The text below is 1,256 characters (Python `len()`).

## The reviewer instructions

```text
No account or password is needed. Cooldown has no account of its own: it shows the usage limits of the user's own Claude, ChatGPT or Gemini account. For review, use the built-in sample data:

1. Open the app. On the first screen, under "No Claude account yet", tap "Try with sample data". (Also in Settings > Accounts > + Add account, last row.)
2. Three made-up accounts open, one per service: Teams (Claude), Personal (ChatGPT) and Lab (Gemini). A violet "Sample data" banner shows on every screen. Every tab, chart, the Summary and Settings work with them. Nothing is fetched, sent or saved.
3. Tap "Exit" on the banner to leave.

The always-on notification (the foreground service), the status-bar icon, reset pings and home-screen widgets only ever show real accounts, so they stay off in the sample. The attached foreground-service video shows them with a real account: the switch in Settings > Alerts turned on, the notification and status-bar icon, a refresh, and the switch turned off.

Signing in with a real account, for completeness: Settings > Accounts > + Add account, then Claude (sign in in the browser, paste the code shown), ChatGPT (enter the short code at auth.openai.com) or Gemini (sign in on Google's own page, paste the code shown).
```

## Robin's decision: the sample mode (2026-11-10)

**Decided by Robin, 2026-11-10: "Port sample mode".** The Android app gets the iPhone app's
sample-data mode, built with the v2.0 feedback items (item 9), so the reviewer needs no
credentials: the answer becomes "tap *Try with sample data* on the first screen". The instructions
above are that rewrite (2026-11-11); they are checked on the v2.0 build before pasting
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
