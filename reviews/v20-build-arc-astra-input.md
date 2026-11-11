REVIEW: full · plan=v20-build-arc

Scope and threat model: a multi-session execution plan (a runbook) for a small Android + iOS app that shows the user's own Claude, ChatGPT and Gemini usage limits. It builds nine already-approved UI items on both platforms, then releases v2.0 to GitHub, TestFlight and a Google Play closed test. Sessions run in separate git worktrees and land by merging main, pushing the branch, then a plain fast-forward push to main (no force, ever). Risks to find: two sessions editing the same lines of a shared file; main going red for one platform when the other lands; shared test fixtures changing under a client; an irreversible store step on an unchecked precondition; a reserved decision taken by a session; a partial state with no way back.

## Goal
Every item built on both platforms and checked; one release commit; a published GitHub v2.0 carrying the Android APK and the iOS build under the same version (never an iOS-only release); the TestFlight build uploaded; Play Day 0 (internal upload, closed track, invites).

## Closed decisions
All nine items ship in v2.0. Version: Android 26 / "2.0", iOS 2.0 build 1, set only in the release commit. Play on a personal account (not yet registered) with the 12-testers-for-14-days rule. Sample-data mode is Play's reviewer path. Anything visible beyond the approved wireframe stops the session; it is never improvised.

## Shape
Step 1 (serial) writes all shared spec material. Then two code lanes run at the same time: Lane A (Android, five steps in order) and Lane I (iOS, three steps in order); they share no code. A docs lane D (privacy policy, Play declarations, listing text, a feature-graphic wireframe for the owner) runs beside them and touches no code and no tracker file. Then a serial tail: fold-in, device pass, release commit, store assets, TestFlight upload (owner), GitHub publish, Play Day 0 (owner).

Batches are grouped by shared code: "lines and labels" (chip label, chart day grid, pace-line wording), "pace map" (hide quiet accounts, map layers and label leaders), "summary" (account tags, filter, sort, snapshot share), "duet" (Android notification only), "sample" (Android sample mode; iOS gets a one-account swap in its summary step). One item per step was rejected: seventeen sessions re-reading the same code.

## Shared files — how two writers never collide
Rule basis: a dry run in a scratch repo showed git merges two branches cleanly when one unchanged line separates their edits, and conflicts when edits abut.
- Shared spec and fixtures: Step 1 writes; read-only for the lanes; a wrong fixture stops the lane and the orchestrating session gives the fix to one session.
- Fixtures both clients read directly: Step 1 writes changed and new cases into new files no current test reads; each lane points its own tests at them; the fold-in step merges them into the old files and switches both clients' tests back in one commit. Until then the spec keeps the shipped rules, so main stays green for both platforms.
- Roadmap tracker: Lane A edits only each item's Status line; Lane I only an "iOS:" line that Step 1 adds as the entry's last bullet, with the item's description between them. The fold-in step merges them.
- Bug tracker: Lane A only during the lanes; Lane I and D record defects in their own log; the fold-in files them.
- Surface spec files (shared by both platforms): Step 1 appends an Android section and then an iOS section to each file, each with a placeholder; each lane edits only inside its own; the fold-in merges them into the body.
- The runbook: one progress table per lane, separated by headings; each lane writes only its own steps' logs. The handover file: one block per lane.
- Code: Lane A owns the Android tree, Lane I the iOS tree. Only Lane A uses gradle and the emulator; only Lane I the simulators.

## Steps (what changes · check · reversal)
0. Plan, one cross-family review, freeze · findings table · revert.
1. Spec first · fixtures derived by a committed script and checked against the wireframe's worked examples; both platforms' suites pass unchanged; one fresh judge · revert (nothing reads the new files).
A1–A5, I1–I3. Build each batch · unit tests on the new fixtures, lint, emulator or simulator captures of every state the wireframe draws at three widths, dark and light; a downgrade test for the one new stored preference (tags); four isolation tests for the sample mode; one fresh judge per step · revert the step's commits, newest first.
D1. Docs and graphic wireframe · the owner approves the graphic · revert. The privacy row for the new provider is marked "from version 2.0" so the live policy stays true for current users.
2. Fold-in · both suites on the merged fixtures, spec check, judge · revert returns to the lane state.
3. Device pass on the owner's phone and tablet · starting state recorded first, every item and the checks carried from the previous runbook, defects filed, the owner says what blocks, blocking ones fixed and judged · "always after" restores each recorded setting.
4. Release commit (version bump, docs) landed, CI green on that sha; from a clean tree at that sha, the APK, the Play bundle and the iOS archive built with the documented checks (signer digest, version codes, update-free Play build, 16 KB pages, secret present without printing it) · nothing tagged or uploaded · revert before tagging.
5. Store screenshots (cropped to Play's 2:1 limit), the foreground-service video, the approved graphic exported · revert.
6. Owner uploads the archive to TestFlight, Beta App Review · irreversible (build number spent).
7. Tag, draft, verify the draft's hashes and signer against step 4, then a review packet with a fresh judge of a different model (author's model is the planner's) and a GPT cross-check (it publishes), then the owner's go in chat naming the commit and hash, recorded verbatim, then publish · irreversible; withdraw from "latest" and fix forward in a new version code.
8. Owner at the Play Console, the session guides: existing-key signing, internal upload (package name claimed for good), check the store-signed APK's digest against the trusted digest before any tester installs, then closed track and invites · irreversible.

## Whole-plan reversal from a partial state
Steps 0–2 and the lanes are commits: revert newest first. With one lane done and the other not, main is green (each lane reads fixtures the other does not, and the spec keeps the shipped rules); finish the slow lane or revert the fast one; nothing ships, because the release needs the fold-in, which needs both lanes. Devices are restored by the recorded state. Steps 6–8 cannot be undone; fixes go forward on both channels under one version-code sequence.

## Reserved for the owner
Device checks and what blocks; any visible change beyond the wireframe; the graphic; the TestFlight upload; the release go; every Play Console action; recruiting testers; six open questions (whether to attach an IPA or a TestFlight link; whether publish waits for Beta App Review; screenshot crop and data; graphic now or later; an Android share-card variant in or out; two lanes at once on one machine).

## Failure modes
Landing conflict: abort the merge, stop, report. Wrong fixture: one spec fix, then both lanes re-merge. The notification may not fit the real shade (step 3 measures; a new width goes to the owner). Sample leaking into real data (isolation tests, device check). Review delays; testers dipping below 12 restarts the 14 days (recruit 15–18).

## UNVERIFIED
The phone's real notification widths; iOS control widths; two worktree builds at once on one machine; whether a new closed-track build resets Play's clock; whether Play's reviewer accepts the sample mode.

## Tests and gates
- Android unit tests (both flavors), lint, assemblies; iOS package and app tests (every code step, fold-in, release)
- The spec "no code" check (steps 1, 2)
- The fixture generator re-derives every new case (step 1)
- The widget-layout generator byte-identical (any widget change)
- Downgrade test for the tag preference (A4, I3)
- Four sample-mode isolation tests (A5)
- One fresh judge round per code step
- The release checks: asset hash equals build, signer equals the trusted digest, version codes, update-free Play build, archive checks (steps 4, 7)
- Different-model judge, GPT cross-check and the owner's recorded go before publishing (step 7)
- Store-signed APK digest equals the trusted digest before testers install (step 8)
- The merge dry run (step 0)
