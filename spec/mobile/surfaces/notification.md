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
| "Always-on usage notification" | on / off | Off removes the notification and its status-bar ring; subtitle: "Silent and ongoing, with a status-bar ring. Nothing else posts on its own except the reset pings below." |
| "Show accounts" - "First" / "Second · optional" | Second: "None" or any account | "None" = single layout; any account = the Duet (section 2). Second cannot equal First. |
| "Tapping a number opens" | "Cooldown" / "That service's app" | Per-half tap target (section 1.5). Helper copy when the service app is missing: "The <Provider> app isn't installed — taps open Cooldown instead." |
| "Status-bar ring shows" | "First" / "Second" / "Whichever is higher" | Which account the one ring shows; with one account the row reads "One account, so there is nothing to choose". |
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
| Plain-string fallback (lock screen, TalkBack, skins ignoring custom views) | title = "<Label> · 5h" (or "· Weekly"); text = the collapsed line, prefixed "Synthetic data · " while the synthetic series is on | `notify/PinnedNotification.kt:273` |

Text colours come from the platform's own notification text appearances, never from the app theme, so
the same layouts read correctly on a light or dark shade. Only the figure takes a usage colour.

### 1.2 Anatomy, collapsed (`res/layout/notif_big_number.xml`)

One row, 2 dp padding top and bottom, content height about 53 dp (19 dp label line, 13 dp bar plus 3 dp
margin, 16 dp sub plus 4 dp margin). Left column takes the remaining width; the figure block trails.

| Element | Size | Colour / style | Rule |
|---|---|---|---|
| Provider mark (left of label) | 14 dp square, 4 dp gap right | tinted with the account accent | the Claude / ChatGPT mark of the account's provider |
| Label (title) | 13 sp, platform notification Title style, 1 line, ellipsis | platform title colour | "<Account label> · 5h", or "<Account label> · Weekly" when the headline is weekly |
| Synthetic dot | 7 dp circle, 5 dp margin left, after the label, at the end of the row | violet `#B388FF` | visible only while the synthetic series is on; contentDescription "Synthetic data" |
| Bar (full width of the left column) | ImageView 13 dp high, 3 dp margin top, bitmap stretched to fit | see 1.6 | headline-window usage with pace tick |
| Sub line | 12 sp, platform Info style, 1 line, ellipsis, 4 dp margin top | platform info colour | the highest-priority strip behind a "●" or, quiet, the reset line (1.4) |
| Figure | 32 sp bold, no font padding, 14 dp margin left of the left column | severity colour (1.3) | "42%" or "—" |
| "LEFT" caption | 10 sp, letter-spacing 0.08, under the figure, centred on it | platform info colour | only when "Usage display" is Left and a reading exists |

Layout order: [mark] [label ........ synth dot] / [bar] / [sub] on the left; [figure] / ["LEFT"] on the
right, vertically centred.

```text
 [mark] Personal · 5h                          42%
 [=========|---------------------------]
 Resets in 2h 10m · 6:40 PM
```

### 1.3 Anatomy, expanded (`res/layout/notif_big_number_expanded.xml`)

2 dp padding top, 4 dp bottom. Same header as collapsed, with these differences, then the panel.

| Element | Difference from collapsed | Rule |
|---|---|---|
| Label | 14 sp | same text |
| Bar | 13 dp high, 4 dp margin top | same drawing |
| Sub line | always the reset line, **never a strip** | the strips live in the panel below |
| Figure | **44 sp** bold | the height is generous, so the figure grows |
| Synthetic marker | none in the row | the whole view sits under the full-width band instead (1.7) |
| Panel (`notif_panel_single.xml` included under the header) | 10 dp margin top | section 3 |

Android caps an expanded custom view at about 256 dp; the single layout is not height-priced the way the
Duet is: the panel simply shows what its rules in section 3 allow (strips displace model caps, never the
Weekly row).

### 1.4 What each text line says (single account)

Headline window: the 5h window when the account has one, **else the Weekly window** (CCRM-54 (ChatGPT
Account): a ChatGPT Plus or Pro account has no 5h window). When the headline is Weekly the label says
"Weekly", pace is measured over 7 days, and the panel drops its Weekly row so the same number is not
printed twice.

Figure text: Used mode, the percentage truncated ("99.7" shows "99%"); Left mode, the remainder floored
("99.7 used" shows "0%"), never negative. No reading shows "—". The warning ladder and fills always key
on the *used* percentage, whatever the figure says.

| State | Figure | Bar | Sub line (collapsed) | Sub line (expanded) |
|---|---|---|---|---|
| Quiet, countdown leads | "42%" in severity colour | fill, pace tick, red tail if above pace | "Resets in 2h 10m · 6:40 PM" | same |
| Quiet, clock leads | same | same | "Resets at 6:40 PM · in 2h 10m" | same |
| Reset under 5 minutes | same | same | "Resets soon · 6:40 PM" (countdown form) | same |
| Not started: window exists but has no reset instant, or no window at all | "—" | bare track, no tick | "Not started yet" | "Not started yet" |
| No reading yet (nothing ever fetched) | "—" | bare track, no tick | "Not started yet" | "Not started yet" |
| Sign-in broken (re-auth needed) | the **last cached** figure (the single layout keeps showing it) | last cached | the red strip: "●  Sign-in stopped working" (dot in fixed red) | reset line; the strip is in the panel |
| Stale (polls running, none succeeded for 6 h) or a Free-plan fault | figure at **50% alpha** | bar at **45% alpha** | strip: "●  Stale — nothing since 6:00 AM" | reset line; strip in the panel |
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
| Nominal width | 340 dp for single bars and panel rows; 156 dp for a Duet collapsed half; the ImageView stretches it (`fitXY`), so a few percent of residual stretch is expected |
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

### 2.1 Width and height budget

| Quantity | Value | Arithmetic |
|---|---|---|
| Collapsed half width | **156 dp** | 360 dp card - 2 x 16 dp system padding = 328 dp; minus one 16 dp gutter = 312; split two ways. The gutter is a start margin on Second so halves stay equal. A skin giving the full card width makes halves 172 dp; real halves run from about 101 dp (Fold 7 inner shade) to 156 dp, which is why the layout is built for the tight case |
| Collapsed content height | about 52 dp | label line about 14 dp, 1 dp gap, figure about 35 dp (30 sp), plus 1 dp padding each side; collapsed custom views get about 48 to 64 dp depending on skin |
| Collapsed label ceiling | 138 dp, 127 dp with one dot, 116 dp with two (never below 24 dp) | 156 - 14 mark - 4 gap - 11 per dot (6 dp dot + 5 dp margin); on narrower halves the layout's weight ellipsizes against the real width |
| Expanded cap | about 256 dp | Android's limit on an expanded custom view |
| Expanded header chrome | 136 dp | two header blocks of 58 dp (label 19, bar 13 + 4 margin, sub 16 + 4 margin; the 36 sp figure fits inside), 10 dp between them, 10 dp before the panel |
| Expanded panel budget | about 120 dp (Fold 7 about 136) | 256 - 136 |
| Panel pieces | Weekly row 43 dp (35 for the last); two rows 78 dp; one-line strip 38 dp | one strip plus two Weekly rows = 116 dp, the worst case that fits |

### 2.2 Anatomy, collapsed (`res/layout/notif_duet.xml`)

Two halves, each a **two-line** block (rev G: the label on its own line so the figure never competes with
it). 1 dp padding top and bottom.

| Element | Size | Colour / style | Rule |
|---|---|---|---|
| Provider mark | 14 dp, 4 dp gap | account accent | |
| Label | 12 sp, platform Title style, no font padding, 1 line, ellipsis, max width per 2.1 | platform title colour | the account label only; **no window name** collapsed |
| Condition dot | 6 dp, 5 dp margin left, after the label | fixed red (`#B3402A` light / `#E0705A` dark) for an account fault; the First account's accent for the app-global update strip | visible when that account has a sign-in, plan or stale fault; First also for an update strip. The dot is a pointer to the strip in the expanded panel, not a message. The expiry warning does not raise it |
| Synthetic dot | 6 dp, after First's condition dot | `#B388FF` | synthetic series on |
| Bar | 8 dp high ImageView (see 1.6 note), weight 1, 1 dp margin top | account accent, ladder above 80 | drawn at 156 dp nominal width |
| Figure | 30 sp bold, 8 dp margin left of the bar, no "LEFT" caption collapsed | severity colour | "42%" or "—" |

```text
 [mark] Personal  ●          [mark] ChatGPT
 [=====|-----------]  42%   [======|-------]  63%
```

### 2.3 Anatomy, expanded (`res/layout/notif_duet_expanded.xml`)

Two header blocks, 2 dp padding top and 4 dp bottom, Second 10 dp under First, then the compact panel.

| Element | Size | Rule |
|---|---|---|
| Mark | 14 dp, 4 dp gap | |
| Label | 14 sp, max width 200 dp, ellipsis | "<Account label> · 5h" or "<Account label> · Weekly" (headline promotion applies per account) |
| Condition dot | 6 dp, 5 dp margin left | as 2.2 |
| Bar | 13 dp high, 4 dp margin top, full width | drawn at 340 dp nominal |
| Sub | 12 sp, 4 dp margin top | the reset line (1.4), or "No reading yet" |
| Figure | **36 sp** bold, 14 dp margin left | severity colour |
| "LEFT" caption | 10 sp, letter-spacing 0.08 | Left mode and a reading exists, under the figure |

### 2.4 States (per half; the other half is unaffected)

| State | Collapsed | Expanded |
|---|---|---|
| Quiet | label, bar, figure | "Personal · 5h", reset line, figure |
| No reading, or sign-in broken | figure "—", empty track, **no tick**, red dot after the label (broken only) | sub "No reading yet" and the red strip. A broken sign-in is stricter here than on the single layout: no cached figure is shown |
| Stale | that half's figure at 50% alpha and bar at 45% | same; the other half stays crisp |
| Free-plan fault | red dot | red strip; the half is **not** dimmed (dimming keys on stale only on the Duet) |
| Headline is Weekly (no 5h window) | the weekly figure, label unchanged | label "ChatGPT · Weekly"; no Weekly row for that account in the panel |
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

One glyph for two accounts. "Status-bar ring shows" picks First, Second or "Whichever is higher"
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
| Row budget | 4 minus the number of strips (at least 1); Weekly first, then model caps | exactly the two Weekly rows (no model caps ever) |
| Pace on rows | measured over 7 days (all rows are 7-day surfaces) | same |

Rows:

| Row | Label | Value | Reset text |
|---|---|---|---|
| Weekly, single | "Weekly" (absent when the headline is Weekly) | "48%" or "—" | "Resets in 4d 3h · Tue 9:00 AM" or "Resets Tue 9:00 AM · in 4d 3h" |
| Weekly, Duet | "<Account label> · Weekly" (absent for an account whose headline is Weekly) | same | same |
| Model cap, single only | "<Model name>" (e.g. "Opus 4.6") | "22%" | none |

Rows exist only when the account has data. In Left mode the value is the remainder; the bar still draws
the spend.

Differs from the Duet approved in settings-diet-wireframe.html section 2 and its original strip cap of
two: CCBG-26 (Panel Scaling), approved 2026-09-10, cut the Duet to one one-line strip and removed model
caps; the code and `notif_duet_expanded.xml` comment line "falls to two" is stale, `Duet.maxStrips` is 1.

## 4. Status-bar ring and rails gauge (CCRM-83 (Ring Renderer), NEEDLE mark)

The notification's small icon. One window (the headline 5h one, or Weekly when that is the headline), one
account, no numbers. Drawn by `ui/RingRenderer.kt` (NEEDLE) via `ui/UsageIcon.kt`; the widgets and share
card share the same renderer with a different mark (TICK, specified in their own files).

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
| rails-gauge-wireframe.html | see section 4.3 |
| status-bar-gauge-wireframe.html | superseded twin-ring design; see 4.3 |
| notification-grouping-wireframe.html, fold-all-alerts-wireframe.html, notification-one-surface-wireframe.html | history of the strip mechanism; the strip set shrank (sign-in, stale/plan, expiry, update only) after CCRM-61 (Settings Diet), and strip type sizes are now 13.5 / 12 sp (single), 13 sp (Duet) |

## 9. Sources

| Part | Code | Approving wireframe / tracker |
|---|---|---|
| Pinned notification, both layouts | `notify/PinnedNotification.kt`, `res/layout/notif_big_number.xml`, `notif_big_number_expanded.xml`, `notif_duet.xml`, `notif_duet_expanded.xml`, `notif_panel_single.xml`, `notif_panel_duet.xml`, `notif_synthetic_wrap.xml`, drawables `notif_condition_dot`, `notif_strip_bg`, `notif_synth_dot`, `notif_synthetic_band` | design/settings-diet-wireframe.html (CCRM-62 (Duet Notification)), design/2026-09-26-v18-gate-fixes.html, design/duet-panel-fit-wireframe.html (CCBG-26 (Panel Scaling)) |
| Duet maths | `notify/Duet.kt` | same |
| Conditions | `notify/Conditions.kt`, `channel/UpdateChannel.kt`, `github/.../Channel.kt` | design/notification-one-surface-wireframe.html, design/fold-all-alerts-wireframe.html |
| Bars | `ui/BarRenderer.kt`, `ui/BarGeometry.kt`, `ui/Palette.kt` | design/bar-pace-marks-wireframe.html (CCRM-43 (Bar Pace Marks)) |
| Ring glyph | `ui/RingRenderer.kt`, `ui/RingGeometry.kt`, `ui/UsageIcon.kt` | design/rails-gauge-wireframe.html (CCRM-51 (Rails Gauge)), design/status-bar-glyph-legibility-wireframe.html (CCRM-49 (Glyph Legibility)), design/spent-ring-wireframe.html |
| Used or Left, Reset display | `ui/Palette.kt` (`Fmt`) | design/display-tokens-wireframe.html (CCRM-22 (Used or Left), CCRM-23 (Reset Display)) |
| Reset pings | `alerts/Alerts.kt`, `alerts/ResetRollover.kt` | BEHAVIOR-MOBILE.md section 2 |
| Settings rows | `SettingsScreen.kt` | design/settings-diet-wireframe.html |
