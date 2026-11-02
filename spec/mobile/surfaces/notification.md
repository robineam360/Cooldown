# Surface spec: the always-on notification, the status-bar ring, the reset pings (Android)

Covers the pinned always-on notification (single account and the Duet, collapsed and expanded), its
status-bar ring glyph, the condition strips, and the reset-ping notifications. Describes **v1.9 as
shipped**; the approving wireframe for each part is linked in that part's Sources. Code is the truth:
where an approved wireframe and the code disagree, the code is documented and a "Differs from" line says
so. **When you change a surface, update this file in the same commit.**

Not covered: reset-ping *rules* (modes, thresholds) live in [BEHAVIOR-MOBILE.md](../BEHAVIOR-MOBILE.md)
section 2 and the condition rules in its section 3; they are linked, not repeated. Widgets, the app and
shared foundations are in the sibling files of this folder.

Paths below are relative to `app/src/main/`; Kotlin lives under `java/com/robin/claudeusage/`.

## 0. What exists today (read first)

| Thing a designer might expect | Today |
|---|---|
| Notification styles Gauge / Number tile / Progress bar / Huge number | **Only "Huge number" exists.** The other three were removed by CCRM-61 (Settings Diet) on 2026-09-09; there is no "Notification style" setting. Everything below is the Huge number style, which is the only one built from custom RemoteViews and the only one that can carry two accounts. |
| Status-bar glyph choice Ring / Pie / Battery / Number | **Ring only** (no setting). Drawn by the rails gauge, NEEDLE pace mark. |
| Quick Settings tile (CCRM-11 (Tile Reset Time)) | **Does not ship.** Removed by CCRM-61 (Settings Diet); no tile service or manifest entry exists. Section 6 keeps the facts that came out of it. |
| Standalone usage, pace, sign-in, stale and update notifications | **Do not exist.** Every one is a strip inside the pinned panel (section 5). The only standalone notification is the reset ping (section 7). |

Settings that drive this surface (Settings, "Always-on notification" section, plus Appearance):

| Setting (exact label) | Values | Effect |
|---|---|---|
| "Always-on usage notification" | on / off | Off removes the notification and its status-bar ring; subtitle: "Silent and ongoing, with a status-bar icon. Nothing else posts on its own except the reset pings below." |
| "Show accounts" - "First" / "Second · optional" | Second: "None" or any account | "None" = single layout; any account = the Duet (section 2). Second cannot equal First. |
| "Tapping a number opens" | "Cooldown" / "That service's app" | Per-half tap target (section 1.5). Helper copy when the service app is missing: "The <Provider> app isn't installed — taps open Cooldown instead." |
| "Status-bar icon shows" | "First" / "Second" / "Whichever is higher" | Which account the one icon shows; caption "The 5h ring, or the Weekly bar where the account has no 5h or picks it, in that account's own colour." (CCBG-63 (Status Ring Wording), Robin 2026-11-02); with one account the row reads "One account, so there is nothing to choose". |
| Appearance: "Usage display" | "Used" / "Left" | Flips every digit readout (section 1.4). Bars and the ring keep drawing the spend. |
| Appearance: "Reset time" | "Countdown" / "Clock time" | Which form leads in a reset line (section 1.4). |
| Appearance: "Show red past the pace mark" | on / off | Gates the red slice past the pace mark on bars and ring; the pace mark itself always draws. |
| Appearance: "Time format", "Theme color" | | 12/24 h in reset lines; accent colour ("Per provider" by default, so two accounts differ by colour as well as by mark). |

## 1. The pinned notification, single account (Second = None)

### 1.1 Delivery facts

| Fact | Value | Source |
|---|---|---|
| Channel | id `pinned_usage_v2`, name "Pinned usage", importance LOW (silent, no heads-up, keeps the status-bar icon) | `notify/PinnedNotification.kt:76` |
| Flags | ongoing, only-alert-once, no timestamp, category status; kept alive by a foreground service (CCRM-67 (Pin Service)) so it stays pinned near the top | `notify/PinnedNotification.kt`, `notify/PinnedService.kt` |
| Style | DecoratedCustomViewStyle: the system draws icon, app name and chrome; the app owns only the content strip | `notify/PinnedNotification.kt:312` |
| Action button | one, "Refresh" (refreshes every account) | `notify/PinnedNotification.kt:229` |
| App-name accent | the severity colour of the shown figure (Duet: of the ring's account) | `notify/PinnedNotification.kt:226` |
| Redraw | every poll and every relevant Settings change, via the one refresh seam | `notify/Surfaces.kt` |
| Plain-string fallback (lock screen, TalkBack, skins ignoring custom views) | title = "<Label> · Weekly" (always, since CCRM-103 (Hybrid Notification)); text = the collapsed line, prefixed "Synthetic data · " while the synthetic series is on | `notify/PinnedNotification.kt` |

Text colours come from the platform's own notification text appearances, never from the app theme, so
the same layouts read correctly on a light or dark shade. Only the figure takes a usage colour.

### 1.2 Anatomy, collapsed (`res/layout/notif_big_number.xml`)

**CCRM-103 (Hybrid Notification), shape grammar rev D §3 (`design/2026-11-02-shape-grammar.html`, frozen
2026-11-02): H4.** The 5h is always the hollow ring on the left, the Weekly always the bar and the figure on
the right; the shapes never swap, whatever the Headline. One row, 2 dp padding top and bottom, content
height about 53 dp (19 dp label line, 13 dp bar plus 3 dp margin, 16 dp sub plus 4 dp margin); the 44 dp
ring sits inside it, so the collapsed height is unchanged (Δ 0 against v2.0). Left column takes the
remaining width; the figure block trails.

| Element | Size | Colour / style | Rule |
|---|---|---|---|
| 5h ring (left of the column) | 44 dp, 14 dp gap right, vertically centred | `ui/ShapeGrammar.kt` `GrammarRing.paint` bitmap (band 0.10 S, track, fill in the account accent with its severity step, dashed room to the pace tick, red slice past it, the Usage end mark, the tick) | **gone for a plan with no 5h (B1: today's row exactly)**; shown with no reading yet (hairline ring, "—") |
| Ring figure (in the bore) | 0.29 × 44 ≈ 12.8 dp before fitting (0.24 for four characters), shrunk until it clears the band by 1.5 dp; sized in dp, so font scale cannot push it into the band | bold, tabular lining digits, its "%" at 58%; ink (`#ECECEC` dark / `#1F1F1F` light); the ladder colour above 90 **or while the 5h binds**; Vs pace green / red / ink; 50% ink with no reading; 45% ink when greyed | the 5h in the Usage display ("34%", "66%" Left, "19%" Vs pace); **no "5h" label** anywhere; TalkBack hears "5-hour window, 34 percent" |
| Provider mark (left of label) | 14 dp square, 4 dp gap right | tinted with the account accent | the Claude / ChatGPT mark of the account's provider |
| Label (title) | 13 sp, platform notification Title style, 1 line, ellipsis | platform title colour | "<Account label> · Weekly", whatever leads |
| Synthetic dot | 7 dp circle, 5 dp margin left, after the label, at the end of the row | violet `#B388FF` | visible only while the synthetic series is on; contentDescription "Synthetic data" |
| Bar (full width of the left column) | ImageView 13 dp high, 3 dp margin top, bitmap stretched to fit | see 1.6 | the Weekly's usage with its pace tick and the Usage end mark |
| Sub line | 12 sp, platform Info style, 1 line, ellipsis, 4 dp margin top | platform info colour | the highest-priority strip behind a "●" or, quiet, the line in 1.4 |
| Figure | 32 sp bold, letter-spacing −0.02, tabular lining digits, "%" at 58%, no font padding, 14 dp margin left of the left column | severity colour (1.4); in Vs pace unsigned and coloured (`#9CCC65` / `#2E7D32` under the mark, `#FF5252` / `#C62828` over, ink on it, the severity colour when it falls back to usage) | the Weekly: "52%" or "—" |
| Caption | 10 sp, letter-spacing 0.08, under the figure, centred on it | platform info colour | "LEFT" in Left mode; "UNDER PACE" / "OVER PACE" / "ON PACE" in Vs pace (the colour-blind cue); none in Used or when the figure falls back |

Layout order: [ring] then [mark] [label ........ synth dot] / [bar] / [sub] in the column; [figure] /
[caption] on the right, vertically centred.

```text
 ╭──╮  [mark] Teams · Weekly                    52%
 │34│  [==========|---------------------]
 ╰──╯  Slow down · ≈11%/day
```

### 1.3 Anatomy, expanded (`res/layout/notif_big_number_expanded.xml`)

2 dp padding top, 4 dp bottom. Same header as collapsed, with these differences, then the panel.

| Element | Difference from collapsed | Rule |
|---|---|---|
| 5h ring | the 44 dp ring with the **5h's time left under it**: 11 sp, 13 dp line, 3 dp gap, tabular digits, platform info colour ("in 2h 20m"); ring plus line 60 dp, centred on the header | the time line is gone without a reset clock, and while the week is spent (the 5h cannot be used); no ring for a plan with no 5h (B1) |
| Label | 14 sp | same text |
| Bar | 13 dp high, 4 dp margin top | same drawing |
| Sub line | always the line in 1.4, **never a strip** | the strips live in the panel below |
| Line 2 (`sub2`) | 12 sp, 2 dp margin, gone when empty | today's distance / reset text (rev D C3: no pace readout in the notification) |
| Figure | **44 sp** bold | the height is generous, so the figure grows |
| Synthetic marker | none in the row | the whole view sits under the full-width band instead (1.7) |
| Panel (`notif_panel_single.xml` included under the header) | 10 dp margin top | section 3: **no 5h row any more** (the ring and its time line replace it, −43 dp) and no Weekly row (the header is the Weekly) |

Android caps an expanded custom view at about 256 dp; the single layout is not height-priced the way the
Duet is: the panel simply shows what its rules in section 3 allow (strips displace model caps, never the
Weekly row).

### 1.4 What each text line says (single account)

**v2.0 (CCRM-96 (Weekly First), CCRM-97 (Pace Number), CCRM-100 (Vs Pace Display); built on the v2.0
branch, `design/2026-11-02-weekly-first-pace.html` §2–4, copy calls in BEHAVIOR-MOBILE §5).** The headline
window is the account's **Headline** (BEHAVIOR-MOBILE §5): Weekly by default; 5-hour; Auto = 5h while core §6.2's
`sessionBinding` holds. An account with no 5h always leads with Weekly. Lines (`notify/LeadLines.kt`):

**CCRM-103 (Hybrid Notification), rev D §3:** the label, the bar and the figure are always the Weekly and the ring
always the 5h; the Headline now decides only **whose words the sub line carries**. "Auto takeover" (Auto while the
5h binds, or Headline 5-hour) never swaps a shape: the ring turns loud (its ladder colour, the red slice, its figure
in that colour) and the sub line takes the 5h's words; the trailing figure stays the Weekly. The status-bar icon (4)
follows its own Status-bar icon choice, not the Headline.

| Lead | Collapsed line | Expanded line 1 / line 2 | Ring |
|---|---|---|---|
| Weekly | "Slow down · ≈11%/day" — the pace words only; the ring replaced v2.0's "· 5h 34%" companion (no 5h: "Slow down · ≈11%/day · resets Sun", B1) | "Slow down · ≈11%/day to last" / "13 over the pace mark (39%) · resets Sun" (Left: "10 to spare · pace mark 40% · resets Sun"; Vs pace: "52% used (mark 39%) · resets Sun") | quiet: ink figure; **while the 5h binds** (Q1) the arc and the figure take the ladder colour, the red slice shows |
| Weekly, spent | "Out until Sun 9:30 PM · in 3d 4h" | same / none | **greyed** (CCRM-102 (Spent Week Gate)): the track alone at 22%, the figure ("34%") at 45% ink, no time line |
| Takeover (5-hour, or Auto while the 5h binds) | the 5h's reset line, "Back at 3:40 PM · in 50m" while it binds | the 5h's reset line / "Projected to hit 100% at 3:25 PM" when core §3 projects a hit (as v2.0) | loud, as above |
| Weekly not started (Q6) | "Weekly not started" | same / none | as its 5h reads ("—" and the hairline without a reading) |
| No reading yet (Q7), single and Duet | "No reading yet" | same / none | hairline ring, "—" |

Model-cap rows follow in the panel only while the account's "Show model limits" is on (CCRM-99). The figure follows
Used / Left / Vs pace; in Vs pace it is unsigned and coloured, with "UNDER PACE" / "OVER PACE" / "ON PACE" under it
(none when it falls back to usage), and panel row figures are coloured distances too. Before CCRM-103: the panel's
first row was the companion — the 5h under a Weekly lead, the Weekly under a 5h lead — and a 5h lead swapped the
label and the figure to the 5h.

Figure text: Used mode, the percentage truncated ("99.7" shows "99%"); Left mode, the remainder floored
("99.7 used" shows "0%"), never negative. No reading shows "—". The warning ladder and fills always key
on the *used* percentage, whatever the figure says.

| State | Figure | Bar | Sub line (collapsed) | Sub line (expanded) |
|---|---|---|---|---|
| Takeover (5-hour, not binding), countdown leads | "52%" (the Weekly) in severity colour | fill, pace tick, red tail if above pace | "Resets in 2h 10m · 6:40 PM" | same |
| Takeover, clock leads | same | same | "Resets at 6:40 PM · in 2h 10m" | same |
| 5h reset under 5 minutes | same | same | "Resets soon · 6:40 PM" (countdown form) | same |
| Weekly not started: no reset instant, or no Weekly reading | "—" | bare track, no tick | "Weekly not started" | "Weekly not started" |
| Takeover with a 5h that has no reset instant | the Weekly figure | the Weekly bar | "Not started yet" | "Not started yet" |
| No reading yet (nothing ever fetched) | "—" | bare track, no tick | "No reading yet" | "No reading yet" |
| Sign-in broken (re-auth needed) | the **last cached** figure (the single layout keeps showing it) | last cached | the red strip: "●  Sign-in stopped working" (dot in fixed red) | reset line; the strip is in the panel |
| Stale (polls running, none succeeded for 6 h) or a Free-plan fault | figure (and the ring's figure) at **50% alpha** | bar (and the ring) at **45% alpha** | strip: "●  Stale — nothing since 6:00 AM" | reset line; strip in the panel |
| Above pace (used > even pace + 3 points) | unchanged (colour is ladder only) | red slice from the tick to the fill edge, if "Show red past the pace mark" is on | normal | normal |
| 81 to 90% | `#FDD663` (dark shade) / `#F9A825` (light) | fill the same | normal | normal |
| 91 to 99% | `#FFA726` / `#F57C00` | same | normal | normal |
| Spent, 100% | `#FF5252` / `#C62828`; "100%" (Used) or "0%" + "LEFT" (Left) | full fill in the same red | normal | normal |
| Left mode with a reading | remainder + "LEFT" caption | still draws the spend | normal | normal |
| Synthetic series on | as above | as above | collapsed row gets the violet dot; plain-string text prefixed "Synthetic data · " | whole view under the band (1.7) |

Pace verdict thresholds: the dead zone is 3 points (`PACE_DEAD_ZONE`, `ui/Palette.kt:21`), strict
greater-than, identical to the in-app chart and sentence. Elapsed percent comes from the window's reset
instant and a 5h or 7-day length; no reset instant means no tick and no red.

Time strings: "6:40 PM" or "18:40" per "Time format"; the Weekly reset line adds the weekday ("Tue 9:00 AM").
Relative forms: "in 2h 10m", "in 4d 3h", "in 12m", "soon", "any moment".

### 1.5 Tap targets

| Surface | Target | Rule |
|---|---|---|
| Anywhere on the single notification | one content intent | "Tapping a number opens": "Cooldown" opens the app on that account's page; "That service's app" launches the account's provider app (falls back to Cooldown when it is not installed) |
| "Refresh" button | refresh broadcast | refreshes all accounts |
| Duet halves | one target per half (section 2.5) | |

### 1.6 The bar bitmap

Drawn by `ui/BarRenderer.kt` to a bitmap; RemoteViews cannot host a tick, so there is no drawable bar.

| Part | Rule |
|---|---|
| Nominal width | 340 dp for single bars and panel rows; 156 dp for a Duet collapsed half, 47 dp beside its 36 dp ring (`Duet.barDp`); the ImageView stretches it (`fitXY`), so a few percent of residual stretch is expected |
| End (CCRM-103 (Hybrid Notification), rev D §1e) | Appearance › Usage end mark: round (as before), or a square fill end with the short end-mark line over a 1 dp gap cleared to the shade (`BarRenderer.draw(endMark, surfaceArgb = null)`); the ring takes the same mark |
| Bar height in the bitmap | 8 dp, full pill (radius = height / 2) |
| Bitmap height | 8 dp plus 0.3 x height overhang above and below (about 12.8 dp), so the tick is never clipped; side padding is half a tick width |
| Track | the fill colour at 25% alpha, full width |
| Fill | severity-ladder colour; minimum visual width = bar height, so 1% still reads as started; no reading = bare track, never a fake 0% |
| Red segment | `#FF5252` dark / `#C62828` light, from the pace tick to the fill edge, clipped to the fill's rounded shape so the boundary is a straight vertical edge; no minimum width |
| Pace tick | width max(2 px, 0.31 x bar height), about 2.5 dp; full bitmap height; ink `#F2F2F4` at 60% (dark) or `#1D1D1F` at 48% (light); drawn last |
| Tick honesty | no percent or no reset clock = no tick |
| Severity colour | the account accent until used exceeds 80; above 80 yellow, above 90 orange, 100 red (hexes in 1.4) |

Arithmetic worth knowing before redrawing: ImageView heights in the layouts are 13 dp (single bars, Duet
expanded bars and Duet panel rows), 10 dp (single panel rows) and 8 dp (Duet collapsed). The bitmap is
about 12.8 dp tall, so 13 dp shows it 1:1 while 10 dp and 8 dp scale it down vertically (to about 0.78 and
0.63) under `fitXY`. Whether that compression is wanted is not recorded in the approved wireframes.

### 1.7 Synthetic-data marker (verification mode)

| Where | Marker | Spec |
|---|---|---|
| Expanded views (single and Duet) | full-width band above the whole view | text "SYNTHETIC DATA", 9.5 sp bold white, letter-spacing 0.06, centred, 2 dp padding top and bottom, 2 dp margin below, background `#7C4DFF`, radius 4 dp |
| Collapsed single | violet dot after the label | 7 dp, `#B388FF` |
| Collapsed Duet | violet dot after First's label | 6 dp, `#B388FF`; app-global so First only |
| Surfaces that ignore custom views | text prefix | "Synthetic data · " |

## 2. The Duet (Second set)

Two accounts side by side collapsed, two stacked header blocks expanded. Chosen by one value (Second not
None); an install that never sets Second never sees any of this.

**CCRM-103 (Hybrid Notification), rev D §3 D1–D4:** each half is the Weekly (label, bar, figure) with its 5h as
a ring; the Headline picks only the expanded sub's words, per account (1.4). Collapsed halves have no sub line, as
before. An expanded header's sub is "Slow down · ≈11%/day · resets Sun" under a Weekly lead, the 5h's reset line in
a takeover. **The panel has no bar rows any more** — the companion rows left with the grammar (each 5h is its
header's ring with its time left, each Weekly its header) — so it carries only the condition strip. An account with
no 5h has no ring anywhere on the Duet (B1). The status-bar icon's "Whichever is higher" compares the gauge windows (4).

### 2.1 Width and height budget

| Quantity | Value | Arithmetic |
|---|---|---|
| Collapsed half width | **156 dp** | 360 dp card - 2 x 16 dp system padding = 328 dp; minus one 16 dp gutter = 312; split two ways. The gutter is a start margin on Second so halves stay equal. A skin giving the full card width makes halves 172 dp; real halves run from about 101 dp (Fold 7 inner shade) to 156 dp, which is why the layout is built for the tight case |
| Collapsed content height | about 52 dp | label line about 14 dp, 1 dp gap, figure about 35 dp (30 sp), plus 1 dp padding each side; collapsed custom views get about 48 to 64 dp depending on skin |
| Collapsed label ceiling | 138 dp, 127 dp with one dot, 116 dp with two (never below 24 dp); less 44 dp beside the 36 dp ring (94 / 83 / 72), less 18 dp beside the bare 14 dp ring | 156 - ring and its gap - 14 mark - 4 gap - 11 per dot (6 dp dot + 5 dp margin); on narrower halves the layout's weight ellipsizes against the real width (`Duet.labelClampDp`) |
| Collapsed ring | 36 dp at a half of 150 dp or more — the cover and the Fold 7 inner shade alike (CCBG-64 (Inner Duet Ring), Robin 2026-11-02: the Step 7 pass measured the inner halves at about 180 dp); the bare 14 dp ring only for a half known to be under 150 | 36 + 8 gap + a 40 dp bar + 8 + a 57 dp figure = 149 (`Duet.halfRing`, `Duet.halfDp`) |
| Expanded cap | about 256 dp | Android's limit on an expanded custom view |
| Expanded header chrome | 136 dp, up to 140 dp | two header blocks of 58 dp (label 19, bar 13 + 4 margin, sub 16 + 4 margin; the 36 sp figure fits inside), 60 dp for a header with the 44 dp ring and its time line, 10 dp between them, 10 dp before the panel |
| Expanded panel budget | about 116 dp (Fold 7 about 132) | 256 - 140 |
| Panel pieces | one-line strip 38 dp; no bar rows | well inside the budget |

### 2.2 Anatomy, collapsed (`res/layout/notif_duet.xml`)

Two halves, each a **two-line** block (rev G: the label on its own line so the figure never competes with
it). 1 dp padding top and bottom.

| Element | Size | Colour / style | Rule |
|---|---|---|---|
| Provider mark | 14 dp, 4 dp gap | account accent | |
| Label | 12 sp, platform Title style, no font padding, 1 line, ellipsis, max width per 2.1 | platform title colour | the account label only; **no window name** collapsed |
| Condition dot | 6 dp, 5 dp margin left, after the label | fixed red (`#B3402A` light / `#E0705A` dark) for an account fault; the First account's accent for the app-global update strip | visible when that account has a sign-in, plan or stale fault; First also for an update strip. The dot is a pointer to the strip in the expanded panel, not a message. The expiry warning does not raise it |
| Synthetic dot | 6 dp, after First's condition dot | `#B388FF` | synthetic series on |
| 5h ring, half ≥ 150 dp (D1) | 36 dp at the half's left, 8 dp to the column, vertically centred; the half stays 52 dp | as 1.2 (the figure 0.29 × 36 before fitting, about 10 dp; dashed room kept at 28 dp and up) | gone for an account with no 5h (B1); TalkBack "5-hour window, 34 percent" |
| 5h ring, half < 150 dp (D3) | the bare 14 dp ring, no digits, no dashed room, at the far end of the label line; the line's end is padded 18 dp so the label ellipsizes before it | as 1.2 | same |
| Bar | 8 dp high ImageView (see 1.6 note), weight 1, 1 dp margin top | account accent, ladder above 80 | the Weekly; drawn at 156 dp nominal width, 47 dp beside the 36 dp ring (about what it gets) |
| Figure | 30 sp bold, **28 sp beside the 36 dp ring**, tabular lining digits, "%" at 58%, 8 dp margin left of the bar, no caption collapsed | severity colour; Vs pace coloured (1.2) | the Weekly: "52%" or "—" |

```text
 ╭──╮ [mark] Teams           [mark] Personal ●
 │34│ [===|---]  52%         [======|-------]  50%
 ╰──╯
```

Which ring a collapsed half takes is decided against the **nominal** 156 dp half (`Duet.NOMINAL_HALF_DP`):
RemoteViews never learns a half's real width, so today every half with a 5h draws the 36 dp ring and the D3 bare
ring is built but not selected (see 8).

### 2.3 Anatomy, expanded (`res/layout/notif_duet_expanded.xml`)

Two header blocks, 2 dp padding top and 4 dp bottom, Second 10 dp under First, then the compact panel.

| Element | Size | Rule |
|---|---|---|
| 5h ring (D4) | 44 dp with the 5h's time left under it, as the single header (1.3), 14 dp to the column; that header is 60 dp | gone for an account with no 5h (B1: "Personal's header is today's") |
| Mark | 14 dp, 4 dp gap | |
| Label | 14 sp, max width 200 dp, ellipsis | "<Account label> · Weekly" |
| Condition dot | 6 dp, 5 dp margin left | as 2.2 |
| Bar | 13 dp high, 4 dp margin top, full width | the Weekly, drawn at 340 dp nominal |
| Sub | 12 sp, 4 dp margin top | 1.4's words for that account ("Slow down · ≈11%/day · resets Sun", or the 5h's reset line in a takeover), or "No reading yet" |
| Figure | **36 sp** bold, tabular lining digits, "%" at 58%, 14 dp margin left | severity colour; Vs pace coloured |
| Caption | 10 sp, letter-spacing 0.08 | "LEFT", or "UNDER PACE" / "OVER PACE" / "ON PACE", under the figure |

### 2.4 States (per half; the other half is unaffected)

| State | Collapsed | Expanded |
|---|---|---|
| Quiet | ring, label, bar, figure | ring with its time left, "Teams · Weekly", the pace words, figure |
| 5h binding (D2) | the ring carries the loud state (ladder colour, red slice, figure in that colour); the Weekly figure is unchanged | same; a takeover's sub is the 5h's words |
| Week spent | the ring greyed (CCRM-102 (Spent Week Gate)), the figure at 45% ink | same, no time line |
| No reading, or sign-in broken | ring hairline with "—", figure "—", empty track, **no tick**, red dot after the label (broken only) | sub "No reading yet" and the red strip. A broken sign-in is stricter here than on the single layout: no cached figure is shown |
| Stale | that half's figure and ring figure at 50% alpha, bar and ring at 45% | same; the other half stays crisp |
| Free-plan fault | red dot | red strip; the half is **not** dimmed (dimming keys on stale only on the Duet) |
| No 5h window (B1) | no ring: today's half, 30 sp figure | no ring: today's header |
| Update strip present | accent dot on **First** only | strip in the panel |
| 100% | red figure and full red bar; a four-character figure takes about 74 dp so the label ellipsizes sooner | "100%" or "0%" + "LEFT" |
| Left mode | remainder figures, no caption | remainder figures with "LEFT" captions |
| Synthetic series on | violet dot after First's dots | band over the whole view; **no strip at all** (it would cost 19 dp of a 120 dp panel); halves keep their dots |

Plain-string fallback title: "<First label> 42% · <Second label> 63%"; text: the collapsed line.

### 2.5 Tap targets

| Region | Target |
|---|---|
| First half (collapsed and expanded header) | First account, resolved by "Tapping a number opens" against First's provider |
| Second half | Second account, resolved against Second's provider |
| Anything else (row edges, the expanded panel) | First (the content intent) |
| "Refresh" | one button, refreshes both |

### 2.6 Status-bar ring on a Duet

One glyph for two accounts. "Status-bar icon shows" picks First, Second or "Whichever is higher"
(higher means Second only when strictly ahead; ties keep First; a missing reading hands the glyph to the
other account; both missing falls back to First). The glyph's colour is the account's own accent below 80,
so the hue says whose number it is; the app-name accent in the shade follows the ring's account. Whichever
is higher may switch during the day on purpose; the colour change is the tell.

## 3. The expanded panel (native rows, CCBG-26 (Panel Scaling))

The panel is native views, never a bitmap, so it can only be cut, never shrunk. Two twin layouts with the
same view ids: `notif_panel_single.xml` and `notif_panel_duet.xml`. Slots: 3 strips, 1 overflow line,
4 bar rows; unused slots are hidden. The whole panel is hidden when it has no rows, strips or overflow.

| Element | Single | Duet (compact) |
|---|---|---|
| Panel top margin | 10 dp | 10 dp |
| Strip | tinted rounded block: title 13.5 sp bold + detail 12 sp up to 2 lines; padding 9 dp sides, 7 dp top and bottom; 12 dp margin below | one line: the condition's short text, 13 sp bold; padding 9 / 6; 8 dp margin below; detail hidden |
| Strip tint | the strip's hue at 13% alpha (0x21), corner radius 8 dp, 6 dp dot, 7 dp gap | same |
| Strip hue | fault: fixed red `#B3402A` light / `#E0705A` dark; warning (expiry, update): the account accent | same |
| Strip cap | **3** | **1**, the remainder folded as a "+N more" tail on that line (12 sp) |
| Overflow line | "+ 2 more — open the app for the rest" (12 sp, own row, 12 dp below) | none (tail instead) |
| Bar row line | 18 dp: label 13.5 sp, value 16 sp bold right | 18 dp: label 13.5 sp, reset text 13.5 sp muted (gives way first, ellipsis), value 13.5 sp bold |
| Bar row bar | 10 dp ImageView, 7 dp margin top | 13 dp ImageView, 4 dp margin top |
| Bar row reset text | below the bar, 12 sp, 3 dp margin; hidden when empty (model caps) | inline on the label line |
| Row spacing | first row 0 dp, then 14 dp | first row 0 dp, then 8 dp |
| Row budget | 4 minus the number of strips (at least 1); model caps only | none: no bar rows on the Duet panel (no model caps ever) |
| Pace on rows | measured over 7 days (all rows are 7-day surfaces) | same |

Rows (CCRM-103 (Hybrid Notification): the 5h and Weekly rows are gone — the 5h is the header's ring with its time
left, the Weekly is the header):

| Row | Label | Value | Reset text |
|---|---|---|---|
| Model cap, single only, while "Show model limits" is on | "<Model name>" (e.g. "Opus 4.6") | "22%", tabular, "%" at 58%; in Vs pace green under the mark, red over | none |

Rows exist only when the account has data. In Left mode the value is the remainder; the bar still draws
the spend, with the Usage end mark.

Differs from the Duet approved in settings-diet-wireframe.html section 2 and its original strip cap of
two: CCBG-26 (Panel Scaling), approved 2026-09-10, cut the Duet to one one-line strip and removed model
caps; the code and `notif_duet_expanded.xml` comment line "falls to two" is stale, `Duet.maxStrips` is 1.

## 4. Status-bar ring and rails gauge (CCRM-83 (Ring Renderer), NEEDLE mark)

The notification's small icon. One window (the headline 5h one, or Weekly when that is the headline), one
account, no numbers. Drawn by `ui/RingRenderer.kt` (NEEDLE) via `ui/UsageIcon.kt`; the widgets and share
card share the same renderer with a different mark (TICK, specified in their own files).

**Shape grammar (CCRM-103 (Hybrid Notification), rev D §4, §4b; supersedes v2.0's Headline-driven gauge):** the
Headline no longer picks the status-bar window. `data/StatusIcon.kt StatusIconRule` picks one of four glyphs per
account (`spec/mobile/fixtures/status-icon/`), drawn by `UsageIcon.drawGlyph` in the same 24 dp box:

| Glyph | When | Drawing |
|---|---|---|
| 5h ring | Status-bar icon "5h ring" (the default), the 5h not full, the week not spent | this section's NEEDLE ring, unchanged, on the 5h |
| Red cross | the drawn window full (truncated 100): the 5h under "5h ring"; the Weekly, or a full 5h, under "Weekly bar" | a red cross alone, arms 7 units, stroke 3.4, round caps, no circle |
| Greyed ring | "5h ring" while the Weekly is spent (core §6.1); outranks the cross | hairline 30% ink, the 5h's arc at 40% ink, no needle |
| Weekly bar (N3) | "Weekly bar" chosen, or a plan with no 5h | a 21 × 10-unit capsule at y 7: track 16% ink, fill in the severity colour (min 9%), red past the tick, a 1.2-unit 50% outline, the tick 1.8 units at 95% ink in a 4.2-unit cleared halo |

The choice is per account, in its ⋮ › Headline… sheet, group "Status-bar icon" between Headline and Model limits:
"5h ring" · "Weekly bar", each with its 16 dp glyph; caption "Either turns to a red cross while you are out.", or
for a plan with no 5h the group greyed at 45% on "Weekly bar" with "This plan has no 5-hour window, so the status
bar always shows the Weekly bar." In a Duet the icon belongs to the account "Status-bar icon shows" picks;
"Whichever is higher" compares each account's drawn window (the 5h for the ring, the Weekly for the bar).

### 4.1 Real size

| Fact | Value |
|---|---|
| Bitmap | 24 dp square |
| Slot | the status bar fits it into a **about 15 dp slot, by width**: it lands at about **14 dp, 37 px on a Fold 7**. Every number below was chosen against that rasterised size |
| Shape | **square is optimal**: a wide bitmap keeps its aspect at the same width (48 x 24 dp renders about 14.9 x 7.6 dp), so side-by-side layouts do not port |
| Colour | **survives in the status bar** (sampled back exactly); is flattened to one tint where a surface monochromes the icon |

### 4.2 Geometry (units: 24ths of the 24 dp box; Ø of the gauge = 22.4)

| Element | Size | Ratio of Ø | Colour / alpha |
|---|---|---|---|
| Extent hairline (full circle at band-centre radius 9.2) | 1.493 wide | 1/15 | neutral ink at 50% |
| Usage band | 4.0 wide, centre radius 9.2, from 12 o'clock clockwise | 0.179 | severity colour (4.3) |
| Red slice | same band, from the needle angle to the fill tip | | `#FF5252` dark / `#C62828` light |
| Hub dot | radius 1.344 at the centre | 0.12 diameter | neutral ink at 85% |
| Needle | 1.792 wide, from hub to rim radius 11.2, round ends | 0.08 | neutral ink at 85% |
| Needle cleared halo | 4.48 wide, starts 0.7 beyond the hub; erased to real transparency, so a gap shows even where the icon is flattened | 0.2 | cleared |
| Spent cross | arms +/-4.2 from centre, 2.0 wide, round ends | | neutral ink at 90% |

Draw order (the contract): **extent, usage, red slice, hub, needle, spent cross**.

### 4.3 Colour and honesty rules

| Rule | Detail |
|---|---|
| Fill colour | `Palette.barColor` of the used percent over the account accent: **the account's own accent up to and including 80%**, yellow above 80, orange above 90, red at 100 (same value as the figure, so glyph and number cannot disagree). On a Duet the accent is what says whose |
| Neutral ink | white (dark system theme) or `#111112` (light) at the alphas above; **time has no severity**, so the hairline, hub, needle and cross never take a ladder hue. Keyed to the system theme, not the app's |
| Red slice | from the needle to the fill tip when used exceeds elapsed by more than 3 points and "Show red past the pace mark" is on. It wins over the severity fill, nothing suppresses it above 80. With a red slice the fill's end is butt-capped so the flat red tip reads flat; otherwise round-capped |
| Minimum fill | any non-zero reading draws at least 9% of the ring (about 32 degrees) so 1% still reads as started |
| No reading | the hairline alone, never a fake 0% |
| Needle and hub | draw only with a reading, a reset clock **and** usage above 0; "no reading", "no usage" and "no clock" all render as the hairline alone |
| Spent cross | at a truncated 100% an x sits in the hollow, nothing else changes |
| Used / Left | the ring always draws the spend; Left mode changes nothing on it |
| Weekly flag | none: the old weekly hub dot was removed by CCRM-62 (Duet Notification) |

Differs from rails-gauge-wireframe.html: that table lists a "12 o'clock post at 100%", a "weekly flag dot"
and a hub that exists only as a fallback; the shipped glyph has the spent cross instead of the post, no
weekly dot, and the hub always drawn with the needle. Differs from status-bar-gauge-wireframe.html (twin
rings, CCRM-48 (Status-Bar Gauge)): superseded, the glyph is one ring.

## 5. Condition strips and their cap (CCBG-12 (Status Icon Swap), CCRM-44 (One Surface))

States, not notifications: derived live at draw time, cleared on their own, nothing persisted, nothing to
dismiss. They exist because a second notification makes Android swap the live meter for the launcher
icon. Rules of when each is true are in BEHAVIOR-MOBILE.md section 3; this section is the copy and layout.

| Condition | Hue | Collapsed / Duet one-line text ("short") | Strip title | Strip detail |
|---|---|---|---|---|
| Sign-in stopped working | red | "Sign-in stopped working" | same | "The saved sign-in failed and couldn't renew. Open the app and re-sign in." |
| Free plan, no usage reported (CCBG-27 (Free Plan 403)); takes the slot stale would fill | red | "No usage on the Free plan" | "Usage isn't available on the Free plan" | "<Provider> only reports usage for Pro, Max and Team. Upgrade the plan, or remove this account." |
| Stale (no success for 6 h) | red | "Stale — nothing since 6:00 AM" | "Usage data is stale" | "Nothing fetched since Thu 7:46 PM (12m ago). Last error: <re-auth needed / rate limited / can't reach <vendor> / server error / unrecognised response / app error>" |
| Sign-in expiring (within 7 days, known expiry only) | accent | "Sign-in expires in 2d 3h 4m" | same | "Valid until Aug 13, 8:30 PM. Paste a fresh token when convenient." |
| Update available (GitHub build only; the Play build never has one) | accent | "Update available — v1.10" | same | "You have v1.9. Tap to open the app; nothing installs by itself." |

Order and cap: faults first (per account: sign-in, then plan-or-stale), then expiry warnings, then update
last (first into the overflow). Shown accounts go first (First, Second, then every other account in
registry order), so a shown account is never crowded out of its own panel. Cap 3 single, 1 Duet; the
remainder becomes the overflow line (single) or the "+N more" tail (Duet).

Account prefix: in the single layout the shown account's strips are bare and every other account's strip
is prefixed "<Label>: " (for example "Product: Sign-in stopped working"). **With a Duet every strip is
prefixed**, including the shown accounts'.

Where a strip appears: always in the expanded panel; the collapsed single row gives its sub line to the
highest-priority strip as "●  <short>" with the dot in the strip's hue (the reset line stays in the
expanded header); the Duet shows no strip collapsed, only the per-half dot.

Single-layout stale dimming: figure 50% and bar 45% when the shown account is stale or on a Free-plan
fault. Duet: per half, stale only.

## 6. Quick Settings tile (not shipped), and where the facts went

No tile exists in v1.9 (see section 0). What the project learned from it still constrains the status-bar
ring: a Quick Settings tile flattens its icon to one tint, so colour (the account identity and severity)
cannot be carried there, which is why the tile was removed instead of kept as a second glyph. Anything
that flattens the ring keeps its marks readable because the needle and its halo are cleared to
transparency, leaving a gap even on a single tint. The pre-removal pinned notification and tile also
fell back to the Weekly window as headline when there was no 5h window; that rule lives on in section 1.4.

## 7. Reset-ping notifications (CCRM-61 (Settings Diet): the only standalone notification)

Rules (modes Off / If busy / Always, per account and per window, the 80% threshold, firing even with the
pin on) are in [BEHAVIOR-MOBILE.md](../BEHAVIOR-MOBILE.md) section 2. Layout and copy only:

| Element | Value |
|---|---|
| Channel | id `reset_alerts`, name "Window resets", importance DEFAULT (audible, unlike the pin) |
| Small icon | `ic_stat_bars` (static bars glyph; not the live ring) |
| Title | "<Account label>: 5h reset" or "<Account label>: Weekly reset" |
| Body, live next window | "Usage is back at 3%. Next reset in 4h 12m." (percentage truncated; relative phrase like the pin's) |
| Body, idle account (window ended, next not started) | "Usage is back at 0%." with no next-reset clause |
| Style | BigTextStyle (the body wraps, expanded shows it whole); no action buttons |
| Tap | opens the app on that account's page; auto-cancels |
| Lifetime | until the fresh window resets in turn, minimum 1 hour |
| Identity | one per account per window: ids 4 (5h) and 5 (Weekly) plus 100 x account slot, so a new ping replaces the previous one for that account and window |

## 8. Wireframe versus code, index

| Wireframe | Differs |
|---|---|
| settings-diet-wireframe.html section 2, collapsed option 1 | wireframe: one top line (mark, label clamped about 72 dp, bar, 30 sp figure on one row, content about 40 dp). Code: rev G two-line halves, label 12 sp on its own line, ceiling 138 dp, content about 52 dp (design/2026-09-26-v18-gate-fixes.html, CCBG-39 (Inner Duet Squeeze)) |
| settings-diet-wireframe.html section 2, single row | wireframe: "ChatGPT · 5-hour window", "7-day" row. Code: "<Label> · 5h" and "Weekly" (CCBG-45 (Single Panel Wording)) |
| settings-diet-wireframe.html section 2, Duet strips | wireframe: two-line strips, cap 2. Code: one-line, cap 1 (CCBG-26 (Panel Scaling)) |
| 2026-11-02-shape-grammar.html rev D §3 D3 | the bare 14 dp ring for a Duet half under 150 dp is built (`Duet.halfRing`, `notif_duet.xml`); every shade measured so far (the cover, the Fold 7 inner at ~180 dp halves) takes the 36 dp ring (CCBG-64, Robin's call) |
| 2026-11-02-shape-grammar.html rev D §3 N2 | the page draws line 2 as "Resets Sun 9:30 pm · in 4d 6h"; the code keeps v2.0's distance line ("13 over the pace mark (39%) · resets Sun"), as the build brief said |
| 2026-11-02-shape-grammar.html rev D §3 D4 | the page's expanded Duet panel shows a model-cap row ("Teams · Fable"); the code keeps the Duet panel free of model caps (CCBG-26 (Panel Scaling)) |
| rails-gauge-wireframe.html | see section 4.3 |
| status-bar-gauge-wireframe.html | superseded twin-ring design; see 4.3 |
| notification-grouping-wireframe.html, fold-all-alerts-wireframe.html, notification-one-surface-wireframe.html | history of the strip mechanism; the strip set shrank (sign-in, stale/plan, expiry, update only) after CCRM-61 (Settings Diet), and strip type sizes are now 13.5 / 12 sp (single), 13 sp (Duet) |

## 9. Sources

| Part | Code | Approving wireframe / tracker |
|---|---|---|
| The 5h ring, figures, end mark (CCRM-103 (Hybrid Notification)) | `ui/ShapeGrammar.kt` (`GrammarRing`, `Grammar`), `notify/LeadLines.kt`, `notify/Duet.kt` | design/2026-11-02-shape-grammar.html rev D §3, §7, §7b (frozen 2026-11-02) |
| Pinned notification, both layouts | `notify/PinnedNotification.kt`, `res/layout/notif_big_number.xml`, `notif_big_number_expanded.xml`, `notif_duet.xml`, `notif_duet_expanded.xml`, `notif_panel_single.xml`, `notif_panel_duet.xml`, `notif_synthetic_wrap.xml`, drawables `notif_condition_dot`, `notif_strip_bg`, `notif_synth_dot`, `notif_synthetic_band` | design/settings-diet-wireframe.html (CCRM-62 (Duet Notification)), design/2026-09-26-v18-gate-fixes.html, design/duet-panel-fit-wireframe.html (CCBG-26 (Panel Scaling)) |
| Duet maths | `notify/Duet.kt` | same |
| Conditions | `notify/Conditions.kt`, `channel/UpdateChannel.kt`, `github/.../Channel.kt` | design/notification-one-surface-wireframe.html, design/fold-all-alerts-wireframe.html |
| Bars | `ui/BarRenderer.kt`, `ui/BarGeometry.kt`, `ui/Palette.kt` | design/bar-pace-marks-wireframe.html (CCRM-43 (Bar Pace Marks)) |
| Ring glyph | `ui/RingRenderer.kt`, `ui/RingGeometry.kt`, `ui/UsageIcon.kt` | design/rails-gauge-wireframe.html (CCRM-51 (Rails Gauge)), design/status-bar-glyph-legibility-wireframe.html (CCRM-49 (Glyph Legibility)), design/spent-ring-wireframe.html |
| Used or Left, Reset display | `ui/Palette.kt` (`Fmt`) | design/display-tokens-wireframe.html (CCRM-22 (Used or Left), CCRM-23 (Reset Display)) |
| Reset pings | `alerts/Alerts.kt`, `alerts/ResetRollover.kt` | BEHAVIOR-MOBILE.md section 2 |
| Settings rows | `SettingsScreen.kt` | design/settings-diet-wireframe.html |

**Expanded Duet, a header with the 44 dp ring** (CCBG-61 (Duet Line Clip)): its line is the tight form — "Use before
reset · ≈31%/day", without "· resets <day>" — which ellipsized on the Fold 7 cover; a header without a ring (a plan
with no 5h) keeps the full line.

