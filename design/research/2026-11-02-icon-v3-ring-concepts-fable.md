# CCRM-101 (Icon v3) — ten ring concepts (Fable designer, 2026-11-02)

## Conventions (all ten)
Canvas 108 units, visible safe circle r 36 centred (54,54). Angles in degrees clockwise from 12 o'clock; point = (54 + r·sin θ, 54 − r·cos θ). SVG arc `A r r 0 L 1 x y` (L=1 when > 180°).
Plate `#1F1B19`; cream `#F2E8DF`; Claude `#D97757`; ChatGPT `#19C39A`; Gemini `#8AB4F8`; red `#FF5252`.
**Base G** (the status-bar NEEDLE glyph at 3×): band-centre r 27.6, band stroke 12; extent hairline (track) stroke 4.5 cream α0.50; hub circle r 4.0 cream α0.85; needle stroke 5.4 round caps from r 4.0 to r 33.3, cream α0.85; **halo = a real gap** 13.4 wide around the needle, i.e. ±13.9° cut out of any band/track arc it crosses (draw arcs in two pieces).
Mono (Android themed): two alpha grades only — tracks/slots α0.25–0.40, everything else solid ink; red merges into fill.
iOS: the central 72×72 (viewport 18..90) at 1024, plate full-bleed. 1 unit = 0.667 px at 48 px.
No provider-mark echoes (CCRM-60 decided colour alone identifies).

## 1 Relay
One ring; the week's usage is a single clockwise run handed Claude → ChatGPT → Gemini, ending at the pace needle (on pace).
Track: circle r 27.6 stroke 4.5 cream α0.50, gap 236.1→263.9. Fill stroke 12 at r 27.6, butt caps: Claude 0→120 (54,26.4)→(77.90,67.80); ChatGPT 120→195 →(46.86,80.66); Gemini 195→236.1 →(27.65,69.33). Hub r 4.0 α0.85. Needle at 250°: (50.24,55.37)→(22.71,65.39) stroke 5.4 round α0.85.
Z: plate · track · Claude · ChatGPT · Gemini · hub · needle. Mono: track α0.40; one white arc 0→236.1 stroke 12; hub+needle solid.
48 px: the status glyph's silhouette. Risk: three-colour ring ≈ Chrome (keep the open quarter); variant R+ adds red 263.9→285.

## 2 Thirds
One ring cut into three 112° gauges (one per service), each with a pace tick at 62% into its sector: Claude past its tick (red), ChatGPT just short, Gemini well short.
Band-centre r 26.5, fill stroke 10 butt, track stroke 4.5 α0.50, 8° gaps. Sectors A 4→116, B 124→236, C 244→356. Ticks at 73.4°, 193.4°, 313.4°: stroke 3.6 round, r 19→34, cream α0.90; tracks split ±7.13° around each tick: A 4→66.3 & 80.5→116; B 124→186.3 & 200.5→236; C 244→306.3 & 320.5→356.
Fills: Claude 4→66.3 plus red 80.5→98; ChatGPT 124→189 round cap; Gemini 244→274 round cap. No hub.
Mono: tracks α0.40, fills/ticks solid. Risk: faint trefoil/Mercedes; spinner; ticks vanish first at 48 px.

## 3 Trio
Three small full gauges in a triangle (the Mini-Rings face), one over, one near, one far under. Rings don't touch.
Centres 18 from (54,54) at 0°,120°,240°: Claude (54,36), ChatGPT (69.59,63), Gemini (38.41,63). Each: band-centre r 11, fill stroke 5.5, track circle stroke 2.25 cream α0.50.
Claude 0→240 butt + red 240→300 butt; ChatGPT 0→215 round caps; Gemini 0→95 round caps. No ticks/needles.
Mono: tracks α0.40, fills solid. Risk: Olympic/Mastercard genericness; pace only via red.

## 4 Seven
The weekly ring: seven day-slots; fill covers the days used, changing colour at day boundaries; the needle is the clock.
Slots stroke 12 at r 27.6 cream α0.22, 6° gaps: [3,48.43] [54.43,99.86] [105.86,151.29] [157.29,202.71] [208.71,254.14] [260.14,305.57] [311.57,357]. Fills stroke 12 butt in the same slots: Claude days 1–3 (3→48.43, 54.43→99.86, 105.86→151.29); ChatGPT days 4–5 (157.29→202.71, 208.71→254.14); Gemini 260.14→290. Needle at 300°: (50.54,52.0)→(25.16,37.35) α0.85; hub r 4.0. Halo 9 units (±9.3°).
Mono: slots α0.30, fill as white dashed arc 3→290, hub+needle solid. Risk: busiest; spinner/dial.

## 5 Runners
Three runners on one track: only each service's leading tip is drawn, as a short pill; one cream needle.
Track r 27.6 stroke 4.5 α0.50, gap 226.1→253.9. Pills stroke 12 round caps, 22° each: Claude 266→288 (past the needle, over pace); ChatGPT 190→212 (within); Gemini 73→95 (well under). Needle at 240°: (50.54,56.0)→(25.16,70.65) α0.85; hub r 4.0.
Mono: track α0.40, three white pills, hub+needle solid. Risk: no amount shown; spinner shape.

## 6 Lanes (Robin's concentric idea)
Three concentric lanes, outer Claude over pace, middle ChatGPT within, inner Gemini well under; one straight cream pace line through all three.
Bands stroke 6, centre radii 31.5 / 22.5 / 13.5. Lane tracks stroke 6 cream α0.18, split around the line: outer gap 230.45→249.55, middle 226.6→253.4, inner 217.7→262.3. Pace line at 240°: stroke 4.5 round, r 3.5→33.5: (50.97,55.75)→(24.99,70.75), α0.90; hub r 3.0 α0.85.
Outer Claude 0→230.45 butt + red 249.55→285 butt; middle ChatGPT 0→215 round cap; inner Gemini 0→95 round cap.
Mono: lanes α0.25; fills, hub, line solid. Risk: Apple Watch Activity rings likeness (the line must be the most visible cream element).

## 7 Split (Robin's three-way split)
Background replaced by three dim provider-tinted wedges meeting at (54,54), seams at 60°, 180°, 300°:
A Claude `#482F27`: (54,54)→(108,22.8)→(108,0)→(0,0)→(0,22.8)→close. B ChatGPT `#1E4035`: (54,54)→(54,108)→(108,108)→(108,22.8)→close. C Gemini `#373D4A`: (54,54)→(0,22.8)→(0,108)→(54,108)→close. Seams: plate-colour stroke 3 along the rays to (108,22.8), (54,108), (0,22.8).
Foreground: band-centre r 26.5, fill stroke 10 butt; track 4.5 cream α0.50 split ±7.13° at each tick. Ticks at 14.4°, 134.4°, 254.4°: stroke 3.6 round, r 19→34, cream α0.90. Fills: Claude 300→7.27 and red 21.53→36; ChatGPT 60→124; Gemini 180→212.4. Track gaps 7.27..21.53, 127.27..141.53, 247.27..261.53.
Optional nod layer (draw only as a small inset; the designer recommends NONE): cream α0.30 glyphs ≤10 units at r 13 on each wedge bisector — Claude (54,41) regular 8-spoke asterisk r2→5 stroke 1.6; ChatGPT (65.26,60.5) regular hexagon outline circumradius 5 stroke 1.6; Gemini (42.74,60.5) straight-edged 4-point star cut out of the wedge, points 5, waist 1.4.
Mono: split vanishes (ground not in mono). Risk: highest flag risk (colour fields); tri-wedge ≈ pie/Mercedes.

## 8 Ceiling
The week is spent: the three-colour fill has gone all the way round and is held by a stop bar at 12; the needle sits at pace (300°) and the red from needle to tip says it ran out early.
No extent hairline. Band stroke 12 at r 27.6, butt: Claude 8.3→140; ChatGPT 140→255; Gemini 255→286.1; red 313.9→351.7. Stop bar at 0°: stroke 5 round caps, r 19.5→33.3: (54,34.5)→(54,20.7), cream α0.90. Hub r 4.0; needle at 300°: (50.54,52.0)→(25.16,37.35) α0.85.
Mono: white arcs 8.3→286.1 and 313.9→351.7; bar, hub, needle solid. Risk: closest to Chrome; an always-spent icon may feel negative.

## 9 Sweep
Relay's ring with the three colours blending smoothly along the fill, ending at the pace needle. Same silhouette/mono as Relay.
Fill = filled annular sector outer r 33.6, inner r 21.6, θ 0→236.1, with a sweep gradient. Stops by θ/360 (order Claude → Gemini → ChatGPT, chosen for clean blends): 0.000 #D97757 · 0.222 #D97757 · 0.306 #8AB4F8 · 0.472 #8AB4F8 · 0.556 #19C39A · 0.694 #19C39A · 1.000 #19C39A. In HTML use a CSS conic-gradient (from 0deg at 50% 50%) masked to the annular sector. Track r 27.6 stroke 4.5 α0.50 gap 236.1..263.9; hub r 4.0; needle at 250°: (50.24,55.37)→(22.71,65.39).
Risk: gradients read consumer/fun; provider order differs from the app's.

## 10 Countdown
The fill runs to the pace needle; the remaining track is cream dots counting down to a small clockwise chevron at 12 where the window comes round again.
No continuous hairline. Band stroke 12 at r 27.6, butt: Claude 6→120; ChatGPT 120→210; Gemini 210→276.1. Hub r 4.0; needle at 290°: (50.24,52.63)→(22.71,42.61) α0.85. Dots: four 1° arcs, round caps, stroke 4.5, cream α0.50, centred at 310°, 320°, 330°, 340° on r 27.6. Reset chevron polyline (49.51,32.87)→(53.04,26.42)→(47.01,21.14) stroke 4.5 round caps/joins cream α0.90. Quiet variant: a cream disc r 3.5 at (54,26.4) instead of the chevron.
Mono: dots α0.50; fill one white arc 6→276.1; chevron, hub, needle solid. Risk: refresh/reload silhouette; dots read as loading.

Sibling pairs (don't pick twice): 1/9 (same mono), 2/7 (same gauge), 1/4.
