package com.robin.claudeusage

import com.robin.claudeusage.data.CardId
import com.robin.claudeusage.data.ModelCap
import com.robin.claudeusage.data.SpendCredits
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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
    fun `each window contributes its own card`() {
        assertEquals(
            setOf(CardId.SESSION, CardId.WEEKLY),
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
}
