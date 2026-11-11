package com.robin.claudeusage.notify

import com.robin.claudeusage.data.UsageCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the CCRM-62 (Duet Notification) numbers — since CCRM-105 (Duet Stack) the stacked layout's
 * width budget, state words and drop order — and the ring's account picker.
 *
 * Plain JUnit, no Robolectric: [Duet] is deliberately free of Android types so the three
 * decisions the design settled — the label clamp, the strip cap, and which account the
 * status-bar ring shows — can be checked without an emulator or a shadow `Context`.
 */
class DuetTest {

    // --- CCRM-105 (Duet Stack): the width budget, wireframe §1 -------------------------

    @Test
    fun `the chrome is 136 dp - two rings, their gaps and the figure slot`() {
        // 36 + 6 + 36 + 8 + 8 + 42: no halves any more.
        assertEquals(136f, Duet.CHROME_DP)
    }

    @Test
    fun `the bar takes the rest at each width the wireframe draws`() {
        assertEquals(192f, Duet.barDp(328f)) // 360 dp phone
        assertEquals(216f, Duet.barDp(352f)) // Fold 7 inner
        assertEquals(136f, Duet.barDp(272f)) // Fold 7 cover
        assertEquals(114f, Duet.barDp(250f)) // pessimistic
        assertEquals(40f, Duet.barDp(176f)) // the floor: 136 + 40
        assertEquals(40f, Duet.barDp(150f)) // never under 40; rings and the slot never shrink
    }

    @Test
    fun `the nominal content width follows the screen class`() {
        // Keyed on the smallest width (a phone in landscape stays a phone); a phone's shade takes
        // about 140 dp around the view (measured on AOSP at 360 and 420 dp, One UI's cover at 411).
        assertEquals(220f, Duet.contentDp(360))
        assertEquals(271f, Duet.contentDp(411)) // the Fold 7 cover: the v1.8 pass measured 272
        assertEquals(280f, Duet.contentDp(420))
        assertEquals(352f, Duet.contentDp(600))
        assertEquals(352f, Duet.contentDp(840)) // the Fold 7 inner, a tablet
        assertEquals(176f, Duet.contentDp(300)) // never under the 136 + 40 floor
    }

    @Test
    fun `on a 360 dp phone both tails drop and the names keep room`() {
        val bar = Duet.barDp(Duet.contentDp(360)) // 84 dp, measured
        assertEquals(84f, bar)
        assertFalse(Duet.keepsTail(bar, 0, "Work", "Held · 3:40 PM", 1f))
        assertFalse(Duet.keepsTail(bar, 0, "Atlas", "Out · Fri", 1f))
        assertTrue(Duet.nameRoomDp(bar, 0, "Held", 1f) >= Duet.NAME_FLOOR_DP)
        assertTrue(Duet.nameRoomDp(bar, 0, "Out", 1f) >= Duet.NAME_FLOOR_DP)
    }

    @Test
    fun `the figure slot holds a signed 18 sp figure`() {
        // "+19%" = 2.25 em; at 18 dp that is 40.5 dp, inside the 42 dp slot.
        val plus19 = TextFit.em("+19") + 0.58 * TextFit.em("%") // "%" at 58%
        assertTrue(TextFit.widthDp(plus19, Duet.FIGURE_SP, 1f) <= Duet.FIGURE_SLOT_DP)
    }

    // --- the state word -----------------------------------------------------------

    @Test
    fun `the state word follows the wireframe's precedence`() {
        // Sign in › No reading › Stale › Out · Thu › Held · 3:40 PM › Not started.
        assertEquals(Duet.Word.SIGN_IN, Duet.word(true, true, true, true, true, true))
        assertEquals(Duet.Word.NO_READING, Duet.word(false, true, true, true, true, true))
        assertEquals(Duet.Word.STALE, Duet.word(false, false, true, true, true, true))
        assertEquals(Duet.Word.OUT, Duet.word(false, false, false, true, true, true))
        assertEquals(Duet.Word.HELD, Duet.word(false, false, false, false, true, true))
        assertEquals(Duet.Word.NOT_STARTED, Duet.word(false, false, false, false, false, true))
        assertNull(Duet.word(false, false, false, false, false, false))
    }

    @Test
    fun `only Held and Out carry a tail`() {
        assertEquals("Held · 3:40 PM" to "Held", Duet.wordText(Duet.Word.HELD, "3:40 PM"))
        assertEquals("Out · Thu" to "Out", Duet.wordText(Duet.Word.OUT, "Thu"))
        assertEquals("Held" to "Held", Duet.wordText(Duet.Word.HELD, null))
        assertEquals("Sign in" to "Sign in", Duet.wordText(Duet.Word.SIGN_IN, "x"))
        assertEquals("No reading" to "No reading", Duet.wordText(Duet.Word.NO_READING, null))
        assertEquals("Stale" to "Stale", Duet.wordText(Duet.Word.STALE, null))
        assertEquals("Not started" to "Not started", Duet.wordText(Duet.Word.NOT_STARTED, null))
    }

    // --- the narrow drop order: bar, then the name to 24 dp, then the word's tail ----

    @Test
    fun `at the cover the name keeps 42 dp beside Held 3_40 PM`() {
        // Wireframe table, 272 dp: "name room 118 dp; 42 dp beside 'Held · 3:40 PM'".
        assertEquals(118f, Duet.nameRoomDp(136f, 0, null, 1f), 0.01f)
        assertEquals(42f, Duet.nameRoomDp(136f, 0, "Held · 3:40 PM", 1f), 1f)
        assertTrue(Duet.keepsTail(136f, 0, "Personal Max 20x", "Held · 3:40 PM", 1f))
    }

    @Test
    fun `at 250 dp the long name is at its floor, so Held drops its tail and Out keeps it`() {
        val bar = Duet.barDp(250f)
        assertFalse(Duet.keepsTail(bar, 0, "Personal Max 20x", "Held · 3:40 PM", 1f))
        assertTrue(Duet.keepsTail(bar, 0, "Atlas", "Out · Thu", 1f))
    }

    @Test
    fun `at 226 dp both words drop their tails`() {
        val bar = Duet.barDp(226f)
        assertFalse(Duet.keepsTail(bar, 0, "Personal Max 20x", "Held · 3:40 PM", 1f))
        assertFalse(Duet.keepsTail(bar, 0, "Atlas", "Out · Thu", 1f))
    }

    @Test
    fun `a name shorter than the floor needs only its own width`() {
        // "Al" is about 14 dp at 12 dp: the tail stays while the name itself still fits.
        val bar = Duet.barDp(226f)
        assertTrue(Duet.keepsTail(bar, 0, "Al", "Out · Thu", 1f))
    }

    @Test
    fun `a dot takes its 11 dp from the name, not from the word`() {
        assertEquals(Duet.nameRoomDp(136f, 0, "Held", 1f) - 11f, Duet.nameRoomDp(136f, 1, "Held", 1f), 0.01f)
    }

    // --- large fonts: the collapsed text is capped in dp ----------------------------

    @Test
    fun `the collapsed text follows the font scale up to its caps`() {
        assertEquals(12f, Duet.cappedDp(Duet.NAME_SP, 1f, Duet.NAME_CAP_DP))
        assertEquals(13.5f, Duet.cappedDp(Duet.NAME_SP, 1.3f, Duet.NAME_CAP_DP))
        assertEquals(13.5f, Duet.cappedDp(Duet.NAME_SP, 2f, Duet.NAME_CAP_DP))
        assertEquals(12f, Duet.cappedDp(Duet.WORD_SP, 2f, Duet.WORD_CAP_DP))
        assertEquals(18f, Duet.cappedDp(Duet.FIGURE_SP, 2f, Duet.FIGURE_CAP_DP))
        assertEquals(15.3f, Duet.cappedDp(Duet.FIGURE_SP, 0.85f, Duet.FIGURE_CAP_DP), 0.001f)
    }

    @Test
    fun `at font scale 2 the capped word still leaves the name room at the cover`() {
        // "Held · 3:40 PM" at the 12 dp cap is about 76 dp: 136 − 18 − 6 − 76 ≈ 36 dp of name.
        assertTrue(Duet.keepsTail(136f, 0, "Personal", "Held · 3:40 PM", 2f))
        assertTrue(Duet.nameRoomDp(136f, 0, "Held · 3:40 PM", 2f) in 34f..38f)
    }

    // --- the strip cap: ~120 dp of panel left under two header blocks -----------------

    @Test
    fun `the strip cap falls from three to one once a Second account is set`() {
        assertEquals(3, Duet.maxStrips(false))
        // CCBG-26 (Panel Scaling): one one-line strip is what ~120 dp holds beside the
        // two Weekly rows; anything more folds into the "+ n more" tail.
        assertEquals(1, Duet.maxStrips(true))
        // The single-account cap must stay the CCRM-44 (One Surface) constant itself,
        // not a copy of it that can drift.
        assertEquals(Conditions.MAX_STRIPS, Duet.maxStrips(false))
    }

    // --- which account the ring shows -------------------------------------------------

    @Test
    fun `First and Second are taken literally, whatever the numbers say`() {
        assertEquals(Duet.Slot.FIRST, Duet.ringAccount(UsageCache.RING_FIRST, 12.0, 99.0))
        assertEquals(Duet.Slot.SECOND, Duet.ringAccount(UsageCache.RING_SECOND, 99.0, 12.0))
        // Explicit choices hold even with no reading at all — the ring then draws its
        // no-data face for that account rather than borrowing the other's number.
        assertEquals(Duet.Slot.FIRST, Duet.ringAccount(UsageCache.RING_FIRST, null, 63.0))
        assertEquals(Duet.Slot.SECOND, Duet.ringAccount(UsageCache.RING_SECOND, 63.0, null))
    }

    @Test
    fun `Whichever is higher picks the larger headline percentage`() {
        assertEquals(Duet.Slot.SECOND, Duet.ringAccount(UsageCache.RING_HIGHER, 42.0, 63.0))
        assertEquals(Duet.Slot.FIRST, Duet.ringAccount(UsageCache.RING_HIGHER, 63.0, 42.0))
        // Strictly "is Second ahead of First", so a dead heat cannot make the glyph
        // flicker between two accounts on successive polls.
        assertEquals(Duet.Slot.FIRST, Duet.ringAccount(UsageCache.RING_HIGHER, 50.0, 50.0))
    }

    @Test
    fun `Whichever is higher hands a null side to the other, and both nulls to First`() {
        assertEquals(Duet.Slot.SECOND, Duet.ringAccount(UsageCache.RING_HIGHER, null, 5.0))
        assertEquals(Duet.Slot.FIRST, Duet.ringAccount(UsageCache.RING_HIGHER, 5.0, null))
        assertEquals(Duet.Slot.FIRST, Duet.ringAccount(UsageCache.RING_HIGHER, null, null))
        // Zero is a reading and null is not: an account at 0% still beats one with no
        // reading, because the ring can honestly draw 0.
        assertEquals(Duet.Slot.SECOND, Duet.ringAccount(UsageCache.RING_HIGHER, null, 0.0))
    }

    @Test
    fun `an unrecognised stored mode falls back to First`() {
        // The setting's default, so a value written by a future build (or a corrupted
        // pref) shows the account the picker itself calls First.
        assertEquals(Duet.Slot.FIRST, Duet.ringAccount("whatever", 10.0, 90.0))
    }
}
