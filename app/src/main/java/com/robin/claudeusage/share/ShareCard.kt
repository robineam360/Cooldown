package com.robin.claudeusage.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.robin.claudeusage.data.HistoryPoint
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.ui.BarRenderer
import com.robin.claudeusage.ui.RingRenderer
import com.robin.claudeusage.ui.pacePhrase
import com.robin.claudeusage.ui.providerMarkRes
import com.robin.claudeusage.widgets.AccountInput
import com.robin.claudeusage.widgets.Cell
import com.robin.claudeusage.widgets.Face
import com.robin.claudeusage.widgets.FaceBackground
import com.robin.claudeusage.widgets.FaceInput
import com.robin.claudeusage.widgets.FaceStates
import com.robin.claudeusage.widgets.FaceWindow
import com.robin.claudeusage.widgets.StateId
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CCRM-24 (Share Card): one account's usage as an image to post — wireframe rev D §9e
 * (design/2026-09-25-widgets-reborn.html). Only the account open on Main when Share was
 * tapped: an 88 dp ring with its figure, the account's label and "as of" stamp, the 5h and
 * Weekly bars with their pace and absolute resets, that account's trend, and a COOLDOWN
 * wordmark.
 *
 * Drawn 360 dp wide at a fixed [SCALE] of 4 (1440 px) whatever the phone's density — a card
 * that looks crisp in a chat thread has to be drawn well above display density. The ring,
 * bars and chart come from [RingRenderer], [BarRenderer] and [ChartBitmap], the same
 * painters as every other surface, and every reading is classified by [FaceStates.cell], so
 * the card and the widgets can never disagree about a state.
 *
 * **Privacy:** the account's own label and nothing else that identifies it — no email, no
 * plan tier. Nothing is written until the user taps Share in the preview, and then only to
 * `cacheDir/share/` ([Files]).
 */
object ShareCard {

    const val SCALE = 4f
    const val WIDTH_DP = 360f
    const val WIDTH_PX = (WIDTH_DP * SCALE).toInt()

    private const val PAD = 20f
    private const val RING = 88f
    private const val RING_STROKE = 8f
    private const val HEADER_GAP = 14f
    private const val BAR_H = 10f
    /** MEDIUM's 120 dp, the app's floor for a labelled chart (Fable, Step 6). */
    private const val CHART_H = 120f
    /** S13's in-flow ribbon, edge to edge at the very top (rev D's ≥160 dp rule). */
    private const val BAND_H = 16f

    const val CARD_DARK = 0xFF0D0D0D.toInt()
    const val CARD_LIGHT = 0xFFF5EFE8.toInt()
    private const val INK_DARK = 0xFFEDE8E4.toInt()
    private const val INK_LIGHT = 0xFF26211E.toInt()
    /** S13's violet (rev D). */
    private const val SYNTHETIC = 0xFF7C4DFF.toInt()

    /** One window's row: "5h … 62%", the bar, then pace left and the reset right. */
    data class Row(
        val name: String,
        val figure: String,
        val pct: Double?,
        val elapsed: Double?,
        val pace: String?,
        val reset: String?,
    )

    /** The headline window's trend; null when the window has no reset clock to chart. */
    data class Trend(
        val title: String,
        val samples: List<Pair<Long, Double>>,
        val windowStartMs: Long,
        val windowEndMs: Long,
        val projectedEnd: Pair<Long, Double>?,
        val fillArgb: Int,
    )

    /** Everything the card draws, decided. Pure — the tests read it without a Canvas. */
    data class Model(
        val label: String,
        val provider: Provider,
        val accentArgb: Int,
        val asOf: String?,
        val headline: Cell,
        val rows: List<Row>,
        val trend: Trend?,
        val nowMs: Long,
        val dark: Boolean,
        val showOverPace: Boolean,
        val synthetic: Boolean,
    )

    fun model(
        account: AccountInput,
        history: List<HistoryPoint>,
        dark: Boolean,
        usageLeft: Boolean,
        showOverPace: Boolean,
        synthetic: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Model {
        val input = FaceInput(
            face = Face.RING, accountKey = account.key, window = FaceWindow.SESSION,
            background = FaceBackground.SOLID, dark = dark, accounts = listOf(account),
            usageLeft = usageLeft, showOverPace = showOverPace, synthetic = synthetic,
            unavailable = false, nowMs = nowMs, zone = zone,
        )
        val headline = FaceStates.cell(input, account, FaceWindow.SESSION, unassigned = false)
        val data = account.data
        val rows = buildList {
            if (data?.session != null) add(row(FaceStates.cell(input, account, FaceWindow.SESSION, false), usageLeft))
            if (data?.weekly != null) add(row(FaceStates.cell(input, account, FaceWindow.WEEKLY, false), usageLeft))
        }
        return Model(
            label = account.label,
            provider = account.provider,
            accentArgb = account.accentArgb,
            asOf = asOf(account.fetchedAt, zone),
            headline = headline,
            rows = rows,
            trend = trend(headline, account, history),
            nowMs = nowMs,
            dark = dark,
            showOverPace = showOverPace,
            synthetic = synthetic,
        )
    }

    /** "as of 6:29 PM, Thu Sep 25" — the snapshot's own time, never the render's. */
    fun asOf(fetchedAt: Long, zone: ZoneId): String? = fetchedAt.takeIf { it > 0 }?.let {
        "as of " + DateTimeFormatter.ofPattern("h:mm a, EEE MMM d", Locale.US)
            .withZone(zone).format(Instant.ofEpochMilli(it))
    }

    private fun row(c: Cell, usageLeft: Boolean): Row = Row(
        name = c.window.word + if (usageLeft) " left" else "",
        figure = c.figure,
        pct = c.pct,
        elapsed = c.elapsed,
        // Pace is about usage, so it reads the used figure even in Left mode.
        pace = if (c.pct != null && c.elapsed != null) pacePhrase(c.pct - c.elapsed) else null,
        reset = c.sub,
    )

    private fun trend(c: Cell, account: AccountInput, history: List<HistoryPoint>): Trend? {
        val reset = c.resetsAt?.toEpochMilli() ?: return null
        if (StateId.S5 in c.states || StateId.S6 in c.states) return null
        val length = c.window.lengthMs
        val samples = if (c.window == FaceWindow.WEEKLY) Projection.weeklySamples(history, reset, length)
        else Projection.sessionSamples(history, reset, length)
        val est = if (samples.size >= 2) Projection.estimate(samples, reset) else null
        return Trend(
            title = "${c.window.word} trend",
            samples = samples,
            windowStartMs = reset - length,
            windowEndMs = reset,
            projectedEnd = est?.let { e ->
                if (e.hitsLimitAtMs != null) e.hitsLimitAtMs to 100.0 else reset to e.pctAtReset
            },
            fillArgb = c.fillArgb,
        )
    }

    // ---- drawing ---------------------------------------------------------------------

    private fun ink(dark: Boolean, alpha: Float): Int {
        val base = if (dark) INK_DARK else INK_LIGHT
        return ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or (base and 0x00FFFFFF)
    }

    private fun px(dp: Float) = dp * SCALE

    private fun rowHeightDp(): Float =
        12f + 20f + 4f + BarRenderer.bitmapHeight(px(BAR_H)) / SCALE + 4f + 14f

    /** The card's height for [m], in px; the layout is fixed, so it is plain arithmetic. */
    fun heightPx(m: Model): Int {
        var dp = PAD
        if (m.synthetic) dp += BAND_H
        dp += RING
        dp += m.rows.size * rowHeightDp()
        if (m.trend != null) dp += 14f + 12f + 14f + 6f + CHART_H
        dp += 12f + 12f + PAD
        return px(dp).toInt()
    }

    fun render(context: Context, m: Model): Bitmap {
        val dark = m.dark
        val card = if (dark) CARD_DARK else CARD_LIGHT
        val bmp = Bitmap.createBitmap(WIDTH_PX, heightPx(m), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // Full-bleed: a re-encoding chat app turns transparent corners black.
        c.drawColor(card)
        val inner = WIDTH_DP - 2 * PAD
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fun font(sizeDp: Float, bold: Boolean, argb: Int, spacing: Float = 0f) = text.apply {
            textSize = px(sizeDp)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = argb
            letterSpacing = spacing
        }
        var y = 0f
        val left = px(PAD)
        val right = px(PAD + inner)

        // S13 (R8): a fabricated reading is never posted unmarked — the widgets' own
        // ribbon (Fable, Step 6), in flow so nothing below can collide with it.
        if (m.synthetic) {
            c.drawRect(0f, 0f, WIDTH_PX.toFloat(), px(BAND_H), Paint().apply { color = SYNTHETIC })
            font(8.5f, true, 0xFFFFFFFF.toInt(), 0.06f)
            val label = "● SYNTHETIC"
            c.drawText(label, (WIDTH_PX - text.measureText(label)) / 2f, px(BAND_H) / 2f - (text.ascent() + text.descent()) / 2f, text)
            y += px(BAND_H)
        }
        y += px(PAD)

        // Header: the ring, then the mark + label and the stamp beside it.
        val h = m.headline
        val ring = RingRenderer.draw(
            context, px(RING).toInt(), px(RING_STROKE), h.pct, h.elapsed, h.fillArgb, dark,
            m.showOverPace, spentCross = h.full, mark = RingRenderer.PaceMark.TICK,
            haloArgb = card, density = SCALE,
        )
        c.drawBitmap(ring, left, y, null)
        ring.recycle()
        val ringCx = left + px(RING) / 2f
        val ringCy = y + px(RING) / 2f
        // At 100% the × replaces the figure (Q3, Q10), as on the Ring widget.
        if (!h.full) {
            font(22f, true, if (h.pct == null) ink(dark, 1f) else h.fillArgb)
            c.drawText(h.figure, ringCx - text.measureText(h.figure) / 2f, ringCy - (text.ascent() + text.descent()) / 2f, text)
        }
        val tx = left + px(RING + HEADER_GAP)
        val markSize = px(16f)
        font(17f, true, ink(dark, 1f))
        val labelBase = ringCy - px(3f)
        ContextCompat.getDrawable(context, providerMarkRes(m.provider))?.mutate()?.let { d ->
            d.setTint(m.accentArgb)
            val top = (labelBase + (text.ascent() + text.descent()) / 2f - markSize / 2f).toInt()
            d.setBounds(tx.toInt(), top, (tx + markSize).toInt(), top + markSize.toInt())
            d.draw(c)
        }
        val labelX = tx + markSize + px(6f)
        val label = TextUtils.ellipsize(m.label, text, right - labelX, TextUtils.TruncateAt.END).toString()
        c.drawText(label, labelX, labelBase, text)
        m.asOf?.let {
            font(11f, false, ink(dark, 0.6f))
            c.drawText(TextUtils.ellipsize(it, text, right - tx, TextUtils.TruncateAt.END).toString(), tx, labelBase + px(17f), text)
        }
        y += px(RING)

        // One row per window.
        val tick = BarRenderer.Tick(px(3f), px(1f), ink(dark, 0.9f), card)
        for (r in m.rows) {
            y += px(12f)
            val base = y + px(16f)
            font(12.5f, false, ink(dark, 0.85f))
            c.drawText(r.name, left, base, text)
            font(17f, true, ink(dark, 1f))
            c.drawText(r.figure, right - text.measureText(r.figure), base, text)
            y += px(20f + 4f)
            val bar = BarRenderer.draw(
                px(inner), px(BAR_H), r.pct, r.elapsed, Color(m.accentArgb), dark, m.showOverPace, tick,
            )
            c.drawBitmap(bar, left - BarRenderer.sidePadding(px(BAR_H), tick), y, null)
            y += bar.height + px(4f)
            bar.recycle()
            font(10.5f, false, ink(dark, 0.7f))
            val sub = y + px(11f)
            r.pace?.let { c.drawText(it, left, sub, text) }
            r.reset?.let { c.drawText(it, right - text.measureText(it), sub, text) }
            y += px(14f)
        }

        // The trend, under a hairline.
        m.trend?.let { t ->
            y += px(14f)
            c.drawRect(left, y, right, y + px(1f), Paint().apply { color = ink(dark, 0.08f) })
            y += px(12f)
            font(10.5f, false, ink(dark, 0.6f))
            c.drawText(t.title, left, y + px(11f), text)
            y += px(14f + 6f)
            val chart = ChartBitmap.draw(
                px(inner).toInt(), px(CHART_H).toInt(), SCALE, t.samples, t.windowStartMs,
                t.windowEndMs, t.projectedEnd, Color(t.fillArgb), Color(m.accentArgb), dark, card, m.nowMs,
            )
            c.drawBitmap(chart, left, y, null)
            chart.recycle()
            y += px(CHART_H)
        }

        y += px(12f)
        font(9.5f, false, ink(dark, 0.45f), 0.06f)
        c.drawText("COOLDOWN", left, y + px(10f), text)
        return bmp
    }

    // ---- the file and the intent -------------------------------------------------------

    /**
     * `cacheDir/share/` and nothing else: [clear] runs before every render and on app
     * start, so at most one snapshot ever sits on disk, inside the app's own cache.
     */
    object Files {
        const val DIR = "share"

        fun authority(context: Context) = "${context.packageName}.share"

        fun dir(context: Context) = File(context.cacheDir, DIR)

        fun clear(context: Context) {
            dir(context).listFiles()?.forEach { it.delete() }
        }

        /** Writes [bmp] as the one snapshot and returns its `content://` URI. */
        fun write(context: Context, bmp: Bitmap): Uri {
            clear(context)
            val dir = dir(context).apply { mkdirs() }
            val file = File(dir, "cooldown-snapshot-${System.currentTimeMillis()}.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return FileProvider.getUriForFile(context, authority(context), file)
        }

        /** The system share sheet for [uri], read access granted to the chosen target. */
        fun chooser(uri: Uri): Intent {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return Intent.createChooser(send, null).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
