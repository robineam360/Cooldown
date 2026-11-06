package com.robin.claudeusage

import com.robin.claudeusage.data.CardId
import com.robin.claudeusage.data.CardLayout
import com.robin.claudeusage.data.Headline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * RUNBOOK-v2.0 Step 5: CCRM-96 (Weekly First)'s card order and the account sheet's copy
 * for CCRM-96 and CCRM-99 (Model Limits Toggle).
 */
class V20AppTest {

    private val weeklyFirst = listOf(CardId.WEEKLY, CardId.SESSION, CardId.CREDITS)

    @Test fun `an untouched layout leads with Weekly under Weekly and Auto`() {
        assertEquals(weeklyFirst, cardOrderLayout(CardLayout.DEFAULT, Headline.WEEKLY).order)
        assertEquals(weeklyFirst, cardOrderLayout(CardLayout.DEFAULT, Headline.AUTO).order)
    }

    @Test fun `Headline 5-hour keeps the 5h card on top`() {
        assertSame(CardLayout.DEFAULT, cardOrderLayout(CardLayout.DEFAULT, Headline.FIVE_HOUR))
    }

    @Test fun `hidden and folded cards keep their state when the order follows the Headline`() {
        val l = CardLayout.toMore(CardLayout.DEFAULT, CardId.CREDITS)
        val drawn = cardOrderLayout(l, Headline.WEEKLY)
        assertEquals(weeklyFirst, drawn.order)
        assertEquals(setOf(CardId.CREDITS), drawn.more)
    }

    @Test fun `a reordered layout wins over the Headline`() {
        val moved = CardLayout.move(CardLayout.DEFAULT, CardId.CREDITS, 0)
        assertSame(moved, cardOrderLayout(moved, Headline.WEEKLY))
        assertSame(moved, cardOrderLayout(moved, Headline.FIVE_HOUR))
    }

    @Test fun `headline caption explains Auto, or why the row is greyed`() {
        assertEquals(
            "Auto shows Weekly, and switches to 5h while 5h is past 90% or on course to run out before it resets.",
            headlineCaption(true),
        )
        assertEquals("This plan has no 5-hour window, so Weekly is always the headline.", headlineCaption(false))
    }

    @Test fun `model limits subtitle, shown and hidden`() {
        assertEquals("Fable · 30% this week", modelLimitsSubtitle(listOf("Fable" to 30.4), true))
        assertEquals(
            "Hidden: Fable. The limit still applies to that model; Cooldown just stops drawing it.",
            modelLimitsSubtitle(listOf("Fable" to 30.4), false),
        )
        assertEquals(
            "Hidden: Fable, Spark. The limits still apply to those models; Cooldown just stops drawing them.",
            modelLimitsSubtitle(listOf("Fable" to 30.0, "Spark" to null), false),
        )
    }
}
