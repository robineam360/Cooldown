package com.robin.claudeusage.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.abovePaceWash
import com.robin.claudeusage.ui.chartNowMs
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.SparkGeometry
import com.robin.claudeusage.ui.evenPacePercent
import java.time.Instant

/**
 * The trend chart drawn to a bitmap for the share card (CCRM-24 (Share Card)), adapted
 * from the v1.5 large-widget `ChartBitmap` (`git show 530781f:…/widget/ChartBitmap.kt`).
 *
 * As before, deliberately NOT a pixel-level extraction of `UsageSparkline` — that surface
 * is Compose-bound (TextMeasurer, gestures, callout). What must not drift is shared by
 * construction: [SparkGeometry] (coordinates), [evenPacePercent] + [abovePaceWash] (the
 * wash gate, CCBG-21 (Zero-Point Shading)'s strict one), [chartNowMs] (the now divider
 * at the wall clock, CCRM-74 (Chart Polish) item 1), [Palette.barColor] (the ladder) and [Fmt] (stamps).
 *
 * Two changes from the widget version: [density] is passed in rather than read from the
 * display, since the card renders at a fixed 4×; and the 80/90/100% labels sit inside
 * the plot on a card-coloured halo, because [SparkGeometry] lost its right gutter in
 * CCRM-74 (Chart Polish) — the same placement the in-app chart uses.
 */
object ChartBitmap {

    fun draw(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        samples: List<Pair<Long, Double>>, // (epochMillis, percent), ascending
        windowStartMs: Long,
        windowEndMs: Long,
        projectedEnd: Pair<Long, Double>?,
        color: Color,
        accent: Color,
        dark: Boolean,
        cardArgb: Int,
        nowMs: Long,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val geo = SparkGeometry(w.toFloat(), h.toFloat(), Density(density), windowStartMs, windowEndMs)
        if (!geo.usable) return bmp

        val fg = if (dark) Color(0xFFF2F2F4) else Color(0xFF1D1D1F)
        val muted = fg.copy(alpha = 0.55f)
        val warn80 = Palette.barColor(85.0, accent, dark)
        val warn90 = Palette.barColor(95.0, accent, dark)
        val warn100 = Palette.barColor(100.0, accent, dark)
        fun dp(v: Float) = v * density

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(9.5f); typeface = Typeface.DEFAULT }
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = cardArgb }

        // --- the warning wash, only past the dead zone above pace ---
        val last = samples.lastOrNull()
        if (last != null && abovePaceWash(last.second, evenPacePercent(last.first, windowStartMs, windowEndMs))) {
            val wash = Path().apply {
                moveTo(0f, geo.y(0.0))
                lineTo(geo.plotRight, geo.y(100.0))
                lineTo(0f, geo.y(100.0))
                close()
            }
            fill.color = warn100.copy(alpha = if (dark) 0.10f else 0.07f).toArgb()
            canvas.drawPath(wash, fill)
        }

        // --- threshold guides, ladder colours ---
        val guides = listOf(100.0 to warn100, 90.0 to warn90, 80.0 to warn80)
        for ((pct, c) in guides) {
            line.color = c.copy(alpha = 0.32f).toArgb()
            line.strokeWidth = dp(1f)
            line.pathEffect = DashPathEffect(floatArrayOf(dp(2f), dp(3f)), 0f)
            canvas.drawLine(0f, geo.y(pct), geo.plotRight, geo.y(pct), line)
        }

        // --- the even-pace diagonal ---
        line.color = warn80.copy(alpha = 0.9f).toArgb()
        line.strokeWidth = dp(2f)
        line.pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(2.5f)), 0f)
        canvas.drawLine(0f, geo.y(0.0), geo.plotRight, geo.y(100.0), line)
        line.pathEffect = null

        // --- x axis ---
        line.color = muted.copy(alpha = 0.18f).toArgb()
        line.strokeWidth = dp(1f)
        canvas.drawLine(0f, geo.plotBottom, geo.plotRight, geo.plotBottom, line)
        val compact = windowEndMs - windowStartMs <= 12 * 60 * 60_000L
        fun stamp(ms: Long) = Instant.ofEpochMilli(ms).let {
            if (compact) Fmt.timeOnly(it, use24h = false) else Fmt.dayMonth(it)
        }
        text.color = muted.toArgb()
        val axisY = geo.plotBottom + text.textSize + dp(2f)
        val startLabel = stamp(windowStartMs)
        val endLabel = stamp(windowEndMs)
        canvas.drawText(startLabel, 0f, axisY, text)
        canvas.drawText(endLabel, geo.plotRight - text.measureText(endLabel), axisY, text)

        if (samples.size >= 2) {
            // --- observed: line + one dot per real sample (polling gaps must show) ---
            line.color = color.toArgb()
            line.strokeWidth = dp(2.5f)
            line.strokeCap = Paint.Cap.ROUND
            val path = Path()
            samples.forEachIndexed { i, (t, pct) ->
                if (i == 0) path.moveTo(geo.x(t), geo.y(pct)) else path.lineTo(geo.x(t), geo.y(pct))
            }
            canvas.drawPath(path, line)
            fill.color = color.toArgb()
            for ((t, pct) in samples.dropLast(1)) {
                canvas.drawCircle(geo.x(t), geo.y(pct), dp(2.2f), fill)
            }

            // --- now: the divider at the wall clock, the marker on the newest reading ---
            val (nowT, nowPct) = samples.last()
            val nowX = geo.x(nowT)
            val divider = geo.x(chartNowMs(nowMs, windowStartMs, windowEndMs, nowT))
            line.color = muted.copy(alpha = 0.28f).toArgb()
            line.strokeWidth = dp(1f)
            canvas.drawLine(divider, geo.plotTop, divider, geo.plotBottom, line)

            // --- projection: dashed tail to a hollow, labelled endpoint ---
            projectedEnd?.let { (t, pct) ->
                if (t <= nowT) return@let
                val ex = geo.x(t)
                val ey = geo.y(pct)
                line.color = color.copy(alpha = 0.65f).toArgb()
                line.strokeWidth = dp(2.5f)
                line.pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3.5f)), 0f)
                canvas.drawLine(nowX, geo.y(nowPct), ex, ey, line)
                line.pathEffect = null
                line.color = color.copy(alpha = 0.8f).toArgb()
                line.strokeWidth = dp(1.8f)
                canvas.drawCircle(ex, ey, dp(4f), line)
                text.color = muted.toArgb()
                text.isFakeBoldText = false
                text.textSize = dp(9.5f)
                val l = "~${pct.toInt()}%"
                val lw = text.measureText(l)
                // Below and right of the hollow, where the tail that rises into it isn't;
                // kept clear of the guide-label column.
                val lx = (ex + dp(6f)).coerceAtMost(geo.plotRight - lw - dp(30f)).coerceAtLeast(0f)
                val ly = (ey + dp(6f) + text.textSize).coerceAtMost(geo.plotBottom - dp(2f))
                haloed(canvas, text, l, lx, ly, halo, dp(2f))
            }

            fill.color = color.toArgb()
            canvas.drawCircle(nowX, geo.y(nowPct), dp(4.5f), fill)
            text.color = fg.toArgb()
            text.textSize = dp(11f)
            text.isFakeBoldText = true
            val nowLabel = "${nowPct.toInt()}%"
            val nowW = text.measureText(nowLabel)
            val nowY = geo.y(nowPct)
            // Above-left of its marker, off the rising curve; once the marker is up among
            // the guides, below-right of it instead, where the curve isn't.
            val high = nowY < geo.plotTop + (geo.plotBottom - geo.plotTop) / 3f
            val nx = if (high) (nowX + dp(8f)).coerceAtMost(geo.plotRight - nowW - dp(30f))
            else (nowX - nowW - dp(6f)).coerceAtLeast(0f)
            val ny = (if (high) nowY + dp(6f) + text.textSize else nowY - dp(6f))
                .coerceIn(text.textSize, geo.plotBottom - dp(2f))
            haloed(canvas, text, nowLabel, nx, ny, halo, dp(2f))
        } else {
            // Honest-empty: the plot stays, with the reason in words.
            text.color = muted.toArgb()
            text.textSize = dp(11f)
            val msg = "Not enough history in this window yet"
            haloed(
                canvas, text, msg, (geo.plotRight - text.measureText(msg)) / 2f,
                (geo.plotTop + geo.plotBottom) / 2f, halo, dp(2f),
            )
        }

        // --- guide labels last, on a halo, so the curve passing behind can't eat them ---
        // The three bands are ~9 dp apart, so every halo goes down before any glyph —
        // one label's halo must never bite the one above it.
        text.isFakeBoldText = false
        text.textSize = dp(9f)
        val haloPad = dp(2f)
        val fm = text.fontMetrics
        val placed = guides.map { (pct, c) ->
            val l = "${pct.toInt()}%"
            val lw = text.measureText(l)
            val x = geo.plotRight - dp(3f) - lw
            val baseline = (geo.y(pct) - dp(1.5f)).coerceAtLeast(-fm.ascent)
            canvas.drawRect(RectF(x - haloPad, baseline + fm.ascent * 0.8f, x + lw + haloPad, baseline + fm.descent * 0.5f), halo)
            Triple(l, x to baseline, c)
        }
        for ((l, at, c) in placed) {
            text.color = c.copy(alpha = 0.85f).toArgb()
            canvas.drawText(l, at.first, at.second, text)
        }
        return bmp
    }

    /** [s] on a card-coloured backing rect, so a line crossing behind it can't eat it. */
    private fun haloed(c: Canvas, text: Paint, s: String, x: Float, baseline: Float, halo: Paint, pad: Float) {
        val m = text.fontMetrics
        c.drawRect(RectF(x - pad, baseline + m.ascent * 0.8f, x + text.measureText(s) + pad, baseline + m.descent * 0.5f), halo)
        c.drawText(s, x, baseline, text)
    }
}
