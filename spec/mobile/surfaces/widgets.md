# Widgets — Android home screen, iOS home / lock screen / Control Center / Live Activity

Covers every glanceable widget-type surface of the mobile family: the four Android home-screen faces (Ring, Number,
Countdown, Strip; CCRM-78 (Widgets Reborn) onward), and on iOS the home widgets (Number, Countdown, Bars, Strip,
Overview), the lock-screen widgets, the Control Center control and the Live Activity with its Dynamic Island
(CCRM-94 (iOS App)). It describes the apps **as shipped**; the approving wireframe for each part is linked, and where
a wireframe and the code disagree the code is documented with a "Differs from" note. **When you change a surface,
update this file in the same commit.** The always-on notification and the in-app screens have their own files in this
folder. Numbers that two platforms must share (severity, pace, projection) live in
[../../core/BEHAVIOR-SPEC.md](../../core/BEHAVIOR-SPEC.md), not here.

Conventions: Android sizes are dp (text sp), iOS sizes are pt. "ink" is the face's text colour. `path:line` points at the
code that decides the row; paths are repo-relative. Android's code lives under
`app/src/main/java/com/robin/claudeusage/widgets/`, abbreviated `W/`; `WidgetFace.kt` is the whole face logic.

Approving wireframes (all under `design/`):

| Part | Wireframe | Note |
|---|---|---|
| Android faces, states, config, gallery | `2026-09-25-widgets-reborn.html` (rev D) | The base; superseded in sizing by the two below |
| Android frame sizing | `2026-09-26-widgets-cover-refit.html` (rev F / F.1, CCBG-38 (Cover Buckets)) | Buckets are class thresholds, not exact cells |
| Android fill rule | `2026-09-26-widgets-fill-revh.html` (rev H, CCBG-44 (Widget Fill)) | Hero sized to the room; tiers S / T / W |
| Ring face history | `ring-faces-wireframe.html` (CCBG-10 (Mini-Rings Emptiness), CCBG-11 (Ring Face Clutter)) | Ring takes the room, words move out |
| Spent × at 100% | `spent-ring-wireframe.html` (CCRM-62 (Duet Notification); Cross chosen 2026-09-10) | Carried into the widget ring by CCRM-83 (Ring Renderer) |
| Pre-Reborn widget faces | `widget-wireframes.html` (rev 2) | **Superseded** by CCRM-78; see Differences |
| iOS rev B (Number, Countdown, Strip, lock, control, Live Activity) | `2026-10-23-ios-wireframes.html` + `.js` | Approved 2026-10-23 |
| iOS rev C (Bars, Overview tiers) | `2026-10-24-ios-widgets.html` | Approved 2026-10-24; Ring dropped |
| iOS lock-screen circular options | `2026-10-24-ios-lock-circular.html` | Pick not recorded; see Differences |

---

# Part 1 — Android home-screen widgets

## 1.1 The suite

Four providers, each placeable from the launcher picker, each drawing one face. **Never a combined figure.** Provider
class names and the `widget_prefs` keys are permanent (a rename deletes users' placements); everything else is free to
change.

| Face | Provider class | Picker target cells | Min size / min resize (dp) | Shows |
|---|---|---|---|---|
| Ring | `RingWidgetProvider` | 2×2 | 110×110 / 40×40 | One account's 5h ring, its Weekly as a bar under it |
| Number | `NumberWidgetProvider` | 4×1 | 250×40 / 110×40 | One account's 5h ring at the left of its Weekly figure, bar and reset; account cycler on the tall sizes |
| Countdown | `CountdownWidgetProvider` | 2×1 | 110×40 / 110×40 | A live count to the window's reset, with the absolute time; a 5h count leads with the 5h ring |
| Strip | `StripWidgetProvider` | 4×1 | 250×40 / 250×40 | Up to four accounts, each a 5h ring over a small Weekly bar |

**The shape grammar (v2.0, CCRM-103 (Hybrid Notification), `design/2026-11-02-shape-grammar.html` rev D §5, frozen
2026-11-02):** the 5h is always a hollow ring, a 7-day window always a bar, and no face writes "5h" (the ring's
TalkBack text says "5-hour window"). Every ring is `GrammarRing` (`ui/ShapeGrammar.kt`): band 0.13 of the diameter
(0.10 at 48 dp and under), the pace tick across the band, the red slice past the mark, the dashed room from the fill to
the tick (28 dp and up), the usage end mark (Appearance › Usage end mark), no projection arc. A spent week (CCRM-102
(Spent Week Gate)) draws the 5h ring grey. A plan with no 5h (B1) shows no ring: its Weekly figure stands where the ring
would be (Ring, Strip) or the face keeps its pre-grammar layout (Number). A plan with only a 5h never gets a 5h bar:
the ring alone, its Weekly slot left empty (Ring: no Weekly bar or row; Number: no figure or bar beside the ring — Fable
C5). Every figure is bold with tabular lining digits
(`fontFeatureSettings="tnum,lnum"`) and its "%" at 58% size (`Grammar.smallPercent`).

Source: `app/src/main/res/xml/{ring,number,countdown,strip}_widget_info.xml`; class names `W/WidgetHost.kt:21`.
All four: resizable both ways, `updatePeriodMillis` 0 (no periodic redraw), reconfigurable by long-press, a config
activity opens on add (no `configuration_optional`, CCBG-43 (Widget Settings Hidden)), category home screen.

**Picker previews** (`res/layout/widget_preview_{ring,number,countdown,strip}.xml`, static views and vector drawables
`widget_preview_ring_5h*`, `widget_preview_bar_*`; the picker inflates them with no code; Fable C6): a Solid, dark, S1
face in the shape grammar, filling whatever box the picker gives. Ring: the 5h ring (38%), "Pro", the Weekly row
("Weekly", bar, "20%"). Number: the 5h ring at the left (square, as tall as the box, ≤96 dp), "Pro · Weekly" with "20%"
(32 sp), the Weekly bar, "Resets Sat 9:10 PM" · "as of 6:29 PM". Countdown: the ring and the count share the width 2 : 3,
the count "2:41:07" auto-sized 10–24 sp, "at 9:10 PM", "Pro". Strip: Pro 38% and Work 62% rings, ChatGPT's Weekly "20%"
where its ring would be (B1), each over its Weekly bar, then the name (10 sp). No window chips anywhere. Rings are drawn
as `GrammarRing` draws them (track, fill, Line end mark, pace tick behind the fill, so no dashed room); bars as
`BarRenderer` (track, square fill, Line end mark, 3 dp tick in its halo).

## 1.2 Per-widget configuration

Stored per widget in `widget_prefs` as `w<id>.account`, `.window`, `.bg`, `.v` (plus `.drawn`). Source: `W/WidgetPrefs.kt`.

| Setting | Choices | Default | Applies to | Notes |
|---|---|---|---|---|
| Account | Any signed-in account (chips with the provider mark, in the account's accent) | **Unassigned**: the face draws the first account in registry order and says "tap to choose account" | Ring, Number, Countdown; **not Strip** (the Strip carries every account) | Save is the only thing that assigns. A deleted account becomes S9 |
| Window | **5h** / **Weekly** | v2.0 (CCRM-96 (Weekly First)): the account's Headline — Weekly, or 5h when the Headline is 5-hour (Auto starts on Weekly); the chip follows the account pick until touched, and a widget placed without config stores the window of its first draw. Widgets drawn before v2.0 keep theirs (one never configured keeps 5h). Before v2.0: 5h | Countdown. Stored for every face (the keys are permanent), but since the shape grammar the **Ring always draws the 5h** and the **Number always the Weekly** (both windows show), so the stored window is ignored there (Q "Ring widget's window choice"); the Strip has no window | **Countdown:** chips, or a note in their place when the account has one window ("<name> has one window — no control needed."); a weekly-only account is always Weekly (S4). **Ring and Number:** no Window row at all (it would be a dead control — Fable C6, `windowRowShown`); the pref is still saved as before (the chip's starting value) and ignored. **Strip:** the row carries its note |
| Background | **Solid** / **Gradient** / **Transparent** | Solid | All four | See 1.3 |

On-face controls (Number only, tall sizes): an account cycler writes the account pref without opening the config; see
1.7. (The 5h | Weekly chips went with the shape grammar.)

### Config activity (`W/WidgetConfigActivity.kt`)

Opened on add, or by long-press → "Widget settings". Result is cancelled until Save, so backing out of the add flow
removes the widget and backing out of a reconfigure changes nothing.

| Element | Size · rule | Copy |
|---|---|---|
| Title row | Back arrow 22 dp, title 17 sp semibold, 16 dp side padding | "Add widget" (new) or "Widget settings" (placed; decided by `placed()`, CCBG-48 (Reconfigure Label)) |
| Live preview | The very render the launcher gets, at the widget's current frame, scaled down only if wider than the screen; omitted if it fails to draw | — |
| Row labels | 11 sp, uppercase, letter-spacing 0.66 sp, 65% ink; 14 dp vertical padding; 1 dp divider at 8% ink | "ACCOUNT", "WINDOW" (Countdown and Strip only), "BACKGROUND"; on the Strip "Account — not shown, the Strip carries every account" |
| Account chips | 34 dp tall, 17 dp radius, 14 dp horizontal padding, 8 dp gap, wrap; selected = filled with the account's accent, unselected = 1 dp outline | label = account name; 14 dp provider mark leads |
| Window chips | Same chip, accent of the chosen account; Countdown only (no Window row on the Ring and Number) | "5h", "Weekly"; Strip note "Fixed to each account's headline window — no control here." |
| Background chips | Same chip | "Solid", "Gradient", "Transparent" |
| Primary button | 44 dp tall pill, 22 dp radius, accent fill, 14.5 sp bold, 16 dp margin | "Add widget" or "Save changes" |

Picks survive a fold/unfold mid-config. Saving redraws that widget, re-arms the transition alarm and returns OK.

## 1.3 Shell, colour and type shared by every face

| Element | Size · colour · rule | Source |
|---|---|---|
| Card (Solid) | The provider's Room card colour (Strip, which no account owns: neutral #1A1A1A dark / #FCF8F4 light); corner radius = the system's app-widget radius | `WidgetFace.kt:942`, `res/drawable/widget_bg.xml` |
| Wash (Gradient) | The account's accent over the card, top-left to bottom-right, at 15% alpha dark / 16% light; on the Strip and on any message face a neutral wash (white or black) at 6% | `WidgetFace.kt:942` |
| Transparent | No card; every text run carries a soft shadow in the opposite tone to the ink (on dark: #A6000000; on light: #CCFFFFFF); the grammar rings draw no shadow; bar tick halos and end-mark gaps are cleared instead of coloured | `res/values` `WFace.Shadow*` |
| Padding | x: 14 dp when both sides ≥200; 8 dp when width <90; else 12. y: 14 dp when both sides ≥200; 8 dp when height <100; else 12 | `WidgetFace.kt:97` |
| Ink | #EDE8E4 dark / #26211E light, at the alphas below | `WidgetFace.kt:546` |
| Theme | Follows the **system** night mode, not the app's override | `W/WidgetHost.kt` (`systemDark`) |
| Provider mark | The real Claude / ChatGPT mark, tinted with the account's accent | `WidgetFace.kt:1599` |
| Severity colour | Accent up to 80%; yellow above 80; orange above 90; red at 100 (dark #FDD663 / #FFA726 / #FF5252, light #F9A825 / #F57C00 / #C62828) | `WidgetFace.kt:503` |
| Dim (S7, S8) | Figure at 50% alpha, ring or bar bitmap at 45%, name 50%, mark 50% | `WidgetFace.kt` (`DIM_*`) |
| Ring figure (grammar) | Real text over the ring bitmap, `GrammarRing.figureDp` (0.25 of Ø, four characters 0.21; 0.29 / 0.24 at 48 dp and under) fitted to the bore (`fitFigure`), in dp; full ink, the severity colour above 90%, half ink with no reading ("—"), 45% ink on a spent week | `WidgetFace.kt` (`ringInto`) |
| Bar figure | The severity colour; "—" with no reading | `WidgetFace.kt` (`figure`) |
| Vs pace colour | The figure is the unsigned distance to the pace mark: green under it (#9CCC65 dark / #2E7D32 light), red over (#FF5252 / #C62828), ink on it; the usage figure's own colour when it falls back (rev D §7b) | `Grammar.vsColor` |
| Clock text | Always 12-hour "9:10 PM" whatever the app's Time format says; weekday added only when the instant is more than 24 h from now ("Sat 9:10 PM") | `ui/Palette.kt:285` |
| Stamp ("as of 9:10 PM") | 10 sp, 55% ink, in flow, never overlaid | `WidgetFace.kt:1616` |
| Secondary text alpha | Reset / sub line 78% (50% dim); countdown caption 75% (50% dim); "LEFT" caption 75–80% | `WidgetFace.kt` (ring/number/countdown face functions) |
| Synthetic marker (S13) | Frames 160 dp tall or more: a full-width 12 dp violet (#7C4DFF) ribbon "● SYNTHETIC", 6.5 sp white bold, taking 12 dp off the inner height; shorter frames: a 7 dp violet dot, top-end, 5 dp margin | `WidgetFace.kt:942`, `res/layout/widget_*.xml` |
| Tap | Opens Cooldown on the account shown (Strip: opens the app as it was). An S9 or unassigned face opens **its config** instead | `W/WidgetHost.kt:335` |
| TalkBack | One description per face: "<name>, <5-hour window or Weekly>, <38 percent used / 62 percent left / 10 percent under pace / no reading>, <sub line>", then the companion window the same way; accounts joined by "; " | `WidgetFace.kt` (`describe`) |

### Pace marks

| Mark | Geometry | Source |
|---|---|---|
| Ring (grammar) | `GrammarRing.paint`: track 16% ink (22% grey), fill from 12 o'clock in the severity step, 9% minimum fill; the pace tick a radial line across the band (max(1.5 dp, Ø/32), 90% ink); the red slice from the tick to the tip when more than 3 points ahead (and "red past the pace mark" is on); the dashed room from the fill's end to the tick when under it (28 dp and up); the usage end mark per Appearance (Round / Line / Pace colour), its gap in the card colour (cleared on Transparent). At 100% the figure reads "100%" (no cross on a widget). Spent week: the grey track alone | `ui/ShapeGrammar.kt` |
| Bar tick | 3 dp wide, 90% ink, full bar height, a 1 dp halo in the card colour; centred on the pace point so it straddles the boundary | `WidgetFace.kt` (`barTick`), `ui/BarRenderer.kt` |
| Bar red | Same dead-zone rule; red from the tick to the fill tip | `ui/BarGeometry.kt:55` |
| Bar end mark | Per Appearance › Usage end mark: Round (round fill end), Line (square fill end and a 1.5 dp line, the default), Pace colour; a 1 dp gap in the card colour | `ui/BarRenderer.kt`, `GrammarBar.paintEndMark` |

Ring and bar are bitmaps drawn at the frame's size and never scaled; all text is real `TextView`s at true sp.

## 1.4 Frames, buckets and tiers

The launcher reports the real frame(s) in dp (a Fold reports its cover and its inner frame). One layout (bucket) is
picked per face by class thresholds, the geometry then follows the frame. A composed update holds at most four frames,
smallest first, while its bitmaps stay under 4 MB (largest frames dropped first); each frame is keyed 2 dp under
itself. Before any frame is reported, each bucket is drawn at the key below. Source: `W/WidgetHost.kt:156,196,258`;
`WidgetFace.kt:56,128,541`.

| Bucket | Design size (cover cells, dp) | Chosen when | First-update key (dp) |
|---|---|---|---|
| Ring 1×1 | 91×84 | not (width ≥140 and height ≥140) | 84×84 |
| Ring 2×2 | 181×168 | width ≥140 and height ≥140 | 140×140 |
| Number 2×1 | 181×84 | width <240 | 140×84 |
| Number 4×1 | 363×84 | width ≥240, height <150 | 240×84 |
| Number 4×2 | 363×168 | width ≥240, height ≥150 | 240×150 |
| Countdown 2×1 | 181×84 | not (width ≥140 and height ≥150) | 140×84 |
| Countdown 2×2 | 181×168 | width ≥140 and height ≥150 | 140×150 |
| Strip 4×1 | 363×84 | height <150 | 244×84 |
| Strip 4×2 | 363×168 | height ≥150 | 244×150 |

Reference frames measured on the Fold 7 (reported dp): cover columns 1/2/3/4/6 = 84 / 156 / 244 / 333 / 510 wide, rows
108 / 237 / 366 tall; inner grid 2×2 211×291, 2×1 211×126, 4×1 467×126 (ROADMAP CCRM-78). Rev F's own table used
90.75×84 dp cover cells (4×2 = 363×168); that is the design size above, not what One UI reports.

**Tiers inside a bucket** (rev H, `WidgetFace.kt:117`): height <140 → **S** (S-narrow if width <240, S-wide otherwise);
else width/height <1.35 → **T** (tall); else **W** (wide). **Roomy** type applies once the inner height reaches 200 dp
(name 15, sub 13, caption 12). "Tall frame" for the synthetic marker is height ≥160.

## 1.5 The state ladder (S1–S14)

Defined in ROADMAP "The states every face draws"; decided at draw time from the cache, never from a redraw arriving.
Source: `WidgetFace.kt:371` (`FaceStates.cell`). A cell can carry several states at once.

| State | Trigger | What the reading shows | Copy (exact) |
|---|---|---|---|
| S1 normal | A reading with none of S2/S3/S5/S6 | Figure in the severity colour; ring/bar with pace tick | Sub line "Resets 9:10 PM" ("Resets Sat 9:10 PM" beyond 24 h) |
| S2 above pace | percent > elapsed + 3 and <100 | S1 plus the red slice/bar from the pace tick to the tip; Countdown 2×2 adds the estimate when one exists | "~ runs out 4:20 PM" |
| S3 100% | percent ≥100 (truncated) | The figure is "100%" in red, on a ring too (the grammar ring draws no cross); no estimate | — |
| S4 no 5h window | Account has Weekly only | Window forced to Weekly; names tagged; no 5h ring (B1): its Weekly figure where the ring would be (Ring, Strip), the pre-grammar face (Number), today's Weekly countdown (Countdown) | Name line "… · Weekly" |
| S5 not started | Account known, window absent or no reset clock | **No reading**: the extent alone, figure "—", never 0%; no tick | "Starts when a message is sent"; Strip "Not started" |
| S6 reset passed | reset time ≤ now and last fetch before it | As S5 (extent only, "—") with the past-tense reset | "Reset 9:10 PM"; Countdown headline "Reset 9:10 PM" |
| S7 sign-in broken | Re-auth needed | Last snapshot dimmed; a 6 dp red dot after the name (#FF6B6B dark / #C62828 light) | — |
| S8 stale | Last fetch ≥6 h ago | Dimmed | Sub "Stale"; Countdown caption "Stale"; Strip short line "Stale"; stamp still "as of 9:10 PM" |
| S9 account removed | Saved account no longer exists | Whole face replaced by a message; tap opens config | "Account removed · tap to choose" (Ring: "Account removed" over a smaller 75% "tap to choose") |
| S10 unassigned / no accounts | No saved account (draws the first), or no accounts / never signed in | Unassigned: a normal reading plus the stamp slot reading "tap to choose account" (Number 2×1: an "UNASSIGNED" pill, 8 sp; Countdown 2×1 caption gains " · tap to choose"). No accounts: message | "Open Cooldown to sign in" (Ring: two lines "Open Cooldown" / "to sign in") |
| S11 Left mode | "Usage display: Left" setting | Figures flip ("62%" instead of "38%"); bars and rings do not flip | Name lines gain " · left"; a small "LEFT" caption beside the figure where room allows |
| (v2.0) Vs pace | "Usage display: Vs pace" (CCRM-100 (Vs Pace Display)) | Figures show the **unsigned** distance from the pace mark, "10%" green under it, red over it, ink at "0%" (shape grammar rev D §7b), falling back to usage (its severity colour) when spent or unclocked; bars and rings do not change | Wherever Left mode adds " · left" (Ring and Countdown name lines, the Number 4×2 label, Strip labels) a distance adds " · vs pace" instead (`design/2026-11-02-vs-pace-display.html` §4: "as Left already does"); none when the figure falls back to usage. "10%" is no wider than "100%", so figures need no fit change; labels truncate as before |
| S12 Free plan | Provider reports no usage on the plan | Whole face replaced by a message (Strip: that one ring shows the empty extent, its name and "Free") | "No usage on this plan" (Ring: "No usage" / "on this plan"); Strip "Free" |
| S13 synthetic | Process-wide synthetic series is on | Marker per 1.3 | "● SYNTHETIC" |
| S14 unavailable | A face pulled in a later version (switch exists, off in v1.8) | Recovery message | "Update Cooldown" (Ring 1×1: "Update" / "Cooldown"; 2×1: same one line); "Unavailable in this version · update Cooldown" on wider faces (Ring 2×2: two lines) |

Message sizes: Ring 1×1 9 sp (S14 9.5), other Ring buckets 11.5 sp (S14 11), Number 11.5 sp, Countdown and Strip 11 sp,
all full ink. Source: `WidgetFace.kt:966,986`.

Window rules: the face shows the asked window unless the account has only Weekly (then Weekly) or the asked Weekly
does not exist (then 5h), `WidgetFace.kt:468`. A reset passing is never shown as 0%: it is "no reading".

## 1.6 Ring

Anatomy (every element), sizes from the formulas in 1.6.1.

| Element | Size · colour · rule |
|---|---|
Always the **5h** (the stored window is ignored, Q "Ring widget's window choice"); the Weekly as a bar under it — rev H's
companion ring became that bar (rev D §5 `awRing22`, `awRing11`).

| Element | Size · colour · rule |
|---|---|
| Ring bitmap | `GrammarRing` at Ø per layout (band 0.13 Ø) |
| Bore figure | Per 1.3 "Ring figure (grammar)"; "100%" at 100% |
| Weekly bar (1×1 column) | The Weekly bar alone, as wide as the ring, 5 dp tall, 5 dp under the ring; no digits |
| Weekly row (every other layout) | After the name line, 4 dp above: "Weekly" 11 sp at 78% ink (50% dim), 6 dp gap, the 6 dp Weekly bar, 6 dp gap, the Weekly figure 13 sp bold in its bar colour; the row is max(150, Ø) dp wide (never wider than its room), the bar takes what the words leave |
| Name line | Provider mark + name (+ " · Weekly" for a weekly-only account, + " · left" in Left mode, + " · vs pace", + " · not started" under S5 when no reset line can say it); 13 sp (15 roomy); wraps nowhere, truncates; 6 dp red dot after it under S7 |
| Reset line | The sub (see 1.5; "Weekly spent" while the week is spent) at 12 sp (13 roomy), 78% ink (50% dim) |
| Stamp line | "as of 9:10 PM", or "tap to choose account" when unassigned; 10 sp 55% |
| No 5h (B1) | No ring: the Weekly figure at 0.4 Ø dp in its bar colour where the ring would be, then the same Weekly bar / row |
| No Weekly (5h only) | The ring and its lines; no Weekly bar (1×1) or row — its room stays empty, so the ring is the same size |
| Corner dot | S7 on a ring with no lines: red dot in the top corner |

### 1.6.1 Sizes and drop rules

| Layout | Frame | Layout rule | At design size |
|---|---|---|---|
| Ring 1×1 | 91×84 | Ring over its Weekly bar: Ø = min(92, inner width, inner height − 16); a frame ≥90 dp wider than the ring + 12 dp (3×1 and up) puts the lines and the Weekly row **beside** a ring of min(92, inner width, inner height), the stamp dropping when name, row, reset and stamp do not fit | **Ø52** over a 52×5 dp bar (rev D §5) |
| Ring 2×2, tier W | wide, height ≥140 | Ring Ø = min(160, inner height) (reduced so ≥90 dp remain for the lines); name, Weekly row, reset, stamp beside it, 16 dp gap | — |
| Ring 2×2, tier T | tall | Lines centred under the ring; ring Ø = min(160, inner width, inner height − the lines' reserve − 22 dp for the Weekly row) | **Ø97**, name, Weekly row (150 dp) (rev D §5's Ø96) |

**Drop order (Ring T):** start with 3 lines (name · reset · stamp) plus the Weekly row; while the ring would be under
Ø100, drop the stamp, then the reset line, until only the name (and the row) is left. The reserve under the ring is 1 +
ceil(6 + 1.34 × (name + reset + 10) + 4) dp for 3 lines, ceil(6 + 1.34 × (name + reset) + 2) for 2, ceil(6 + 1.34 ×
name) for 1, plus 22 for the row. The row's room is kept whether or not the account has a Weekly, so the ring is one size
for every account. Source: `WidgetFace.kt` (`ringLayout`, `ringFace`).

Taps: no chips on the Ring. S5 on a Ring that has dropped its reset line says "· not started" in the name.

## 1.7 Number

The shape grammar's tile (rev D §5 `awNum41`, `awNum21`): the **5h ring at the left of the Weekly** label, figure, bar
and reset. Both windows show at once, so the window chips went and the stored window is ignored. A weekly-only account
(B1) has no ring and keeps the pre-grammar face (label and figure on one row on 2×1, "Weekly" as its second line). An
account with only a 5h (Fable C5) is the ring alone: the label is the name alone at every size (no face writes "5h"),
the sub row (the 5h's reset), stamp and controls as usual, and the Weekly slot — figure and bar — laid out but empty, so
the face keeps the two-window geometry and never draws a 5h bar.

| Element | Size · colour · rule |
|---|---|
| 5h ring | `GrammarRing`, Ø per table, vertically centred, 12 dp before the Weekly column (8 on 2×1); figure per 1.3 |
| Mark + label | 13 sp (14 on 4×2 and the tall 2×1; 15 roomy), full ink (50% dim); label = "<name> · Weekly" (Q "The widget mode header": mobile's labels), the name alone on a 2×1 beside the ring (rev D §5) |
| Figure | The Weekly, severity colour (Vs pace colour per 1.3), bold, tabular, small "%"; sp per table; "—" with no reading |
| "LEFT" caption | 10 sp bold, 75% ink, beside the figure, Left mode only |
| Bar | The Weekly; track = the column's width (the inner width without a ring); height 8 / 10 / 12 / 14 dp per table; pace tick and end mark per 1.3 |
| Sub row | The Weekly's sub line (78% ink, 13 sp roomy) + stamp ("as of …" or "tap to choose account") |
| Controls (tall sizes) | The account cycler only: in the 32 dp control row on 4×2, on its own row on the 2×2 (whose old chip row is gone) |
| Cycler | "<name> ⇄" (⇄ at 60% ink), mark, 8% ink plate |
| Pill | "UNASSIGNED", 8 sp, on an unassigned 2×1 only, and beside the ring only when the frame is ≥100 dp tall (card colour text on ink) |

### Sizes

Ring Ø = min(96, inner height − 8 (4×1) / − 16 (2×1) / − 40 (4×2, 2×2), 0.4 × inner width), at least 28. Column = inner
width − Ø − gap.

| Bucket · design frame | Inner (dp) | Layout | Figure sp | Bar | Rows shown |
|---|---|---|---|---|---|
| 2×1 · 181×84 | 157×68 | **Ø52**, column 97: name, figure under it, bar (rev D §5) | min(column/2.4, (inner height−35 (−13 with the pill))/1.17) clamped 20–44 → **28** | 8 (10 when figure ≥40) | ring, name, figure, bar. **No sub row, no stamp, no controls** |
| 2×1 tall = "2×2" (inner height ≥205) | — | Ring at the left; label stacked, figure hero, then bar, sub, stamp (own line), cycler (own row) | min(column/2.4, 72, (inner height−134)/1.17), floor 20 | 12 | everything |
| 4×1 · 363×84 | 339×68 | **Ø60**, column 267: one row of label + figure; bar; sub row (rev D §5) | ((inner height−36)/1.1) clamped 32–44 → 32 | 8 (10 if figure ≥40) | + sub row and stamp |
| 4×2 · 363×168 | 339×144 | Ø96, column 231: label + figure row, bar, sub row; control row under the whole face | by width (column−94)/2.4 and height (inner height−73)/1.17, clamped 44–72 (96 when inner height ≥300) → **57** | 12 (14 if figure ≥64) | everything; control row 32 dp, cycler 26 dp tall |
| 4×2 stacked (inner width <260 and height ≥180, the 3×2) | — | As 2×2: label alone on its row; the "left" word moves into the label | column/2.4, height−93; min 32 | 12 | everything |
| B1 (no 5h) | — | Rev H's layouts on the whole inner width: 2×1 label and figure on one row (32 sp), stacked under 150 dp inner width with ≥100 dp height, compact (name alone) under 150 dp and <100 dp | as rev H | as rev H | as rev H |

Label width is measured: the label gets what the measured figure (+ "LEFT") leaves in the column, floor 24 dp.

**Drop order as space runs out:** controls (below 4×2 / tall 2×1) → sub row and stamp (below 4×1) → the "LEFT" caption
(below 4×1, or stacked) → the pill (a 2×1 beside the ring under 100 dp) → the window word (a 2×1 beside the ring names the
account alone).

Controls (4×2 and tall 2×1 only, not on a message face): the **cycler** steps to the next account in registry order (the
first when unassigned), wrapping, storing the account key. It goes to a private receiver with data
`cooldown-widget://<id>/<action>`, so one widget's tap never moves another (the receiver still accepts the old window
actions from a stale face). Source: `W/WidgetActionReceiver.kt`, `WidgetFace.kt` (`numberFace`), `1.3` for the rest.

Differs from rev D (`2026-09-25-widgets-reborn.html`): rev D's fixed 32 / 44 sp figures became fills (rev H); the 2×2
Number (`numberTall`) is new in rev H; the ring, the Weekly-only figure and the chips' removal are the shape grammar's.

## 1.8 Countdown

The shape grammar (rev D §5 `awCd21`): a **5h** countdown leads with the 5h ring in place of the "5h reset" caption,
"at 9:10 PM" under the count and the account line under that, all in the column beside the ring. The 5h is never a bar,
so a 5h countdown draws no side bar (wide 2×1) and on the 2×2 no figure row and no bar (the ring carries the figure; the
estimate keeps its own line when there is room). A **Weekly** countdown keeps the face below unchanged. A frame too narrow
for the ring and an 18 sp count (inner width under 106 dp: the 110 dp minimum) keeps the caption face.

**The 5h 2×2 hero** (CCBG-66 (Countdown Ring Small), Robin 2026-11-02 "Scale the ring up"): on a 2×2 frame that is not
wide (tier T; a tier-W 2×2 keeps the ring beside the count) the count and "at 9:10 PM" sit at the top of the full inner
width, the 5h ring is centred under them (`cd_hero_box`, 6 dp above it) between two equal gaps, then the estimate if
kept, and the account line at the bottom. The ring takes the height the other rows leave, as the Ring face's does.

| Element | Size · colour · rule |
|---|---|
| 5h ring | `GrammarRing` Ø44 (Ø = min(44, inner width − 78), at least 28), 8 dp before the column, vertically centred; figure per 1.3. On the 2×2 hero: Ø = min(160, inner width, inner height − reserve), where reserve = ⌈2 + 1.2 × count + 3 + 1.34 × "at" sp + 6 + 4 + 1.34 × 12 + 2⌉ (+ 2 + 1.34 × estimate sp with the estimate) |
| Estimate under the hero | Kept while the ring with it is still ≥ Ø64 (the Ring 1×1's and Strip 4×2's ring); below that it goes and the ring takes its room |
| Caption (Weekly, or no room for the ring) | "Weekly reset" / "5h reset" (never "Resets in"); "Stale" at S8; 11 sp default (12 roomy), 75% ink (50% dim); on an unassigned 2×1 "… · tap to choose" |
| Count (live) | A launcher-ticked chronometer counting down to the reset: H:MM:SS, MM:SS under an hour, a negative duration past zero; bold; sp per table; severity-neutral full ink (50% dim) |
| "at 9:10 PM" | The absolute reset time beside or under the count, 78% ink; always on the face so the count stays readable when ambiguous and true after zero |
| Absolute headline (ABSOLUTE) | A Weekly reset more than 24 h away is not counted: the clock "Sat 9:10 PM" at the count's size (live count begins at 24 h) |
| Past tense (S6) | "Reset 9:10 PM" at 80% of the count's size, no count |
| No count (S5, no reading) | Count row replaced by the words "Starts when a message is sent" / "No reading yet" (under 120 dp inner width: "Not started" / "No reading"), full ink |
| Account line | Mark + "<name> · Weekly" on a Weekly count, the name alone on a 5h count (+ " · left", " · vs pace"), 100% ink (50% dim), 6 dp red dot under S7. Beside the ring it is 12 sp (rev D §5) and also says what the caption said: " · Stale" at S8, " · tap to choose" when unassigned (2×1 and 2×2) |
| Stamp | "as of …", 10 sp 55%; left off when it and the account line do not fit one row (beside the ring, the column) |
| Side column (wide 2×1, ≥108 dp tall, Weekly only) | Figure 24 sp over an 8 dp bar, only when ≥95 dp spare beside count and "at" text; width min(160, spare) |
| 2×2 extras (Weekly) | Figure row (figure 18 sp, 32 when inner height ≥180; "LEFT" caption 10 or 8.5 sp), estimate (12 sp roomy, else 11, 78% ink) beside the figure or on its own line when it will not fit, bar 8 dp (10 when count ≥60 sp), stamp |
| 2×2 extras (5h) | The estimate on its own line (under the hero only when kept, above), the stamp ("as of …"; unassigned says it in the account line) |

### Sizes

| Bucket · design frame | Inner (dp) | Count sp | Rows shown |
|---|---|---|---|
| 2×1 · 181×84, 5h | 157×68 | Ø44, column 105: min(column/4.3, (inner height−39)/1.17), clamped 18–44 → **24** (rev D §5) | ring; count, "at …" (11 sp, always under), account line |
| 2×1 · 181×84, Weekly (S-narrow) | 157×68 | min(inner width/4.5, (inner height−37)/1.17), clamped 20–44 → **26** | caption, count + "at …" (inline, 11 sp under the count when it will not fit and height ≥100), account line |
| 2×1 wide (S-wide), Weekly | wide | min(44, (inner width−66)/4.5, (inner height−37)/1.17), min 20 | + side column when it fits; "at" at 12 sp |
| 2×2 · 181×168, 5h (hero) | 157×144 | min(inner width/4.5, inner height × 0.2), clamped 20–56 → **28**; ring **Ø61** (estimate dropped: Ø44 with it) | count, "at …" (12 sp, 14 roomy), ring, account line, stamp |
| 2×2 · One UI 156×237, 5h (hero) | 132×213 | **29**; ring **Ø108** with the estimate | count, "at …", ring, estimate, account line, stamp |
| 2×2 wide (tier W), 5h | wide | Ø44 beside the column: min(column/4.3, (inner height−70)/1.17), clamped 18–80 | ring; count, "at …", estimate, account line, stamp |
| 2×2 · 181×168, Weekly | 157×144 | min(inner width/4.5, inner height−118), clamped 28–80 → **28** | caption, count, "at …" (always below), figure row, estimate, bar, stamp, account line |

The past tense ("Reset 9:10 PM") steps down from 80% of the count until it fits the column, never under 18 sp (12 beside
the ring).

Drop order: the side column and 2×2 rows go below 2×2; the stamp goes when the account line needs the row; "at 9:10 PM"
drops under the count on a narrow frame with ≥100 dp of height; the estimate drops under the figure on a narrow tall frame;
the 2×1 on a short frame has caption, count, account line only. A weekly count never ticks until 24 h out (the transition
alarm fires at reset − 24 h). Source: `WidgetFace.kt` (`countSp`, `cdRing`, `cdHero`, `cdHeroEstimate`, `countdownFace`).

## 1.9 Strip (Accounts Strip)

Up to four accounts in registry order, then "+N" when there are more. No account setting, neutral card. The shape
grammar (rev D §5 `stripCell`): **each account is a 5h ring over a small Weekly bar** — the bar alone at 4×1 (Q "Strip
4×1 Weekly digits": no room for its figure; the bar keeps its tick), the bar and its figure at 4×2. A weekly-only account
(B1) shows its Weekly figure where the ring would be, over the same bar. (v2.0's per-account Headline window for the ring
is superseded: rings are always the 5h.)

| Element | Size · colour · rule |
|---|---|
| Ring per account | `GrammarRing`, Ø per sizes |
| Bore figure | Per 1.3 "Ring figure (grammar)" |
| No 5h (B1) | The Weekly figure at 0.4 Ø dp in its bar colour where the ring would be |
| Weekly bar | 3 dp under the ring: 4×1 a bar as wide as the ring, 4 dp tall, no digits; 4×2 a row (cell − 8 dp wide) of a 5 dp bar and the Weekly figure 11 sp bold in its bar colour, 4 dp apart |
| Name | Mark + name (+ " · left", " · vs pace"); 10 sp on 4×1 (12 roomy; rev D's 9.5 before the grammar), 11 sp on 4×2 (10 once a cell is under 80 dp, 13 when Ø ≥100); 6 dp red dot under S7 |
| Short reset (4×2) | Under the name, 10 sp (11 at Ø ≥100), 72% ink (50% dim): "9:10 PM", "Reset 9:10 PM", "Stale", "Not started", "No reading", "Free" |
| Inline reset (4×1) | "Personal · 9:20 PM": the time at 10 sp 72% after the name, only when a cell's share is ≥ Ø+70 dp |
| Free ring (S12) | The empty track and the name, no Weekly bar; "Free" in the bore at 70% ink (4×1) or under the name (4×2) |
| "+N" | A cell of its own at 30 dp, the ring figure's size (`GrammarRing.figureDp`), 70% ink |
| Stamp (4×2) | "as of …" — the newest fetch among the shown accounts |

### Sizes

| Bucket · design frame | Inner (dp) | Ring Ø | Cell pitch | Rows |
|---|---|---|---|---|
| 4×1 · 363×84 | 339×68 | min(56, (inner width−6(n−1))/n, inner height−26) → **42** (rev D §5); roomy (height ≥100): min(64, …, inner height−30) | min(share, Ø+56; Ø+96 when the reset goes inline) → **84** | ring, Weekly bar, name |
| 4×2 · 363×168 | 339×144 | min(120, (inner width−8(n−1))/n, inner height−80) → **64** (rev D §5) | min(share, Ø+40) → **84** | ring, Weekly bar and figure, name, short reset (the 5h's; the Weekly's for B1), then the stamp |

A wide row becomes a centred group (the pitch cap), not rings spread to thirds. Drop order: stamp, short reset and the
Weekly figure go on 4×1 (the name remains, with the inline reset where it fits); the label's window word is never shown.
At S9 the account simply leaves the strip. Source: `WidgetFace.kt` (`stripRing`, `stripBar`, `stripFace`).

## 1.10 Refresh and transitions

No periodic redraw. One app-wide alarm re-arms on every provider callback and fires at the next of: a placed account's
reset (S6), last fetch + 6 h (dim), last fetch + 24 h + 1 ms (the stamp gains its weekday), and for a Weekly Countdown reset
− 24 h (ticking begins); a Ring, a Number and every Strip account count both windows (the 5h ring and the Weekly bar
draw together); a Countdown counts its own. It redraws all placed widgets from the cache and fetches nothing.
Boot, app update, clock change and time-zone change redraw all and re-arm. Source: `W/Transitions.kt`,
`W/WidgetSystemReceiver.kt`, `W/WidgetActionReceiver.kt`.

## 1.11 Faces gallery (debug)

Debug-only screen, reached from Settings → Debug. Inflates every face × every bucket at its design size × every state
S1–S14 through the same render call the providers use, so the two cannot drift. Source: `W/FacesGalleryActivity.kt`,
`W/GalleryTiles.kt`, `W/GalleryFixtures.kt`.

| Element | Rule |
|---|---|
| Title row | Back arrow, "Faces gallery" 17 sp semibold |
| Theme chips | "Dark" (default) / "Light"; 30 dp chips, 15 dp radius, 12 sp |
| Face filter | "All", "Ring", "Number", "Countdown", "Accounts Strip" |
| Count | "<N> tiles", 12 sp at 60% ink |
| Tile | A 1 dp outlined box at the bucket's design size (width and height in dp), 12 dp gutters, wrapping; caption 10.5 sp "Number 4×2 · S2 (above pace) · 31 KB" (bitmap estimate in KB); a render failure shows the exception text in red 9.5 sp under the tile |
| Fixtures | "Now" is a Thursday 6:29 PM (Asia/Kolkata); 5h resets 9:10 PM, Weekly Saturday 9:10 PM; accounts Pro 38%, Teams 24%, Product 8%, ChatGPT (weekly-only) 31%; on the Strip a state lands on the second ring, Teams |

---

# Part 2 — iOS widgets

All iOS surfaces read the App Group the app writes and **never fetch**. Sizes are pt. The widget card is the system
container (card colour `#1C1C1E` dark / white light, set with `containerBackground`), so the system margin is part of the
available size and is not specified here. The wireframes were drawn on the iPad 11″ canvas Robin runs (Display Zoom "More
Space"): Small 160×160, Medium 346×157, Large 346×346, Extra Large 724×346; the tightest phone case is the iPhone SE Small
at 141 pt (109 pt inside).

## 2.1 Shared pieces

| Piece | Rule | Source |
|---|---|---|
| Palette | Dark: card #1C1C1E, ink #F2F2F2, text #D1D1D6, secondary #98989F, yellow #FDD663, orange #FFA726, red #FF5252, tick #C7C7CC. Light: card white, ink black, text #3A3A3C, secondary #6C6C70, yellow #F9A825, orange #F57C00, red #C62828, tick #8E8E93. Widgets follow the widget's own appearance | `ios/Shared/Theme.swift` |
| Severity | Accent up to 80%; yellow above 80; orange above 90; red at 100 | `Theme.swift` |
| Accent | The account's own colour, else the theme colour, else the provider's (Claude terracotta #E59980 dark / #D97757, ChatGPT green #19C39A / #10A37F) | `ios/Shared/Display.swift`, `Theme.swift` |
| Provider mark | Real mark, tinted; Claude drawn at 82% inside its square to match ChatGPT's apparent size | `Theme.swift` |
| Used / Left | Setting "Usage display": a figure reads "62%" or "38%" with a smaller "left" (42% of the figure's size, min 10 pt, semibold) after it; bars never flip. Text-only surfaces say "38% left" | `Display.swift` |
| Clocks | Lower-case "9:50 pm" (or 24-hour per the Time format setting); Weekly clocks always name the day: "Thu 9:16 pm" | `ios/Shared/CardContent.swift:140,146` |
| Usage bar | Capsule, track = accent at 26%; fill min width = bar height (any reading above 0 shows); above pace (3 points over the even-pace line) and "red past the pace mark" on: fill to the tick in severity colour, then red from a straight edge at the tick to the tip; tick = 3 pt wide, bar height + 2 × max(3, 0.45 × height) tall, rounded 1.5 pt, in the tick colour; dimmed = 45% opacity; no reading = empty track, no tick | `ios/Shared/Charts.swift:6` |
| Usage ring | Track stroke max(1.5, 0.3 × line width) in ring-track colour (mono: 32% of white); fill round-capped; red arc from the tick; tick = capsule max(2, 0.42 × line width) × 1.9 × line width at the pace angle; label bold, mono-digit, default 0.22 × ring diameter | `Charts.swift:186` |
| Status | `fresh`; `aging` (older than 30 min: "as of" appears); `stale` (older than 6 h: dimmed numbers, orange "as of"); `signIn` (dimmed); `plan` (no usage); `noData` | `ios/Shared/Faces.swift:9,63,85` |
| Window reset | A window whose reset has passed reads **0%** (its old figure belongs to the closed window) until the next fetch | `Faces.swift` (`build`) |
| Weekly-only plan | Weekly moves into the main slot of every 5h-led surface ("Weekly" where "5h" would be), so nothing says "no 5h" | `Faces.swift:46` (`leading`) |
| Account tap | Every widget opens the app on its account (`cooldown://account/<key>`); Overview cards each link to their own account | `Faces.swift` (`DeepLink`) |
| Empty | No accounts: two provider marks (22 pt) over "Add an account in Cooldown", 13 pt, centred | `FaceViews.swift:80` |

### Footnote (the bottom line every face uses), in priority order

`FaceFootnote` (`FaceViews.swift:36`): 1 line, scales to 80%.

| Priority | Condition | Copy | Colour |
|---|---|---|---|
| 1 | signIn | "Sign in again" | orange |
| 2 | plan | "No usage on the Free plan" (Claude) | secondary |
| 3 | stale | "as of 9:50 pm" | orange |
| 4 | noData | "Open Cooldown to check" | secondary |
| 5 | aging (5h footnotes only) | "as of 9:50 pm" | secondary |
| 6 | Weekly footnote | "Resets Thu 9:16 pm" | secondary |
| 7 | at 100% with a reset | "Limit · resets 5:50 pm" | red |
| 8 | on a path to 100% before reset (projection) | "~ runs out 7:51 pm" | secondary |
| 9 | otherwise | "Resets 5:50 pm", or "No window running" | secondary |

Pace and projection: the projection only exists when usage is above pace by more than 3 points (`Faces.swift:75`). Pace
line copy (Overview, Line B): "On even pace", "<n>% below even pace", "<n>% above even pace" (red), "Limit reached"; narrow tiers
replace "even pace" with "pace"; projection short form "~ runs out 7:51 pm" or "~<n>% at reset" (`CardContent.swift:80-109`).

**Timelines:** one entry now, a second one second after the next reset (so a closed window reads 0%), refreshed after 30 min;
the app also reloads timelines after each fetch. Source: `ios/Widget/Widgets.swift:28,124`.

## 2.2 Home widgets, small

All three small widgets are `systemSmall`, configured by an account choice (default: the first account); Number also has a
Window choice (5h default, Weekly). Configuration: `ios/Shared/Faces.swift` (`AccountWidgetIntent`, `AccountWindowWidgetIntent`);
widgets `ios/Widget/Widgets.swift:57,71,85`.

### Number (small)

Picker name "Number". Description "One account's 5h or Weekly number, its bar and when it resets."

| Element | Size · rule |
|---|---|
| Label | Mark (size+1) and "<name> · 5h" (or "Weekly"), 14 pt, text colour, 1 line |
| Figure | **46 pt** bold mono-digit (**40 pt at 100%**), severity colour; secondary colour and "—" when signIn / plan / no reading; secondary colour when dimmed (signIn, stale); scales down to 70% |
| Bar | 8 pt, pace tick per 2.1; empty on signIn |
| Footnote | 12.5 pt, 8 pt above; per footnote ladder (the Weekly choice uses the Weekly line) |
| Spacing | Spacers (min 6) above and below the figure |

Number on a weekly-only plan: the label carries "Weekly" and the main window is Weekly.

### Countdown (small)

Picker name "Countdown". Description "Time until an account's 5-hour window resets, ticking live."

| Element | Size · rule |
|---|---|
| Caption | "5h reset" (14 pt, text colour); "Weekly reset" on a weekly-only plan |
| Count | **35 pt** bold mono-digit, ink colour, 8 pt below the caption, scales to 60%: a live timer to the reset (H:MM:SS); Weekly counts down in days and hours, not live (compact duration, e.g. "3d 4h") |
| Under the count | "at 5:50 pm" 13.5 pt secondary; at 100%: "Limit · opens 5:50 pm" in red |
| No reset or signIn | "—:—" 35 pt bold secondary and the footnote at 13.5 pt (the ladder's sign-in / plan / stale / no-data lines) |
| Account line | Bottom, mark + name only (no window), 13 pt, after a flexible spacer |

### Bars (small)

Picker name "Bars". Description "One account's 5h and Weekly, each with its bar and reset." Replaces the retired small Ring.
Approved: `2026-10-24-ios-widgets.html` §1.

| Element | Size · rule |
|---|---|
| Header | Mark 15 + "<name> · 5h", 14 pt, text colour, scales to 70% (a weekly-only plan: "<name> · Weekly") |
| 5h figure | 28 pt bold mono-digit, severity colour (secondary if dimmed / no reading) |
| 5h bar | 8 pt, 3 pt below the figure |
| 5h footnote | 11.5 pt, 2 pt below the bar, the ladder |
| Weekly row | "Weekly" 13 pt text colour, then the Weekly number at the trailing edge 15 pt semibold; 8 pt above (6 when the 5h footnote is gone) |
| Weekly bar | 6 pt, 3 pt below its row |
| Weekly footnote | 11.5 pt, "Resets Thu 9:16 pm" |
| Weekly-only plan | The 5h block alone, led by Weekly (28 pt figure, bar, Weekly footnote); no second row |

**Drop rules** (the layout that fits the height wins, in order): full → without the Weekly footnote (below about 136 pt tall)
→ also without the 5h footnote, its clock moving into the header as "<name> · 5h · 9:50 pm" (below about 119 pt). The pace tick
is kept on both bars.

## 2.3 Strip (medium)

Picker name "Strip", `systemMedium`, one account. Description "One account: 5h big, with Weekly under it." A medium Overview
with exactly one account draws the same face.

| Element | Size · rule |
|---|---|
| Header | Mark + "<name> · 5h" at 15 pt on the left; 5h figure **34 pt** bold mono-digit on the right; baseline aligned |
| Bar | 8 pt, 6 pt below |
| Line | 12.5 pt, 6 pt below; copy below |
| Weekly row | Pushed to the bottom: "Weekly" 13 pt text colour (52 pt wide), 6 pt bar, Weekly number 13 pt semibold (40 pt wide, trailing); gaps 10 pt |
| Weekly-only plan | No Weekly row; the main figure is Weekly |

Line copy (priority): sign-in / plan / no data / stale / aging → the footnote ladder; at 100%: "Limit reached" (red) + " · resets
5:50 pm"; projection: "~ runs out 7:51 pm · resets 5:50 pm"; normal: "Resets in 1h 35m · 5:50 pm" (Clock first on: "Resets at 5:50
pm · in 1h 35m"); no reset: the footnote.

## 2.4 Overview (Medium, Large, Extra Large)

Picker name "Overview" (static, no configuration). Description "Every account's 5h and Weekly — with the trend chart where
there's room." Every account is a card; the card is drawn in one of five **tiers** chosen by the room it gets. Approved:
`2026-10-24-ios-widgets.html` §2–§5 (rev C, layout by Fable, decisions D1 Ring goes, D2 chart on the roomiest cards, D3
"+N more").

### Grid plan (by family and account count)

| Family | Accounts | Columns | Cap |
|---|---|---|---|
| Medium | 1 | the Strip | — |
| Medium | 2–3 | 1 | 3 |
| Medium | 4+ | 1 | 2 |
| Large | any | 1 | 6 |
| Extra Large | 1 | 2 (its 5h and its Weekly side by side; 1 column when weekly-only, cell width ≤420) | 1 |
| Extra Large | 2 | 2 | 2 |
| Extra Large | 3 | 3 | 3 |
| Extra Large | 4 | 2 | 4 |
| Extra Large | 5+ | 3 | 9 |

Row gap 12 pt (6 pt when there is one column and more than four cards or the height is under 200); column gap 16 pt; a single
column in the Extra Large is capped at 420 pt wide so no bar runs longer. Past the cap: a footer "+N more · open Cooldown",
12 pt secondary, 20 pt tall. Source: `FaceViews.swift:275-350`.

### Tier by cell size

| Tier | Cell (w × h) | Name | Number | Content |
|---|---|---|---|---|
| T1 Full | ≥300 × ≥260 | 15, mark 16 | 40 bold | 5h bar (8), line A (reset, live), line B (pace · projection), **trend chart** (60–170 tall), Weekly row + its line |
| T2 Chart-narrow | ≥180 × ≥260 | 15, mark 16 | 30 | As T1 with the chart reduced to lines only (no labels) and "pace" wording |
| T3 Regular | ≥180 × ≥120 | 15, mark 16 | 34 | No chart; the rest as T1; spare height at the bottom |
| T4 Compact | height ≥66 | 14, mark 14 | 20 | Header with the short status; 5h bar (7) with a "5h" label; line B; Weekly bar (5) with its figure |
| T5 Mini | below 66 tall | 13, mark 13 | 17 | One row: "5h" + bar (6) and "Weekly" + bar (5) sharing the space 3 : 2, Weekly figure 12 pt semibold |

Card elements: bar tick kept at every tier ("below a Mini, a bar loses its tick" is the stated limit, not reached); Weekly row
label 13 pt (52 wide) with a 6 pt bar and a 13 pt semibold figure (40 wide); line A and B 12.5 pt, Weekly line 11.5 pt; the "as of"
stamp 11 pt under the card for aging/stale (orange when stale). T4 and T5 header foot-status: "· sign in again", "· as of
9:50 pm", "· no usage", "· open Cooldown", "· no window", "· resets Thu 9:16 pm", "· limit · 5:50 pm", "· runs out 7:51 pm"
(red), "· resets 5:50 pm".

Card title: "<name>" by default; "<name> · 5h" on the Extra Large 5h-only card; "<name> · Weekly" on a Weekly card at T1–T3.
A weekly-only account always leads with Weekly. Line A copy: "Sign in again", "No usage on this plan", "Open Cooldown to check",
"No window running", "Resets Thu 9:16 pm" (Weekly), "Resets in 1:35:00 · 5:50 pm" (live timer), at 100% "Limit · resets in …"
(red). Line B at 100%: "Opens again when the window resets". With no reading the chart still draws its guides and pace line, dimmed.

Differs from `2026-10-24-ios-widgets.html` D3: the wireframe caps Medium at three; the code caps at three for up to three
accounts and at **two** (plus the footer) for four or more.
Differs from ROADMAP CCRM-94 (iOS App) status text: it still lists "Ring small and medium"; the Ring widget was dropped (rev C) and
no Ring exists in the code.

## 2.5 Lock-screen widgets

One widget, picker name "Cooldown", description "One account's 5h on the lock screen." Configured with an account only (no
window choice). The system tints these, so **colour cannot carry severity**; they use size, weight and bars. Tap opens the app on the
account. `ios/Widget/Widgets.swift:146`; faces `FaceViews.swift:563`.

| Family | Anatomy | Copy |
|---|---|---|
| Circular | System accessory background; ring of line width 5 (white, track 32%) with the pace tick, 2 pt padding; figure 19 pt bold in the ring, sublabel under it | Figure: the number (Used or Left, no "%"), "MAX" at 100%, "—" on signIn. Sublabel "5h" (or "W" for a weekly-only plan) |
| Rectangular | Mark 14 (white), "<name> · 5h" 14 pt semibold, flexible gap, figure 17 pt bold; bar 6 pt with pace tick (white); line 13 pt at 85% opacity, all spacing 4 | "Sign in again"; "Limit · resets 5:50 pm"; "Resets in 1h 35m" (relative, ticking); "No window running" |
| Inline | One line: "<name> 5h 46% · resets 5:50 pm"; falls back to without the reset when it does not fit | No account: "Cooldown: add an account" |

No lock-screen state shows "as of", stale, or the Free-plan line: signIn shows "—", and the rest read as fresh numbers.
Differs from `2026-10-24-ios-lock-circular.html`: Robin asked for no circle counter and both windows; the file offers A (two numbers
stacked, recommended), B (numbers with bars), C (upright bars) and **no pick is recorded** — the code still draws the single-window ring.

## 2.6 Control Center control

One control, picker name "Cooldown", description "One account's headline number, Weekly or 5h. Tap to open it." Configured with an account (default
the first). Tapping opens the app on that account. `ios/Widget/LiveAndControl.swift:63-111`.

| Element | Content |
|---|---|
| Icon | SF Symbol "hourglass" |
| Title | "<name> · <window>": the account's Headline window, "Weekly" or "5h" (CCRM-96 (Weekly First)) |
| Value | The percentage, per below |
| Gallery preview | "Teams · Weekly" / "52%" |

Value copy: "Sign in again"; "No usage on this plan"; stale or aging: "46% · as of 9:50 pm"; at 100%: "Limit reached"; otherwise the percentage
(Used or Left). No account: title "Cooldown", value "Add an account".

## 2.7 Live Activity (lock screen) — the Duet with Weekly

Up to **two** accounts (default the first two in the user's order, or the Lock Screen setting's picks), each a 5h row plus a thin Weekly
line, inside iOS's 160 pt cap; a single account gets a taller panel. Dark palette always; background #1C1C1E at 86%; padding 16 horizontal, 12
vertical. The activity goes stale at the earliest reset. `ios/Widget/LiveAndControl.swift:8`, `ios/Shared/FaceViews.swift:620`,
`ios/Shared/LiveActivityModel.swift`, `ios/App/Model/Surfaces.swift:30`.

| Element | Duet (two accounts, per row) | Single account |
|---|---|---|
| Label | Mark 14 + "<name> · 5h" 14 pt, "·", then the line (12 pt, 1 line) | Mark 15 + "<name> · 5h" 15 pt; line below the bar at 12.5 pt |
| 5h bar | 7 pt, pace tick; dimmed at 45% when stale | 8 pt |
| Weekly line | "Weekly" (42 wide) + 4 pt bar + figure (36 wide), 11.5 pt text colour; shown when there is a Weekly reading (or signIn) | "Weekly" (48) + 5 pt bar + figure, 12.5 pt |
| Big number | 38 pt (32 at 100%), semibold, 84 pt column, trailing | 52 pt |
| Row gap | 10 | 8 (rows 7, columns 12) |

Line copy (short = Duet, long = single):

| Condition | Short | Long |
|---|---|---|
| signIn | "sign in again" (orange) | "Sign-in stopped working · open Cooldown" |
| older than 30 min | "as of 9:50 pm" (orange) | same |
| window already reset | "reset · 0%" | "5h reset at 5:50 pm · back at 0%" |
| weekly-only plan | "Thu 9:16 pm" | "Resets Thu 9:16 pm" |
| at 100% | "limit · 0:42:10" (live; red label) | "Limit reached · resets in 0:42:10" |
| on a path to 100% | "runs out 7:51 pm" | same |
| otherwise | "1:35:00" (live, max 58 pt) | "Resets in 1:35:00 · 5:50 pm" (Clock first: "Resets at 5:50 pm · in 1:35:00") |
| no window | "no window running" | same |

Big number colour: severity, secondary when signIn or older than 30 min. **Final frame at a reset:** mark 26 + "<name> · 5h reset"
16 pt semibold over "Usage is back at 0%. The last window peaked at 87%." 13.5 pt (the peak is the last reading).

### Dynamic Island (iPhone), first account only

| Region | Content |
|---|---|
| Expanded leading | Mark 18 + "<name> · 5h" |
| Expanded trailing | Big number 34 pt |
| Expanded bottom | 8 pt bar with pace tick, then the long line at 13 pt |
| Compact leading | Mark 16 + the figure with its "%" (Used or Left, e.g. "46%") in severity colour |
| Compact trailing | A live timer, max 52 pt wide (red at 100%, white otherwise); "reset" once past the reset; the short duration (not live) on a weekly-only plan |
| Minimal | The ring, line width 3.4, no label |

---

# Differences between wireframes and code (summary)

| Where | Wireframe says | Code does |
|---|---|---|
| `widget-wireframes.html` (rev 2, "not built yet") | Three faces (Ring, Mini-rings, Pace chart), a ↻ refresh overlay, a stale pill, tick 0.31 × stroke at 48–60% ink, a 15-minute redraw tick, a 25 sp ring figure | **Superseded by CCRM-78.** Four faces (Ring, Number, Countdown, Strip), no ↻, "Stale" as sub-line text, tick 0.39 × stroke at 90% ink with halo, no periodic redraw |
| `2026-09-26-widgets-cover-refit.html` | Bitmap budget 2 MB (R10) | 4 MB since rev H |
| `2026-09-25-widgets-reborn.html` rev D | Fixed Number figures 32 / 44 sp, Countdown 24 / 28 sp | Rev H fills: Number 32–96 sp, Countdown 20–80 sp; Number 2×2 and the Ring companion are rev H additions |
| `2026-10-24-ios-widgets.html` D3 | Medium cap three | Three up to three accounts, two beyond |
| `2026-10-24-ios-lock-circular.html` | Options without a ring, both windows; recommendation A | Pick not recorded; ring with a single window still ships |
| ROADMAP CCRM-94 text | Lists "Ring small and medium" | No Ring widget (dropped by rev C) |

# Things noticed while documenting (not decided here)

- iOS Number on a weekly-only plan with its Window set to Weekly reads the already-moved Weekly fields and would show "—" with no
  footnote (`FaceViews.swift:103-128` against `Faces.swift:46`). Not confirmed on a device.
- (Fixed 2026-11-04, CCBG-54 (Plan Copy Gap).) iOS `FaceFootnote` plan copy read "No usage on the  plan" for a ChatGPT account; it now reads "No usage on this plan", and Claude's still reads "No usage on the Free plan".
- The lock-screen widgets show no stale or Free-plan state; whether they should is a design call.

## v2.0 on iOS — CCRM-96…100, RUNBOOK-v2.0 Step 6 (Part 2, iOS)

As built on the v2.0 branch (RUNBOOK-v2.0); each entry names the face it amends, which still describes v1.9.


- **Headline rule per face** (mobile §5): Number — a new widget's window is seeded from the default account's
  Headline (`HeadlineWindowOptions.defaultResult`: weekly/auto → Weekly, fiveHour → 5h), stored with the widget;
  nothing stored reads as 5h (v1.9's default) so a placed Number never changes. **Countdown unchanged** (5h, see
  open item). Bars, Strip, lock rectangular: fixed Weekly-first. Overview big number, lock circular and inline,
  Live Activity rows, Island: follow the Headline live (Auto takes over while the 5h binds). Control Center:
  unchanged (5h).
- **Bars (small)**: "Teams · Weekly", 28 pt figure, 8 pt bar, footnote "Resets Sun 9:30 pm" (spent "Limit · Sun
  9:30 pm" red); then "5h" row (15 pt figure, heavy and in its colour step while binding) with a 6 pt bar, no reset
  line. Too short: the footnote goes and the day joins the header ("Teams · Weekly · Sun"). Spent or no 5h: no 5h row.
- **Strip (medium)**: "Teams · Weekly" + 34 pt figure, bar, the reset line ("Limit reached · resets …" when spent);
  bottom row "5h" bar figure (loud while binding); no 5h reset clock on the Strip.
- **Overview**: title "Teams · Weekly" (or "· 5h") down to Regular; Weekly lead line A "Resets Sun 9:30 pm · in 4d
  6h" (spent "Limit · resets in 3d 4h" red), line B band words (red over pace) plus " · ~ runs out Sat 3:10 am" only
  when the projection runs out; a cell under 300 pt words the band alone. Second row = the other window: under a
  Weekly lead "5h" with "Resets in 2h 20m · on track for ~69%" (binding: orange, live countdown, "runs out ~3:25
  pm"; narrow "Resets 5:10 pm · ~69%"); under a 5h lead "Weekly" with "Resets Sun 9:30 pm · Slow down". Spent: no
  second row, "Out until Sun 9:30 pm". Compact/Mini: lead bar first, then the companion. Extra Large, one account:
  Weekly card left, 5h card right.
- **Lock screen**: rectangular "Teams · Weekly 52%" (title scales to 80%), bar, "Resets Sun · 5h 34%" (5h bold while
  binding; weekly-only "Resets Fri 10:18 pm"; spent "Limit · resets Sun 9:30 pm", no 5h). Circular: the Headline
  window's figure without "%", sublabel "W" or "5h"; spent "MAX". Inline: "Teams Weekly 52% · resets Sun" (5h:
  "· resets 3:40 pm"), falling back without the reset.
- **Live Activity**: each row leads with its Headline window: "Teams · Weekly · Sun 9:30 pm", big figure, thin "5h"
  line (bold in its colour step while binding); Auto binding: "Teams · 5h · runs out …", thin "Weekly" line; spent:
  "· out · Sun 9:30 pm" red, no thin line. Row state gains optional `companionLoud`, `spent`, `projection` (older
  states decode). Stale date = the earliest of every shown window's reset, the 5h companion included.
- **Dynamic Island**: expanded line "Resets Sun 9:30 pm · 5h 34%" (Vs pace: "52% used (mark 39%) · resets Sun ·
  5h 34%"); Auto binding "~ runs out 3:25 pm · resets in 0:50:12 · Weekly 52%". Compact and minimal follow the lead
  window (Weekly: "4d 6h", not live).
- Gallery descriptions: Bars "One account's Weekly and 5h, each with its bar."; Strip "One account: Weekly big,
  with 5h under it."; Lock "One account's Weekly and 5h on the lock screen."

## Gemini and the spent-week gate on iOS — CCRM-55, CCRM-102

As built on the v2.0 branch (RUNBOOK-v2.0 Steps 6a and 6c, iOS).

- **CCRM-102 gate on faces showing the 5h:** Number (window 5h) — label, figure and bar at 45%, the footnote replaced by
  "Weekly spent" (12.5 pt, ink). Countdown — the timer at 45%, "Weekly spent" in place of "at 5:10 pm". Overview with a 5h
  lead (Headline 5-hour) — header, bar, line A and chart at 45% (no projection), line B "Weekly spent". Lock circular
  (5h lead) — ring dimmed; inline (5h lead) — "Teams 5h · Weekly spent". Live Activity / Island row with a 5h lead — bar
  and big figure dimmed, the line reads "Weekly spent" (row state gains optional `weekGated`). Control Center (5h) —
  value "Weekly spent". Weekly-led faces (the default) are unchanged: the spent Weekly already drops the 5h companion.
- **CCRM-55:** every face, the lock screen, the Live Activity and the Control Center draw a Gemini account as any other,
  in Gemini Blue with its mark; the pool is never on a widget.


## The shape grammar on iOS — CCRM-103 (Hybrid Notification), RUNBOOK-v2.0 Step 6d

CCRM-103 (Hybrid Notification), frozen design
`design/2026-11-02-shape-grammar.html` rev D §6, §7 (B1), §7b, §8; rules in `spec/mobile/BEHAVIOR-MOBILE.md` §5
"Shape grammar". Code: `ios/Shared/FaceViews.swift`, `ios/Shared/Faces.swift`, `ios/Shared/LiveActivityModel.swift`,
`ios/Widget/LiveAndControl.swift`, `ios/Widget/Widgets.swift` (gallery descriptions only).

### Rules every face follows

- **The 5h is a ring on the left, the Weekly a bar on the right**, whatever the account's Headline (CCRM-96 (Weekly
  First)): every face draws `Face.grammar` (Weekly in the main slot, the 5h as `fiveHour`). No "5h" word on any face
  this note covers. The ring is `GrammarRing` (through `FaceRing`); its VoiceOver label is "5-hour window, 34 percent".
- **Figures** (`FaceFigure`): SF Pro, monospaced digits, the "%" at 58% (`Display.grammarFigure`); the Usage display's
  figure (Used "52%", Left "48%", Vs pace the unsigned distance, green under the mark / red over it / ink on it via
  `Display.figureColour`; no "left" or "vs pace" word after it). Dimmed or no reading: secondary grey.
- **Bars** (`FaceBar`) end per Appearance › Usage end mark: Round = today's capsule (`UsageBar`); Line (default) and
  Pace colour = the fill ends square at the usage, a 1.5 pt line 1.5 pt past each edge over a 1 pt gap in the surface
  colour, coloured per `UsageEndMark.role` (fill colour; or green / red / ink by the ±5 band). Red past the pace tick and
  the tick are as before. Widgets draw no dashed room (no caption on widgets).
- **Spent week** (CCRM-102 (Spent Week Gate)): the ring greys (grey track, no arc, no tick, its figure kept dim). Where
  a face shows the 5h on its own (Number 5h, Countdown, the tile's 5h line) the line reads "Weekly spent" at full
  opacity, as before.
- **No 5h (B1)**: Bars, Number, Countdown, Strip, lock screen and Live Activity draw no ring (the Weekly takes the width;
  the Live Activity keeps an empty slot so bars line up). Overview tiles (T1–T3) keep the slot as the greyed ring with a
  cross (rev C, the tile default); Compact (T4) aligns flush left; Mini (T5) keeps an empty 30 pt slot.
- **Sign-in / no reading**: grey ring with "—", figure "—".

### Home widgets (iPhone small 158, medium 338 × 158, large 338 × 354 pt; 16 pt padding)

| Face | Layout | States |
|---|---|---|
| **Bars** (small) | Mark 15 + "Teams · Weekly" (14 pt); 8 pt below, ring **50** left (10 gap) of the Weekly figure **28 pt** over its **8 pt** bar (5 pt gap); 6 pt below, the Weekly reset "Resets Sun 9:30 pm" (11.5 pt); 6 pt below, the first model limit (`FaceModelRow`): name (12 pt, 38 pt wide, "Claude " dropped), its **5 pt** bar with the end mark, figure 12 pt (28 pt wide), 6 gaps. iPhone SE (141): the reset line goes and " · Sun" joins the header. | Spent: "Limit · Sun 9:30 pm" red; sign in / stale / no data as `FaceFootnote`. No 5h: no ring. No model limit (none reported, or Model limits off for the account), sign in or no usage on the plan: no model row. |
| **Number**, window 5h | Mark + "Teams" (no "· 5h"); the ring hero **84 pt**, figure **24 pt**, centred; "Resets in 2h 20m · 5:10 pm" (Clock first: "Resets at 5:10 pm · in 2h 20m"), 12.5 pt. | Runs out / limit / stale / sign in: `FaceFootnote`. Spent week: grey ring, "Weekly spent" (ink). No 5h: the Weekly face below. |
| **Number**, window Weekly | Unchanged layout: "Teams · Weekly", figure 46 pt (40 at 100%), 8 pt bar, "Resets Sun 9:30 pm". Grammar figure and bar end. | As before. |
| **Countdown** (small) | Ring **40** + "Resets" (13 pt, grey); 10 pt below the live count **35 pt** bold; "at 5:10 pm" 13.5 pt; the account (mark + name, 13 pt) at the foot. | Limit: "Limit · opens 5:10 pm" red. Spent week: grey ring, count dimmed, "Weekly spent". No 5h / sign in / no window: today's face ("Weekly reset", days-and-hours count). |
| **Strip** (medium) | Ring **84** (figure **22 pt**) left, 14 gap; right: mark 16 + "Teams · Weekly" (15 pt) with the Weekly figure **30 pt** right-aligned; **8 pt** bar (6 below); the Weekly line "Slow down · ≈11%/day · resets Sun" (12.5 pt, red when over pace or spent: "Out until Sun 9:30 pm"); 10 pt below, the first model limit: name (13 pt, 44 pt wide), its **6 pt** bar with the end mark, figure 13 pt (36 pt wide), 8 gaps. The 5h row under the Weekly is gone. | States replace the line (`FaceWeeklyLine`). No 5h: no ring. No model limit, sign in or no usage on the plan: no model row. |
| **Overview** T1 Full / T2 Chart-narrow / T3 Regular | The tile: ring **72** (cell ≥ 300 pt) or **64**, 14 gap; right: mark 16 + "Teams · Weekly" (15 pt) and the Weekly figure (0.25 × ring: 18 / 16 pt), 8 pt bar. Under the tile: the Weekly line (14.5 pt semibold), then the 5h line led by a **12 pt ring glyph**: "resets in 2h 20m · on track for ~69%" (14.5 pt; orange while it binds or runs out, red at the limit). T1/T2 add the Weekly's trend chart (60–170 pt; T2 lines only). Stale / aging: "as of …" under. | No 5h: greyed ring with a cross, no 5h line. Spent week: grey ring; the 5h line "Weekly spent". |
| **Overview** T4 Compact | Ring **46**, 10 gap; mark 14 + name (14 pt) with the Weekly figure **20 pt** right; **7 pt** bar (5 below); the Weekly line 12 pt (stale: "as of 3:10 pm" orange). | No 5h: flush left. |
| **Overview** T5 Mini | One 42 pt row: ring **30** with "34" (**11 pt**, no "%"), mark 13, name (13 pt, 58 pt wide), the Weekly's **5 pt** bar, its figure **12 pt** (30 pt wide). | No 5h: an empty 30 pt slot. |
| **Overview** Extra Large, one account | One tile (≤ 420 pt wide) instead of the two side-by-side window cards — see "needs a decision". | |

Medium Overview with one account is the Strip (as before).

### Lock screen (tinted by iOS: the Used figure whatever the Usage display)

- **Circular** (72 pt): the accessory background, the ring (64 pt, mono white) with the **digits only** ("34", 19 pt; no
  "5h" sublabel); "MAX" at 100% (as before); greyed for a spent week; "—" for sign in. No 5h: today's Weekly ring
  (see "needs a decision").
- **Rectangular** (172 × 76): ring **44** (mono, "34%"), 8 gap; right: mark 13 + "Teams" (13 pt semibold) with the
  Weekly figure **17 pt** right; **6 pt** bar (5 above, 4 below); "Weekly · Sun" (12 pt, 88%). Spent: "Limit · resets
  Sun 9:30 pm"; sign in: "Sign in again". No 5h: no ring, the Weekly takes the width.
- **Inline**: the ring glyph rendered as an image (16 pt, no digits), then **"34% · Teams Weekly 52%"**; where that does
  not fit "34% · Weekly 52%" with the glyph, then the text alone. No 5h: "Personal Weekly 50%".

### Live Activity and Dynamic Island (≤ 160 pt)

- **One account**: ring **48** left (12 gap); "Teams · Weekly" (mark 15, 15 pt semibold), **8 pt** bar (6 below), two
  12.5 pt lines "Slow down · ≈11%/day" / "Resets Sun 9:30 pm"; the Weekly figure **44 pt** semibold right.
- **Duet**: per row ring **36**, one line "Slow down · Sun 9:30 pm", figure **36 pt**; rows 10 apart. No 5h: an empty
  36 pt slot. The thin companion line is gone.
- Lines' states: "Sign in again" (one account: "Sign-in stopped working · open Cooldown") orange; "as of 3:10 pm"
  orange; "Out until Sun 9:30 pm" red; a Weekly that reset "Weekly reset · 0%"; "Weekly not started". A 5h that reset
  since the row was written reads 0% in its ring (`Row.fiveReset`).
- **Island compact**: leading ring **30** with "34" (11 pt, no "%"); trailing the Weekly figure (15 pt semibold,
  coloured). No 5h: the provider mark leads.
- **Island expanded**: ring **48** left (14 gap); "Teams · Weekly" (mark 18, 15 pt semibold) with the figure **34 pt**;
  8 pt bar; "Resets Sun 9:30 pm · in 4d 6h" (13 pt; spent "Out until …").
- **Minimal**: the bare ring (provider mark with no 5h).
- **The small 5h glyph** (the 5h line's 12 pt lead, the inline lock image at 16 pt) is `FiveGlyph`, the page's
  `gauge()` — the Android status-bar ring's geometry (needle, hub, the cross from 99.5; greyed: the arc at 40%, no
  needle) — not a small grammar ring.
- Model: `CooldownActivityAttributes.Row` is now always built Weekly-led (`Face.grammar`), the 5h in the companion
  fields; new optional fields `fiveReset`, `weeklyLine`, `weeklyWords` (older states still decode).

### Needs a decision (left as noted)

1. **Model limits (Fable, Argon) on Bars and Strip**: settled (Fable E1.1) — `Face.modelLimit` carries the account's
   first model cap (honouring Model limits, as the app's tile), drawn as above. A cap whose reset has passed reads 0%.
2. **Overview tile header**: the page's `itile` has no account name; the widget tile names it in the bar's label row
   (mark + "Teams · Weekly"). The tile draws its first model limit under the Weekly bar (6 pt), as `itile` does. Extra Large with one account now draws one tile, not today's 5h and Weekly cards side by
   side (a 5h card would be a 5h bar).
3. **Lock circular for a plan with no 5h** (Robin, 2026-11-02: "Let's do what we did for Android"): no ring and no
   "W" — the Weekly as Android's no-5h status-bar glyph (N3, `WeeklyBarGlyph`: the 21 × 10-unit capsule with its pace
   tick in a cleared halo, red past the tick, the red cross when full), 40 × 28 pt (its unit fits both ways, so the tick is never clipped), under the Used figure (17 pt,
   small "%", "MAX" when spent, "—" signed out), all in the lock tint. VoiceOver: "Weekly, 50 percent".
4. **Ring band on the lock-screen circular**: ruled (Fable E1.2) and built — 5 pt at its 64 pt size, as the page's
   `wring` (`GrammarRing.bandOverride`, `FaceRing.bandRatio` 5 / 64; the figure is fitted to that bore).
5. **Mini row and Live Activity rows show no status line** for sign-in / stale beyond the dimmed "—" (Mini) — as drawn.
6. **Gallery descriptions** (`Widgets.swift`) reworded for Bars ("One account's Weekly bar, with its 5h ring.") and
   Strip ("One account: the 5h ring, with the Weekly beside it."); the Control Center control still says "Teams · 5h"
   (not in this piece's brief).

**iOS face titles** (CCBG-71 (iPad Number Title Clip)): a face's "<name> · <window>" title drops to the name alone
(shrinking to 85% at most) when it does not fit, never "ChatGPT · We…". **A dropped widget kind** (the iOS "Ring",
removed in rev C) keeps its last snapshot on a home screen until the user removes it (CCBG-70 (Stale Ring Widget)).

