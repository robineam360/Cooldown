package com.robin.claudeusage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCBG-50 (Degraded Store Notice), wireframe rev B §2: what the restart card says, by
 * each account's last change against what the file brought back.
 */
@RunWith(RobolectricTestRunner::class)
class HeldChangesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val earlier = "an earlier process"
    private val forgotten = mutableListOf<String>()

    @Before
    fun setUp() {
        HeldChanges.clearForTest(context)
        forgotten.clear()
    }

    private fun settle(tokens: Map<String, Boolean>, healthy: Boolean = true) =
        HeldChanges.settle(context, healthy, { tokens[it] }) { forgotten += it }

    private fun items() =
        (HeldChanges.card(context, setOf("a", "b", "c")) as? HeldChanges.Card.Changes)?.items

    @Test
    fun `each account's last change becomes one item, against the file`() {
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_IN, 100L, earlier)
        HeldChanges.record(context, "b", HeldChanges.Op.SIGN_IN, 200L, earlier)
        HeldChanges.record(context, "c", HeldChanges.Op.SIGN_OUT, 300L, earlier)
        settle(mapOf("a" to false, "b" to true, "c" to true))
        assertEquals(
            listOf(
                HeldChanges.Item("a", HeldChanges.Kind.SIGN_IN_LOST),
                HeldChanges.Item("b", HeldChanges.Kind.BACK_ON_OLDER),
                HeldChanges.Item("c", HeldChanges.Kind.SIGNED_IN_AGAIN),
            ),
            items(),
        )
        assertEquals(100L, (HeldChanges.card(context, setOf("a")) as HeldChanges.Card.Changes).at)
        // The file left "a" without a token, so its cached snapshot goes.
        assertEquals(listOf("a"), forgotten)
    }

    @Test
    fun `a sign-in then a Clear counts as a sign-out, and one the file agrees with shows nothing`() {
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_IN, 100L, earlier)
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_OUT, 150L, earlier)
        settle(mapOf("a" to false))
        assertNull(HeldChanges.card(context, setOf("a")))
        assertEquals(listOf("a"), forgotten)
    }

    @Test
    fun `this process's own record waits, and so does a degraded launch`() {
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_IN, 100L)
        settle(mapOf("a" to false))
        assertNull(HeldChanges.card(context, setOf("a")))
        HeldChanges.clearForTest(context)
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_IN, 100L, earlier)
        settle(mapOf("a" to false), healthy = false)
        assertNull(HeldChanges.card(context, setOf("a")))
        // The next working launch settles it.
        settle(mapOf("a" to false))
        assertEquals(listOf(HeldChanges.Item("a", HeldChanges.Kind.SIGN_IN_LOST)), items())
    }

    @Test
    fun `a settled record is gone, and a removed account drops out`() {
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_IN, 100L, earlier)
        HeldChanges.record(context, "z", HeldChanges.Op.SIGN_IN, 100L, earlier)
        settle(mapOf("a" to false)) // "z" no longer resolves
        settle(mapOf("a" to true)) // a second call finds nothing to settle
        assertEquals(listOf(HeldChanges.Item("a", HeldChanges.Kind.SIGN_IN_LOST)), items())
    }

    @Test
    fun `a reset comes first, and each card ends for good when dismissed`() {
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_IN, 100L, earlier)
        settle(mapOf("a" to false))
        HeldChanges.recordReset(context, 50L)
        val first = HeldChanges.card(context, setOf("a"))
        assertEquals(HeldChanges.Card.Reset(50L), first)
        HeldChanges.dismiss(context, first!!)
        val second = HeldChanges.card(context, setOf("a"))!!
        assertEquals(HeldChanges.Card.Changes(100L, listOf(HeldChanges.Item("a", HeldChanges.Kind.SIGN_IN_LOST))), second)
        HeldChanges.dismiss(context, second)
        assertNull(HeldChanges.card(context, setOf("a")))
    }

    private fun stored() =
        context.getSharedPreferences("store_held_changes", Context.MODE_PRIVATE).all.keys

    @Test
    fun `CCBG-51 an item whose account is gone is deleted, not left behind`() {
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_IN, 100L, earlier)
        HeldChanges.record(context, "b", HeldChanges.Op.SIGN_IN, 100L, earlier)
        settle(mapOf("a" to false, "b" to false))
        // Both accounts were removed before any card could be dismissed.
        assertNull(HeldChanges.card(context, emptySet()))
        assertEquals(emptySet<String>(), stored())
    }

    @Test
    fun `CCBG-51 a live account's item survives an orphan's deletion`() {
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_IN, 100L, earlier)
        HeldChanges.record(context, "z", HeldChanges.Op.SIGN_IN, 100L, earlier)
        settle(mapOf("a" to false, "z" to false))
        assertEquals(listOf(HeldChanges.Item("a", HeldChanges.Kind.SIGN_IN_LOST)), items())
        assertEquals(setOf("pendingAt", "pending.a"), stored())
    }

    @Test
    fun `CCBG-51 removing an account drops its held change and its item`() {
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_IN, 100L, earlier)
        settle(mapOf("a" to false))
        HeldChanges.record(context, "a", HeldChanges.Op.SIGN_OUT, 200L, earlier)
        HeldChanges.record(context, "b", HeldChanges.Op.SIGN_IN, 200L, earlier)
        HeldChanges.forgetAccount(context, "a")
        assertEquals(setOf("session", "firstAt", "op.b"), stored())
        HeldChanges.forgetAccount(context, "b")
        assertEquals(emptySet<String>(), stored())
    }
}
