# Repo structure — where Cooldown's five clients live

**Decided 2026-10-23 (Robin): two repos by product family, one `spec/` folder per repo; the shared core's master is
Cooldown's `spec/core/`.** Amended the same day (below): the core shrank to what a provider can break.
Drafted by Fable (expert, xhigh) at Robin's request; the seat verified the release-stream claim in
code (`UpdateCheck.normalize` strips only a leading "v"); Astra reviewed it at xhigh (disposition below).
Status: step 1 and step 2 done 2026-10-23 (CCRM-92 (Shared Spec)); step 3 is the desktop repo's,
run by a session rooted there; steps 4–6 wait on their triggers.

## Context
- Cooldown: PUBLIC GitHub repo, MIT. Android (Kotlin) at the root, reference implementation; an iPhone/iPad app
  (Swift) under ios/ since 23 Oct; same-day feature parity intended. Android's in-app update check reads this repo's
  GitHub `releases/latest`; its version parse strips only a leading "v" and treats any non-numeric part as 0, so a
  release tagged e.g. "mac-v1.0" would read as version 0 and every phone would silently report "up to date".
- CooldownMac: PRIVATE repo, Swift package, macOS menu-bar app, ~669 commits, own cadence, its own update check reads
  its own `releases/latest`. Its CCooldownCore is meant to be reused as-is by a Linux client. Desktop-only abilities:
  reading local Claude Code transcripts and Codex logs (the Ledger), local credentials, pricing. A separate, already
  reviewed and partly executed plan (CCM-132) will eventually make the Mac repo public; that flip is its own step.
- The shared contract (Markdown + JSON: API contract, behaviour spec, fixtures) lives today in CooldownMac/contract/.
  Robin has approved publishing everything shared in it in Cooldown under spec/ (CCRM-92 (Shared Spec)).
- Windows and Linux clients are planned; stack not chosen.

## Recommendation
Two repos by product family, **one `spec/` folder per repo**, built the same way: `spec/core/` + the family layer.
- Cooldown = mobile: Android, ios/, and `spec/` = `spec/core/` (**the master** of the shared core) + `spec/mobile/`.
- CooldownMac = desktop: Mac now, Linux and Windows later around one Swift core; its `contract/` becomes `spec/` =
  `spec/core/` (a pinned copy of Cooldown's `spec/core/`, pulled by a script that writes a PIN file with the Cooldown
  commit, plus a test that fails when the copy and the pin disagree) + `spec/desktop/` (the desktop layer), which
  never enters Cooldown.
**The core is split by cause of change, not by family** (amendment, 23 Oct, Robin's challenge "features differ by
family", answered by Fable): it holds only server facts and the fixture-backed numbers every install must agree on —
the API contract minus polling cadence, the payload fixtures, projection and history cases, severity, credits, pace,
colour breakpoints and accents, the even-pace formula, history, reset detection, profiles §9.1–9.2, ChatGPT's
GET-only/no-cookie/redact rules (§9.6), and the fetch-outcome taxonomy. Feature lists are per family: alerts,
polling cadence, conditions and surfaces live in `spec/mobile/` or `spec/desktop/`. Admission rule (in
`spec/README.md`): core is a server fact, or a fixture-backed number two installs on different platforms must agree
on; a feature's existence, surfaces, cadence and copy are family. Presentation is per platform and in no spec.
Flow rule: a core bug found anywhere gets its failing fixture in Cooldown's `spec/core/` first (a desktop session writes it
through a worker rooted in Cooldown), then the desktop repo pulls the new pin.
Rejected: one monorepo (desktop releases would hijack the phones' `releases/latest` or need permanent pre-release
flags; merging publishes private history irreversibly; contended tracker files); spec as a git submodule (agent-hostile;
the EAM360 precedent backed out of it); master spec in the desktop repo (Android is the reference, Cooldown is public
with CI, and holds the two clients that ship identical features).

## Steps (each in its own commit)
1. Record the decision: CCRM-92 (Shared Spec) says spec/ is the master; Cooldown CLAUDE.md §3 says the desktop repo
   holds Windows and Linux. Reversal: one revert.
2. Build Cooldown spec/ seeded from the Mac contract's core parts only (verbatim files; BEHAVIOR-SPEC cut once at the
   desktop section boundaries — as built: §9.3–§9.5 and §11 out, §9.6 kept because its GET-only, no-cookie and
   redact-before-saving rules bind phones too), plus Android's two captured payloads, which Android's tests then read from spec/;
   a no-code check in CI; a private-data scan of the seed commit before it is pushed to the public repo. Gate: Android's
   test suite passes unchanged. Reversal before push: one revert; after push the content is public (Robin approved).
2a. Done 23 Oct (amendment): `spec/` reorganised to `spec/core/` + `spec/mobile/`; the core cut by the admission rule
   (API §6 cadence, BEHAVIOR §8 alerts except reset detection, gauge geometry, desktop surfaces, the pace-alert
   fixture out; User-Agent examples platform-neutral); `spec/mobile/BEHAVIOR-MOBILE.md` written from Android's code.
   Gate: Android's suite unchanged. Reversal: one revert.
3. Desktop repo, done by a worker rooted there: `contract/` becomes `spec/` = `spec/core/` (pinned copy) +
   `spec/desktop/` (everything the core cut that is desktop: §8 alerts, polling cadence, gauge geometry, Mac
   surfaces, §9.3–9.5, §11, ledger/logins/pricing/alerts fixtures), fixture path update, pull script, PIN and pin
   test. Gate: swift test. Reversal: one revert.
4. Later, Robin's call: split the Swift core into a cross-platform CooldownCore (macOS/iOS/Linux) and a
   CooldownDesktopCore, so iOS can depend on CooldownCore by package URL instead of forking it — possible only once the
   desktop repo is public (CCM-132's flip) or with a CI token. Reversal: one revert.
5. Optional before the desktop's first public release: rename CooldownMac (its name is compiled into its update
   check). Checked 23 Oct: the repo has no GitHub releases; whether builds are installed on Robin's or a tester's Mac
   is UNVERIFIED, and GitHub's redirect of a renamed repo's API URL is UNVERIFIED. Before renaming, list installed
   builds and test the old update URL after a rename on a scratch repo. Reversal: rename back.
6. Before a second desktop client ships: desktop releases are coordinated (one release carries every desktop
   platform's asset under one version, like the APK+IPA rule) or each client's update check filters by its own asset
   name — the desktop repo has the same `releases/latest` collision the phones would have had.

## Review disposition
Astra (xhigh, 23 Oct): concerns ×2, both adopted — step 6 added (desktop release collision), step 5 now requires
checking installed builds and the rename redirect first.
