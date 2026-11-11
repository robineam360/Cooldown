# Foundations — the design system every mobile surface shares

Colour, type, shared shapes, words and icons that the app, the notification, the widgets and the
share card all read, Android and iPhone/iPad side by side. **Describes the apps as shipped**:
where an approved wireframe and the code disagree, the code is documented and a "Differs from"
note says so. **When you change a token, update this file in the same commit.**

The numbers two installs must agree on are not restated here: severity 70/90, the bar colour
breakpoints 80/90/100, the 12 accent hexes, the ±3-point pace dead zone and the even-pace line
live in [core/BEHAVIOR-SPEC.md](../../core/BEHAVIOR-SPEC.md) (§1, §5, §6) and are linked, not
copied. Per-surface layout is in [notification.md](notification.md), [widgets.md](widgets.md) and
[app.md](app.md). Source paths below are relative to the repo root; Android is
`app/src/main/java/com/robin/claudeusage/`, iOS is `ios/`.

## 1. Colour

### 1.1 Providers: mark and colour

Three services, exactly (CCRM-53 (Provider Model)). The mark is the provider's own single-colour
glyph, hand-traced and unmodified, never a dot or an initial (CCRM-56 (Provider Identity),
decision 4). Colour is the account's accent (bars, figures, charts) and is separate from the mark.

| Provider | Display name | Accent light | Accent dark | Mark | Notes |
|---|---|---|---|---|---|
| Claude | Claude | `#D97757` | `#E59980` | `ic_provider_claude` (Android), `ClaudeMark` (iOS) | Theme option "Claude Orange"; the app default |
| ChatGPT | ChatGPT | `#10A37F` | `#19C39A` | `ic_provider_chatgpt`, `ChatGPTMark` | Theme option "ChatGPT Green" (provider-only) |
| Antigravity | Gemini | `#4285F4` | `#8AB4F8` | `ic_provider_gemini` | Theme option "Gemini Blue" (provider-only). **Android only: iOS ships Claude and ChatGPT** |

Source: `data/Provider.kt:10`, `ui/Palette.kt:62`, `ios/Shared/Theme.swift:36`.

Mark rules, both platforms:

- The mark is tinted by the account's resolved accent, or by ink on a monochrome surface.
- The Claude starburst runs edge to edge in its square while the ChatGPT knot fills about 70% of
  its own, so **Claude is drawn at 82% of the box** to match apparent size (iOS scales it in code,
  Android's drawable carries the same). Source: `ios/Shared/Theme.swift:61`.
- Sizes in use: see 3.5.

### 1.2 Accent resolution and Theme colour

Three levels, same order on both platforms: the account's own override, else the global Theme
colour, else the provider's colour. "Per provider" is the global value that falls through to the
third level. Source: `ui/Palette.kt` (`resolveAccent`), `ios/Shared/Display.swift:65`.

| Token | Android | iOS | Used by |
|---|---|---|---|
| Theme colour (global) | "Per provider" default; the 11 named colours below plus "Claude Orange" and "Material You" | "Per provider" default; the 11 named colours below | Settings, every surface's bars and figures |
| Per-account accent | Account card colour grid; "Provider colour (default)" clears it | Same | That account only, on every surface |
| "Material You" | System dynamic colour (Android 12+) | **None** | App only; widgets, notification and charts resolve it through Claude Orange |
| "Claude Orange" as a pickable colour | Yes (also the pseudo-default name) | **Not offered**: Claude's orange is reachable only through "Per provider" or the provider default | Differs from the core's twelve (see 1.2 note) |
| ChatGPT Green, Gemini Blue | Resolvable by name, **not offered in the grids** | Not present | Provider default only |

The twelve named accents with light and dark hexes are the core's table (BEHAVIOR-SPEC §5, "Accent
themes"); Android's `Palette.options` and iOS's `ThemeColours.options` carry the same values.
Display order in the Android grid: Claude Orange, Blue, Indigo, Cyan, Teal, Green, Amber, Deep
Orange, Red, Pink, Purple, Brown. Source: `ui/Palette.kt:62`, `ios/Shared/Display.swift:91`.

Note: iOS lists eleven (Claude Orange left out) where the core says every client ships twelve. A
shared-core gap, recorded here rather than silently fixed.

### 1.3 Rooms: the surface the colour sits on

| Token | Android light | Android dark | iOS light | iOS dark | Source |
|---|---|---|---|---|---|
| Claude room surface | `#F5EFE8` | `#0D0D0D` | one palette for every provider (below) | one palette | `ui/Rooms.kt:34` |
| Claude room card | `#FCF8F4` | `#1A1A1A` | | | |
| Neutral room surface (ChatGPT, Gemini) | `#F7F7F8` | `#0D0D0D` | | | `ui/Rooms.kt:48` |
| Neutral room card | `#FFFFFF` | `#1A1A1A` | | | |
| Headline typeface | Claude room: serif; neutral: default sans | | system sans everywhere | | `ui/Rooms.kt` |
| iOS background | | | `#F2F2F7` | `#0E0E0E` | `ios/Shared/Theme.swift:11` |
| iOS card / grouped surface | | | `#FFFFFF` | `#1C1C1E` | |

- **Black Room (CCRM-76 (Black Room))**: Claude's dark room is the neutral black (`#0D0D0D` /
  `#1A1A1A`), not the warm `#1B1715` of CCRM-60 (Dual Identity), because the orange accent and
  yellow pace line had no contrast against warm brown. The light room and the serif headline
  stand.
- Rooms apply to the app's Main and History screens only, following the selected tab's provider.
  Settings and the Guide keep Material's default scheme. Android card tint is applied to
  `surfaceContainer*` and `surfaceVariant`.
- iOS has **no per-provider rooms and no serif**: one dark and one light palette, accent only
  carries identity. Differs from the dual-identity wireframe (design/dual-identity-wireframe.html),
  which draws the Claude room's serif figures; iOS chose not to port it.
- Differs from design/2026-10-24-ios-appearance-accounts.html: its CSS draws the dark background
  as `#000`; the code (and design/2026-10-23-ios-wireframes.js) use `#0E0E0E`.

iOS palette (all tokens), source `ios/Shared/Theme.swift:11-18`:

| Token | Light | Dark | Role |
|---|---|---|---|
| ink | `#000000` | `#F2F2F2` | Headlines, figures |
| txt | `#3A3A3C` | `#D1D1D6` | Body copy |
| sub | `#6C6C70` | `#98989F` | Secondary, captions |
| line | `#D1D1D6` | `#38383A` | Hairlines, axis |
| fill | `#E5E5EA` | `#2C2C2E` | Chips, wells |
| ringTrack | `#C7C7CC` | `#55555A` | Ring track (widget faces) |

Android text colours are Material 3's baseline scheme (`onSurface`, `onSurfaceVariant`); the
widgets and the share card use their own warm ink: **`#26211E` on light, `#EDE8E4` on dark**.
Share card ground: `#F5EFE8` light, `#0D0D0D` dark. Widget neutral card: `#FCF8F4` light, `#1A1A1A`
dark. Source: `widgets/WidgetFace.kt:309,543`, `share/ShareCard.kt:70-73`.

### 1.4 Bar colour, warning ladder, red

The breakpoints and their strict/inclusive edges are the core's (§5); the hexes per theme as
shipped, identical on both platforms:

| Role | Light | Dark | Android source | iOS source |
|---|---|---|---|---|
| Accent (below the first breakpoint) | account accent | account accent | `Palette.barColor` | `Palette.severity` |
| Yellow (above 80) | `#F9A825` | `#FDD663` | `ui/Palette.kt:141` | `ios/Shared/Theme.swift:13` |
| Orange (above 90) | `#F57C00` | `#FFA726` | | |
| Red (at 100) and the over-pace slice | `#C62828` | `#FF5252` | | |
| Warning text (expiry-soon, notes) | `#9A6700` | `#FDD663` | `Palette.warn` | iOS uses yellow/orange directly |

Only the fill, the figure that sits with it and the chart's guides key on **used** percent; Used
or Left (4.2) never moves a colour.

Condition and error hues (the pinned notification's strips and dot): fault `#B3402A` light /
`#E0705A` dark, fixed so an accent cannot theme a fault away; a warning takes the account's accent.
Source: `notify/PinnedNotification.kt:844`.

### 1.5 Pace tick, red past the pace mark, chart guides

The even-pace maths is the core's (§6) and the dead zone is the core's (§5). What is drawn:

| Token | Android | iOS | Source |
|---|---|---|---|
| Pace tick ink, in-app bars | `onSurface` at 60% alpha dark, 48% light | solid `#C7C7CC` dark, `#8E8E93` light | `MainCards.kt` (`UsageBarLine`), `ios/Shared/Theme.swift:15` |
| Pace tick ink, notification bars | `#F2F2F4` dark at 60%, `#1D1D1F` light at 48% | n/a | `ui/BarRenderer.kt:144` |
| Pace tick ink, widgets and share card | ink at 90% (3 dp wide) with a 1 dp halo in the face colour (cleared on Transparent faces) | n/a | `widgets/WidgetFace.kt:838` |
| Ring needle, hairline, spent cross | ink at 85%, 50%, 90% | n/a | `ui/RingRenderer.kt:59-62` |
| Ring tick (figure in the bore) | ink at 90% | solid tick ink | `ui/RingRenderer.kt:62` |
| Red slice (pace line to fill edge) | red, light `#C62828` / dark `#FF5252` | same | `BarGeometry.redSegment`, `RingGeometry.redSegment` |
| Chart even-pace diagonal and its legend | the yellow rung: `#F9A825` light / `#FDD663` dark, 90% alpha, 2.5 dp dashed | `pace`: `#E0A000` light / `#FDD663` dark, 2.6 pt dashed | `ui/Sparkline.kt:282,553`, `ios/Shared/Charts.swift:119` |
| Chart guides 80/90/100 | the yellow/orange/red rung, dashed, 32% alpha | same hues, 70% dashed | `ui/Sparkline.kt:418,541` |
| Above-pace wash under the curve | orange rung at 34% dark / 30% light; red wash 10% / 7% | curve and last dot turn red | `ui/Sparkline.kt:534,575` |
| Chart now divider | `muted` at 28%, now dot 4.5 dp | `sub` 70%, 1 pt | `ui/Sparkline.kt:600` |
| Chart axis | `muted` at 18% | `line`, 1 pt | `ui/Sparkline.kt:730` |

Rules that hold on every surface:

- **Time has no severity.** Pace ticks, needles, hairlines and the spent cross are neutral ink at
  an alpha, never a ladder hue and never an accent. On multi-account faces (a Duet half, the
  Strip's rows, the Live Activity's two accounts) every tick is the same neutral ink, so only the
  fill says whose reading it is.
- The tick always draws when there is a reading **and** a reset clock; with either missing there is
  no tick and no red, never a guessed mark. Credits rows have no clock, so no tick.
- **"Show red past the pace mark"** (default on) gates the red slice only, never the tick. Off:
  the fill keeps its ladder colour all the way to its edge. Android keeps one toggle per surface
  family; iOS has one that covers the app, widgets and Live Activity.
- The pace-line partner table in `ui/Palette.kt` (`paceColor`) is **drawn nowhere today**; since
  CCRM-51 (Rails Gauge) no surface uses it. It is kept as a contract (the partner is always a cool
  hue) and is not a token to reuse.
- Differs from design/provider-identity-wireframe.html, which draws the bar pace tick in `#0288D1`
  (the pace partner): the shipped tick is neutral ink everywhere.

### 1.6 Monochrome rules for system chrome

- **Status-bar glyph** (Android): the bitmap's colour survives in the status bar on Samsung One UI
  but is stripped to an alpha mask in the Quick Settings tile, so **colour is an enhancement only**:
  shape and alpha carry the level and the pace mark; colour adds severity. Below 80% the glyph wears
  the shown account's accent, above it the severity ladder takes the hue (CCRM-62 (Duet
  Notification)). Differs from core §5, which says always-on chrome is monochrome until 80%.
- **iOS lock-screen widgets** are mono: the system tints them, so the ring and bars draw in one
  colour with the track at 32% alpha and tick and red in that same colour (wireframe rev B section
  5). Source: `ios/Shared/Charts.swift:206`.
- The `ic_stat_bars` drawable is plain white bars, an alpha-mask-only asset.

## 2. Type

Android's app uses Material 3's baseline typography unchanged (no custom `Typography`), on the
device font. iOS uses the system font (SF) with explicit point sizes, digits monospaced on figures.
**Window and figure type is never baked into a bitmap**: rings and bars are bitmaps, every number is
a real text view at true sp (the notification and widgets) so it follows the system font scale.

### 2.1 App screens

| Token | Android | iOS | Used by | Source |
|---|---|---|---|---|
| Card title | `titleSmall` (14 sp, medium) | 17 pt regular (titles on iPad and iPhone cards) | "5-hour window", "7-day window", "Usage credits" | `MainCards.kt:573`, `ios/App/UI/Cards.swift:79` |
| Headline figure ("47%") | `bodyMedium` (14 sp) bold; **serif in the Claude room**, sans elsewhere | 17-19 pt bold, monospaced digits, sans | Card rows, credits | `MainCards.kt:278`, `ios/App/UI/Cards.swift:72` |
| Body | `bodyMedium` 14 sp | 14.5-16 pt | Row labels, notices | |
| Secondary and caption | `bodySmall` 12 sp, `onSurfaceVariant` | 13.5 pt (compact lines), 14.5 pt (open cards) | Reset line, pace line, status line | |
| Pace line above pace | `bodySmall` bold in the orange rung | same size, red | Pace readout | `MainCards.kt:427` |
| Chart labels | 9.5 sp tiny, 11 sp callouts (bold for the now and last-value labels) | 10-12 pt, bold for the last value | Trend chart | `ui/Sparkline.kt:401,488` |
| Synthetic banner | 11.5 sp bold, 0.35 sp tracking, on `#9A6B1E` with `#1A1400` ink, 34 dp tall | n/a | Debug synthetic series | `MainActivity.kt:341` |

### 2.2 Pinned notification (Android, Huge number base)

Huge number is the style every notification wireframe mocks first. Sizes by layout; the
authoritative per-element map is [notification.md](notification.md).

| Element | Size | Layout |
|---|---|---|
| Collapsed figure, single | 32 sp | `notif_big_number.xml` |
| Collapsed figure, Duet half | 30 sp | `notif_duet.xml` |
| Expanded figure, single | 44 sp | `notif_big_number_expanded.xml` |
| Expanded figure, Duet half | 36 sp | `notif_duet_expanded.xml` |
| Panel row label and value | 13.5 sp (13 sp in the Duet panel's tighter rows) | `notif_panel_single.xml`, `notif_panel_duet.xml` |
| Panel secondary line | 12 sp | |
| Panel headline row | 16 sp | `notif_panel_single.xml` |
| Collapsed label line, strips | 12 sp, 13 sp | |
| Fine print (as-of, plan tag) | 10 sp | |

The Huge number layout has its own scale (13.5 and 12 sp) and the only dimming; other styles
(Gauge, Number tile, Progress bar) must not reuse its numbers.

### 2.3 Widgets, share card, Live Activity

Widget figure sizes are computed per frame bucket (a floor of fitted sizes), so a table of
constants would lie. The anchors that hold, from the approved rev D frames:

| Element | Android widgets | iOS widgets |
|---|---|---|
| Large figure (Number, Countdown) | 32 and 44 sp, stepping down 2 sp at a time to fit, never under 18 | 46 pt (40 at limit), 35 pt countdown, 28 pt strip |
| Ring bore figure | 16 / 26 sp scaled with the ring from Ø88 | `0.22 x ring diameter`, bold, monospaced |
| Window name on a row or strip | 9.5-12 sp | 13 pt row labels, 11.5-12.5 pt footnotes |
| Reset text | 10-15 sp | 11.5-14 pt |
| No-reading note | 10 sp at 55% ink | Same words, `sub` ink |

Source: `widgets/WidgetFace.kt:727-780,1351-1380`, `ios/Shared/FaceViews.swift:115-200`. See
[widgets.md](widgets.md). Share card: rendered at 4x on a 360 dp-wide card, header ring Ø88 with an
8 dp band, bar 10 dp, chart 120 dp, pad 20 dp (`share/ShareCard.kt:60-68`).

Dimming: a stale widget figure dims to 0.5 and its bar or ring to 0.45 (`WidgetFace` and the share
card `DIM_FIGURE`/`DIM_GAUGE`); iOS dims a stale face to 0.45 opacity. Source:
`ios/Shared/Charts.swift:50`.

## 3. Shared shapes

### 3.1 Bar

| Token | Android | iOS | Source |
|---|---|---|---|
| Height, app card | 12 dp (default in `UsageBarLine`) | 11 pt open card, 9 pt compact | `MainCards.kt:151`, `ios/App/UI/Cards.swift:33,134` |
| Height, pinned notification | 8 dp | n/a | `notify/PinnedNotification.kt:776` |
| Height, widgets | per frame, 4-8 dp | 4-8 pt (weekly rows 4-6) | `ios/Shared/FaceViews.swift` |
| Height, share card | 10 dp | 10 pt | `ShareCard.kt:64`, `ios/App/UI/ShareCard.swift:54` |
| Shape | full pill, radius = height / 2 (a "square" option keeps a 2 dp radius) | Capsule | `Tokens.barCornerRadius`, `ios/Shared/Charts.swift` |
| Track | the fill colour at 25% alpha, full width | the accent at 26% alpha | `MainCards.kt`, `ios/Shared/Charts.swift:25` |
| Minimum fill | fill is at least one bar height wide once usage is above 0 (anti-sliver) | same | `ui/BarRenderer.kt`, `ios/Shared/Charts.swift:22` |
| Draw order | track, fill, red slice, tick | same | `BarGeometry.kt` header |
| Fill to red boundary | straight vertical edge on the pace line; the red slice has no minimum width and covers the fill's rounded tip | same | CCRM-43 (Bar Pace Marks) rev B |

### 3.2 Pace tick: TICK against NEEDLE

Two marks exist, never mixed on one surface:

| Mark | Geometry | Used by |
|---|---|---|
| Bar tick, notification and app | width `0.31 x bar height` (minimum 2 px), overhang `0.30 x height` past each long edge, round-capped | Notification bars, in-app bars (Android) |
| Bar tick, widgets and share card | fixed 3 dp wide, 1 dp halo in face colour, overhang as the ring tick | Widget bars, share card |
| Bar tick, iOS | 3 pt wide, extends `max(3, 0.45 x height)` past each edge, 1.5 pt radius | All iOS bars |
| NEEDLE | a clock hand pinned to a hub, in 24ths of the icon box: needle 1.792 wide, halo 4.48, outer rim 11.2; hub fallback radius 1.344 | **Status-bar glyph only** |
| TICK (ring) | straight round-capped tick across the stroke: width `0.39 x stroke` (min 2.5 dp), overhang `3.5/12 x stroke` each side (min 2.5 dp), 1 dp halo | Every ring with a figure in its bore (widgets, share card) |
| Ring tick, iOS | capsule `max(2, 0.42 x stroke)` wide, `1.9 x stroke` long, centred on the stroke | iOS rings |

Source: `ui/BarGeometry.kt:37-40`, `ui/RingRenderer.kt:40-90`, `ios/Shared/Charts.swift:42,208`.

### 3.3 Ring and rails gauge

- Android draws every ring through one painter (CCRM-83 (Ring Renderer)): status-bar glyph, widget
  faces and share card cannot disagree. Draw order, bottom to top: **extent hairline, usage band,
  red slice, hub, needle, spent cross** (NEEDLE) or **track, fill, red slice, tick** (TICK).
- Status-bar glyph: 24 dp bitmap, band stroke `4/24`, band-centre radius `9.2/24`, hairline `1.493/24`
  (a fifteenth of the 22.4 diameter), spent cross arm `4.2/24` and width 2/24. Fitted by width into
  a slot of about 14 dp (37 px on a 420 dpi Fold 7), so square is optimal and non-square layouts
  lose height; numbers were chosen against the rasterised size, not a zoomed vector (CCRM-49 (Glyph
  Legibility)). Source: `ui/RingRenderer.kt:40-60`, `ui/UsageIcon.kt`.
- TICK ring ratios: hairline `0.26 x stroke` (min 1.1 dp), spent cross arm `0.32 x radius`, width
  `0.22 x stroke` (min 1.4 dp), transparent-face shadow 2 dp blur. Minimum visible fill sweep: 1%
  still reads as "started". Source: `ui/RingRenderer.kt:70-85`.
- iOS ring: track `max(1.5, 0.3 x stroke)` on faces, full-width on mono; fill round-capped; default
  stroke 7 (5 on lock screen); bore figure `0.22 x diameter` with sublabel `0.13 x diameter`.
  Source: `ios/Shared/Charts.swift:186`.
- No reading: the extent alone, never a fake 0%. At a truncated 100: the spent cross in the hollow
  (status bar always; a widget when the cross replaces the figure, CCRM-79 (Ring Face)).

### 3.4 Condition dots

- The Duet's 6 dp dot on a half's label line is a white oval recoloured at runtime; it points at
  "there is a strip for this account in the expanded panel" and carries no message. Fault hue and
  warning hue per 1.4. Source: `res/drawable/notif_condition_dot.xml`.
- Synthetic-data dots on widgets (`widget_synth_dot`) and the notification (`notif_synth_dot`) are
  the debug marker, violet `#7C4DFF` on the share card.

### 3.5 Provider mark sizes and spacing

| Place | Android | iOS |
|---|---|---|
| Card title, tab label | 20 dp | 20-22 pt |
| Chip, pinned label line | 14 dp | 14-15 pt |
| Add-account sheet | 28 dp | 26-32 pt |
| Sidebar, big header (iOS) | n/a | 22-54 pt |

Spacing constants worth reusing (Android, `MainCards.kt`): bar row top/bottom gap 4 dp under the
label and 10 dp after; 6 dp before a pace line; 10 dp before a chart; chevron 20 dp; the 7-day
card's two chart columns split with a fixed gutter. iOS pads 6-10 pt before bars and pace lines.
Chart heights (CCRM-75 (Chart Height)): Small 72 dp (lines only: no labels, axis dates, callouts or
legend), Medium 120 dp (default), Large 180-300 dp by width.

## 4. Words

### 4.1 Window naming

| Context | Session | 7-day |
|---|---|---|
| Space-tight surfaces: notification (collapsed, expanded, strips, panel rows), widgets, tile, status text, Live Activity, lock screen, share-card rows | **5h** | **Weekly** |
| Duet and multi-account rows | `<Account> . 5h`, `<Account> . Weekly` (middle dot) | |
| In-app card titles (Android) | 5-hour window | 7-day window |
| Layout sheet, History chips, History pane labels | 5-hour, 5-hour windows | 7-day, 7-day windows |
| Settings reset rows (Android) | "5h reset" | "Weekly reset" |
| iOS in-app | "5-hour window" | "7-day window" (cards); "5h" and "Weekly" in the sidebar and share card |
| Alerts and reset pings | "5h" | "Weekly" |

Rule: on any space-tight surface write "5h" and "Weekly", never "5-hour window" or "7-day"; two
accounts share the expanded panel and every label competes for width. The in-app cards keep the
long titles. Source: `notify/PinnedNotification.kt:857`, `widgets/WidgetFace.kt:43`,
`MainCards.kt:498,573`, `ios/App/UI/Cards.swift:102`.

### 4.2 Used or Left (CCRM-22 (Used or Left))

One global token. The words flip, the **bars never do**: fills, ticks, the warning ladder and the
chart's guides always key on used percent.

| Mode | Shown | Rule |
|---|---|---|
| Used | "47%" (cards: "47% used" only where the card is worded) | truncate: 99.7 shows 99, never overstating |
| Left | "53% left" | floor the exact remainder: 99.7 used shows 0 left; over-limit clamps at 0 |

Every numeric readout follows the token (worded labels, ring bores, status-bar digits, the number
tile), nothing exempt. A large figure keeps its size and the word "left" goes small after it (iOS:
42% of the figure size, minimum 10 pt). Where a headline already says "left", a secondary readout
beside it drops the word. Source: `ui/Palette.kt:422-433`, `ios/Shared/Display.swift:47-62`.

### 4.3 Reset display (CCRM-23 (Reset Display))

Two forms; the chosen one leads and the other keeps the second slot where there is room. Neither
drops out: the token decides order, not presence.

| Form | Wording | Notes |
|---|---|---|
| Countdown | "Resets in 4h 47m", "in 2d 5h" past 24 h, "in 12m" under an hour | **"Resets soon" inside five minutes** (no stale seconds on a 15-minute cadence). Android past reset: "any moment" |
| Clock | "Resets at Thu 11:45 PM" or "Thu 23:45" | Android cards always name the weekday, 5h included; iOS lowercases "pm" ("Thu 9:50 pm") |
| Widget clock (Android) | 12-hour always, "9:10 PM", weekday only when more than 24 h from now ("Sat 9:10 PM"), AM/PM dropped where it does not fit | Ignores the Time format chip |
| Time only | "11:45 PM" / "23:45" | 5h window only, where the day is never in question |
| Chart axis | 5h: clock; 7-day: "23 Jul" (a bare weekday would repeat) | |
| Window not started | "Starts when a message is sent" | CCBG-30 (Phantom Window); card keeps its bar, no chart, no pace |

Time format chip: System, 12-hour, 24-hour (system follows the locale). Compact durations use two
units at most ("3d 4h", "2h 14m", "9m"). Source: `ui/Palette.kt:259-330`,
`MainCards.kt:222-250`, `ios/Shared/CardContent.swift`.

### 4.4 Number formatting

- Percent: **truncated**, never rounded, wherever a window percent is shown ("99.7" reads 99). The
  pace delta in words is the exception: it rounds ("12% above pace").
- Credits and money: **rounded**, US-locale symbols whatever the device locale, grouping commas,
  the currency's exponent (0-6): "$9.57", "$1,234.00"; unknown code falls back to "CODE ". Billing
  figures read the same everywhere. A window and a credits percent can differ by one point on one
  screen; that is intended.
- Rate: one decimal, "1.8%/h". Plan names: spec/core API-CONTRACT §11 ("Max", "Ultra", "Pro 100", "Ultra 5x"); no
  multiplier is appended since 2026-11-02.
- Dates: "Jul 16, 2026", "Aug 13, 8:30 PM", "23 Jul". Relative past: "just now", "12m ago", "2h 10m
  ago", "never".

### 4.5 Pace phrase family

All gated by the ±3 dead zone (core §5); above pace is the only bold/red state.

| State | Open card and chart (long) | Folded card and compact line (short) |
|---|---|---|
| Above | "12% above even pace" | "12% above pace" |
| Below | "32% below even pace" | "32% below pace" |
| Within the dead zone | "On even pace" | "on pace" |
| At 100 | "Limit reached" (iOS; paired with "Opens again when the window resets") | n/a |
| Folded line | "Resets in 2h 41m . 32% below pace" (middle dot) | |
| Projection | "At this pace: ~70% when the window resets . 1.8%/h"; "At this pace: 100% at <time> - <span> before the reset" | |

The above-pace clause alone goes bold in the warning colour; the reset clause stays quiet. Source:
`ui/Sparkline.kt:822`, `ui/CompactLineCopy.kt`, `ios/Shared/CardContent.swift:80-100`.

### 4.6 Suppression and empty-state copy

A suppressed verdict states its reason, and the three reasons are worded differently (core §3.1).

| Case | iOS wording | Android wording |
|---|---|---|
| Young window | "Too early in this window to project a pace" | "Usage hasn't moved enough yet to project a pace" (single line for all three) |
| Too little usage | "Not enough usage yet to project a pace" | |
| No projection | "Not enough readings yet to project a pace" | |
| No chart samples | n/a | "Not enough history in this window yet to chart a pace" |
| Window not started | "Starts when a message is sent" | same |
| No window running (widget) | "No window running" | |
| Stale (6 h, spec/mobile section 3) | "Stale - nothing since <day time>"; widget "as of <time>" | status line "Checked 14m ago . Tried 2m ago", "Not checked yet" |
| Plan has no usage | "Usage isn't available on the <plan> plan" | Plan-refusal strip |
| Sign-in stopped | "<Account>: Sign-in stopped working" | Sign-in needed strip |

Differs between platforms: iOS words the three suppressions apart as the core asks; Android's
chart note carries one generic line plus a separate "not enough history" line. Source:
`ios/Packages/CooldownCore/.../Projection.swift:161`, `MainCards.kt:379,435`.

### 4.7 Glossary

- **Window**: one usage allowance with a reset time (5h or Weekly). **Even pace**: the diagonal from
  0% at the window's start to 100% at its reset. **Pace tick / mark**: even pace's position now.
- **Above pace**: more than 3 points past even pace. **Dead zone**: those 3 points.
- **Spent**: usage truncated to 100, drawn with an x in a ring's hollow.
- **Room**: the provider-tinted surface behind the Main and History screens. **Accent**: an
  account's colour. **Theme colour**: the global default accent.
- **Duet**: the pinned notification carrying two accounts. **Strip**: a condition strip, or the
  widget that lists up to four accounts and "+N".
- **Credits**: pay-as-you-go spend, a row with no clock and so no tick. **Plan tag**: the plan name
  beside an account, by spec/core API-CONTRACT §11's names ("Max", "Ultra", "Pro 100"); no multiplier since 2026-11-02.

## 5. Icons

- **Launcher (v2.0 branch): Shared pace** — CCRM-101 (Icon v3), designed by ChatGPT, chosen by Robin
  2026-11-02; master `design/icon-v3/shared-pace.svg` (README there). Three clockwise arcs in an open C on
  the charcoal plate `#1F1B19`, common centre (52, 50) in the master's 100-unit box: outer Claude `#D97757`
  (its excess past the pace line in red `#FF5252`), middle ChatGPT `#19C39A` meeting the line, inner Gemini
  `#8AB4F8` short of it; tracks `#524944`; a red pace line with a plate-coloured halo. Android: the mark scaled
  0.81 into the 66-unit safe circle (`ic_launcher_foreground.xml`), monochrome with tracks at α 0.4 and a real
  gap in the Claude arc at the line (no halo); About's `drawable/ic_launcher.xml` the same; iOS `icon-1024.png`
  flattened on the plate. Replaces the Pulse below on that branch.
- **Launcher (v1.9): Pulse.** An ECG trace on a flat charcoal plate `#1F1B19`: terracotta `#D97757` P bump,
  red `#FF5252` Q-R-S beat (red is only ever the beat), green `#10A37F` T bump, a dashed cream
  `#F3EBE3` ceiling, stroke 9, baseline y 64 (108 units), seven butt-capped dashes at pitch 12.5.
  Android ships the adaptive layers plus a monochrome silhouette; the iOS app icon is a single
  1024 px image (`ios/Shared/Assets.xcassets/AppIcon.appiconset`). Source:
  `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`; CCRM-60 (Dual Identity)
  round 10, variant S2.
- **Status-bar glyph**: the 24 dp rails gauge of 3.3, one window (5h) of one account; details,
  per-state rendering and the Quick Settings tile are in [notification.md](notification.md).
- **Share card**: [app.md](app.md) and CCRM-24 (Share Card); it reuses the ring and bar painters
  above at 4x with the rooms' light and dark grounds.

## 6. How a new surface should start

1. **Pick the room and tokens before drawing.** Take accent from the 1.2 resolution, ladder from
   1.4, tick ink from 1.5, type from 2, shapes from 3. Do not mint a colour or a size; if one is
   missing, add it here in the same commit.
2. **Honour CLAUDE.md section 2**: anything the user sees needs a wireframe, with real numbers (dp
   or pt sizes, what is above what, behaviour at each width), approved explicitly before code. For
   a notification, mock the Huge number style first. For a redraw of an existing element, draw it
   whole at reduced scale with every guide, label and mark it has today; only simplify when the
   simplification is the proposal, and label it so.
3. **Rasterise status-chrome and tight surfaces before judging.** A vector zoom flatters; render at
   the real pixel size (the status bar slot is about 14 dp) and magnify that.
4. **Wire the shared switches**: Used or Left, Time format, Reset time, Show red past the pace mark,
   Theme colour and per-account accent. A surface that ignores one is a defect.
5. **Show every state in the wireframe**, not the happy path:
   - empty and not-started window ("Starts when a message is sent"), no reading, no reset clock
   - signed out or sign-in needed, plan refusal, stale, error
   - 0%, 1%, below pace, within the dead zone, above pace, at 80/90 and at 100 (spent)
   - Used and Left, 12 h and 24 h, countdown-first and clock-first
   - light and dark, each provider room, one account and two or more
   - the narrow width, with the row or label that drops out and the order in which it drops
6. **Words**: "5h" and "Weekly" on tight surfaces; truncate window percent; no model credits or
   build story in user-facing copy.
7. **Tracker IDs in the doc**: CLAUDE.md section 1 applies to every tracker ID, epic name
   in brackets on first use.

## v2.0 on iOS — CCRM-96…100, RUNBOOK-v2.0 Step 6

As built on the v2.0 branch (RUNBOOK-v2.0); each entry names the section it amends, which still describes v1.9.

#### §4.2 Used / Left (the Mode table) — add a row and two notes
| Vs pace | "−13%" / "+10%" / "0%" (minus U+2212) | core §6.3 `vsPace`, toward zero (`sign(d) × floor(|d| + 1e-9)`); falls back to the Used figure when spent (≥ 99.5), unclocked or unread (core §6.1, mobile §5) |

- iOS stores Vs pace as its own Bool `display.usageVsPace` (App Group); choosing it clears `display.usageLeft`, so a
  v1.9 downgrade reads Used. `Display.mode`, `Display.setMode`, `ios/Shared/Display.swift`.
- iOS: in Vs pace mode a headline figure carries a small "vs pace" where Left puts "left" (42% of the size, min
  10 pt); bare figures (rings, Summary rows, Island compact) show the signed figure alone. **Companions always show
  usage**, bare in Left mode ("5h 66%"), never a distance (`Display.usage`).

#### §4.5 Pace phrase family — iOS column replaced (the ±3 dead-zone phrases are retired on iOS)
| State | iOS open card | iOS folded line |
|---|---|---|
| Weekly, a band | "Slow down · ≈11%/day until Sun" (halves drop " until …") | "Slow down · ≈11%/day · resets Sun" |
| Weekly over pace (Slow down, Nearly out) | whole line red | whole line red |
| Weekly spent (≥ 99.5) | "Out until Sun 9:30 pm" red + "Opens again when the window resets" | "Out until Sun 9:30 pm" |
| Allowance | "≈11%/day", "<1%/day", "40% left, 6h", "40% left, <1h" (under 30 min) | same |
| 5h | no pace line; the "At this pace …" projection line stays; "Limit reached" at 100 | "Resets in 2h 20m · on track for ~69%" / "· runs out ~3:25 pm" (red) / "· Limit reached" |
| Pace-mark caption (V1), under every in-app bar (5h, Weekly, model half, Summary row) | "39% pace mark · 13 over" / "· 19 under" / "· on it"; Left "61% pace mark · 13 over" / "· 10 to spare"; Vs pace "52% used · 39% pace mark" (core §6.3, since CCRM-104); 11.5 pt (10.5 in a half), first word bold; starts 5 pt right of the mark, else ends 5 pt left; none when spent, unclocked or unread | none |
| Dashed room | under pace by ≥ 1 point, a 1 pt dashed outline (sub at 80%) from the fill to the mark, in-app bars only | |
Source: `ios/Shared/CardContent.swift`, `ios/Shared/Charts.swift` (`UsageBar.showsRoom`, `PaceCaption`),
`ios/Packages/CooldownCore/Sources/CooldownCore/PaceText.swift`. The signed Pace is never printed on iOS.

#### Bars (§ "Height, app card" row) — append
"Summary row bar 10 pt; one-line Summary row (6+ accounts) 7 pt, 50–110 pt wide."

## Gemini and the spent-week gate on iOS — CCRM-55, CCRM-102

As built on the v2.0 branch (RUNBOOK-v2.0 Steps 6a and 6c, iOS).

- **§1.2 Providers table, Antigravity row:** replace "**Android only: iOS ships Claude and ChatGPT**" with "iOS: `GeminiMark`
  (asset `ios/Shared/Assets.xcassets/GeminiMark.imageset`, the same spark as `ic_provider_gemini`, template-rendered,
  drawn at 100% of the box); Gemini Blue `#4285F4` light / `#8AB4F8` dark (`ios/Shared/Theme.swift`)". The provider's
  display name is "Gemini" everywhere on iOS.
- **Theme colour, "Per provider" swatch (iOS):** three equal bands (Claude, ChatGPT, Gemini), was two (rev B §6 default).
- **§4.5 / copy, new iOS rows:**
  | Weekly spent, 5h shown | card: figure, bar, chart (no projection) and reset line at 45%, no caption; one full-ink line "Weekly limit reached — 5h can't be used until Thu 10:21 pm" (weekly reset, `dayClock`) | widgets/faces showing the 5h: dimmed to 45%, stamp "Weekly spent" at full opacity |
  Source: `CardContent.weekGateNote`, `CardContent.weekSpentStamp`, `WindowCard.weekGate`.

## The shape grammar — CCRM-103 (Hybrid Notification), RUNBOOK-v2.0 Step 6d

Frozen 2026-11-02 at rev D of `design/2026-11-02-shape-grammar.html`. It supersedes the per-window shapes above
wherever the two disagree; the rules are in BEHAVIOR-MOBILE §5 ("Shape grammar").

- **Two shapes, one meaning each.** The 5-hour window is a hollow ring, always on the left; the 7-day windows are
  bars, always on the right. No "5h" text anywhere; a ring's accessibility label is "5-hour window, 34 percent"
  ("No 5-hour window" for the cross).
- **The ring** (Android `ui/ShapeGrammar.kt GrammarRing`): centre-line radius 0.39 S; band 0.13 S, 0.10 S at 48 dp
  and under (never under 3 dp); track ink 16% (22% greyed); fill the account colour with the §1.4 step, round cap
  only under Round, at least 9% when non-zero; the red slice from the tick to the fill tip more than 3 points over;
  the pace tick a radial line from R − band to R + band at max(1.5, S/32) dp, ink 90%; the dashed room under the mark
  from the fill's visible end to the tick, in the account colour at 60%, line max(1, S/60), dashes max(1.6, S/26),
  dropped under 28 dp; the open app tile's dotted projection arc. The figure is a real text: 0.25 S (0.29 S at 48 and
  under; four characters 0.21 / 0.24 S), fitted to clear the band's inner edge by 1.5 dp; ink, or the severity colour
  above 90. States: greyed (a spent week, CCRM-102 (Spent Week Gate)): the track only, the figure at 45%; not
  applicable (a plan with no 5h): the track and a cross (arms 0.12 S, ink 45%), no figure; no reading: the track and
  "—".
- **Figures** (rev C §1c, rev B §1b): the system sans with tabular, lining digits (`tnum`, `lnum`; iOS
  `.monospacedDigit()`), the "%" at 58% of the digits' size on the baseline.
- **Usage end mark** (rev D §1e): Round (E0, the pre-v2.0 cap) · Line (E1, default) · Pace colour (E2b). Line and Pace
  colour end the fill square at the usage and add a short line: on a bar 1.5 dp wide, 1.5 dp past each edge; on a
  ring a radial line 0.35 of the band past each edge, max(1.3, S/40) dp; both over a 1 dp gap in the surface colour.
  Line is the fill's colour; Pace colour is ink inside the ±5 on-pace band, green under, red over.
- **Vs pace figures and colours** (CCRM-104 (Pace Line Parity), core §6.3 "Printed form", replacing rev D §7b's
  unsigned figure): signed everywhere — "+19%" green under the mark (dark #9CCC65, light #2E7D32), "−12%" (U+2212)
  red over it (#FF5252 / #C62828, the bar's red), "0%" plain ink — **except inside a 5-hour ring**, where the figure
  stays unsigned and the colour alone gives the side. A fallback usage figure keeps its severity colour. The
  notification's "UNDER PACE" / "OVER PACE" / "ON PACE" caption is gone (the sign carries the side). Wireframe
  `design/2026-11-06-mobile-pace-line-wireframe.html`.
- **The pace line** (CCRM-104): every Weekly band line ("Slow down · ≈11%/day · resets Sun") is now core's short line,
  "6d 23h behind · Wed 12:30 PM" / "13h 59m ahead · Thu 6:15 PM" / "On the mark · Sat 8:00 AM", duration and word in
  the tone. Narrow columns drop the weekday, then the clock; the duration and word are the floor (wireframe D10).
  Band words and "≈X%/day" survive only in alerts. Per-surface sizes are in app.md, notification.md and widgets.md.
- **Status-bar glyphs** (Android, rev D §4): the 5h ring (unchanged geometry, §3.3); a red cross alone (arms 7 of 24
  units, stroke 3.4) while out; the ring greyed (track 30%, arc 40%, no needle) while the week is spent; N3, the
  Weekly as a 21 × 10-unit capsule with its tick in a cleared halo.

## v2.0 feedback on Android

Step A1 (2026-11-11, RUNBOOK Lane A), built on Android:

- **§1.5 chart tokens — grid ink (CCRM-107 (Day Grid))**: charts `onSurfaceVariant` at 13%, 1 dp solid
  (`GRID_ALPHA`); the Pace map ink at 10% (`MAP_GRID_ALPHA`). The quietest line on a chart: below the axis (18%) and
  the dashed 80/90/100 guides. Ordinals: 9.5 sp (map 10 sp), the now-column one bold at 95% ink.
- **The pace line (CCRM-108 (Reset Named))**: the clock after the dot is named, "6d 23h behind · resets Wed 12:30
  PM", "On the mark · resets Sat 8:00 AM". Forms, widest first: another day "· resets Wed 12:30 PM" → "· Wed 12:30 PM"
  → "· Wed" → the floor; later the same day "· resets 11:00 PM" → "· 11:00 PM" → the floor. The floor (the duration
  and its word, or "On the mark") is never cut; below it the 10 sp step-down and the ellipsis are unchanged.

Step A2 (2026-11-11, RUNBOOK Lane A), built on Android — CCRM-105 (Duet Stack):

- **§2.2 Pinned notification sizes:** collapsed figure, Duet row **18 sp** (was 30 / 28 per half), right-aligned in a
  42 dp slot; expanded figure, Duet header **24 sp** (was 36). Collapsed Duet name 12 sp, state word 11 sp.
- **§2 "true sp", one stated exception:** the collapsed Duet's text is sized in dp and capped — figure 18 dp, name
  13.5 dp, state word 12 dp — as the ring's bore figure already is (Robin, 2026-11-10). Its expanded view scales.

## v2.0 feedback on iOS

Step I1, built 2026-11-11 at 7d479ba (wireframe v2.0 feedback rev B §3–§4).

- **Grid ink** (CCRM-107 (Day Grid)): `line` at 60%, 0.5 pt — the quietest line on a chart, under the guides (dashed
  80/90/100 at 70%), the axis (`line`, 1 pt) and the diagonal. Used by `TrendChart` and the Pace map.
- **Grid ordinals**: 9.5 pt regular `sub` on a chart's axis strip (bold `ink` in the column holding now); 10 pt `sub` on the
  Pace map. The end labels stay 11 pt (chart) and 10 pt (map) and are never bold.
- **The pace line** (CCRM-108 (Reset Named)): the reset reads "resets <weekday> <clock>"; the drop order is "resets", then
  the clock (another day keeps its weekday to the last), then the floor, then 0.8 size (`PaceLine.Drop`: none, word, clock,
  floor).
