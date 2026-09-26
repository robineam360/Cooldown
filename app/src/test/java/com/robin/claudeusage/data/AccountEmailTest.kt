package com.robin.claudeusage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CCBG-34 (Account Display Name): the approved wireframe's Q1, Q3 and Q4. */
class AccountEmailTest {

    @Test
    fun splitKeepsTheDomainWhole() {
        assertEquals("robin" to "@eam360.com", AccountEmail.split("robin@eam360.com"))
        assertEquals("no-at" to "", AccountEmail.split("no-at"))
    }

    @Test
    fun theLineIsSuppressedWhenTheNameIsTheEmail() {
        assertTrue(AccountEmail.shownUnder("Work", "robin@eam360.com"))
        assertFalse(AccountEmail.shownUnder("Robin@EAM360.com", "robin@eam360.com"))
        assertFalse(AccountEmail.shownUnder("Work", null))
    }

    @Test
    fun theSuggestionNeverNamesTwoLoginsAlike() {
        assertEquals("robin", AccountEmail.suggestion("robin@eam360.com", emptyList(), "Account 4", 16))
        assertEquals("eam360", AccountEmail.suggestion("robin@eam360.com", listOf("robin@gmail.com"), "Account 4", 16))
        assertEquals("gmail", AccountEmail.suggestion("Robin@gmail.com", listOf("robin@eam360.com"), "Account 4", 16))
        assertNull(AccountEmail.suggestion("robin@eam360.com", emptyList(), "robin", 16))
        assertNull(AccountEmail.suggestion(null, emptyList(), "Work", 16))
        assertEquals(16, AccountEmail.suggestion("robin.richard.rajan.long@x.com", emptyList(), "W", 16)!!.length)
    }
}
