# Play Console — Robin's ordered checklist for the v2.0 launch

Written 2026-11-10 for CCRM-66 (Play Store Launch). **Every click below is Robin's.** No session
signs in, pays, verifies, uploads, enrols a key or changes a Console setting; a session prepares the
copy in `release/play/`, builds and checks the AAB, and guides one screen at a time.

**Changed by Robin, 2026-11-17:** Play launches at **v2.0** on **EAM360's organisation developer
account**, the same publisher as the App Store record. An organisation account has no closed-test
rule (§ Policy check, 1), so Phase E is an optional internal test and Phase F is the production
release. Phases A, E and F below are rewritten for that; the personal-account text is in git
history (this file before 2026-11-17). [CLOSED-TEST-PLAN.md](CLOSED-TEST-PLAN.md) and
[TESTER-INVITE.md](TESTER-INVITE.md) are retired.

~~**Decided by Robin, 2026-11-10:** Play launches at **v2.0** on a **personal** developer account
(Q4 in RUNBOOK.md Step 9 and ROADMAP.md's v1.9 section, now closed). The account does not exist
yet. The mandatory closed test starts **after the eight v2.0 feedback items** (Robin's Fold 7
notes of 2026-11-10) are built and land, never before. iOS stays on TestFlight, published by
EAM360. The timeline is in CLOSED-TEST-PLAN.md.~~

Policy facts below carry a source and the date they were read; the full list, with what could not
be confirmed, is in [§ Policy check](#policy-check-2026-11-10) at the end. **Re-read every fee,
limit and question in the Console on the day**: Play changes them.

**Marks used:** ⛔ irreversible, named before Robin does it · 🔒 Robin only, never shared with a
session (passwords, ID documents, the keystore and its passwords, payment details) · 📄 the copy
to paste is in this folder.

---

## Phase A — access to EAM360's organisation account (2026-11-17)

The account, its identity check (D-U-N-S), its payments profile and the fee are EAM360's and already
done; nothing here is paid or verified by Robin.

1. **The Google account** 🔒 — Robin's dedicated Google account, not his personal Gmail (kept for a
   personal developer account of his own later). Its address is not written in this public repo.
   Turn on 2-step verification on it (the Console requires it).
2. **The invite** — EAM360's account owner or an admin adds that account under **Users and
   permissions → Invite new users**. Permissions to ask for (Console names shift; match them on the
   day): **Admin** for the account, or at least the account-level right to **create apps** plus, for
   this app (or all apps): view app information · edit and delete draft apps · manage store presence
   · manage policy declarations (App content) · release to testing tracks and manage tester lists ·
   **release to production, exclude devices and use Play App Signing**. Without the last one Robin
   cannot enrol the key or release, and the lead would have to do those screens.
3. **Accept** the email invite, sign in at <https://play.google.com/console> with that account and
   pick the EAM360 developer account.
4. **No device verification** — the Android-phone check is for new personal accounts (§ Policy
   check, 6); if the Console asks anyway, the Fold 7 works.
5. **Public developer details** are EAM360's (name, address, email), set on the account by its
   owner. The app's own contact email (Phase B, step 10) is `robin@eam360.com`.

## Phase B — create the app (can start now)

9. **Create app**: app name **`EAM360 Cooldown`** 📄 (Q7, Robin 2026-11-17: matches the
   App Store record; [listing.md](listing.md) §1; Play allows 30 characters), default language **English (United Kingdom)** or **English (United
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

## Phase E — internal test (optional, no clock)

An organisation account may go straight to production; this phase only gets the Play build onto
colleagues' phones first, the way the TestFlight link does on iOS.

23. **Testing → Internal testing → Testers:** an email list or a Google Group (up to 100 people).
    Internal releases are not reviewed and are available within minutes.
24. **The release** is the one from step 22 (the same AAB). Release notes 📄 (app only, no build story).
25. **Copy the opt-in link** (Testers tab) and send it yourself. Each person accepts on the web and
    installs from Play with the Google account on that list. Someone who has the GitHub APK
    installs over it (same package, same signer once step 20 is done).

## Phase F — production

26. **Countries:** pick the countries (India at least; all countries is fine for a free app).
27. **Production → Create release** → the same AAB (same versionCode) or the current release's AAB
    → release notes 📄 → **staged rollout** (for example 20%, then 100%; a bad rollout can be
    halted). ⛔ Submitting puts the app in Google's review; once approved, the listing is public.
    Optional: **Publishing overview → Managed publishing** holds an approved release until Robin
    clicks Publish.
28. **Wait for review** (hours to a few days for a new app; longer for a first `specialUse`
    declaration). A rejection email names what to change; fix it in the repo first.

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
| 1 | Closed test, new personal accounts | **Not our case since 2026-11-17** (EAM360 is an organisation account; organisation accounts are exempt per secondary sources, confirm on the Console's Dashboard that Production is open). Applies to personal accounts created after 13 Nov 2023. **At least 12 testers opted in continuously for the 14 days** before applying for production (was 20 until Dec 2024; ROADMAP's CCRM-66 still says 20). A tester who opts out before 14 days does not count; an opt-out and back in must still add up to consecutive days. | <https://support.google.com/googleplay/android-developer/answer/14151465> |
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
