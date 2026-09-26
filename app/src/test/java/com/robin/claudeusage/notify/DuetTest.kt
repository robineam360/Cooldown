package com.robin.claudeusage.notify

import com.robin.claudeusage.data.UsageCache
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the CCRM-62 (Duet Notification) numbers and the ring's account picker.
 *
 * Plain JUnit, no Robolectric: [Duet] is deliberately free of Android types so the three
 * decisions the design settled — the label clamp, the strip cap, and which account the
 * status-bar ring shows — can be checked without an emulator or a shadow `Context`.
 */
class DuetTest {

    // --- the label clamp: the label's own line across a 156 dp half (rev G) ----------

    @Test
    fun `the label takes the whole half less the mark, whatever the figure`() {
        // 156 − 14 mark − 4 gap. CCBG-39 (Inner Duet Squeeze): the figure sits on the line
        // below now, so "100%" and "2%" leave the label the same room.
        assertEquals(138, Duet.labelClampDp())
    }

    @Test
    fun `the condition dot takes its 11 dp from the label`() {
        assertEquals(127, Duet.labelClampDp(dots = 1))
    }

    @Test
    fun `the synthetic dot beside the condition dot takes another 11 dp`() {
        assertEquals(116, Duet.labelClampDp(dots = 2))
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
