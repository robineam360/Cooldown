# Closed test plan — Cooldown v2.0 on Google Play

Written 2026-11-10 for CCRM-66 (Play Store Launch). The Console clicks are in
[CONSOLE-STEPS.md](CONSOLE-STEPS.md); its § Policy check holds the sources for every rule quoted
here.

**The rule** (read 2026-11-10, <https://support.google.com/googleplay/android-developer/answer/14151465>):
a personal developer account created after 13 Nov 2023 must run a closed test with **at least 12
testers opted in continuously for 14 days** before it may apply for production access. Robin's
account will be new, so it applies.

**When it starts** (Robin, 2026-11-10): **after the eight v2.0 feedback items are built and land**
(his Fold 7 notes of 2026-11-10: the Duet notification, "7d", the day grid, since/until, the Pace map's
1% floor, the Personal/Work tag, the Pace map chips, the Summary share). The test runs on the v2.0
build that will also be the GitHub release, so testers judge what ships.

## Timeline

**Day 0 = the day the eight items land on `main` and the v2.0 release commit is cut.** Phases A–C
of CONSOLE-STEPS.md (account, identity, app record, declarations) run **before** Day 0, in parallel
with the build, because identity checks can take days.

| When | What | Who |
|---|---|---|
| Now → Day 0 | Register the personal account, identity and device verification, create the app, fill the declarations that do not depend on the build (CONSOLE-STEPS.md A–C) | Robin |
| Now → Day 0 | Recruit testers and collect their Google account emails (§ Recruiting); create the Google Group | Robin |
| Before Day 0 | Decide the review path (sample mode port or test account / video) and the screenshot crop (V2.0-ASSET-CHECK.md) | Robin |
| Day 0 | v2.0 release commit; CI green; `bundlePlayRelease` and RELEASING.md §5's checks; the GitHub APK from the same commit | session |
| Day 0–1 | Play App Signing with the existing key (PEPK); upload the AAB to **Internal testing**; check the Play-signed APK's SHA-256 = the trusted digest | Robin, session checks |
| Day 1 | The same AAB to the **Closed** track; release notes; countries; start rollout | Robin |
| Day 1–3 | Google reviews the closed release (hours to a few days); the opt-in link works once it is approved | Google |
| Day 2–3 | Send the invite with the opt-in link ([TESTER-INVITE.md](TESTER-INVITE.md)); chase until **15+** have opted in and installed | Robin |
| **Clock start** | The first day **12 or more** testers are opted in at once. Call it **Day S** (≈ Day 3 if recruiting is done beforehand) | — |
| Day S + 7 | Mid-test check-in message; count opted-in testers in the Console; triage feedback | Robin, session triages |
| Day S + 14 | The requirement shows met on the Dashboard; **Apply for production** with the answers in § Production application | Robin |
| Day S + 14 → +21 | Google's decision (it says usually 7 days or less) | Google |
| After approval | Production release (staged rollout), the listing goes public | Robin |

**Realistic arithmetic:** production is about **Day 0 + 3 weeks** if 15 testers are ready on
Day 0, and about **Day 0 + 4–5 weeks** if recruiting starts on Day 0 or the count dips below 12
once. GitHub (APK) and TestFlight carry v2.0 to everyone meanwhile.

**v2.0 on GitHub does not wait for Play.** The GitHub release can publish on Day 0 as usual
(RELEASING.md). Every fix during the test is a new versionCode on **both** channels.

## Recruiting 12+ testers

- **Target 15–18 opted-in testers**, so two or three drop-outs never take the count below 12. A dip
  below 12 is treated as restarting the 14 days (§ The clock).
- **Who:** colleagues at Sedin / EAM360 and friends who already use Claude, ChatGPT or Gemini on a
  paid plan — they get real value, so they stay. A tester without a paid plan can still open the
  app, but a Claude Free account shows no numbers, so prefer paid users.
- **What each tester needs:** an Android phone (Android 12+, the app's minSdk 31), a Google
  account signed in to the Play Store on it, and that account's email for the list.
- **The list:** a **Google Group** (for example `cooldown-testers@googlegroups.com`) set to
  "anyone can ask to join" or Robin adds them. The group is the closed track's tester list, so
  adding or removing a person never touches the Console. Alternatively paste the emails into a
  Console email list.
- **Ask for 14 days of staying opted in, not 14 days of daily use.** The rule counts opted-in
  days. Opening the app a few times and leaving the notification on is enough; leaving the test
  or uninstalling early is what hurts.
- **Never pay for testers or use tester-exchange services.** Google's production questions ask
  how engaged testers were; real users with real feedback are the answer that passes.

## What testers do, and how feedback comes back

1. Join the Google Group (or be added), open the opt-in link, tap **Become a tester**, install
   from Play with the same Google account.
2. If they already run the GitHub APK: Play shows the app as installed (same package, same key,
   same versionCode), so **no uninstall is needed** — but to count, they must still opt in and be
   on the Play build. Simplest: uninstall the GitHub APK first (they sign in again), then install
   from Play. Their choice; say it in the invite.
3. Sign in one account, turn on Settings → Alerts → Always-on notification, add a widget, open the
   Summary tab. Use it normally for the 14 days.
4. **Feedback goes to one place:** email `robin@eam360.com` (or the GitHub issues page for those
   who have an account). Play's private tester feedback also arrives in the Console (**Ratings and
   reviews → Testing feedback**). Crash reports stay on the phone until the tester shares them
   from Settings → Diagnostics.
5. **Mid-test check-in (Day S + 7):** a short message: anything broken, anything confusing, one
   thing you'd change. Keep the replies; they are the production application's evidence.

Every piece of feedback is triaged the usual way: a defect gets a CCBG-N in BUGS.md, a feature idea a
note in ROADMAP.md. Fixes that change what the user sees still need a wireframe (CLAUDE.md §2).

## Builds during the 14 days

- **A new build goes to the closed track by creating a new release** on that track with a new
  versionCode (the same one the GitHub fix release uses); Play reviews it, and testers get it as a
  normal Play update.
- **Does a new build reset the clock?** **UNVERIFIED.** Google's page does not say; most secondary
  sources say no (Google counts opted-in days, not versions), one says testers must update and stay
  opted in. **Rule here:** ship a fix during the test only when it matters (a crash, a wrong
  number); batch cosmetic fixes until after the application. After any new closed release, check
  the Dashboard's tester count and day count the next day.
- **Never pause or stop the closed track**, never remove the tester list or the countries mid-test,
  and never let the release be replaced by one that fails review — each of these can drop testers'
  access (**UNVERIFIED** whether each one resets the clock; avoid them all).
- A **Play-only** fix does not exist: Play refuses a reused versionCode, so a fix is a new release
  on both channels (RELEASING.md §5).

## The clock — what resets it

| Event | Effect | Confidence |
|---|---|---|
| Fewer than 12 testers opted in at the same time | The continuous count breaks; a fresh 14 days from when 12 are back | Implied by Google's "opted in continuously"; exact counting UNVERIFIED |
| A tester opts out and back in | Their days must still be consecutive; the gap does not count | Google's page |
| A new build on the closed track | Probably nothing | UNVERIFIED (secondary sources) |
| Adding testers | Nothing; new ones start their own 14 days, and the count only has to reach 12 continuous | Secondary sources; UNVERIFIED |
| Uninstalling but staying opted in | Opt-in is what counts; uninstalling probably does not reset it, but engagement questions follow | UNVERIFIED |

## Production application — draft answers

Google asks three groups of questions after the requirement is met (§ Policy check 1c in
CONSOLE-STEPS.md). Draft answers to adjust with the test's real numbers on the day:

**About the closed test**
- *How easy was it to recruit testers?* — "Easy: colleagues and friends who already pay for Claude,
  ChatGPT or Gemini and wanted to see their limits. N joined, M stayed for all 14 days."
- *How engaged were testers?* — "Most kept the always-on notification on for the whole test,
  which is the app's main use; several added widgets and used the Summary tab." (Use the Console's
  install and retention figures, and the count of emails received.)
- *Summary of feedback* — the real list: what was reported, grouped (bugs, wording, requests).

**About the app**
- *Intended audience* — "Adults who use paid Claude, ChatGPT or Gemini plans and want to see their
  usage limits and reset times without opening the provider's app."
- *How does the app provide value?* — "It shows each account's weekly and 5-hour usage, the pace
  against an even spread of the week, and when each window resets, in an always-on notification,
  widgets and a one-page summary of every account. Sign-in happens on the phone; tokens stay on the
  device; no servers, ads or analytics."
- *Expected installs in the first year* — an honest small number (for example "under 1,000"; Robin's
  call).

**About production readiness**
- *What changed because of the test?* — the fixes shipped during or after the test, by CCBG-N and
  name.
- *How did you decide it was ready?* — "Every report from the test is fixed or triaged; CI runs the
  unit tests and lint on every push; each release passes a device pass on a Galaxy Z Fold 7 (cover
  and inner screen, light and dark); the same build has been on GitHub since v2.0's release."

## Risks to name before Day 0

- **The provider question is unchanged.** CCRM-66 (Play Store Launch) was dropped on 2026-09-16
  because Anthropic's published terms prohibit third-party apps offering Claude.ai sign-in, and a
  listing puts the app in front of the people who enforce it. The Gemini path uses Google's
  Antigravity sign-in, and Play is Google's own store. Robin's 2026-11-10 decision accepts this; it
  is recorded here so the closed test is not where it is first noticed.
- **Review may question `specialUse`.** The fallback is CCRM-67 (Pin Service)'s `dataSync` plus a
  watchdog — a code change and a new release, which during the test means a new closed release.
- **App access.** Reviewers without a working path can reject for "insufficient access"; settle the
  review path before Day 0 (CONSOLE-STEPS.md step 12).
