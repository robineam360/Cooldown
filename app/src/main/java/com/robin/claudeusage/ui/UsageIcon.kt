package com.robin.claudeusage.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.TypedValue

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

    private fun dp(context: Context, value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
        )
}
