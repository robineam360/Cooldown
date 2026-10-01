# v1.9 Step 7 — Fold 7 device pass, 2026-10-01

Phone: SM-F966B, One UI / Android 16 (SDK 36), cover screen (folded) unless noted. Started over
wireless adb, switched to USB (RZCY70YN0LJ) at 12:41Z. Seat rung 1; one fresh Opus judge
(purpose c) before item 1's install: **concerns** (no app-state baseline; pin the device id) —
both adopted (app-state-before.txt, every adb call names the serial); its note on the store's
first open was checked on the phone ("Open · healthy").

| # | Check | Result | Evidence |
|---|---|---|---|
| Before | phone-state-before.txt; v1.8 APK pulled, sha256 2eea1a4c…8819 = expected | done | phone-state-before.txt, app-state-before.txt |
| 1 | github release of 0e8ac99 (1.9/25, sha256 73a1c1f9…846e) `install -r` over v1.8 | **pass** — 3 accounts signed in (Personal ChatGPT Plus, Work and Product Claude Team Premium), history from 29 Sept, 7 widgets, pin id 9100 kept; on-device sha256 = APK | 00-before-*.png, 01-after-install-main.png |
| 2 | Browser picker after CCRM-88 (Scoped Queries) | **pass** — "Open with" lists Brave, Edge, Samsung Browser: every *enabled* browser (Chrome is disabled on this phone, `pm list packages -d`). No under-report; QUERY_ALL_PACKAGES not needed | 02-browser-picker.png |
| 3 | github Updates card, Check for updates | **pass** — card present; "You're running the latest version (v1.9)"; "Last checked just now — up to date (v1.8)" | 03-github-updates-card.png |
| 4 | Crash: Crash now → relaunch → card → Share → text → back keeps → pick removes → Not now | **pass** — exit-info reason 4 APP CRASH (EXCEPTION), so the default handler ran; One UI shows no "has stopped" dialog for a first crash. Card "Cooldown crashed today at 6:17 PM". Shared text has no token, email or message (07-shared-text.txt). Backing out kept the card; picking Quick Share (then backing out of it, nothing sent) removed it; a second crash's Not now kept it gone across the next process start | 06-crash-card.png, 07-*.png/txt, 08-after-pick-card-gone.png |
| 4b | CCBG-46: sign-in survives force-stop + relaunch | **pass** | — |
| 4b | CCBG-50: Simulate degraded store | notice on main (the whole page, per rev B §2) and on Accounts (all "Not signed in"); Off → "Open · healthy", accounts back | 09–11 |
| — | Debug Endpoint probe absent from the release build | **pass** — the Debug section ends at Crash test | 04-debug-bottom.png |
| 5 | Pin survives MY_PACKAGE_REPLACED | **pass** (id 9100 present straight after each install, before launch) | — |
| 7 | play flavor via bundletool (every split's signer = trusted digest), `install-apks` over github at equal versionCode | **pass** — installed; no Updates card, no strip, no Check for updates; picker works | 13–15 |
| 8 | back to github APK, `install -r` | **pass** — on-device sha256 73a1c1f9…846e | apks.txt |
| 9 | CCBG-45 one-account expanded notification | **pass** — "Work · 5h" collapsed, "Weekly" expanded (Second set to None for the shot, restored to Personal) | 12-ccbg45-single-expanded.png |
| 9 | CCBG-35 Settings tabs on the cover | **pass** — Accounts/Alerts/Appearance/More fit without scrolling | 04-debug-bottom.png |
| 9 | Privacy row | **pass** — opens the policy (the GitHub app claims github.com links on this phone) | 02-privacy-browser-picker.png |
| — | CCBG-48 reconfigure label | **pass** for a placed widget (id 149) opened for reconfigure: "Save changes"; cancelled, widgets intact | 16-ccbg48-reconfigure.png |
| 9 | CCBG-35 and the privacy row on the **inner** screen | **pass** — tabs fill the width; Privacy policy row under Share feedback | 17-inner-settings-more.png |
| 5 | Reboot | alarm re-armed at boot (WidgetActionReceiver, RTC); **pin back only after 5 min 45 s** — PinBootWorker held by One UI's JobScheduler, then the FGS started fine → filed **CCBG-52 (Boot Pin Delay), Low** | logcat excerpt in CCBG-52 |
| After | Always-after: installed sha256 73a1c1f9…846e (github), deviceidle unforce, battery reset, screen_off_timeout 60000; diff against Before clean, clock offset 0 s | done | phone-state-after.txt |

**Not done this session (Robin, 2026-10-01: "We'll do others in next session"):** 4b's CCBG-50
restart cards after a sign-in / sign-out held while degraded (needs Robin at the browser), and item 6,
the FGS demo video (needs a shade cleared of Robin's own notifications).

Screens left out of git on purpose: the crash "system dialog" capture (it showed the GitHub app,
since One UI shows no dialog) and the share-sheet capture (direct-share targets); the shared text
is in 07-shared-text.txt.
