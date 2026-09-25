# Cooldown v1.7

The main screen is redesigned with compact cards and a customizable layout, and the charts are polished and sizable. Install over v1.6 — settings, history and accounts all survive.

## What's new

**Main screen**
- Customize the card layout: reorder, hide, or move cards behind "More" [CCRM-25 (Card Layout)]
- Comfortable / Compact density (Settings → Appearance; Compact is opt-in): Compact folds each card to a title, bar and one line — tap it to open the chart in place [CCRM-72 (Main Screen Redesign)]
- One status line — "Checked 4m ago ↻" — replaces the Refresh button; tap it to refresh [CCRM-72 (Main Screen Redesign)]
- Claude accounts sit on black in the dark theme, like ChatGPT [CCRM-76 (Black Room)]
- Reset layout to defaults when customization goes sideways [CCRM-35 (Layout Reset)]

**Charts**
- Chart height is now selectable: Small / Medium (default) / Large [CCRM-75 (Chart Height)]
- The 7-day card charts either the whole pool or a model cap — "All" or "Fable" chips above the chart [CCRM-73 (Model Cap Chart)]
- Chart labels moved inside the plot, so the chart's now-line sits exactly under the bar's pace mark; headlines drop "used" and pace phrases read in % [CCRM-74 (Chart Polish)]
- Transposed chart as a toggle: time across (default) or time down the y-axis [CCRM-77 (Transposed Chart)]

**Accounts and History**
- Account cards are redesigned: shorter, cleaner, three action cells [CCRM-65 (Accounts Redesign)]
- Drag accounts into your preferred order on the Reorder sheet [CCRM-71 (Account Order)]
- "Plan Fit" block on History: is this plan the right size, read off eight weeks [CCRM-70 (Plan Fit)]

**Notification and reliability**
- The always-on notification is a foreground service now, so it no longer sinks to the bottom of the shade or stops refreshing [CCRM-67 (Pin Service)]
- Diagnostics is hidden until you tap the version in About seven times [CCRM-34 (Diagnostics Log)]
- Honest User-Agent sent to Anthropic's API [CCRM-68 (Honest Agent)]

## Fixed

- CCBG-28 (Pin Sinks) — always-on notification now guaranteed persistent and always visible in shade
- CCBG-29 (Refresh Swallowed) — tapping refresh while offline is no longer lost; it syncs when connectivity returns
- CCBG-30 (Phantom Window) — ChatGPT's 5h window no longer counts down if unused

## Known

- CCBG-24 (Duet Label Clamp) — with Usage display set to Left, a seven-character account label on the notification's collapsed row can lose two characters beside a three-character figure

## Install

- Download the APK from [releases/latest](https://github.com/robineam360/Cooldown/releases/latest)
- Read the [Cooldown User Guide v1.7](https://github.com/robineam360/Cooldown/blob/main/release/docs/Cooldown-User-Guide-v1.7.pdf)
