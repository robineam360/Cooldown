## Cooldown v1.6 — two accounts on one notification, Settings on a diet, the Pulse icon

Install the APK below over your existing install — sign-ins, settings and history survive.
**Any home-screen widgets and the Quick Settings tile disappear on update: they were removed
in this version.** The always-on notification is their replacement.

### What shipped

- **CCRM-62 (Duet Notification) — two accounts on one always-on notification.** Pick a
  **First** and a **Second** account under Settings → Alerts → Show accounts. Collapsed, each
  half shows its provider's mark, the account name, a bar with the even-pace tick and a big
  percentage in that half's warning colour. Expanded, both headers with their reset lines, the
  **Weekly** rows, per-model caps, condition strips and one-tap Refresh. Each half is its own
  tap target. The status-bar **ring** loses its weekly dot and wears the shown account's own
  colour — **Status-bar ring shows** First / Second / Whichever is higher — and at 100% goes
  solid red with an ×. Wording everywhere is "5h" and "Weekly".
- **CCRM-61 (Settings Diet) — four swipeable tabs.** Accounts · Alerts · Appearance · More: 24
  rows instead of 43. One "Show red past the pace mark" switch covers the app and the
  notification; Usage credits appears only for accounts that report a credit budget; Check for
  updates lives under More → Updates. **Reset pings are per account** (5h / Weekly, each Off /
  If busy / Always) and are the one notification that still posts on its own. **Removed:** all
  five home-screen widgets, the Quick Settings tile, the standalone threshold / pace / sign-in /
  stale / update notifications and their six channels, the Gauge / Number tile / Progress bar
  notification styles, the Pie / Battery / Number status-bar glyphs. Sign-in, stale and
  update-available notices still appear as dots and strips inside the notification.
- **CCRM-60 (Dual Identity) — the Pulse icon and two rooms.** A new launcher tile: an ECG
  beat on charcoal — Claude terracotta in, ChatGPT green out, the red spike crossing a dashed
  even-pace ceiling; no trademarks on the tile. The main screen's top bar leads with both
  provider marks. Each tab is a room: Claude tabs get a warm ivory surface and serif headline
  figures, ChatGPT tabs a cool neutral surface; the cards are identical.

### Fixed since the device pass

- **CCBG-25 (Idle Reset Silence)** — a reset ping now fires for an account left idle across
  the reset, not only for one that kept chatting. (Verified by unit tests; a live idle reset on
  a device is still to be observed.)
- **CCBG-23 (Mark Size Mismatch)** — the ChatGPT mark now renders the same size as the Claude
  mark on every surface (top bar, tabs, Settings chips, notification halves).

### Known issues (all Low)

- **CCBG-21 (Zero-Point Shading)** — a 5-hour chart at exactly 0% paints its whole above-pace
  region red until the first reading lands.
- **CCBG-22 (Credits Rows For All)** — the Usage credits section lists a switch for every
  account instead of only those with a credit budget.
- **CCBG-24 (Duet Label Clamp)** — on the collapsed notification a seven-character account
  name can ellipsize next to a three-character figure.

Docs: the [User Guide (PDF)](https://github.com/robineam360/Cooldown/blob/main/release/docs/Cooldown-User-Guide-v1.6.pdf)
and the [Brochure (PDF)](https://github.com/robineam360/Cooldown/blob/main/release/docs/Cooldown-Brochure.pdf).
Unofficial personal tool — not affiliated with Anthropic, OpenAI or Google.
