# Rev H device check — Fold 7 over USB, 2026-09-26

Release build of 363c192 (+ version bump, versionCode 24), installed over the live app; phone restored
(Before/After diff clean). Robin's 21 test widgets on cover pages 5–8, one reported frame each.

- **CCBG-44 (Widget Fill):** every face fills its frame on the six-column cover (`01`–`04`): Ring 1×1 /
  2×2 / 3×2 / 4×3 with the Weekly companion; Number 2×1…6×1 and 6×2 with chips; Strip 3×1…6×1 (inline
  reset on 6×1) and 4×2 / 6×2; Countdown 2×1, 3×1, 2×2, 3×2, 4×2, 6×2 (MM:SS under an hour). "tap to
  choose account" in the stamp slot on unassigned faces. **Found and fixed:** the Number 2×1's
  UNASSIGNED pill clipped at the top under One UI's font (the fit test's font is shorter) — the pill
  state's figure is 30 sp (`09`).
- **No SafeUpdate fallback:** the in-app log's last line predates the install (`05`); AppLog does not
  write to logcat.
- **Resize:** Ring 1×1 → 2×1 redrew at once as the ring alone (`06`), then restored. A resize across
  S → T → W was not possible on these pages (neighbours block the handles) — rests on `WidgetFitTest`.
- **Fold / unfold:** not exercised — these placements live on the cover home only and report one
  frame each, so no two-frame map exists to test; the two-frame budget rests on the unit test.
- **CCBG-43 (Widget Settings Hidden):** adding a Ring from the picker opens "Add widget" (`07`); Back
  places nothing. One UI's long-press menu also offers "Settings" for a placed widget.
- **CCBG-42 (Kebab Drift):** every account card's ⋮ at x 948, above ↻ (`08`).
