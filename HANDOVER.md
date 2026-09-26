# Handover — Cooldown (Android)

The prompt for the next session lives here. Each session that ends with a next step replaces
the "Next session" block below (RUNBOOK.md Conventions §5). Statuses stay in ROADMAP.md and
BUGS.md; the ordered plan stays in RUNBOOK.md.

## ▶ Next session — start here: rev H device check, then CCRM-78 (Widgets Reborn) Step 8, release v1.8 (written 2026-09-26)

**v1.8 is held** (Robin, 2026-09-26) for three bugs he found on the phone, all now fixed in code:
CCBG-42 (Kebab Drift, `059608f`), CCBG-43 (Widget Settings Hidden, `f69af70`: config opens on add, a tap
on an unassigned face opens it) and CCBG-44 (Widget Fill): every face fills its frame at every size —
Fable's review, wireframe rev H (`design/2026-09-26-widgets-fill-revh.html`, approved: "go with Fable's
recommendations"), build plan Astra-reviewed, built `7b6909b` and fixed through two judge rounds
(`f6caf52`, `363c192`). None is seen on the phone yet. The tree still carries the uncommitted release prep
(versionCode 24 / "1.8", RELEASING.md's six-line order with the v1.7 signer digest); it goes into Step 8's
step 1. RUNBOOK.md Step 8's Log says exactly what the device check must show.

Paste this as the prompt in a fresh session in `~/Projects/Cooldown`, with the Fold 7 on USB and Robin
there to unlock:

> Read CLAUDE.md and HANDOVER.md, then RUNBOOK.md Conventions, Step 7's Before / Always-after blocks and
> Step 8 in full, BUGS.md CCBG-42 to CCBG-44, and `design/research/2026-09-26-ccbg44-build-plan.md` step 10.
> Run the rev H device check on the Fold 7 over USB: record Before, install a release build of HEAD,
> place each face at the six-column sizes in `design/research/2026-09-26-v18-robin-home/`, confirm the app
> log has no SafeUpdate fallback line, fold / unfold, resize across S → T → W and back, check CCBG-42's ⋮
> and that adding a widget opens its config (CCBG-43). File anything found; set the three bugs Verified.
> Restore the phone (Always after). Then Robin shoots the widget screenshots for the docs, and Step 8
> runs from its Resume block (the version bump and RELEASING.md are already in the tree), a judge
> verdict before step 6 (publish).
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
