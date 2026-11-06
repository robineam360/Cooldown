# iPad Pro pass — PR #23 build (CCRM-94 (iOS App) parity + RUNBOOK-v2.0 Step 7 leftovers)

Prepared 2026-11-06 so the pass runs the moment the iPad is connected. The build is the PR #23 branch (`claude/cranky-newton-9a444c`),
**not landed on `main`** until this pass is done. A session cannot tap the iPad: Robin drives it, the session captures and files.
Home pages, the shade and Settings captures show private data — keep them out of the repo; commit only Cooldown crops.

## 0 · Before (record the starting state in the RUNBOOK-v2.0 Step 7 log)
- Installed build on the iPad (version, build, Debug or TestFlight), screen auto-lock setting, a screenshot of each Home page and
  the lock screen (widgets, Live Activity), the Cooldown settings that will change (Headline per account, Usage display, Usage end
  mark, Summary order, Theme), and which widgets are placed.
- Keep the iPad awake and unlocked for the whole pass (restore auto-lock *before* disconnecting).

## 1 · Build and install (session)
From a clean checkout of the branch tip (a clean copy, not a worktree with local edits):
```bash
swift test --package-path ios/Packages/CooldownCore
(cd ios && xcodegen generate)
test -s ios/Config/Antigravity.secrets.xcconfig && echo secret-present        # never print it
xcodebuild build -project ios/Cooldown.xcodeproj -scheme Cooldown -configuration Debug \
  -destination 'generic/platform=iOS' -derivedDataPath ios/.derived -allowProvisioningUpdates DEVELOPMENT_TEAM=<team id>
xcrun devicectl list devices                                                    # the iPad's identifier
xcrun devicectl device install app --device <id> ios/.derived/Build/Products/Debug-iphoneos/Cooldown.app
xcrun devicectl device capture screenshot --device <id> <file>.png               # one capture per state
```
The team id is passed on the command line, never committed (HANDOVER.md has it). **Robin:** unlock the iPad, tap *Trust* if asked,
Developer Mode on, approve any signing or keychain prompt. First launch of a new build may ask for notification permission: allow.

## 2 · What Robin taps, in order (the session captures after each)
**A. Accounts (real accounts; the session never sees tokens)**
1. Settings → Add account → Claude (browser, paste the code), ChatGPT (device code), Gemini (Google page, paste the code). Skip any already signed in.
2. Settings → tap each account → its page: **Name** (rename sheet: counter n/16, "Use <name>" row, clear → default), **Check now** (↻),
   **Sign in again** (must replace the account, not add one), accent colour, Headline.
3. Remove an account only on a throwaway (add Claude 2 first), to see the alert and that the last-account line shows only for the last one.

**B. Main screen and notices (real accounts)**
4. Each account tab: Weekly bar + 5h ring, tile, chart, credits card (Claude with credits: bar, ⓘ pill; ChatGPT balance if shown).
5. Notices: airplane mode → pull to refresh → the "Couldn't reach <Vendor>" notice with its status button (button opens the status page);
   back online. A real Gemini Pro account used once, then a refresh after an hour.
6. Chart wash: an account above pace (or via the sample, below) — the red wash and orange overshoot; "Time down" in Settings flips it.

**C. History**
7. Calendar icon → 5-hour / 7-day. **iPad landscape and portrait (≥ 600 pt): two columns side by side, no picker**, Plan Fit on the 7-day
   side with its header "PLAN FIT · <PLAN> · LAST 8 WEEKS"; Split View narrower than 600 pt → one column with the picker and the short header.

**D. Share card** — ellipsis → Share card: Weekly leads (Headline Weekly), 5h leads (Headline 5-hour), a spent week if reachable.

**E. Sample data (the reviewer path)**
8. Settings → remove nothing. Quit to the first screen only on a clean install, or use Add account → **Try with sample data**: banner, Exit,
   Settings show "Sample", no Add account / Reset alerts / Lock Screen, share card band; change a Display setting, Exit, confirm the real setting is unchanged.

**F. About** — Settings → About: Version (build), Made by, **Share feedback** (Mail opens pre-addressed to robin@eam360.com; with no Mail account, the alert), **Privacy policy** (opens the GitHub page; shows the Android-only text until the privacy commit lands on `main`).

**G. Widgets, lock screen, Live Activity (Robin places; iOS decides refresh timing)**
9. Home Screen: Number, Bars (small), Strip, Overview; lock-screen widgets; Control Center control. The 4×2 picker previews.
10. Live Activity: Settings → Lock Screen on → lock the iPad → check the figure. **CCBG-72 (Live Activity Stale Mode) recheck:** change Usage display (Used ↔ Vs pace) and Usage end mark, lock again — the Live Activity must follow at once.
11. Reset alerts: set Weekly or 5h to **Always** on a window due to reset soon and wait for the alert (the open "reset alert on Always" item).
12. Second lock-screen widget: add a second Cooldown widget on the lock screen and confirm the picker offers its own account choice.

## 3 · RUNBOOK-v2.0 Step 7 leftovers (from its log "Unverified" and HANDOVER.md "Open, in order")
- iPad recheck of `bbc19db`: portrait Liquid Glass tab bar, title marks, Summary order setting, plan names on the iPad.
- A running / binding / full 5h and Headline 5-hour / Auto on it; a spent week (5h greyed with its note); Gemini's pool shown once used and a refresh after an hour; the 4×2 picker previews.
- Real Gemini Pro account signed in by Robin: not started, used, pool hidden/shown, spent week.

## 4 · CCBG-70 (Stale Ring Widget) — Robin's own action, not code
Find the small "9%, as of …" ring on the Home Screen. Confirm it offers **no** "Edit Widget" (that confirms the orphaned kind), remove it, and add
**Bars (small)** in its place. Capture before and after.

## 5 · After (always)
Put every recorded setting back, widgets match the starting screenshots, auto-lock restored. The PR #23 build stays installed on purpose. File every
defect in BUGS.md (Robin decides what blocks), tick Step 7 in RUNBOOK-v2.0.md, and report to the parent session.

## Results (fill during the pass)
| # | Item | Result | Capture |
|---|---|---|---|
| 1-3 | Accounts, account page, rename, sign in again, remove | | |
| 4-6 | Main screen, notices, chart wash | | |
| 7 | History two columns, Plan Fit | | |
| D | Share card | | |
| 8 | Sample data | | |
| F | About, feedback mail, privacy link | | |
| 9-12 | Widgets, Live Activity (CCBG-72), reset alert on Always, second lock-screen widget | | |
| §3 | Step 7 leftovers | | |
| §4 | CCBG-70 | | |
