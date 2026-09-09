package com.robin.claudeusage.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.TypedValue
import kotlin.math.cos
import kotlin.math.sin

/**
 * The small usage icon: the status-bar ring gauge.
 *
 * Since CCRM-51 (Rails Gauge) it draws the Mac's **Rails** instrument (their
 * CCM-59/CCM-60 [Menu Bar]): a hairline tracing the window's extent, usage
 * measured against it, and a **clock-hand needle** at the pace position. The eye
 * reads "how much" and "how far ahead" as lengths rather than as a filled band.
 *
 * The status bar reproduces a bitmap's colours exactly, so [draw]'s `fillArgb`
 * carries the resolved severity colour straight through — pass
 * `Palette.barColor(...)` so the glyph and the notification's own number can
 * never disagree.
 *
 * **Size is the other hard fact.** The bitmap is 24 dp but the status bar fits it
 * into a ~15 dp slot *by width*, so it lands around 14 dp — 37 px on a Fold 7. Every
 * number below was chosen against that rasterised size, not against a zoomed vector;
 * see `design/rails-gauge-wireframe.html`, which mocks at 14.1 dp and rasterises to
 * the real 37 px. That discipline is why CCRM-49 exists and CCRM-48 (Status-Bar
 * Gauge) shipped unreadable.
 */
object UsageIcon {

    /** The one style offered in settings. */
    const val RING = "ring"

    // ---- Rails geometry, in 24ths of the icon box -------------------------------
    // Every value is a ratio of the *gauge diameter* (22.4), so it ports from the
    // Mac's 15 pt reference without argument: their band ratio is 0.167, ours 0.179.

    private const val BAND_R = 9.2f          // band-centre radius
    private const val BAND_W = 4f            // band / usage-arc stroke
    private const val RIM = 11.2f            // the needle's outer edge
    private const val HAIR_W = 1.493f        // 1/15 of Ø
    private const val POST_W = 1.493f
    private const val POST_IN = 6.72f        // BAND_R - BAND_W/2 - 0.12*BAND_W
    private const val POST_OUT = 11.68f
    private const val NEEDLE_W = 1.792f      // Mac J2: 1.2 pt
    private const val NEEDLE_HALO = 4.48f    // Mac J2: 3 pt cleared
    private const val HUB_R = 3.75f          // the weekly dot — the needle's pin
    private const val HUB_FALLBACK_R = 1.344f // Mac J2's own hub, when there is no dot
    private const val EMPTY_RING_R = 3.10f   // the "nothing used" rung, drawn open
    private const val EMPTY_RING_W = 1.3f

    // ---- Alphas. Time is neutral; only usage carries colour. --------------------
    private const val HAIR_A = 0.50f         // lifted from the Mac's 35% for 37 px
    private const val POST_A = 0.70f
    private const val NEEDLE_A = 0.85f       // Mac J2
    private const val EMPTY_A = 0.65f

    /** Below this, a non-zero fill would vanish; 1% still has to read as "started". */
    private const val MIN_SWEEP = 0.09f

    /**
     * [sessionElapsed] positions the needle. [fillArgb] is the resolved severity
     * colour — pass `Palette.barColor(...)` so the glyph and the notification's own
     * number can never disagree. [weeklyPct]/[weeklyElapsed] drive the 7-day flag dot in
     * the hub ([RingGeometry.weeklyFlag]). [showOverPace] is the surface's "show red
     * past the pace mark" toggle; it gates the red slice only, never the needle.
     */
    fun draw(
        context: Context,
        pct: Double?,
        left: Boolean = false,
        sessionElapsed: Double? = null,
        fillArgb: Int? = null,
        dark: Boolean = true,
        weeklyPct: Double? = null,
        weeklyElapsed: Double? = null,
        showOverPace: Boolean = true,
    ): Bitmap {
        val size = dp(context, 24f).toInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        railsGauge(c, size, pct, sessionElapsed, fillArgb, dark, weeklyPct, weeklyElapsed, showOverPace)
        return bmp
    }

    /**
     * The rails gauge — a ring band with a hairline track.
     *
     * Draw order matters and is the whole contract: **extent · usage · red slice ·
     * hub · needle · spent post**. The needle goes near-last so it survives whatever
     * it crosses, and the spent post goes *last* so that when the needle happens to
     * land at 12 o'clock (a window burned almost instantly, or one about to reset)
     * the "spent" cue still wins the overlap. At 100% used the pace verdict is moot
     * anyway.
     *
     * The honesty gates, all shared with every other pace surface:
     *  - no reading → the extent alone. **Never 0%.**
     *  - **no usage → no needle**, even with a known clock. A mark on an unused gauge
     *    measures nothing and reads as "just opened" or "about to reset".
     *  - no reset clock → no needle and no red. Never a guessed position.
     * The first two render byte-identically, which is the point.
     */
    private fun railsGauge(
        c: Canvas,
        size: Int,
        pct: Double?,
        elapsed: Double?,
        fillArgb: Int?,
        dark: Boolean,
        weeklyPct: Double?,
        weeklyElapsed: Double?,
        showOverPace: Boolean,
    ) {
        val u = size / 24f
        val cx = size / 2f

        // 1 · the extent: a hairline circle — this is what keeps the instrument a
        // shape when there is no reading.
        c.drawCircle(cx, cx, BAND_R * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = HAIR_W * u
            color = ink(dark, HAIR_A)
        })

        // 2 · usage, and 3 · the red slice past the needle.
        //
        // CCRM-51 review: the red slice wins over the severity fill — deliberately the
        // opposite of the Mac's "never two alarms on one gauge". Nothing suppresses it
        // above 80, which is also why RingRenderer/BarRenderer needed no change: they
        // already behaved this way.
        val red = RingGeometry.redSegment(pct, elapsed, showOverPace)
        if (pct != null) {
            val fraction = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
            val sweep = if (fraction > 0f) fraction.coerceAtLeast(MIN_SWEEP) * 360f else 0f
            if (sweep > 0f) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fillArgb ?: Color.WHITE
                    style = Paint.Style.STROKE; strokeWidth = BAND_W * u
                    // A round cap would poke past the red's flat tip, so the flat
                    // tip only reads as flat if the fill under it is butt-capped.
                    strokeCap = if (red != null) Paint.Cap.BUTT else Paint.Cap.ROUND
                }
                val box = RectF(cx - BAND_R * u, cx - BAND_R * u, cx + BAND_R * u, cx + BAND_R * u)
                if (sweep >= 360f) {
                    c.drawArc(box, 0f, 360f, false, fill)
                } else {
                    c.drawArc(box, RingGeometry.START_ANGLE, sweep, false, fill)
                }
                if (red != null) {
                    val (start, len) = red
                    val end = (start + len).coerceAtMost(sweep)
                    if (end > start) {
                        val slice = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (dark) OVER_DARK else OVER_LIGHT
                            style = Paint.Style.STROKE; strokeWidth = BAND_W * u
                            strokeCap = Paint.Cap.BUTT
                        }
                        c.drawArc(
                            box, RingGeometry.START_ANGLE + start, end - start, false, slice,
                        )
                    }
                }
            }
        }

        // 4 · the hub. The 7-day window rides here as a state, not a level — a second
        // level was measured unreadable at this size (CCRM-49), and the ring's hollow is
        // empty space already paid for.
        //
        // The needle draws only with a reading, a clock, AND usage actually started —
        // the last gate is CCRM-51's honesty rule, and it is what makes "no usage" and
        // "no clock" render byte-identically.
        val needleDraws = RingGeometry.showTick(pct, elapsed) && (pct ?: 0.0) > 0.0
        val flag = RingGeometry.weeklyFlag(weeklyPct, weeklyElapsed)
        if (flag != null) {
            drawFlag(c, cx, u, flag, dark)
        } else if (needleDraws) {
            // No weekly reading, but the needle still draws — so it keeps Mac J2's own
            // small hub and does not float.
            c.drawCircle(cx, cx, HUB_FALLBACK_R * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink(dark, NEEDLE_A)
            })
        }
        val hubEdge = if (flag != null) HUB_R else HUB_FALLBACK_R

        // 5 · the needle: a clock hand pinned to whatever hub is there. The gauge starts
        // at 12 o'clock and sweeps clockwise, so a hand *is* the window's clock — which
        // is why this replaced the Mac's radial band tick, at 7.6 px the weakest mark on
        // the glyph.
        if (needleDraws) {
            radialMark(
                c, cx, u, RingGeometry.tickSweep(elapsed!!),
                rIn = hubEdge, rOut = RIM,
                // The halo starts clear of the hub: erasing there would bite a notch
                // out of the very dot the needle turns on.
                haloFrom = hubEdge + 0.7f, haloW = NEEDLE_HALO,
                lineW = NEEDLE_W, lineColor = ink(dark, NEEDLE_A), round = true,
            )
        }

        // 6 · the spent post. The 12 o'clock post exists *only* at a truncated 100 —
        // it is the "spent" marker and nothing else. Below 100 the gauge stays clean.
        // A post is a cleared halo *with* an ink line inside it, so both the gap and
        // the mark read clearly against any fill colour.
        if (pct != null && pct.toInt() >= 100) {
            radialMark(
                c, cx, u, 0f,
                rIn = POST_IN, rOut = POST_OUT,
                haloFrom = POST_IN, haloW = NEEDLE_HALO,
                lineW = POST_W, lineColor = ink(dark, POST_A), round = false,
            )
        }
    }

    /**
     * The 7-day rung, drawn. An escalation in one shape step then three colour steps:
     * **empty → grey → yellow → red**.
     *
     * `EMPTY` is an *outlined* dot, and that is load-bearing rather than decorative. A
     * filled black dot — the first proposal — fails: on a dark bar it reads as a hole
     * punched in the glyph. "You have used nothing" would render as "your week is
     * gone". An outline survives because it is a shape, not an alpha.
     */
    private fun drawFlag(
        c: Canvas, cx: Float, u: Float, flag: RingGeometry.WeeklyFlag, dark: Boolean,
    ) {
        if (flag == RingGeometry.WeeklyFlag.EMPTY) {
            c.drawCircle(cx, cx, EMPTY_RING_R * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = EMPTY_RING_W * u
                color = ink(dark, EMPTY_A)
            })
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (flag) {
                RingGeometry.WeeklyFlag.SPENT -> if (dark) OVER_DARK else OVER_LIGHT
                RingGeometry.WeeklyFlag.ABOVE -> if (dark) 0xFFFDD663.toInt() else 0xFFF9A825.toInt()
                // A step brighter than the hairline, so it reads as a mark not an artifact.
                else -> if (dark) 0xFFBDBDBD.toInt() else 0xFF757575.toInt()
            }
        }
        c.drawCircle(cx, cx, HUB_R * u, paint)
    }

    /** The severity ladder's top rung, shared with `Palette.barColor` and the slice. */
    private const val OVER_DARK = 0xFFFF5252.toInt()
    private const val OVER_LIGHT = 0xFFC62828.toInt()

    /**
     * Neutral ink at [alpha]. **Time has no severity** — the hairline, the needle and
     * the spent post are always the foreground colour at an alpha, never a ladder hue;
     * only usage carries colour. Keyed to the *bar's* theme rather than the app's, per
     * CCBG-13 (Light Status Bar).
     */
    private fun ink(dark: Boolean, alpha: Float): Int {
        val a = (alpha * 255f).toInt().coerceIn(0, 255)
        return if (dark) Color.argb(a, 255, 255, 255) else Color.argb(a, 17, 17, 18)
    }

    /**
     * One radial mark at [sweepDeg] past 12 o'clock, clockwise, sitting in a **cleared
     * halo**: the halo is erased first (PorterDuff.CLEAR, real transparency) and the
     * line drawn inside it. Those erased margins are what keep the mark legible over
     * any fill colour, and what leave a visible gap even on a surface that flattens
     * the bitmap to one tint.
     *
     * [haloFrom] lets the halo start further out than the line, so a hub-pinned needle
     * needs no clearance where it crosses its own pin.
     */
    private fun radialMark(
        c: Canvas, cx: Float, u: Float, sweepDeg: Float,
        rIn: Float, rOut: Float, haloFrom: Float, haloW: Float,
        lineW: Float, lineColor: Int, round: Boolean,
    ) {
        val rad = Math.toRadians((sweepDeg - 90f).toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        c.drawLine(
            cx + haloFrom * u * dx, cx + haloFrom * u * dy,
            cx + rOut * u * dx, cx + rOut * u * dy,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = haloW * u
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            },
        )
        c.drawLine(
            cx + rIn * u * dx, cx + rIn * u * dy,
            cx + rOut * u * dx, cx + rOut * u * dy,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = lineW * u
                strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
                color = lineColor
            },
        )
    }

    private fun dp(context: Context, value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
        )
}
