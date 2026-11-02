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
        // No reset clock means no Pace: such a row stays eligible and sorts after every clocked one.
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

    /**
     * Appearance › Summary order (Robin, 2026-11-02; BEHAVIOR-MOBILE §6): the list in [order]. Every
     * row keeps the group [rank] gives it (its words and styling); only the order changes. [entries]
     * are in your order (the account order). Cases: `fixtures/summary/cases.json` → `order`.
     */
    fun ordered(entries: List<Entry>, order: SummaryOrder): List<Ranked> {
        val ranked = rank(entries)
        if (order == SummaryOrder.MOST_ROOM) return ranked
        val group = ranked.associate { it.entry.key to it.group }
        val sorted = when (order) {
            SummaryOrder.YOURS, SummaryOrder.MOST_ROOM -> entries
            // Stable sorts: ties keep your order; no clock / no reading go last, in your order.
            SummaryOrder.RESETS_SOONEST -> entries.sortedBy { it.msUntilReset ?: Long.MAX_VALUE }
            SummaryOrder.MOST_USED -> entries.sortedByDescending { it.usedPct ?: Double.NEGATIVE_INFINITY }
        }
        return sorted.map { Ranked(it, group.getValue(it.key)) }
    }

    /** The All tab exists with two or more accounts; so does its Appearance toggle. */
    fun tabShown(accountCount: Int): Boolean = accountCount >= 2

    /** True = launch opens the All tab; false = the last account looked at, as before. */
    fun opensOnSummary(accountCount: Int, openOnSummary: Boolean): Boolean =
        tabShown(accountCount) && openOnSummary
}

/**
 * Appearance › Summary order (Robin, 2026-11-02): [YOURS] (default) the account order; [MOST_ROOM] the
 * §6 ranking; [RESETS_SOONEST] the Weekly that resets next first; [MOST_USED] the highest Weekly first.
 * [header] is how the list's header names it.
 */
enum class SummaryOrder(val key: String, val label: String, val header: String) {
    YOURS("yours", "Your order", "your order"),
    MOST_ROOM("room", "Most room first", "most room first"),
    RESETS_SOONEST("resets", "Resets soonest", "resets soonest"),
    MOST_USED("used", "Most used", "most used");

    companion object {
        val DEFAULT = YOURS
        fun fromKey(key: String?): SummaryOrder = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
