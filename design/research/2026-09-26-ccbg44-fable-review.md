# CCBG-44 (Widget Fill) — Fable design review, 2026-09-26

Fable 5.1 (plan rung 3, purpose (a) expertise), read-only. Input: Robin's four home-screen shots
(`2026-09-26-v18-robin-home/`), ROADMAP v1.8 R1–R10 / S1–S14 / CCRM-78–84, BUGS CCBG-10/11/38/41/43/44,
wireframes rev D, F, G, `WidgetFace.kt`, `WidgetHost.kt`, the widget layouts and infos.
UNVERIFIED by Fable: the host bitmap cap and inner-screen density behind the R10 change; `tnum` on a
RemoteViews Chronometer; the 0.714 draw-ratio arithmetic. Questions Q1–Q5 are Robin's.

## 0 · Read the screenshots in the launcher's units first

Robin's cover is a **six-column** grid (rev F §0: 1 col 84, 2 cols 156, 3 cols 244, 4 cols 333, 6 cols
510; rows 108 / 237 / 366 dp reported), drawn by One UI at 0.714. So the brief's "4×2" is the **6×2
(510×237)**; the Ring's big one is a **4×3 (333×366)**; the Countdown's "2×2 tall" is a 2×2 (156×237)
and its top-left a 3×2 (244×237). Every dp/sp below is *reported*; multiply by 0.71 for what the eye
sees on the cover. The fill rule keys on reported inner size, so a Pixel (ratio 1.0, smaller reported
frames) gets smaller type automatically.

## 1 · Diagnosis

- **Ring** — rev F d.3 caps Ø at 150 and d.4 says "extra height stays empty, centred"; the 1×1 bucket
  caps Ø64 even on a 2×1. Shot 1: the 3×2 and the 4×3 draw the *same* ring; the 4×3 has ~42 visible dp
  of nothing above and below; the 2×2's label ellipsizes because " (unassigned)" costs ~110 dp and the
  stamp has already given way (rev F.1).
- **Number** — `num_reading` (weight 1) centres a rev-D-sized block (label row ~40 + bar 12 + sub 16)
  above a bottom-pinned 28 dp control row, and the 44 sp figure is fixed. Shot 2 bottom (6×2): two ~35
  visible dp bands. 4×1 at 5–6 columns is fine (the bar takes the width). A 2×2 Number is reachable
  (`minResizeWidth` 110) and would centre a 65 dp block in 213.
- **Countdown** — `cd_gap1/2` (weight 1) split the spare into two bands, but the content stays
  2×1-sized (28 sp count = 20 sp visible). Shot 4: every two-row frame shows two ~40 visible dp bands;
  the 6×2 also leaves ~300 dp beside the count.
- **Strip** — Ø ceilings 88/64/56 are rev D's cover numbers and `strip_col` centres; weight-1 cells
  spread three rings to the third-points (CCBG-10's pitch cap was lost). Shot 3: the 4×2 and the 6×2
  draw the same Ø~84 rings with ~25 visible dp above and ~50 below; on the 6×2 the rings sit ~70
  visible dp apart. The real ceiling is **R10's 2 MB**: four rings × two Fold frames.

## 2 · The layout system: fill, don't scale

**Rule.** Each face has one *hero* (ring Ø, figure sp, count sp) and fixed *secondary rows* of known
height. Rows are added in a fixed order when a threshold is met, dropped in reverse at the narrow end.
The hero takes the height (or width) the rows leave, up to a cap. Spare never goes *between* rows; what
remains after the cap splits above and below the block. Left-aligned as rev D; rings centred. Pads
unchanged (8/12/14).

**Tiers** (inside the existing buckets, `bucketFor` unchanged, the `stripRoomy`/`numberStacked`
pattern): **S** H<140 — *narrow* W<240 / *wide* ≥240; **T** H≥140 and W/H<1.35; **W** H≥140 and
W/H≥1.35. **Roomy type** at innerH ≥ 200: label 15, sub 13, caption 12, marks 14, stamp 10 (Q11
fixed), gaps 8; else rev D's 13/12/11/12–14/10, gaps 6.

**Width budgets** (Roboto Bold): a figure "100%" = 2.4 em → sp ≤ width/2.4; a count "−0:00:00" =
4.5 em → sp ≤ innerW/4.5.
**Caps:** Ring Ø160, companion Ø100, Strip Ø120 (memory); figure 72 sp (96 at innerH ≥ 300), count
80 sp (legibility at 0.714 = 57 visible).

## 3 · Per face

**Ring**

| Tier | Spec |
|---|---|
| 1×1 (84×108) | Ø = min(innerW, innerH, 92) = 68 (was 64), figure = 16·Ø/64 = 17 sp. Nothing else. |
| S wide (3×1+, bucket 1X1) | Ring left, Ø = min(innerH, 92) = 84, figure 21 sp; column at Ø+12 when innerW−Ø−12 ≥ 90: mark+name 13, "Resets 9:20 PM" 12 at 78 %, stamp 10. 2×1 (156): column doesn't fit → ring alone, Ø84 centred. |
| T (2×2, 3×2, 4×3, inner 2×2) | Reserve 56 (name 18, reset 16, stamp 14, gaps 8). Ø = min(innerW, innerH−56, 160): 2×2 → Ø132/31 sp; 3×2 → Ø153/36 sp; 4×3 and inner 2×2 → Ø160/38 sp, stroke 13. Lines centred under. 4×3 spare 122 → **companion Weekly ring** Ø100, stroke 6, 22 sp figure with "Weekly" 9 sp in the bore, same accent, under the lines — when the account has both windows and spare ≥ 108; else spare splits (59/59, rare). |
| W (4×2, 5×2, 6×2, inner 4×2) | Ring left Ø = min(innerH, 160); column at Ø+16, vertically centred on the ring: name 15, "Resets 9:20 PM" 13, stamp 10. Companion Ø100 at the right end when innerW−Ø−162 ≥ 100 (6×2 ✓, 4×2 ✗ → column widens). |

Drop order (narrow end): companion → stamp → reset → name.

**Number**

| Tier | Spec |
|---|---|
| S narrow <100 tall | unchanged (rev F.1 compact). |
| S narrow (2×1 156×108) | stacked as rev F; figSp = clamp(min(innerW/2.4, innerH−29), 32, 44) → 44; bar 10. Pill present → 36. |
| S wide (3×1…6×1, inner 4×1) | one row; figSp = clamp(innerH−36, 32, 44) → 32 at rev D's 84, 44 at 108; bar 10 when figSp ≥ 40. Sub row as now. |
| T narrow (2×2 156×237) — new | label line (mark 14 + 15 sp), figure alone left at min(innerW/2.4, 72) = 52, bar 12, "Resets 9:20 PM" 13, stamp 10 on its own line; then (Q3) chips row and cycler row, 28 dp each, bottom-pinned. 205 of 213. |
| T/W large (3×2, 4×2, 6×2, 4×3, inner 4×2) | one row when innerW ≥ 260, else stacked (3×2). figSp = clamp(min((innerW−94)/2.4 row or innerW/2.4 stacked, innerH−73), 44, 72) → 72 on every 237-tall frame; bar 12 (14 at ≥64 sp); control row 32, chips 26; reading block centred above it — bands ≤ 31 (22 visible). 4×3: cap 96. |

**Countdown**

| Tier | Spec |
|---|---|
| S narrow (2×1) | as now; count = min(innerW/4.5, innerH−44) → 30 sp (was 24); "at" under when inline won't fit (rev F). |
| S wide (3×1…6×1) | count = min(44, (innerW−66)/4.5) with "at 9:20 PM" inline → 3×1 34 sp, 4×1+ 44 sp; when innerW−4.5·count−82 ≥ 95 (5×1+) a right column: figure 24 sp + LEFT over an 8 dp bar, bottom-aligned to the count. Label+stamp under. |
| T and W (all two-row and taller) | one left-aligned column: caption; count; "at 9:20 PM" 14 under; figure row (32 sp + LEFT left, "~ runs out" right, own line when it doesn't fit); bar 8 (10 at count ≥ 60); label+stamp bottom. countSp = clamp(min(innerW/4.5, innerH−118), 28, 80): 2×2 → 29 (spare 64 → the two gaps 32 each), 3×2 → 48, 4×2 → 66, 6×2 → 80, inner 2×2 → 41. A two-column W was measured and rejected: it forces the count under 40 sp and reopens the bands. 4×3: 66 sp, 149 spare — not a placement this face is for; say so on the wireframe. |

**Strip** (`minResizeWidth` 250 → never under four columns; bucket by height)

| Tier | Spec |
|---|---|
| S (4×1…6×1, inner 4×1) | rev G, plus CCBG-10's rule back: cell pitch capped at Ø+56, row centred as a group; when the cell is ≥ Ø+70 wide the reset joins the name: "Personal · 9:20 PM" (time 10 sp, 72 %). |
| T/W (4×2, 6×2, 4×3, inner 4×2) | gaps 8; Ø = min((innerW−8(n−1))/n, innerH−62, 120) [62 = name 17 + reset 15 + stamp 14 + gaps]: 4×2 → 96 (three accounts) / 70 (four); 6×2 → 120 / 114. Bore figure round(18·Ø/88): 20 / 25 sp; name 13 and reset 11 at Ø ≥ 100; "Weekly" tag 10 at Ø ≥ 110. Pitch cap Ø+40, group centred; stamp right under the row. Block Ø+62 → 27–51 spare split. 4×3: nothing more to add ("never a sum") — spare splits. |

**Unassigned (Q4):** "(unassigned)" leaves every label; the "as of" slot reads **"tap to choose
account"** (10 sp, 55 %); Number 2×1 keeps rev D's pill; Countdown 2×1 caption "5h reset · tap to
choose"; Ring 1×1 says nothing — the tap opens config (CCBG-43 (Widget Settings Hidden)).

## 4 · Rules that change, and memory

At 2.625 px/dp, worst case per frame, ×2 frames on a Fold:
- Ring: Ø160 (706 KB) + companion Ø100 (276 KB) = 0.98 MB → **1.96 MB**. Passes 2 MB only at ≤ 2.625
  density; otherwise `withinBudget` drops the inner frame.
- Strip: 4 × Ø120 = 1.59 MB → **3.18 MB**. At 2 MB the cap could only reach Ø94 — no visible gain.
- Number/Countdown: bars ≤ 190 KB.

1. **R10 budget 2 → 4 MB.** Reason: the host cap is 6 B × display px (≈16 MB on 1080×2520; 6.9 MB on a
   720×1600 phone); bitmaps over 16 KB cross Binder as ashmem blobs, so the 1 MB transaction limit is
   not the bound; `SafeUpdate`'s fallback stays. "At most three buckets" and "five bitmaps per bucket"
   **unchanged** — tiers live inside buckets; the gallery and `WidgetFitTest` add the tier frames (3×2,
   6×2, 4×3, inner 2×2). `revF_revDFramesKeepRevDGeometry` becomes a rev H invariance test (the 1×1 Ø64
   cap and the Countdown gaps change on rev D frames).
2. **CCRM-79 (Ring Face) "Omits: reset, weekly"** — the Ring gains an absolute reset line from 2×2 up
   (R4-true) and the companion ring on 4×3/6×2. **R7:** `Transitions` counts both windows for a Ring
   whose account has both (the tier is frame-dependent; over-approximating is safe under R4).
3. **CCRM-80 (Number Face)** — controls on the 2×2 Number (Q3); `WidgetHost.wire` wires by tier, not
   bucket.
4. **Rev F d.4** ("extra height stays empty") is reversed — that is this bug. Q11's stamp gains two
   homes (Ring S-wide, Number 2×2).

## 5 · Risks, ranked

1. **Inner-screen density** — if > 2.625, Strip Ø120 × 2 frames exceeds 4 MB; the unit test decides;
   fallback cap 110 (2.67 MB). UNVERIFIED.
2. **Chronometer at 80 sp** — Roboto Bold digits are proportional; the count jitters. Add
   `android:fontFeatureSettings="tnum"` (static attribute, RemoteViews-safe). Whether One UI's host
   honours it: device check.
3. **Companion ring reads as a second account** — mitigated by the "Weekly" bore tag and shared
   accent; check on the phone next to a Strip.
4. **Strip pitch cap** needs `strip_row` wrap_content + centred and `setViewLayoutWidth` per cell — a
   `tools/widget_layouts.py` regeneration (three files each).
5. **Tight Ring budget at 2 MB** if Q5 is declined: cap main Ø150 + companion Ø96 (1.75 MB).
6. Chips at 26–28 dp are under 48 dp touch targets — already accepted at rev D.

## 6 · Questions for Robin

1. **Companion Weekly ring** on the Ring's 4×3 and 6×2 (both-window accounts only)? *Rec: yes* — the
   only fill in the ring's own grammar; the alternative is 42 visible dp of air above and below.
2. **"Resets 9:20 PM" under the Ring's name** from 2×2 up (CCRM-79 said the Ring bears no time)?
   *Rec: yes* — absolute, R4-true, and every other face has it.
3. **Number 2×2**: chips + cycler on two bottom rows, or no controls (36/36 spare)? *Rec: controls* —
   otherwise it is a 4×1 stood on end.
4. **Unassigned wording** as §3 proposes? *Rec: yes* — CCBG-43 makes it a one-tap state; the
   parenthesis was costing ~110 dp of label on every face.
5. **R10 budget 2 → 4 MB** (Strip only)? *Rec: yes*; if no, Strip rings stay ≤ Ø94 but type, pitch
   and the inline reset still improve.
