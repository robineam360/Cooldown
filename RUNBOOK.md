# Runbook — the Play Store arc (v1.7 → Play)

Ordered, checkable steps to take Cooldown from a GitHub-releases sideload to a public Google
Play listing, and to decide afterwards whether an iPhone version follows. Roadmap item:
CCRM-66 (Play Store Launch). The v1.6 runbook (dual-identity arc, all steps ticked) is in git
history at commit `a7dba1b`; the v1.5 one (multi-provider arc) at `e18cf32`.

**What is different about this arc.** Most of it is not code. The steps that decide whether the
listing survives — asking Anthropic and OpenAI whether they object, and the Play console's
14-day closed test — are Robin's to run and are gated on other people's clocks. So the order
below starts the slow, external clocks first and does the code while they run. Nothing here
is a wireframe decision yet; the two visible changes (Step 4) go through working agreement 2
before they are built.

**How this arc is run.** One **Fable** session orchestrates: drafts, briefs sub-agents, reviews,
tests, commits and ticks this file. **Sonnet** for the manifest/flavor work and the privacy
page; **Haiku** for copy sweeps and tracker edits; the orchestrator itself for anything that
touches signing or the console, because a wrong move there is not undoable. If a session runs
out mid-arc, a fresh Fable session resumes from the *Resume* block of the first unticked step.

**Where the detail lives.** The reasoning behind each change is in ROADMAP.md item CCRM-66 (Play
Store Launch). This file says what order, what to paste, and what "done" means. The release
mechanics for the GitHub channel stay in [RELEASING.md](RELEASING.md); Step 8 extends that file
for the second channel rather than duplicating it here.

## Conventions — every session reads this block first

1. **Start from the paste.** Each step has a fenced *Resume in a fresh Fable session* block that
   names what to read. It never needs an earlier conversation.
2. **Tests:** `./gradlew testDebugUnitTest` green and `./gradlew assembleDebug` compiling before
   a step closes. From Step 5 on, `./gradlew bundlePlayRelease` must also succeed.
3. **Commits:** straight to `main`, subject `feat(CCRM-66): …` / `docs(CCRM-66): …`, body naming
   anything unrelated riding along. Never stage `ccooldown-release.jks`, `keystore.properties`,
   `local.properties`, or any PEPK output / upload-key export produced in Step 6.
4. **Close-out, in this order:** (a) every item in the step's *Done when* list; (b) the roadmap
   item's Status line; (c) tick the step in the Progress table (☐ → ☑) and write one dated line
   after its **Log:**; (d) commit and push, the tick in the same commit as the work.
5. **Handover.** The session's last message: what changed in one paragraph, what Robin has to
   do himself before the next step, then the next step's *Resume* block copied from this file.
6. **Wireframe gate.** Step 4 draws the About disclaimer and the Play-flavor Settings before
   Step 5 builds them. A session that wants to change anything else the user sees stops and asks
   Robin first, in one AskUserQuestion naming the file to open.
7. **The permission answer is binding.** If Anthropic or OpenAI say no in Step 1, the arc stops
   for that provider: the Play flavor ships without that provider, or not at all. Robin decides;
   a session never argues past a written no.
8. **Nothing about the store may imply affiliation.** No provider name in the title, no provider
   mark in the icon, feature graphic or screenshots' framing. Provider marks stay inside the app,
   beside the account they identify, with the disclaimer. Tracker IDs carry their epic name on
   first use.

## Progress

| Step | Item | Who | Gated on | Status |
|---|---|---|---|---|
| 1 | Ask Anthropic and OpenAI whether they object | Robin sends · session drafts | — | ☐ |
| 2 | Play console account, title check, tester recruitment | Robin | — | ☐ |
| 3 | v1.7 out the door first (CCRM-65 device pass, open fixes) | session | — | ☐ |
| 4 | Wireframes: About disclaimer · Play-flavor Settings · store graphics | session drafts · Robin approves | — | ☐ |
| 5 | Code: scoped queries, `github`/`play` flavors, AAB | Sonnet · orchestrator reviews | 4 | ☐ |
| 6 | Signing: existing key into Play App Signing, internal track, update-over-sideload check | orchestrator · Robin at the console | 5 | ☐ |
| 7 | Privacy policy page, Data safety form, listing | Sonnet drafts · Robin submits | 5 | ☐ |
| 8 | Closed test: 20 testers, 14 days; RELEASING.md for two channels | Robin · session | 6, 7 | ☐ |
| 9 | Production release | Robin · session | 1 answered or 30 days silent, 8 | ☐ |
| 10 | Decision: iPhone (CCRM-7 (iOS)) | Robin | 9 live ≥ 4 weeks | ☐ |

Steps 1 and 2 start on day one and run in the background of everything else. 3 is independent
and should ship before any Play code lands, so v1.7 is the last GitHub-only release. 4 → 5 → 6
→ 7 → 8 → 9 in order. 10 is a decision, not work.

---

## Step 1 · Ask Anthropic and OpenAI whether they object

**Who:** the session drafts two emails; Robin sends them from his own address and records the
dates. No sub-agents.

**Why first:** the app signs in with Anthropic's Claude Code OAuth client and OpenAI's Codex CLI
client, both copied from their open-source CLIs. A Play listing makes that visible. The
question is theirs to answer, and their answer (or their silence) decides Step 9. Nothing
technical in the arc depends on this, which is why it runs in parallel, but nothing goes to
production before it is resolved.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, then RUNBOOK.md — its Conventions block and Step 1 — then README.md's
"Unofficial" notice and ROADMAP.md item CCRM-66 (Play Store Launch). Draft two short emails
for Robin to send from his own address, one to Anthropic and one to OpenAI, and put them in
design/research/2026-09-xx-permission-emails.md with the recipient address each was sent to
left as a field Robin fills in (find the current developer-relations / trust-and-safety
contact route on anthropic.com and openai.com the day you draft; do not guess an address).
Each email, in Robin's voice, plain and under 200 words: who he is; what Cooldown is (an
open-source Android app, MIT, that shows the user's own Claude / ChatGPT 5-hour and weekly
usage windows on the phone; link to the repo); exactly how it authenticates (the user signs
in themselves on the phone through the provider's own OAuth flow, using the same public
client id the provider's CLI ships with; the token never leaves the device except to the
provider's own endpoints; no server of ours, no analytics); that it is clearly marked
unofficial and shows the provider's mark only beside the account it identifies; that he
intends to publish it on Google Play under the name Cooldown with his own icon; and one
question — do they object, or want anything changed? Offer to register a client of our own
if they have a route for that. Then tell Robin the two things he does: send, and write the
sent dates into the Log line below. Do not send anything yourself.
```

**Done when:**
- ☐ Both emails sent; dates and recipient routes recorded in the Log.
- ☐ A 30-day silence window is noted (send date + 30) — after it, Step 9 may proceed on
  silence, with the emails kept as the record of good faith.
- ☐ Any reply is quoted verbatim in `design/research/2026-09-xx-permission-emails.md` and its
  consequence written into Convention 7's terms: proceed, proceed without that provider, or stop.

**Log:**
-

---

## Step 2 · Play console account, title check, tester recruitment

**Who:** Robin, at the console. The session only checks the title and writes the tester note.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, then RUNBOOK.md — Conventions and Step 2. Two small things, then a checklist
for Robin. (1) Search Google Play (web) for apps titled "Cooldown" and near variants; report
whether the bare title is free and propose two fallbacks that carry no provider name (Play
titles are 30 characters). (2) Write a five-line note Robin can paste to colleagues asking
them to join the closed test — what it is, that they need a Google account on an Android 12+
phone, that they must stay opted in for two weeks, and that nothing they do is reported
back. Then list for Robin, without doing them: create the developer account (personal;
one-time fee; identity verification; his name and country appear on the listing); confirm
the developer-account requirement that a personal account runs a closed test with 20
testers for 14 continuous days before production; create the app entry with the chosen
title, "free", "app" (not game); note the console's current requirements for a privacy
policy URL and the Data safety form so Step 7 has the exact questions.
```

**Done when:**
- ☐ Developer account verified; app entry created; title chosen and recorded here.
- ☐ At least 20 colleagues have said yes to testing (names not recorded in the repo; a count is).
- ☐ The console's exact Data safety questions and any new 2026 requirements pasted into
  `design/research/2026-09-xx-play-console-requirements.md` for Step 7.

**Log:**
-

---

## Step 3 · v1.7 out the door first

**Who:** the session, with the Fold 7 over USB for the device pass. Follows RELEASING.md.

**Why here:** CCRM-65 (Accounts Redesign) and the CCBG-21 (Zero-Point Shading) / CCBG-22
(Credits Rows For All) fixes are already on `main`, unreleased. They should ship as a normal
GitHub release before the Play flavors exist, so the last GitHub-only build is a clean
baseline and the flavor work is the only thing in v1.8's diff.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, RELEASING.md, then RUNBOOK.md — Conventions and Step 3 — then ROADMAP.md
item CCRM-65 (Accounts Redesign) and BUGS.md entries CCBG-21, CCBG-22, CCBG-25. Phone over
USB adb. (1) Device pass for CCRM-65 on the Fold 7 cover screen and inner screen: the Free
plan notice, renewal stopped, the amber ≤3-day expiry line if reachable, the ChatGPT card,
the two-column inner layout; capture into design/research/2026-09-xx-v17-device-pass/.
Confirm CCBG-21 (a 0% chart no longer washes red) and CCBG-22 (credits rows only where
credits exist) on the same pass. (2) If anything is wrong, fix it and re-run; if a fix
changes an approved layout, stop and show the wireframe change first. (3) Release v1.7:
versionCode 23, versionName "1.7"; tests green; signed APK; fresh Accounts-tab screenshot in
release/docs/src/shots/ and the guide's Accounts page updated; USER-GUIDE changelog; release
notes naming CCRM-65 and the fixes, with CCBG-24 (Duet Label Clamp) as known; tag, push,
gh release; Check for updates on the phone reports v1.7. Close out per Convention 4.
```

**Done when:**
- ☐ CCRM-65 (Accounts Redesign) Status set to Shipped v1.7 with the device pass recorded.
- ☐ v1.7 published on GitHub, `releases/latest` resolves to it, the phone agrees.

**Log:**
-

---

## Step 4 · Wireframes — About disclaimer, Play-flavor Settings, store graphics

**Who:** the session drafts one review HTML in `design/`; Robin approves, one question at a time.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md (working agreement 2), then RUNBOOK.md — Conventions and Step 4 — then
ROADMAP.md item CCRM-66 (Play Store Launch), then SettingsScreen.kt's AboutCard and the
"Check for updates" row, and design/settings-diet-wireframe.html for the approved four-tab
Settings. Draft design/play-store-wireframe.html with three sections, each with states.
(A) The About card carrying the "Unofficial" disclaimer in-app: the README wording
condensed, where it sits relative to the version line, at 410 dp and the inner-screen
two-column width, dark and light; also the Play flavor's About without "Check for updates"
and without the GitHub release link, and the GitHub flavor's About unchanged. (B) The
Settings → More tab in the Play flavor: exactly which rows leave (Check for updates and
anything pointing at releases/latest) and what fills the gap, if anything. (C) The store
listing graphics: the 512 px icon (the Pulse tile as-is, confirm), a 1024×500 feature
graphic with no provider mark and no provider name, and the framing for phone screenshots
(which of the existing 1080×2371 captures, in what order, with what captions). Mocks keep
every function the real element has. Put a Decisions table at the top with every choice as
an open row; ask Robin the questions one at a time in chat, record each answer in the table,
and stop when he has approved every row. Nothing is built in this step.
```

**Done when:**
- ☐ `design/play-store-wireframe.html` exists; its Decisions table has no open rows; the Log
  names the approved rev.

**Log:**
-

---

## Step 5 · Code — scoped queries, `github` / `play` flavors, the AAB

**Who:** Sonnet builds, orchestrator reviews and tests; no phone needed until the last check.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, then RUNBOOK.md — Conventions and Step 5 — then ROADMAP.md item CCRM-66
(Play Store Launch) and design/play-store-wireframe.html's Decisions table (binding). Brief
Sonnet with the decisions baked in: (1) AndroidManifest.xml — remove QUERY_ALL_PACKAGES and
its tools:ignore; add a <queries> block with an <intent> for ACTION_VIEW on https so the
browser picker still lists browsers; check every call site that enumerated packages
(the sign-in "Open with" picker, CCRM-46 (Picker Icons)) still compiles and degrades to
the system chooser when the list is short. (2) app/build.gradle.kts — a flavorDimension
"channel" with flavors github and play sharing applicationId; a BuildConfig boolean
CHANNEL_PLAY; the Play flavor hides the Check for updates row, the releases/latest link on
About and any UpdateCheck scheduling (UpdateGate.kt, UpdateCheck.kt), per the wireframe;
the GitHub flavor is byte-for-byte the current behaviour. (3) The About disclaimer as drawn,
in both flavors. (4) ./gradlew bundlePlayRelease produces a signed AAB with the existing
signing config; assembleGithubRelease still produces the APK RELEASING.md expects; both
install over the current v1.7 on the Fold 7 (same signer) with sign-ins and history intact.
Unit tests: one for the flavor gate (UpdateGate never schedules under CHANNEL_PLAY), and
lint clean on the manifest with no suppressions. Update RELEASING.md's build command names.
```

**Done when:**
- ☐ No `QUERY_ALL_PACKAGES` anywhere; lint passes without the suppression.
- ☐ `bundlePlayRelease` and `assembleGithubRelease` both green; both installed over v1.7 on
  the Fold 7 without losing accounts or history; the picker verified on the Fold 7's One UI.
- ☐ About shows the disclaimer as approved; Play flavor has no update row or GitHub link.

**Log:**
-

---

## Step 6 · Signing — the existing key becomes the Play app-signing key

**Who:** the orchestrator, with Robin at the console. **No sub-agents touch this step.**

**Why it matters:** if Play generates its own signing key, a Play install has a different signer
from every GitHub APK, so nobody can move between channels without uninstalling, and
uninstalling loses sign-ins and history (see CCBG-1 (History Retention)). Uploading the
existing `ccooldown-release.jks` key as the app-signing key keeps one signer across both
channels. This is a one-way choice at the console; it cannot be changed after the first upload.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, RELEASING.md, then RUNBOOK.md — Conventions and Step 6. Confirm first that
ccooldown-release.jks and keystore.properties are at the repo root and that the memory note
about the OneDrive keystore backup still holds (ask Robin to confirm the backup exists
before anything else — this step must not be the day we find out it doesn't). Then, in the
console's App integrity → App signing, Robin chooses "Use an existing key" — NOT "Let Google
generate". Walk him through Google's PEPK tool with the exact command, run in the scratchpad
directory, exporting the release key encrypted with Google's public key; the output file is
uploaded once and then deleted locally — never committed, never left in the repo. Verify
after upload that the console's app-signing certificate SHA-256 equals
8bc21a2aca81e5a09b239d1847822549f10775d76849f0e1948980ecd044f64f (the installed app's
signer, from RELEASING.md). Upload the Step 5 AAB to the Internal testing track with Robin
as the only tester; install it from Play onto the Fold 7 OVER the v1.7 sideload and confirm
accounts and history survive; then install the GitHub v1.7 APK over the Play install and
confirm the same in reverse. Record both directions in the Log. Also decide and record
whether the same keystore continues as the upload key (simplest) or a separate upload key is
generated; default is the same key.
```

**Done when:**
- ☐ Console shows the existing certificate as the app-signing certificate; SHA-256 matches.
- ☐ Internal-track install verified in both directions over the sideload without data loss.
- ☐ No key material in the working tree (`git status` clean of `.jks`, `.pem`, `.zip` exports).

**Log:**
-

---

## Step 7 · Privacy policy, Data safety, the listing

**Who:** Sonnet drafts; Robin pastes into the console and submits. Haiku for the copy sweep.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, README.md (the Unofficial notice and the Privacy paragraph), RUNBOOK.md —
Conventions and Step 7 — design/research/2026-09-xx-play-console-requirements.md from Step
2, and design/play-store-wireframe.html's section C. (1) Privacy policy: write
docs/privacy.md (or the path GitHub Pages serves for this repo — check settings; enable
Pages on main if needed) in plain language: what the app stores (OAuth tokens, encrypted
with the Android Keystore; usage samples; account labels), where it sends anything (only to
the signed-in provider's own endpoints — name them; and, in the GitHub flavor only, one
unauthenticated call to api.github.com for the version check), what it never does (no
server of ours, no analytics, no ads, no SDKs beyond AndroidX and OkHttp), how to delete
everything (Clear on the account card; uninstall), contact email, date. (2) Data safety
answers, drafted as a table matching the console's questions from Step 2; the defensible
position is "no data collected or shared" with the provider traffic explained in the policy
— flag any question where that is arguable so Robin decides. (3) Listing copy: title per
Step 2; short description (80 chars) and full description (4000) with no provider name in
the title, nominative use only in the body, the Unofficial disclaimer paragraph verbatim
near the top; content-rating questionnaire answers (utility, no user content, no ads);
target audience 18+. (4) Assets from the wireframe: 512 icon, 1024×500 feature graphic
(render from an SVG in design/, check with a PNG), the screenshot set with captions. Put
everything under release/play/ and tell Robin what to paste where.
```

**Done when:**
- ☐ Privacy policy live at a public URL and that URL entered in the console.
- ☐ Data safety form submitted; content rating received; listing saved with all assets.
- ☐ `release/play/` holds the copy and assets as the record of what was submitted.

**Log:**
-

---

## Step 8 · Closed test — 20 testers, 14 days — and RELEASING.md for two channels

**Who:** Robin runs the track; the session extends RELEASING.md and watches the tracker.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, RELEASING.md, RUNBOOK.md — Conventions and Step 8. (1) Promote the Step 6
AAB to a Closed testing track, tester list from Step 2; Robin sends the join link with the
Step 2 note. Record the start date; the 14 days run from when 20 are opted in, not from
sending. (2) Extend RELEASING.md with a second channel section: bundlePlayRelease alongside
assembleGithubRelease from the same commit and version; the AAB goes to the Play track, the
APK to the GitHub release, same tag; the Play rollout waits for GitHub so releases/latest is
never ahead of Play by more than a day; versionCode increments once per release, never per
channel. (3) During the two weeks, any tester report is filed in BUGS.md as a CCBG with
"Play closed test" in its symptom line. Do not ship a new build mid-test unless a High is
filed; a new build does not reset the 14-day clock but the tester count must stay ≥ 20.
```

**Done when:**
- ☐ Console confirms the closed-test requirement is met and production access is unlocked.
- ☐ RELEASING.md describes both channels; a dry run of both build commands is green.
- ☐ Every tester-filed CCBG is triaged (fixed, scheduled or Won't fix with a reason).

**Log:**
-

---

## Step 9 · Production

**Who:** Robin presses the button; the session prepares and verifies.

**Gate, per Convention 7:** Step 1 answered without objection for each provider shipped, or 30
days of silence with the sent emails on record. A written objection removes that provider from
the Play flavor before this step, or stops the arc — Robin's call, recorded in the Log.

**Resume in a fresh Fable session:**

```
Read CLAUDE.md, RELEASING.md, RUNBOOK.md — Conventions, Step 1's Log (the gate) and Step 9.
Confirm the gate in one sentence before doing anything. Then run a normal two-channel release
per the extended RELEASING.md: the version that goes to production is the same commit and
versionCode as the current GitHub release (cut a fresh one if anything changed since); staged
rollout 20% → 100% over a week; README gains a "Get it on Google Play" link beside the
GitHub-release link with the disclaimer unchanged; USER-GUIDE.md and the brochure mention
both install routes. After the listing is live, install from Play onto a phone that has never
had the sideload, sign in, and confirm the always-on notification works end to end. Close out:
CCRM-66 (Play Store Launch) Status → Live on Play <date>; tick; commit; push.
```

**Done when:**
- ☐ Listing public; a clean-phone install works end to end.
- ☐ README and docs carry both routes; CCRM-66 marked live.

**Log:**
-

---

## Step 10 · Decision — iPhone

**Who:** Robin. Not a build step; a recorded decision after Play has been live at least four weeks.

**What to weigh, from the 2026-09-11 assessment:** iOS has no always-on notification, so the
app's main surface becomes widgets and Live Activities — a redesign, not a port; background
refresh runs when iOS decides, so figures will be staler; Apple review enforces guideline 5.2.2
(third-party services without permission) more actively than Play does, so the Step 1 answers
matter more there; the Developer Program is a yearly fee; and per CLAUDE.md it is a separate
repo that copies the shared contract in. What ports cleanly: the OAuth flows
(ASWebAuthenticationSession), Keychain storage, the parsers, pace logic and their tests.

**Done when:**
- ☐ Robin writes one paragraph into CCRM-7 (iOS)'s Status line: go, with a start date and the
  repo name; or hold, with what would change the answer.

**Log:**
-
