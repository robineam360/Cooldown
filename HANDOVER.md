# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: v1.9 Step 6 close-out, then Step 7 (written 2026-10-01)

**Step 6 is drafted, but it stays open until Robin approves the feature graphic.** `docs/privacy.md`
covers both flavors, every host and store, and CCBG-50 (Degraded Store Notice)'s
`store_held_changes` record. `release/play/` holds the Data safety answers, the listing copy, the FGS
declaration and the App access note, and RELEASING.md §5 is the Play channel. A fresh Opus judge
first ruled the policy blocking, because the hidden Debug endpoint probe was undisclosed. All 13
findings are fixed and the delta was accepted. CCBG-51 (Held Change Residue) was filed from that
review. The privacy link in the app resolves once this commit is on main.

**Robin, before the next session:**
1. Open `design/2026-10-01-play-feature-graphic.html` and say **"approved A"** or **"approved B"**,
   or ask for a change.
2. Decide whether the Debug section's Endpoint probe stays in release and Play builds. It is
   disclosed in the policy now; removing it from release builds would be a small code change.
3. Decide whether CCBG-51 (Held Change Residue, Low) rides in v1.9.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`:

> Read CLAUDE.md; RUNBOOK.md Conventions and Step 6. Robin's graphic answer: <A|B|changes>.
> Export the approved variant to release/play/feature-graphic.png (1024×500, no alpha; the command
> is on the design page), tick Step 6 per Convention 4 (Progress ☑, CCRM-89 (Privacy Policy) and
> CCRM-90 (Listing Pack) Status lines), then run Step 7's Resume block. Robin's calls on the Debug
> probe and CCBG-51 (Held Change Residue): <answers>.

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
