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

    // --- the label clamp: 156 dp per half, minus the figure ---------------------------

    @Test
    fun `label clamps at 72 dp, and at 56 when the figure is four characters`() {
        // A three-character figure at 30 sp bold takes ~54 dp of the 156 dp half.
        assertEquals(72, Duet.labelClampDp("42%"))
        assertEquals(72, Duet.labelClampDp("7%"))
        // "100%" takes ~74 dp instead, so the label gives back the difference.
        assertEquals(56, Duet.labelClampDp("100%"))
    }

    @Test
    fun `the no-reading placeholder is the roomiest figure there is`() {
        // The em dash is one character, so a broken or unread half keeps the full clamp —
        // the label is the only thing left on that half worth reading.
        assertEquals(72, Duet.labelClampDp("—"))
    }

    // --- the strip cap: ~120 dp of panel left under two header blocks -----------------

    @Test
    fun `the strip cap falls from three to two once a Second account is set`() {
        assertEquals(3, Duet.maxStrips(false))
        assertEquals(2, Duet.maxStrips(true))
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
