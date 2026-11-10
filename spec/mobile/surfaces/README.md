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
- **v2.0 feedback (Fold 7, 2026-11-10), approved rev B** — `design/2026-11-10-v20-feedback-wireframes.html`,
  core rules in [BEHAVIOR-SPEC §6.3 "Planned — v2.0 feedback"](../../core/BEHAVIOR-SPEC.md). Each build folds
  its delta into these files in the same commit:
  - CCRM-105 (Duet Stack): `notification.md` §2.1 (width budget: 136 dp of chrome, no halves), §2.2 (anatomy:
    two 36 dp rings left, two stacked rows, 42 dp figure slot), §2.3 (expanded figure 24 sp, column ≈186 dp),
    §2.4 (per-row state words; drop order bar → name to its 24 dp floor → the state word's tail), §8 (the bare
    14 dp ring retires); `foundations.md` §2 (collapsed Duet text is capped in dp at large font scales — Robin's
    exception).
  - CCRM-106 (7d Chip): `app.md` tile chips and the classic 7-day card's label "All" → "7d"; "Weekly · All
    models" unchanged.
  - CCRM-107 (Day Grid): `app.md` trend chart (grid lines, axis ordinals, Small and share-card charts),
    `foundations.md` chart tokens (grid ink), the Pace map's axis and foot line.
  - CCRM-108 (Reset Named): `foundations.md` "The pace line", `notification.md` §1.2/§1.4/§2.3 fitting numbers,
    `app.md` tile lines, Summary line 3, "The pace line on iOS", `widgets.md` Weekly lines.
  - CCRM-109 (Quiet Accounts): `app.md` Pace map — accounts whose Weekly figure prints "0%" leave the map, foot-line count
    ("2 at 0%", joined with "· 1 no reading"), the all-at-0% message.
  - CCRM-110 (Account Tags): `app.md` Settings › Accounts (Tag… with person/briefcase glyph + word, neutral),
    the account menu, iOS account page; Summary header (All · Personal · Work filter, remembered; the sort menu).
  - CCRM-111 (Map Layers): `app.md` Pace map (Figures · Trails · Projection chips on the title line, the callout,
    heavier last 24 h, dotted leaders).
  - CCRM-112 (Summary Snapshot): `app.md` share card (the Summary version: rows, then the map).
  - CCRM-113 (Sample Port), §9: `app.md` notice slot (the sample banner on every tab, Settings and History),
    status line words, first-screen entry, Add account row, Settings › Accounts / Alerts / Appearance / More in the
    sample, the share band's "● SAMPLE DATA"; "Reviewer sample data (iOS…)" becomes both platforms.
    `notification.md` and `widgets.md`: one line each — the sample never reaches them. Rule:
    [BEHAVIOR-MOBILE §7](../BEHAVIOR-MOBILE.md).
