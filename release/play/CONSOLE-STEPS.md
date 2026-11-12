# Play Console — Robin's ordered checklist for the v2.0 launch

Written 2026-11-10 for CCRM-66 (Play Store Launch). **Every click below is Robin's.** No session
signs in, pays, verifies, uploads, enrols a key or changes a Console setting; a session prepares the
copy in `release/play/`, builds and checks the AAB, and guides one screen at a time.

**Decided by Robin, 2026-11-10:** Play launches at **v2.0** on a **personal** developer account
(Q4 in RUNBOOK.md Step 9 and ROADMAP.md's v1.9 section, now closed). The account does not exist
yet. The mandatory closed test starts **after the eight v2.0 feedback items** (Robin's Fold 7
notes of 2026-11-10) are built and land, never before. iOS stays on TestFlight, published by
EAM360. The timeline is in [CLOSED-TEST-PLAN.md](CLOSED-TEST-PLAN.md).

Policy facts below carry a source and the date they were read; the full list, with what could not
be confirmed, is in [§ Policy check](#policy-check-2026-11-10) at the end. **Re-read every fee,
limit and question in the Console on the day**: Play changes them.

**Marks used:** ⛔ irreversible, named before Robin does it · 🔒 Robin only, never shared with a
session (passwords, ID documents, the keystore and its passwords, payment details) · 📄 the copy
to paste is in this folder.

---

## Phase A — the account (can start now; it does not wait for v2.0)

Identity checks can take days, so this phase is worth doing while v2.0's eight items are built.

1. **Pick the Google account** that will own the developer account. ⛔ It cannot be swapped later
   without transferring every app. A dedicated Gmail (for example one used only for publishing)
   keeps Play mail out of a work inbox and survives a job change; Robin's call. Turn on 2-step
   verification on it first (Play requires it to sign in to the Console).
2. **Register** at <https://play.google.com/console/signup> → **"Yourself"** (personal), not "An
   organisation". ⛔ The account type cannot be changed afterwards (an organisation later means a
   new account and an app transfer). Pay the one-time registration fee 🔒 (US $25 as of the 2026-09 research; confirm on the sign-up page) (card in Robin's name;
   the payments profile is created here).
3. **Developer name** — the name shown under the app on Play. It can differ from Robin's legal
   name. Suggested: `Robin Richard Rajan` or a portfolio name such as `Robin R. Apps`; it is the
   name every future app in the portfolio shares. Changeable later (§ Policy check, 6a).
4. **Identity verification** 🔒 — legal name and address, matching a government ID that Robin
   uploads to Google (never to a session). The legal name must match the payments profile.
5. **Contact details for Google** (private): a contact email and a phone number, each verified by
   a code. These are Google's way to reach Robin and are **not** shown on Play.
6. **Public developer details** (§ Policy check, 6a): on a personal account Play shows Robin's
   **legal name**, his **country** and the **developer email** — even on a free app, whatever
   developer name is chosen. A free app shows **no street address**; once the account monetises
   (the possible "Pro" purchase) the **full address** is shown, so a business or virtual address
   is needed before that day. For the **EEA and UK**, declare trader status: as a free hobby app
   with no sales, **non-trader** shows only email and country (Robin's call; a trader shows name,
   address, phone and email). Use a public-facing address for the developer email
   (`robin@eam360.com` or a dedicated one).
7. **Android device verification** — new personal accounts must prove access to a non-rooted
   Android 10+ phone: the Console home page shows a QR code; install the **Play Console app** on
   the phone, sign in as the account owner, pick the developer account, tap Verify (under a
   minute; no phone number is collected). The Fold 7 works.
8. **Wait for "Your identity has been verified"** (email and the Console's home page). Until then
   apps can be created but not published to any testing track (UNVERIFIED for the closed track;
   assume it).

## Phase B — create the app (can start now)

9. **Create app**: app name `Cooldown — AI usage limits` 📄 ([listing.md](listing.md) §1; Play
   allows 30 characters), default language **English (United Kingdom)** or **English (United
   States)** — pick the one the copy is written in (en-GB spelling: *colour*, *organisation*),
   **App**, **Free**. ⛔ **Free can never become paid upfront**; in-app purchases stay possible.
   Tick the Developer Programme Policies and US export laws declarations.
   - **Title availability:** Play is not known to reserve app names (UNVERIFIED; its policy forbids names that impersonate or mislead, not duplicates); "Cooldown" is
     not a unique handle on Play the way a package name is. Search Play for "Cooldown" first: if a
     well-known app uses the plain name, the subtitle (`— AI usage limits`) keeps it distinct. The
     unique, permanent handle is the package name, set at the first upload (step 22).
10. **Store settings → App category:** Tools · **Contact details:** the public email, website
    `https://github.com/robineam360/Cooldown`. Tags from Play's fixed list on the day.

## Phase C — app content (the declarations; fill now, re-check on v2.0)

Each item below lives under **Policy and programmes → App content** (the left menu; names may
shift). Drafts are 📄 in this folder; re-read each Play definition against the draft on the day and
fix the draft in the repo first if they disagree.

11. **Privacy policy URL** 📄 — `https://github.com/robineam360/Cooldown/blob/main/docs/privacy.md`.
    **Blocker before the first upload:** the Android half of `docs/privacy.md` still lists only
    Anthropic, OpenAI and GitHub; v2.0's Android app also talks to Google for Gemini
    (`accounts.google.com`, `oauth2.googleapis.com`, `daily-cloudcode-pa.googleapis.com`; `www.googleapis.com`
    appears only in the sign-in's permission names, nothing is sent there). The v2.0 release adds that row (RELEASING.md §5: a change
    to what leaves the phone updates the policy and the Data safety form in the same release).
12. **App access** 📄 [app-access.md](app-access.md) — "All or some functionality is restricted".
    **Decided by Robin, 2026-11-10: port the sample mode.** The Android app gets the iPhone app's
    sample-data mode (`ios/App/UI/SampleMode.swift`, CCRM-94 (iOS App) parity wireframe rev B §7),
    built **with the v2.0 feedback items** as their item 9, so it is in the AAB that goes to the
    closed track. It is app work: the wireframe session draws the Android delta first (CLAUDE.md
    §2). The App access answer is then the same on both stores: no credentials; the reviewer taps
    "Try with sample data" on the first screen and every tab of the app shows sample accounts. The
    always-on notification and the widgets stay on real accounts (Robin, 2026-11-10, CCRM-113 (Sample Port)):
    the note says they need a real account and points to the foreground-service video. Refresh `app-access.md`'s instructions on the v2.0 build (they predate Gemini,
    the Summary tab and the sample mode). The test account / video options are no longer the plan;
    if Play's reviewer still asks for a live sign-in, that is a new decision for Robin, and
    credentials would go only into the Console field, never into the repo.
13. **Ads:** "No, my app does not contain ads."
14. **Content rating (IARC questionnaire):** category **Utility, Productivity, Communication or
    Other**; answer **No** to violence, sexuality, language, controlled substances, gambling;
    **No** to user-to-user interaction and to sharing location; the app shares a usage image only
    through the system share sheet at the user's tap (not user-generated content in IARC's sense).
    Expected result: *Everyone / PEGI 3*. Uses the contact email.
15. **Target audience and content:** age groups **18 and over** only (the providers' own terms
    need an adult account), not designed for children, **No** to "appeals to children". This keeps
    the app out of the Families programme.
16. **Data safety** 📄 [data-safety.md](data-safety.md) — answers for the play build. **Must be
    re-checked on v2.0 before submitting:** Gemini adds Google as a recipient of the OAuth tokens
    (User IDs → shared with Anthropic, OpenAI **and Google**). The account-creation question:
    "My app does not allow users to create an account" (users sign in to accounts that already
    exist at the providers).
17. **Government apps / Financial features / Health / News:** No · No · No · No.
18. **Foreground service permissions** 📄 [fgs-declaration.md](fgs-declaration.md) — appears after
    the first AAB upload that declares `FOREGROUND_SERVICE_SPECIAL_USE`. Paste the three texts and
    the video link. **The video must be re-recorded on the v2.0 build** (it shows v1.9's
    notification) and uploaded by Robin as an unlisted YouTube video (or wherever the form accepts
    a link on the day).
19. **Advertising ID:** "No" (the app never reads it; manifest has no `AD_ID` permission).

## Phase D — signing and the first upload (after v2.0's eight items and the sample mode land)

20. **Play App Signing → use an existing key** (Robin's Q3, 2026-09-27). ⛔ Google keeps a copy of
    the key for good; this is what lets the GitHub APK and the Play build update each other.
    🔒 Robin copies `ccooldown-release.jks` from his OneDrive backup himself, runs Google's
    **PEPK** tool as the Console shows (the command and Google's encryption key are on that page),
    uploads the encrypted output, then deletes the PEPK output. A session never sees the keystore,
    its passwords or the PEPK file, and none of them is ever staged in git.
21. **Upload key:** the same key stays the upload key (one signer for both channels, RELEASING.md
    §5). Do not let Play generate a new upload key.
22. **The AAB** — a session builds `bundlePlayRelease` from the v2.0 release commit, runs
    RELEASING.md §5's checks (bundletool validate, signer = the trusted digest, versionCode = the
    APK's, `check_artefact.py --channel play`) and hands Robin the file and its sha256. ⛔ The
    package name `com.robin.claudeusage` is claimed on Play at this upload, permanently.
    The first upload may go to **Internal testing** first (Robin plus a couple of addresses, no
    review wait) to see the Play-signed APK; verify **App bundle explorer → the signed universal
    APK's SHA-256 = RELEASING.md's trusted digest** before any tester installs it.

## Phase E — the closed test (the 14-day clock)

23. **Testing → Closed testing → Create track** (or use the default "Closed testing - Alpha").
24. **Testers:** a **Google Group** is the easiest list to maintain (add or remove people without
    touching the Console; testers join the group with the Google account they use on their
    phone). An email list in the Console works too. Add at least **15** people for the
    12-tester rule (spares for drop-outs; CLOSED-TEST-PLAN.md § Recruiting).
25. **Feedback channel** (shown to testers on the opt-in page): an email address or a URL. Use
    `robin@eam360.com` or the GitHub issues page.
26. **Create release** on the closed track → the v2.0 AAB → release notes 📄 (app only, no build
    story) → **Review release → Start rollout to Closed testing**. Closed releases are reviewed by
    Google (hours to a few days); the opt-in link only works once the release is approved.
27. **Countries:** pick the countries the testers are in (India at least, plus any other tester's
    country); a tester outside the selected countries cannot install.
28. **Copy the opt-in link** (Testers tab, "Join on the web") and send the invite
    ([TESTER-INVITE.md](TESTER-INVITE.md)) yourself. Each tester must **accept the opt-in on the
    web and install from Play** with the same Google account.
29. **Watch the counter:** the Console's Dashboard shows the progress toward production access
    (testers opted in, days run). The 14 days count only while **12 or more testers are opted in
    at the same time**; check it every couple of days (CLOSED-TEST-PLAN.md § The clock).

## Phase F — apply for production (day 15 or later)

30. **Dashboard → Apply for production** (enabled once the requirement shows met). Google asks a
    questionnaire, in three groups (as described on Play's help page; wording varies; answer from
    the closed test's real record — draft answers are in CLOSED-TEST-PLAN.md § Production
    application):
    - **About the closed test:** how easy it was to recruit testers; how engaged they were;
      what feedback came in and how it was collected; a summary of that feedback.
    - **About the app:** the intended audience; how the app provides value to users; expected
      installs in the first year.
    - **About production readiness:** what changed in the app because of the test; how you
      decided it was ready for production.
31. **Wait for the answer** (Google says usually 7 days or less). If refused, the email names what
    to change; the closed test continues meanwhile and a new application is allowed.
32. **Production release:** promote the closed-test release (same AAB, same versionCode) or upload
    the current release's AAB → countries → **staged rollout** (for example 20%, then 100%) →
    production review. Only then is the listing public.

## Phase G — after launch, every release

- One versionCode sequence for both channels: every GitHub release that ships after launch also
  goes to Play under the same versionCode (RELEASING.md §5); Play refuses a reused versionCode.
- The target API deadline for **updates** applies too (§ Policy check, item 2).
- A change to what leaves the phone updates `docs/privacy.md`, `data-safety.md` and the Console's
  Data safety form in the same release.

---

## Policy check (2026-11-10)

Read on the web on 2026-11-10 (Google's help pages show no "last updated" date; the date below is
the day they were read). **UNVERIFIED** marks what no primary Google page confirmed.

| # | Topic | Finding | Source |
|---|---|---|---|
| 1 | Closed test, new personal accounts | Applies to personal accounts created after 13 Nov 2023. **At least 12 testers opted in continuously for the 14 days** before applying for production (was 20 until Dec 2024; ROADMAP's CCRM-66 still says 20). A tester who opts out before 14 days does not count; an opt-out and back in must still add up to consecutive days. | <https://support.google.com/googleplay/android-developer/answer/14151465> |
| 1a | Does a new build reset the clock? | **UNVERIFIED.** Google's page does not say. Most secondary sources say no (Google counts opted-in days, not versions); at least one says a new release needs testers to update and stay opted in. Plan for it to be safe, but keep updates few. | Secondary only, e.g. <https://www.testerscommunity.com/blog/does-updating-app-reset-14-day-closed-testing> |
| 1b | Does dropping below 12 reset it? | Google's wording ("opted in continuously") implies yes; secondary sources say a dip below 12 restarts a fresh 14 continuous days. Treated as a reset here; strictly **UNVERIFIED** as to the exact counting. | Same page as #1 |
| 1c | Production application | Three groups of questions: the closed test (recruiting ease, whether testers used all features, whether usage matched production, a feedback summary); the app (audience, value, estimated first-year installs); readiness (changes made from testing, how readiness was judged). | Same page as #1 |
| 2 | Target API level | **API 35** required since 31 Aug 2025; **API 36 required from 31 Aug 2026 for new apps and updates** (extension to 1 Nov 2026 on request). Cooldown targets **36** (`app/build.gradle.kts`), so it meets today's rule. The next bump (API 37) is expected for Aug 2027 by the usual yearly pattern (**UNVERIFIED**). | <https://support.google.com/googleplay/android-developer/answer/11926878> |
| 2a | 16 KB page size | Apps targeting API 35+ must support 16 KB pages on 64-bit devices; Google's guide says updates without it **cannot be released from 1 Feb 2027** (earlier blog posts said 1 Nov 2025 — the guide's later date is taken as current, **UNVERIFIED** why it moved). Cooldown ships one native library (`libandroidx.graphics.path.so`); `tools/check_artefact.py` already checks 16 KB alignment for both channels. | <https://developer.android.com/guide/practices/page-sizes>; <https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html> |
| 3 | Android developer verification (apps outside Play) | Opened to all developers March 2026; developer APIs, limited-distribution accounts and the "advanced flow" (power users installing unverified apps) launched Aug 2026; **enforced from 30 Sep 2026 in Brazil, Indonesia, Singapore and Thailand** on certified Android 7+ devices; **global from 2027**. Play auto-registers apps it distributes (over 99% done); apps outside Play register through the Android Developer Console or Play Console with the package name and the SHA-256 of the signing key; Play App Signing can mean a second key to register. A free limited-distribution account (up to 20 devices, no ID) exists for hobbyists. | <https://developer.android.com/developer-verification>; <https://developer.android.com/developer-verification/guides> |
| 3a | What it means for the `github` flavor | The GitHub APK and the Play build share the package name and — because Play App Signing uses **our existing key** (Q3) — the same signing certificate. So registering `com.robin.claudeusage` through Play should also cover the GitHub APK on certified devices. **UNVERIFIED** that Play's automatic registration includes sideloaded copies signed with that key; check **Play Console → the app → developer verification / package registration** after the first upload and register the key there explicitly if offered. Until then, a user in the four enforcement countries cannot install the GitHub APK on a certified phone. No change to the flavor's package or signing is needed. | Inference from #3 |
| 4 | Foreground service declaration | Apps targeting Android 14+ declare each FGS type under App content: the feature, the user impact if the task is deferred or interrupted, and **a video link**. `specialUse` is allowed only in limited scenarios and is reviewed. No separate video rule for `specialUse` beyond that (**UNVERIFIED**). | <https://support.google.com/googleplay/android-developer/answer/13392821> |
| 5 | Data safety | No 2025–2026 change found on the help page (its newest dated entry is 5 Dec 2023), **UNVERIFIED** that nothing changed. The deletion badge applies to apps with a deletion-request route or auto-delete within 90 days. | <https://support.google.com/googleplay/android-developer/answer/10787469> |
| 6 | Personal account: fee, ID, device | The fee (US $25 one-time) is **UNVERIFIED** on a primary page today (read from the sign-up flow on the day). Legal name, address and verified contact email and phone are required. New personal accounts must verify access to a **non-rooted physical Android phone on Android 10+** with the Play Console app before publishing; one device can verify several accounts; no phone number is collected. | <https://support.google.com/googleplay/android-developer/answer/13628312>; <https://support.google.com/googleplay/android-developer/answer/14316361> |
| 6a | What a personal account shows publicly | **Legal name, country, and the developer email**; the **full address once the account monetises** on Play. The developer name is separate and changeable. In the **EEA and UK** it follows the declared trader status: a trader shows name, address, phone and email; a non-trader only email and country. | <https://support.google.com/googleplay/android-developer/answer/13634081> |
| 7 | Phone screenshots | JPEG or 24-bit PNG, 320–3840 px per side, and the long side **no more than twice the short side**. Robin's full-frame Fold 7 cover shots are **1080×2371 (2.20:1) and will be refused as-is**: crop to 1080×2160 (for example drop the 149-px status-bar band and 62 px of the gesture bar) or pad. | <https://support.google.com/googleplay/android-developer/answer/9866151> |
