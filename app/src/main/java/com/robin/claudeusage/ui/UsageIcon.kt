package com.robin.claudeusage.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import com.robin.claudeusage.data.StatusGlyph
import kotlin.math.max
import kotlin.math.min

/**
 * The small usage icon: the status-bar ring gauge.
 *
 * Since CCRM-51 (Rails Gauge) it draws the Mac's **Rails** instrument (their
 * CCM-59/CCM-60 [Menu Bar]): a hairline tracing the window's extent, usage
 * measured against it, and a **clock-hand needle** at the pace position. The eye
 * reads "how much" and "how far ahead" as lengths rather than as a filled band.
 *
 * It shows **one window — the 5h one — and nothing else** (CCRM-62 (Duet Notification)).
 * Which account's 5h window is the "Status-bar ring shows" setting's answer, resolved by
 * [com.robin.claudeusage.notify.Duet.ringAccount]; the *colour* is how the glyph says
 * whose it is, so identity and severity share one hue and identity yields above 80%.
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

    // The geometry, the draw order and the honesty gates moved to [RingRenderer]
    // (CCRM-83 (Ring Renderer)), which the widgets and the share card share; this glyph
    // is its 24 dp NEEDLE call and must stay byte-identical to what it drew before.

    /**
     * [sessionElapsed] positions the needle. [fillArgb] is the resolved severity
     * colour — pass `Palette.barColor(...)` so the glyph and the notification's own
     * number can never disagree. Since CCRM-62 (Duet Notification) that colour is the
     * *account's* identity too: below 80% it is the shown account's own accent, and only
     * above it does the severity ladder take the hue back. [showOverPace] is the
     * surface's "show red past the pace mark" toggle; it gates the red slice only, never
     * the needle.
     *
     * There is no weekly reading on this glyph any more. The 7-day window used to ride
     * in the needle's hub as a flag dot; CCRM-62 dropped it, because with two accounts
     * the hue had to become the account identity and the hollow was the only interior
     * room the ring had left (CCRM-49 (Glyph Legibility) measured a two-ring "Twin" at
     * this size and could not read it). What that buys is legibility, not a second
     * reading. CCRM-50 (Weekly Flag) was the dot and nothing else, so it goes with it.
     */
    fun draw(
        context: Context,
        pct: Double?,
        left: Boolean = false,
        sessionElapsed: Double? = null,
        fillArgb: Int? = null,
        dark: Boolean = true,
        showOverPace: Boolean = true,
    ): Bitmap {
        val size = dp(context, 24f).toInt().coerceAtLeast(24)
        return RingRenderer.draw(
            context, size, RingRenderer.ICON_BAND_W * (size / 24f), pct, sessionElapsed,
            fillArgb, dark, showOverPace, spentCross = true,
            mark = RingRenderer.PaceMark.NEEDLE,
        )
    }

    /**
     * CCRM-103 (Hybrid Notification), rev D §4: the glyph [StatusIconRule] picked. [RING]
     * is [draw] unchanged; the other three are drawn here in the same 24-unit box (the
     * page's `gauge()` greyed, its red cross and `barGlyph()`).
     *
     * @param pct the drawn window's percent: the 5h for the rings, the Weekly for the bar.
     * @param elapsed that window's elapsed percent.
     */
    fun drawGlyph(
        context: Context,
        glyph: StatusGlyph,
        pct: Double?,
        left: Boolean,
        elapsed: Double?,
        fillArgb: Int?,
        dark: Boolean,
        showOverPace: Boolean,
    ): Bitmap {
        if (glyph == StatusGlyph.RING) return draw(context, pct, left, elapsed, fillArgb, dark, showOverPace)
        val size = dp(context, 24f).toInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val u = size / 24f
        val cx = size / 2f
        val ink = if (dark) Color.WHITE else Color.BLACK
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (glyph) {
            StatusGlyph.GREYED_RING -> {
                // The 5h's arc at 40% ink and no needle: it cannot be used until the week resets.
                val r = 9.2f * u
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.493f * u
                p.color = withAlpha(ink, 0.30f)
                c.drawCircle(cx, cx, r, p)
                p.strokeWidth = 4f * u
                p.color = withAlpha(ink, 0.40f)
                val f = max(((pct ?: 0.0) / 100.0).toFloat().coerceAtMost(1f), 0.09f)
                c.drawArc(RectF(cx - r, cx - r, cx + r, cx + r), -90f, f * 360f, false, p)
            }
            StatusGlyph.CROSS -> {
                // Out right now: a red cross alone, no circle (rev C §4, C7, C8).
                val a = 7f * u
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3.4f * u
                p.strokeCap = Paint.Cap.ROUND
                p.color = Palette.barColor(100.0, androidx.compose.ui.graphics.Color.Red, dark).toArgb()
                c.drawLine(cx - a, cx - a, cx + a, cx + a, p)
                c.drawLine(cx + a, cx - a, cx - a, cx + a, p)
            }
            StatusGlyph.WEEKLY_BAR -> drawBarGlyph(c, u, pct, elapsed, fillArgb, ink, dark, showOverPace)
            StatusGlyph.RING -> Unit
        }
        return bmp
    }

    /**
     * N3, A1's bar (rev D §4): the Weekly as a 21 × 10-unit capsule in the 24 box, its fill
     * clipped to the capsule, red past the tick, a hairline outline, and the pace tick in a
     * cleared halo so it reads against any status-bar colour.
     */
    private fun drawBarGlyph(
        c: Canvas, u: Float, pct: Double?, elapsed: Double?, fillArgb: Int?, ink: Int, dark: Boolean,
        showOverPace: Boolean,
    ) {
        val x0 = 1.5f * u
        val w = 21f * u
        val y0 = 7f * u
        val h = 10f * u
        val rr = h / 2f
        val capsule = RectF(x0, y0, x0 + w, y0 + h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = withAlpha(ink, 0.16f)
        c.drawRoundRect(capsule, rr, rr, p)
        if (pct != null) {
            val fw = w * max(min(pct, 100.0), if (pct > 0.0) 9.0 else 0.0).toFloat() / 100f
            c.save()
            c.clipPath(Path().apply { addRoundRect(capsule, rr, rr, Path.Direction.CW) })
            p.color = fillArgb ?: ink
            c.drawRect(x0, y0, x0 + fw, y0 + h, p)
            if (showOverPace && elapsed != null && pct > elapsed + 3.0) {
                val tx = w * (elapsed / 100.0).toFloat()
                p.color = Palette.barColor(100.0, androidx.compose.ui.graphics.Color.Red, dark).toArgb()
                c.drawRect(x0 + tx, y0, x0 + fw, y0 + h, p)
            }
            c.restore()
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * u
        p.color = withAlpha(ink, 0.50f)
        c.drawRoundRect(RectF(x0 + 0.6f * u, y0 + 0.6f * u, x0 + w - 0.6f * u, y0 + h - 0.6f * u), rr - 0.6f * u, rr - 0.6f * u, p)
        if (pct != null && pct > 0.0 && elapsed != null) {
            val tx = x0 + w * (elapsed / 100.0).toFloat().coerceIn(0f, 1f)
            c.drawLine(tx, y0 - 3.4f * u, tx, y0 + h + 3.4f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 4.2f * u
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            })
            c.drawLine(tx, y0 - 3f * u, tx, y0 + h + 3f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 1.8f * u
                strokeCap = Paint.Cap.ROUND
                color = withAlpha(ink, 0.95f)
            })
        }
    }

    private fun withAlpha(argb: Int, a: Float): Int = Color.argb((a * 255).toInt(), Color.red(argb), Color.green(argb), Color.blue(argb))

    private fun dp(context: Context, value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
        )
}
