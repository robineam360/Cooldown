# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: v1.9 Step 4, Crash Capture and Keystore Wedge (written 2026-09-30)

**Step 3 is done.** The app now builds as two flavors from one commit: `github` (the Releases APK,
update check unchanged) and `play` (no update path at all; it clears update state inherited from a
GitHub install). That is CCRM-87 (Update Channel). CCRM-88 (Scoped Queries) replaced
`QUERY_ALL_PACKAGES` with a scoped `<queries>` that also names the three provider apps, so the
notification's "open the provider's app" tap keeps working. CCRM-91 (CI Gate) is green on `main` at
02df435. The task names are now `testGithubDebugUnitTest testPlayDebugUnitTest`,
`assembleGithubRelease` (published as `app-release.apk`) and `bundlePlayRelease`. The build needs
JDK 21. Two spec gaps were ruled on and logged in RUNBOOK Step 3: "no `.so`" became an allowlist
plus a 16 KB check, and the provider-app `<package>` lines. A fresh Opus judge and Sol reviewed;
every concern is adopted or answered. **Q4, the Play account type, is still open** and gates only
Step 9.

**Robin, optional before Step 4:** two Dependabot PRs (work-runtime 2.12.0, Gradle wrapper 9.8.0)
went red on the old JDK 17 workflow. Comment `@dependabot rebase` on each to re-run them on the
fixed one. Nothing else is needed.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md; RUNBOOK.md Conventions and Step 4, and run Step 4's Resume block.

---

### What changed on 2026-09-25 (Step 4, the short version)

**The four widgets are built** — CCRM-78 (Widgets Reborn) to CCRM-82 (Accounts Strip) read Built:
Ring, Number, Countdown and "All accounts" providers (R9 names), the config screen, the on-face
chips and account cycler, the one transition alarm, and the system receiver. All four were
placed on an API 36 emulator and every R7 trigger redrew them; that pass found and fixed
cover-cell overflows and broken chip backgrounds, now guarded by `WidgetFitTest`. The Countdown
ships a live H:MM:SS count (H:MM is not reachable through RemoteViews). Calls made: the widget
bars take rev D's 3 dp tick and the Number figure keeps its severity colour (Robin); "No reading
yet", R7's fourth kind (the stamp's weekday) and the Strip 4×1 "Free" placement (Fable).

### Open for Robin or the Step 7 device pass

- A system light/dark switch has no R7 trigger: widgets keep the old theme until the next redraw.
- A widget the launcher placed without config, reconfigured later, shows "Add widget" rather
  than "Save changes".
- A long unassigned label ("Personal (unassigned)") pushes the Ring 2×2's "as of" stamp off
  its row.
- One UI's handling of `configuration_optional` and `previewLayout` (the Pixel launcher skipped
  config on add and showed the previews).
