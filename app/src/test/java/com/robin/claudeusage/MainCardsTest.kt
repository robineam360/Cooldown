package com.robin.claudeusage

import com.robin.claudeusage.data.CardId
import com.robin.claudeusage.data.CardLayout
import com.robin.claudeusage.data.Headline
import com.robin.claudeusage.data.ModelCap
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.SpendCredits
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.GrammarRing
import com.robin.claudeusage.ui.PaceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.robin.claudeusage.data.Provider
import androidx.compose.ui.unit.dp
import java.time.Instant

/**
 * The main screen's two pure decisions (CCRM-72 (Main Screen Redesign) / CCRM-73 (Model
 * Cap Chart)): which series the 7-day chart draws, and which cards the account has data
 * for — the count the top bar's ⋮ appears on.
 */
class MainCardsTest {

    private val resets = Instant.ofEpochMilli(1_757_000_000_000L)
    private fun window(pct: Double) = UsageWindow(pct, resets, null)

    // --- weeklyChartSelection ---

    @Test
    fun `nothing stored charts All`() {
        assertNull(weeklyChartSelection(null, listOf("Fable")))
    }

    @Test
    fun `a stored cap the account still has is kept`() {
        assertEquals("Fable", weeklyChartSelection("Fable", listOf("Fable")))
        assertEquals("Opus", weeklyChartSelection("Opus", listOf("Sonnet", "Opus")))
    }

    @Test
    fun `a stored cap the account no longer has falls back to All`() {
        assertNull(weeklyChartSelection("Sonnet", listOf("Fable")))
        assertNull(weeklyChartSelection("Fable", emptyList()))
    }

    // --- dataCards ---

    @Test
    fun `no data at all is no cards`() {
        assertEquals(emptySet<CardId>(), dataCards(null, creditsVisible = true))
    }

    @Test
    fun `both windows are one account tile, in the 7-day card's slot`() {
        // CCRM-103 (Hybrid Notification): the 5h ring rides inside the tile, so the
        // 5-hour card is no longer a card of its own.
        assertEquals(
            setOf(CardId.WEEKLY),
            dataCards(
                UsageData(session = window(14.0), weekly = window(84.0), modelCaps = emptyList()),
                creditsVisible = true,
            ),
        )
    }

    @Test
    fun `a cap alone is enough for the 7-day card`() {
        // ChatGPT has no 5-hour window since 2026-07-12; a Claude account can report a
        // cap with no pool figure. Either way the card exists.
        assertEquals(
            setOf(CardId.WEEKLY),
            dataCards(
                UsageData(
                    session = null,
                    weekly = null,
                    modelCaps = listOf(ModelCap("Fable", window(61.0))),
                ),
                creditsVisible = true,
            ),
        )
    }

    @Test
    fun `credits count only while they are reportable and shown`() {
        val credits = SpendCredits(
            usedMinor = 1_972L,
            limitMinor = 40_000L,
            exponent = 2,
            currency = "USD",
            serverSeverity = null,
        )
        val data = UsageData(
            session = window(0.0), weekly = null, modelCaps = emptyList(), credits = credits,
        )
        assertEquals(setOf(CardId.SESSION, CardId.CREDITS), dataCards(data, creditsVisible = true))
        // Hidden per account in Settings — one card left, so the ⋮ goes away with it.
        assertEquals(setOf(CardId.SESSION), dataCards(data, creditsVisible = false))
    }

    @Test
    fun `an unreportable credit budget is not a card`() {
        val empty = SpendCredits(
            usedMinor = 0L,
            limitMinor = null,
            exponent = 2,
            currency = "USD",
            serverSeverity = null,
        )
        assertEquals(
            setOf(CardId.SESSION),
            dataCards(
                UsageData(
                    session = window(0.0), weekly = null, modelCaps = emptyList(), credits = empty,
                ),
                creditsVisible = true,
            ),
        )
    }

    // --- drawnCards: saved layouts never hide the 5h or blank the page (judge F2) ---

    private val order = listOf(CardId.SESSION, CardId.WEEKLY, CardId.CREDITS)

    @Test
    fun `a layout that hid only the 7-day card still shows the tile`() {
        val l = CardLayout(order, hidden = setOf(CardId.WEEKLY), more = emptySet())
        assertEquals(DrawnCards(listOf(CardId.WEEKLY), emptyList()), drawnCards(l, setOf(CardId.WEEKLY)))
        // SESSION un-hidden too, with credits: the tile sits in the 5-hour card's place.
        assertEquals(
            DrawnCards(listOf(CardId.WEEKLY, CardId.CREDITS), emptyList()),
            drawnCards(l, setOf(CardId.WEEKLY, CardId.CREDITS)),
        )
    }

    @Test
    fun `a layout that hid only the 5-hour card still shows the tile`() {
        val l = CardLayout(order, hidden = setOf(CardId.SESSION), more = emptySet())
        assertEquals(DrawnCards(listOf(CardId.WEEKLY), emptyList()), drawnCards(l, setOf(CardId.WEEKLY)))
    }

    @Test
    fun `the tile follows its own Usage row, else the other window card's place`() {
        // Its own row decides place and fold (the sheet shows that row): a stored fold on the
        // other window id is not what the user sees, so it does not fold the tile.
        val credFirst = CardLayout(listOf(CardId.CREDITS, CardId.SESSION, CardId.WEEKLY), emptySet(), setOf(CardId.SESSION))
        assertEquals(
            DrawnCards(listOf(CardId.CREDITS, CardId.WEEKLY), emptyList()),
            drawnCards(credFirst, setOf(CardId.WEEKLY, CardId.CREDITS)),
        )
        val ownFold = CardLayout(listOf(CardId.SESSION, CardId.WEEKLY, CardId.CREDITS), emptySet(), setOf(CardId.WEEKLY))
        assertEquals(
            DrawnCards(listOf(CardId.CREDITS), listOf(CardId.WEEKLY)),
            drawnCards(ownFold, setOf(CardId.WEEKLY, CardId.CREDITS)),
        )
        val split = CardLayout(listOf(CardId.SESSION, CardId.CREDITS, CardId.WEEKLY), setOf(CardId.SESSION), emptySet())
        assertEquals(
            DrawnCards(listOf(CardId.CREDITS, CardId.WEEKLY), emptyList()),
            drawnCards(split, setOf(CardId.WEEKLY, CardId.CREDITS)),
        )
    }

    @Test
    fun `both window cards hidden hides the tile`() {
        val l = CardLayout(order, hidden = setOf(CardId.SESSION, CardId.WEEKLY), more = emptySet())
        assertEquals(
            DrawnCards(listOf(CardId.CREDITS), emptyList()),
            drawnCards(l, setOf(CardId.WEEKLY, CardId.CREDITS)),
        )
    }

    @Test
    fun `all hidden with credits off still shows the tile - never blank`() {
        val l = CardLayout(order, hidden = order.toSet(), more = emptySet())
        assertEquals(DrawnCards(listOf(CardId.WEEKLY), emptyList()), drawnCards(l, setOf(CardId.WEEKLY)))
        // A no-7-day account keeps the tile under SESSION.
        assertEquals(DrawnCards(listOf(CardId.SESSION), emptyList()), drawnCards(l, setOf(CardId.SESSION)))
        assertEquals(DrawnCards(emptyList(), emptyList()), drawnCards(l, emptySet()))
    }

    // --- tileStartsOnFive (Fable A5) ---

    @Test
    fun `the tile starts on 5h only when the drawn order leads with the 5-hour card`() {
        assertTrue(tileStartsOnFive(CardLayout.DEFAULT, hasSession = true))
        assertFalse(tileStartsOnFive(CardLayout.DEFAULT, hasSession = false))
        assertFalse(tileStartsOnFive(cardOrderLayout(CardLayout.DEFAULT, Headline.WEEKLY), hasSession = true))
        assertTrue(tileStartsOnFive(cardOrderLayout(CardLayout.DEFAULT, Headline.FIVE_HOUR), hasSession = true))
        val credFirst = CardLayout(listOf(CardId.CREDITS, CardId.SESSION, CardId.WEEKLY), emptySet(), emptySet())
        assertTrue(tileStartsOnFive(credFirst, hasSession = true))
    }

    // --- the account tile (CCRM-103 (Hybrid Notification), shape grammar rev D) ---

    @Test
    fun `ring size follows the width - 56 narrow, 72, 84 wide`() {
        assertEquals(56, tileRingDp(320f))
        assertEquals(56, tileRingDp(359.9f))
        assertEquals(72, tileRingDp(360f))
        assertEquals(72, tileRingDp(411f))
        assertEquals(84, tileRingDp(700f))
        assertEquals(84, tileRingDp(760f))
        assertEquals(12, tileRingGapDp(320f))
        assertEquals(14, tileRingGapDp(411f))
    }

    @Test
    fun `ring state - no 5h is the cross, a spent week greys it`() {
        assertEquals(GrammarRing.State.NOT_APPLICABLE, tileRingState(null, window(50.0)))
        assertEquals(GrammarRing.State.GREY, tileRingState(window(12.0), window(100.0)))
        assertEquals(GrammarRing.State.NORMAL, tileRingState(window(34.0), window(52.0)))
        assertEquals(GrammarRing.State.NORMAL, tileRingState(UsageWindow(null, null, null), null))
    }

    @Test
    fun `ring label names the window the ring no longer prints`() {
        val n = GrammarRing.State.NORMAL
        assertEquals("5-hour window, 34 percent", ringDescription(n, "34%", PaceText.Mode.USED, PaceText.VsTone.UNDER))
        assertEquals("5-hour window, 66 percent left", ringDescription(n, "66%", PaceText.Mode.LEFT, PaceText.VsTone.UNDER))
        assertEquals("5-hour window, 19 percent under pace", ringDescription(n, "19%", PaceText.Mode.VS_PACE, PaceText.VsTone.UNDER))
        assertEquals("5-hour window, 13 percent over pace", ringDescription(n, "13%", PaceText.Mode.VS_PACE, PaceText.VsTone.OVER))
        assertEquals("5-hour window, 100 percent", ringDescription(n, "100%", PaceText.Mode.VS_PACE, PaceText.VsTone.USAGE))
        assertEquals("5-hour window, no reading yet", ringDescription(n, "—", PaceText.Mode.USED, PaceText.VsTone.USAGE))
        assertEquals("No 5-hour window", ringDescription(GrammarRing.State.NOT_APPLICABLE, null, PaceText.Mode.USED, PaceText.VsTone.USAGE))
    }

    @Test
    fun `tap readout - behind, on the mark, and nothing when spent or unclocked`() {
        val len = 18_000_000L // 5h
        val now = 1_800_000_000_000L
        // 50% of the window gone, 34% used: the mark passed 34% 48 minutes ago.
        val under = UsageWindow(34.0, Instant.ofEpochMilli(now + 9_000_000L), null)
        assertEquals("48m behind pace", tapReadout(under, len, false, now))
        val on = UsageWindow(50.4, Instant.ofEpochMilli(now + 9_000_000L), null)
        assertEquals("On the pace mark", tapReadout(on, len, false, now))
        val over = UsageWindow(70.0, Instant.ofEpochMilli(now + 9_000_000L), null)
        assertTrue(tapReadout(over, len, false, now)!!.startsWith("Pace catches up "))
        assertNull(tapReadout(UsageWindow(100.0, Instant.ofEpochMilli(now + 9_000_000L), null), len, false, now))
        assertNull(tapReadout(UsageWindow(34.0, null, null), len, false, now))
        assertNull(tapReadout(null, len, false, now))
    }

    @Test
    fun `projection arc runs to the projected end, or to 100 if it gets there first`() {
        assertNull(tileProjectionPct(null))
        assertEquals(69.4, tileProjectionPct(Projection.Estimate(15.0, null, 69.4))!!, 1e-9)
        assertEquals(100.0, tileProjectionPct(Projection.Estimate(30.0, 1L, 100.0))!!, 1e-9)
    }

    // --- 6ee08e6 judge: the one "Usage" row and the screen agree in every stored state ---

    @Test
    fun `a legacy layout that hid the reported id reads as the tile shown, folded as the other was`() {
        val legacy = CardLayout(order, hidden = setOf(CardId.WEEKLY), more = setOf(CardId.SESSION))
        val present = setOf(CardId.WEEKLY, CardId.CREDITS)
        val seen = tileLayout(legacy, present)
        // The row (WEEKLY) reads shown and folded, exactly as the screen draws the tile.
        assertFalse(CardId.WEEKLY in seen.hidden)
        assertTrue(CardId.WEEKLY in seen.more)
        assertEquals(order, seen.order)
        assertEquals(DrawnCards(listOf(CardId.CREDITS), listOf(CardId.WEEKLY)), drawnCards(legacy, present))
        // Folding the tile back above More, the credits switch may hide again (the tile counts as shown).
        assertTrue(CardLayout.canHide(seen.copy(more = emptySet()), CardId.CREDITS, present))
        // Nothing to migrate in a consistent layout.
        val plain = CardLayout(order, hidden = emptySet(), more = emptySet())
        assertEquals(plain, tileLayout(plain, present))
        val both = CardLayout(order, hidden = setOf(CardId.SESSION, CardId.WEEKLY), more = emptySet())
        assertEquals(both, tileLayout(both, present))
    }

    @Test
    fun `the Usage row hides and shows both window ids`() {
        val shown = CardLayout(order, hidden = emptySet(), more = emptySet())
        val hidden = hideOrShow(shown, CardId.WEEKLY, show = false)
        assertTrue(hidden.hidden.containsAll(setOf(CardId.SESSION, CardId.WEEKLY)))
        assertEquals(DrawnCards(listOf(CardId.CREDITS), emptyList()), drawnCards(hidden, setOf(CardId.WEEKLY, CardId.CREDITS)))
        val back = hideOrShow(hidden, CardId.WEEKLY, show = true)
        assertTrue(back.hidden.none { it == CardId.SESSION || it == CardId.WEEKLY })
        assertEquals(setOf(CardId.CREDITS), hideOrShow(shown, CardId.CREDITS, show = false).hidden)
    }

    /** Robin, 2026-11-02: the title's three marks need 76 dp of marks and gaps beside the measured text. */
    @Test
    fun `the title's three marks and their fallback width`() {
        assertEquals(listOf(Provider.CLAUDE, Provider.CHATGPT, Provider.ANTIGRAVITY), AppTitleMarks.providers)
        assertEquals(176f, AppTitleMarks.width(100.dp).value, 0.01f)
    }
}
