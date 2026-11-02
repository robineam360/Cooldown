package com.robin.claudeusage.data

/**
 * CCRM-96 (Weekly First): the one-time "Weekly now leads" notice, owed only to installs
 * that had an account before v2.0. Stored as one string so a v1.9 downgrade ignores it.
 */
object WeeklyFirstNotice {
    /** Owed: shown on the next app open, then [DONE]. */
    const val PENDING = "pending"
    const val DONE = "done"

    /** The value written at the first v2.0 process start. */
    fun settle(hadAccountsBefore: Boolean): String = if (hadAccountsBefore) PENDING else DONE
}
