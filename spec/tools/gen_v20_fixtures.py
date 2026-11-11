#!/usr/bin/env python3
"""Derive the v2.0 feedback fixtures from the written rules (RUNBOOK Step 1).

Rules: spec/core/BEHAVIOR-SPEC.md section 6.3 "Planned - v2.0 feedback" (CCRM-107 (Day Grid), CCRM-108 (Reset
Named), CCRM-109 (Quiet Accounts), CCRM-111 (Map Layers)) and the approved wireframe
design/2026-11-10-v20-feedback-wireframes.html rev B, sections 3, 4, 5, 6 and 7.

Writes:
  spec/core/fixtures/pace-distance/printed-v20.json   (core: every family)
  spec/mobile/fixtures/map-foot/cases.json            (mobile: the Pace map's foot-line count)
  spec/mobile/fixtures/tags/cases.json                (mobile: the tag filter, tags optional: rev B, as built on iOS in I3)
  spec/mobile/fixtures/tags/cases-personal-work.json  (mobile: Robin 2026-11-11, every account Personal or Work; A4 and I4 read it;
                                                       Step 2 folds it into cases.json)

Run from the repo root:  python3 spec/tools/gen_v20_fixtures.py          (writes)
                         python3 spec/tools/gen_v20_fixtures.py --check  (fails if a file would change)

Standard library only. Nothing here is read by a test until a lane points its tests at the files; Step 2 folds
printed-v20.json into printed.json.
"""
import datetime as dt
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MINUS = "−"
DOT = " · "
DAY_MS = 86_400_000
HOUR_MS = 3_600_000
WEEK_MS = 7 * DAY_MS
FIVE_H_MS = 5 * HOUR_MS
EPS = 1e-9
WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]


# ---------------------------------------------------------------- shared numbers (section 6.3, unchanged)

def half_up(x):
    return math.floor(x + 0.5 + EPS)


def toward_zero(d):
    return int(math.copysign(math.floor(abs(d) + EPS), d)) if d != 0 else 0


def elapsed_pct(now_ms, reset_ms, length_ms):
    return max(0.0, min(100.0, (length_ms - (reset_ms - now_ms)) / length_ms * 100))


def on_mark(elapsed, used):
    return abs(elapsed - used) + EPS < 1


def mark_time(used, now_ms, reset_ms, length_ms):
    """Section 6.3 markTime: ('over', inMs) / ('under', agoMs) / ('on', None) / None."""
    if used is None or reset_ms is None or used >= 99.5:
        return None
    e = elapsed_pct(now_ms, reset_ms, length_ms)
    if on_mark(e, used):
        return ("on", None)
    at = half_up((reset_ms - length_ms) + used / 100 * length_ms)
    if used > e:
        return ("over", at - now_ms)
    return ("under", now_ms - at)


def duration(ms):
    """Section 6.3 durations: the two largest units, each rounded down."""
    d, rem = divmod(ms, DAY_MS)
    h, rem = divmod(rem, HOUR_MS)
    m = rem // 60_000
    if d > 0:
        return f"{d}d {h}h"
    if h > 0:
        return f"{h}h {m}m"
    return f"{m}m"


def figure(used, elapsed):
    """Vs pace figure, signed (section 6.3); None when there is none."""
    if used is None or elapsed is None or used >= 99.5:
        return None
    v = toward_zero(elapsed - used)
    return "0%" if v == 0 else (f"+{v}%" if v > 0 else f"{MINUS}{-v}%")


def clock(t, hour24):
    if hour24:
        return f"{t.hour:02d}:{t.minute:02d}"
    h = (t.hour + 11) % 12 + 1
    return f"{h}:{t.minute:02d} {'AM' if t.hour < 12 else 'PM'}"


def same_day(a, b):
    return a.date() == b.date()


def day_clock(now, reset, hour24):
    """Section 6.3 'The reset in a line': weekday and clock; weekday dropped later the same local day."""
    c = clock(reset, hour24)
    return c if same_day(now, reset) else f"{WEEKDAYS[reset.weekday()]} {c}"


def local(s):
    return dt.datetime.fromisoformat(s)


def utc_local(ms):
    return dt.datetime.fromtimestamp(ms / 1000, dt.timezone.utc).replace(tzinfo=None)


# ---------------------------------------------------------------- CCRM-108 (Reset Named): the pace line

def floor_text(side, dur_ms):
    if side == "on":
        return "On the mark"
    return f"{duration(dur_ms)} {'behind' if side == 'under' else 'ahead'}"


def pace_line_forms(side, dur_ms, now, reset, hour24):
    """The planned drop order: a bare clock always means today."""
    if side is None:
        return []
    f = floor_text(side, dur_ms)
    c = clock(reset, hour24)
    if same_day(now, reset):
        return [f"{f}{DOT}resets {c}", f"{f}{DOT}{c}", f]
    wd = WEEKDAYS[reset.weekday()]
    return [f"{f}{DOT}resets {wd} {c}", f"{f}{DOT}{wd} {c}", f"{f}{DOT}{wd}", f]


def tone(side):
    return {"under": "under", "over": "over", "on": "ink"}.get(side)


# ---------------------------------------------------------------- CCRM-107 (Day Grid)

def grid(case):
    n = case["columns"]
    length = DAY_MS if n == 7 else HOUR_MS
    start = local(case["startLocal"])
    now = local(case["nowLocal"])
    w = case["plotWidth"]
    widths = case["widths"]
    col_w = w / n
    verticals = []
    for k in range(1, n):
        verticals.append({"k": k, "atLocal": (start + dt.timedelta(milliseconds=k * length)).isoformat(timespec="minutes"),
                          "xFraction": k / n})
    horizontals = [{"k": k, "pct": 100 * k / n} for k in range(1, n)]
    elapsed_ms = (now - start) / dt.timedelta(milliseconds=1)
    now_col = int(elapsed_ms // length) + 1 if 0 <= elapsed_ms < n * length else None
    now_x = elapsed_ms / (n * length) * w
    reset = start + dt.timedelta(milliseconds=n * length)
    if n == 7:
        start_label = f"{start.day} {MONTHS[start.month - 1]}"
        end_label = f"{reset.day} {MONTHS[reset.month - 1]}"
    else:
        start_label = clock(start, case["hour24"])
        end_label = clock(reset, case["hour24"])
    labels = [{"text": start_label, "at": "left edge", "bold": False}]
    now_box = (now_x - widths["now"] / 2, now_x + widths["now"] / 2) if case["nowLabel"] else None
    start_box = (0, widths["start"])
    end_box = (w - widths["end"], w)

    def gap(a, b):
        return max(a[0], b[0]) - min(a[1], b[1])

    ordinals = []
    for k in range(2, n):
        xc = (k - 0.5) * col_w
        box = (xc - widths["ordinal"] / 2, xc + widths["ordinal"] / 2)
        if col_w < case["dropBelowColumn"]:
            ordinals.append({"text": str(k), "column": k, "drawn": False, "why": f"column {col_w:.2f} < {case['dropBelowColumn']}"})
            continue
        if now_box and gap(box, now_box) < case["nowGap"]:
            ordinals.append({"text": str(k), "column": k, "drawn": False,
                             "why": f"gives way to now: gap {gap(box, now_box):.2f} < {case['nowGap']}"})
            continue
        if gap(box, start_box) < case["endGap"] or gap(box, end_box) < case["endGap"]:
            g = min(gap(box, start_box), gap(box, end_box))
            ordinals.append({"text": str(k), "column": k, "drawn": False,
                             "why": f"gives way to an end label: gap {g:.2f} < {case['endGap']}"})
            continue
        ordinals.append({"text": str(k), "column": k, "drawn": True, "centreX": round(xc, 4), "bold": k == now_col})
    out = dict(case)
    out["expected"] = {
        "verticals": verticals,
        "horizontals": horizontals,
        "nowColumn": now_col,
        "nowX": round(now_x, 4),
        "startLabel": start_label,
        "endLabel": end_label,
        "ordinals": ordinals,
    }
    return out


# em model of the wireframe (TextFit's), for label widths given to the cases as inputs


def em(s):
    tot = 0.0
    for c in s:
        if c.isdigit():
            tot += .56
        elif c in "iltrfj":
            tot += .30
        elif c in "mw":
            tot += .86
        elif c.islower():
            tot += .54
        elif c in "WMO":
            tot += .80
        elif c.isupper():
            tot += .62
        elif c == " ":
            tot += .25
        elif c == ":":
            tot += .23
        elif c == "%":
            tot += .46
        else:
            tot += .5
    return tot


def width(s, sp):
    return round(em(s) * sp, 2)


# ---------------------------------------------------------------- CCRM-109 (Quiet Accounts)

def row_percent(used):
    """The row's printed Weekly used figure: a window row truncates (section 2's window bars; Android
    Palette.usageInt, iOS Formatting.windowPercentDisplay). Half up is for credits and estimates only."""
    return None if used is None else math.trunc(used)


# ---------------------------------------------------------------- CCRM-111 (Map Layers): figures, callout, projection

def map_figure(name, mode, used, elapsed, spent):
    if used is None:
        return None
    if spent:
        return f"{name} {'0%' if mode == 'left' else '100%'}"  # the wireframe's figOf: spent Left prints "0%"
    if mode == "vs":
        f = figure(used, elapsed)
        if f is not None:
            return f"{name} {f}"
        mode = "used"
    if mode == "left":
        return f"{name} {max(0, math.trunc(100 - used))}% left"  # as the row: Left truncates the remainder
    return f"{name} {math.trunc(used)}%"  # as the row: Used truncates


def forecast(case):
    est = case["estimate"]
    if est is None or case["spent"] or case["resetMs"] is None:
        return None
    hit = est["hitsLimitAtMs"]
    if hit is not None and hit < case["resetMs"]:
        now = utc_local(case["nowMs"])
        return f"100% at {day_clock(now, utc_local(hit), case['hour24'])}"
    return f"~{half_up(est['pctAtReset'])}% at reset"


def projection_layer(case):
    est = case["estimate"]
    if est is None or case["spent"] or case["resetMs"] is None:
        return None
    start = case["resetMs"] - WEEK_MS
    if est["hitsLimitAtMs"] is not None and est["hitsLimitAtMs"] < case["resetMs"]:
        x = (est["hitsLimitAtMs"] - start) / WEEK_MS * 100
        return {"endWeekGonePct": round(x, 6), "endUsedPct": 100.0, "ring": "red", "tailEnd": "red"}
    return {"endWeekGonePct": 100.0, "endUsedPct": float(min(100.0, est["pctAtReset"])), "ring": "hollow",
            "tailEnd": "accent"}


# ---------------------------------------------------------------- CCRM-111: name placement with short leaders

DIRS = [("E", 1, 0), ("SE", 1, 1), ("S", 0, 1), ("SW", -1, 1), ("W", -1, 0), ("NW", -1, -1), ("N", 0, -1), ("NE", 1, -1)]


def hit(a, b, pad):
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax < bx + bw + pad and bx < ax + aw + pad and ay < by + bh + pad and by < ay + ah + pad


def seg_hits_rect(p, q, r):
    """Does segment p-q intersect rectangle r (x, y, w, h)? Liang-Barsky clipping."""
    x0, y0 = p
    x1, y1 = q
    dx, dy = x1 - x0, y1 - y0
    t0, t1 = 0.0, 1.0
    for pv, qv in ((-dx, x0 - r[0]), (dx, r[0] + r[2] - x0), (-dy, y0 - r[1]), (dy, r[1] + r[3] - y0)):
        if pv == 0:
            if qv < 0:
                return False
        else:
            t = qv / pv
            if pv < 0:
                t0 = max(t0, t)
            else:
                t1 = min(t1, t)
            if t0 > t1:
                return False
    return True


def place_names(case):
    g = case["geometry"]
    plot = case["plot"]
    W, H = plot["width"], plot["height"]
    m = g["nameAreaMargin"]
    area = (-m["left"], -m["top"], W + m["left"] + m["right"], H + m["top"] + m["bottom"])
    dh = g["dotHalf"]
    dots = []
    for d in case["dots"]:
        cx = d["weekGonePct"] / 100 * W
        cy = (100 - d["usedPct"]) / 100 * H
        dots.append(dict(d, cx=cx, cy=cy, box=(cx - dh, cy - dh, 2 * dh, 2 * dh)))
    fixed = [tuple(f["box"]) for f in case.get("fixed", [])]
    rings = [tuple(r["box"]) for r in case.get("rings", [])]
    placed = []  # boxes of drawn names
    results = {}

    def inside(r):
        return r[0] >= area[0] and r[1] >= area[1] and r[0] + r[2] <= area[0] + area[2] and r[1] + r[3] <= area[1] + area[3]

    def refuse(r, me):
        if not inside(r):
            return "leaves the plot"
        for f in fixed:
            if hit(r, f, g["fixedGap"]):
                return "overlaps a word"
        for o in dots:
            if hit(r, o["box"], 0):
                return "overlaps a dot" if o is not me else "overlaps its own dot"
        for p in placed:
            if hit(r, p, g["nameGap"]):
                return "overlaps a placed name"
        for rg in rings:
            if hit(r, rg, 0):
                return "sits on a ring"
        tail = me.get("tail")
        if tail and seg_hits_rect((me["cx"], me["cy"]), (tail["toWeekGonePct"] / 100 * W, (100 - tail["toUsedPct"]) / 100 * H), r):
            return "sits on its own tail"
        return None

    order = sorted(dots, key=lambda d: (d["cy"], d["cx"]))
    for d in order:
        w, h = d["nameWidth"], d["nameHeight"]
        cx, cy = d["cx"], d["cy"]
        s = g["spots"]
        cands = [
            ("right", (cx + s["rightDx"], cy - h / 2, w, h)),
            ("below", (cx + s["belowDx"], cy + h / 2 + s["belowDy"], w, h)),
            ("left", (cx - s["leftDx"] - w, cy - h / 2, w, h)),
            ("aboveLeft", (cx - s["leftDx"] - w, cy - s["aboveDy"] - h, w, h)),
        ]
        tried = []
        pick = None
        for spot, r in cands:
            why = refuse(r, d)
            if why is None:
                pick = {"spot": spot, "box": [round(v, 4) for v in r], "leader": None}
                break
            tried.append(f"{spot}: {why}")
        if pick is None:
            for dist in g["leaderDistances"]:
                for name, ux, uy in DIRS:
                    n = math.hypot(ux, uy)
                    ex, ey = cx + dist * ux / n, cy + dist * uy / n
                    bx = ex if ux > 0 else (ex - w if ux < 0 else ex - w / 2)
                    by = ey if uy > 0 else (ey - h if uy < 0 else ey - h / 2)
                    r = (bx, by, w, h)
                    why = refuse(r, d)
                    if why is None:
                        for p in placed:
                            if seg_hits_rect((cx, cy), (ex, ey), p):
                                why = "path crosses a placed name"
                                break
                    if why is None:
                        for o in dots:
                            if o is not d and seg_hits_rect((cx, cy), (ex, ey), o["box"]):
                                why = "path crosses a dot"
                                break
                    if why is None:
                        pick = {"spot": "leader", "box": [round(v, 4) for v in r],
                                "leader": {"distance": dist, "direction": name,
                                           "end": [round(ex, 4), round(ey, 4)]}}
                        break
                    tried.append(f"{dist} {name}: {why}")
                if pick:
                    break
        if pick is None:
            pick = {"spot": "none", "box": None, "leader": None}
        else:
            placed.append(tuple(pick["box"]))
        refusals = sorted({t.split(": ", 1)[1] for t in tried})
        pick["refusedFor"] = refusals
        results[d["name"]] = pick
    out = {k: v for k, v in case.items()}
    out["expected"] = {"order": [d["name"] for d in order],
                       "names": [dict(name=d["name"], **results[d["name"]]) for d in case["dots"]]}
    return out


# ---------------------------------------------------------------- the cases

def build_core():
    now = "2026-11-04T14:00"
    pace_line = []
    for name, side, dur, reset in [
        ("behind, widest", "under", 604_799_999, "2026-11-11T12:30"),
        ("ahead", "over", 50_340_000, "2026-11-05T18:15"),
        ("on the mark", "on", None, "2026-11-07T08:00"),
        ("behind, reset later today", "under", 20_700_000, "2026-11-04T23:00"),
    ]:
        forms = pace_line_forms(side, dur, local(now), local(reset), False)
        pace_line.append({"name": name, "side": side, "durationMs": dur, "nowLocal": now, "resetLocal": reset,
                          "hour24": False, "reset": forms[0].split(DOT)[1], "text": forms[0],
                          "tinted": None if side == "on" else floor_text(side, dur), "tone": tone(side)})
    pace_line.append({"name": "no readout", "side": None, "durationMs": None, "nowLocal": now,
                      "resetLocal": "2026-11-07T08:00", "hour24": False, "reset": None, "text": None, "tinted": None,
                      "tone": None,
                      "why": "spent, no reset clock or no reading: the family's held / spent / signed-out / no-reading line instead"})

    forms_cases = []
    for name, side, dur, n, reset, h24 in [
        ("another day: resets, then the bare reset, then the weekday alone, then the floor", "over", 86_399_999, now,
         "2026-11-11T12:30", False),
        ("tomorrow, under 24 h: still another day, so the weekday stays to the last", "under", 3_600_000, now,
         "2026-11-05T09:00", False),
        ("on the mark, another day", "on", None, now, "2026-11-07T08:00", False),
        ("later today: no weekday, so a bare clock (it means today), then the floor", "under", 125_280_000, now,
         "2026-11-04T23:00", False),
        ("24-hour, another day", "under", 604_799_999, now, "2026-11-05T09:00", True),
        ("24-hour, later today", "over", 3_540_000, now, "2026-11-04T23:00", True),
        ("the wireframe's moment (Tue 10 Nov 2:00 PM): a reset days away ends on the weekday, never a bare clock",
         "under", 20_700_000, "2026-11-10T14:00", "2026-11-15T21:30", False),
        ("no readout", None, None, now, "2026-11-11T12:30", False),
    ]:
        forms_cases.append({"name": name, "side": side, "durationMs": dur, "nowLocal": n, "resetLocal": reset,
                            "hour24": h24, "forms": pace_line_forms(side, dur, local(n), local(reset), h24)})

    now_ms = 1_793_800_800_000  # Wed 2026-11-04 14:00 UTC
    e2e = []
    for name, used, reset_ms in [
        ("behind", 40, 1_794_038_400_000),
        ("ahead", 91, 1_793_902_500_000),
        ("on the mark, under by a fraction", 60.5, 1_794_038_400_000),
        ("on the mark, over by a fraction", 61.2, 1_794_038_400_000),
        ("behind, reset later today", 20, 1_793_833_200_000),
        ("spent: no figure, no line", 99.6, 1_793_902_500_000),
    ]:
        e = elapsed_pct(now_ms, reset_ms, WEEK_MS)
        mt = mark_time(used, now_ms, reset_ms, WEEK_MS)
        line = None
        why = f"elapsed {e:.2f}"
        if mt is not None:
            side, dur = mt
            line = pace_line_forms(side, dur, utc_local(now_ms), utc_local(reset_ms), False)[0]
            if dur is not None:
                why += f", {'inMs' if side == 'over' else 'markWasAtUsedAgoMs'} {dur}"
        else:
            why += "; used >= 99.5 is spent (section 6.1): the usage figure and the family's spent line instead"
        e2e.append({"name": name, "usedPct": used, "nowMs": now_ms, "resetMs": reset_ms, "windowLengthMs": WEEK_MS,
                    "figure": figure(used, e), "line": line, "why": why})

    # ---- day grid
    def gcase(name, columns, start, now_l, plot_w, hour24=False, now_label=True):
        reset = local(start) + dt.timedelta(days=7) if columns == 7 else local(start) + dt.timedelta(hours=5)
        sl = f"{local(start).day} {MONTHS[local(start).month - 1]}" if columns == 7 else clock(local(start), hour24)
        el = f"{reset.day} {MONTHS[reset.month - 1]}" if columns == 7 else clock(reset, hour24)
        return grid({"name": name, "columns": columns, "startLocal": start, "nowLocal": now_l, "hour24": hour24,
                     "plotWidth": plot_w, "nowLabel": now_label,
                     "widths": {"start": width(sl, 9.5), "end": width(el, 9.5), "ordinal": width("0", 9.5),
                                "now": width("now", 9.5)},
                     "nowGap": 6, "endGap": 4, "dropBelowColumn": 14})

    grid_cases = [
        gcase("7-day, the wireframe's first drawing: now at 39% is in column 3, so \"3\" gives way", 7,
              "2026-11-04T09:30", "2026-11-07T02:58", 339),
        gcase("7-day, now late in column 3: \"3\" stays and is bold", 7, "2026-11-04T09:30", "2026-11-07T08:30", 339),
        gcase("7-day, now on a column line belongs to the later column (start + 2 x 24 h is column 3)", 7,
              "2026-11-04T09:30", "2026-11-06T09:30", 339),
        gcase("7-day, now in column 7 (the reset's): no ordinal is bold; the end labels never are (\"now\" hidden "
              "this close to the end, as the family's chart does)", 7, "2026-11-04T09:30", "2026-11-10T14:00", 339,
              now_label=False),
        gcase("7-day, narrow (two-stack half, 150 wide): ordinals that touch an end label give way", 7,
              "2026-11-04T09:30", "2026-11-07T21:30", 150),
        gcase("7-day, the wireframe's iOS cap half (150 wide, now at 39%): by the rule \"2\" and \"5\" stay "
              "(the drawing, measured at 5.2 per character, shows \"4\" and \"5\")", 7,
              "2026-11-04T09:30", "2026-11-07T02:58", 150),
        gcase("7-day, columns under 14 wide: no ordinals at all (the grid lines still draw)", 7,
              "2026-11-04T09:30", "2026-11-07T21:30", 90),
        gcase("5h, the wireframe's drawing: now at 52% is in column 3, so \"3\" gives way", 5,
              "2026-11-10T09:00", "2026-11-10T11:36", 339),
        gcase("5h, now early in column 3: \"3\" stays and is bold", 5, "2026-11-10T09:00", "2026-11-10T11:05", 339),
        gcase("5h, 24-hour clocks at the ends", 5, "2026-11-10T09:00", "2026-11-10T10:10", 339, hour24=True),
    ]

    # ---- quiet accounts
    quiet = []
    for name, used in [("no use", 0.0), ("a sliver", 0.3), ("half a point still prints 0%", 0.5),
                       ("just under the edge", 0.99), ("on the edge: prints 1%", 1.0), ("just over", 1.01),
                       ("a reading of 52.9 prints 52%", 52.9)]:
        rp = row_percent(used)
        quiet.append({"name": name, "usedPct": used, "rowPrints": f"{rp}%", "plotted": rp != 0})
    quiet.append({"name": "no reading", "usedPct": None, "rowPrints": None, "plotted": False,
                  "why": "off the map as before; counted by the foot line"})
    quiet.append({"name": "not started (no reset clock, section 1.1)", "usedPct": 0.0, "resetMs": None,
                  "rowPrints": "0%", "plotted": False,
                  "why": "an untouched window prints 0%; with no reset clock it has no x either"})

    # ---- figures after the name
    figs = []
    for name, mode, used, el, spent in [
        ("Used", "used", 52, 39.0, False), ("Left", "left", 52, 39.0, False), ("Vs pace, over", "vs", 52, 39.0, False),
        ("Vs pace, under", "vs", 30, 49.2, False), ("Vs pace, on the mark", "vs", 60.5, 60.71, False),
        ("Vs pace with no reset clock falls back to Used", "vs", 12, None, False),
        ("spent, Used", "used", 100, 70.0, True), ("spent, Left prints 0% (as drawn, no 'left')", "left", 100, 70.0, True),
        ("spent, Vs pace prints the usage figure", "vs", 100, 70.0, True),
        ("Used truncates like the row", "used", 51.5, 39.0, False),
        ("Left truncates the remainder like the row", "left", 51.5, 39.0, False),
    ]:
        figs.append({"name": name, "account": "Teams", "mode": mode, "usedPct": used, "elapsedPct": el,
                     "spent": spent, "label": map_figure("Teams", mode, used, el, spent)})
    figs.append({"name": "no reading: not on the map", "account": "Teams", "mode": "used", "usedPct": None,
                 "elapsedPct": None, "spent": False, "label": None})

    # ---- callout forecast and projection layer (Wed 2026-11-04 14:00 UTC; resets Sat 8:00 AM and later)
    reset_sat = 1_794_038_400_000
    reset_thu = 1_793_902_500_000
    fc = []
    pj = []
    tue = 1_794_319_200_000  # Tue 2026-11-10 14:00 UTC, the wireframe's moment
    for name, n_ms, used, est, reset, spent in [
        ("projects under 100: the projected % at the reset", now_ms, 52, {"pctAtReset": 74.4, "hitsLimitAtMs": None},
         reset_sat, False),
        ("rounds half up", now_ms, 52, {"pctAtReset": 73.5, "hitsLimitAtMs": None}, reset_sat, False),
        ("runs out on another day (the wireframe's Wed 6:10 PM, seen on Tue): the instant, as a reset is printed",
         tue, 91, {"pctAtReset": 100.0, "hitsLimitAtMs": tue + DAY_MS + 4 * HOUR_MS + 10 * 60_000},
         tue + 3 * DAY_MS, False),
        ("runs out later today: a bare clock (it means today)", now_ms, 91,
         {"pctAtReset": 100.0, "hitsLimitAtMs": now_ms + 4 * HOUR_MS + 10 * 60_000}, reset_thu, False),
        ("a projection of 99.6 that does not run out prints ~100% at reset", now_ms, 80,
         {"pctAtReset": 99.6, "hitsLimitAtMs": None}, reset_sat, False),
        ("no estimate (too little span or movement): no third line, no tail", now_ms, 52, None, reset_sat, False),
        ("spent: no forecast, no tail", now_ms, 100, {"pctAtReset": 100.0, "hitsLimitAtMs": None}, reset_sat, True),
        ("no reset clock: no forecast, no tail", now_ms, 12, {"pctAtReset": 40.0, "hitsLimitAtMs": None}, None, False),
    ]:
        c = {"name": name, "usedPct": used, "nowMs": n_ms, "resetMs": reset, "hour24": False, "estimate": est,
             "spent": spent}
        fc.append(dict(c, text=forecast(c)))
        pj.append(dict(c, layer=projection_layer(c)))

    # ---- map labels: the dense 200-wide snapshot map
    geom = {"dotHalf": 7, "nameGap": 2, "fixedGap": 1,
            "nameAreaMargin": {"left": 2, "top": 6, "right": 8, "bottom": 6},
            "spots": {"rightDx": 10, "belowDx": 2, "belowDy": 3, "leftDx": 10, "aboveDy": 6},
            "leaderDistances": [24, 36, 48]}

    def dot(name, g, u, **kw):
        return dict({"name": name, "weekGonePct": g, "usedPct": u, "nameWidth": width(name, 10.5), "nameHeight": 13}, **kw)

    dense = place_names({
        "name": "dense 200-wide snapshot map",
        "plot": {"width": 200, "height": 120},
        "geometry": geom,
        "fixed": [
            {"word": "ahead of pace", "box": [4, 4, width("ahead of pace", 10.5), 13]},
            {"word": "behind pace", "box": [200 - 4 - width("behind pace", 10.5), 103, width("behind pace", 10.5), 13]},
            {"word": "even pace", "box": [128, 34, 30, 30]},
        ],
        "dots": [
            dot("Teams", 62, 74), dot("Atlas", 62, 64), dot("Lab", 62, 70), dot("Work", 56, 48),
            dot("Personal", 68, 58), dot("Side", 74, 68), dot("Gemini", 88, 92), dot("Pro", 58, 62),
            dot("Max", 25, 26), dot("Plus", 44, 89), dot("Ultra", 37, 38),
        ],
    })
    proj = place_names({
        "name": "Projection layer on: a name does not sit on its own tail or on any ring",
        "plot": {"width": 200, "height": 120},
        "geometry": geom,
        "fixed": [],
        "rings": [{"of": "Teams", "box": [196.5, 26.5, 7, 7]}],
        "dots": [
            dot("Teams", 60, 52, tail={"toWeekGonePct": 100, "toUsedPct": 75}),
            dot("Lab", 40, 20),
        ],
    })

    words = {"aboveDiagonal": "ahead of pace", "belowDiagonal": "behind pace", "diagonal": "even pace",
             "xStart": "week start", "xOrdinals": ["2", "3", "4", "5", "6"], "xEnd": "reset",
             "footNote": "each account's own week", "xCentre": None,
             "_note": "xCentre is gone: the centre note moves from the axis to the card's foot line (footNote)."}

    return {
        "_meta": {
            "note": ("BEHAVIOR-SPEC section 6.3 'Planned - v2.0 feedback' (approved 2026-11-10, wireframe "
                     "design/2026-11-10-v20-feedback-wireframes.html rev B): CCRM-107 (Day Grid), CCRM-108 (Reset "
                     "Named), CCRM-109 (Quiet Accounts), CCRM-111 (Map Layers). Derived by spec/tools/gen_v20_fixtures.py "
                     "from the written rules; do not edit by hand. These blocks replace their namesakes in printed.json "
                     "at RUNBOOK Step 2; blocks not here (figure, duration, dayClock, caption) are unchanged and stay "
                     "in printed.json. Conventions as printed.json: MINUS is U+2212, the separator is ' · ', clock "
                     "strings en-US, local times naive."),
            "tolerance": "exact strings; numbers to 1e-6",
            "dropOrder": ("paceLineForms lists every form from widest to the floor. A reset on another day: "
                          "'resets Wed 12:30 PM' -> 'Wed 12:30 PM' -> 'Wed' -> the floor. Later the same local day: "
                          "'resets 11:00 PM' -> '11:00 PM' -> the floor. A bare clock always means today."),
            "dayGrid": ("Inputs are the window start, now, the plot width and the label widths (measured by the "
                        "wireframe's em model at 9.5 sp; a client tests its rule with these widths, not its own "
                        "measurement). Verticals at start + k x 24 h (7-day) or + k x 1 h (5h), k = 1..n-1; "
                        "horizontals at k/n of 100%, k = 1..n-1 (the 100% guide is the n-th). Ordinals 2..n-1 are "
                        "centred in their columns; the start label is left-aligned at x 0 and the reset label "
                        "right-aligned at the plot width. An ordinal gives way when its box is less than nowGap (6, "
                        "the wireframe's words) from the 'now' label's box (centred on now), or less than endGap (4, "
                        "the wireframe's drawing) from an end label's box; every ordinal drops when a column is under "
                        "dropBelowColumn (14). nowColumn is 1-based; start + k x 24 h itself belongs to column k+1. "
                        "Only an ordinal is bold: in columns 1 and n the end labels stay plain, as drawn."),
            "quiet": ("An account leaves the Pace map when its row prints '0%' Weekly used: the displayed percent "
                      "truncates like a window row (section 2's window bars), so 0.99 prints 0% and is hidden, and 1.0 prints 1% "
                      "and is plotted (a row reading 1% is always on the map). The list still shows the row. The "
                      "map's figure labels truncate the same way; only the callout's estimate rounds half up."),
            "mapLabels": ("Placement geometry for the test (units are dp/pt; each family keeps its own drawn "
                          "offsets, and tests its placer with these). Plot origin top-left, x = week gone % x width, "
                          "y = (100 - used) % x height. A dot's hit box is 2 x dotHalf square. Names go top-down "
                          "(smallest y first, then smallest x). Spots in order: right (cx + rightDx, cy - h/2), below "
                          "(cx + belowDx, cy + h/2 + belowDy), left (cx - leftDx - w, cy - h/2), above-left "
                          "(cx - leftDx - w, cy - aboveDy - h). Then leaders: each distance in leaderDistances, and "
                          "for each the eight directions clockwise from east in screen coordinates (E, SE, S, SW, W, "
                          "NW, N, NE); the leader ends at the dot centre + distance along the direction, and the name "
                          "box touches that end by its side or corner facing away from the dot (E: left-middle; SE: "
                          "top-left; S: top-centre; ...). A spot is refused if the box leaves the name area (the plot "
                          "plus nameAreaMargin), comes within fixedGap of a fixed word, overlaps any dot's hit box, "
                          "comes within nameGap of a placed name, sits on any ring, or crosses its own projection "
                          "tail; a leader is refused also if its segment (dot centre to end) crosses a placed name's "
                          "box or another dot's hit box. Nothing free: spot 'none', the name is not drawn and takes "
                          "no space (its dot stays and still opens its callout). refusedFor lists the reasons met on "
                          "the way, for reading the case."),
            "forecast": ("The callout's third line from section 3's estimate on the weekly samples: hitsLimitAtMs "
                         "before the reset -> '100% at ' + the instant printed as a reset is (weekday dropped later "
                         "the same local day); otherwise '~' + pctAtReset rounded half up + '% at reset'. No estimate, "
                         "spent, or no reset clock -> no line. Times in UTC."),
            "projectionLayer": ("A dashed tail from the dot to a ring. Runs out before the reset -> the ring sits on "
                                "the 100% line at the week-gone % of hitsLimitAtMs, ring and tail end red; otherwise a "
                                "hollow ring on the right edge (100% week gone) at pctAtReset. null = no tail, no ring."),
        },
        "paceLine": pace_line,
        "paceLineForms": forms_cases,
        "endToEnd": {"_note": "From the numbers to the printed figure and line, in UTC, 12-hour clock, a 7-day window. "
                              "nowMs is Wed 2026-11-04 14:00 UTC.", "cases": e2e},
        "dayGrid": grid_cases,
        "quiet": quiet,
        "mapLabels": {"words": words, "figures": figs, "placement": [dense, proj]},
        "callout": {"forecast": fc},
        "projectionLayer": pj,
    }


def build_map_foot():
    def acct(name, used, reset=True):
        return {"name": name, "usedPct": used, "clocked": reset}

    def foot(accts):
        zero = sum(1 for a in accts if a["usedPct"] is not None and math.trunc(a["usedPct"]) == 0)
        # no reading, or a reading that prints 1% or more with no reset clock (no readout, core 6.1 / 6.3)
        none = sum(1 for a in accts if a["usedPct"] is None or (math.trunc(a["usedPct"]) != 0 and not a["clocked"]))
        plotted = [a["name"] for a in accts if a["usedPct"] is not None and math.trunc(a["usedPct"]) != 0 and a["clocked"]]
        parts = []
        if zero:
            parts.append(f"{zero} at 0%")
        if none:
            parts.append(f"{none} no reading")
        empty = None
        if not plotted and zero and not none:
            empty = {"title": "Nothing to plot yet", "detail": "every account is at 0% this week"}
        return {"plotted": plotted, "left": "each account's own week", "count": DOT.join(parts) or None,
                "empty": empty}

    cases = []
    for name, accts in [
        ("the wireframe's 'with the suggested unread count'",
         [acct("Teams", 52), acct("Personal", 30), acct("Tiny", 0.2), acct("Tiny 2", 0.0), acct("Product", None)]),
        ("two hidden (the cover drawing)",
         [acct("Teams", 52), acct("Personal", 30), acct("Lab", 61), acct("Tiny", 0.2), acct("Tiny 2", 0.4)]),
        ("one at 0%: singular is the same form", [acct("Teams", 52), acct("Tiny", 0.0)]),
        ("one no reading", [acct("Teams", 52), acct("Product", None)]),
        ("a not-started week counts as at 0% (its row prints 0%)",
         [acct("Teams", 52), acct("Fresh", 0.0, reset=False)]),
        ("a reading of 1% or more with no reset clock cannot be plotted: counted as no reading",
         [acct("Teams", 52), acct("Odd", 3.0, reset=False)]),
        ("0.99 prints 0%: hidden and counted", [acct("Teams", 52), acct("Edge", 0.99)]),
        ("1.0 prints 1%, so it is plotted and not counted", [acct("Teams", 52), acct("Edge", 1.0)]),
        ("nothing unplotted: no count", [acct("Teams", 52), acct("Personal", 30)]),
        ("every account at 0%: the frame stays with the message",
         [acct("Tiny", 0.0), acct("Tiny 2", 0.3), acct("x", 0.2)]),
    ]:
        cases.append({"name": name, "accounts": accts, "expected": foot(accts)})
    return {
        "_meta": {
            "note": ("The Pace map's foot line on mobile (CCRM-109 (Quiet Accounts), wireframe rev B section 5; core "
                     "section 6.3 'Planned - v2.0 feedback': each family words the count). Derived by "
                     "spec/tools/gen_v20_fixtures.py. Each case lists the accounts the map covers (after the Summary "
                     "filter: the map follows it). usedPct is the Weekly reading (null = no reading or signed out); "
                     "clocked = the week has a reset clock. left is the foot line's left text, count its right text "
                     "(11 sp muted): 'N at 0%' for readings whose row prints 0% (a not-started week included), "
                     "'N no reading' for accounts with none (and for the rare reading of 1% or more with no reset clock, which has no x: "
                     "core groups it with no reading as no readout; Fable call 2026-11-11), joined by ' · ', each part left out at zero, null "
                     "when both are. empty is the message in the behind wedge when every account is at 0%."),
            "notDrawn": ("A map with nothing plotted where some accounts have no reading is not drawn in rev B; no "
                         "case covers it.")},
        "footLine": cases,
    }


def build_tags():
    def a(name, tag):
        return {"name": name, "tag": tag}

    def run(accts, remembered):
        tagged = [x for x in accts if x["tag"]]
        shown_ctrl = bool(tagged)
        segs = {"all": True, "personal": any(x["tag"] == "personal" for x in accts),
                "work": any(x["tag"] == "work" for x in accts)}
        eff = remembered if shown_ctrl and remembered in segs and segs[remembered] else "all"
        shown = [x["name"] for x in accts if eff == "all" or x["tag"] == eff]
        return {"controlShown": shown_ctrl, "segmentsEnabled": segs if shown_ctrl else None, "filter": eff,
                "shown": shown, "oneLineRows": len(shown) >= 6,
                "snapshotHeader": {"all": "All accounts", "personal": "Personal accounts", "work": "Work accounts"}[eff]}

    six = [a("Teams", "work"), a("Personal", "personal"), a("Lab", None), a("Atlas", "work"), a("Side", "personal"),
           a("Design", "work"), a("Gemini", "work")]
    cases = []
    for name, accts, rem in [
        ("no tags: no control, everything under All", [a("Teams", None), a("Personal", None)], "work"),
        ("the sample: Teams Work, Personal Personal, Lab untagged; Work chosen",
         [a("Teams", "work"), a("Personal", "personal"), a("Lab", None)], "work"),
        ("Personal chosen: the untagged account shows only under All",
         [a("Teams", "work"), a("Personal", "personal"), a("Lab", None)], "personal"),
        ("All", [a("Teams", "work"), a("Personal", "personal"), a("Lab", None)], "all"),
        ("a segment with no accounts is disabled; a remembered filter on it falls back to All",
         [a("Teams", "work"), a("Lab", None)], "personal"),
        ("an unknown remembered value reads as All", [a("Teams", "work"), a("Lab", None)], "family"),
        ("seven accounts: one-line rows under All", six, "all"),
        ("the one-line form follows the rows shown, not the accounts: Work shows four", six, "work"),
    ]:
        cases.append({"name": name, "accounts": accts, "rememberedFilter": rem, "expected": run(accts, rem)})
    return {
        "_meta": {
            "note": ("Account tags and the Summary filter on mobile (CCRM-110 (Account Tags), CCRM-112 (Summary "
                     "Snapshot); wireframe rev B sections 6 and 8; BEHAVIOR-MOBILE section 6). Derived by "
                     "spec/tools/gen_v20_fixtures.py. tag is null (None), 'personal' or 'work'; the stored key and "
                     "its spelling are each client's (additive, absent = None). accounts are in your order. The "
                     "All / Personal / Work control shows once any account has a tag; a segment with no accounts is "
                     "disabled; a remembered filter that is unknown, disabled or has no accounts reads as All. The "
                     "filter narrows the list, the Pace map and the snapshot; untagged accounts show only under All. "
                     "oneLineRows: the one-line row form from six rows shown. snapshotHeader: the share snapshot's "
                     "header under the filter. The sort order is unchanged by the filter (summary/cases.json order "
                     "applies to the rows shown).")},
        "filter": cases,
    }


PW_NOTE = ("Account tags and the Summary filter on mobile after Robin's 2026-11-11 change: there is no None; every account is "
           "Personal or Work, chosen when it is added (Personal pre-selected). CCRM-110 (Account Tags), CCRM-112 (Summary "
           "Snapshot); BEHAVIOR-MOBILE section 6. Derived by spec/tools/gen_v20_fixtures.py. A stored tag is 'personal' or "
           "'work'; a missing, empty or unknown value reads as personal (that is how v1.9's accounts become Personal at "
           "upgrade, with nothing written). Accounts are in your order; storedTag null = nothing stored. The All / Personal "
           "/ Work control exists while the Summary does (two or more accounts); a segment with no accounts is disabled, All "
           "never. The filter in force is the remembered value when it names an enabled segment of a shown control, else "
           "All. It narrows the list, the ranking, the Pace map and the snapshot (the snapshot header names it). oneLineRows: "
           "the one-line form from six rows shown. storedAfter: the remembered value once the memory rule has run (a stored "
           "value the filter in force does not match becomes 'all'; an absent value stays absent; with fewer than two "
           "accounts there is no Summary, the rule does not run, and the value stays as it was). memory: sequences of "
           "changes (open the Summary, tag an account, add, remove, choose a segment), each step followed by the filter in "
           "force and the value stored; the client's test replays them against its own store. Tagging never brings an "
           "earlier choice back.")


def pw_tag(stored):
    return stored if stored in ("personal", "work") else "personal"


def pw_filter(accts, remembered):
    ctrl = len(accts) >= 2
    segs = {"all": True, "personal": any(pw_tag(x["storedTag"]) == "personal" for x in accts),
            "work": any(pw_tag(x["storedTag"]) == "work" for x in accts)}
    return ctrl, segs, (remembered if ctrl and remembered in segs and segs[remembered] else "all")


def run_pw_memory(accts, stored, steps):
    accts = [dict(x) for x in accts]
    out = []
    for st in steps:
        do = st["do"]
        if do == "tag":
            next(x for x in accts if x["name"] == st["account"])["storedTag"] = st["tag"]
        elif do == "add":
            accts.append({"name": st["account"], "storedTag": st["tag"]})
        elif do == "remove":
            accts = [x for x in accts if x["name"] != st["account"]]
        elif do == "choose":
            assert pw_filter(accts, st["filter"])[2] == st["filter"], "a disabled segment cannot be chosen"
            stored = st["filter"]
        elif do != "open":
            raise ValueError(do)
        if len(accts) >= 2:  # the rule runs only while the Summary exists
            eff = pw_filter(accts, stored)[2]
            stored = None if stored is None else eff
        else:
            eff = "all"
        out.append({"filter": eff, "stored": stored})
    return out


def build_tags_pw():
    def a(name, tag):
        return {"name": name, "storedTag": tag}

    def run(accts, remembered):
        ctrl, segs, eff = pw_filter(accts, remembered)
        shown = [x["name"] for x in accts if eff == "all" or pw_tag(x["storedTag"]) == eff]
        return {"effectiveTags": [pw_tag(x["storedTag"]) for x in accts], "controlShown": ctrl,
                "segmentsEnabled": segs if ctrl else None, "filter": eff, "shown": shown,
                "oneLineRows": len(shown) >= 6,
                "snapshotHeader": {"all": "All accounts", "personal": "Personal accounts", "work": "Work accounts"}[eff],
                # The memory rule runs only while the Summary exists (two or more accounts): with one account the stored
                # value is left exactly as it was (BEHAVIOR-MOBILE §6; memory sequence "a second account added to a
                # single-account install" agrees).
                "storedAfter": None if remembered is None else (eff if ctrl else remembered)}

    sample = [a("Teams", "work"), a("Personal", "personal"), a("Lab", "work")]
    seven = [a("Teams", "work"), a("Personal", "personal"), a("Lab", "work"), a("Atlas", "work"), a("Side", "personal"),
             a("Design", "work"), a("Gemini", "personal")]
    cases = []
    for name, accts, rem in [
        ("upgrade from v1.9: nothing stored reads as Personal, Work is disabled, a leftover Work falls back to All",
         [a("Teams", None), a("Lab", None)], "work"),
        ("one account: no Summary, so no control and nothing to filter", [a("Teams", "work")], "work"),
        ("the sample: Teams Work, Personal Personal, Lab Work; Work chosen", sample, "work"),
        ("Personal chosen", sample, "personal"),
        ("All", sample, "all"),
        ("a segment with no accounts is disabled; a remembered filter on it falls back to All",
         [a("Teams", "personal"), a("Lab", "personal")], "work"),
        ("an unknown remembered value reads as All", sample, "family"),
        ("an empty or unknown stored tag reads as Personal", [a("Teams", ""), a("Lab", "family"), a("Atlas", "work")],
         "personal"),
        ("seven accounts: one-line rows under All", seven, "all"),
        ("the one-line form follows the rows shown, not the accounts: Work shows four", seven, "work"),
        ("nothing remembered: reads as All and nothing is stored", sample, None),
    ]:
        cases.append({"name": name, "accounts": accts, "rememberedFilter": rem, "expected": run(accts, rem)})
    three = [a("Teams", "work"), a("Personal", "personal"), a("Lab", "personal")]
    memory = []
    for name, accts, stored, steps in [
        ("the last Work account becomes Personal: All is stored, and marking another Work later does not bring Work back",
         three, "work",
         [{"do": "open"}, {"do": "tag", "account": "Teams", "tag": "personal"},
          {"do": "tag", "account": "Lab", "tag": "work"}, {"do": "choose", "filter": "work"}]),
        ("removing the last Personal account falls back to All; adding a Personal one later leaves All",
         [a("Teams", "work"), a("Atlas", "work"), a("Personal", "personal")], "personal",
         [{"do": "remove", "account": "Personal"}, {"do": "add", "account": "Side", "tag": "personal"},
          {"do": "choose", "filter": "personal"}]),
        ("a choice that still has accounts survives other changes",
         [a("Teams", "work"), a("Atlas", "work"), a("Personal", "personal")], "work",
         [{"do": "tag", "account": "Personal", "tag": "work"}, {"do": "add", "account": "Side", "tag": "personal"},
          {"do": "tag", "account": "Atlas", "tag": "personal"}]),
        ("an unknown stored value is replaced by All", sample, "family", [{"do": "open"}]),
        ("upgrade: nothing stored stays nothing stored until a segment is chosen; Work cannot be chosen yet",
         [a("Teams", None), a("Lab", None)], None,
         [{"do": "open"}, {"do": "choose", "filter": "personal"}, {"do": "tag", "account": "Lab", "tag": "work"},
          {"do": "choose", "filter": "work"}]),
        ("a second account added to a single-account install: the rule starts to run when the Summary appears",
         [a("Teams", "work")], "work", [{"do": "open"}, {"do": "add", "account": "Lab", "tag": "personal"}]),
    ]:
        memory.append({"name": name, "accounts": accts, "storedFilter": stored, "steps": steps,
                       "expected": run_pw_memory(accts, stored, steps)})
    return {"_meta": {"note": PW_NOTE}, "filter": cases, "memory": memory}


def dump(obj):
    return json.dumps(obj, ensure_ascii=False, indent=2) + "\n"


def self_check():
    """The unchanged rules must reproduce printed.json's shipped cases before the new ones are trusted."""
    old = json.load(open(os.path.join(ROOT, "spec/core/fixtures/pace-distance/printed.json"), encoding="utf-8"))
    for c in old["endToEnd"]["cases"]:
        e = elapsed_pct(c["nowMs"], c["resetMs"], c["windowLengthMs"])
        mt = mark_time(c["usedPct"], c["nowMs"], c["resetMs"], c["windowLengthMs"])
        assert figure(c["usedPct"], e) == c["figure"], c["name"]
        side, dur = mt
        shipped = f"{floor_text(side, dur)}{DOT}{day_clock(utc_local(c['nowMs']), utc_local(c['resetMs']), False)}"
        assert shipped == c["line"], (c["name"], shipped)
    for c in old["dayClock"]:
        assert day_clock(local(c["nowLocal"]), local(c["resetLocal"]), c["hour24"]) == c["text"], c["name"]
    for c in old["duration"]:
        assert duration(c["ms"]) == c["text"], c


def wireframe_examples(core, foot, tags):
    """The wireframe's worked examples, as printed in rev B, must come out of the rules."""
    texts = set()
    for c in core["paceLine"]:
        texts.add(c["text"])
    for c in core["paceLineForms"]:
        texts.update(c["forms"])
    want = ["6d 23h behind · resets Wed 12:30 PM", "13h 59m ahead · resets Thu 6:15 PM",
            "On the mark · resets Sat 8:00 AM", "5h 45m behind · resets Sun 9:30 PM",
            "5h 45m behind · Sun"]
    for w in want:
        assert w in texts, w
    got = {n["spot"] if n["spot"] != "leader" else f"leader {n['leader']['distance']}"
           for n in core["mapLabels"]["placement"][0]["expected"]["names"]}
    assert got == {"right", "below", "left", "aboveLeft", "leader 24", "leader 36", "leader 48", "none"}, got
    refused = {r for n in core["mapLabels"]["placement"][0]["expected"]["names"] for r in n["refusedFor"]}
    assert {"path crosses a placed name", "path crosses a dot"} <= refused
    labels = {f["label"] for f in core["mapLabels"]["figures"]}
    for w in ["Teams 52%", "Teams 48% left", f"Teams {MINUS}13%"]:
        assert w in labels, w
    fcs = {c["text"] for c in core["callout"]["forecast"]}
    assert "~74% at reset" in fcs and "100% at Wed 6:10 PM" in fcs
    assert "2 at 0% · 1 no reading" in {c["expected"]["count"] for c in foot["footLine"]}
    g = core["dayGrid"]
    assert g[0]["expected"]["startLabel"] == "4 Nov" and g[0]["expected"]["endLabel"] == "11 Nov"
    by = {c["name"].split(":")[0]: c["expected"] for c in g}
    w7, w5 = by["7-day, the wireframe's first drawing"], by["5h, the wireframe's drawing"]
    assert w7["nowColumn"] == 3 and not w7["ordinals"][1]["drawn"]  # "3" gives way to "now"
    assert w5["nowColumn"] == 3 and not w5["ordinals"][1]["drawn"]  # 5h: "3" gives way
    work = [c for c in tags["filter"] if c["name"].startswith("the sample")][0]
    assert work["expected"]["shown"] == ["Teams"]


def main():
    self_check()
    core = build_core()
    foot = build_map_foot()
    tags = build_tags()
    tags_pw = build_tags_pw()
    wireframe_examples(core, foot, tags)
    outs = {
        "spec/core/fixtures/pace-distance/printed-v20.json": dump(core),
        "spec/mobile/fixtures/map-foot/cases.json": dump(foot),
        "spec/mobile/fixtures/tags/cases.json": dump(tags),
        "spec/mobile/fixtures/tags/cases-personal-work.json": dump(tags_pw),
    }
    check = "--check" in sys.argv
    bad = []
    for rel, text in outs.items():
        p = os.path.join(ROOT, rel)
        if check:
            cur = open(p, encoding="utf-8").read() if os.path.exists(p) else None
            if cur != text:
                bad.append(rel)
        else:
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "w", encoding="utf-8").write(text)
    if bad:
        print("out of date: " + ", ".join(bad), file=sys.stderr)
        sys.exit(1)
    print("checked" if check else "wrote", ", ".join(outs))


if __name__ == "__main__":
    main()
