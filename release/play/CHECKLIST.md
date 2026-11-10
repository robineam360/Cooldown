# Google Play listing checklist — prepared 2026-11-04, used after v2.0

> **2026-11-10 — superseded in order by [CONSOLE-STEPS.md](CONSOLE-STEPS.md).** Robin decided Play
> launches **at v2.0** on a **personal** account (Q4 answered), with the closed test after the
> eight v2.0 feedback items land ([CLOSED-TEST-PLAN.md](CLOSED-TEST-PLAN.md)). The tables below stay
> the asset inventory; [V2.0-ASSET-CHECK.md](V2.0-ASSET-CHECK.md) is the current v2.0 check.

**Status: Play is after v2.0** (Robin's scope call, 2026-11-04: "Till v2.0, let's focus on getting
the app out to everyone via the Cooldown repo and TestFlight. After v2.0 is published, let's work to
get our apps in the public GooglePlay and Apple App Store (and not now in v1.9 or v2.0)."). So for
v1.9 and v2.0 there is **no AAB, no Console step and no Q4 work**. This file only prepares the list,
so that the day Play starts nobody has to rediscover what the Console asks for. CCRM-66 (Play Store
Launch) and RUNBOOK Step 9 hold the decision; the `play` flavor stays in the tree and in CI.

**Owner:** *Robin* = Robin at the Console, every click his (RUNBOOK Convention 8); *session* = a
session drafts or checks it in the repo and never holds a Console login.
**Re-read on the day:** every limit, size and rule below is Play's as of drafting. Check it in the
Console before pasting.

## A. Recapture on the v2.0 build

v2.0 changes what a listing shows: the Weekly-first surfaces (CCRM-96 (Weekly First)), the Summary
tab (CCRM-98 (Summary Tab)), the hybrid notification with the weekly bar and a small 5h ring
(CCRM-103 (Hybrid Notification)), the per-account Vs-Pace figure (CCRM-100 (Vs Pace Display)) and a
new launcher icon (CCRM-101 (Icon v3)); all of them live on the v2.0 branch
`worktree-v20-arc-and-antigravity`. So nothing below that shows the app is taken on v1.9.

| Asset | Why it is taken on v2.0, not v1.9 | Status |
|---|---|---|
| Phone screenshots (2 minimum, 4–8 recommended, main screen and notification first) | The main screen, the notification and the cards all change in v2.0 | Not taken; recapture on the v2.0 build |
| App icon, 512×512 PNG | CCRM-101 (Icon v3) replaces the launcher icon, so the Play icon follows it | Not exported; export from the v2.0 icon |
| Feature graphic, 1024×500 | Variant B (`feature-graphic.png`) is built on the v1.9 Pulse icon and a pre-v2.0 look | Exists, approved 2026-10-01; Robin decides in the morning whether it survives the new icon |
| Foreground-service demo video (`fgs-demo.mp4`) | Shows the v1.9 notification; v2.0's notification is Weekly-first | **Recorded on v1.9, 2026-11-04** (45 s, Accounts tab cut: it showed email addresses). Re-record on the v2.0 build before Step 9 |
| Tablet screenshots (7-inch, 10-inch) | Optional | Skip unless Robin wants them |

## B. Console items (Main store listing, App content, Release)

| # | Item | What it needs | Status today | Owner |
|---|---|---|---|---|
| 1 | Developer account | Personal or organisation (Q4 in RUNBOOK Step 9), fee, ID check | Open; Q4 unanswered | Robin |
| 2 | Create app | Name, default language, free, app or game, declarations | After v2.0 | Robin |
| 3 | App name (30) | `Cooldown — AI usage limits` recommended | Drafted, `listing.md` §1 | session drafts, Robin pastes |
| 4 | Short description (80) | `See your 5-hour and weekly AI usage limits, and when they reset.` | Drafted; **rewrite for v2.0**: the weekly window now leads | session |
| 5 | Full description (4000) | 3344 characters, plain text | Drafted; **rewrite for v2.0** (Summary tab, Weekly first, Gemini) | session |
| 6 | App icon (512×512) | No provider marks | See §A | session exports, Robin uploads |
| 7 | Feature graphic (1024×500) | No alpha | See §A | Robin approves |
| 8 | Phone screenshots | 2–8, full frame, no private data, no endorsement framing | See §A | session shoots, Robin approves |
| 9 | Category, tags, contact | Tools; robin@eam360.com; the GitHub page | Drafted, `listing.md` §4 | Robin |
| 10 | Privacy policy URL | `docs/privacy.md` must resolve and agree with the data-safety answers | Policy exists; check the URL the day of submission | session checks |
| 11 | Data safety form | Answered for the play build, question by question | Drafted, `data-safety.md`; **re-check against v2.0** (Gemini adds a host: it must be in the policy) | session drafts, Robin submits |
| 12 | App access | Reviewer path: the Android sample mode (Robin, 2026-11-10) | Drafted, `app-access.md`; rewrite on the v2.0 build once the sample mode is built | Robin |
| 13 | Foreground service declaration (`specialUse`) | Written justification plus the demo video | Text drafted, `fgs-declaration.md`; video per §A | session drafts, Robin submits |
| 14 | Content rating (IARC) questionnaire | Answers about the app's content | Not started | Robin |
| 15 | Target audience and content | Age groups; not for children | Not started | Robin |
| 16 | Ads declaration | Contains no ads | Not started | Robin |
| 17 | Government apps, financial features, health | Declare "no" | Not started | Robin |
| 18 | Signing | Play App Signing with the existing key (PEPK export enrolled once, never staged) | After v2.0 | Robin |
| 19 | AAB | `bundlePlayRelease` from the release commit, the same versionCode, validated and signer-checked per RELEASING.md §5 | **Not built for v1.9 or v2.0**; built when Play starts | session builds, Robin uploads |
| 20 | Upload requirements | Target API level, 16 KB page size, anything new | Read on the day | session reads |
| 21 | Internal testing track, then the closed-test rule (12 testers, 14 days on a new personal account, if Q4 is personal) | Testers list, release notes | After v2.0 | Robin |
| 22 | Release notes (500) | App only, no build story | After v2.0 | session drafts |

## C. Rules that carry over unchanged

- A session never opens the Console, enrols a key or changes a setting; it prepares and guides.
- Nothing from `release/play/` is published before Robin starts Step 9.
- One versionCode sequence for both channels, and a Play-only fix is still a new release.
- Provider names appear only as nominative use; none in the app name, icon or graphic.
- Review credentials are never written into the repo.
