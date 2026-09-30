# Runbook — the v1.9 arc: Play-ready and steady

**Plan status: FROZEN 2026-09-27.** Cross-family review: Astra at xhigh. Rounds 1 and 2 were blocking, and round 3 returned **concerns**. All thirteen findings are adopted. Q4 (the Play account type) stays open by Robin's choice and gates only Step 9. See Step 1's Log.

This runbook takes `main` as of 2026-09-27 (v1.8 shipped, versionCode 24) to a released v1.9, and
then to the same build on Google Play's internal-testing track. It is written as ordered, checkable
steps.

**Items:**
- CCRM-86 (Play Readiness), with its four parts: CCRM-87 (Update Channel), CCRM-88 (Scoped Queries),
  CCRM-89 (Privacy Policy) and CCRM-90 (Listing Pack);
- CCRM-91 (CI Gate);
- CCRM-85 (Crash Capture);
- CCBG-46 (Keystore Wedge);
- visible fixes: CCBG-35 (Tab Clip), CCBG-45 (Single Panel Wording), and CCBG-48 (Reconfigure Label)
  if it qualifies;
- CCBG-50 (Degraded Store Notice), added by Robin on 2026-09-30 after the Step 4 judge. It needs a
  wireframe and Robin's review call on the scope change.

**Filed but not in v1.9:** CCBG-47 (Widget Theme Lag) and CCBG-49 (Unassigned Label Overflow).

**Out of scope:** CCRM-66 (Play Store Launch) stays the launch decision. It is gated on the
Anthropic/OpenAI check and it is Robin's.

Earlier arcs are in git history: v1.8 at `6e4c1a0`, v1.7 at `4f30dba`, the dropped Play Store arc at
`b827dc5` (read its research, never its conclusions, as current), and v1.6 at `a7dba1b`.

**Robin's answers (2026-09-27), recorded in ROADMAP.md's v1.9 section:**
- **Q1:** v1.9 goes as far as a Console account and the internal-testing track (Step 9). It does not
  start closed testing and does not publish to production.
- **Q2:** CCBG-45 is fixed as-is, under CLAUDE.md §2's exemption.
- **Q3:** Play App Signing uses our existing key.
- **Q4:** the account type (personal or organisation) is **open**, and Robin will talk it through
  with Fable. **Step 9 cannot start until Q4 is answered.**

**Five things the plan is built on:**
1. **Nothing outside git or the phone changes before Step 8.** Steps 1–6 are commits, and
   `git revert` reverses them. Step 7 changes the phone, and its *Always after* block restores it.
   Step 8 is irreversible once published. So is Step 9 (the fee, the identity check, the key
   enrolment, and the package name claimed on Play).
2. **The Play build lacks out-of-store update code rather than hiding it.** The code lives in a
   flavor source set, not behind a flag (CCRM-87).
3. **One key, one versionCode sequence, one commit** for both channels. The AAB is built from the
   release commit, and its signer is checked against RELEASING.md's trusted digest.
4. **No session opens the Play Console on its own.** Step 9 is Robin's clicks, with the session
   guiding. Every irreversible console action is named before it is taken.
5. **The phone ends on the github flavor.** A play-flavor build left on the Fold 7 would strand
   Robin without update checks.

**How this arc is run.** Delegation follows the `model-mesh` skill (CLAUDE.md §4), and the seat
builds by default. Minor spec or wireframe gaps go to a Fable judge, and Fable's answer goes to
Astra. Only disagreements, major visual changes and reserved decisions come to Robin. Whoever writes
code lands it with its Status line in one commit, and the session ticks this file. A session that
runs out mid-arc is resumed by pasting the *Resume* block of the first unticked step. If Step 7 had
begun, run its *Always after* block first.

## Conventions — every session reads this block first

1. **Start from the paste.** Each step's fenced block needs no earlier conversation.
2. **Tests:** `./gradlew testGithubDebugUnitTest testPlayDebugUnitTest` green and
   `./gradlew assembleGithubDebug assemblePlayDebug` compiling before a step closes. These are the
   flavor task names Step 3 confirmed on AGP 9.4 (2026-09-30); the old `testDebugUnitTest` is now
   ambiguous and fails. Release outputs: `assembleGithubRelease` →
   `app/build/outputs/apk/github/release/app-github-release.apk` (published as `app-release.apk`),
   `bundlePlayRelease` → `app/build/outputs/bundle/playRelease/app-play-release.aab`.
3. **Commits:** straight to `main`. Subjects look like `feat(CCRM-87): …` or `fix(CCBG-46): …`, and
   the body names anything unrelated that rides along. Never stage `ccooldown-release.jks`,
   `keystore.properties`, `local.properties` or any PEPK output.
4. **Close-out, in order:**
   1. every *Done when* item;
   2. the roadmap and bug Status lines;
   3. the Progress tick (☐ → ☑) and one dated line after **Log:**;
   4. commit and push, with the tick in the same commit as the work.
5. **Handover.** The session's last message covers three things: what changed (one paragraph), what
   Robin must do himself before the next step, and the next step's *Resume* block. The same block
   replaces the "Next session" section of [HANDOVER.md](HANDOVER.md).
6. **Wireframe gate.** Step 2 draws every visible change in every state and both width classes.
   Review happens in the `design/` HTML, never in chat. Questions go to Robin one at a time, and
   silence is not approval. A later step that wants to change anything else the user sees stops and
   goes back to the wireframe.
7. **Mocks keep full functionality.** Notification sections use the Huge-number style as their base.
8. **The Play Console is never opened by a session.** Only Robin opens it (Step 9).
9. Tracker IDs carry their epic name on first use (CLAUDE.md §1). Two sub-agents never edit the same
   file at once.

## Progress

| Step | Item | Who | Gated on | Status |
|---|---|---|---|---|
| 1 | Plan: Fable draft, Robin's Q1–Q4, items filed, Astra xhigh, freeze | session | — | ☑ |
| 2 | Wireframe: privacy row, crash card, play-flavor Updates absence, CCBG-35, CCBG-48 if exempt | Sonnet draws · session polishes · Robin approves | 1 | ☑ |
| 3 | Channel split, scoped queries, CI (CCRM-87, 88, 91) | session · fresh Opus judge | 2 | ☑ |
| 4 | Crash Capture and Keystore Wedge (CCRM-85, CCBG-46) | session · fresh Opus judge | 2, 3 | ☑ |
| 5 | Visible fixes: About privacy row, CCBG-35, CCBG-45, CCBG-48; CCBG-50 once wireframed | session | 2, 3; CCBG-50: wireframe + Robin's review call | ☐ |
| 6 | Docs and listing pack (CCRM-89, CCRM-90), RELEASING.md Play channel | session · Sonnet drafts copy · Robin approves the graphic | 3 | ☐ |
| 7 | Fold 7 device pass, both flavors, phone restored, release gate | session · phone over USB · Robin unlocks | 3–6 | ☐ |
| 8 | Release v1.9 on GitHub; AAB built from the same commit | session · fresh judge before publish · Robin at the phone | 7's gate | ☐ |
| 9 | Play Console: account, app, existing-key signing, internal-track upload | **Robin at the console** · session guides | 8, **Q4** | ☐ |

---

## Step 1 · Plan, file, review, freeze

**Who:** the planning session, 2026-09-27.

**Done when:**
- ☑ Fable (mesh-expert) drafted the plan, and the session checked its findings against the code
  (`QUERY_ALL_PACKAGES` at `AndroidManifest.xml:29-30`, `CredentialStore.kt:20-32`,
  `isMinifyEnabled = false`, and the highest IDs CCRM-85 and CCBG-45).
- ☑ Robin answered Q1–Q4 one at a time. Q4 is open by his choice.
- ☑ CCRM-86–91 and CCBG-46–49 are filed, the CCRM-66 split note is written, and CCBG-45, CCBG-35
  and CCRM-85 read Planned v1.9.
- ☑ Astra at xhigh returns non-blocking, or every remaining finding is answered in the text and
  Robin accepts the residual on the record. Then **FROZEN** replaces DRAFT in the status line, with
  the date.

**Reversal:** documentation only — `git revert` of the planning commits.

**Log:**
- 2026-09-27 — Fable drafted the plan. Robin answered: Q1 account plus internal test, Q2 fix as-is,
  Q3 existing key, Q4 open (a portfolio of small free apps, a possible one-time "Pro" IAP later).
  Items filed and this runbook written.
- 2026-09-27 — Astra xhigh round 1: **blocking**, with five findings, all adopted.
  1. CCBG-46's wipe list was too broad (`InvalidKeyException` includes
     `UserNotAuthenticatedException`), so wiping is narrowed to AEADBadTag and
     KeyPermanentlyInvalidated, and the preserve cases get tests.
  2. The AAB was built after publishing, so both artefacts are now built and verified before the tag.
  3. The phone was restored to github only on the success path, so *Always after* now reinstalls the
     kept github APK.
  4. bundletool would have signed with the debug key, so it now gets the release keystore and a
     signer check before install.
  5. The privacy blob URL contradicted the "no github.com" check, so the check now targets update
     endpoints and classes via a `tools/` script and is rerun at Step 8.
- 2026-09-27 — Astra xhigh round 2: **blocking**, with four findings, all adopted in the text.
  1. Permanent-key recovery now also deletes and rebuilds the master key, with a write/read probe
     and tests (only CredentialStore uses that key).
  2. The whole-plan reversal said the phone could be restored, but after v1.9 it can only roll
     forward. Step 7 now keeps the v1.8 APK and says installing v1.9 is one-way for the phone.
  3. Step 8 now requires CI green on the exact release commit.
  4. CCRM-87's own acceptance criterion now matches the update-endpoint check.
  Adoption stopped (no third round), so freezing is Robin's call.
- 2026-09-27 — Robin: *"I allow up to 5 Astra rounds for this session. Go and let's get it cleared.
  Just keep the open question on Company/Personal account open and think for both with PROs and
  CONs of each."* The Q4 pros-and-cons table was added to Step 9, and round 3 was sent.
- 2026-09-27 — Astra xhigh round 3: **concerns**, with four findings, all adopted.
  1. Q4's disclosure rows were wrong: a personal account shows Robin's legal identity even on a free
     app, monetised disclosure is Play's own rule, and an organisation at a home address shows that
     address. The rows are corrected, with a read-on-the-day check before Robin pays.
  2. Step 8 now checks Play's current upload requirements (target API, 16 KB pages) against the AAB
     before publishing.
  3. versionCode 25 now moves to the start of Step 7, and Step 8 installs the published APK on the
     phone and checks it.
  4. CI now runs both flavors' tests.
  Plan **FROZEN**.

---

## Step 2 · Wireframe — every visible v1.9 change, reviewed to approval

**Who:** a Sonnet sub-agent draws (purpose b), the session does the polish pass and reviews, and
Robin approves.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 2; ROADMAP.md's v1.9 section, CCRM-85 (Crash
Capture) in full; BUGS.md CCBG-35 (Tab Clip) and CCBG-48 (Reconfigure Label). Open
design/2026-09-17-main-screen-redesign.html for conventions (CSS tokens, .phone frame, numbered
h2 sections, a "For review" banner). Brief a Sonnet sub-agent (purpose b) to draw
design/2026-09-2x-v19-play-ready.html with real dp/sp, on the Fold 7 cover and inner widths and at
360 dp, dark and light: (1) the About card with a "Privacy policy" row, both flavors; (2) the
CCRM-85 next-launch card "Cooldown crashed on <date> · Share report / Not now" — present, after
Share, after Not now (gone), two crashes on file, a crash with no trace (ExitReasons only) — and
where it sits relative to the existing status line and notices; (3) the Debug section (behind the
7-tap unlock) with a "Crash now" entry for the Step 7 test; (4) the play flavor's Settings More tab
with the Updates card absent, and the invalidResponse notice without its "Check for updates"
action; (5) CCBG-35 — the Settings tab row at 360 dp and at font scale 2.0, with a proposed fix
(e.g. scrollable tabs or shorter titles); (6) CCBG-48 — only if the approved rev D reconfigure view
(design/2026-09-25-widgets-reborn.html) already shows "Save changes"; if it does, it is a
restoration and needs no drawing, so say so. Do the polish pass yourself. Minor gaps go to a Fable
judge, then Astra. Then name the file to Robin and ask one question at a time. On "change X",
revise (rev B …). No code. When Robin says approved, write the revision and every decision into the
Status lines of CCRM-85, CCRM-87, CCRM-89, CCBG-35 and CCBG-48; close out per Convention 4.
```

**Done when:**
- ☑ Robin has said "approved" to a named revision.
- ☑ Every decision is written into the items' Status lines, including whether CCBG-48 is a
  restoration.

**Reversal:** documentation only.

**Log:**

- 2026-09-30 — rev A drawn (Sonnet, 703e4a5). The seat's polish pass produced rev B: every existing element redrawn from the code, the crash card moved above the account tabs, share and exit-reason rules, fit-based tabs, a Crash reports card. Fable was out of credits, so a fresh Opus judge ruled instead: keep rev B on all 7 calls, 6 concerns adopted. Astra (high) agreed with all 7 and raised 2 concerns: one adopted, one (where Crash reports lives) goes to Robin as Q1. Verdict in `design/research/2026-09-30-v19-wireframe-revB-astra.txt`. Waiting for Robin.
- 2026-09-30 — Robin answered Q1–Q7 one at a time, each as drawn (Crash reports stays behind the unlock), and **approved rev B**. Decisions are in the Status lines of CCRM-85, 87 and 89 and CCBG-35 and 48; CCBG-48 is a restoration. Step 2 done.

---

## Step 3 · Channel split, scoped queries, CI

**Who:** the session (rung 1). After it, a **fresh Opus judge** (mesh-judge, purpose c) reviews the
diff, because it touches the release path and the signing config.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 3; ROADMAP.md CCRM-86 (Play Readiness), CCRM-87
(Update Channel), CCRM-88 (Scoped Queries), CCRM-91 (CI Gate); RELEASING.md; app/build.gradle.kts;
app/src/main/AndroidManifest.xml; .github/dependabot.yml; the approved Step 2 wireframe's
play-flavor section. Build, one commit per item:
CCRM-87 — flavor dimension "channel", flavors github and play, same applicationId; move
UpdateCheck, UpdateNotification, the Polling auto-check hook, the Updates card and the
invalidResponse "Check for updates" action behind a small UpdateChannel interface with the github
implementation under src/github/ and a no-op under src/play/ that also clears an inherited
latestKnownVersion; tests on both flavors, including the stale-latestKnownVersion case. Confirm the
real task names on AGP (testGithubDebugUnitTest, assembleGithubDebug, assembleGithubRelease,
bundlePlayRelease — or whatever the aggregate names are) and rewrite Convention 2 and RELEASING.md's
build lines in the same commit. The release asset must keep the file name app-release.apk (copy
the flavor output to that name in RELEASING.md's order).
CCRM-88 — drop QUERY_ALL_PACKAGES from main, add a <queries> intent for ACTION_VIEW + BROWSABLE +
https; the github re-add is decided at Step 7, not now.
CCRM-91 — .github/workflows/ci.yml running BOTH flavors' unit tests (github and play), an unsigned assembleGithubRelease,
bundlePlayRelease and lint (baseline if needed) on push and PR; no secrets; restore the
github-actions entry in dependabot.yml.
Check: `unzip -l` of both outputs has no .so; the play APK's dex and resources contain no
"api.github.com", no "releases/latest" and no UpdateCheck/UpdateNotification class (the privacy
policy's github.com/…/docs/privacy.md URL is allowed — write this check as a script in tools/ so
Step 8 reruns it on the final artefact); bundlePlayRelease signed locally passes `bundletool validate` and its signer
(keytool -printcert -jarfile) equals RELEASING.md's trusted digest. Then spawn a fresh mesh-judge
(Opus, purpose c) on the diff against CCRM-87/88/91; adopt or answer every finding. Close out per
Convention 4.
```

**Done when:**
- ☑ Both flavors compile, both flavors' tests are green, and the stale-`latestKnownVersion` test
  exists.
- ☑ The play artefact holds no update endpoint or update class (checked by the `tools/` script;
  the privacy URL is allowed), and every `.so` in either output is on the allowlist and ready for
  16 KB pages (amended 2026-09-30 from "no `.so`"; see the Log).
- ☑ The AAB passes `bundletool validate`, and its signer equals the trusted digest.
- ☑ CI runs both flavors' unit tests and is green on `main`.
- ☑ Convention 2 and RELEASING.md name the real tasks, and the release asset name stays
  `app-release.apk`.
- ☑ The judge's verdict is recorded and its findings are adopted or answered.

**Reversal:** `git revert` per commit, newest first. Delete `ci.yml` with its commit's revert.

**Log:**
- 2026-09-30 — CCRM-87 (Update Channel) built. Real task names on AGP 9.4: `testGithubDebugUnitTest`,
  `testPlayDebugUnitTest`, `assembleGithubRelease`, `bundlePlayRelease` (`testDebugUnitTest` is now
  ambiguous). Tests green on both flavors (657 github, 643 play). `bundletool` 1.18.3 installed from
  Homebrew for the AAB checks (reversal: `brew uninstall bundletool`).
- 2026-09-30 — **Spec gap: "no `.so`" was a false premise.** Every output, and v1.8's shipped APK,
  carries `lib/<4 ABIs>/libandroidx.graphics.path.so` (androidx.graphics:graphics-path 1.0.1, via
  Compose UI; PathIterator below API 34, and minSdk is 31, so it cannot be excluded). Fable was out
  of usage credits, so a fresh Opus judge ruled: **accept**, a minor gap, not Robin's. Its two
  concerns were adopted. Sol (GPT-6, high) cross-checked the verdict: **concerns**, two. Test the
  real device splits and the AAB's alignment config: adopted. Pin full ABI paths: adopted; its "verify
  the library's contents" half is answered, not adopted, because a hash pin would fail every Compose
  bump, and the 16 KB checks already run on whatever the contents are. Verdicts are in
  `design/research/2026-09-30-v19-step3-so-gap.txt`. The rule in `tools/check_artefact.py` is now: every `.so` sits at an allowlisted ABI
  path; every ELF PT_LOAD has p_align ≥ 0x4000; in an APK (alone or in an `.apks` set) it is stored
  uncompressed at a 16 KB offset; an AAB's BundleConfig asks for uncompressed native libraries with
  `PAGE_ALIGNMENT_16K`. It passes on the play AAB, the play and github APKs, a bundletool universal
  APK and all 172 device split APKs. A fake library fails it. Step 8 still reads Play's live
  requirements on the day.
- 2026-09-30 — CCRM-88 (Scoped Queries) built. A gap in the spec: the provider-app tap in the pinned
  notification resolves its target with `getLaunchIntentForPackage`, which package visibility hides,
  so `<queries>` also names the three provider packages (restores existing behaviour; nothing
  visible changes). Step 7's device check covers the browser picker and that tap, in both flavors.
- 2026-09-30 — CCRM-91 (CI Gate) built. Local lint on both flavors: 333 and 332 warnings, 0 errors,
  so no baseline. Lint runs per flavor (`lintGithubDebug lintPlayDebug`) rather than the bare `lint`,
  which covers only the default variant. Actions pinned to their current majors (checkout v7,
  setup-java v6, setup-gradle v6, upload-artifact v7); Dependabot keeps them current.
- 2026-09-30 — First CI run on 90271b3 **failed**: all 14 Robolectric classes, "Android SDK 36 requires
  Java 21 (have Java 17)". The Mac passes only because `~/.gradle/gradle.properties` points Gradle at
  JDK 21, which the repo never recorded. CI now sets up JDK 21.
- 2026-09-30 — CI green on `main` at 02df435 (run 36740267293: both flavors' tests, unsigned
  `assembleGithubRelease` + `bundlePlayRelease`, lint on both flavors). Signed artefacts rebuilt at
  HEAD: AAB `bundletool validate` passes, AAB/APK signers equal the trusted digest, no
  `QUERY_ALL_PACKAGES` in either merged manifest, and `tools/check_artefact.py` passes on the AAB,
  the play APK, a universal APK and every device split.
- 2026-09-30 — Fresh Opus judge on `126c701..90271b3` (Fable out of credits): **concerns**, six
  findings. J1 (CI on JDK 17) was already fixed at 02df435; J2–J5 adopted (JDK 21 in the build docs,
  a test rename, `UpdateInfo` and "Check for updates" added to the scan, `&&` in RELEASING §2); J6
  (SHA-pin the actions) answered, not adopted. Table and reasons:
  `design/research/2026-09-30-v19-step3-judge.md`. Two Dependabot PRs opened today ran on the old
  JDK 17 workflow and are red; they need a rebase onto 02df435 before they can go green. **Step 3
  closed.**

---

## Step 4 · Crash Capture and Keystore Wedge

**Who:** the session (rung 1). After it, a **fresh Opus judge** (purpose c), because CCBG-46 can
sign accounts out.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 4; ROADMAP.md CCRM-85 (Crash Capture) in full;
BUGS.md CCBG-46 (Keystore Wedge); data/CredentialStore.kt, Shortcuts.kt, diag/AppLog.kt; the
approved Step 2 wireframe's crash card and Debug "Crash now" entry. Build CCRM-85 exactly as
specified (Application class with the chained handler in finally, pure diag/CrashReport.kt with
the one scrubber and no exception messages, diag/ExitReasons.kt with the watermark rule, the
next-launch card, the Debug "Crash now" entry, the README/user-guide sentence). Build CCBG-46 as
its Fix line says: walk the cause chain; wipe ONLY on AEADBadTagException or
KeyPermanentlyInvalidatedException from the store's own open/decrypt, and then delete both the
prefs file and the MasterKey.DEFAULT_MASTER_KEY_ALIAS Keystore entry, rebuild, and prove it with a
write/read probe (probe fails → the in-memory case, never a second wipe); every other failure
(UserNotAuthenticatedException, locked device, other InvalidKey/KeyStore exceptions, a transient
error still failing after one retry) runs with an empty in-memory store, logs WARN and leaves the
file untouched; never from a BOOT_COMPLETED path. Tests: the scrubber strips tokens (with and
without "Bearer"), emails and messages; the handler always chains; each exception case goes the
right way and the preserve cases assert secure_credentials still exists; no wipe on the boot
path. Spawn a fresh mesh-judge (Opus, purpose c) on the diff against the
two specs; adopt or answer every finding. Close out per Convention 4.
```

**Done when:**
- ☑ Every test named above exists and passes on both flavors.
- ☑ The crash card matches the approved wireframe state by state (the copy is word for word, pinned
  by `CrashCopyTest`; the look is checked on the phone in Step 7).
- ☑ The judge's verdict is recorded.

**Reversal:** `git revert`. CCBG-46 has wiped nothing yet, because it runs only on phones that get
the build.

**Log:**
- 2026-09-30 — Built. The tests are `CredentialStoreTest` (every exception case, preserve cases
  assert the file survives, the reset reads back from a fresh store, the no-reset boot path and a
  source scan for it), `CrashReportTest` (the scrubber with and without Bearer, emails, keyed
  secrets, canaries in every kept field and in sample ANR and native tombstone traces, no messages),
  `CrashHandlerTest` (always chains), `ExitReasonsTest` (whitelist, duplicates, watermark),
  `CrashCopyTest` and `CrashStoreTest`. The gate is green: 707 github tests and 693 play tests,
  both debug builds, lint on both. **Fresh Opus judge (purpose c):** round 1 was `blocking`
  (a share of eight ANR-sized reports could throw TransactionTooLargeException), plus five
  concerns and four nits. Every finding within the spec was fixed. The round 2 delta was `accept`.
  A first judge spawn died on the account's weekly limit before reading anything and was re-run.
  **Robin, 2026-09-30:** a corrupted *value* stays a preserve case, as specced. The degraded-store
  notice is filed as CCBG-50 and is to be wireframed for v1.9. One ungraded note: at the 5-minute
  background retry a main-thread getter can run the two-attempt retry (≈200 ms) under the opener's
  lock. It applies only while degraded, and was accepted.
- 2026-09-30 — **Astra xhigh call 4**, an exception Robin granted for the scope change and the
  CCBG-46 rulings: **concerns**. The 13 prior findings are confirmed fixed, and three new ones were
  adopted (table in `design/research/2026-09-30-v19-plan-findings.md`, verdict verbatim in
  `…-astra-call4.txt`): C4-1, a degraded process that holds a write stays on memory, with no merge;
  C4-2, a trace cut at the read cap drops its partial last line before scrubbing; C4-3, dismissal
  is the chooser's pick callback only, and a failed launch keeps the card (fixing a missing
  NEW_TASK flag for non-Activity contexts on the way). Robin's instruction: adopt everything agreed
  and bring back only disagreements. There were none.

---

## Step 5 · Visible fixes

**Who:** the session (rung 1).

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 5; ROADMAP.md CCRM-89 (Privacy Policy); BUGS.md
CCBG-35 (Tab Clip), CCBG-45 (Single Panel Wording), CCBG-48 (Reconfigure Label); the approved Step
2 wireframe; ROADMAP's v1.8 R1 for the "5h"/"Weekly" wording. Build, one commit each: the About
card's "Privacy policy" row (opens the docs/privacy.md blob URL on main); CCBG-35 as approved;
CCBG-45 — the one-account notification uses the Duet wording, "5h" and "Weekly", collapsed and
expanded (Robin's Q2: fix as-is); CCBG-48 only if Step 2 ruled it a restoration. Check each at 360
dp and on the Fold 7 widths in the emulator. Close out per Convention 4.
Before any of that: CCBG-50 (Degraded Store Notice) was added to v1.9 by Robin on 2026-09-30 and
cleared by Astra's exception call 4 (concerns, adopted). Draw its wireframe in design/ (every state,
both width classes) and get Robin's approval, then build it here too. The states must include:
degraded with nothing written; a sign-in or sign-out made while degraded (the notice says it lasts
only until the app restarts); recovery mid-session with nothing written (the notice goes); and the
restart after a degraded write (the interim write is gone and the preserved file is back). The
policy is fixed in code: once the in-memory store holds a write, the process stays on it, and nothing
is merged into or overwrites the file. Also draw a Debug-only "simulate degraded store" entry,
in memory only, so Step 7 can check every state without touching the Keystore. CredentialStoreTest
already pins the policy.
```

**Done when:**
- ☐ Each fix matches the wireframe or the approved design it restores, and the tests are green.

**Reversal:** `git revert`.

**Log:**

---

## Step 6 · Docs and listing pack

**Who:** the session. A Sonnet sub-agent drafts the listing copy (purpose b) and the session edits
it. The session draws the feature graphic, and Robin approves it in `design/`.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 6; ROADMAP.md CCRM-89 (Privacy Policy), CCRM-90
(Listing Pack), CCRM-67 (Pin Service)'s FGS risk note; README (the privacy and install sections);
.github/SECURITY.md; AndroidManifest.xml (the specialUse subtype); data/AccountEmail.kt and the
endpoints each provider calls. Write docs/privacy.md (true to the code, both flavors); write
release/play/data-safety.md from code, question by question, taking the maximal honest answer
wherever Play's definition is unclear and marking those lines "re-read on the day"; brief a
Sonnet sub-agent (purpose b) to draft release/play/listing.md (title, short and full description;
no provider mark in title/icon/graphic; nominative mentions only), then edit it; write
release/play/fgs-declaration.md and release/play/app-access.md; draw
design/2026-09-2x-play-feature-graphic.html (1024×500, the Pulse icon, no provider marks) and ask
Robin to approve it; add a "Play channel" section to RELEASING.md (the AAB from the release
commit, the checks, never attached to the GitHub release, versionCode shared). README keeps "not
on any store" until a launch. Close out per Convention 4.
```

**Done when:**
- ☐ Every file above exists and agrees with the code.
- ☐ Robin has said "approved" to the graphic.
- ☐ RELEASING.md has the Play channel section.

**Reversal:** `git revert`.

**Log:**

---

## Step 7 · Fold 7 device pass

**Who:** the session, with the phone over USB adb. Robin unlocks the phone, and nobody stores the
pattern. Keep the screen awake for the whole session, and put auto-off back before disconnecting.

**Before and Always after:** use the v1.8 Step 7 blocks verbatim (`git show 6e4c1a0:RUNBOOK.md`,
"Step 7 · Fold 7 device pass"). Record the phone's state into
`design/research/2026-09-xx-v19-device-pass/phone-state-before.txt`. Restore every value that was
**recorded**, never a default. A resumed session runs *Always after* first. v1.9 adds two things:
- **To Before:** pull the installed APK and keep it in the research folder (outside git), with its
  path, sha256 and versionCode. This is the pre-arc build, v1.8 (`2eea1a4c…8819`) as of 2026-09-27.
- **To Always after, as its first line:** pull the installed APK. If its sha256 is not the github
  build's (logged in the research folder when item 1 installs it; that APK is kept in the folder,
  outside git), `adb install -r` that github APK and check its sha256 again. The phone never leaves
  the cable on a play build, on success, failure or a cut-short session.

**Two different restorations.** *After flavor testing:* the phone goes back to the v1.9 github
candidate, as described above. *Rolling back the whole arc:* Android refuses to downgrade a
non-debuggable app without an uninstall, and an uninstall loses data. So once item 1 installs
versionCode 25, the phone's only rollback is **forward**: a build of the reverted `main` with a
higher versionCode, installed with `adb install -r`. The kept v1.8 APK is the record and the
last-resort reinstall after an uninstall, which only Robin may choose. Installing item 1 is
therefore a one-way step for this phone, and Robin accepts it by starting Step 7.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 7; the v1.8 Step 7 Before / Always-after blocks
(git show 6e4c1a0:RUNBOOK.md); the Status lines of CCRM-85–91, CCBG-35, 45, 46, 48. Memory notes
on the Fold 7 (screencap display ids, the secure bouncer, shade cropping) apply. Run Before. First commit versionCode 25 / versionName
"1.9" (both flavors) — the candidate carries the release version, so the phone never needs a
downgrade. Then:
(1) install the github release build over v1.8 with adb install -r — accounts and history survive;
(2) the browser picker lists Samsung Internet, Chrome and any other installed browser — if One UI
under-reports, re-add QUERY_ALL_PACKAGES in src/github only and note the play-flavor gap; (3) the
Updates card, auto-check and the update strip work as before on github; (4) the crash: Debug
"Crash now" → the system dialog still appears → relaunch → the card shows → Share → the shared text
has no token, email or exception message → Not now keeps it gone; also: backing out of the share
sheet keeps the card, and picking an app removes it; (4b) CCBG-46 and CCBG-50, without touching the
Keystore: sign-in persists across a force-stop and relaunch, and the CCBG-50 Debug "simulate degraded
store" entry shows each approved notice state, including a sign-in made while degraded; (5) the pin survives
MY_PACKAGE_REPLACED and a reboot; (6) the CCRM-90 FGS demo video with screenrecord (display id per
the memory note), saved to release/play/; (7) the play flavor: bundletool build-apks --connected-
device with --ks ccooldown-release.jks, --ks-key-alias and the passwords from keystore.properties
(never the debug key), unzip the .apks and check every APK's signer with apksigner against the
trusted digest BEFORE install-apks over the github build (same key, same versionCode — if the
installer refuses the equal versionCode, adb uninstall is NOT allowed: build a signed play APK with
assemblePlayRelease, check its signer, and adb install -r it instead) → no Updates card, no update strip
even though github had written latestKnownVersion, no "Check for updates" action, the picker
works; (8) end on the github build: adb install -r the github APK kept from item 1 and check its sha256
(Always after repeats this if the session is cut short); (9) CCBG-35, CCBG-45 and the privacy row on the phone. File every defect in
BUGS.md with a severity. Run Always after and diff. Gate for Step 8: no High open, and every
deferral accepted in Robin's words. Close out per Convention 4.
```

**Done when:**
- ☐ Items 1–9 are recorded, with shots in the research folder (the private shade is cropped to the
  card).
- ☐ The phone ends on the github build, its sha256 is logged, and the *Always after* diff is empty.
- ☐ Gate: no High open, and every deferral is accepted by Robin on the record.

**Reversal:** the *Always after* block restores the phone, and code fixes are `git revert`. The
phone never loses data in this pass. No uninstall is allowed and no destructive test runs.

**Log:**

---

## Step 8 · Release v1.9

**Who:** the session. A fresh judge reviews before publishing (RELEASING.md rule), and Robin
confirms "Check for updates" on the phone. **Irreversible once published.**

**Resume in a fresh session:**

```
Read CLAUDE.md; RELEASING.md (with the Play channel section and Step 3's task names); RUNBOOK.md
Conventions and Step 8; the Status lines Step 7 verified. versionCode 25 / versionName "1.9" is already set
(Step 7). Docs: USER-GUIDE.md changelog; README bullets (crash reports stay on the phone;
privacy policy link); guide.html and brochure.html rebuilt with release/docs/build.sh, every
changed page read in the PDF; release notes (app only — no build story): crash capture, a steadier
credential store, the privacy policy, the fixes. Then RELEASING.md's order, with both
artefacts built and verified BEFORE anything is published: commit → push main → **CI green on
that exact sha** (gh run list --commit <sha>; pending or failed stops here, and any recommit
repeats this gate) → build BOTH from it
(assembleGithubRelease, asset copied to app-release.apk; bundlePlayRelease) → verify the AAB
(bundletool validate, signer = trusted digest, versionCode 25 / 1.9, the tools/ update-path check
on a universal APK built from it with the release key, and Play's CURRENT upload requirements read
on the day — target API level, 16 KB page size, anything new — so a published v1.9 never needs
replacing before Step 9) → tag and push → draft with --verify-tag →
download and verify the APK (sha256 equal, signer = trusted digest, versionCode 25 / 1.9) → a
fresh mesh-judge verdict covering both artefacts → publish with --latest → adb install -r the downloaded published APK on the Fold 7 and check the
installed sha256 equals the asset (the phone already holds a versionCode 25 candidate, so "Check
for updates" will not replace it; Robin's tap only confirms it reads up to date). Log the AAB's sha256
and signer here; do not attach it. Any AAB failure stops before the tag: fix, recommit, rebuild
both. Robin taps Check for updates.
Set the shipped items to Shipped v1.9; close out per Convention 4.
```

**Done when:**
- ☐ v1.9 is published and `releases/latest` resolves to it. The phone runs the published APK
  (sha256 checked), and "Check for updates" reads up to date.
- ☐ Play's current upload requirements were checked against the AAB before publishing.
- ☐ CI was green on the exact release commit before the tag.
- ☐ The AAB was built and verified from the release commit **before publishing**. Its sha256 and
  signer are logged here, and the file is kept outside git for Step 9.
- ☐ Every arc item reads Shipped v1.9, or Deferred with a reason.

**If it goes wrong mid-way:** as in RELEASING.md.
- Before the draft is published, nothing is public: delete the draft and move the tag only if it
  must move.
- After publishing, there are two recoveries. To withdraw distribution: `gh release edit v1.9
  --draft`. To recover installed apps: v1.9.1 (versionCode 26) on both channels.

**Log:**

---

## Step 9 · Play Console — account, app, existing-key signing, internal testing

**Who:** **Robin at the console, every click his.** The session guides from `release/play/` and
checks results, and never holds a console login. **Blocked until Q4 (the account type) is answered**,
by Robin after his talk with Fable. That answer is recorded here before anything else.

### Q4 — personal or organisation account (open; both paths are planned)

Robin's context: a portfolio of small single-purpose apps (Cooldown, then simple timer or checklist
apps) under one developer name. They are all free and open source now, with a possible one-time
"Pro" in-app purchase later. Rows marked † are Play facts to re-read on the day, never trusted from
this file.

| | Personal | Organisation |
|---|---|---|
| Cost and paperwork | The one-time fee (US $25 †) and a government ID check. No company needed. | The same fee, plus a D-U-N-S number (free, but days to weeks †) for a legal entity **Robin owns**. Verification is longer, and a website and contact details may be asked for †. |
| Getting to production | New personal accounts must run a closed test with **12 opted-in testers for 14 days in a row** † before they can apply for production. v1.9 stops at the internal track, so this bites only at launch, and again for **every new app** in the portfolio †. | No closed-test rule †. Straight to production once the app passes review. |
| What the public sees | Play verifies and may display **Robin's legal identity** (name and country †) even on a free app. Once an app sells something (the "Pro" purchase), **address and contact details are shown publicly** †. That is Play's own monetisation rule, not only the EU's. A private address needs a business or virtual address †. | The organisation's name and its registered address †. It reads as a studio brand across many apps. An organisation **registered at Robin's home shows that address**, so keeping home private needs a separate business address either way. |
| Payments ("Pro" later) | A payments profile in Robin's name. Income is personal income. | A payments profile in the organisation's name, which also suits tax and invoices. |
| Changing course | Apps can be **transferred** to an organisation account later, keeping users, reviews and the signing key †. So "personal now, organisation later" is a real path. | Going back is also a transfer, but there is rarely a reason to. |
| Fits v1.9 (internal track only) | Yes, today. | Yes, once the D-U-N-S arrives. That can delay Step 9 by weeks. |

**The session's read, not a decision:**
- **Personal** is cheapest and fastest for Step 9. Its costs arrive later: the 12-tester gate for each
  new app, and a public home address once "Pro" ships.
- **Organisation** costs time up front and fits the portfolio-and-"Pro" plan best.
- **Personal now, transfer later** keeps v1.9 moving, at the price of one transfer. The Pulse key
  enrolled under Q3 travels with the app.

**Before Robin chooses and pays:** read Play's current identity and disclosure pages for both account
types on the day. The † rows are the ones most likely to have moved.

**Robin decides after his talk with Fable.** The answer goes into this step's Log before anything
else happens. Everything below applies to both paths, except that the organisation path first waits
for the D-U-N-S.

**Irreversible, named up front:**
1. the registration fee and the identity verification, with the developer name and country made
   public;
2. the package name `com.robin.claudeusage` claimed on Play for good. "claude" in the store URL is
   a trademark risk, recorded for the launch decision;
3. enrolling our existing key in Play App Signing (Q3). Google then holds a copy of the key;
4. the app's **Free** setting, which can never become paid upfront. In-app purchases stay possible,
   which fits Robin's possible "Pro" purchase.

**Resume in a fresh session:**

```
Read CLAUDE.md; RUNBOOK.md Conventions and Step 9 (Q4's answer must be in its Log — if not, stop);
ROADMAP.md CCRM-66 (Play Store Launch) and CCRM-86–90; release/play/*; docs/privacy.md;
RELEASING.md's Play channel section and trusted digest. Confirm the keystore backup exists where
Robin keeps it before anything is exported. Guide Robin, one screen at a time, naming each
irreversible choice before he makes it: (1) the developer account of the Q4 type; (2) create the
app — name "Cooldown" (or the fallback Robin picks if taken), app, Free; (3) Play App Signing →
use an existing key → export with Google's PEPK tool from ccooldown-release.jks into the
scratchpad, upload, then delete the PEPK output (never in git); the same key stays the upload key;
(4) the declarations the Console demands before an internal release, filled from release/play/ —
re-read each Play definition on the day against our drafts, fix the drafts in the repo if they
disagree; (5) Internal testing → create a release with the v1.9 AAB logged in Step 8 → testers:
Robin's own address → roll out. Verify: App bundle explorer → the signed universal APK's signer =
RELEASING.md's trusted digest; the release shows "available to internal testers"; the opt-in link
opens. The Fold 7 keeps the github build (same versionCode — Play will show it as installed).
Do not start closed testing, and do not touch production. Close out per Convention 4, and write
HANDOVER.md's next block around the launch decision (the provider check, CCRM-66).
```

**Done when:**
- ☐ Q4's answer and the account are recorded.
- ☐ The app exists, and the existing key is enrolled: the Play-signed APK's signer equals the
  trusted digest.
- ☐ v1.9 is available on the internal track, and no closed or production track has been touched.
- ☐ No key material or PEPK output is left outside Robin's backup.

**Reversal:** none for items 1–4 above. The internal release can be halted and the app record left
unpublished. Whether the app record can be deleted after an internal upload is unverified, so treat
it as permanent.

**Log:**

---

## Risks and partial states

- **Unverified until the step that meets it:**
  - AGP's aggregate task names under flavors (Step 3);
  - whether One UI reports every browser through scoped `<queries>` (Step 7);
  - whether the installer accepts an equal-versionCode sibling-flavor install (Step 7 has the
    fallback);
  - `screenrecord` on the cover display (Step 7);
  - Play's exact Data-safety definitions and the in-app privacy-link wording (Step 9, on the day);
  - which declarations an internal release demands (Step 9).
- **The flavor split renames every Gradle task.** Until Step 3's commit rewrites Convention 2, the
  paste blocks name the old tasks.
- **A play build left on the phone** strands Robin without update checks. Step 7 ends on the github
  build by rule, and Step 9 never installs from Play onto the Fold 7.
- **CCBG-46 wipes on a transient error** → every account signed out. This is guarded by the
  transient/unrecoverable split, the boot-path exclusion, tests and a fresh judge. Only tokens are at
  risk, and signing in mints them again.
- **CCRM-85's handler hides a crash.** It is chained in `finally`, and Step 7 proves the system dialog
  still appears.
- **Play review sees the app before the provider check.** The internal track is not a public
  listing and gets limited review. The check still comes before any closed test (Q1), and that
  timing is Robin's accepted trade-off.
- **Partial execution:**
  - after Steps 3–6: unreleased commits, reversed with `git revert` newest first, each step on its
    own;
  - after Step 7 has begun: run *Always after* first;
  - after Step 8 publishes: forward recovery only (v1.9.1, versionCode 26, both channels);
  - Step 9: no reversal. See its list.
- **Whole-plan reversal before Step 8:** revert the arc's commits newest first (this removes
  `ci.yml` and restores `dependabot.yml`). Before Step 7, nothing outside git has changed. After
  Step 7's item 1, the phone rolls **forward** to a build of the reverted `main` (see Step 7's "Two
  different restorations"). *Always after* restores its settings.
