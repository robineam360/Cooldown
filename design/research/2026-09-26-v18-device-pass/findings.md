# v1.8 device pass — Fold 7 (SM-F966B), 2026-09-26, wireless adb, release-signed build of ec63da0 (versionName 1.7)

## Picker (cover, One UI launcher)
- previewLayout honoured: all four faces show their live-style previews with sizes (Countdown 2x1, Ring 2x2, All accounts 4x1, Number).
- F1: Ring 2x2 preview is drawn in a portrait box and the ring bitmap is clipped left and right (02-cover-picker-previews.png).
- F2: All accounts 4x1 preview clips the account labels under the rings (bottom cut).

## Cell sizes (dumpsys appwidget, One UI launcher, Robin's grid)
Reported frame (dp, what RemoteViews picks against); One UI then scales the host by hsResizeRatio.
- Cover (hsResizeRatio 0.714, portrait): 1×1 84.2×107.8 · 2×2 155.8×237.0 · 3×3 244.2×365.7 · 4×2 333.0×237.0 · 4×3 333.0×365.7 · 6×1 509.7×107.8 (fine grid, 6 cols)
- Inner (hsResizeRatio 0.625): 2×2 202.3×264.4 · 4×3 470.5×414.5 · 5×2 604.6×264.4 · 5×3 604.6×414.5
- Code assumes cover cells 90.75×84 (Bucket enum): 2×2 key 169×156, 2×1 key 169×72, 4×1 key 351×72.
- F3 (High): on the cover every 2-col and 4-col frame is narrower than its key, so findBestFitLayout falls back to the smallest bucket. Ring placed at default 2×2 (155.8×237) drew the RING_1X1 face ("—" for S5, no label) — logcat "findBestFitLayout, widgetSize=155.80952x236.95238, bestFitSize=79.0x72.0". 04-cover-ring-add.png

## Notification (cover, Duet, synthetic Above pace)
- F4: collapsed Duet shows no synthetic dot (07-cover-duet-collapsed-synthetic.png). uiautomator lists no duet_synth_dot node. The First half's label row is only as wide as the bar (~64 dp: 219–386 px); "Personal" ellipsizes to "Perso…" and fills it, so the 6 dp dots after it (condition dot and synthetic dot alike) get zero width. The clamp was fitted to a 156 dp half; One UI's cover half is ~128 dp with the 30 sp figure taking 56 dp. Affects the red fault dot too, not only synthetic.
- Expanded Duet with synthetic: band shows, no condition strip — as rev E (08-cover-duet-expanded-synthetic.png). Weekly panel line "Resets in 5d 11h · Fri 3:2…" ellipsizes (pre-existing, cosmetic).
- CCBG-37 fix (label weight, wrap_content row) installed and seen: dot shows beside "Pers…", TalkBack node "Synthetic data" present (10-…-fixed.png); synthetic Off unchanged (13-…-real-fixed.png).
- Single-row collapsed (Second = None): the third line "Resets in … · …" does NOT clip on One UI, synthetic on (dot shows, 11-…) or off (12-…). The Pixel-skin clip does not reproduce here.
- configuration_optional: One UI skipped the config screen on add (like the Pixel launcher); the widget came up unassigned (first account). Picker honours previewLayout.

## Not runnable over wireless adb (need USB)
- airplane mode past STALE_DATA_MS, wifi/data toggles, reboot + alarm re-arm: each drops the wireless link.
