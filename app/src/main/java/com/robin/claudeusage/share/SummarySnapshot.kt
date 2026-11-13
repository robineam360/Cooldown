package com.robin.claudeusage.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.robin.claudeusage.SummaryRowModel
import com.robin.claudeusage.data.AccountTag
import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.MapLayers
import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.SummaryFilter
import com.robin.claudeusage.data.TagMarks
import com.robin.claudeusage.data.SummaryOrder
import com.robin.claudeusage.data.SummaryRank
import com.robin.claudeusage.ui.BarRenderer
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.Grammar
import com.robin.claudeusage.ui.PaceMapEntry
import com.robin.claudeusage.ui.PaceMapRules
import com.robin.claudeusage.ui.PaceMapScene
import com.robin.claudeusage.ui.PaceText
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.RingRenderer
import com.robin.claudeusage.ui.SummaryRows
import com.robin.claudeusage.ui.drawPaceMap
import com.robin.claudeusage.ui.providerMarkRes
import com.robin.claudeusage.ui.tagGlyphRes
import com.robin.claudeusage.widgets.AccountInput
import com.robin.claudeusage.widgets.Cell
import com.robin.claudeusage.widgets.Face
import com.robin.claudeusage.widgets.FaceBackground
import com.robin.claudeusage.widgets.FaceInput
import com.robin.claudeusage.widgets.FaceStates
import com.robin.claudeusage.widgets.FaceWindow
import com.robin.claudeusage.widgets.StateId
import java.time.ZoneId

/**
 * CCRM-112 (Summary Snapshot): the Summary as one image to post — wireframe rev B §8 and the 2026-11-11 addendum §7
 * (`design/2026-11-10-v20-feedback-wireframes.html`, `design/2026-11-11-v20-narrow-duet-and-row-glyph-wireframes.html`).
 * 360 dp wide at a fixed [ShareCard.SCALE] of 4 whatever the phone's density or font scale: the header (the filter's
 * name, "as of", the order's name), the rows in the screen's order and filter, then "Pace map" and the map with the
 * layers on screen — never the chips, never a pinned callout or its dimming — and its foot line, then COOLDOWN.
 *
 * The rows are the per-account card's two-slot row (5h ring 32 dp, mark 14 + name 13 sp SemiBold, figure 17 sp bold,
 * bar 8 dp with tick, the pace span left and "Resets …" right); from six rows shown one line each (mark, name, 7 dp
 * bar, word, figure). **CCRM-115 (Row Tag Glyph):** a 12 dp tag glyph and 4 dp lead each row's mark, but only while
 * the rows on the card carry both tags — so only under All, and never for an install whose accounts are all Personal;
 * under a filter the header already says which. Names, never e-mail addresses.
 *
 * Drawn off the main thread, written nowhere until the user taps Share in the preview ([ShareCard.Files]).
 */
internal object SummarySnapshot {

    private const val SCALE = ShareCard.SCALE
    private const val WIDTH_DP = ShareCard.WIDTH_DP
    private const val PAD = 20f
    private const val INNER = WIDTH_DP - 2 * PAD
    private const val RING = 32f
    private const val RING_STROKE = 3.5f
    private const val RING_GAP = 10f
    private const val BAR_H = 8f
    private const val ONE_LINE_BAR_H = 7f
    private const val BAND_H = 16f
    private const val MAP_H = 200f
    private const val DIM = 0.5f

    private const val CARD_DARK = ShareCard.CARD_DARK
    private const val CARD_LIGHT = ShareCard.CARD_LIGHT
    private const val INK_DARK = 0xFFEDE8E4.toInt()
    private const val INK_LIGHT = 0xFF26211E.toInt()
    private const val SYNTHETIC = 0xFF7C4DFF.toInt()

    /** One account's row, decided. */
    data class Row(
        val name: String,
        val provider: Provider,
        val accentArgb: Int,
        val tag: AccountTag,
        /** The 5h ring (the Weekly gauge on a plan without a 5h is not drawn here: a bare track with a cross). */
        val ring: Cell,
        val hasSession: Boolean,
        val figure: String,
        val pct: Double?,
        val elapsed: Double?,
        /** The pace slot, left: the tinted span, "Out until …", "Held · …", "Stale", or the unread word. */
        val pace: String?,
        val paceTone: PaceText.VsTone?,
        val reset: String?,
        /** A spent week: the figure and its line in the severity red. */
        val alarm: Boolean,
        /** Signed out / no reading: the row at 50%, its word, no bar fill. */
        val faded: Boolean,
        /** The one-line form's word. */
        val word: String,
        val fillArgb: Int,
    )

    data class Model(
        val title: String,
        val asOf: String?,
        val orderName: String,
        val rows: List<Row>,
        /** Both tags on the card (CCRM-115): the glyph column is drawn. */
        val glyphColumn: Boolean,
        /** CCRM-118: a row whose tag's mark is off draws a blank slot while the column exists. */
        val tagMarks: Map<AccountTag, Boolean> = TagMarks.ALL_ON,
        val entries: List<PaceMapEntry>,
        val layers: MapLayers,
        val mode: PaceText.Mode,
        val foot: PaceMapRules.Foot,
        val dark: Boolean,
        val showOverPace: Boolean,
        val synthetic: Boolean,
    ) {
        /** One line each from six rows shown (the screen's S2 rule). */
        val oneLine: Boolean get() = SummaryRows.compact(rows.size)
    }

    /** The share entry is shown only when some account on the Summary has a reading (rev B §8). */
    fun hasReading(rows: List<SummaryRowModel>): Boolean = rows.any { it.data != null }

    /**
     * The glyph column rides only while the rows on the card carry both tags (addendum R4 A) and, CCRM-118 (Header
     * Glyphs), at least one of those tags is marked; a row whose tag is off leaves its slot blank.
     */
    fun glyphColumn(tags: List<AccountTag>, marks: Map<AccountTag, Boolean> = TagMarks.ALL_ON): Boolean =
        TagMarks.snapshotColumn(tags, marks)

    /** "as of 2:00 PM, Tue Nov 10": the oldest reading the card shows (never the render's own time). */
    fun asOf(fetchedAts: List<Long>, zone: ZoneId): String? =
        fetchedAts.filter { it > 0 }.minOrNull()?.let { ShareCard.asOf(it, zone) }

    fun model(
        accounts: List<AccountInput>,
        rows: List<SummaryRowModel>,
        tags: Map<String, AccountTag>,
        filter: SummaryFilter,
        order: SummaryOrder,
        layers: MapLayers,
        mode: PaceText.Mode,
        use24h: Boolean,
        dark: Boolean,
        showOverPace: Boolean,
        synthetic: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        accentOf: (SummaryRowModel) -> Color,
        tagMarks: Map<AccountTag, Boolean> = TagMarks.ALL_ON,
    ): Model {
        val usageLeft = mode == PaceText.Mode.LEFT
        val byKey = accounts.associateBy { it.key }
        val cards = rows.mapNotNull { r ->
            val a = byKey[r.profile.key] ?: return@mapNotNull null
            row(a, r, tags[r.profile.key] ?: AccountTag.PERSONAL, usageLeft, dark, showOverPace, synthetic, nowMs, zone, use24h)
        }
        val entries = rows.mapNotNull { r -> if (!r.plotted) null else r.mapEntry(accentOf(r), use24h) }
        return Model(
            title = filter.snapshotHeader,
            asOf = asOf(accounts.filter { a -> rows.any { it.profile.key == a.key } }.map { it.fetchedAt }, zone),
            orderName = order.header,
            rows = cards,
            glyphColumn = glyphColumn(cards.map { it.tag }, tagMarks),
            tagMarks = tagMarks,
            entries = entries,
            layers = layers,
            mode = mode,
            foot = PaceMapRules.foot(rows.map { PaceMapRules.Account(it.used, it.clocked) }),
            dark = dark,
            showOverPace = showOverPace,
            synthetic = synthetic,
        )
    }

    private fun row(
        a: AccountInput,
        r: SummaryRowModel,
        tag: AccountTag,
        usageLeft: Boolean,
        dark: Boolean,
        showOverPace: Boolean,
        synthetic: Boolean,
        nowMs: Long,
        zone: ZoneId,
        use24h: Boolean,
    ): Row {
        val input = FaceInput(
            face = Face.RING, accountKey = a.key, window = FaceWindow.SESSION, background = FaceBackground.SOLID,
            dark = dark, accounts = listOf(a), usageLeft = usageLeft, showOverPace = showOverPace, synthetic = synthetic,
            unavailable = false, nowMs = nowMs, zone = zone,
        )
        val ring = FaceStates.cell(input, a, FaceWindow.SESSION, unassigned = false)
        val week = FaceStates.cell(input, a, FaceWindow.WEEKLY, unassigned = false)
        val data = a.data
        // CCBG-94 (Snapshot Not Started): a week with a reading but no clock yet (Gemini before its first use) is drawn
        // as the Summary draws it — its figure, a bare track, "Not started yet" — not as an unread row.
        val notStarted = a.authState == AuthState.OK && week.pct == null &&
            data?.weekly?.percent != null && data.weekly.resetsAt == null
        val unread = !notStarted && (data == null || data.weekly == null || week.pct == null)
        val spent = !unread && Pace.isSpent(week.pct)
        val stale = StateId.S8 in week.states
        val base = ShareCard.rowOf(week, usageLeft, nowMs)
        val held = r.group == SummaryRank.Group.HELD
        val unreadWord = if (a.authState != AuthState.OK) "Sign in" else "No reading"
        val pace: String? = when {
            notStarted -> NOT_STARTED
            unread -> unreadWord
            spent -> week.resetsAt?.let { "Out until ${Fmt.widgetClock(it, nowMs, zone)}" }
            stale -> "Stale"
            held -> SummaryRows.withoutReset(SummaryRows.heldWords(data?.session, data?.weekly, use24h))
            else -> base.pace
        }
        val reset = when {
            notStarted || unread || stale -> null
            else -> base.reset
        }
        val word = when {
            notStarted -> "Not started"
            unread -> unreadWord
            else -> SummaryRows.compactWord(r.group, r.weekly, r.elapsed, r.data?.session, use24h)
        }
        return Row(
            name = a.label, provider = a.provider, accentArgb = a.accentArgb, tag = tag, ring = ring,
            hasSession = data?.session != null,
            figure = when {
                notStarted -> "${Fmt.usageInt(data!!.weekly!!.percent!!, usageLeft)}%"
                unread -> "—"
                else -> week.figure
            },
            pct = when {
                notStarted -> data!!.weekly!!.percent
                unread -> null
                else -> week.pct
            },
            elapsed = if (notStarted) null else week.elapsed, pace = pace,
            paceTone = if (!notStarted && !unread && !spent && !stale && !held) base.paceTone else null,
            reset = reset, alarm = spent, faded = unread, word = word, fillArgb = week.fillArgb,
        )
    }

    /** The Summary's line-3 words for a week with no clock yet (`SummaryRows.words`). */
    private const val NOT_STARTED = "Not started yet"

    // ---- layout (dp) ------------------------------------------------------------------

    private fun px(dp: Float) = dp * SCALE

    private const val HEADER_H = 22f + 4f + 14f
    private const val ONE_LINE_H = 24f

    private fun fullRowH(): Float = 12f + 20f + 4f + BarRenderer.bitmapHeight(px(BAR_H)) / SCALE + 4f + 14f

    private fun rowH(m: Model): Float = if (m.oneLine) ONE_LINE_H else fullRowH()

    /** The card's height in px; the layout is fixed, so it is plain arithmetic (the tests hold it to the drawing). */
    fun heightPx(m: Model): Int {
        var dp = PAD
        if (m.synthetic) dp += BAND_H
        dp += HEADER_H + 14f
        dp += m.rows.size * rowH(m)
        // hairline, label, map, foot line, footer.
        dp += 14f + 12f + 14f + 6f + MAP_H + 4f + 14f
        dp += 12f + 12f + PAD
        return px(dp).toInt()
    }

    fun render(context: Context, m: Model): Bitmap = draw(context, m).first

    private fun ink(dark: Boolean, alpha: Float): Int {
        val base = if (dark) INK_DARK else INK_LIGHT
        return ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or (base and 0x00FFFFFF)
    }

    private fun fade(argb: Int, f: Float): Int =
        ((((argb ushr 24) * f).toInt().coerceIn(0, 255)) shl 24) or (argb and 0x00FFFFFF)

    /** The card and the y, in px, where its drawing ended. */
    internal fun draw(context: Context, m: Model): Pair<Bitmap, Float> {
        val dark = m.dark
        val card = if (dark) CARD_DARK else CARD_LIGHT
        val bmp = Bitmap.createBitmap(ShareCard.WIDTH_PX, heightPx(m), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(card)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fun font(sizeDp: Float, bold: Boolean, argb: Int, spacing: Float = 0f) = text.apply {
            textSize = px(sizeDp)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = argb
            letterSpacing = spacing
        }
        val left = px(PAD)
        val right = px(PAD + INNER)
        var y = 0f

        if (m.synthetic) {
            c.drawRect(0f, 0f, ShareCard.WIDTH_PX.toFloat(), px(BAND_H), Paint().apply { color = SYNTHETIC })
            font(8.5f, true, 0xFFFFFFFF.toInt(), 0.06f)
            // CCRM-113 (Sample Port): the sample reuses the band, in iOS's words; the debug series keeps "● SYNTHETIC".
            val label = if (com.robin.claudeusage.data.SampleMode.isOn) "● SAMPLE DATA" else "● SYNTHETIC"
            c.drawText(label, (ShareCard.WIDTH_PX - text.measureText(label)) / 2f, px(BAND_H) / 2f - (text.ascent() + text.descent()) / 2f, text)
            y += px(BAND_H)
        }
        y += px(PAD)

        // Header: the filter's name, "as of", and the order's name at the right.
        font(17f, true, ink(dark, 1f))
        val orderRoom = px(86f)
        c.drawText(TextUtils.ellipsize(m.title, text, right - left - orderRoom, TextUtils.TruncateAt.END).toString(), left, y + px(17f), text)
        font(11f, false, ink(dark, 0.6f))
        c.drawText(m.orderName, right - text.measureText(m.orderName), y + px(17f), text)
        m.asOf?.let { c.drawText(TextUtils.ellipsize(it, text, right - left, TextUtils.TruncateAt.END).toString(), left, y + px(22f + 4f + 11f), text) }
        y += px(HEADER_H + 14f)

        val tick = BarRenderer.Tick(px(3f), px(1f), ink(dark, 0.9f), card)
        for (r in m.rows) {
            val a = if (r.faded) DIM else 1f
            if (m.oneLine) {
                drawOneLine(c, context, text, ::font, r, m, left, right, y, a, card, tick)
                y += px(ONE_LINE_H)
            } else {
                drawFull(c, context, text, ::font, r, m, left, right, y, a, card, tick)
                y += px(fullRowH())
            }
        }

        // The map, under a hairline.
        y += px(14f)
        c.drawRect(left, y, right, y + px(1f), Paint().apply { color = ink(dark, 0.08f) })
        y += px(12f)
        font(10.5f, false, ink(dark, 0.6f))
        c.drawText("Pace map", left, y + px(11f), text)
        y += px(14f + 6f)
        val map = drawMap(context, m, card)
        c.drawBitmap(map, left, y, null)
        map.recycle()
        y += px(MAP_H + 4f)
        font(10.5f, false, ink(dark, 0.55f))
        c.drawText(m.foot.left, left, y + px(10f), text)
        m.foot.count?.let { c.drawText(it, right - text.measureText(it), y + px(10f), text) }
        y += px(14f)

        y += px(12f)
        font(9.5f, false, ink(dark, 0.45f), 0.06f)
        c.drawText("COOLDOWN", left, y + px(10f), text)
        y += px(12f + PAD)
        return bmp to y
    }

    private fun drawGlyphAndMark(
        c: Canvas, context: Context, r: Row, m: Model, x0: Float, baseline: Float, text: TextPaint, glyphDp: Float, markDp: Float, a: Float,
    ): Float {
        var x = x0
        if (m.glyphColumn) {
            val g = px(glyphDp)
            if (m.tagMarks[r.tag] != false) ContextCompat.getDrawable(context, tagGlyphRes(r.tag))?.mutate()?.let { d ->
                d.setTint(ink(m.dark, 0.6f * a))
                val top = (baseline + (text.ascent() + text.descent()) / 2f - g / 2f).toInt()
                d.setBounds(x.toInt(), top, (x + g).toInt(), top + g.toInt())
                d.draw(c)
            }
            x += g + px(SummaryRows.GLYPH_GAP_DP)
        }
        val s = px(markDp)
        ContextCompat.getDrawable(context, providerMarkRes(r.provider))?.mutate()?.let { d ->
            d.setTint(fade(r.accentArgb, a))
            val top = (baseline + (text.ascent() + text.descent()) / 2f - s / 2f).toInt()
            d.setBounds(x.toInt(), top, (x + s).toInt(), top + s.toInt())
            d.draw(c)
        }
        return x + s + px(5f)
    }

    private fun drawFull(
        c: Canvas, context: Context, text: TextPaint, font: (Float, Boolean, Int, Float) -> TextPaint,
        r: Row, m: Model, left: Float, right: Float, y: Float, a: Float, card: Int, tick: BarRenderer.Tick,
    ) {
        val dark = m.dark
        val alarm = Palette.barColor(100.0, Color(r.accentArgb), dark).toArgb()
        val rowH = px(fullRowH())
        // The 5h ring, vertically centred on the row; a plan with no 5h draws the bare track and a cross.
        val ringBmp = RingRenderer.draw(
            context, px(RING).toInt(), px(RING_STROKE),
            if (r.hasSession) r.ring.pct else null, r.ring.elapsed, r.ring.fillArgb, dark, m.showOverPace,
            spentCross = !r.hasSession || r.ring.full, mark = RingRenderer.PaceMark.TICK, haloArgb = card, density = SCALE,
        )
        val ringTop = y + (rowH - px(RING)) / 2f
        c.drawBitmap(ringBmp, left, ringTop, Paint().apply { alpha = ((if (r.ring.dim) 0.45f else 1f) * 255f).toInt() })
        ringBmp.recycle()
        if (r.hasSession && !r.ring.full) {
            font(7f, true, fade(if (r.ring.pct == null) ink(dark, 1f) else r.ring.fillArgb, if (r.ring.dim) 0.45f else 1f), 0f)
            val cx = left + px(RING) / 2f
            val cy = ringTop + px(RING) / 2f
            c.drawText(r.ring.figure, cx - text.measureText(r.ring.figure) / 2f, cy - (text.ascent() + text.descent()) / 2f, text)
        }
        val tx = left + px(RING + RING_GAP)
        val line1 = y + px(12f + 16f)
        font(13f, true, fade(ink(dark, 1f), a), 0f)
        val figureW = run { font(17f, true, 0, 0f); text.measureText(r.figure) }
        val nameX = drawGlyphAndMark(c, context, r, m, tx, line1, text, SummaryRows.GLYPH_ONE_LINE_DP, 14f, a)
        font(13f, true, fade(ink(dark, 1f), a), 0f)
        c.drawText(TextUtils.ellipsize(r.name, text, right - figureW - px(8f) - nameX, TextUtils.TruncateAt.END).toString(), nameX, line1, text)
        font(17f, true, fade(if (r.alarm) alarm else ink(dark, 1f), a), 0f)
        c.drawText(r.figure, right - figureW, line1, text)
        val barTop = y + px(12f + 20f + 4f)
        val bar = BarRenderer.draw(
            right - tx, px(BAR_H), if (r.faded) null else r.pct, r.elapsed.takeIf { !r.faded }, Color(r.accentArgb), dark, m.showOverPace, tick,
        )
        c.drawBitmap(bar, tx - BarRenderer.sidePadding(px(BAR_H), tick), barTop, Paint().apply { alpha = (a * 255f).toInt() })
        val sub = barTop + bar.height + px(4f + 11f)
        bar.recycle()
        r.pace?.let {
            val tone = when (r.paceTone) {
                PaceText.VsTone.UNDER -> Grammar.vsUnder(dark).toArgb()
                PaceText.VsTone.OVER -> Grammar.vsOver(dark).toArgb()
                PaceText.VsTone.ON -> ink(dark, 1f)
                else -> null
            }
            font(10.5f, tone != null && !r.alarm, fade(if (r.alarm) alarm else tone ?: ink(dark, 0.7f), a), 0f)
            c.drawText(TextUtils.ellipsize(it, text, (right - tx) * 0.62f, TextUtils.TruncateAt.END).toString(), tx, sub, text)
        }
        r.reset?.let {
            font(10.5f, false, fade(ink(dark, 0.7f), a), 0f)
            c.drawText(it, right - text.measureText(it), sub, text)
        }
    }

    /** From six rows: mark, name, a 7 dp bar, the word, the figure — one line (24 dp). */
    private fun drawOneLine(
        c: Canvas, context: Context, text: TextPaint, font: (Float, Boolean, Int, Float) -> TextPaint,
        r: Row, m: Model, left: Float, right: Float, y: Float, a: Float, card: Int, tick: BarRenderer.Tick,
    ) {
        val dark = m.dark
        val alarm = Palette.barColor(100.0, Color(r.accentArgb), dark).toArgb()
        val base = y + px(ONE_LINE_H / 2f) + px(4.5f)
        val nameX = drawGlyphAndMark(c, context, r, m, left, base, text, SummaryRows.GLYPH_ONE_LINE_DP, 14f, a)
        font(13f, true, fade(ink(dark, 1f), a), 0f)
        val nameW = px(74f)
        c.drawText(TextUtils.ellipsize(r.name, text, nameW, TextUtils.TruncateAt.END).toString(), nameX, base, text)
        val figureW = px(38f)
        val wordW = px(90f)
        val barLeft = nameX + nameW + px(6f)
        val barRight = right - figureW - px(6f) - wordW - px(6f)
        val bar = BarRenderer.draw(
            barRight - barLeft, px(ONE_LINE_BAR_H), if (r.faded) null else r.pct, r.elapsed.takeIf { !r.faded }, Color(r.accentArgb), dark, m.showOverPace, tick,
        )
        c.drawBitmap(bar, barLeft - BarRenderer.sidePadding(px(ONE_LINE_BAR_H), tick), y + (px(ONE_LINE_H) - bar.height) / 2f, Paint().apply { alpha = (a * 255f).toInt() })
        bar.recycle()
        font(10.5f, false, fade(if (r.alarm) alarm else ink(dark, 0.75f), a), 0f)
        c.drawText(TextUtils.ellipsize(r.word, text, wordW, TextUtils.TruncateAt.END).toString(), barRight + px(6f), base, text)
        font(15f, true, fade(if (r.alarm) alarm else ink(dark, 1f), a), 0f)
        c.drawText(r.figure, right - text.measureText(r.figure), base, text)
    }

    /** The Pace map at the card's scale: the screen's own painter and scene, no chips, no callout, no dimming. */
    private fun drawMap(context: Context, m: Model, card: Int): Bitmap {
        val w = px(INNER).toInt()
        val h = px(MAP_H).toInt()
        val density = Density(SCALE, 1f)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val ink = Color(if (m.dark) INK_DARK else INK_LIGHT)
        val empty = if (m.entries.isEmpty()) m.foot.empty else null
        val scene = PaceMapScene.build(measurer, SCALE, w.toFloat(), h.toFloat(), m.entries, m.layers, m.mode, empty, ink, m.dark)
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawPaceMap(scene, m.layers, m.mode, null, null, ink, Color(card), m.dark)
        }
        return image.asAndroidBitmap()
    }
}
