# The app — in-app screens as shipped

Covers the in-app screens of the Android app and the iPhone/iPad app: the main screen (tabs, cards, trend chart, status line, notices), Settings, History, the sign-in flows, the layout sheet, the share card and first launch. Android first, iOS alongside.
Describes the apps as shipped; the approving wireframe for each part is linked, and where the code and the wireframe disagree the code wins and a "Differs from" line says so.
When you change a surface, update this file in the same commit (CLAUDE.md §2 still requires the wireframe first).
Not covered: the always-on notification, widgets, Live Activity and Control Center (see their own files in this folder), and the planned CCRM-96 / CCRM-97 changes, which are not built.

**Conventions.** dp/sp are Android, pt is iOS. "Muted" is `onSurfaceVariant` on Android and `Palette.sub` on iOS. Android type is the Material 3 default scale (labelSmall 11 sp, bodySmall 12, bodyMedium 14, bodyLarge 16, titleSmall 14 medium, titleMedium 16, titleLarge 22); the app sets no custom Typography. Paths are relative to the repo root; Android is under `app/src/main/java/com/robin/claudeusage/` (written `A/`), iOS under `ios/` (written `I/`).

---

## 1. Shared vocabulary

### 1.1 Colours

| Token | Android | iOS | Rule |
|---|---|---|---|
| Accent | `Palette.accentName`: the account's own accent, else the global Theme colour unless "Per provider", else the provider colour. Claude `#D97757` light / `#E59980` dark; ChatGPT `#10A37F` / `#19C39A` | `Display.accentName(for:)`, same three levels; `Provider.accent` | `A/ui/Palette.kt:85`, `I/Shared/Display.swift:68` |
| Bar fill by severity | accent up to 80%; above 80% yellow `#F9A825` light / `#FDD663` dark; above 90% orange `#F57C00` / `#FFA726`; at 100% red `#C62828` / `#FF5252` | same hex values | `A/ui/Palette.kt:141`, `I/Shared/Theme.swift:27` |
| Over-pace red | red from the pace tick to the fill's end, when used > even pace + 3 points and the Settings switch "Show red past the pace mark" is on | same | `A/ui/BarGeometry.kt:62`, `I/Shared/Charts.swift:25` |
| Surface and card | Claude tab: light `#F5EFE8` surface / `#FCF8F4` card; dark `#0D0D0D` / `#1A1A1A`. Every other provider: light `#F7F7F8` / `#FFFFFF`; dark `#0D0D0D` / `#1A1A1A`. Main and History only; Settings and the guide keep Material defaults | iOS palette, both tabs the same: dark bg `#0E0E0E` card `#1C1C1E`; light bg `#F2F2F7` card `#FFFFFF` | `A/ui/Rooms.kt:17`, `I/Shared/Theme.swift:11` |
| Headline typeface | Claude tab: system serif for the percentage on every card row; other tabs sans | sans everywhere | `A/ui/Rooms.kt` (`serifHeadline`) |

Differs from `design/dual-identity-wireframe.html` (rooms): the dark Claude room is neutral black, not warm brown (CCRM-76 (Black Room), 2026-09-17); iOS never adopted the rooms or the serif.

### 1.2 Pace vocabulary

| Term | Rule | Source |
|---|---|---|
| Even pace | percent of the window elapsed, 0 at window start, 100 at reset | `A/ui/Palette.kt:461` |
| Dead zone | 3 points either side of even pace; inside it the verdict is "on pace" and nothing turns red | `A/ui/Palette.kt:22` |
| Above pace | used > even pace + 3 (strict) | `A/ui/Sparkline.kt:62` |
| Used / Left | Settings "Usage display"; flips only the words and figures. Bars, colours, guides and the pace tick always show what is spent. Used truncates, Left floors the remainder, over-limit clamps Left at 0 | `A/ui/Palette.kt:420-430`, `I/Shared/Display.swift:50` |
| Reset wording | countdown "Resets in 2h 41m" (days form "Resets in 1d 3h"); "Resets soon" inside 5 minutes; clock "Resets at Thu 9:50 PM". Settings "Reset time" decides which leads; the other keeps the second slot where there is room | `A/ui/Palette.kt:311`, `A/MainCards.kt:222` |
| Window not started | no reset time: "Starts when a message is sent", no chart, no pace, no projection (CCBG-30 (Phantom Window)) | `A/MainCards.kt:222`, `I/Shared/CardContent.swift:44` |
| Pace line (v2.0, both platforms) | the Weekly line names the reset (CCRM-108 (Reset Named)): "21h 50m ahead · resets Sun 5:08 PM", "1d 18h behind · resets Sun 10:17 AM", "On the mark · resets Sat 8:00 AM"; the duration and word are the tinted span (semibold; red ahead, green behind, ink on the mark), the rest the surface's ink. Drop order by measured width, one everywhere: another day "· resets Sun 9:30 PM" → "· Sun 9:30 PM" → "· Sun" → the floor (the span alone, never cut); a reset later today "· resets 11:00 PM" → "· 11:00 PM" → the floor. A bare clock always means today. Below the floor Android steps the text to 10 sp, then ellipsizes; iOS steps the floor to 0.8 of its size (`FitLine`, four variants). Where: the tile's folded and open pace lines and the Summary's line 3; spent, held ("Held · back at … · resets Fri"), signed-out, unclocked and no-reading lines are unchanged; the one-line Summary row's word slot is still the tinted span alone and the share card's Weekly row keeps "Resets …" in its right slot. iOS prints this line's clock "AM"/"PM" while its card lines (reset row, "Out until Fri 1:48 pm") print "pm" in lower case — filed as CCBG-82 (Clock Case Mix) | Android `PaceText.paceLineForms`; iOS `I/Shared/PaceLineView.swift` (`FitLine`) |

---

## 2. Main screen

### 2.1 Frame

Android, top to bottom: optional synthetic-data banner, top app bar, [app-wide notices], [account tab strip], pager of accounts. Approved by `design/multi-account-wireframe.html` (tabs), `design/2026-09-17-main-screen-redesign.html` rev E (cards, density, chart height, layout), `design/provider-identity-wireframe.html` and `design/dual-identity-wireframe.html` (title marks, accent).

| Element | Size and rule | Source |
|---|---|---|
| Synthetic banner | 34 dp tall, `#9A6B1E` fill, text "SYNTHETIC DATA · tap to turn off" 11.5 sp bold `#1A1400`, letter-spacing 0.35 sp; whole bar taps to turn it off. Main only, debug-only | `A/MainActivity.kt:351` |
| Title | The app's identity (Robin, 2026-11-02): Claude, ChatGPT and Gemini marks, always all three, 20 dp each in its provider colour (the drawables' own optical sizing: Claude at 82% of its box, the ChatGPT blossom ~93%, the Gemini spark 93%), 4 dp apart, 8 dp gap, text "Cooldown". Where the three do not fit beside the text in the width the top bar leaves (measured: 76 dp of marks and gaps + the text's measured width; about 180 dp, so a 320 dp phone falls back), the app's own "Shared pace" tile (`ic_app_mark`, 24 dp, CCRM-101 (Icon v3)'s master on a rounded plate) stands in for the three. Other screens show a plain text title: "Settings", "Usage history" | `A/MainActivity.kt` (`AppTitle`, `AppTitleMarks`) |
| Actions | History (calendar icon, "Usage history"), Settings (gear, "Settings"), then the ⋮ ("More options") only when the selected account has a reading | `A/MainActivity.kt` (`canShare`) |
| ⋮ menu | "Main screen layout" (only when the account has two or more cards), "Share snapshot"; on the All tab one item, "Share snapshot" (the Summary snapshot, §8; hidden when no shown account has a reading) | `A/MainActivity.kt:425` |
| Content column | horizontal padding 20 dp, capped at 760 dp wide (`ChartColumnMaxWidth`), centred, vertical scroll; 16 dp above the first card, 24 dp below the last | `A/ui/Adaptive.kt:93,184`, `A/MainActivity.kt` (`ProfileTabs`) |
| One account at a time | at every width, tabs and swipe included; a wide window never splits into two profile panes | `A/MainActivity.kt:543` |
| Back | system back walks Settings/History → Main instead of exiting | `A/MainActivity.kt` (`BackHandler`) |

iOS frame (`I/App/UI/MainScreens.swift:70`): a navigation stack; toolbar trailing: calendar (History, "Usage history"), gear (Settings), ellipsis menu (Refresh, Layout, Share card, Add account). Title is a 34 pt bold row (30 pt in the iPad landscape sidebar header): the Claude, ChatGPT and Gemini marks (each 0.76 of the title size, 0.12 apart), "Cooldown" — always all three since 2026-11-02 (Robin); a `ViewThatFits` falls back to the app's "Shared pace" tile (`AppMark`, 0.91 of the title size — 1.2 × a mark, as on Android) where they do not fit (a narrow Split View, a tight sidebar). The marks carry Android's optical sizing in their SVGs (Claude 82%, the ChatGPT blossom re-centred at ~93% — before, iOS drew it off-centre at 69%, visibly smaller — Gemini 93%), so they read one size everywhere, the native tab icons included. Toolbar items hide when there are no accounts. Refresh is also pull-to-refresh on every scroll view.

Differs from the Android frame: iOS puts Refresh, Add account and Layout in one ellipsis menu; Android has no Add account on Main (it lives in Settings) and puts refresh in the status line.

### 2.2 Layout by width

| Class | Android | iPhone | iPad |
|---|---|---|---|
| Width classes | COMPACT < 600 dp, MEDIUM 600–840, EXPANDED ≥ 840 (`A/ui/Adaptive.kt:ProvideWidthClass`). Fold cover ≈ 411 dp, inner ≈ 750 dp (MEDIUM) | one account per screen, swipe between accounts | landscape ≥ 1000 pt wide: sidebar ("All accounts" plus one row per account) and the Summary or only the tapped account, full width. Portrait or Split View ≥ 700 pt: the phone's model, tabs for All and every account, one page at a time. Narrower than 700 pt: the iPhone layout. No pinned account since 2026-11-02 (Robin) |
| Cards per account | one column at every width; the 7-day card runs two stacks side by side when the card has a model cap and the window is ≥ 700 dp (`hasTwoColumns`) | one column; 7-day card pairs All with the first cap side by side | `twoAcross`: the account's windows sit side by side in a row |
| Chart width | cover ≈ 339 dp (411 − 40 − 32); inner ≈ 678 dp (750 − 40 − 32), or ≈ 331 dp per stack in the two-stack 7-day card | card width, Large height keyed to it | card width 356 (portrait) or 396 (landscape) |

### 2.3 Account tabs

| Property | Android | iOS | Source |
|---|---|---|---|
| Visibility | none with one account (no strip, the ~48 dp is given back); tab only for accounts holding a token | strip shown whenever there are accounts | `A/MainActivity.kt:553`, `I/App/UI/MainScreens.swift:168` |
| Label | provider mark 20 dp in the account accent, 6 dp gap, account name (max 16 characters); label text neutral (`onSurface`, unselected 60% alpha), never the accent, to avoid a mid-swipe colour crossfade | mark 20 pt, 7 pt gap, name 16 pt medium; selected ink, unselected sub | `A/ui/ProviderMark.kt:54` |
| Indicator | secondary indicator in the selected account's accent | 3 pt capsule in the account accent under the selected tab | same |
| Fit | the shared rule (CCBG-81 (History Tab Wrap), Robin's option B, approved 2026-11-11, `design/2026-11-11-ccbg81-history-tab-strip.html`): an account strip is fixed (equal shares) while every tab's label fits its share on one line, and scrolls once one would not — it follows the names, the width and the font size, not the account count. Android as built: Main's and History's strips are one composable; each tab's label (mark + name, "All" included) is measured at its one-line width in titleSmall plus the tab's 32 dp padding; while all fit their equal share it is the fixed `TabRow`, else the `ScrollableTabRow` (edge padding 0). History at 360 dp with three accounts scrolls ("Personal" on one line); the cover keeps three short names in fixed thirds; a long name with two accounts scrolls instead of wrapping, on Main too; on the cover and the inner screen Main's strip is fixed whenever its labels fit (it used to scroll from four tabs). `FIXED_TAB_LIMIT` is gone. Captures `design/research/2026-11-v20-build/A6/ccbg81/` | iOS differs today: Main uses native tabs ("Native tabs" below). The History strip (`AccountTabs`, `I/App/UI/MainScreens.swift`) is one line at a fixed 16 pt (`lineLimit(1)`, no Dynamic Type): 1–3 accounts in equal shares, scrolling from four (tabs ≥ 110 pt). It cannot wrap: "Personal" fits whole on the folded iPhone Duo (127 pt per tab) and the iPhone 17e (130 pt); below a column of about 354 pt a label would truncate ("Perso…") instead of the strip scrolling — filed as CCBG-88 (Tab Strip Truncates). Source `design/2026-11-11-iphone-duo-pass.html` §5 | `A/ui/ProfileTabStrip.kt`; `I/App/UI/MainScreens.swift` (`AccountTabs`) |
| Selection | keyed on the account key, not position, so reordering accounts in Settings keeps the same account on screen | `selectedKey` | `A/MainActivity.kt:557-600` |
| Reduce motion | tab tap jumps instead of animating when the system animator scale is 0 | `withAnimation` | `A/ui/Adaptive.kt:Motion` |

iPad sidebar (landscape, 300 pt wide): large title 30 pt, a segmented "5h" / "Weekly" switch (remembered), one row per account (mark 22 pt, name 16 pt, plan chip 11 pt, 5 pt-high bar with pace tick, right-aligned percent in the severity colour), selected row tinted with the account accent at 18%, the Footer at the bottom. A plan with no 5-hour window always shows Weekly and labels the percentage "Weekly". Approved by `design/2026-10-24-ios-sidebar-window.html`; the pin glyph, the "Touch and hold an account to pin it to the top." note and the "Pin to the top" menu are gone since 2026-11-02 (Robin: no pinned account), and the shape grammar rows below supersede the switch.

iPad account page header (21 pt bold name, mark 22 pt, plan chip 12 pt, credits text right-aligned: "Usage credits $12.00 / $40.00 · 30%" or "Credits $4.20 balance"). Since 2026-11-02 the iPad shows one account at a time, full width, or the Summary — no pinned half, no "Pinned" label, no divider.

### 2.4 Cards, in order

The order, visibility and fold come from the per-account layout (section 4). Default order is 5-hour, 7-day, Usage credits. A card the account has no data for is omitted: no placeholder, no dash (CCRM-56 (Provider Identity) decision 6). 12 dp between cards (Android), 12 pt (iOS).

| Card exists when | Android | iOS |
|---|---|---|
| 5-hour | the reading has a session window | same |
| 7-day | the reading has a weekly window or any model cap | the reading has a weekly window |
| Usage credits | credits are reportable (a cap, or real spend, or a balance) and the account's "Show for <name>" switch is on | same, plus iOS needs a limit or a balance |

If none exist Android says "This account reports no usage windows right now." (bodyMedium, muted). iOS shows nothing under the notice.

#### 2.4.1 Card shell

Android: Material `Card`, default shape (12 dp radius), `surfaceContainer` fill (the room's card colour), inner padding 16 dp (`A/MainCards.kt:CardShell`). In Compact the whole card is the tap target (not just the chevron). iOS: `CardBox`, 18 pt continuous radius, 16 pt padding, `Palette.card` (`I/App/UI/Cards.swift:5`).

#### 2.4.2 Row style (shared by every window row)

| Part | Android | iOS |
|---|---|---|
| Label | left, bodyMedium, muted; "5-hour window", "All", or a cap name such as "Fable" | left, 17 pt (5-hour card), 16 pt (7-day halves), `txt` |
| Figure | right, bodyMedium bold, onSurface; Used "14%" (no "used"), Left "86% left"; serif on the Claude tab | right, 19 pt bold (5-hour), 17 pt bold (7-day half); Left shows "86%" with a smaller "left" word (42% of the size, min 10 pt) |
| Credits figure | "No cap" when there is no cap | "Credits" row when only a balance |
| Chevron (Compact only) | 20 dp, muted, after the figure with a 6 dp gap; "Expand" / "Collapse"; ▾ closed, ▴ open | 15 pt semibold chevron.down / chevron.up |

Source: `A/MainCards.kt:260`, `I/App/UI/Cards.swift:19-60`.

#### 2.4.3 The usage bar

| Property | Android | iOS |
|---|---|---|
| Height | 12 dp track; the box is taller (height + 2 × 0.3 height ≈ 19 dp) so the tick can overhang | 11 pt (5-hour), 9 pt (7-day halves), 5 pt (iPad sidebar), 10 pt (share card); box = height + 2 × max(3, 0.45 height) |
| Track | fill colour at 25% alpha, fully rounded | accent at 26%, capsule |
| Fill | severity colour, fully rounded; minimum visible width = bar height on iOS | same |
| Over-pace red | red segment inside the fill's clip, starting exactly on the pace tick, no minimum width, straight vertical boundary, covers the rounded tip | same, with a flat edge at the tick |
| Pace tick | width max(2 px, 0.31 × height), full height plus 0.3 × height overhang each side, onSurface at 60% (dark) / 48% (light); always drawn when there is a percent and a reset clock; drawn last | 3 pt wide, extends max(3, 0.45 height) each side, `Palette.tick` (`#C7C7CC` dark / `#8E8E93` light) |
| No clock | no tick (credits row: money has no clock) | no tick |

Source: `A/MainCards.kt:152`, `A/ui/BarGeometry.kt`, `I/Shared/Charts.swift:6`. Approved by `design/bar-pace-marks-wireframe.html` and `design/pace-marks-wireframe.html`.

#### 2.4.4 5-hour card

Anatomy, Comfortable (Android): row (2.4.2) · 8 dp · bar · [10 dp · trend block (section 3)] · 8 dp · reset row. Reset row: leading form left, other form right, bodySmall muted (`A/MainCards.kt:222`); no reset time → one line "Starts when a message is sent".

iOS: row · bar (12 pt gap) · 12 pt · chart · 6 pt · pace line · 4 pt · projection line · 10 pt · reset row, text 14.5 pt `txt` (`I/App/UI/Cards.swift:19`). Reset row is "Resets in 3h 0m" left, "Resets at Sat 7:43 pm" right.

| State | What shows |
|---|---|
| Comfortable | everything above; chart open |
| Compact folded | title row with chevron, bar, then one line (below). Android: bodySmall muted, 8 dp under the bar; iOS: 14.5 pt |
| Compact expanded | identical to Comfortable; the open state is remembered per card per account |
| Behind More | always drawn as Compact, collapsed to begin with; opening it is session-only and never written to the saved layout |
| No reset time | bar plus "Starts when a message is sent"; no chart, no pace |
| At 100% | bar red; Android keeps the pace readout and drops the projection line; iOS replaces them with "Limit reached" (red) and "Opens again when the window resets" |
| No history yet | Android: the line "Not enough history in this window yet to chart a pace" replaces the chart and pace lines; iOS: chart draws from the current reading alone |

The folded line (`A/ui/CompactLineCopy.kt:38`): "Resets in 2h 41m · 32% below pace"; "Resets in 2h 41m · 12% above pace" with only the pace clause bold in `barFill(95)` (orange); "Resets in 2h 41m · on pace"; when the reset form is Clock time, iOS leads with "Resets at Sat 7:43 pm"; no reset time: "Starts when a message is sent". The short forms "above pace" / "below pace" are used here and the long "above even pace" under the chart; both use the same 3-point dead zone. iOS draws the whole folded line red when above pace (`I/Shared/CardContent.swift:118`).

Defaults on first run: Android Comfortable shows every card open, Compact folded. iOS Comfortable opens the 5-hour card and keeps 7-day folded on iPhone, both open on iPad; a weekly-only plan opens its Weekly card instead (`I/App/UI/Cards.swift:218`).

Differs from `design/2026-09-17-main-screen-redesign.html` §1 (Comfortable is a strict no-op on the cards): true on Android; iOS Comfortable folds the 7-day card by default (iOS wireframe rev B §1).

#### 2.4.5 7-day card

Android anatomy: title "7-day window" (titleSmall, chevron beside it in Compact) · 10 dp · "All" row + bar (4 dp gap, 10 dp after) · one row + bar per model cap (name from the server, e.g. "Fable") · [chips] · 10 dp · trend block of the selected series · 2 dp · divider · 8 dp · reset row for the selected series. The bars never change with the chart toggle.

| Part | Rule | Source |
|---|---|---|
| Chips | `FilterChip` row, 8 dp gap, "All" plus one chip per cap; shown only when the account has a cap; selects which series the chart, pace line, projection and reset row follow; stored per account, survives collapse; a stored name the account no longer has falls back to All | `A/MainCards.kt:717`, `weeklyChartSelection` |
| Two stacks | (v1.9 card; on Android the account tile replaced it, so its "All" row label no longer exists — v2.0's chip is "7d", see the tile) when the card is open, has a cap, and the window is ≥ 700 dp: All on the left, one cap on the right, 16 dp gutter, each a full stack (row, bar, chart, pace, projection, divider, reset row). With two or more caps the right stack carries the chip pair choosing the cap (no "All" chip); with one cap there is no toggle. "All" selected resolves to the first cap | `A/MainCards.kt:634` |
| Compact folded | title, every bar (All plus caps), then one line for All only: "Resets in 3d 4h · 8% below pace" | `A/MainCards.kt:547` |
| Weekly only | a plan with no 5-hour window shows this card first; the account tab and sidebar show Weekly | |

iOS (`I/App/UI/Cards.swift:87`): title "7-day window" with chevron; with a cap, two halves side by side (16 pt gap): "All" and the first cap with "Claude " stripped from its name; each half has its own row (16 pt label, 17 pt bold figure), 9 pt bar, chart (height min(chart height, 120) with a cap), pace line 13.5 pt, projection line, reset line (short form: leading reset only). Without a cap one full-width half. Folded: title, both halves' rows and bars, then the folded line for All at 13.5 pt. Since v2.0 the left half reads "7d" (CCRM-106 (7d Chip)); the card (`WeeklyCard`) is kept in code but placed by no layout since CCRM-103 (Hybrid Notification).

Differs from `design/2026-09-17-main-screen-redesign.html` §4/§8 on iOS: there are no All/cap chips; the cap half shows no history chart samples (iOS passes no history for the cap), only its bar and reading.

#### 2.4.6 Usage credits card

Identical in both densities; never folds, no chevron, no tap target except the figure's provenance note on Android. Source: `A/MainCards.kt:751`, `I/App/UI/Cards.swift:154`.

| State | Row text | Bar | Footer line (bodySmall) |
|---|---|---|---|
| Capped, money left | "Usage credits" · "$9.57 / $40.00" · "24% ⓘ" (bold figure) | accent bar by severity, no pace tick | "$30.43 left · covers you when you hit your plan limits" |
| Capped, all spent | same, 100% | red | "All credits spent — nothing left to cover plan overruns" (error colour) |
| No cap, spend | "Usage credits" · "$9.57 spent" · "No cap" | none | "No monthly spend limit — credits cover you when you hit your plan limits" |
| Balance only (ChatGPT) | "Usage credits" · "$12.40 balance" · "No cap" | none | "Covers you when you hit your plan limits" |

Tapping the percentage toggles the provenance note "Computed from the exact amounts — finer than the server's rounded figure, not an estimate." (pill, 8 dp radius, 9/6 dp padding, 8% muted fill). The percentage is rounded, where window figures truncate.

iOS (2026-11-05, parity wireframes rev B §5): the same card — "Usage credits" 16 semibold, the amounts (scale to 0.8), the figure 17 bold with an info glyph (tap toggles the provenance pill, session-only), a 9 pt bar with no pace tick (severity colour; red and a red footer when all credits are spent), and the footer line, in Android's three shapes: capped ("$9.57 / $40.00" · "24%" · "$30.43 left · covers you when you hit your plan limits"), all spent ("All credits spent — nothing left to cover plan overruns"), and no cap / balance (no bar, no glyph: "$9.57 spent" · "No cap" · "No monthly spend limit — …"; "$12.40 balance" · "No cap" · "Covers you when you hit your plan limits").

### 2.5 Status line, messages and the More disclosure

| Element | Android | iOS | Source |
|---|---|---|---|
| Status line | one row 16 dp under the last card: "Checked just now", "Checked 14m ago", "Checked 14m ago · Tried 2m ago" (second clause only when the last attempt failed after the last success), "Not checked yet"; 12.5 sp muted; a 15 dp refresh glyph, or a 15 dp spinner while refreshing; the whole row is the refresh button, disabled while running | Footer: "Checked 14m ago", "Checking…", "Not checked yet", 15 pt `sub`, 14 pt clockwise-arrow glyph; also pull-to-refresh | `A/ui/StatusLineCopy.kt:25`, `I/App/UI/MainScreens.swift:203` |
| Refresh floor | a manual refresh inside 180 s of the last success is refused; the refusal text appears in bodyMedium under the status line | same floor | `spec/mobile/BEHAVIOR-MOBILE.md` §1 |
| More | "More ▾ · 2 cards" / "More ▴ · 1 card", bodySmall muted, 6 dp vertical padding, full-width tap; opens the folded cards below, 8 dp apart, each in Compact form | "More ▾ · 2 cards" 15 pt `txt` with a small triangle glyph; iPad shows folded cards inline instead | `A/MainCards.kt:859`, `I/App/UI/Cards.swift:229` |

Approved by `design/2026-09-17-main-screen-redesign.html` §2 and §9.

### 2.6 Notices above the cards

Android order (`A/MainActivity.kt:566-583`): app-wide stack above the tab strip and outside the pager, in the slot 16 dp side padding, 12 dp above and 4 dp below, width capped at 720 dp: first the degraded-store notice, then the store-change card, then a waiting crash card. At compact height (< 480 dp, a phone in landscape) the stack moves into the open page's scrolling column. Per-account notices sit under the status line.

| Notice | Where | Anatomy | Copy (verbatim) |
|---|---|---|---|
| Degraded store, no held change | app-wide | tinted surface (orange `barFill(95)` at 11%, 12 dp radius, padding 12 / 10 dp): title bodyMedium semibold orange; body bodySmall; evidence line bodySmall monospace orange 80%; "Try again" text button when the store can retry (spinner while checking). Not dismissable | title "Cooldown can't open your saved sign-ins"; body "Android's secure storage isn't responding. Anything saved is still on this phone, untouched, and comes back once storage answers. Cooldown keeps checking. A sign-in or sign-out made now lasts only until the app restarts."; evidence "<error> · checked 9:41 AM" |
| Degraded, reset failed | app-wide | same | title "Cooldown couldn't set up secure storage"; body "Android's secure storage isn't working, so sign-ins can't be saved. A sign-in made now lasts only until the app restarts, which tries again."; evidence "Reset failed · <error>" |
| Degraded, a change is held | app-wide | same, no retry | title "Changes made now last only until Cooldown restarts"; body "Secure storage wasn't responding, so what you just changed is held in memory only. It's undone when Cooldown restarts, and the sign-ins saved before come back."; evidence "Held in memory since 9:41 AM" (reset-failed variant: "Secure storage isn't working, so what you just changed is held in memory only. It's undone when Cooldown restarts.") |
| Store change card (next launch) | app-wide | `Card`, 12 dp radius, outline-variant 1 dp border, 14/16 dp padding, 20 dp grey ring with "i", title bodyMedium semibold, detail bodySmall muted, right-aligned "OK" and "Open Settings" buttons (flow-wrap at large fonts) | e.g. "Your Work sign-in from 28 Sep wasn't kept" / "Secure storage wasn't responding then, so it lasted only until the app closed. Sign in again in Settings if Work needs it."; reset card "Your saved sign-ins couldn't be read" / "Android's secure storage could no longer unlock them, so Cooldown cleared them to start fresh. Sign in again in Settings."; several changes "3 sign-in changes from 28 Sep weren't kept" |
| Crash card | app-wide | same shape as the store card with a "!" ring; buttons "Not now", "Share report" | title "Cooldown crashed today at 9:41 AM" or "Cooldown stopped responding …"; detail "A report is saved on this phone. It goes nowhere unless you share it." / "2 reports on this phone. Share sends both, newest first." / "3 reports on this phone. Share sends all 3, newest first." / "Android kept no trace, so the report has only the time, the reason and the app version." |
| Fetch error | per account, under the status line | tinted surface, 12 dp radius, padding 12 / 10 dp, tint = error colour for severe kinds else orange, 11% fill: title bodyMedium semibold (the fix), evidence line bodySmall monospace (raw status, plus "· next try ~3:40 PM" for rate limiting), then one action; shown beside the last-good cards, never in place of them | see the table below |
| No account yet | per account page | `Card` with bold "No Claude account yet" and bodyMedium instructions | Claude: "Open Settings and tap "Sign in on this phone" for the Claude account. It opens Claude's sign-in in your browser — no computer needed." ChatGPT: "Open Settings and tap "Sign in with a code" for the ChatGPT account. It shows a short code to type at auth.openai.com — on this phone or any other device." Hidden while the store notice explains (unless a change is held) |
| No data yet | per account | bodyMedium line | "No data yet — try Refresh now." (omitted for the plan error) |

Fetch error kinds (`A/data/ErrorKind.kt:46`, action in `A/MainActivity.kt:1000`; `Vendor` is Anthropic / OpenAI / Google):

| Kind | Severe | Title | Action |
|---|---|---|---|
| auth | yes | "Sign-in stopped working — re-sign in from Settings." | "Open Settings" |
| rateLimited | no | "Rate limited — backing off, retries on its own." | none |
| network | no | "Couldn't reach <Vendor> — check your connection, or see if it's them." | "Check <Vendor> status" |
| server | no | "<Vendor>'s server errored — usually theirs, usually brief." | "Check <Vendor> status" |
| invalidResponse | no | "The server answered in a shape this app doesn't know — an app update may be needed." | GitHub build: "Check for updates"; Play build: none |
| internal | yes | "Something unexpected went wrong in the app." | none |
| plan | yes | "<Provider> doesn't report usage on this plan — it needs a paid plan (Pro, Max or Team)." | "See <Provider> plans" |

iOS shows one notice at a time, in this order: plan refusal › sign-in needed › the last fetch's error › stale › expiring. A fetch error absorbs staleness: its evidence line ends " · last reading Thu 9:50 pm" when the last good reading is over 6 hours old. A "quiet" notice (the plan refusal, expiring) draws on the grey card fill instead of orange (2026-11-05).

iOS notices (`I/Shared/CardContent.swift:171`, `I/App/UI/Cards.swift:177`): one `NoticeView` above the cards, 14 pt radius, orange (or red for sign-in) at 16% fill, padding 13 / 11 pt, title 15 pt semibold, body 14 pt, an optional capsule button (ink fill, 14.5 pt semibold). Cards dim to 45% opacity under an error or warning notice, and the 5-hour card's right reset line becomes "as of 9:41 pm".

| iOS notice | When | Copy |
|---|---|---|
| Plan | the account's plan reports no usage | title "Usage isn’t available on the <plan> plan"; body "<Provider> only reports usage windows for paid plans (the account's own provider as the app names it: Claude, ChatGPT or Gemini). This account stays signed in and will show numbers if the plan changes."; no button, cards hidden |
| Sign-in | re-auth needed | title "<label>: Sign-in stopped working"; body "The saved sign-in failed and couldn’t renew. The numbers below are the last ones that worked."; button "Sign in again" |
| Rate limited | last attempt rate-limited or backed off | title "Rate limited — backing off, retries on its own."; evidence "<message> · next try ~3:40 pm"; no body, no button (2026-11-05, parity wireframes rev B §3) |
| Network | last attempt was a transport failure | title "Couldn’t reach <Vendor> — check your connection, or see if it’s them."; evidence = the message; button "Check <Vendor> status" opens the provider's status page; no body |
| Server | HTTP status other than 200/401/403/429 | title "<Vendor>’s server errored — usually theirs, usually brief."; evidence "HTTP 503"; button "Check <Vendor> status"; no body |
| Invalid reply | a 200 the parser cannot read (Gemini keeps its own notice below) | title "The server answered in a shape this app doesn’t know — an app update may be needed."; evidence = the message; no button, no body; warning tone |
| Internal | anything uncategorised | title "Something unexpected went wrong in the app."; evidence = the message; red tint; no button |
| Expiring | Claude only, sign-in within 3 days of its ~30-day life (BEHAVIOR-MOBILE §3; both apps, settled by the owner 2026-11-05), not while sign-in is broken | quiet note on the grey card fill (not orange), never dims the cards: title "Sign-in expires in 2d 3h"; body "Sign in again to keep polling going."; button "Sign in again" |
| Stale | no reading for over 6 hours | title "Stale — nothing since Thu 9:50 pm"; body "Cooldown hasn’t had a reading for over 6 hours. Pull down to try again." |

Differs from Android: iOS draws the three conditions of `spec/mobile/BEHAVIOR-MOBILE.md` §3 as one notice, and has no fetch-error taxonomy, store notices or crash card in the app (Android draws stale and expiring conditions in the pinned notification, and the expiry line inside the Settings account card).

### 2.7 First launch and empty states

| Platform | State | What shows |
|---|---|---|
| Android | zero accounts | the registry's first account's page with "No Claude account yet" (above); no tab strip; a notification-permission prompt on Android 13+. There is no onboarding screen |
| iOS | zero accounts | `EmptyStateView`: title "Cooldown" 34 pt bold top-left; centred Claude and ChatGPT marks 54 pt, 14 pt apart; headline "Track your usage windows" 22 pt bold; body "See how much of your 5-hour and weekly limits you’ve used, and when they reset — for Claude and ChatGPT." 15.5 pt centred; two 260 pt-wide capsule buttons "Add a Claude account" and "Add a ChatGPT account" (accent fill, black text); footnote "Not affiliated with Anthropic or OpenAI." 12.5 pt; toolbar buttons hidden |
| Both | sample entry | "Try with sample data" on the first screen (Android: a 48 dp text button in the accent, 12 dp under the "No … account yet" card, caption "Three made-up accounts to look around. Nothing is saved or sent." bodySmall muted; iOS: under the capsules); see "Sample data" below |

Approved by `design/2026-10-23-ios-wireframes.html` rev B (first launch). Differs from it on Android: the wireframe is iOS only; Android has no equivalent screen.

### 2.8 The Summary — header, Personal/Work filter, row glyph and Pace map (v2.0, both platforms)

The Summary's rows and per-platform layout are in "New §2.8 The Summary (All tab)" under "v2.0 on Android" and "v2.0 on iOS" below; this section holds what both platforms built on 2026-11-11 from `design/2026-11-10-v20-feedback-wireframes.html` rev B §5–§9 as amended by the addendum `design/2026-11-11-v20-narrow-duet-and-row-glyph-wireframes.html` (§Row glyph, §Adding and tagging, which wins), CCRM-109 (Quiet Accounts), CCRM-110 (Account Tags), CCRM-111 (Map Layers), CCRM-115 (Row Tag Glyph). Android code: `data/AccountTag.kt` (tag and filter rules), `ui/AccountTags.kt`, `ui/SummaryRows.kt`, `SummaryScreen.kt`, `ui/PaceMap.kt`, `ui/PaceMapRules.kt` (quiet rule, foot line, figures, callout forecast, projection end, pin and hit test), `ui/MapLabels.kt`. iOS code: `I/App/UI/AccountTags.swift`, `Summary.swift`, `SummarySnapshot.swift`, `SignInSheets.swift`, `SettingsHistory.swift`, `AppModel.swift`, `I/App/UI/PaceMap.swift` (the old `PaceMapLayout` is gone); rules CooldownCore `AccountTag`, `SummaryFilter` (`resolve`, `memory`), `RowGlyphFit`, `PaceMapRules`, `MapLabels`. Captures `design/research/2026-11-v20-build/A3/`, `A4/` (Android); `I2/` (built at 42ad02e; iPhone 17 Pro, iPhone 17e, iPad Pro 13″ portrait and landscape, dark and light, from the `-demo map|dense|quiet|hidden` scenarios; `render/` holds every chip combination, callout and quiet state from `PaceMapTests`), `I3/` (built at a43afb4 and its close-out commit), `I4/` (iOS).

#### Header

"Weekly", then the **All · Personal · Work** control (the chosen segment tonal and semibold; a segment with no accounts at 38%), then the sort menu at the right ("your order ▾": the four orders with a check on the current one; the same setting as Appearance › Summary order, which stays). The control shows whenever the Summary exists (two or more accounts). Where the line cannot hold all three the sort button shows its glyph alone ("⇅ ▾"), then the control wraps under the title across the whole width. A tap on a dimmed segment shows "No Work accounts yet. Tag one in Settings." ("Personal" likewise) under the header for 3 s.

| | Android | iOS |
|---|---|---|
| Sizes | "Weekly" titleSmall; control 28 dp, 12 sp; sort a 28 dp outlined button | "Weekly" 17 pt; control 13 pt, 28 pt high, an outlined bar of three segments; sort 13 pt, 1 pt `line` outline, radius 8, a native pull-down |
| When it narrows | the sort button is its glyph on a list under 320 dp wide (a 360 dp phone); from font scale 1.5 the control wraps | by measured line width (VoiceOver "Sort: your order"); the iPad's list column carries the header and the map column follows it |
| Dimmed segment | the 3 s line 12 sp muted | 12 pt `sub`, also announced to VoiceOver; the segment is a Button that is never disabled, so the tap arrives, read as "Work, dimmed" |

#### The filter

The remembered value (`summaryFilter`, a new key; v1.9 never reads it; Android in its preferences, iOS in the App Group) is the last segment tapped. The filter in force is that value when it names an enabled segment, else All; the filter narrows the accounts before the order ranks them, so the list, the rank numbers, the Pace map with its foot-line count and empty message, the one-line threshold (six rows **shown**, not six accounts) and the snapshot follow the same rows. The footer and refresh still cover every account. **Memory rule:** a stored value the filter in force does not match (unknown, or its group has no accounts) is rewritten to `all` at once; an absent value stays absent; tagging later never brings the old choice back. It runs when the Summary comes on screen and whenever the accounts or a tag change (Android `UsageCache.settleSummaryFilter`, called from `UsageRepository`'s add / tag / remove and by the Summary); iOS (`SummaryFilter.memory`, run by `AppModel.runFilterMemory`) also runs it at launch and when the Summary changes, while the Summary exists; in the sample it touches only the sample's settings copy, and a screenshot `-demo` launch leaves the user's key alone; the view reads the stored value on every draw.

#### Row glyph (CCRM-115 (Row Tag Glyph))

An outlined person (Personal) or briefcase (Work) in the muted ink, never a colour or the word, hidden from TalkBack / VoiceOver, which read the tag on the name — "Teams, Work account" — whether or not the glyph is drawn. Full row: a 14 dp/pt box + 4 before the provider mark (on line 1, so it dims with it); one-line row: a 12 dp/pt box + 4 between the rank disc and the name (grouped with the name, so the bar pays 16). Under a Personal or Work filter it stays. **Drop-out:** the name ellipsizes first down to its floor (64 dp/pt at its own text scale, or its own width if shorter), then the glyph column leaves **every** row shown at once, then the name goes on ellipsizing. Decided on the tightest row shown from measured widths and re-decided on a width, filter, rows, display or font change. Android: Material outlined icons; a one-line bar keeps 40 dp, so the column leaves under a 295 dp list (`SummaryRows.fullKeepsGlyph`, `oneLineKeepsGlyph`). iOS: SF Symbols `person` / `briefcase` (12 pt symbol in the full row's box, 11 pt in the one-line box); one-line rows drop it when the bar would fall under 50 pt (`RowGlyphFit`; the mark, plan chip, figure and gaps are measured, the name by its font); iPad never drops it; an iPhone 17e drops it on a one-line list with a long name.

#### Pace map

Supersedes the Pace map paragraphs of the per-platform Summary sections and "The pace line on iOS" wherever they differ.

- **Title line.** "Pace map" left (Android titleSmall, iOS 17 pt); on the right, in place of "Weekly, where each account stands", three chips **Figures · Trails · Projection**: selected = ink at 13% fill (iOS `fill`) and semibold, unselected = a 1 dp/pt outline and regular; no check glyph. Defaults Figures off, Trails on, Projection off, remembered under the new keys `paceMap.figures|trails|projection` (Android in `usage_cache`, iOS in the app's own defaults; v1.9 ignores them). When the title and the chips do not fit one line the chips wrap under the title, left-aligned, 6 below. Android: 32 dp high **including** the touch target (Material's 48 dp minimum is not applied, so the header is the 32 dp the wireframe draws), 12 sp, 10 dp side padding, 6 dp apart, 8 dp radius; a switch for TalkBack; `FlowRow`. iOS: 13 pt capsules, 10 pt side padding, 5 pt vertical, 6 pt apart; the app's fonts are fixed sizes, so only a narrow width wraps them (iPad split view; every iPhone holds one line).
- **Grid** (CCRM-107 (Day Grid)). Sevenths both ways under the 0/50/100% lines, which stay (Android ink 10%, `MAP_GRID_ALPHA`; iOS the chart's grid ink). The x axis reads "week start", 2…6, "reset" (10 sp/pt, Android ink 60%, iOS `sub`; never bold — each dot has its own week, so there is no now column); an ordinal gives way to an end word it would touch (at 360 dp the "2" yields to "week start"). Wedge words "ahead of pace" above the diagonal and "behind pace" below it; "even pace" slides along the diagonal to a free stretch and also keeps off every ring.
- **Who is plotted (the quiet edge).** A window row truncates, so the map hides an account whose Weekly row prints "0%" (under 1: 0.99 is hidden, 1.0 is plotted); so do accounts with no reading and a reading with no reset clock (it has no x; before, Android sat it on the origin). The list still shows every row. Spent: × on the dot. Android: stale shows the last reading, undimmed (as shipped).
- **Foot line** (Android `PaceMapFoot`). 4 dp/pt under the plot: left "each account's own week" (11 sp/pt muted; it left the axis); right, the same size, the count of accounts left off: "2 at 0%", "1 no reading", joined "2 at 0% · 1 no reading"; nothing when none. Android counts a not-started week as 0% and a reading of 1% or more with no reset clock as no reading.
- **All at 0%.** The frame, grid, diagonal and words stay at full height; in the behind wedge, right-aligned at (96%, 26%), 10.5 sp/pt ink 55%: "Nothing to plot yet" (semibold) over "every account is at 0% this week"; Android's TalkBack reads it as the map's state. With nothing plotted but some accounts unread, the empty frame shows the foot line's count and no message.
- **Figures layer.** "Teams 52%" (Used), "Teams 48% left" (Left; a spent account reads "Teams 0%", Robin 2026-11-11), "Teams −13%" / "+19%" / "0%" (Vs pace; no reset clock falls back to Used). Name 11.5 sp/pt semibold ink; figure bold (iOS 11.5 pt) in the severity colour (Used, Left, the Vs fallback) or green / red / ink (Vs pace). The figure labels truncate like the row (51.5 → "51%", "48% left"); only the callout's "~N% at reset" rounds half up.
- **Trails layer.** The week at 1.6 dp/pt, accent 55%; the last 24 h (a seventh of the week before the dot) 2.6, accent 90%, round caps; an account with fewer than two samples has no trail (the trail ends on the live point, so it needs two behind it; Android `PaceMapRules.hasTrail`). Off: dots only.
- **Projection layer.** A dashed tail (1.6, 5/4, accent 65%) from the dot to a ring (r 3.5, 1.5 stroke, card fill) on the right edge at the projected %; where the estimate runs out before the reset the ring sits on the 100% line at that moment, red, and the tail fades from the accent to red. The estimate is the account chart's (`Projection.estimate` on the week's samples). No tail and no ring with no estimate, spent, or no reset clock.
- **Callout.** A tap on a dot (48 dp/pt target) or a name pins it (Android widens the name's box to 48 dp high); the nearest dot wins. The others dim to 35% (trails, tails, leaders, dots, names, and on Android the rings; not the frame or its words). Pill: 8 radius, card at 97%, 1 border in the account colour at 55% (iOS padding 9 × 6 pt). Line 1: a 7 dp/pt accent dot, the name 12 semibold, the figure 12 bold at the right; line 2, 11: the pace line (its drop order measured to the plot's width) or the row's line where there is none ("Out until Fri 1:48 PM", iOS "pm", red when spent; "Held · …"); line 3, 11: "~74% at reset" (muted) or "100% at Wed 6:10 PM" (red; a bare clock later today), none without a projection. It opens 10 right and 8 above the dot, flips left when it would pass the plot's right edge by 6, below the dot at the top, and is clamped to the plot. The same account again opens its page; another dot moves the pin; the empty plot clears it. Android: drawn in the canvas; the lines stack from their measured heights (24 + 15 + 15 dp at scale 1.0, taller as the text grows); the pin is held by the Summary (one for the cover and the inner layout, so a fold keeps it) and is dropped, not just hidden, when its account leaves the map; TalkBack one node per dot on its 48 dp target, "Teams, 52 percent, 21h 50m ahead · resets Sun 5:58 PM, ~74% at reset", click label "Pin, again to open" (then "Open"). iOS: VoiceOver one element per dot with the same label, hint "Double-tap to pin, again to open".
- **Names and leaders** (closes CCBG-79 (Leader Reads as Trail)). `MapLabels`, the same rules on both, top-down: right, below, left, above-left; then a dotted leader (1, 1.5/2.5, accent 55%; iOS from the dot's edge) 24, 36 or 48 from the dot in eight directions, refused across a placed name or another dot (so a stack of dots at one point shows two names and no leader), never longer than 48 and never to the far edge. A name never sits on its own tail or a ring. Nothing free: the name is not drawn and takes no room; its dot stays and its target still pins a callout that names it. The fixture's offsets (`printed.json` → `mapLabels`): dot box 14, 2 between names, 1 off a word, right +10, below +2/+3, left −10, above-left −6; margins Android 2/6/8/6 dp, iOS a 4 pt right margin.

---

## 3. The trend chart

One chart per open window card, one series (the window's samples), full window on the time axis, fixed 0–100% on the value axis. Android `ui/Sparkline.kt` (`UsageSparkline`), iOS `Shared/Charts.swift` (`TrendChart`). Approved by `design/error-and-estimates-wireframe.html` (estimate marks), `design/2026-09-17-main-screen-redesign.html` §3½ (height) and rev D (guide labels inside the plot, "now" alignment).

### 3.1 Size

| Setting | Android height | iOS height | Elements |
|---|---|---|---|
| Small | 72 dp | 72 pt | lines only: guides (no labels), even-pace diagonal, curve and fill, wash, now divider, current dot, projection dash and dot. No guide labels, axis labels, value labels, legend or callout; the chart is inert to touch |
| Medium (default) | 120 dp | 120 pt | everything |
| Large | `0.35 × chart width`, clamped 180–300 dp, and at most 45% of the window height | `0.45 × card width`, clamped 180–300 pt | everything |

7-day card with a cap on iOS caps the chart at min(height, 120) pt. Orientation "Time across" (default) or "Time down" (Settings → Chart orientation): time runs top to bottom and usage left to right, so the bar above and the chart share an axis. Source: `A/ui/DisplayPrefs.kt:35`, `A/ui/Adaptive.kt:107`, `I/App/Model/CardLayout.swift:83`.

### 3.2 Plot geometry

| Property | Across | Down |
|---|---|---|
| Plot box | full view width; 12 dp top inset (room for the 100% label above its line); 15 dp bottom axis strip (none at Small) | full view; no insets (labels sit on halos inside the plot) |
| Time axis | x, window start at left | y, window start at top |
| Value axis | y, 0 at the floor | x, 0 at the left edge |
| Now divider alignment | the divider's x equals the bar's pace-mark x, because the plot and the bar span the same width and both read the wall clock (re-evaluated on a 5-second tick) | the same, vertically |

iOS across: plot top 20 pt, bottom H − 20 pt, right edge W; down: right edge W − 52 pt (times down the right side), top 16, bottom H − 6. Source: `A/ui/Sparkline.kt:123`.

### 3.3 Elements, back to front

| Element | Android | iOS | Shown at |
|---|---|---|---|
| Pace wash | a faint red triangle above the diagonal (red guide colour at 10% dark / 7% light), drawn under everything, only when the newest reading is above pace + 3 at the divider's instant (CCBG-21 (Zero-Point Shading)) | a faint red triangle above the diagonal (red at 10% dark / 7% light), drawn first, only while the window is over pace; at every size (2026-11-05, rev B §5) | M, L (S too on Android) |
| Day grid (v2.0, CCRM-107 (Day Grid)) | drawn after the pace wash and before the 80/90/100 guides, so the guides, the diagonal and the data sit over it: vertical lines at window start + k × (span / n), k = 1…n−1, and horizontal lines at k/n of 100%, k = 1…n (the n-th lies under the 100% guide); n = 7 for a window measured in days (seven equal day columns, lines at k/7), 5 for the 5h (hourly columns, 20% steps). 1 dp solid, `onSurfaceVariant` at 13% (`GRID_ALPHA`); pure layout in `A/ui/DayGrid.kt` | same rule (CooldownCore `DayGrid`, the core rule), ink `line` at 60%, 0.5 pt, in `TrendChart` | all; Time down transposes it (day lines across, percent lines down); not started: no chart, no grid |
| Guides | dashed lines at 80, 90, 100% in yellow, orange, red (bar ladder at 85/95/100), 1 dp, 32% alpha, dash 5/7 | same colours at 70% alpha, 1 pt, dash 2/4 | all |
| Guide labels | "100%", "90%", "80%", 9.5 sp, in the guide colour at 85%, inside the plot at the right end, just above their own line, on a card-coloured halo (2 dp pad); down: left of each vertical line, right-aligned, stacked in three rows, 28 dp below the top | 10 pt, right-aligned at the right edge | M, L |
| Even-pace diagonal | dashed line from (start, 0%) to (reset, 100%), 2.5 dp, yellow at 90%, dash 10/6 | 2.6 pt, `Palette.pace` (`#FDD663` dark / `#E0A000` light), dash 5/4 | all |
| Legend | "even pace" 9.5 sp with a 14 dp dashed swatch, top-left corner (bottom-right when usage is high early: a sample left of 45% of the width above 70%); down: under the start label, on a halo | "even pace" 10.5 pt with a 13 pt swatch at left, near the 95% line (down: bottom-left) | M, L |
| Curve fill | area from the curve to 0%, split at the diagonal: below it the series colour at 18% light / 20% dark; above it orange at 30% light / 34% dark (the overshoot) | split at the diagonal as on Android: below it the accent at 18% light / 20% dark, above it orange at 30% / 34% (2026-11-05); both flip with "Time down" | all |
| Curve | series line 2.5 dp round-capped, series colour | 2.2 pt, accent (the red curve and red last dot went with the wash, 2026-11-05: the wash carries "over pace"; red stays on the bar slice and the pace text) | all |
| Sample dots | 2.6 dp radius on every sample but the last | 2.4 pt radius | all |
| Now divider | wall-clock line across the plot, muted at 28%, 1 dp (1.5 dp when down); never left of the last sample, clamped to the window | muted at 70%, 1 pt, up to the 104% line | all |
| Current dot | 4.5 dp radius, series colour, on the last real sample (the dot is the last observation, the divider is the clock) | 5 pt radius | all |
| Current label | "<n>%" 11 sp bold onSurface, above the dot (below it in the top third; beside it when down), yields to guide labels and the callout | 12 pt bold, above-left of the dot, right of it near the left edge | M, L |
| Projection tail | dashed line from the current dot to the end point, 2.5 dp, series colour at 65%, dash 7/8; end point a hollow ring 4 dp radius, 1.8 dp stroke, 80% alpha. The tail ends where it crosses 100% when the limit is hit first. Withheld whenever the projection is suppressed | dashed 2 pt, accent at 75%, dash 4/3; ring 8 pt, red at or above 100% | all |
| Projection label | "~74%" 11 sp bold muted, beside the end ring, only when it clears the guide labels, the current label and the callout; not drawn when the limit is hit | "~74%" 11.5 pt bold, only below 100% | M, L |
| X axis line | 1 dp, muted 18%, across the plot floor | 1 pt `line` | M, L (across) |
| Time labels | 9.5 sp muted under the floor: window start at left, reset at right; "now" centred under the divider only when it clears both end labels by 6 dp. 5-hour window: clock time ("9:50 AM" or "09:50"); 7-day: "d MMM" ("23 Jul"). Since v2.0 (CCRM-107 (Day Grid)), between the end labels the ordinals 2…n−1 (2…6 on a 7-day chart, 2…4 on a 5h) at 9.5 sp `onSurfaceVariant`, centred in their columns; the one in now's column (an instant on a line belongs to the later column) bold, `onSurface` at 95%. An ordinal gives way when its box comes within 6 dp of the "now" label or within 4 dp of an end label, and all drop below a 14 dp column; "now" and the end labels never move for an ordinal. Small (no axis strip) and Time down: grid lines, no ordinals. Inner two stacks: each stack has its own grid and ordinals | 11 pt `sub`; "now" shown only more than 56 pt from either edge; 5-hour clock "9:50 am" (am/pm lowercase), 7-day "23 Oct". Ordinals as on Android (9.5 pt `sub`, the now column bold `ink`, start label 11 pt; same give-way and 14 pt column rule); Small: the grid, no ordinals; Time down: the ordinals run down the right edge under the same rule (measured by label height) | M, L |
| Selection callout | tap a sample to pin; tap it again to clear; long-press then drag to scrub. Snaps to the nearest real sample, never interpolates. Draws a 1.5 dp crosshair in the series colour at 55%, a 4.5 dp dot on a 6.5 dp surface halo, and a 6 dp-radius pill (surface at 95%, series-colour 1 dp border at 55%) with two lines: the time (9.5 sp muted; 5-hour "9:50 AM", 7-day "Thu 9:50 AM") and bold 11 sp in core's printed form (CCRM-104 (Pace Line Parity) D8, `paceLabel`, rounded toward zero): "14% · −8%" red over the mark, "14% · +10%" green under it, "14% · 0%" ink within a point (Left mode "86% left · …"), so "+" never means over on mobile. The pill avoids the guide labels | none | M, L; Android only |
| Accessibility | one-line summary: "Usage chart. 62% at 9:50 AM, 8% above even pace. Projected 74% by 2:50 PM. Tap a point for its value." | hidden from accessibility (the card's combined label reads the numbers) | |

Differs from the iOS code comment and `design/2026-10-23-ios-wireframes.html` rev B ("Nothing removed" from Android's chart): the iOS chart has no pace wash, no orange overshoot fill, no selection callout and no ⓘ provenance marks. The Android chart is the reference.

The day grid draws wherever the chart does: the open tile (7d, model and 5h chips) and the share card's trend on both platforms — Android's share-card chart (`ChartBitmap`) has the same grid and ordinals, bold in now's column, and no "now" label, so its ordinals yield only to the end labels — and on iOS the Overview/Large and XL widgets' chart. The Pace map's grid is in §2.8. Captures `design/research/2026-11-v20-build/A1/` (Android) and `I1/` (iOS, built at 7d479ba from `design/2026-11-10-v20-feedback-wireframes.html` rev B §2–§4; iPhone 17 Pro, iPhone 17e, iPad Pro 13″ both orientations; `render/` holds the widget, lock-screen and Live Activity faces from `SurfaceRenderTests`).

### 3.4 Text lines under the chart

Shown in every size (at Small they are the only place the numbers are left). Android bodySmall; iOS 14.5 pt (5-hour card) or 13.5 pt (7-day halves).

| Line | Android copy and style | iOS copy and style | Source |
|---|---|---|---|
| Pace readout | 6 dp under the chart. Above pace: "12% above even pace" bold, orange (`barFill(95)`); below: "32% below even pace" muted; inside the dead zone: "On even pace" muted | same wording; above pace red, never bold; at 100%: "Limit reached" red | `A/ui/Sparkline.kt:822`, `I/Shared/CardContent.swift:84` |
| Projection, runs out | 2 dp under the pace line, error colour, with a trailing ⓘ: "At this pace: 100% at Thu 3:10 PM — 1d 2h before the reset · 4.2%/h". Tap toggles the provenance note | "At this pace: runs out ~Thu 3:10 pm · 4.2%/h" (clock only, no day, on a 5-hour card when the time is today); no ⓘ | `A/MainCards.kt:448`, `I/Shared/CardContent.swift:96` |
| Projection, reaches reset | muted, ⓘ: "At this pace: ~74% when the window resets · 4.2%/h" | "At this pace: ~74% when the window resets · 4.2%/h" | |
| Provenance note | "Projected from this window's samples — a least-squares fit anchored on the latest reading. It shifts as new polls land." in a pill (8 dp radius, 9/6 dp padding, 8% muted fill) | none | `A/ui/Estimates.kt` |
| Suppressed, few samples | Android only, replaces the chart and pace lines, 8 dp above, muted: "Not enough history in this window yet to chart a pace" | (chart still draws) | `A/MainCards.kt:379` |
| Suppressed, not enough movement | Android: "Usage hasn't moved enough yet to project a pace" (2 dp under the pace line) | iOS names the reason: "Too early in this window to project a pace" (window barely open: elapsed under max(window ÷ 100, 1 minute)); "Not enough usage yet to project a pace" (under 5% used); "Not enough readings yet to project a pace" (no estimate: too few samples, too short a span, too little movement, a flat fit) | `I/Packages/CooldownCore/Sources/CooldownCore/Projection.swift:161`, `spec/core/BEHAVIOR-SPEC.md` §3.1 |
| At the limit | Android: no projection line | "Opens again when the window resets" | `I/Shared/CardContent.swift:80` |
| Reset row | leading reset left, other reset right, muted | same, `txt` | 2.4.4 |

Differs from `spec/core/BEHAVIOR-SPEC.md` §3.1 ("a client must phrase the three suppressions differently"): Android collapses all three into the one "Usage hasn't moved enough yet to project a pace", and also draws the projection tail whenever an estimate exists, regardless of the young-window and minimum-usage floors; iOS follows the spec.

---

## 4. Appearance controls on the main screen

All live in Settings → Appearance and apply to every card at once.

| Control | Values | Default | Effect | Source |
|---|---|---|---|---|
| Density | Comfortable, Compact | Comfortable | Compact folds 5-hour and 7-day to title, bar(s) and one line; credits never fold | `A/ui/DisplayPrefs.kt:11` |
| Chart height | Small, Medium, Large | Medium | 3.1 | `A/ui/DisplayPrefs.kt:35` |
| Chart orientation | Time across, Time down | Time across | 3.2 | `A/ui/DisplayPrefs.kt:60` |
| Usage display | Used, Left | Used | 1.2 | |
| Reset time | Countdown, Clock time | Countdown | which reset form leads | |
| Show red past the pace mark | on, off | on | gates only the red segment; the tick always draws | |

### 4.1 Layout sheet (per account)

Opened from ⋮ → "Main screen layout" on Android (bottom sheet) and the ellipsis → "Layout" on iOS (sheet with medium and large detents). Approved by `design/2026-09-17-main-screen-redesign.html` §9, states a–e.

| Element | Android | iOS | Source |
|---|---|---|---|
| Title and hint | "Main screen layout" (titleMedium) and "Show, hide and reorder the cards on this account's screen." (bodySmall muted), 16 dp side padding | navigation title "Layout" and "Show, hide and reorder the cards on <name>’s screen." | `A/LayoutSheet.kt:91`, `I/App/UI/LayoutSheet.swift:6` |
| Card row | 56 dp tall: 24 dp glyph (calendar for the two windows, a hand-drawn coin for credits), 14 dp gap, label ("5-hour window", "7-day window", "Usage credits", bodyLarge), switch, drag handle. A hidden card draws at 60% alpha and keeps its handle | glyph (clock, calendar, creditcard), label, toggle; list in edit mode so each row has a drag handle | |
| Rows listed | only the cards the account has data for | same; credits only when the reading carries them and its switch is on | |
| Behind More | a non-draggable divider row 34 dp tall: dashed line, "BEHIND MORE" (labelSmall), dashed line; dragging a card across it folds or unfolds it | a "Behind More" section; swipe a card left for "Behind More" (grey) or "Above More" (blue); empty text "Nothing folded. Swipe a card left to put it behind More." | |
| Never blank | the last card above More cannot be hidden: its switch is disabled and "At least one card must stay visible above More." (11.5 sp, amber) appears under the row | same copy as the section footer, amber | `A/data/CardLayout.kt` |
| Actions | footer: "Reset layout" (accent, left) opens a confirm "Reset layout?" / "Shows every card again, in the default order, for this account only. Doesn't touch its history or settings." with "Cancel" and accent "Reset layout"; "Done" (right) | "Reset layout" at top left (applies at once, no confirm), "Done" at top right | `A/LayoutSheet.kt` |
| Persistence | every change applies at once, per account, no confirm other than Reset | same | |
| Accessibility | TalkBack move-up / move-down actions per row | native | |

---

## 5. History screen

Android: a screen titled "Usage history" with a back arrow. iOS: pushed from the calendar button, large title "Usage history". Source `A/HistoryScreen.kt:67`, `I/App/UI/SettingsHistory.swift:366`.

### 5.1 Anatomy

Android, top to bottom: account tab strip (only with two or more accounts; same fixed/scrollable rule as Main; indicator in onSurface, not accent) · 16 dp · window chips · pane(s) · 12 dp · footnote.

| Part | Rule |
|---|---|
| Window chips | "5-hour" and "7-day" `FilterChip`s, 8 dp apart; only on a narrow window (COMPACT) and only when the account has both windows; side-by-side on MEDIUM and wider instead |
| Side-by-side | ≥ 600 dp: two columns, 16 dp gutter, headings "5-hour windows" and "7-day windows" (titleSmall, accent, 4 dp start padding); content width capped at 1100 dp (640 dp when narrow) |
| 5-hour pane | week card paged by a pair of arrows: "Older week" left, "Newer week" right (disabled at the ends), centred title ("This week", "Last week", or a range such as "14–20 Jul") with the Mon–Sun range under it; a count line "12 sessions · 2 maxed out" (singular "1 session"); then one bar row per window, newest first |
| 7-day pane | the Plan Fit block (below) then one bar row per weekly window, labelled by its date range |
| Bar row | 6 dp vertical padding; 76 dp label column (label bodySmall; the open window adds "now" in labelSmall accent), 10 dp, 16 dp-high bar with 5 dp radius (track at 22%, fill by the peak's severity colour), 10 dp, 40 dp right-aligned peak "62%" (muted; the severity colour when the window hit its limit at 99.5% or more) |
| Footnote | bodySmall muted: "History is recorded as each window closes, so it fills in over time. Peak %s are sampled at the polling interval." |
| Empty | 5-hour and weekly: "No sessions recorded yet. History builds up as you use the account and each 5-hour or 7-day window closes — check back later." |

iOS: account tabs (same control), a segmented "5-hour" / "7-day" picker (200 pt wide) always shown, one card per week with chevron arrows ("Earlier week" / "Later week"), title ("This week", "Last week", or a range), range line, "<n> sessions" or "<n> weeks" plus " · <m> maxed out", bar rows (label column 104 pt with the clock-and-weekday start time, an 11 pt bar, 44 pt peak; peak red at 100%), the footnote "… Peak 5h readings are sampled when the app checks." ("weekly" on the 7-day pane; CCBG-53 (History Footnote) fixed on iOS), empty text "No closed windows yet." iOS has no side-by-side layout and no Plan Fit block.

**iOS, from 2026-11-05 (parity wireframes rev B §4):** the Plan Fit block (the Android rules and copy, ported with its tests; no plan-change or model-cap wording yet because iOS history records carry no plan or model tag, so the header plan is the live one and the cap reads "the cap") sits above the 7-day week card for every account except a plan-refused one, new accounts included ("First week in progress." / "The reading starts after four weeks."); the header drops its "LAST 8 WEEKS" token on the single-column layout (every iPhone). From 600 pt wide (iPad, wide Split View) the picker goes and the two panes sit side by side — headings "5-hour windows" / "7-day windows" in the account accent, 16 pt gutter, Plan Fit on top of the right column, content at most 1100 pt, one footnote ("… Peak 5h and weekly readings are sampled when the app checks."). A plan with no 5-hour window shows the 7-day pane alone at any width, with no picker.

Known defect on Android only (CCBG-53 (History Footnote)): both footnotes contain a literal "%s" that renders as typed (the string is never formatted). Fixed on iOS 2026-11-04; fix Android in code, then here.

### 5.2 Plan Fit block (Android, 7-day pane, hidden for a plan-refused account)

Above the weekly bars: a rounded 12 dp, `surfaceVariant` block, 12 dp padding (never an accent or warning colour: a reading, not an alarm). Four lines: header (labelSmall muted), reading (titleSmall bold), evidence (bodySmall muted, 2 dp above), optional 5-hour line. Approved by `design/2026-09-16-plan-fit-and-account-order.html` §1–4. Source `A/PlanFitBlock.kt:39`, `A/ui/PlanFitCopy.kt`.

| Part | Copy |
|---|---|
| Header | "PLAN FIT · MAX 5X · LAST 8 WEEKS"; the period token is dropped on the narrow layout; "N WEEK(S)" under four weeks; none for a brand-new account |
| Reading | "This plan runs out on you." / "You use a small part of this plan." / "No use recorded on this account." / "This plan fits how you use it." / "First week in progress." / "2 of 4 weeks recorded." / "Changed from Pro 3 weeks ago." / "Changed plans 2 weeks ago." |
| Evidence | "Hit the cap in 5 of 8 weeks" (names a shared model cap: "the Fable cap"); "Capped or above 75% in 6 of 8 weeks"; "Under 25% in 7 of 8 weeks · never capped"; "Between 25% and 75% most weeks · capped once"; "The reading starts after four weeks."; "The reading restarts after four weeks on the new plan."; gaps use "recorded weeks"; the narrow layout says "the last 8 weeks" |
| 5-hour line | "5h cap hit 3 times"; absent at zero and for accounts without a 5-hour window |

---

## 6. Settings

Android: a screen titled "Settings", back arrow; a tab bar of four pages and a swipeable pager. iOS: a sheet with a grouped list, "Done" at top right, an Edit button at top left (drag to reorder accounts). Approved by `design/settings-diet-wireframe.html` (four tabs), `design/2026-10-24-ios-appearance-accounts.html` (iOS sections), `design/accounts-redesign-wireframe.html` (account cards).

### 6.1 Frame

| Property | Android | Source |
|---|---|---|
| Tabs | "Accounts", "Alerts", "Appearance", "More"; fixed row when every title fits its share of the width with 10 dp padding each side, otherwise scrollable with 16 dp padding; titles bodyMedium single line | `A/SettingsScreen.kt:960`, `SettingsTabs` |
| Page | scroll column capped at 1100 dp (640 dp narrow), 8 dp top, 24 dp bottom, 20 dp side padding | `A/SettingsScreen.kt` |
| Two columns | ≥ 700 dp wide: Accounts cards split into two columns by index parity, Alerts splits "Always-on notification" and "Reset pings", Appearance splits the two halves, More splits "Polling and Usage credits and Updates" from "About and diagnostics". 20 dp gutter | |
| Section style | `SectionLabel`: UPPERCASE labelMedium muted, 4 dp start padding, 8 dp below; `SectionCard`: Card with 16 dp padding; `RowDivider`: horizontal divider with 10 dp vertical padding | `A/SettingsScreen.kt:3538` |

iOS section order (`I/App/UI/SettingsHistory.swift:7`): Accounts, Usage credits (only for accounts reporting credits), Reset alerts, Lock Screen, Appearance, About.

### 6.2 Accounts

| Part | Android | iOS |
|---|---|---|
| Degraded notice | `DegradedStoreNotice` at the top of the page (2.6) | none |
| Account card | `Card`, 16 dp padding. Header row: provider mark 20 dp in the account accent, 6 dp, name (titleMedium, one line, ellipsis), 8 dp, 8 dp status dot (green `#188038` light / `#81C995` dark "Active", or error colour "Needs re-auth"; only with a token), 6 dp, plan chip (labelSmall in accent on accent at 14%, 10 dp radius; the plan's name per spec/core API-CONTRACT §11 — "Pro 100", "Max", "Ultra", "Ultra 5x" — never a multiplier, since 2026-11-02), spinner 18 dp while busy, ⋮ ("Account options", 28 dp button) | list row: mark 20 pt, name, plan chip, right text "Signed in" (sub) or "Sign in again" (orange); touch and hold opens the menu |
| Email line | under the header, 26 dp indent, bodySmall muted, local part truncates before the domain; shown only when it adds something beyond the name | Details sheet only |
| Tag (v2.0, CCRM-110 (Account Tags)) | Both: every account is **Personal or Work**, no "None"; stored additively (Android `"t"` in the registry's account JSON, iOS a `tag` string on the account; `personal` / `work`); a missing, empty or unknown value reads as Personal and nothing is written at upgrade (every v1.9 account is Personal); an unknown value round-trips untouched; v1.9 ignores the key. The first account, which never passes the Add sheet, is Personal. Signing in again never touches the tag; a change applies at once. Android: a neutral chip after the plan chip on **every** card, signed in or not — outline 1 dp `outlineVariant`, 10 dp radius, a 12 dp glyph and the word in labelSmall; a tap opens the Tag dialog; the name gives first, down to 64 dp at its own text scale (or its own width if shorter), then the chip drops its word and keeps its glyph (`TagChipFit`); TalkBack "Tag: Personal", action "change" | the chip after the plan chip on **every** account: 12 pt glyph (SF Symbols `person` / `briefcase`, in the chip's ink, never a colour) + 11 pt semibold word, a 1 pt `sub` 50% outline, radius 6. Narrow phone (addendum T4 B, iPhone 17e): the whole row; then a plain "Signed in" leaves whole (never "Signed…"); beside a warning word ("Sign in again", which outranks the name) the chip drops its word and keeps its glyph (VoiceOver still "Tag: Work"); only then does the name ellipsize. The account page's Account section has a "Tag" row after Accent colour (a menu picker, the current choice with its glyph and a chevron, always a value; VoiceOver "Work") |
| Menu | "Details…" (with a token), "Clear usage history…", "Rename…", "Tag…" (v2.0, after "Rename…": the dialog "Tag · <name>", two 48 dp radio rows (glyph + word), the help "Tags group accounts on the Summary: All · Personal · Work." and "Close"), "Accent colour…", "Remove account" (error colour; only with two or more accounts) | "Rename", "Tag" (v2.0, a submenu after Rename, two rows with glyphs leading; its icon is always the current glyph), "Details…", "Accent colour…", "Sign in again", "Clear usage history…", "Remove" (destructive) |
| Claude, signed out | "Not signed in" (bodySmall muted), filled button "Sign in on this phone", then "Opens Claude's sign-in in your browser — no computer needed. Needs a paid plan: Claude reports no usage for Free accounts." | taps open the Claude sign-in sheet |
| Claude, signed in | status line (bodySmall muted) with a 28 dp refresh button ("Check token now"): "Checking…", "Checked 12m ago", "Checked 12m ago · renews in 6h 4m", "… · renewal due at next check", "Not checked yet"; then red lines as they apply: free plan ("Free plan: Claude doesn't report usage — upgrade to Pro, Max or Team to see numbers."), renewal dead ("Renewal has stopped working — re-sign in below." and day-count variants), amber expiry-soon ("Sign-in expires in 2d 3h — re-sign in to keep polling going."), red "Rate-limited — next try in 12m"; divider; a three-cell action row (min 44 dp, 10 dp radius, 20 dp icon over a labelSmall label): "Re-sign in" (accent, outlined when re-auth is needed), "Clear" (error), "Dashboard" (opens the Claude usage page) | account page (2026-11-05, parity wireframes rev B §1-2): section "Sign-in" — the status line 13.5 pt muted with a 28 pt ↻ "Check now" button (a spinner while checking; same fetch as pull-to-refresh), the condition rows in this order (free plan, sign-in stopped, expiring, rate-limited; red, amber for expiring), then the action row "Sign in again" in the account accent, semibold while sign-in is broken. No Clear, no Dashboard (iOS keeps its six-item menu). The status text "renews in …" comes from the Keychain token's expiry and is absent when unreadable; Gemini gets the same lines minus the ~30-day estimate; ChatGPT shows no renewal clause. The Settings row reads "Sign in again" › "Expires in 2d" › "Signed in" |
| ChatGPT, signed out | "Not signed in", filled "Sign in with a code", then "Shows a short code to type at auth.openai.com — on this phone or any other device. Needs device code sign-in turned on in ChatGPT → Settings → Security." | tap opens the device-code sheet |
| ChatGPT, signed in | status line "Checked 12m ago" with refresh; red "Sign-in stopped working — sign in with a code below."; "Rate-limited — next try in 12m"; action row "Sign in with a code", "Clear", "Settings" | account page, section "Sign-in": status line with ↻, then "Sign-in stopped working — sign in with a code below." / "Rate-limited — next try in 12m" as they apply, the action "Sign in with a code" and the footer "Shows a short code to type at auth.openai.com — on this phone or any other device." No Clear, no Settings row |
| Messages | "Claude signed in — usage fetched, polling started."; "Token checked — working."; "Checked — working."; "<name> signed out." or "<name> signed out — until Cooldown restarts." while degraded | |
| Add / reorder | with one account a full-width outlined "+ Add account"; with two or more a row of "+ Add account" and a text button "Reorder" | "Add account" row with a plus glyph; reorder by dragging while Edit is on, "the order is the same everywhere" |
| Add sheet | modal bottom sheet "Add account" (titleLarge); rows: Claude (28 dp mark) "Signs in through your browser, then paste the code back — as before." and ChatGPT "Shows a short code to type at auth.openai.com — on this phone or any other device. Needs device code sign-in turned on in ChatGPT → Settings → Security." Picking a row starts that provider's sign-in. v2.0 (CCRM-110 (Account Tags)): under the title "This account is" (bodyMedium, `onSurfaceVariant`), 8 dp, a full-width `SingleChoiceSegmentedButtonRow` (40 dp) of **Personal | Work** — an 18 dp glyph and the word in each segment, the chosen one tonal and SemiBold, the glyph in place of Material's check — 6 dp, "Groups it on the Summary. You can change it later." (bodySmall, muted), 16 dp, then the provider rows. The glyphs leave both segments together when "Personal" with its glyph no longer fits half the row (`AddSheetFit`; font 2.0 on a 360 dp phone). The sheet scrolls once it outgrows the screen. Last row, under a divider: a 28 dp violet ring-and-dot glyph, "Try with sample data" (bodyLarge) and "Three made-up accounts to look around. Nothing is saved or sent." | sheet "Add account" 20 pt bold, two rows (32 pt mark, 17 pt name): Claude "Sign in with your Claude account in the browser, then paste the code it shows." and ChatGPT "Sign in with a code on any device — no password in this app."; footnote "Cooldown reads the same usage numbers the Claude and ChatGPT apps show. Tokens stay in this device’s Keychain. Not affiliated with Anthropic or OpenAI." v2.0: above the provider rows "This account is" (13.5 pt `txt`), a two-segment control in the Summary control's style (36 pt high, radius 10, 15 pt words with 14 pt glyphs, the chosen one a 13% fill and semibold) and "Groups it on the Summary. You can change it later." (12.5 pt `sub`); VoiceOver reads "This account is" then "Personal, selected". The sheet opens at the large detent only (the control's ~90 pt made the provider rows truncate at medium). The fourth row is "Try with sample data" (see "Sample data") |
| Adding an account (v2.0, both platforms) | Personal is selected **every time the sheet opens** (not remembered); the shown tag is written when the account is created; "Try with sample data" ignores the control; the first-launch buttons leave it Personal. An account is created only when its sign-in succeeds: a cancelled or failed sign-in leaves no account and no tag (Robin 2026-11-11). Android implementation: the row tap makes a provisional card that hosts the sign-in; Cancel (Claude, Gemini), dismissing the code sheet (ChatGPT) or a dead end removes it (`UsageRepository.abandonProfile`, under the repository's lock, so a dismiss that races the token save keeps the account; slots are never reused), and a provisional account with no token is swept at the next launch once it is over an hour old or nothing is pending for it (`settleProvisional`) | the choice rides on `AppModel.pendingTag` with the sign-in; "Sign in again" ignores it; closing the sheets drops it |
| Reorder sheet | bottom sheet "Account order" (titleMedium) with "Sets the tab order everywhere." and a drag-handle list; "Done" | inline (Edit) |
| Browser picker | with two or more browsers installed, a dialog "Open with": "Pick the browser where you're signed in to the Work Claude account." and one row per browser (32 dp icon, bodyLarge name); Cancel | iOS opens the default browser |

Dialogs (`A/SettingsScreen.kt:1165-1430`):

| Dialog | Copy |
|---|---|
| Rename account | title "Rename account"; field "Name" with a counter "n/16"; a suggestion chip `Use "<suggestion>" (from <email>)` when an email exists; help "Used everywhere — tabs, notifications and shortcuts. Clear the field to go back to "<default>"."; "Save", "Cancel". iOS: an alert with a text field "Name" and the help line "Used everywhere — tabs, widgets and the Live Activity. Clear the field to go back to “<default>”." (superseded 2026-11-05, parity wireframes rev B §1: a medium sheet `RenameSheet` — inline title "Rename account", Cancel / Save, one field capped at 16 characters with the counter "n/16", the row `Use “<suggestion>” (from <email>)` from Android's `AccountEmail.suggestion` rule, and the help line; used by the account page's Name row and the touch-and-hold Rename); a cleared or blank field restores the positional default — "Claude", then "Claude 2" (`Account.defaultLabel`) |
| Clear usage history? | `This clears, for "<name>" only:` · "8 days of trend history" · "a year of session and weekly history" · "The account itself, its sign-in and its settings are untouched — it stays." · "Clear history" (error), "Cancel". iOS adds "Current readings and the sign-in stay." |
| Remove "<name>"? | "This deletes, permanently and only for this account:" · "the saved sign-in" · "its cached usage and settings — alerts, credits, reset pings" · "8 days of trend history" · "a year of session and weekly history" · "Its launcher shortcut disappears. The pinned notification switches to <other>, if it was showing this account." · "To keep the history and just sign out, use Clear instead." · "Remove account" (error), "Cancel". While the store is degraded it is replaced by "Can't remove "<name>" right now" / "Secure storage isn't responding, so <name>'s saved sign-in can't be deleted with the rest of it. Try again once your saved sign-ins are back." with "OK". iOS (from 2026-11-04): the same dialog as an alert titled `Remove “<name>”?` from the touch-and-hold menu's Remove — the five Android lines minus the two Android-only ones (launcher shortcut, pinned notification) and "use Clear instead" (iOS has no sign-out), "reset alerts" for "reset pings"; buttons "Remove account" (destructive), "Cancel"; no degraded-store variant (the Keychain has none). The account page ends with a red, centred "Remove account" group opening the same alert (2026-11-05); removing the last account is allowed on iOS (Android blocks it), and the alert then adds "This is your only account. Cooldown goes back to its first-launch screen." |
| Details | title is the account name; lines (bodyMedium): "Email: …", "Last checked: Thu 7:46 PM (12m ago)", Claude only: "Last auto-renewed: …", the sign-in expiry line ("Sign-in expires around Aug 13, 8:30 PM · ~25d 2h 4m left" with the note "Anthropic doesn't report the real expiry — this is a flat ~30-day estimate from sign-in, corrected if renewal stops working earlier.", or "Sign-in valid until … · … to go", or the red renewal-dead line), "Added: Jul 16, 2026" (or "before v0.7" / "—"), monospace "Token …a1b2"; "Close". iOS: a list sheet with Plan, Email, Last checked, Last successful check, the expiry note, Added; no token tail |
| Accent colour | title "Accent colour · Claude"; a grid of 40 dp circles, six per row, 12 dp gap (first swatch the provider colour, then the selectable themes); selected has a 3 dp onSurface ring and a check; caption "Selected: Provider colour (default)"; "Close". iOS: a medium sheet with a 6-column grid of 36 pt circles, caption "First swatch: provider colour (default). This account's bars, numbers, charts and widgets use the colour picked here, whatever the theme colour." |

### 6.3 Alerts (Android) and Reset alerts (iOS)

| Section | Rows |
|---|---|
| Always-on notification (Android only; details in the notification file) | switch "Always-on usage notification" / "Silent and ongoing, with a status-bar icon. Nothing else posts on its own except the reset pings below."; when on: "Show accounts" (First: chip per account with its mark; Second · optional: "None" plus chips, the first account disabled), "Tapping a number opens" (chips "Cooldown", "That service's app"; note "The <Provider> app isn't installed — taps open Cooldown instead."), "Status-bar icon shows" (chips "First", "Second", "Whichever is higher"; with one account "One account, so there is nothing to choose" and a disabled "First" chip), a link row "System notification settings" / "The two channels left" |
| Reset pings | intro "The one notification that still posts on its own. "If busy" pings only when that window had reached 80% before it reset."; per account its name and two rows "5h reset" and "Weekly reset", each a three-segment control "Off" / "If busy" / "Always" |
| iOS Reset alerts | one row per account and window: mark 18 pt, "<name> · 5h" or "<name> · Weekly", segmented "Off" / "If busy" / "Always"; footer "“If busy” only alerts when the window that closed peaked at 80% or more. Alerts are set ahead from the reset times Cooldown last saw." Turning one on asks for notification permission |
| iOS Lock Screen | switch "Live Activity"; a "Shows" row listing the selected accounts (up to two; a third replaces the oldest); footer "Two accounts’ 5h and Weekly on the lock screen while a window runs. iOS keeps it up to 8 hours and it updates when Cooldown checks." or "Live Activities are switched off for Cooldown in the iOS Settings app." |

### 6.4 Appearance

One `SectionCard` (cover) or two cards side by side (inner). Rows in order, each a bold bodyLarge title, a bodySmall muted note, then the control:

| Row | Control and note |
|---|---|
| Theme | chips "System", "Light", "Dark" |
| Time format | chips "System", "12-hour", "24-hour"; note "Times shown like Thu 11:45 PM" or "Times shown like Thu 23:45" |
| Usage display | chips "Used", "Left"; note "How percentages read on every surface. Bars and colours always show what's spent." and a second line `Shows "47% used" — what each window has consumed.` or `Shows "53% left" — what remains of each window.` |
| Density | chips "Comfortable", "Compact"; note "How much each card shows at once." and "Comfortable keeps every chart open, exactly as today. Compact folds each card to a title, bar and one line — tap it to open the chart in place." (iOS: "Comfortable opens the 5-hour chart and keeps the rest a tap away. Compact folds …") |
| Chart height | chips "Small", "Medium", "Large"; note "How tall an open chart draws." and "Large draws everything. Small keeps just the lines — guides, pace, curve — and drops every label; the pace and estimate text below the chart still say the numbers." |
| Chart orientation | chips "Time across", "Time down"; note "Time down puts usage on the same axis as the bar above it." |
| Reset time | chips "Countdown", "Clock time"; note "Which form leads wherever a reset is shown. Where there's room, the other form keeps the second slot." and `Leads with "resets 4:12 PM". A clock time can't go stale on surfaces that refresh every 15 minutes.` or `Leads with "resets in 2h 14m", collapsing to "resets soon" inside five minutes.` |
| Show red past the pace mark | switch; "Colours the bar past the even-pace tick, in the app and in the notification. Off keeps the tick without the colour." (iOS: "… in the app, widgets and Live Activity …") |
| Theme color | six circles per row, 40 dp, 12 dp gap, 6 dp vertical padding: "Per provider" (a sweep of the two provider colours), "Material You" (Android only; follows the wallpaper), then Claude Orange `#D97757`, Blue, Indigo, Cyan, Teal, Green, Amber, Deep Orange, Red, Pink, Purple, Brown (light/dark hex in `A/ui/Palette.kt:55`; iOS omits Claude Orange and Material You); caption "Selected: Per provider" (+ " (follows your wallpaper)") |

Controls are 32 dp `FilterChip` rows with 8 dp gaps on Android and segmented pickers (or a swatch grid) on iOS. Every change applies immediately and refreshes the notification and widgets.

### 6.5 More (Android) — polling, credits, updates, about, diagnostics

| Section | Rows |
|---|---|
| Polling | "Check usage every" (bodyLarge), a four-segment control "5m" / "15m" / "30m" / "60m"; note "Applies to all configured profiles." or, under 15, "Short intervals use chained jobs; Android may delay them to save battery."; "Next automatic check: in ~12m" or "due now" |
| Usage credits | only when some account reports credits: intro "Pay-as-you-go credits that cover you once a plan window runs out. The section only appears for accounts that actually have a credit budget."; per account a switch "Show for <name>" / "Credits card on this profile's screen" |
| Updates | GitHub build only (Play build has no section): switch "Check automatically" / "Checks GitHub for a newer release every 6 hours, riding the usage poll. A new version notifies once; a failed check never notifies."; button "Check for updates" (spinner "Checking…"); status line ("Not checked yet — the first check rides the next poll.", "Last checked 2h 3m ago — <outcome>", "Last check failed 5m ago — couldn't reach GitHub. Retries with the next poll."); result dialog "Update available" (`v1.9 is available (you have v1.8).`, "Open GitHub", "Later", or a skipped-version note) or "You're up to date" (`You're running the latest version (v1.8).`) |
| About | centred card: 56 dp launcher icon, "Cooldown" (titleMedium), "Version <name>" (bodySmall, 7 taps unlock Debug until the app closes, then "Debug tools unlocked until the app is closed"), "Made by Robin Richard Rajan", "Built with Claude Code" (muted), outlined "Share feedback" (opens an email; fallback dialog "Email me at …"), text button "Privacy policy", the disclaimer "Unofficial. Not affiliated with Anthropic or OpenAI. "Claude" is a trademark of Anthropic, PBC. "ChatGPT" is a trademark of OpenAI." (labelSmall, centred) |
| iOS About | rows (2026-11-05, rev B §8): "Version" (the installed version and build, e.g. "2.0 (14)", read from the bundle), "Made by" "Robin Richard Rajan", "Share feedback" (opens Mail to robin@eam360.com, subject "Cooldown <version> feedback", body "What happened:" and a footer with the version, iOS version and "iPhone" or "iPad"; with no mail account, an alert "Email me at robin@eam360.com"), "Privacy policy" (opens the GitHub page), then the disclaimer "Unofficial. Not affiliated with Anthropic, OpenAI or Google. …" |
| Diagnostics and Debug (hidden until the 7-tap unlock) | "App log" (Info / Debug chips, last 12 lines in monospace, "Reload", "Share", "Clear", "Nothing logged yet."), "Crash reports" (count, newest time, "each is kept for 30 days. A report never contains tokens, emails or exception messages."; "Share", "Delete"), then Debug: synthetic data, trend diagnostics, account debug, "Simulate degraded store", "Crash test" ("Crash now", confirm "Crash Cooldown now?"). Not part of the public surface |

---

## 7. Sign-in flows

### 7.1 Claude (browser approval and a pasted code)

| Step | Android (inside the account card, replaces the card body) | iOS (sheet, medium or large, drag indicator) |
|---|---|---|
| Start | "Sign in on this phone" opens Claude's sign-in in a browser (the picker first when two or more are installed) | "Sign in to Claude" 20 pt bold; steps "1. Open the sign-in page and approve Cooldown. 2. Copy the code Claude shows you and paste it here."; capsule "Open claude.com sign-in" (Claude accent, black text) |
| Paste | "Finish signing in" (titleSmall); "Sign in on the page that opened, then copy the code it shows and paste it here."; outlined field "Paste the sign-in code" with a trailing "Paste" button; row "Finish sign-in" (enabled with text), "Reopen page", "Cancel" (flow-wrapped, the last button right-aligned) | field "Paste the code" (no autocapitalisation); "Cancel" and "Sign in" capsules (disabled until the page was opened and a code is present; "Signing in…" while working); an error line in red under the field |
| Done | message "<name> signed in — usage fetched, polling started."; polling starts | sheet dismisses; note "Name this account after signing in (e.g. “Teams”); the plan name comes from Anthropic." (v2.0, addendum T7; was "the plan tag") |

### 7.2 ChatGPT (device code)

A modal bottom sheet on Android (`A/SettingsScreen.kt:2296`) and a sheet on iOS (`I/App/UI/SignInSheets.swift:96`). Title "Sign in to ChatGPT" (titleLarge; 20 pt bold). Copy is shared (`A/ui/DeviceCodeCopy.kt`, `I/Packages/CooldownCore/Sources/CooldownCore/DeviceCodeCopy.swift`).

| Stage | Body copy | Controls |
|---|---|---|
| Starting | "Asking OpenAI for a code…" | spinner (20 dp); Close |
| Waiting | "Open the page in any browser — it can be a different device — sign in to ChatGPT, and enter this code."; instruction "Go to" · the verification address the server returns, shown without "https://" (bodyLarge semibold accent, selectable) · "and enter this code"; the code in monospace 30 sp, letter-spacing 2 sp, selectable (iOS 34 pt semibold, kerning 3); "Code expires in 14:32" (bodySmall, ticking each second; iOS "Expires in 14:32"); "Waiting for you to finish…"; prerequisite box (surfaceVariant, 12 dp radius, 12 dp padding, info glyph 16 dp) "First time? Turn on device code sign-in in ChatGPT → Settings → Security, or the code won't be accepted. Work accounts need their admin to allow it." with the link "Open ChatGPT security settings" | "Open in browser" (filled), "Copy code" (outlined), Cancel. iOS: one capsule "Open <verification address>" (ChatGPT green, black text) |
| Expired | "That code expired before it was used. Codes last 15 minutes." plus the prerequisite box ("If ChatGPT wouldn't accept the code, turn on device code sign-in in ChatGPT → Settings → Security, then get a new code. Work accounts need their admin to allow it.") | "Get a new code"; Close |
| Denied | "That sign-in didn't go through (HTTP 403). Start again with a new code." plus the same box | "Get a new code"; Close; iOS adds the security-settings link |
| Unavailable | "OpenAI has code sign-in switched off for this client, so there's no code to enter. Signing in would need the browser fallback, which this version doesn't have. Try again later." | Close only; iOS adds the link |
| Failed | "Couldn't reach OpenAI to get a code — <detail>." | "Get a new code"; Close |
| Granted | sheet closes; message "<name> signed in — usage fetched, polling started." (a replaced account adds "A different ChatGPT account signed in, so this card's old history was cleared.") | iOS shows a done page: ChatGPT mark 48 pt, "Signed in", "<name> · <plan>", capsule "Done" |

The sheet polls at the server-given interval while waiting. Approved by `design/provider-identity-wireframe.html` and the CCRM-54 (ChatGPT Account) device-code states in `design/2026-10-23-ios-wireframes.html`.

---

### Sample data (both platforms, CCRM-113 (Sample Port))

iOS since 2026-11-05 (parity wireframes rev B §7); Android since 2026-11-11 (`data/SampleMode.kt`, `ui/SampleUi.kt`; captures `design/research/2026-11-v20-build/A5/`); BEHAVIOR-MOBILE §7.

- **Entry.** "Try with sample data" · "Three made-up accounts to look around. Nothing is saved or sent." on the first screen (§2.7) and as a row of Add account (§6.2: Android the last row, under a divider; iOS the fourth row); it ignores the Personal | Work control. No confirmation. Android: entry lands on the All tab; Exit returns to the tab the user was on (the first screen when there was no account).
- **Isolation.** The app swaps to the built-in demo (temporary storage, no Keychain or credentials, nothing persisted) and swaps back on Exit. The widgets, notification, reset pings and status-bar icon stay real throughout. Android: a process-wide flag, never written; while it is on the app's root hands every screen a sample repository — a `UsageRepository` over in-memory twins (`MemoryPrefs` cache and registry, in-memory history and session log, no credentials) — and re-keys the whole tree, which closes every dialog, sheet and menu and drops its pending edit; a dialog binds to the repository it opened with, never to the flag. Real polling, reset checks and alerts build their own real repositories and never read it; `Surfaces.refresh` refuses a sample cache. Entering suspends the debug synthetic series in memory (restored on Exit). Opening the app from a real surface (a launch naming an account, or "refresh") exits the sample first; process death returns to the real app.
- **Settings in the sample.** Settings changed in the sample apply inside it, start from the user's own, and are dropped on Exit (BEHAVIOR-MOBILE §7). Android keeps them in an in-memory copy (`data/MemoryPrefs.kt`) of the user's Appearance and Summary settings (theme, time, usage display, end mark, reset form, over-pace, density, chart, open-on-summary, order, filter, map layers); iOS in two throwaway `UserDefaults` suites seeded from the real ones and deleted on Exit and at launch (`SampleStore`, `I/Shared/Faces.swift`, tested by `SampleStoreTests`). A tag edited in the sample stays in memory; nothing is saved or written.
- **Accounts.** Teams (Claude, **Work**), Personal (ChatGPT, Plus, **Personal**), Lab (Gemini, Pro, **Work**), tags fixed in every demo scenario (the addendum wins over rev B §9's untagged Lab); Teams' plan is Max on Android, Team on iOS. iOS's former fourth account, Product (a second Claude), is gone, so the stores' review notes describe the same three accounts on both phones. Readings are the iOS demo's, parsed on Android through the real parsers: Teams 46% at 46% elapsed, weekly 18%, a Fable limit, credits $123.90 / $400.00 · 31%; Personal 26% / 9% (well under); Lab 62% / 64% (above pace, held); seven days of history and sixteen closed windows per account. iOS `-demo upgraded` is the v1.9 upgrade (nothing stored, all Personal, Work disabled).
- **Banner.** Violet `#7C4DFF` at 16%, 12 dp/pt radius, "● Sample data — made-up accounts", trailing "Exit". iOS: tops each tab page. Android (`SampleBanner`): the first item under the app bar on every tab (All included), Settings and History; 36 dp tall in a 48 dp row so "Exit" has a whole target; "● Sample data" SemiBold violet (`#B388FF` on dark), " — made-up accounts" in the ink, 13 sp; where the line does not fit (font 1.3 on a 360 dp phone, and larger) the tail drops (`SampleBannerFit`); "● Sample data" and "Exit" never drop or wrap; at compact height it moves into the scrolling column with the other notices. "Exit" and Settings › Accounts "Exit sample data" are the same exit.
- **Main screen.** Unchanged layouts drawing the sample. The share card and the Summary snapshot stay and carry the 16 dp/pt `#7C4DFF` "● SAMPLE DATA" band (8.5 bold white). Android: the status line reads "Sample data · nothing is fetched" (12.5 sp muted, no glyph, not a button) on an account and on the Summary; no fetch-error notice and no "Weekly now leads" card; store and crash cards are the real ones.
- **Settings.** Account rows read "Sample". iOS: Add account, Reset alerts, Lock Screen, Sign in again and Remove are hidden; the account page hides Remove and the sign-in action. Android › Accounts: each card is the header alone — mark, name, the word "Sample" in place of the status dot, plan chip, tag chip, ⋮ (Rename…, Tag…, Accent colour…, Headline… work on the in-memory copy; Details…, Clear usage history… and Remove account are hidden); no e-mail line, no check line, no action row; "+ Add account" and "Reorder" give way to an outlined "Exit sample data". Alerts: one card, "Off while the sample is open" with the real-surfaces sentence. Appearance: every control works on the copy, with the caption "Changes here apply to the sample and are dropped when you exit." More: Polling, Usage credits and Diagnostics are hidden; Updates and About stay.

---

## 8. Share card

Android: ⋮ → "Share snapshot" renders off the main thread then shows a dialog "Share snapshot" containing the card image (16 dp radius, aspect ratio of the bitmap) with "Share" and "Cancel"; nothing is written to disk until Share, and the previous snapshot is deleted. iOS: ellipsis → "Share card" opens a sheet "Share card" with the card (18 pt radius, shadow) and a "Share" capsule (system share sheet); with no reading: "Nothing to share yet — this account has no reading." Approved by `design/2026-09-25-share-card-renders.html` rev D §9e. Source `A/share/ShareCard.kt:54`, `I/App/UI/ShareCard.swift:7`.

**Android, Weekly first** (built 2026-11-06 from parity wireframes rev B §6, `design/2026-11-05-ios-parity-wireframes.html`; it supersedes the order, figure size and trend window in the Android cells of Rows and Trend below): the rows follow the account's Headline through `ShareCard.order`, which reads the notification's Headline rule (`A/data/Headline.kt`) — Weekly first by default and under Auto, the 5h first under Headline 5-hour or while Auto sees the 5h binding; a spent week leads whatever the Headline. The lead row's figure is 24 sp bold on a figure line 6 dp taller; the other row keeps 17 sp. The trend title and chart are the lead window's ("Weekly trend" by default). The header ring stays the 5h gauge (the Weekly only on a plan with no 5h). A spent week, on a plan with a 5h: the Weekly figure and its pace slot in the severity red (`#FF5252` dark, `#C62828` light), the slot reading "Out until Sat 9:10 PM"; the 5h row (label, figure, bar, reset) and the header ring with its figure at 45%, the 5h pace slot empty and its reset slot "Resets 9:10 PM" (not the widgets' "Weekly spent"; "Stale" and "Starts when a message is sent" stay). A plan with no 5h has the one row, which leads. No reading: the ring's "—", no rows, no trend. The synthetic band is unchanged. Renders of the five states in both themes: `design/research/2026-11-06-share-card-weekly-first/`.

| Part | Android (360 dp wide, rendered at 4×, PNG `cooldown-snapshot-<ms>.png`) | iOS (300 pt wide, 3×) |
|---|---|---|
| Surface | dark `#0D0D0D`, light `#F5EFE8`, ink `#EDE8E4` / `#26211E`; no margin shown | palette background; 20 pt padding |
| Synthetic band | 16 dp band `#7C4DFF` at the top, "● SYNTHETIC" 8.5 sp bold white, only when the data is synthetic | shown only in the reviewer sample data (2026-11-05): a 16 pt `#7C4DFF` band, "● SAMPLE DATA" 8.5 pt bold white |
| Header | 88 dp ring (8 dp stroke) with the headline figure inside (22 sp bold, severity colour), 14 dp gap; at right the provider mark 16 dp in the account accent, the account name (17 sp bold, ellipsised), under it "as of 6:26 PM, Thu Oct 23" (11 sp, 60% ink) | 80 pt ring (7 pt stroke, 20 pt figure) beside the mark 22 pt, the name 21 pt bold, "as of 6:26 pm, Thu 23 Oct" 13 pt |
| Rows | per window present: 12 dp gap, label 12.5 sp ("5h" / "Weekly", "… left" in Left mode) and figure 17 sp bold right-aligned, a 10 dp bar with a pace tick, then pace slot (10.5 sp) left and the reset right; on the Weekly row the slot is the tinted span of the pace line (CCRM-104 D9, `ShareCard.weeklyPace`): "13h 59m ahead" red / "6d 23h behind" green / "On the mark" ink, semibold, the reset keeping the right (the card renders Used, so the figure is unchanged); the 5h row keeps its even-pace phrase | the lead row first (24 pt figure), then the other (19 pt). The lead follows the account's Headline through the widgets' rule — Weekly by default, 5h under Headline 5-hour or when Auto sees the 5h binding; a spent week leads whatever the Headline, red, with its "Out until Sun 9:30 pm" line, and the 5h row dims to 45% with an empty phrase slot; a plan with no 5h has one row (2026-11-05). A 10 pt bar, then the pace line 12.5 pt left and "Resets Thu 9:50 pm" right |
| Trend | divider, "5h trend" or "Weekly trend" 10.5 sp, a 120 dp chart (drawn with the same elements as 3.3, no callout) when the window has a reset time and is not idle | "<lead> trend" ("Weekly trend" or "5h trend") 13 pt, 130 pt chart of the lead window; the header ring stays the 5h gauge (Weekly only on a plan with no 5h) |
| Footer | "COOLDOWN" 9.5 sp, letter-spacing 0.06, 45% ink | "COOLDOWN" 11 pt medium, tracked |

Differs from Android: iOS shows the band only for sample data; a weekly-only plan has the one row. **Android half of the Weekly-first card (listed for the v2.0 Android session, 2026-11-05):** order the rows by the Headline rule, 24 sp lead figure vs 17 sp, the trend follows the lead, the ring stays the 5h gauge, a spent week leads and dims the 5h row.

### 8.1 Summary snapshot (v2.0, both platforms, CCRM-112 (Summary Snapshot))

On the All tab the share item reads "Share snapshot" (the account tabs keep their own share). It shares one image of the Summary as filtered: the header (the filter's name 17 bold — "All accounts", "Work accounts", "Personal accounts" — "as of …", the order's name at the right), the rows in the screen's order and filter, "Pace map" and the map drawn with the layers saved on screen (grid, trails, figures, projection; Vs pace draws as Used) and its foot line — never the chips, a pinned callout or its dimming — then "COOLDOWN". Full row: a 32 dp/pt 5h ring (grey when spent), mark 14, name 13 semibold, the figure 17 bold (Used; "left" in Left mode; Vs pace as Used), an 8 bar with its pace tick, then the pace span left (tinted) and "Resets Sun 9:30 PM" right; from six rows shown, one line each. Spent rows read "Out until …" in red; signed-out or unread rows are at 50% with no bar fill and no reset; a week with a reading but no clock yet reads as the Summary does — its figure ("0%"), a bare track, "Not started yet", full ink (CCBG-94 (Snapshot Not Started)); a stale row reads "Stale" in the pace slot; held rows read "Held · …". The glyph column (12 box + 4, before the mark) shows **only while the rows on the card carry both tags** (iOS `Resolved.snapshotGlyphs`), so only under All; an install whose accounts are all Personal shares the card without it. Names, never e-mail addresses. The sample adds the "● SAMPLE DATA" band.

| | Android | iOS |
|---|---|---|
| Opening it | the All tab's top-bar ⋮ returns with one item, "Share snapshot" (hidden when no shown account has a reading), opening the CCRM-24 (Share Card) preview **dialog** | the ellipsis menu's share item; a **sheet** titled "Share snapshot" previews the card above a Share button (the system share sheet), as the account card's share does; with no row shown having a reading it says "Nothing to share yet — no account has a reading." |
| Card | 360 dp at 4×; "as of" = the **oldest** reading shown; map 320 × 200 dp, the screen's own painter and scene | 300 pt at 3× on the app ground, padding 20; "as of 12:39 pm, Wed 11 Nov" 11 pt = the **newest** reading among the rows shown (the account card's own form); order name 11 pt `sub`; a rule, "Pace map" 10.5 pt, the map 200 pt high, its foot line 9.5 pt |
| Rows | the glyph 12 dp + 4 dp | pace span 10.5 pt; one-line rows 26 pt: mark, name 12 pt semibold (58 pt), bar 7 pt, the short word ("13h 26m ahead", "Out · Fri"; 10 pt, 64 pt), the figure 14 pt bold; glyph 11 pt in a 12 pt box |
| Unread / held | "Sign in" / "No reading"; "Held · back at …" | "Sign in again" / "No reading"; "Held · 3:28 pm" in orange; spent "Out until Fri 3:05 pm" left, the reset right |

The opening differs (Android a dialog, iOS a sheet with a preview); the image is the same card, with the per-platform sizes and the "as of" rule above. Code `A/share/SummarySnapshot.kt`, `I/App/UI/SummarySnapshot.swift`; `render/` in the I3/I4 captures holds the card in every state.

---

## 9. Reading the sources

| Area | Android | iOS |
|---|---|---|
| Frame, tabs, notices, errors | `A/MainActivity.kt`, `A/StoreNotices.kt`, `A/CrashCards.kt`, `A/data/ErrorKind.kt`, `A/data/QuickLinks.kt` | `I/App/UI/MainScreens.swift`, `I/Shared/CardContent.swift` (`Notice`) |
| Cards, bar, status line | `A/MainCards.kt`, `A/ui/BarGeometry.kt`, `A/ui/CompactLineCopy.kt`, `A/ui/StatusLineCopy.kt`, `A/ui/Estimates.kt` | `I/App/UI/Cards.swift`, `I/Shared/Charts.swift`, `I/Shared/CardContent.swift` |
| Chart | `A/ui/Sparkline.kt`, `A/ui/Adaptive.kt`, `A/ui/DisplayPrefs.kt` | `I/Shared/Charts.swift`, `I/App/Model/CardLayout.swift` |
| Layout sheet | `A/LayoutSheet.kt`, `A/data/CardLayout.kt`, `A/ui/ReorderList.kt` | `I/App/UI/LayoutSheet.swift` |
| Settings and sign-in | `A/SettingsScreen.kt`, `A/ui/AccountStatusLine.kt`, `A/ui/DeviceCodeCopy.kt`, `app/src/github/.../channel/UpdatesCard.kt` | `I/App/UI/SettingsHistory.swift`, `I/App/UI/SignInSheets.swift`, `I/Shared/Display.swift` |
| History | `A/HistoryScreen.kt`, `A/PlanFitBlock.kt`, `A/ui/PlanFitCopy.kt` | `I/App/UI/SettingsHistory.swift` |
| Share card | `A/share/ShareCard.kt`, `A/share/ChartBitmap.kt` | `I/App/UI/ShareCard.swift` |

Shared numbers (severity breakpoints, projection, pace, dead zone, history) are in `spec/core/BEHAVIOR-SPEC.md`; cadence, reset pings and conditions are in `spec/mobile/BEHAVIOR-MOBILE.md`.

## v2.0 on iOS — CCRM-96…100, RUNBOOK-v2.0 Step 6

As built on the v2.0 branch (RUNBOOK-v2.0); each entry names the section it amends, which still describes v1.9.

#### §2.3 Account tabs — add
iOS: with two or more accounts an **"All"** tab comes first (icon `text.alignleft`, ink underline, counts as one tab
for the three-across / scroll rule). iPad landscape: an **"All accounts"** row tops the sidebar (count on the right,
fill when selected); it is the default detail. iPad portrait: "All" is the first tab, as on the iPhone, and shows
the Summary. Launch: All when Open on the summary is on and 2+ accounts; otherwise the last account
looked at (stored `lastAccount`), else the first. `ios/App/UI/MainScreens.swift`, `AppModel.launchKey`.

#### §2.4 Cards, in order — iOS row
"Default order: Weekly, then 5h (Headline 5-hour: 5h first); a layout moved in the sheet keeps its order (flag
`reordered` in the stored layout; a v1.9 layout with a non-standard order counts as moved). Show/hide/fold never
make the order the user's; Reset layout hands it back to the Headline. Comfortable opens the first window card on
iPhone (both on iPad)." `CardLayout.ordered(by:)`, `AppModel.drawnLayout`.

#### §2.4.4 5-hour card — iOS
Remove "N% below/above even pace" / "On even pace". Under the bar: the V1 caption and dashed room. Open: the
projection line, then the reset row. Folded: "Resets in 2h 20m · on track for ~69%" (red with "runs out ~3:25 pm").

#### §2.4.5 7-day card — iOS
Each half (All, model cap): bar, V1 caption, chart, band line ("Slow down · ≈11%/day", full width "… until Fri"),
projection line, reset. Spent: red figure, "Out until Sun 9:30 pm", no caption. **Show model limits off**
(per account, default on): no model half, All takes the full width.

#### §2.6 Notices above the cards — add
"Weekly now leads" (CCRM-96 F1): title 15 pt semibold, body "The weekly limit is the one every plan has, so it's
now the big number everywhere, with 5h beside it. Prefer 5h on top?", buttons "Keep Weekly" (Claude-accent capsule,
black text) and "Keep 5h first" (outlined). Card on `fill`, 14 pt radius. Shown above the cards and above the
Summary until answered; settled at the first v2.0 launch (`weeklyFirstNotice` = pending only when accounts
existed). "Keep 5h first" sets every account's Headline to 5-hour.

#### New §2.8 The Summary (All tab) — iOS
List card "Weekly · most room first", rows by `SummaryRank` (eligible by Pace, held, spent, no reading). Row: mark
18 · name 15 · plan chip · companion 12 ("no 5h" / "5h 34%" / held "5h 93% · back 3:40 pm" bold orange) · figure
17 bold in the severity colour (Usage display mode, no "vs pace" word); bar 10 pt with dashed room; V1 caption;
"Room to spare · ≈22%/day · resets Fri" 13 pt (spent "Out until Thu 8:00 am", row at 60%; "No reading"). From
six accounts, one-line rows: rank disc 16 pt in the accent, name, bar, word ("Slow down", "5h · 3:40 pm",
"Out · Thu", "No reading"), figure. Pace map card below (iPhone 230 pt; iPad two columns, list left, map 340 pt
right, title "All accounts" 30 pt bold): over-pace triangle red 8%, 0/50/100% lines, dashed even-pace diagonal
labelled "even pace"; a 9 pt dot per plotted account; spent: ring with ×. Header, filter, row glyph, wedge words,
grid, axis, foot line, layers, callout and names: §2.8. Tapping a row opens that account. `ios/App/UI/Summary.swift`.

#### §2.2 iPad sidebar
Segments "Headline · Weekly · 5h", default Headline (a stored v1.9 choice is kept). Headline: each row shows its
account's lead window (Auto takes over while 5h binds) and names it under the figure ("Weekly"/"5h"; a binding 5h
tag is bold orange). Weekly and 5h segments as v1.9.

#### §6.2 Accounts — iOS
The row pushes an account page: **Account** (Plan; Accent colour… with its dot), **Headline** ("Lead with",
caption "What widgets, the lock screen, the Live Activity, the iPad sidebar and the card order show first.",
segmented Weekly · 5-hour · Auto; footer "Auto shows Weekly, and switches to 5h while 5h is past 90% or on course to
run out before it resets."; a plan with no 5h: control greyed, footer "This plan has no 5-hour window, so Weekly is
always the headline."), **Model limits** (only while the reading has a model cap: "Show model limits", subtitle
"Fable · 30% this week" / "Hidden: Fable. The limit still applies to that model; Cooldown just stops drawing it."),
**Usage** (Details…, Clear usage history… in red). The touch-and-hold menu is unchanged.

#### §6.4 Appearance — iOS
Usage display: "Used · Left · Vs pace", Vs pace note "Shows “+10%” — how far under the pace mark you are (“−20%”:
over it)." New "Open on the summary" switch (2+ accounts only, default on), subtitle "With two or more accounts,
start on the All tab. Off: start on the last account you looked at." Density note now "Comfortable opens the first
card's chart and keeps the rest a tap away. …"

#### §8 Share card — iOS
The 5h row's line is now its projection ("On track for ~69%"); the Weekly row reads the band words. Layout otherwise
unchanged (5h row still above Weekly — not in any v2.0 wireframe).

## Gemini and the spent-week gate on iOS — CCRM-55, CCRM-102

As built on the v2.0 branch (RUNBOOK-v2.0 Steps 6a and 6c, iOS).

- **§2.4.4 5-hour card — iOS, add (CCRM-102 (Spent Week Gate)):** while the account's Weekly is spent (≥ 99.5) the 5h
  card is disabled, not hidden: header, bar (no dashed room), chart without its projection and the reset line(s) at 45%
  ("on track for ~N%" and the V1 caption dropped); folded it shows the reset line alone; the note above sits under it
  at full opacity (14.5 pt, ink), wrapping. Same rule inside the Gemini pool pair. `ios/App/UI/Cards.swift`.
- **§2.4 Cards, add (CCRM-55):** a Gemini account's cards are the shipped ones in Gemini Blue; Argon is the 7-day card's
  model half ("Argon", hidden by Show model limits); once the Claude + GPT pool has been used (a reset clock or a reading
  above 0), a group label "Claude + GPT in Antigravity" (15 pt semibold, `txt`) and a second pair (7-day, then 5h, the same
  anatomy, folded by default, no history so no chart samples) follow the account's cards. Untouched: nothing drawn.
- **§2.6 Notices, add (H):** Gemini only, after a 200 without the Gemini groups: title "Google changed something — usage
  unavailable", evidence line in 12.5 pt monospaced `sub` "Reply held 1 of 3 groups", body "Cooldown won’t guess. An
  app update may be needed."; warning tone (cards dim as under any notice); the last good cards stay; no cards and the
  notice alone when there was never a reading. No action button on iOS (no update channel).
- **§2.7 First launch (L3):** a third mark and a third 260 pt capsule "Add a Gemini account" in Gemini Blue; body "…for
  Claude, ChatGPT and Gemini."; footnote "Not affiliated with Anthropic, OpenAI or Google. “Gemini” and “Antigravity” are
  trademarks of Google LLC." (centred). Without the client secret the Gemini mark, capsule and body mention are hidden.
- **§6.4/6.5 About (L2), iOS:** "Unofficial. Not affiliated with Anthropic, OpenAI or Google. “Claude” is a trademark of
  Anthropic, PBC; “ChatGPT” is a trademark of OpenAI; “Gemini” and “Antigravity” are trademarks of Google LLC."
- **§7 Sign-in flows, new §7.3 Gemini (iOS, I1–I5):** Add account gains a third row (32 pt mark, "Gemini", "Signs in with
  Google in your browser, then paste the code back."), hidden on a build without the secret; the sheet's footnote reads
  "Cooldown reads the same usage numbers the Claude, ChatGPT and Gemini apps show. Tokens stay in this device’s Keychain.
  Not affiliated with Anthropic, OpenAI or Google. “Gemini” and “Antigravity” are trademarks of Google LLC."; the sheet
  now opens at medium and large. Sign-in sheet: "Sign in to Gemini", steps "1. Sign in with Google and approve Cooldown.
  2. Copy the code Google shows you and paste it here.", Gemini-Blue capsule "Sign in with Google" opening an
  `ASWebAuthenticationSession` (shared browser session; it never auto-completes, the user closes it). Then: "Copy the code
  Google shows you and paste it here.", a field (no autocapitalisation, placeholder "4/…"), helper "The code starts with
  4/. You can also paste the whole address.", buttons Cancel · Continue (Continue disabled while empty). Errors: red line
  under the field and the line "That code didn’t work — it may have expired or been copied wrong. Start again." or "That
  code is for a different sign-in — start again."; the left button becomes "Start again" (a fresh PKCE attempt).
- **§2.2 iPad sidebar / §6.2 Accounts:** a Gemini account is an ordinary row (mark, name, plan pill Free · Pro · Ultra from
  `loadCodeAssist`'s `paidTier.id`); re-sign-in opens the Gemini sheet.
## v2.0 on Android — CCRM-96…100, CCRM-102, RUNBOOK-v2.0 Step 5

As built on the v2.0 branch; each entry names the section it amends, which still describes v1.9.

#### §2.3 Account tabs — add
Android: with two or more signed-in accounts an **"All"** tab comes first (a three-bar glyph, 20 dp, in ink, then
"All"); its label is measured like the others for the fixed/scrollable rule (§2.3 Fit).
While All is showing the shell takes Settings' neutral scheme (global accent, no room tint) and the top bar's ⋮
carries only "Share snapshot" (§8.1). Launch: a notification or shortcut naming an account opens that account; otherwise All
when Open on the summary is on and 2+ accounts; otherwise the last account looked at (stored `lastAccount` in its
own `main_ui` preferences file), else the first. `MainActivity.kt` (`ProfileTabs`, `LastAccount`).

#### §2.4 Cards, in order — Android row
**Shape grammar (CCRM-103 (Hybrid Notification), Step 6d):** the account page has one window card, the account
tile (below), then Usage credits. The tile takes the 7-day card's slot (`CardId.WEEKLY`): its place, its
show/hide and its More fold; the 5-hour card is no longer a card of its own, so `dataCards` lists `SESSION` only
for an account reporting no 7-day window at all (the tile then sits in that slot). The Headline's order rule
(Weekly first, Headline 5-hour: 5h first) no longer moves anything visible: the ring is always the tile's left.
A layout whose stored order differs from the v1.9 default counts as reordered and keeps its order; only a drag
stores an order. `dataCards`, `cardOrderLayout` in `MainCards.kt`, `LayoutSheet.kt`.

#### §2.4.2 Row style — figure
Every figure on the tile and the Summary rows is the grammar's figure (shape grammar rev C §1c): the system sans
with tabular, lining digits (`tnum, lnum`), bold, its "%" at 58% on the baseline (`grammarFigure`,
`grammarDigits`). Its colour is the usage's severity colour (the account colour, its step above 80). Usage
display: Used "52%"; Left "48%" on the tile (no word; Summary keeps "48% left"); **Vs pace** the **signed**
distance to the mark (CCRM-104 (Pace Line Parity) D1, core §6.3, rounded toward zero): "+19%" green under the mark
(dark #9CCC65 / light #2E7D32), "−13%" (U+2212) red over it (#FF5252 / #C62828), "0%" onSurface on it, with no
"vs pace" word — falling back to the Used figure in its severity colour when spent, unclocked or unread. The
**rings stay unsigned** ("19%" in the tone; `AppPace.figure`). `AppPace.figureSigned`, `grammarFigureColor`,
`Grammar.vsColor`.

#### §2.4.3 The usage bar — add
Under pace (used < mark) the room from the fill to the tick is a dashed outline: 1.3 dp, onSurface 55%, dash
3 dp / gap 2 dp, inset 1 dp top and bottom, open on the fill side. **V1 caption** under every in-app window bar
(the tile's Weekly and each model limit, Summary rows; the 5h is a ring and has none): 11.5 sp bodySmall in onSurfaceVariant, 5 dp above, 17 dp line (22 dp in
all; the line grows with large text, CCBG-93 (Summary Caption Clip)), starting 5 dp right of the tick or ending 5 dp left of it when it would not fit (`PaceText.captionRightOfMark`); a caption wider than the bar takes its short form, "52% used" / "13 over" (`PaceText.captionForms`, as the notification).
Text `PaceText.caption`: "39% pace mark · 13 over" / "· 10 under" / "· on it"; Left "40% pace mark · 10 to spare";
Vs pace "52% used · 39% pace mark" (D6). The leading value ("39%"; Vs pace "52% used") is onSurface SemiBold. No caption when
spent, unclocked or unread. A press on a figure underlines this caption for 3 s (§2.4.4). Credits bars unchanged.
`PaceCaption(underline)`, `UsageBarLine(dashedRoom)`.

#### §2.4.4 + §2.4.5 The account tile — Android (replaces the 5-hour and 7-day cards)
Shape grammar rev D §1–§2, §7 (`design/2026-11-02-shape-grammar.html`), CCRM-103 (Hybrid Notification). One card
(the card shell, 16 dp padding). **Tile:** a row, centred vertically: the **5h ring** (`GrammarRingBox`) 72 dp —
56 dp under 360 dp of window width, 84 dp from 700 dp — then a 14 dp gap (12 dp under 360 dp), then the **7-day
bars** stacked with 8 dp between them. The ring is hollow: track onSurface-ink 16%, the arc from 12 o'clock in the
account colour (its severity step above 80, the 9% minimum fill), the pace tick across the band, the red slice past
the tick when more than 3 points ahead (per the over-pace toggle), the dashed room from the fill to the tick in
the account colour at 60%, the usage end mark per Appearance › Usage end mark. In the bore only the figure (0.25 of
the ring, four characters 0.21, fitted 1.5 dp clear of the band), ink, the severity colour and ExtraBold above 90%,
the Vs pace colour in Vs pace; **no "5h" text anywhere**. TalkBack: "5-hour window, 34 percent" ("… 66 percent
left", "… 19 percent under/over pace"; no reading "… no reading yet"). **Bars:** first "Weekly · All models" (just
"Weekly" when no model limit is shown) — label 13.5 sp onSurface, the figure at the ring's number size (18 sp at
72 dp, 14 at 56, 21 at 84), bar 10 dp; then each shown model limit — label 12.5 sp onSurfaceVariant, figure 14 sp,
bar 7 dp. Label and figure share a 20 dp row (label ellipsises), 5 dp above the bar; every bar carries the dashed
room and the V1 caption (§2.4.3).
**Closed (Compact, the default fold):** under the tile, 10 dp below, the **pace line** 13.5 sp SemiBold onSurface
(`AppPace.weeklyFolded`, CCRM-104 (Pace Line Parity) D3 and CCRM-108 (Reset Named): "21h 50m ahead · resets Sun 9:30 PM" — the span "<duration> ahead|behind"
semibold in the tone (red over, green under), "On the mark · resets Sun 9:30 PM" with the span in the ink, the rest onSurface;
the band words and "≈X%/day" are gone from the line; spent "Out until Sun 9:30 PM"; no clock "Starts when a message is
sent"). The form is measured at run time on the scaled text width (`PaceLineText`, `PaceText.fit`), dropping per the
pace-line row of §1.2 ("· resets Sun 9:30 PM" → "· Sun 9:30 PM" → "· Sun" → the span alone, never cut); if even that does not fit
the text steps down to 10 sp, then ellipsizes; 2 dp below, the **5h line** 13.5 sp
onSurfaceVariant led by a 12 dp ring glyph (the status-bar ring geometry, the severity colour, red slice, × at 100%)
and a 4 dp gap instead of the word "5h": "resets in 2h 20m · on track for ~69%" (on course to run out: "resets in
1h 30m · on track for 100% at 3:25 PM", that clause bold orange; no projection: "resets in 2h 20m"); no 5h line
before the 5h has a reading and a clock. Lines wrap rather than ellipsise. No chevron: the whole card is the tap
target and opens the tile.
**Open (Comfortable, or a tapped Compact tile):** no lines under the tile; 12 dp below it the chips **7d ·
<model> · 5h** (CCRM-106 (7d Chip), both platforms; FilterChips, 8 dp apart; "5h" only on an account with a 5h, model chips only while model limits
show; no chips with neither; `TileChips`, `SEVEN_DAY_CHIP`; TalkBack reads the 7d chip as "7-day, all models"; the
selection key is unchanged — null = the 7-day series — so a stored choice survives; "Weekly · All models" on the lead
bar row is unchanged; the inner screen's two stacks take the same chip row) and the chosen series under them. 7d
or a model: the Weekly chart as before (§3), then
the pace line (`AppPace.weeklyOpen`, the same forms in bodySmall onSurfaceVariant, span in the tone; spent "Out until
…" bold orange), the projection line, a divider and the reset row. **5h:** the 5h chart, "At this pace: ~69% when the window resets · 15.0%/h ⓘ", a divider and the
5h reset row, and the ring draws its **projection**: a dotted arc (0.4 of the band, the fill colour at 75%) from
just past the fill to the projected end (~69%, or 100% when it gets there first) — only while the 5h chip is
chosen. Tapping the ring on an open tile chooses the 5h chip **and** swaps the 5h chart's projection line for the
5h window's caption for 3 s (rev D C5, "both on one tap"). The 5h chip is not stored (session only); the tile starts on
5h when the drawn card order leads with the 5h (Headline 5-hour, or the 5-hour card dragged first; credits don't
count), else on 7d — as iOS (`tileStartsOnFive`). The series choice is still stored. Inner screen (≥ 700 dp) with a model limit shown and 7d or a model chosen: two chart columns, all models
left and the chosen model (7d → the first) right, each with its lines and reset row; the tile's bars above, not
repeated.
**Press a figure (CCRM-104 (Pace Line Parity) D5; replaces rev D §2b's "Pace catches up …" readout):** the Weekly figure
or a model figure underlines its V1 caption (§2.4.3) for 3 s (the font's own underline; `PaceCaption(underline)`),
and TalkBack reads the caption as the figure's click action label. The ring's figure swaps the 5h line after its
glyph (closed tile) or the projection line under the 5h chart (open tile, bodySmall) for the 5h window's caption
("40% pace mark · 12 over", in the global Used/Left/Vs pace mode; onSurface SemiBold, underlined) for 3 s. Spent,
unclocked or unread: no caption, so the press does nothing (a plain ring press still opens the tile). `tapReadout`
and the markTime words are gone from the app (`PaceText.markReadout` stays for its fixtures).
**States.** No reading yet: the ring's track and "—" (ink 50%), a bare bar with "—" (onSurfaceVariant). **A plan
with no 5h** (rev C §7): the ring slot stays, grey track (ink 22%) and a cross (0.12 of the ring each way, ink
45%), no figure, label "No 5-hour window"; no 5h line, no 5h chip. **Spent week** (CCRM-102 (Spent Week Gate)):
the ring greys (track only, no arc, no tick), its figure at 45%; the band line says "Out until …" and the 5h line
becomes the note "Weekly limit reached — 5h can’t be used until Sun 9:30 PM" (13.5 sp onSurfaceVariant, full
opacity, no glyph); the ring's tap reads nothing out. With the 5h chip chosen on a spent week, the 5h chart, its
lines and reset row draw at 45% with no projection, then a divider and the same note in bodySmall SemiBold
onSurface. **Show model limits off:** the bars are "Weekly" alone, no model chips. **Saved layouts** (judge F2):
the tile follows its own sheet row, "Usage" (its place and its fold); the sheet hides or shows both window ids
together. A v1.9/v2.0 layout that hid only the reported window card while keeping the other is read as the tile
shown, folded as the other was (`tileLayout`, which the sheet and the screen both read through, so the row's switch
always matches the screen); the order is left alone — it is hidden only when the stored layout hides **both**. And the sheet's never-blank rule (§4)
holds whatever was stored: if nothing would be drawn while the account reports cards, the tile shows
(`drawnCards`).
`AccountTile`, `TileBarRow`, `tileRingDp`, `tileRingState`, `ringDescription`, `tileProjectionPct`,
`drawnCards`, `tileStartsOnFive`, `PaceLineText` in `MainCards.kt`; `AppPace.fiveHourLine`, `AppPace.weeklyPaceLine`.

#### §2.6 Notices above the cards — add
"Weekly now leads" (CCRM-96 F1), in the crash-card slot above the tabs (in the page column at compact height),
after the store and crash cards: card on surfaceContainer, 12 dp radius, outlineVariant border; title bodyMedium
SemiBold "Weekly now leads"; body bodySmall muted "The weekly limit is the one every plan has, so it's now the big
number everywhere, with 5h beside it. Prefer 5h on top?"; a filled "Keep Weekly" and an outlined "Keep 5h first".
Shown while `weeklyFirstNotice` is pending, on every tab including All, until a button is tapped; either button
marks it shown. "Keep 5h first" sets every account's Headline to 5-hour, re-posts the surfaces and re-reads the order.

#### New §2.8 The Summary (All tab) — Android
List card headed by "Weekly" (titleSmall), the All · Personal · Work control and the sort menu (§2.8), rows by `SummaryRank`, hairline dividers,
each row tappable (opens that account's tab). Full row (shape grammar rev D S1, CCRM-103 (Hybrid Notification)):
a **48 dp 5h ring** on the left, centred on the row, then a 12 dp gap and the three lines. The ring is §2.4.4's
ring at 48 dp (band 0.10 of the ring, figure up to 0.29 fitted to the bore, the account colour and its step, the
held row's ring loud above 90%), not tappable (the row is); a plan with no 5h: the grey ring with a cross, so every
row has the same frame; a spent week: greyed, figure dim; an unread account: the track and "—". The ring is not
dimmed with the row. Line 1: mark 18 dp in the accent · name 15 sp (ellipsised) · plan pill 11 sp (outlineVariant
border, 6 dp radius) · figure 17 sp bold in the grammar's type (§2.4.2) in the severity colour (Usage display;
Left "48% left"; Vs pace signed ("+19%" green, "−13%" red, "0%" onSurface; D1); "—" unread). **No companion text** — the ring replaced
"5h 34%" and "no 5h". Bar 10 dp with neutral tick, red past it per the toggle, dashed room; V1 caption; line 3:
13 sp, the **pace line** (D3 and CCRM-108 (Reset Named): "1d 7h behind · resets Fri 9:00 AM", span semibold in the tone, the rest onSurface,
`SummaryRows.paceLine`; forms measured on the row's width and dropped per §1.2's pace-line row, never the duration+word;
10 sp then ellipsis as the last resort), starting at the row's text edge — no pace pip since 2026-11-02 (Robin: "what is the point of showing
that indicator … just below the actual bar which also shows that anyway"; the freed 48 dp goes to the words, so
"resets <day>" survives on narrower widths) (spent "Out until Thu 8:00 AM",
"Not started yet", "No reading yet"; a row its 5h holds: "Held · back at 3:40 PM · resets Fri",
`SummaryRows.heldWords`, which still drops "· resets <day>" first under the measured width, CCBG-60 (Summary
Line Clip)); the name gives way before the plan pill, which keeps its place beside it (the pill
reads `Fmt.planLabel`, as Settings: "Pro 100", CCBG-62 (Plan Pill Case), API-CONTRACT §11). **Press the figure** (CCRM-104 D5; CCBG-56 (Summary Readout Tap)): its own 80 × 48 dp target over the
figure and the bar's end underlines the row's V1 caption for 3 s (the font's own underline, CCBG-57 (Readout
Underline Clip)); the target exists only while there is a caption (Fable F7), so a spent, unclocked or unread
row's figure opens the account like the rest of the row; TalkBack: the figure carries the caption as its click
action. Spent and unread rows:
line 1, bar and words at 50%. From six accounts, one-line rows (no ring): 17 dp rank disc in the accent (number
10.5 sp bold, #121212), name 64 dp, 8 dp bar, word 12.5 sp 90 dp (the pace line's tinted span alone, semibold in the tone — "13h 59m ahead", "6d 23h behind", "On the mark"; a held row "Held · 3:40 PM"
in the 5h colour — the full row's words without "resets", never "5h" (judge F3); "Held" with no 5h clock — "Out ·
Thu", "No reading"), figure 15 sp bold in the grammar's type (Vs pace signed), 40 dp right-aligned. Having no
caption on the row, a press on the figure (clickable, 40 dp wide; TalkBack label the caption) swaps the word slot for the
caption's form that fits 90 dp ("13 over", "52% used"; semibold, underlined) for 3 s; the row's tap opens the account.
Pace map card under it (plot 230 dp on the cover): insets
30/10/12/26 dp; ahead-of-pace triangle red 7% dark / 5% light; 0/50/100% lines (ink 35% / 12%) with 10 sp labels;
dashed even-pace diagonal in the 80% step; wedge words 10.5 sp ink 55%: "ahead of pace" top-left above the
diagonal, "behind pace" bottom-right below it (`PaceText.MAP_AHEAD` / `MAP_BEHIND`, CCRM-104 (Pace Line Parity));
"even pace" 10 sp along the diagonal, starting at 62% of the week and sliding (6% steps) to the first stretch no
dot sits on. Per plotted account a 5.5 dp dot with a 2 dp card ring; spent: × on the dot. Title chips, grid, axis,
foot line, who is plotted, layers, callout and names: §2.8. Inner screen
(≥ 700 dp): two columns, list left, square map right. Footer: the status line with the stalest account's check;
tapping refreshes every account. `SummaryScreen.kt`, `ui/PaceMap.kt`, `ui/SummaryRows.kt`.

#### §4.1 Layout sheet — add
Rows show the drawn order (Headline order for an untouched layout); see §2.4. Since the account tile, an account
lists one window row, **"Usage"** (Robin, 2026-11-02; the calendar glyph), for the tile — never a separate
"5-hour window" or "7-day window" row — and hiding it hides both window ids; the ⋮'s
"Main screen layout" item needs two cards, so an account without a credits card no longer shows it (Share
snapshot stays).

#### §6.2 Accounts — Android
The account card's ⋮ gains **"Headline…"** (signed-in accounts, after "Accent colour…"), opening a bottom sheet:
mark 20 dp + the account name (titleMedium); **HEADLINE** label; "What the notification, status-bar gauge, new
widgets and the card order lead with" (bodyLarge); segmented Weekly · 5-hour · Auto; caption "Auto shows Weekly,
and switches to 5h while 5h is past 90% or on course to run out before it resets."; an account with no 5h: the group
at 45%, segments disabled, caption "This plan has no 5-hour window, so Weekly is always the headline.". **MODEL
LIMITS** (only while the reading has a cap): switch "Show model limits", subtitle "Fable · 30% this week" / "Hidden:
Fable. The limit still applies to that model; Cooldown just stops drawing it." (two or more: "The limits still
apply to those models; Cooldown just stops drawing them."). Each change re-posts the surfaces.

#### §6.4 Appearance — Android
Usage display chips "Used · Left · Vs pace"; Vs pace note "Shows "+10%" — how far under the pace mark you are
("−20%": over it)." New switch row "Open on the summary" after Usage display (2+ signed-in accounts only, default on),
subtitle "With two or more accounts, start on the All tab. Off: start on the last account you looked at."

#### §8 Share card — Android
Unchanged in this step (share/ not touched): Vs pace renders the share card as Used. The Summary snapshot is §8.1.


## The shape grammar on iOS — CCRM-103 (Hybrid Notification), RUNBOOK-v2.0 Step 6d

CCRM-103 (Hybrid Notification), the shape grammar (`design/2026-11-02-shape-grammar.html` rev D, §1, §2, §2b, §6, §7,
§7b).

### Account tile (`AccountTile`, `ios/App/UI/Cards.swift`) — replaces the 7-day and 5-hour cards

One card (`CardBox`: 16 pt padding, 18 pt corners, `p.card`) per account, as the page's `itile(a, 329, 72)`:

| Part | As built |
|---|---|
| Ring | `GrammarRing`, 72 pt, left, centred against the bars; 14 pt gap to the bars |
| Lead bar row | "Weekly · All models" (15 pt, ink) when a model limit shows, else "Weekly"; figure 18 pt (0.25 × ring) bold, `Display.grammarFigure` (small "%"); bar 8 pt |
| Model row | the model name (13.5 pt, `p.txt`), figure 15 pt, bar 6 pt; 8 pt between rows |
| Captions | each bar's V1 `PaceCaption` at 12.5 pt; the dashed room under the mark (`UsageBar showsRoom`) |
| Figure colour | Used / Left: the severity colour of the account accent; Vs pace: `Display.figureColour` (green under, red over, ink on the mark), unsigned, no "vs pace" word; dimmed (error/stale notice): `p.sub` |
| Bar end | `GrammarBar` = `UsageBar` + the Usage end mark: Round = today's capsule; Line / Pace colour = square end, a 1.5 pt line 1.5 pt past each edge over a 1 pt gap in the card colour, coloured per `UsageEndMark.role`; the pace tick on top |
| Folded lines | line 1: the band line (`CardContent.compactLine` of Weekly) 14.5 pt semibold, red when alarmed; line 2: a 12 pt ring glyph (`GrammarRing`, no figure) then the 5h's folded line ("Resets in 2h 20m · on track for ~69%") 14.5 pt `p.sub` (red when alarmed); 10 pt above line 1, 2 pt between |
| Open | chips 7d · <model> · 5h (CCRM-106 (7d Chip): "7d · Opus · 5h"; one chip only → no row; no 5h → "7d · Opus"; model limits hidden → "7d · 5h"; VoiceOver "7-day, all models", `CardContent.spokenTitle`; the stored chip selection is unchanged; the lead bar label "Weekly · All models" is unchanged, wireframe Q4 (a); 14 pt, 4 × 14 pt padding, 9 pt corners, selected `p.fill`, others a 1 pt `p.line` outline; 12 pt above, 8 below), then the chosen chart (chart height setting) and its lines: Weekly / model = pace line (with "until <day>"), projection line, reset row (countdown left, clock right); 5h = "Limit reached" when at it, projection line, reset row ("as of …" on the right while dimmed) |
| Ring projection | the dotted arc to the projection only while the tile is open on the 5h chip |
| Open / fold | a tap on the tile (not on a figure) toggles; default Comfortable open, Compact folded, behind More folded |
| Default chip | 5h when the drawn card order leads with the 5h (Headline 5-hour or reordered), else 7d; the chips row is dropped when only one chip exists |

Layout sheet: `.session` and `.weekly` still order, hide and fold; the tile sits where the first visible one of the two
does (in More only when both are in More). The iPad halves show the tile at full width (credits stay in the header).

#### Tap readout (form b, §2b)

Tapping a figure swaps that figure's own line for 3 s with `PaceText.markReadout(MarkTime.of(...), use24h:
Display.use24h)`, semibold, ink, underlined: the lead or model figure → its bar's caption; the ring → the 5h line (folded),
or the projection line under the 5h chart (open; the ring tap also selects the 5h chip). Nothing happens when the readout
is nil (spent, not started, no reading) or the 5h is greyed by a spent week. VoiceOver: each bar row and the ring carry the
readout as their hint; the ring's label stays "5-hour window, N percent".

#### States

| State | Ring | Lines |
|---|---|---|
| Normal | 5h ring with figure, tick, red slice / dashed room, end mark | band line, 5h line |
| No 5h (weekly-only plan) | `.notApplicable`: grey track and cross, no figure; slot kept | band line only |
| 5h not started | ring at 0% | "Starts when a message is sent" |
| No reading | ring "—" (the tile with no 5h shows the cross) | as `compactLine` says |
| Week spent (CCRM-102 (Spent Week Gate)) | `.grey`, figure dim | the spent line, then the gate note ("Weekly limit reached — 5h can’t be used until Sun 9:30 pm") at full opacity, no glyph |
| Error / stale notice | ring and bars at 45% | unchanged |

Pool (CCRM-55 (Antigravity Account) E2): its own tile, "Weekly" bar and 5h ring, with the pool's name as the last folded
line (page T6b) instead of the old label above two cards.

### Summary rows (`SummaryRow`, `ios/App/UI/Summary.swift`)

A 48 pt `GrammarRing` (the 5h) left of each row, centred on its lines, 12 pt gap, in place of the "5h 34%" companion;
every row has the same frame: a plan with no 5h shows the grey cross, a spent week the grey ring, no reading "—". Nothing
sits beside the name but the plan chip: a held row says why in line 3 only, "Held · back at 3:40 pm · resets Fri"
(rev D S1, Fable D1), beside its loud ring. The Weekly figure is 17 pt `grammarFigure`,
Vs pace coloured. Bars take the end mark. Tapping the figure swaps line 3 for the readout for 3 s; tapping elsewhere
opens the account. Rows of the one-line form (six rows shown or more) are unchanged. The row glyph (Personal/Work) leads
the mark on both forms (§2.8).

### iPad sidebar (`IPadLandscape.sidebarRow`, `ios/App/UI/MainScreens.swift`)

Row (`sideRow`): ring 40 pt (the 5h; grey while the week is spent, grey cross for no 5h, so names align), then the mark
(16 pt) and name (16 pt) with the plan chip and pin as before, over a 4 pt Weekly bar (no room, end mark on); the Weekly
figure 16 pt bold right (min 40 pt). The figure is always the Weekly and the ring the 5h, so the Headline · Weekly · 5h
segmented switch is removed.

### Settings › Appearance (`SettingsScreen`, `ios/App/UI/SettingsHistory.swift`)

"Usage end mark" right after Usage display: subtitle "How a bar or ring ends at your usage" (13 pt `p.sub`), a segmented
Round · Line · Pace colour (Line default), writing `Display.setUsageEndMark`, then three previews 18 pt apart, each a
36 pt ring (64% against 52%, over pace) and a 54 × 8 pt bar, drawn Round, Line and Pace colour. iOS has no status-bar
icon: no Status-bar icon row anywhere.

### Layout sheet on iOS (Robin, 2026-11-02)

The tile is one row, **"Usage"** (`CardID.title`), in place of the "5-hour window" and "7-day window" rows. The row
stands for the window card the tile is drawn under (`LayoutSheet.usageID`: the first shown above More, else behind
it); showing, hiding, folding and unfolding it applies to both window ids, and reordering carries the other id right
behind it, so the stored layout can never contradict the row. "Usage credits" is unchanged.

### Step 7 pass fixes (2026-11-02, CCBG-56 … CCBG-65)

- **Tile bar rows** (CCBG-58 (Tile Title Clip), CCBG-59 (Model Figure Alignment)): the label takes all the width the
  figure leaves, so every row's figure is flush right on one edge; a lead label that still does not fit drops
  " · All models" ("Weekly") rather than ellipsizing. iOS the same (`ViewThatFits`).
- **The readout's underline** (CCBG-57): the font's own underline on Android (`TextDecoration.Underline`), as iOS's
  `.underline()` — never across the glyphs.
- **Account sheet** (CCBG-65 (Sheet Under Taskbar)): the ⋮ › Headline… sheet opens fully (never half-expanded),
  scrolls, and pads for the navigation bar and a large screen's taskbar (only what the sheet's own insets have not
  consumed, so a phone is not padded twice). Fold 7 recheck: an inset dump and captures on the inner screen and a
  phone-width capture.
- **The Summary figure's target** carries no TalkBack node of its own: the figure offers the readout as its click
  action, and only when there is one; the target shows no ripple.
- **Shortened labels** (the tile's " · All models", line 3's "· resets <day>") are measured again whenever the width
  changes, so a fold or unfold brings the full text back where it fits.
- **iOS Summary rows**: the figure's target is at least 48 pt wide and 12 pt past it above and below; the name gives
  way before the pill; line 3 drops "· resets <day>" when it does not fit; the six-account word for a held row is
  "Held · 3:40 pm" (never "5h").
- **iPad sidebar** (CCBG-68 (iPad Plan Chip Wrap)): the plan chip stays on one line and gives way to the name.
  CCBG-67 (iPad Sidebar Tap)'s highlight-and-scroll is superseded by the layout below.

### iPad: the Summary and one account at a time (Robin, 2026-11-02)

"Drop the 'pinned' account as it's confusing here … make the summary/all as the default one … only on tap of the
individual account, we'll show the details as we have the whole space … should be same for the phones" — Robin
picked "Only that account" over "All stacked, scroll to it".

- **Landscape** (≥ 1000 pt): the 300 pt sidebar is "All accounts" (the Summary; the default detail when "Open on the
  summary" applies, whenever no account is chosen, and when the account on screen is removed) and one row per account; the highlighted row is the detail's.
  A tap on an account shows only that account's page at the detail's full width (`IPadLandscape.shownKey`); "All
  accounts" returns to the Summary. Each new page starts at its top. With one account there is no Summary row and
  that account shows.
- **Portrait / Split View ≥ 700 pt:** the phone's model — the title, tabs for All and every account, one page at a
  time; no pinned section.
- **Gone everywhere:** the pinned half, "Pinned" in the header, the pin glyph in the sidebar and in Settings ›
  Accounts, "Touch and hold an account to pin it to the top.", and "Pin to the top" (sidebar and Settings menus).
  The stored `pinned` flag is still decoded and never read (stored keys are never reused).
- **Phones:** unchanged; neither the iPhone nor Android ever pinned an account in the main screen (on the iPhone the
  only trace was the Settings › Accounts pin glyph and its "Pin to the top" item, now removed with the concept).

### Summary order (Robin, 2026-11-02; Android and iOS)

Appearance › **Summary order**, right after "Open on the summary" and shown with it (two or more accounts):
Android a wrapping row of four FilterChips — "Your order" (default) · "Most room first" · "Resets soonest" · "Most
used" — under the bodyLarge SemiBold title, no subtitle; iOS a Picker row "Summary order" with the same four labels.
The All tab's list header names the choice in lower case on its sort menu at the right (§2.8; the same setting): "your order ▾", "most room first",
"resets soonest", "most used". Rows keep their group's words and styling (held, spent, no reading) in every order;
the rank number on a one-line row and the Pace map's labels follow the list's order. The Pace map's dots are
unaffected; widgets that list accounts (Android Strip, iOS Overview) keep their own order (`SummaryRank.ordered`,
BEHAVIOR-MOBILE §6).

### Native tabs on iPhone and iPad (Robin, 2026-11-02, "Native everywhere"; `ios/App/UI/MainScreens.swift`)

Supersedes the custom tab strip and the custom iPad sidebar described above (§1 iPad rows, the iPad sidebar
sections, and the "Summary and one account at a time" layout, whose selection rule it keeps).


- **One `TabView`** (`MainTabs`) with `.tabViewStyle(.sidebarAdaptable)` and the iOS 18 `Tab` API (deployment target
  iOS 18.0). Built with Xcode 27.2, so the chrome is Liquid Glass with no styling of our own: no tint, background or
  glass is set on the tab bar or sidebar.
  - **iPhone:** the Liquid Glass tab bar at the bottom. Past five tabs the system adds **More**.
  - **iPad portrait (and Split View):** the floating Liquid Glass tab bar at the top.
  - **iPad landscape:** the native sidebar, which the user can collapse into the tab bar. It replaces the custom
    300 pt sidebar.
- **Tabs, in order:** **All** (SF Symbol `text.alignleft`, only with two or more accounts, `SummaryRank.tabShown`),
  then one tab per account in `model.accounts` order (Settings › Account order). Each account tab shows the provider
  mark and the account label.
  - **Tab icon:** the provider mark's asset (`ClaudeMark` / `ChatGPTMark` / `GeminiMark`, 24 × 24 template SVGs) via
    `Tab(_:image:value:)`. The system draws it in its own tint (selected or unselected), **not the account accent**.
    Claude's 82 % optical scale from `ProviderMark` doesn't apply there.
  - **Sidebar rows** show the mark and name only. The per-row 5h ring, Weekly bar, Weekly figure and plan chip are
    gone (Robin accepted this). The "All accounts" row is now titled **All**, with no account count.
- **Selection:** `AppModel.selectedKey` (`AppModel.summaryKey` for All). The rule stays the pure helper
  `MainTabs.shownKey` (was `IPadLandscape.shownKey`, tests in `CardLayoutTests.theTabsShowTheSummaryOrOneAccount`):
  the chosen tab, else All where it shows, else the first account. The following all set the selection as before:
  deep links (`DeepLink.key`), the `-tab` launch argument, reset-alert taps, `launchKey` ("Open on the summary") and
  `validSelection`.
- **Each tab has its own `NavigationStack`** with today's toolbar (History, Settings, the ⋯ menu: Refresh, Layout,
  Share card, Add account). History is pushed on the tab it was opened from. `-screen history` opens it on whichever
  tab is selected.
- **Page content (`TabPage`):**
  - **Regular width ≥ 700 pt (iPad):** an account is the full-width page, `AccountHeader` over
    `AccountCards(twoAcross: true, cardWidth: max(396, width − 40))`, 20 pt side margins. The Summary is
    `SummaryView(wide:)` from 900 pt.
  - **Narrower (iPhone, narrow Split View):** the phone page, `AccountCards` with 16 pt margins.
  - Every page has `.refreshable` and the `Footer` at the end.
  - **App title** (`AppTitle`, 34 pt) tops every page, except in iPad landscape (regular, wider than tall,
    ≥ 1000 pt). There the sidebar header carries it (`tabViewSidebarHeader`, 30 pt) and the Summary page is headed
    "All accounts" (30 pt bold), as before.
- **Unchanged:** the empty state (`EmptyStateView`, in its own stack, no tabs), the sign-in and other sheets, and the
  one-account case (no All tab, so one tab).
- **Removed:** `PhoneMain`, `IPadTabs`, `IPadLandscape` (the custom sidebar, its rows and the All row).
  `AccountTabs` **stays**: History's account picker (`SettingsHistory.swift`) still uses it.

#### Visible consequences, listed for Robin (2026-11-02)

1. **iPhone loses the swipe between accounts** (the old page-style `TabView`). Native tabs switch only by tapping.
2. **Tab icons follow the system tint**, not the provider accent (the custom strip coloured the selected underline in
   the account accent).
3. **The All tab's title is "All"** in both the tab bar and the sidebar. The old sidebar row said "All accounts" with
   the account count, and the old strip's VoiceOver label said "All accounts".
4. **Collapsing the sidebar in iPad landscape** leaves no app title on screen: the landscape rule is by window shape,
   not by whether the sidebar is open.
5. **iPhone past five tabs (four or more accounts plus All):** the extra accounts sit under the system's More list.
6. **iPhone page top:** the title is now the first row of the scrolling page (top padding 4 pt), under the navigation
   bar. It no longer sits above a fixed tab strip.

## The pace line on iOS — CCRM-104 (Pace Line Parity)

Wireframe `design/2026-11-06-mobile-pace-line-wireframe.html` §9 (approved 2026-11-06 under Robin's delegation); strings are core
§6.3 "Printed form" (`PaceText.signed`, `PaceText.paceLine`, `PaceLine`; cases `spec/core/fixtures/pace-distance/printed.json`,
read by `PrintedFormTests`). This section supersedes the readout, unsigned-figure and band-line wording above wherever they differ.

- **Figure.** Every Vs pace figure but a ring is signed: "+19%" green, "−12%" (U+2212) red, "0%" ink (`Display.number`).
  Rings stay unsigned, tone only (`Display.ringNumber`: tile ring, Summary ring, 5h rings on faces, the Island's compact ring,
  the end-mark previews). The notification-style "vs pace" words never appear. The Vs pace share card still renders as Used.
- **The pace line replaces every Weekly band line** (`WindowContent.line`, drawn by `PaceLineView`/`FitLine`,
  `ios/Shared/PaceLineView.swift`), naming the reset since v2.0 (§1.2's pace-line row, CCRM-108 (Reset Named)): "21h 50m ahead · resets Sun 4:59 PM", "1d 18h behind · resets Sun 10:17 AM", "On the mark · resets Sat 8:00 AM";
  the duration and word tinted (semibold), the rest in the surface's own ink. Where: tile folded line 1 (14.5 pt semibold) and the
  open tile's lines, the classic Weekly/5h cards, Summary line 3 (13 pt), Strip (12.5), Overview T1–T4, Bars small (11.5 pt, 126 pt
  wide), the Live Activity (single: line 1 is the pace line, line 2 the 5h line led by a ring glyph "resets in 2h 20m · on track
  for ~69%"; Duet: the pace line alone), the Island expanded (13 pt; "· in 4d 6h" is gone). A spent, held, signed-out, unclocked
  or no-reading window keeps its own line; the Lock Screen widgets and the Number face are unchanged.
- **Drop order, one everywhere** (§1.2's pace-line row since v2.0): "resets", then the clock, then the weekday, then the floor steps to 0.8 of its size; the duration and word are never cut.
  `FitLine` is a `ViewThatFits` over the four variants, so the choice is made on the real text width per string (Dynamic Type
  included). `PaceLineFitTests` pins it. CCBG-60 (Summary Line Clip)'s " · resets <day>" drop is this rule now.
- **Captions.** Vs pace "52% used · 39% pace mark" (was "· mark 39%"). A press on a bar figure underlines its caption for 3 s;
  on the ring it puts the 5h's caption in the 5h line for 3 s; in a one-line Summary row it swaps the word slot (the pace line's
  tinted span) for the caption. VoiceOver offers the caption as the figure's action. The markTime readout ("Pace catches up …",
  "1d 4h behind pace") is gone from the app (`PaceText.markReadout` stays in core for the shared fixture).
- **Pace map.** Wedge words "ahead of pace" (above the diagonal) and "behind pace" (below); names on every dot at any count, no
  rank numbers; "even pace" slides along the diagonal to a free stretch. Placement, leaders and the "each account's own week"
  note (now the foot line): §2.8.
- **Share card, Weekly row**: left slot the tinted span ("13h 59m ahead"), right slot "Resets …".
