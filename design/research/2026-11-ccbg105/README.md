# CCBG-105 (Corner Label Collision) — before / after

The reported Summary (Fold 7 inner screen, 2026-11-18, Figures on): ChatGPT 21% at 28.6% of its week,
Work 17% at 39%, Claude 9% at 86%, Product 58% at 98%; Gemini at 0% is off the map.

| Folder | What | Made by |
|---|---|---|
| `before/` | Android at release commit 09d5ba8: Claude's dot on "behi● pace" | `PaceMapCornerWordsTest` run against 09d5ba8 (the test fails there) |
| `after/` | Android with the fix: inner portrait, inner landscape, cover | `PaceMapCornerWordsTest` with `SHOT_DIR` |
| `before-ios/` | iOS at 09d5ba8, phone and iPad square, dark and light: the dot grazes "behind pace" (iOS centres the word on 6%, so a dot at 4–8% covers it) | `PaceMapTests.renders` with `SHOT_DIR` |
| `after-ios/` | iOS with the fix | the same |

Rule: core §6.3 "The wedge words yield to a dot", fixture `printed.json` → `mapLabels.wedgeWords`.
The dashed diagonal crossing "ChatGPT 21%" (visible in `after/ccbg105-cover.png`) is a separate rule and is
not changed here (BUGS.md CCBG-105, "Not fixed here").
