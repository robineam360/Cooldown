# Spend meter — design ideation, and why it stopped

Ideation only. No wireframe was drawn and no code was written: CCRM-69 (Window Dollars) is blocked
on a measurement, and this file records what was designed on the way there so none of it has to be
re-thought if the measurement goes the other way.

Dated 2026-09-16. Ideation by Fable; decisions by Robin.

---

## How this got here

Robin asked for "a spend meter (on how much I would have spent on API instead of the plan usage)",
having seen dollar figures in Claude Code.

The data audit killed the obvious implementation. Neither provider's payload carries **any token
count** — not input, output, cache-read or cache-write, on any endpoint the app calls. Both return
percent-of-window, reset timestamps, severity, and per-model *window* slots. `CLAUDE.md` forbids
reading a local file or process to get usage, so the desktop trick of parsing Claude Code's JSONL
logs is permanently out. API pricing is per-token. So a true API cost is not computable.

ROADMAP.md's appendix had already ruled on exactly this, listing **"Estimated spend in dollars for
a subscription account"** under things that are *structurally unavailable, not merely unbuilt*.

Robin's first instinct was to reverse that and ship an estimate in both hours and dollars with a
toggle. The design below is what that would have been. He then hit the problem himself — OpenAI
publishes limits in *messages*, not hours, so the two providers could not share a unit — and said:
*"If we cannot get a clear dollar amount, maybe we shouldn't bother to even build this. I was
excited as I saw dollar amounts on Claude Code window but then why we can't use that here?"*

That question had a better answer than either of us had reached, and it stopped the design.

## The answer, and the lead

**Claude Code can price your usage because it made the requests.** It holds the token counts and
prices them from a local table — measurement at the source. The phone only ever asks "how full is
the window?" and is told a percentage. No design closes that gap.

**But Anthropic's payload already has the field.** From the captured response in
`UsageParserTest.realPayload`:

```json
"five_hour":{"utilization":0.0,"resets_at":"…",
  "limit_dollars":null,"used_dollars":null,"remaining_dollars":null},
"seven_day":{"utilization":30.0,"resets_at":"…",
  "limit_dollars":null,"used_dollars":null,"remaining_dollars":null},
```

`used_dollars` and `limit_dollars`, per window, from Anthropic. **Nothing in this app reads them**
(`grep` finds no occurrence in `app/src/main/`), and they were null when that payload was captured
— **2026-07-27**. Anthropic built those fields for a reason.

So: probe before designing. CCRM-69 (Window Dollars) carries the method and the two branches.

---

## The fallback design, if `used_dollars` is still null

Kept because it is the honest ceiling on what a phone can show without the real figure.

### Plan Value — arithmetic on facts, no estimate

`SessionLog` already stores **a year of closed weekly windows** with peak percent and a hit-limit
flag (`MAX_AGE_MS = 366 days`) — a much longer memory than `HistoryStore`'s 8 days. The 7-day
window *is* the plan cap, so "how much of the plan do I actually use" is the mean of those peaks.
Real from day one, no new storage, no estimate.

- **Utilisation** = mean `peakPct` over closed weekly records (default: last 4 closed weeks), plus
  the live open week as "so far".
- **Money line** = `plan price × utilisation` worked / unused. A *definition*, not an estimate;
  the linear-in-weekly-cap assumption is stated in the ⓘ.
- **Caps hit** = count of `hitLimit` records.

```
┌──────────────────────────────────────────────┐
│ Plan · Max 5x                     62% used   │  facts
│ ▁▃▆█  last 4 weeks · 1 hit the cap           │  facts
│ $62 of $100 worked this month                │  definition
└──────────────────────────────────────────────┘
```

Placement: one compact card (~88dp) below the credits card, hideable per profile per CCRM-25 (Card
Layout); plus a summary strip above `WeeklyView` on History.

### The priced estimate, had it shipped

Recorded so the reasoning is not lost, **not endorsed**.

- The chain is percent → Anthropic's published hours-of-Sonnet range → dollars via one per-hour
  rate. Anthropic publishes **hours, not tokens**, so the dollar step needs a tokens-per-hour
  constant nobody publishes. Roughly ±2x.
- Unit choice would be a **global** `Hours / Dollars / Off` preference in Appearance, mirrored as a
  one-tap flip inside the ⓘ note. Not a segmented control on the card (repeats per account, ~40dp
  each, makes a unit look like per-account state) and **not tap-to-cycle** — that gesture already
  means "show provenance" since CCRM-30 (Estimate Honesty), and that is the last interaction to
  sacrifice on an estimated line.
- The estimate sits **below a hairline**, in `onSurfaceVariant`, bodySmall, never bold, always with
  `≈` and a range. Facts above it keep their weight. Two money lines in the same weight would imply
  a shared basis.
- Seeded, visible, dated, editable per-hour rate (Robin's choice, 2026-09-16) — never a hidden
  constant.
- Never a decimal in either unit; decimals make a guess look measured. Never on the notification,
  the ring, the pings, or the share card — CCRM-30 already ruled those too tight to carry a
  provenance marker, and a number that cannot carry its marker does not ship there.
- **No multiplier KPI.** "3.4x value" is noise wearing a KPI's clothes. A three-state break-even
  sentence in the ⓘ note does the same job honestly: *clears the $20 plan price even at the low
  end* / *around the $20 plan price* / *below the $20 plan price this month* — no adjective on the
  third, because any gloss is an accusation.
- Fable's own correction, round 2: it first argued the dollar figure would read as an accusation in
  a quiet week. On the arithmetic that only happens below ~10% utilisation — subscriptions are
  priced well under API rates, so the estimate is usually flattering. That does not rescue the
  multiplier, but it does make a bounded comparison defensible.

### States that any version must answer

First run with no closed week · Free plan (no windows — card absent, the CCBG-27 (Free Plan 403)
notice already explains why) · plan with no known price (Team, Enterprise) · 0% every week · 100%
in a week · the open week mid-flight · credits spent vs never · ChatGPT's weekly-only shape ·
Claude's per-model sub-caps (pool only — Opus/Sonnet slices are sub-caps of the same plan, not
extra value) · the cover screen, where the sparkline drops and the money line ellipsises before the
percent.

---

## Account reordering — settled, unblocked

Separate feature, no dependency on any of the above. Cleared as safe: everything that could break
keys off stable identity rather than position — the pinned First/Second halves, the status-bar ring
choice, notification IDs and alarm request codes (`Profile.slot`, never reused), per-account
accents. Only the tab strip and the Accounts card read registry order directly.

**Recommendation: `Move up` / `Move down` in each card's existing ⋮ menu**, instant, no confirm,
greyed at the ends. Not chevrons on the row — CCRM-65 (Accounts Redesign) fought for every dp and
the header is full. Not drag handles — the cards are 161dp tall and the inner screen alternates
them left/right by index, so a drag would cross columns non-monotonically. Drag is right for 56dp
rows in one column; if a fifth account ever makes 4-step moves grate, the upgrade is a
`⋮ → Reorder…` bottom sheet, not a rework of the card.

Mechanics: `ProfileRegistry.move(key, delta)` as a pure `Companion` function over `State`, bump
`rev`, fan out exactly as `rename` does. **The selected tab must key on `profile.key`, not index**,
or a move silently changes which account you are looking at.

Two side-effects, both judged too minor to surface in the UI but recorded here: the notification
panel's overflow fault-strips follow registry order (`notify/Conditions.kt:80-94`), and so does app
shortcut order.

---

## Open questions, if the fallback is ever built

In consequence order. Questions 1 (what prices the dollar — **answered: seeded + editable**) and 2
(ChatGPT — **answered: Claude only for now**) are settled; the rest were never reached.

3. Toggle home — global Appearance preference vs a control on each card face.
4. Default unit — hours or dollars.
5. Break-even sentence — inside the ⓘ note, or on the card face in dollar view.
6. Plan-value money line — `$12 of $20 worked`, or utilisation only with no price anywhere.
7. Price source — hardcoded dated USD table, or table-seeded and editable per account.
8. Placement — main card plus History strip, or History strip only.
9. Reorder interaction — ⋮ menu, or a reorder bottom sheet.
10. Period — last four closed weeks, or calendar month (which jumps at the boundary, since weeks
    straddle it).
