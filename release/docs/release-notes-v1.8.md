# Cooldown v1.8

Home-screen widgets are back as a new suite that fills any size you give it, and each account now shows the email it signed in with. Install over v1.7 — settings, history and accounts all survive.

## What's new

**Widgets, reborn** [CCRM-78 (Widgets Reborn)]
- Four widgets in the picker under **Cooldown**: **Ring** (one account as a gauge), **Number** (the big percentage, like the pinned notification), **Countdown** (when the window comes back, ticking live) and **All accounts** (every account as its own ring, side by side — never a sum) [CCRM-79 (Ring Face), CCRM-80 (Number Face), CCRM-81 (Countdown Face), CCRM-82 (Accounts Strip)]
- Adding one opens its settings: pick the account, the window (5h or Weekly) and the background (Solid, Gradient or Transparent). Change them later from the widget's long-press **Settings**
- Every widget fills whatever size you place it at, from 1×1 to a full row. Bigger Rings add the reset time and a second, smaller ring for the other window; the Number 2×2 and 4×2 carry 5h|Weekly chips and an account switcher; the Countdown's count grows with the widget [CCBG-44 (Widget Fill)]
- The same ring the status bar draws, now painted by one renderer everywhere [CCRM-83 (Ring Renderer)]
- Widgets from v1.5 do not come back on their own — add the new ones from the picker
- If you use widgets without the always-on notification, add Cooldown to One UI's **Never sleeping apps** (Settings → Battery → Background usage limits) so the widgets keep refreshing

**Accounts**
- Each account shows the email it signed in with, under its name in Settings → Accounts and first in ⋮ → Details; Rename suggests a name from it. Only inside the app — never on the notification, widgets or share card [CCBG-34 (Account Display Name)]
- **Clear usage history…** in an account's ⋮ starts its trend over without removing the account [CCRM-14 (Clear History)]

**Share**
- **Share snapshot** in the main screen's ⋮: a square image of the ring, the bars with pace and resets, and the 5h trend [CCRM-24 (Share Card)]

## Fixed

- CCBG-24 (Duet Label Clamp) and CCBG-37 (Duet Dot Squeeze) — the two-account notification keeps both names whole, with their dots, in Used and Left
- CCBG-31 (Alert Crash) — turning on the always-on notification with one account no longer crashes the app
- CCBG-32 (Accounts Button Wrap) — the sign-in card's buttons wrap cleanly on narrow phones instead of squeezing "Cancel"
- CCBG-33 (Device-Code Prerequisite) — ChatGPT sign-in now says which ChatGPT security setting it needs, with a link to it
- CCBG-42 (Kebab Drift) — each account card's ⋮ sits in line with its refresh button

## Install

- Download the APK from [releases/latest](https://github.com/robineam360/Cooldown/releases/latest)
- Read the [Cooldown User Guide v1.8](https://github.com/robineam360/Cooldown/blob/main/release/docs/Cooldown-User-Guide-v1.8.pdf)
