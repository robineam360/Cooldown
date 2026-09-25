# Cooldown — tester-fix wireframes: CCBG-32 (Accounts Button Wrap), CCBG-33 (Device-Code Prerequisite), CCBG-34 (Account Display Name)

Android app (Jetpack Compose, Material 3). Settings → Accounts tab lists one card per account.
Card content width = screen width − 72 dp (page 16 dp ×2 + card 20 dp ×2): 288 dp on a 360 dp phone,
339 dp on a 411 dp phone.

## CCBG-32 · Accounts Button Wrap

**Today (approved in design/accounts-redesign-wireframe.html §h "Finishing sign-in — unchanged from today"):**
a Claude card in "Finish signing in" state ends with ONE `Row`:
`[ Finish sign-in ]  8dp  Reopen page  <weight(1) spacer>  Cancel`
(filled Button, TextButton, TextButton). Measured on an API 36 emulator: at 411 dp it fits with ~30 dp
spare; at 360 dp (wm density 480) the row needs ~323 dp of 288 dp, so the last child, "Cancel", is
squeezed to a 15 dp column and its letters stack one per line — exactly the tester's screenshot.
A larger font scale breaks it earlier.

**Proposed fix — same row, allowed to wrap (FlowRow), Cancel always at the right end of its line:**

Wide (everything fits — unchanged pixel-for-pixel from today):
```
┌ Personal ─────────────────────────────────── ⋮ ┐
│ Finish signing in                              │
│ Sign in on the page that opened, then copy …   │
│ ┌ Paste the sign-in code ─────────── Paste ┐   │
│ └──────────────────────────────────────────┘   │
│ (Finish sign-in)  Reopen page          Cancel  │
└────────────────────────────────────────────────┘
```
Narrow (360 dp, or font scale up to ~1.3 at 411 dp): Cancel wraps to a second line, right-aligned,
8 dp below:
```
│ (Finish sign-in)  Reopen page                  │
│                                        Cancel  │
```
Very narrow (huge font): Reopen page wraps too; order is kept, Cancel still right-aligned:
```
│ (Finish sign-in)                               │
│ Reopen page                                    │
│                                        Cancel  │
```
Rules: no button ever shrinks below its one-line label (labels are `maxLines = 1, softWrap = false`);
states unchanged — busy: all three disabled plus the existing spinner; empty code field: Finish
disabled. Precedent: the ChatGPT device-code sheet already uses a FlowRow for its buttons with Cancel
on its own line below.

Rejected alternative: keep one Row and shave TextButton padding (12 → 4 dp) — fits 360 dp at 1.0 font
by ~13 dp and breaks again at font 1.15; fragile.

## CCBG-33 · Device-Code Prerequisite

Fact (tester screenshot + OpenAI docs, 2026-09): ChatGPT's device-code sign-in only works after the
user turns on **"Enable device code sign-in for Codex, Excel, PowerPoint, and Word"** under
**ChatGPT → Settings → Security** (chatgpt.com/security-settings). Off by default (anti-phishing).
Workspace (Team/Enterprise) members can't turn it on themselves — their admin must allow it. When it
is off, the OpenAI page refuses the code; our poll just keeps waiting until the code expires (15 min),
so the app never says why.

Proposed, three places (copy final-draft; bodySmall, onSurfaceVariant unless stated):

1. **Sign-in sheet, WAITING state** — a tonal info box (surfaceVariant background, 12 dp radius,
   12 dp padding, ⓘ 16 dp icon) between the body sentence and "Go to auth.openai.com/codex/device":
```
 Sign in to ChatGPT
 Open the page in any browser — it can be a different device — sign in to
 ChatGPT, and enter this code.
 ┌──────────────────────────────────────────────────────────┐
 │ ⓘ First time? Turn on "device code sign-in" in ChatGPT → │
 │   Settings → Security first, or the code won't be        │
 │   accepted. Work accounts need their admin to allow it.  │
 │   Open ChatGPT security settings ›          (text link)  │
 └──────────────────────────────────────────────────────────┘
 Go to
 auth.openai.com/codex/device
 and enter this code
 ABCD-EFGH
 Code expires in 14:52                 Waiting for you to finish…
 [Open in browser]  (Copy code)
 Cancel
```
   The link opens chatgpt.com/security-settings through the same browser picker as "Open in browser".
2. **EXPIRED and DENIED bodies** gain one sentence: *"If ChatGPT refused the code, turn on device code
   sign-in in ChatGPT → Settings → Security, then get a new code."* (same info box with the link, no
   new layout.)
3. **ChatGPT card before first sign-in** — the existing subtitle under "Sign in with a code" gains a
   second sentence: *"Shows a short code to type at auth.openai.com — on this phone or any other
   device. Needs device code sign-in turned on in ChatGPT's security settings."*
4. **User guide** (release/USER-GUIDE.md "ChatGPT: sign in with a code" + docs/src guide): one
   paragraph naming the setting, its path, and the workspace-admin case.

Narrow width: the info box text wraps; nothing drops. Large font: the sheet already scrolls.

## CCBG-34 · Account Display Name

Tester: *"Rather than Account, if we get Meta data, we can show the Display Name."* The developer asked
whether that means the email; **no answer yet**. Today a new account is named positionally
("Account 3") until the user renames it. Data the app already receives: Claude `/api/oauth/profile`
carries `account.full_name`, `account.display_name` and `account.email`; ChatGPT's id_token carries
`email` (and possibly `name`). Note: account labels appear on the lock-screen notification, widgets and
the share card, and the diagnostics log rule is "no emails".

Proposal: **park** CCBG-34 until the tester answers (the entry itself says so), recording the data
available and the privacy question (a name/email on the lock screen) for when it is picked up.

---

## Decisions (2026-09-25): Fable judged, Astra reviewed, both agreed, so it did not go to Robin

**CCBG-32: approved with changes.** The narrow state is a new arrangement; the wide state is §h
exactly as it was. The narrow state shows at 360 dp, on the Fold 7 cover (~301 dp of content) and
in the inner screen's two-column Accounts (~310 dp). It is built as a custom `Layout`, not a
FlowRow, because a FlowRow can't right-align a child that has wrapped. The busy state keeps
today's behaviour: all three buttons disabled. *Astra:* a label wider than a whole line must wrap
inside its button rather than clip, so labels are not pinned to one line.

**CCBG-33: approved with changes.** The copy, word for word:
- WAITING box: *"First time? Turn on device code sign-in in ChatGPT → Settings → Security, or
  the code won't be accepted. Work accounts need their admin to allow it."*
- EXPIRED / DENIED box: the same box, below the body and above "Get a new code": *"If ChatGPT
  wouldn't accept the code, turn on device code sign-in in ChatGPT → Settings → Security, then
  get a new code. Work accounts need their admin to allow it."* (The admin sentence is Astra's
  addition.)
- The link is a TextButton, *"Open ChatGPT security settings"*, that opens
  chatgpt.com/security-settings through the browser picker.
- STARTING, UNAVAILABLE and FAILED show no box. The copy lives in `DeviceCodeCopy.hint(stage)`.
- The subtitle on the ChatGPT card and on the Add-account picker gains: *"Needs device code
  sign-in turned on in ChatGPT → Settings → Security."*

**CCBG-34: parked** until Raja answers.
