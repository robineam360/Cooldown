package com.robin.claudeusage.data

/**
 * CCRM-98 (Summary Tab): the All tab's Weekly list, most room first
 * (spec/mobile BEHAVIOR-MOBILE §6). Cases: `spec/mobile/fixtures/summary/cases.json`.
 */
object SummaryRank {

    enum class Group { ELIGIBLE, HELD, SPENT, NO_READING }

    /** One account's inputs; [key] is opaque (a profile key in the app). */
    data class Entry(
        val key: String,
        /** Weekly used; null = no reading. */
        val usedPct: Double?,
        /** §6's elapsedPercent of the weekly window; null = no reset clock. */
        val elapsedPct: Double?,
        /** Weekly reset minus now; null = no reset clock. */
        val msUntilReset: Long?,
        /** Core §6.2's predicate, already evaluated. */
        val sessionBinding: Boolean,
        /** 5h reset minus now; null = no clock. */
        val sessionMsUntilReset: Long?,
    )

    data class Ranked(val entry: Entry, val group: Group)

    /** [entries] in tab order; the result is display order. */
    fun rank(entries: List<Entry>): List<Ranked> {
        val read = entries.filter { it.usedPct != null }
        val spent = read.filter { Pace.isSpent(it.usedPct) }
        val live = read - spent.toSet()
        // No reset clock means no Pace: such a row sorts after every clocked one, as on pace.
        val eligible = live.filter { !it.sessionBinding }.sortedWith(
            compareBy<Entry>({ e -> e.elapsedPct?.let { Pace.pace(e.usedPct!!, it) } ?: Double.MAX_VALUE },
                { it.msUntilReset ?: Long.MAX_VALUE }),
        )
        val held = live.filter { it.sessionBinding }.sortedBy { it.sessionMsUntilReset ?: Long.MAX_VALUE }
        return eligible.map { Ranked(it, Group.ELIGIBLE) } +
            held.map { Ranked(it, Group.HELD) } +
            spent.sortedBy { it.msUntilReset ?: Long.MAX_VALUE }.map { Ranked(it, Group.SPENT) } +
            entries.filter { it.usedPct == null }.map { Ranked(it, Group.NO_READING) }
    }

    /** The All tab exists with two or more accounts; so does its Appearance toggle. */
    fun tabShown(accountCount: Int): Boolean = accountCount >= 2

    /** True = launch opens the All tab; false = the last account looked at, as before. */
    fun opensOnSummary(accountCount: Int, openOnSummary: Boolean): Boolean =
        tabShown(accountCount) && openOnSummary
}
