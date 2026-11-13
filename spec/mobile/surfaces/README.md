# spec/mobile/surfaces/ — what every mobile surface looks like today

The as-shipped design of the Android and iPhone/iPad apps, written down once so a new wireframe
starts here instead of in the Kotlin, the Swift and forty old wireframe files. The rules behind
the numbers are in [../../core/BEHAVIOR-SPEC.md](../../core/BEHAVIOR-SPEC.md) (maths, thresholds,
colours that must agree everywhere) and [../BEHAVIOR-MOBILE.md](../BEHAVIOR-MOBILE.md) (mobile
product rules); these files are the layouts, sizes, states and words.

| File | Covers |
|---|---|
| [foundations.md](foundations.md) | Colours, type, bar/tick/ring geometry, provider marks, window and reset wording, number formats — the tokens every surface reuses |
| [app.md](app.md) | The app: account tabs, cards (Comfortable/Compact), the trend chart and its lines, layout sheet, notices, Settings, History, sign-in, share card |
| [notification.md](notification.md) | Android's always-on notification (every style, the Duet, the expanded panel, condition strips), the status-bar gauge, the QS tile, reset pings |
| [widgets.md](widgets.md) | Android home-screen widgets; iOS home and lock-screen widgets, the Control Center control, the Live Activity |

## How to use it

1. **Designing a change:** read the surface's file and foundations.md, then wireframe only the
   delta (CLAUDE.md §2). Every wireframe still shows the states — empty, no reading, stale, at
   100%, above pace, not started, and the narrowest width — and the states table in the surface's
   file is the checklist.
2. **Building it:** the approved wireframe wins over this folder until the build lands; then
   update the file **in the same commit** as the code, so this folder never describes an older
   app than the one that ships.
3. **Where code and an old wireframe disagree,** the file documents the code and says so in a
   "Differs from …" note. Those notes are candidates for BUGS.md, not silent truths.

Not here: desktop surfaces (the desktop repo's own layer) and anything a provider can break (core).

## Planned changes not yet in these files

- CCRM-96 (Weekly First) and CCRM-97 (Pace Number): weekly leads every surface, the 5h companion,
  the per-account Headline, and the pace words — [BEHAVIOR-MOBILE §5](../BEHAVIOR-MOBILE.md) and
  `design/2026-11-02-weekly-first-pace.html`. Fold them into these files when they are built.
