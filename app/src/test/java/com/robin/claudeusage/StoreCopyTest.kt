package com.robin.claudeusage

import com.robin.claudeusage.data.CredentialStoreOpener.State
import com.robin.claudeusage.data.HeldChanges
import com.robin.claudeusage.data.HeldChanges.Item
import com.robin.claudeusage.data.HeldChanges.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CCBG-50 (Degraded Store Notice): the words exactly as wireframe rev B draws them. */
class StoreCopyTest {

    private val clock: (Long) -> String = { if (it == 1L) "9:41" else "9:52" }
    private val labels = mapOf("p" to "Personal", "w" to "Work")

    @Test
    fun `the four wordings of the notice`() {
        assertNull(StoreCopy.notice(State(), clock))
        val locked = StoreCopy.notice(State(healthy = false, errorName = "KeyStoreException", checkedAt = 1L), clock)!!
        assertEquals("Cooldown can't open your saved sign-ins", locked.title)
        assertEquals("KeyStoreException · checked 9:41", locked.evidence)
        assertTrue(locked.canRetry)

        val held = StoreCopy.notice(State(healthy = false, errorName = "X", heldSince = 2L), clock)!!
        assertEquals("Changes made now last only until Cooldown restarts", held.title)
        assertTrue(held.body.endsWith("and the sign-ins saved before come back."))
        assertEquals("Held in memory since 9:52", held.evidence)
        assertFalse(held.canRetry)

        val failed = StoreCopy.notice(State(healthy = false, resetFailed = true, errorName = "KeyStoreException"), clock)!!
        assertEquals("Cooldown couldn't set up secure storage", failed.title)
        assertEquals("Reset failed · KeyStoreException", failed.evidence)
        assertFalse(failed.canRetry)

        val heldReset = StoreCopy.notice(State(healthy = false, resetFailed = true, heldSince = 2L), clock)!!
        assertFalse("no promise that anything comes back", heldReset.body.contains("come back"))
    }

    @Test
    fun `the restart card, one account at a time`() {
        fun one(kind: Kind, whenText: String = "today at 9:52") =
            StoreCopy.card(HeldChanges.Card.Changes(0L, listOf(Item("w", kind))), whenText) { labels.getValue(it) }
        assertEquals("Your Work sign-in from today at 9:52 wasn't kept", one(Kind.SIGN_IN_LOST).title)
        assertEquals("Work is back on the sign-in saved before", one(Kind.BACK_ON_OLDER).title)
        assertEquals("Work is signed in again", one(Kind.SIGNED_IN_AGAIN).title)
        assertTrue(one(Kind.SIGNED_IN_AGAIN).detail.startsWith("You signed out of Work today at 9:52 while"))
        // "from on 28 Sep" reads wrong.
        assertEquals("Your Work sign-in from 28 Sep wasn't kept", one(Kind.SIGN_IN_LOST, "on 28 Sep").title)
    }

    @Test
    fun `the restart card, two accounts, sign-outs first`() {
        val c = StoreCopy.card(
            HeldChanges.Card.Changes(0L, listOf(Item("p", Kind.SIGN_IN_LOST), Item("w", Kind.SIGNED_IN_AGAIN))),
            "today at 9:52",
        ) { labels.getValue(it) }
        assertEquals("2 sign-in changes from today at 9:52 weren't kept", c.title)
        assertEquals(
            "Secure storage wasn't responding, so they lasted only until the app closed: you signed " +
                "out of Work and signed in to Personal. Check Settings → Accounts.",
            c.detail,
        )
    }

    @Test
    fun `the reset card and the Clear line`() {
        assertEquals("Your saved sign-ins couldn't be read", StoreCopy.card(HeldChanges.Card.Reset(0L), "") { it }.title)
        assertEquals("Work signed out — until Cooldown restarts.", StoreCopy.clearedMessage("Work", true))
        assertEquals("Work signed out.", StoreCopy.clearedMessage("Work", false))
    }
}
