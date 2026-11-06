# v2.0 Android device pass — Galaxy Z Fold 7, 2026-11-06

- **Build:** `assembleGithubRelease` of `d81fbe9` (origin/main), signed with the release key, which matches the
  installed v1.9. sha256 `ab44d3ca86b30672dd81f68c35cd2915ad36fab352e99c963257c7a9f7a30b4f`. This is a test build,
  not a release candidate: it still reads versionName 1.9, versionCode 25.
- **Install:** `adb install -r` over the published v1.9 at 19:45 IST. No uninstall, and every account stayed
  signed in (Claude, ChatGPT, Gemini, Work, Product).
- **Phone:** SM-F966B, USB adb, One UI 8 (420 dpi cover, font scale 1.0). Screen timeout was 300000 at connect,
  held at 1800000 during the pass and restored to 300000 at the end.
- **Settings touched and restored:** Usage display was Used, set to Vs pace, back to Used. Summary order was
  Your order, cycled through all four, back to Your order. The debug synthetic series was turned on, then off.
  Debug tools re-lock on the next launch.
- **Not tested, as agreed:** the Step 7 leftovers, the reset alert on Always, and the second lock-screen widget.

Captures are in this folder. Shade captures are cropped to the Cooldown card.

## Results

| # | Check | Cover | Inner | Result |
|---|---|---|---|---|
| 1a | CCRM-104 (Pace Line Parity) short pace line: "1h 52m behind · Tue 9:15 am", "On the mark · Fri 12:29 am", and with synthetic data "15h 7m ahead · Thu 7:06 am"; the word is tinted green when behind, red when ahead, and plain on the mark | ✓ | ✓ | **Pass** (01, 05, 14) |
| 1b | Vs pace signed: "+1%", "+3%", "+10%" in green under the mark; "−8%" in red over it (U+2212, read from the uiautomator dump, with synthetic data); "0%" plain; figure inside the 5h ring unsigned and coloured only ("45%" green, "16%" red) | ✓ | ✓ | **Pass** (04, 05, 17) |
| 1c | Pace map wedge words "ahead of pace" / "behind pace", "even pace" on the diagonal, names on every dot | ✓ | ✓ | **Pass, with CCBG-79** (02, 14, 18) |
| 1d | Pressing a Summary figure underlines the pace-mark caption for 3 s, then it reverts | ✓ | – | **Pass** (03) |
| 1e | Pace-mark caption: "49% pace mark · 1 under" / "· on it"; in Vs pace "48% used · 49% pace mark" | ✓ | ✓ | **Pass** |
| 1f | Widgets use the same forms: Strip "17h 24m behind · Wed 6:30 PM", Ring "5h 45m behind", Weekly "+3%" signed, 5h ring "66%" unsigned | ✓ | n/a | **Pass** (12, 13) |
| 2 | Weekly-first share card: Weekly row leads at the large figure, 5h row second, header ring stays the 5h gauge, "Weekly trend", "On the mark" in plain ink; Gemini (not started): "—" and "Starts when a message is sent" | ✓ | – | **Pass** (07, 08) |
| 3 | Gemini card refresh (CCBG-75 (Antigravity Crash on Refresh) regression): the first tap answered "Skipped — wait 19s (rate-limit floor)" and the second answered "Checked just now"; same pid 14735, crash buffer empty | ✓ | – | **Pass** (06) |
| 4a | Summary page: Weekly First rows, Pace map, status line; inner shows two columns with the map on the right | ✓ | ✓ | **Pass** (01, 14) |
| 4b | Summary order: Your order (tab order); Most room first (Product +10, Work +3, ChatGPT +1, Claude 0, Gemini unclocked last); Resets soonest (Sun, Tue, Wed, Fri, Gemini last); Most used (67, 48, 19, 12, 0); the header names the choice | ✓ | – | **Pass** |
| 4c | Weekly First cards on the account tabs (Claude, ChatGPT, Gemini with its Argon row) | ✓ | ✓ | **Pass** (05) |
| 4d | Notification, collapsed: Work · Weekly, 5h ring, bar, pace line, figure | ✗ | ✓ | **Fail on the cover: CCBG-78** (09, 10, 15) |
| 4e | Notification, expanded: pace line without the weekday, permanent pace-mark caption, model row (Fable) signed in Vs pace, Refresh | ✓ | ✓ | **Pass** (09, 15) |
| 4f | Status-bar ring (Work 5h, about 27% used, needle about 93% elapsed) | ✓ | – | **Pass** (11) |
| 4g | Home-screen widgets on the cover (Strip, Overview, 2 Ring, Countdown, 2 Number) | ✓ | n/a | **Pass** (12, 13). Robin has no Cooldown widget on the inner screen. |
| 5 | Dependabot batch sanity (compileSdk 37): launch, refresh (all accounts and per account), notification updates as time passes (5h 45m → 5h 57m behind), background poll worker re-enqueued and ran with the app in the background | ✓ | ✓ | **Pass**, see the worker note below |
| 6 | Crash card (CCRM-85 (Crash Capture)) | – | ✓ | **Pass**: "8 on this phone · newest yesterday at 10:18 AM", all from before this install (the CCBG-75 crashes of 2026-11-05). No crash card at launch; `exit-info` after the install shows only PACKAGE UPDATED; process 14735 lived through the whole pass (16) |

## Defects filed

- **CCBG-78 (Collapsed Pace Line Clip), Medium.** On the cover the collapsed notification cuts the pace line to
  "… · Sun 9:30 …". `TextFit` assumes a 184 dp column, but the cover gives about 147 dp, so the weekday is not
  dropped and the line is ellipsized instead. Fix is a constant; it needs Robin's go.
- **CCBG-79 (Leader Reads as Trail), Low.** An unclocked dot at the origin always gets a leader across the whole
  plot, which reads as a trail. With coincident dots, leaders run through other labels. Fixing it needs a
  wireframe.

## Notes, not filed

- **Claude's reset clock moved one minute:** "Fri 12:29 am" at 19:46, then "Fri 12:30 am" at 20:01. This is the
  server's `resets_at` jitter crossing a minute boundary. It was there before v2.0.
- **Background worker:** the `UsagePollWorker` jobs were re-enqueued at 20:00 (job ids 67808 → 67817…). The run
  with the app in the background is recorded in the worker line below.
- **Poll timeouts in the app log:** "auto → Error: timeout" for p11, work and chatgpt:p5 between 18:00 and 19:07,
  under v1.9 before this install. None were logged after the install.
- **AM/PM case:** widgets and the share card always print "PM"; the app follows the system locale ("pm"). Both
  are as specified (foundations.md, "Widget clock").
