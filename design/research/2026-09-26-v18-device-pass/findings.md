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

# Re-run over USB, 2026-09-26 (release build of 20328c3, versionName 1.7), captures 20–52

State: `phone-state-before-usb.txt` / `phone-state-after-usb.txt` — identical but for the clock
reading (same offset, 0 s) and the charger (USB → AC after `battery reset`, a live reading).

## CCBG-38 (Cover Buckets) — verified
- Cover, every face × size: Ring 1×1 (28) / 2×2 (21), Number 2×1 stacked (25) / 4×1 (23) / 4×2 (24),
  Countdown 2×1 (23) / 2×2 (26), Strip 4×1 (23) / 4×2 (27). Each draws its own layout.
- Inner defaults (38): Ring 2×2 211×291, Countdown 2×1 211×126, Strip and Number 4×1 467×126;
  resized Ring 1×1, Countdown 2×2 (39), Number 3×1 (40) and 2×1. Inner 4×2 not placed: no free
  4-column two-row span without moving Robin's widgets.
- Picker: cover previews fit (20, 22); inner Countdown preview ellipsizes "at…" (36) → CCBG-40.

## States
- Synthetic Above pace / At 100% / No data on every cover face (30–32), inner (41); ribbon ≥160 dp,
  dot below. Banner tap → Off, faces real again (34, 35). Gallery: 126 tiles, no clipping (33).
- Reboot: faces survive with real data, synthetic Off, alarm re-armed (20:20, the real reset).
- `am kill` refused: PinnedService is a foreground service. Not force-stopped.
- Launcher restart: our faces survive; Samsung Health's Energy widget showed "Couldn't add
  widget" until the reboot.

## R7 scheduling (airplane on, battery unplugged, deviceidle force-idle, manual clock)
- Clock jump to 21:05: Countdown redrew to 15:10 (MM:SS) ~15 s later, cold process (42).
- Across the 21:20:42 reset: 00:40 → 00:04 → −00:12 → −00:29 (43); the alarm fired 21:21:35.8 in
  deep idle, 53.8 s late, inside its window; face drew S6 "Reset 9:20 PM" (44).
- Alarm window: `setAndAllowWhileIdle(RTC)` → window 0.75× the lead, capped at 1 h (seen +1h
  when armed 2h40m out, +54 s at 1 min out). Cooldown is on the battery allowlist (bucket 5
  EXEMPTED), so this is the allowlisted case.
- Timezone Europe/London: "Reset 4:50 PM" (45). Back to IST.
- 00:04:30 → stale alarm (fetchedAt + 6 h = 00:05:16) fired 00:05:35.7, 18.8 s late; every face
  dimmed, Countdown reads "Stale" (46).

## Other surfaces
- Status-bar ring vs v1.7 (52): same geometry and colours.
- Inner shade collapsed Duet: "P… 12%" / "W… 23%", Left "P… 88%" (47, 48) → CCBG-39. CCBG-24's
  own symptom (Left worse than Used) gone.
- Share card preview (49) and system chooser; not sent. Clear History dialog to Cancel (50).
- Fold: cover faces fresh after folding (51).
- Side effects on Robin's phone: Samsung Health Medications "Time zone changed" notice; Wispr
  Flow's accessibility service paused by the reboot.
