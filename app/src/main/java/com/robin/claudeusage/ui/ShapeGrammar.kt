package com.robin.claudeusage.ui

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.robin.claudeusage.data.Pace
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * CCRM-103 (Hybrid Notification), the shape grammar (`design/2026-11-02-shape-grammar.html`
 * rev D, frozen 2026-11-02): the 5h is always a hollow ring, the 7-day windows always bars.
 * One painter for every surface — the app tile (Compose, through `nativeCanvas`), the
 * notification and the widgets (bitmaps) — so they can never disagree. Every number below
 * is the page's `ring()`, `bar()`, `gauge()` and `barGlyph()`, in dp.
 *
 * Text is never baked in: the ring's figure is a real `Text`/`TextView` centred over the
 * bitmap, sized by [GrammarRing.figureDp] and fitted by [GrammarRing.fitFigure].
 */
object Grammar {

    /** The figure's "%" at 58% of the digits' size, on the baseline (rev B §1b). */
    const val PCT_SCALE = 0.58f

    /** Tabular, lining digits (rev C §1c): every digit one height and one width. */
    const val FIGURE_FEATURES = "tnum, lnum"

    /** Vs pace green / red (rev D §7b); the red is the bar's own red slice. */
    fun vsUnder(dark: Boolean): Color = if (dark) Color(0xFF9CCC65) else Color(0xFF2E7D32)
    fun vsOver(dark: Boolean): Color = if (dark) Color(0xFFFF5252) else Color(0xFFC62828)

    /** The page's ink: the neutral the tick, track and grey ring are drawn in. */
    fun ink(dark: Boolean): Color = if (dark) Color(0xFFECECEC) else Color(0xFF1F1F1F)

    /**
     * A figure's colour in Vs pace: green under the mark, red over it, [onInk] on it, and
     * [usage] (its severity colour) when it fell back to the usage figure.
     */
    fun vsColor(tone: PaceText.VsTone, dark: Boolean, onInk: Color, usage: Color): Color = when (tone) {
        PaceText.VsTone.UNDER -> vsUnder(dark)
        PaceText.VsTone.OVER -> vsOver(dark)
        PaceText.VsTone.ON -> onInk
        PaceText.VsTone.USAGE -> usage
    }

    /** "34%" with its "%" at [PCT_SCALE], for a TextView or RemoteViews. Other text passes through. */
    fun smallPercent(figure: CharSequence): CharSequence {
        if (figure.length < 2 || figure.last() != '%' || !figure[figure.length - 2].isDigit()) return figure
        return SpannableString(figure).apply {
            setSpan(RelativeSizeSpan(PCT_SCALE), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * The usage end mark's colour (rev D §1e): [UsageEndMark.LINE] the fill's own colour;
     * [UsageEndMark.PACE] (E2b) ink inside the ±5 on-pace band, green under, red over; null
     * for [UsageEndMark.ROUND], which draws no line.
     */
    fun endMarkColor(mark: UsageEndMark, usedPct: Double?, elapsedPct: Double?, fill: Color, dark: Boolean): Color? =
        when (mark) {
            UsageEndMark.ROUND -> null
            UsageEndMark.LINE -> fill
            UsageEndMark.PACE -> when {
                usedPct == null || elapsedPct == null -> ink(dark)
                abs(Pace.pace(usedPct, elapsedPct)) <= 5.0 -> ink(dark)
                elapsedPct > usedPct -> vsUnder(dark)
                else -> vsOver(dark)
            }
        }
}

/**
 * The grammar's ring (the desktop's rev B proportions, mobile colours): centre-line radius
 * 0.39 S, band 0.13 S (0.10 S at 48 dp and under, so the figure has room), the pace tick
 * a radial line across the band, the red slice from the tick to the fill tip more than 3
 * points ahead, the 9% minimum fill, the dashed room from the fill to the tick at 28 dp
 * and up, the usage end mark, and on the open app tile the dotted projection arc.
 */
object GrammarRing {

    enum class State {
        /** A reading (or none yet: [GrammarRing.paint] with a null pct draws the track alone). */
        NORMAL,
        /** A spent week (CCRM-102 (Spent Week Gate)): the track alone, the figure dimmed. */
        GREY,
        /** A plan with no 5h (rev C §7): the track and a cross, no figure. */
        NOT_APPLICABLE,
    }

    private const val TRACK_A = 0.16f
    private const val GREY_TRACK_A = 0.22f
    private const val CROSS_A = 0.45f
    private const val TICK_A = 0.90f
    private const val ROOM_A = 0.60f
    private const val PROJ_A = 0.75f
    private const val MIN_FILL = 0.09f

    /** The band at [sizeDp], in dp. */
    fun bandDp(sizeDp: Float): Float = max(3f, (if (sizeDp <= 48f) 0.10f else 0.13f) * sizeDp)

    /** The bore's radius (the band's inner edge), in dp. */
    fun boreDp(sizeDp: Float): Float = 0.39f * sizeDp - bandDp(sizeDp) / 2f

    /** The figure's size before fitting: 0.25 S (0.29 S at 48 and under), four characters 0.21 S (0.24 S). */
    fun figureDp(sizeDp: Float, text: String): Float {
        val small = sizeDp <= 48f
        return sizeDp * if (text.length >= 4) (if (small) 0.24f else 0.21f) else (if (small) 0.29f else 0.25f)
    }

    /**
     * Shrinks [figureDp] until the text's box (its measured [textWidthDp] at that size, and
     * 0.72 of the size tall) clears the band's inner edge by 1.5 dp.
     */
    fun fitFigure(sizeDp: Float, figureDp: Float, textWidthDp: Float): Float {
        val ri = boreDp(sizeDp)
        val d = hypot(textWidthDp / 2f, 0.36f * figureDp)
        return if (d > ri - 1.5f) figureDp * (ri - 1.5f) / d else figureDp
    }

    /** Whether the figure takes the severity colour (above 90) rather than the ink. */
    fun loud(pct: Double?): Boolean = pct != null && pct > 90.0

    /**
     * Paints the ring into the [sizePx] square at ([left], [top]) of [canvas].
     *
     * @param accent the account colour; the fill is its severity step ([Palette.barColor]).
     * @param surface the surface colour, for the end mark's gap; null clears it instead.
     * @param projectionPct the open tile's dotted arc to the projected end, or null.
     */
    fun paint(
        canvas: Canvas,
        left: Float,
        top: Float,
        sizePx: Float,
        density: Float,
        pct: Double?,
        elapsed: Double?,
        accent: Color,
        dark: Boolean,
        state: State = State.NORMAL,
        endMark: UsageEndMark = UsageEndMark.DEFAULT,
        showOverPace: Boolean = true,
        surface: Color? = null,
        projectionPct: Double? = null,
        room: Boolean = true,
    ) {
        val sDp = sizePx / density
        val sw = bandDp(sDp) * density
        val r = 0.39f * sizePx
        val cx = left + sizePx / 2f
        val cy = top + sizePx / 2f
        val ink = Grammar.ink(dark)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val box = RectF(cx - r, cy - r, cx + r, cy + r)

        p.strokeWidth = sw
        p.color = ink.copy(alpha = if (state == State.NORMAL) TRACK_A else GREY_TRACK_A).toArgb()
        canvas.drawCircle(cx, cy, r, p)

        if (state == State.NOT_APPLICABLE) {
            val k = 0.12f * sizePx
            p.strokeWidth = max(1.5f, sDp / 28f) * density
            p.strokeCap = Paint.Cap.ROUND
            p.color = ink.copy(alpha = CROSS_A).toArgb()
            canvas.drawLine(cx - k, cy - k, cx + k, cy + k, p)
            canvas.drawLine(cx + k, cy - k, cx - k, cy + k, p)
            return
        }
        if (state == State.GREY || pct == null) return

        val f = if (pct <= 0.0) 0f else max(min(pct, 100.0).toFloat() / 100f, MIN_FILL)
        val g = elapsed?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
        val fill = Palette.barColor(pct, accent, dark)
        val red = showOverPace && g != null && elapsed != null && pct > elapsed + 3.0
        val capRound = endMark == UsageEndMark.ROUND && !red

        // fill
        if (f > 0f) {
            p.strokeWidth = sw
            p.color = fill.toArgb()
            p.strokeCap = if (capRound) Paint.Cap.ROUND else Paint.Cap.BUTT
            canvas.drawArc(box, -90f, min(f, 0.9999f) * 360f, false, p)
        }
        // the dashed room, from the fill's visible end to the tick; dropped under 28 dp
        if (room && g != null && elapsed != null && pct < elapsed && sDp >= 28f) {
            val a0 = f + if (capRound && f > 0f) (sw / 2f / (2f * PI.toFloat() * r)) else 0f
            if (g - a0 > 0.01f) {
                val lw = max(1f, sDp / 60f) * density
                val ro = r + sw / 2f - lw / 2f
                val rn = r - sw / 2f + lw / 2f
                val dash = max(1.6f, sDp / 26f) * density
                val path = Path().apply {
                    arcTo(RectF(cx - ro, cy - ro, cx + ro, cy + ro), -90f + a0 * 360f, (g - a0) * 360f, true)
                    lineTo(cx + rn * sin(2 * PI * g).toFloat(), cy - rn * cos(2 * PI * g).toFloat())
                    arcTo(RectF(cx - rn, cy - rn, cx + rn, cy + rn), -90f + g * 360f, -(g - a0) * 360f, false)
                }
                canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = lw
                    color = accent.copy(alpha = ROOM_A).toArgb()
                    pathEffect = DashPathEffect(floatArrayOf(dash, dash * 0.75f), 0f)
                })
            }
        }
        // the projection: a dotted arc to where the window is heading (the open app tile only)
        if (projectionPct != null && projectionPct / 100.0 > f) {
            val a = f + 0.03f
            val b = min(projectionPct, 100.0).toFloat() / 100f
            if (b > a) {
                canvas.drawArc(box, -90f + a * 360f, (b - a) * 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = sw * 0.4f
                    strokeCap = Paint.Cap.ROUND
                    color = fill.copy(alpha = PROJ_A).toArgb()
                    pathEffect = DashPathEffect(floatArrayOf(0.1f, sw * 0.8f), 0f)
                })
            }
        }
        // the red slice, from the tick to the fill tip
        if (red && g != null) {
            p.strokeWidth = sw
            p.strokeCap = Paint.Cap.BUTT
            p.color = Palette.barColor(100.0, accent, dark).toArgb()
            canvas.drawArc(box, -90f + g * 360f, (min(f, 0.9999f) - g) * 360f, false, p)
        }
        // the usage end mark: a short radial line, 0.35 of the band past each edge, with a gap
        val endColor = Grammar.endMarkColor(endMark, pct, elapsed, fill, dark)
        if (endColor != null && f > 0f) {
            val a = 2.0 * PI * f
            val e0 = r - sw * 0.85f
            val e1 = r + sw * 0.85f
            val lw = max(1.3f, sDp / 40f) * density
            val x0 = cx + e0 * sin(a).toFloat(); val y0 = cy - e0 * cos(a).toFloat()
            val x1 = cx + e1 * sin(a).toFloat(); val y1 = cy - e1 * cos(a).toFloat()
            canvas.drawLine(x0, y0, x1, y1, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = lw + 2f * density
                if (surface != null) color = surface.toArgb()
                else xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            })
            canvas.drawLine(x0, y0, x1, y1, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = lw
                strokeCap = Paint.Cap.ROUND
                color = endColor.toArgb()
            })
        }
        // the pace tick, across the band
        if (g != null && pct > 0.0) {
            val a = 2.0 * PI * g
            val r0 = r - sw
            val r1 = r + sw
            p.strokeWidth = max(1.5f, sDp / 32f) * density
            p.strokeCap = Paint.Cap.ROUND
            p.color = ink.copy(alpha = TICK_A).toArgb()
            canvas.drawLine(
                cx + r0 * sin(a).toFloat(), cy - r0 * cos(a).toFloat(),
                cx + r1 * sin(a).toFloat(), cy - r1 * cos(a).toFloat(), p,
            )
        }
    }
}

/** The grammar's bar additions (rev D §1e): the square fill end and the usage end mark. */
object GrammarBar {

    /**
     * The end mark at [x], spanning the bar ([top]..[bottom]) and 1.5 dp past each edge,
     * 1.5 dp wide, over a 1 dp gap in [surface] (cleared when null).
     */
    fun paintEndMark(canvas: Canvas, x: Float, top: Float, bottom: Float, density: Float, color: Color, surface: Color?) {
        val w = 1.5f * density
        val over = 1.5f * density
        val gap = 1f * density
        canvas.drawRect(
            RectF(x - w / 2f - gap, top - over - gap, x + w / 2f + gap, bottom + over + gap),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (surface != null) this.color = surface.toArgb()
                else xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            },
        )
        canvas.drawRoundRect(
            RectF(x - w / 2f, top - over, x + w / 2f, bottom + over), w / 2f, w / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() },
        )
    }
}
