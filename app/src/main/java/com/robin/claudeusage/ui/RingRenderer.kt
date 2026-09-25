package com.robin.claudeusage.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.TypedValue
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The one ring painter (CCRM-83 (Ring Renderer)): the status-bar glyph, the v1.8 widget
 * faces and the share card all draw the rails gauge through here, so the three can never
 * disagree about what a reading looks like.
 *
 * It generalises what used to be `UsageIcon.railsGauge`, and it has two pace marks:
 *
 *  - [PaceMark.NEEDLE] — the status-bar icon's clock hand pinned to a hub. Its geometry is
 *    the icon's own, in 24ths of the box, **moved here unchanged**: [UsageIcon.draw] calls
 *    this at 24 dp and must stay byte-identical to what it drew before (the check in
 *    `RingRendererTest`). Nothing about it scales except through the box.
 *  - [PaceMark.TICK] — every ring with a figure in its bore (widgets, share card), wireframe
 *    rev D: no hub, no needle, a straight round-capped tick across the stroke at 90% ink with
 *    a 1 dp halo in the face colour, overhanging the stroke equally inside and out, so the
 *    pace position never runs through the text. The usage band has round ends.
 *
 * The honesty gates are shared by both: no reading → the extent alone, never 0%; no usage →
 * no pace mark, even with a known clock; no reset clock → no mark and no red. Text is never
 * baked in — the figure in the bore is a real `TextView` at true sp.
 */
object RingRenderer {

    enum class PaceMark { NEEDLE, TICK }

    // ---- NEEDLE: the status-bar glyph, in 24ths of the icon box ---------------------
    // Every value is a ratio of the *gauge diameter* (22.4), so it ports from the Mac's
    // 15 pt reference without argument (CCRM-51 (Rails Gauge)); see UsageIcon's header
    // for why each was chosen against the rasterised 37 px, not a zoomed vector.

    private const val BAND_R = 9.2f          // band-centre radius
    private const val RIM = 11.2f            // the needle's outer edge
    private const val HAIR_W = 1.493f        // 1/15 of Ø
    private const val CROSS_ARM = 4.2f       // half-extent of the spent ×, inside the 7.2 hollow
    private const val CROSS_W = 2.0f
    private const val NEEDLE_W = 1.792f      // Mac J2: 1.2 pt
    private const val NEEDLE_HALO = 4.48f    // Mac J2: 3 pt cleared
    private const val HUB_FALLBACK_R = 1.344f

    /** The icon's band stroke in 24ths; [UsageIcon.draw] passes `BAND_W * size / 24`. */
    const val ICON_BAND_W = 4f

    // ---- Alphas. Time is neutral; only usage carries colour. ------------------------
    private const val HAIR_A = 0.50f         // lifted from the Mac's 35% for 37 px
    private const val CROSS_A = 0.90f
    private const val NEEDLE_A = 0.85f       // Mac J2
    private const val TICK_A = 0.90f         // rev C: "more visible than just a thin line"

    /** Below this, a non-zero fill would vanish; 1% still has to read as "started". */
    private const val MIN_SWEEP = 0.09f

    // ---- TICK: the widget ring, from the approved wireframe (rev D) ------------------
    /** 3.5 dp on the 9 dp stroke, never under 2.5 dp. */
    private const val TICK_W_RATIO = 0.39f
    private const val TICK_W_MIN_DP = 2.5f
    /** How far the tick passes *each* stroke edge: the bar tick's 3.5 of 12, floored. */
    private const val TICK_OVER_RATIO = 3.5f / 12f
    private const val TICK_OVER_MIN_DP = 2.5f
    private const val TICK_HALO_DP = 1f
    private const val HAIR_RATIO = 0.26f
    private const val HAIR_MIN_DP = 1.1f
    private const val X_ARM_RATIO = 0.32f
    private const val X_W_RATIO = 0.22f
    private const val X_W_MIN_DP = 1.4f
    /** Transparent faces (rev D): a soft shadow in the opposite tone under the ring. */
    private const val SHADOW_BLUR_DP = 2f

    /**
     * Draws the gauge to a fresh [sizePx]² bitmap.
     *
     * @param strokePx the usage band's width. For [PaceMark.NEEDLE] the rest of the
     *   geometry is the icon's own ratios of [sizePx]; for [PaceMark.TICK] the ring is
     *   inset so the tick's overhang, its round caps and its halo never clip.
     * @param pct null → the extent alone (no reading) — never a fake 0%.
     * @param elapsed the window's elapsed percent; null → no pace mark and no red.
     * @param fillArgb the resolved severity colour — pass `Palette.barColor(...)` so the
     *   ring and the figure beside it can never disagree.
     * @param showOverPace the "Show red past the pace mark" toggle; it gates the red
     *   slice only, never the mark.
     * @param spentCross at a truncated 100, an × in the hollow. The status bar always
     *   draws it; a widget passes it when the × replaces the figure (CCRM-79 (Ring Face)).
     * @param haloArgb [PaceMark.TICK] only: the face colour the tick's halo is drawn in.
     *   Null clears it to real transparency instead (a Transparent face has no colour).
     * @param shadowArgb [PaceMark.TICK] only: a soft shadow under everything drawn, for
     *   a Transparent face over a wallpaper it does not match. Null → none.
     * @param density [PaceMark.TICK] only: px per dp for the tick's dp floors. Null → the
     *   display's; the share card passes 4 (CCRM-24 (Share Card)'s 4× render).
     */
    fun draw(
        context: Context,
        sizePx: Int,
        strokePx: Float,
        pct: Double?,
        elapsed: Double?,
        fillArgb: Int?,
        dark: Boolean,
        showOverPace: Boolean,
        spentCross: Boolean,
        mark: PaceMark = PaceMark.NEEDLE,
        haloArgb: Int? = null,
        shadowArgb: Int? = null,
        density: Float? = null,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        when (mark) {
            PaceMark.NEEDLE ->
                needleGauge(Canvas(bmp), sizePx, strokePx, pct, elapsed, fillArgb, dark, showOverPace, spentCross)
            PaceMark.TICK -> {
                val density = density ?: context.resources.displayMetrics.density
                tickGauge(
                    Canvas(bmp), sizePx, strokePx, density, pct, elapsed, fillArgb, dark,
                    showOverPace, spentCross, haloArgb,
                )
                if (shadowArgb != null) return withShadow(bmp, shadowArgb, density)
            }
        }
        return bmp
    }

    /**
     * The status-bar rails gauge. Draw order is the whole contract: **extent · usage ·
     * red slice · hub · needle · spent cross**. The needle goes near-last so it survives
     * whatever it crosses, and the cross goes *last*, in the hollow. Moved verbatim from
     * `UsageIcon.railsGauge`; the band's width is now the [strokePx] argument.
     */
    private fun needleGauge(
        c: Canvas,
        size: Int,
        strokePx: Float,
        pct: Double?,
        elapsed: Double?,
        fillArgb: Int?,
        dark: Boolean,
        showOverPace: Boolean,
        spentCross: Boolean,
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
        // above 80.
        val red = RingGeometry.redSegment(pct, elapsed, showOverPace)
        if (pct != null) {
            val sweep = sweepFor(pct)
            if (sweep > 0f) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fillArgb ?: Color.WHITE
                    style = Paint.Style.STROKE; strokeWidth = strokePx
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
                            color = over(dark)
                            style = Paint.Style.STROKE; strokeWidth = strokePx
                            strokeCap = Paint.Cap.BUTT
                        }
                        c.drawArc(
                            box, RingGeometry.START_ANGLE + start, end - start, false, slice,
                        )
                    }
                }
            }
        }

        // 4 · the hub — Mac J2's own small pin, drawn on exactly the needle's own
        // condition: with no needle the ring is the extent alone, and "no reading", "no
        // usage" and "no clock" all render byte-identically.
        //
        // The needle draws only with a reading, a clock, AND usage actually started —
        // the last gate is CCRM-51's honesty rule.
        val needleDraws = markDraws(pct, elapsed)
        if (needleDraws) {
            c.drawCircle(cx, cx, HUB_FALLBACK_R * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink(dark, NEEDLE_A)
            })
        }

        // 5 · the needle: a clock hand pinned to that hub. The gauge starts at 12 o'clock
        // and sweeps clockwise, so a hand *is* the window's clock.
        if (needleDraws) {
            radialMark(
                c, cx, u, RingGeometry.tickSweep(elapsed!!),
                rIn = HUB_FALLBACK_R, rOut = RIM,
                // The halo starts clear of the hub: erasing there would bite a notch
                // out of the very dot the needle turns on.
                haloFrom = HUB_FALLBACK_R + 0.7f, haloW = NEEDLE_HALO,
                lineW = NEEDLE_W, lineColor = ink(dark, NEEDLE_A), round = true,
            )
        }

        // 6 · the spent cross. At a truncated 100 the hollow carries an × in neutral ink
        // and nothing else changes (design/spent-ring-wireframe.html, option A).
        if (spentCross && pct != null && pct.toInt() >= 100) {
            val arm = CROSS_ARM * u
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink(dark, CROSS_A); strokeWidth = CROSS_W * u
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(cx - arm, cx - arm, cx + arm, cx + arm, paint)
            c.drawLine(cx + arm, cx - arm, cx - arm, cx + arm, paint)
        }
    }

    /**
     * The widget ring: **extent · usage · red slice · pace tick · spent cross**. Same
     * gates as the needle; the tick is the only difference in what it says.
     */
    private fun tickGauge(
        c: Canvas,
        size: Int,
        strokePx: Float,
        density: Float,
        pct: Double?,
        elapsed: Double?,
        fillArgb: Int?,
        dark: Boolean,
        showOverPace: Boolean,
        spentCross: Boolean,
        haloArgb: Int?,
    ) {
        val cx = size / 2f
        val tickW = tickWidth(strokePx, density)
        val halo = TICK_HALO_DP * density
        val r = radius(size, strokePx, density)
        val box = RectF(cx - r, cx - r, cx + r, cx + r)

        // 1 · the extent
        c.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(HAIR_MIN_DP * density, strokePx * HAIR_RATIO)
            color = ink(dark, HAIR_A)
        })

        // 2 · usage, round-ended (rev C); 3 · the red slice from the tick to the tip, its
        // leading end round too so the arc finishes as one soft cap. Its start sits under
        // the tick, so the tick is what the eye reads as the boundary.
        if (pct != null) {
            val sweep = sweepFor(pct)
            if (sweep > 0f) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fillArgb ?: Color.WHITE
                    style = Paint.Style.STROKE; strokeWidth = strokePx
                    strokeCap = Paint.Cap.ROUND
                }
                if (sweep >= 360f) c.drawArc(box, 0f, 360f, false, fill)
                else c.drawArc(box, RingGeometry.START_ANGLE, sweep, false, fill)
                RingGeometry.redSegment(pct, elapsed, showOverPace)?.let { (start, len) ->
                    val end = (start + len).coerceAtMost(sweep)
                    if (end > start) {
                        c.drawArc(
                            box, RingGeometry.START_ANGLE + start, end - start, false,
                            Paint(fill).apply { color = over(dark) },
                        )
                    }
                }
            }
        }

        // 4 · the pace tick: halo first (in the face colour, or cleared), then the ink.
        if (markDraws(pct, elapsed)) {
            val rad = Math.toRadians((RingGeometry.START_ANGLE + RingGeometry.tickSweep(elapsed!!)).toDouble())
            val half = strokePx / 2f + tickOverhang(strokePx, density)
            val dx = cos(rad).toFloat()
            val dy = sin(rad).toFloat()
            val x1 = cx + (r - half) * dx
            val y1 = cx + (r - half) * dy
            val x2 = cx + (r + half) * dx
            val y2 = cx + (r + half) * dy
            c.drawLine(x1, y1, x2, y2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = tickW + 2f * halo
                strokeCap = Paint.Cap.ROUND
                if (haloArgb != null) color = haloArgb
                else xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            })
            c.drawLine(x1, y1, x2, y2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = tickW
                strokeCap = Paint.Cap.ROUND
                color = ink(dark, TICK_A)
            })
        }

        // 5 · the spent cross, scaled to the bore
        if (spentCross && pct != null && pct.toInt() >= 100) {
            val arm = r * X_ARM_RATIO
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink(dark, CROSS_A)
                strokeWidth = max(X_W_MIN_DP * density, strokePx * X_W_RATIO)
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(cx - arm, cx - arm, cx + arm, cx + arm, paint)
            c.drawLine(cx + arm, cx - arm, cx - arm, cx + arm, paint)
        }
    }

    /**
     * The band-centre radius a [PaceMark.TICK] ring of [size] draws at: the tick's
     * overhang, its round cap and its halo all fit, plus one pixel for antialiasing.
     */
    fun radius(size: Int, strokePx: Float, density: Float): Float =
        size / 2f - strokePx / 2f - tickOverhang(strokePx, density) -
            tickWidth(strokePx, density) / 2f - TICK_HALO_DP * density - 1f

    private fun tickWidth(strokePx: Float, density: Float): Float =
        max(TICK_W_MIN_DP * density, strokePx * TICK_W_RATIO)

    private fun tickOverhang(strokePx: Float, density: Float): Float =
        max(TICK_OVER_MIN_DP * density, strokePx * TICK_OVER_RATIO)

    /** The mark draws only with a reading, a clock, and usage actually started. */
    private fun markDraws(pct: Double?, elapsed: Double?): Boolean =
        RingGeometry.showTick(pct, elapsed) && (pct ?: 0.0) > 0.0

    private fun sweepFor(pct: Double): Float {
        val fraction = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
        return if (fraction > 0f) fraction.coerceAtLeast(MIN_SWEEP) * 360f else 0f
    }

    /** A soft shadow of everything drawn, composited under it. */
    private fun withShadow(src: Bitmap, shadowArgb: Int, density: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val blur = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(SHADOW_BLUR_DP * density, BlurMaskFilter.Blur.NORMAL)
        }
        val offset = IntArray(2)
        val alpha = src.extractAlpha(blur, offset)
        c.drawBitmap(alpha, offset[0].toFloat(), offset[1].toFloat(), Paint().apply { color = shadowArgb })
        alpha.recycle()
        c.drawBitmap(src, 0f, 0f, null)
        src.recycle()
        return out
    }

    /** The severity ladder's top rung, shared with `Palette.barColor` and the slice. */
    private const val OVER_DARK = 0xFFFF5252.toInt()
    private const val OVER_LIGHT = 0xFFC62828.toInt()

    private fun over(dark: Boolean): Int = if (dark) OVER_DARK else OVER_LIGHT

    /**
     * Neutral ink at [alpha]. **Time has no severity** — the hairline, the marks and the
     * spent cross are always the foreground colour at an alpha, never a ladder hue; only
     * usage carries colour. Keyed to the *surface's* theme, per CCBG-13 (Light Status Bar).
     */
    private fun ink(dark: Boolean, alpha: Float): Int {
        val a = (alpha * 255f).toInt().coerceIn(0, 255)
        return if (dark) Color.argb(a, 255, 255, 255) else Color.argb(a, 17, 17, 18)
    }

    /**
     * One radial mark at [sweepDeg] past 12 o'clock, clockwise, sitting in a **cleared
     * halo**: the halo is erased first (PorterDuff.CLEAR, real transparency) and the line
     * drawn inside it, which keeps the mark legible over any fill colour and leaves a gap
     * even on a surface that flattens the bitmap to one tint.
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

    /** dp → px on [context]'s display, for callers sizing a ring. */
    fun dp(context: Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)
}
