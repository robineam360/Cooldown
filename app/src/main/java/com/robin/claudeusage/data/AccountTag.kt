package com.robin.claudeusage.data

/**
 * CCRM-110 (Account Tags), as amended 2026-11-11: every account is **Personal or Work** — there is no "None". The tag
 * is chosen when the account is added (Personal pre-selected) and changed from the account's menu. It is stored
 * additively as `personal` / `work` ([ProfileRegistry]'s `"t"` field); a missing, empty or unknown stored value
 * **reads as Personal**, so every account an earlier version saved becomes Personal at upgrade with nothing written,
 * and an unknown value survives a round trip untouched. BEHAVIOR-MOBILE §6; cases in
 * `spec/mobile/fixtures/tags/cases.json`.
 */
enum class AccountTag(val key: String, val label: String) {
    PERSONAL("personal", "Personal"),
    WORK("work", "Work");

    companion object {
        /** A stored value read as a tag: anything but `work` is Personal. */
        fun read(raw: String?): AccountTag = entries.firstOrNull { it.key == raw } ?: PERSONAL
    }
}

/**
 * The Summary's All · Personal · Work control (CCRM-110), remembered as `summaryFilter` (`all`, `personal`, `work`).
 */
enum class SummaryFilter(val key: String, val label: String, val tag: AccountTag?) {
    ALL("all", "All", null),
    PERSONAL("personal", "Personal", AccountTag.PERSONAL),
    WORK("work", "Work", AccountTag.WORK);

    /** The share snapshot's header ("All accounts", "Personal accounts", "Work accounts"). */
    val snapshotHeader: String get() = "$label accounts"

    fun matches(tag: AccountTag): Boolean = this.tag == null || this.tag == tag

    companion object {
        fun parse(raw: String?): SummaryFilter? = entries.firstOrNull { it.key == raw }
    }
}

/** The filter's rules, pure (BEHAVIOR-MOBILE §6); [UsageCache] carries the stored value. */
object SummaryFilters {

    /** The control exists while the Summary does: two or more accounts. */
    fun controlShown(accountCount: Int): Boolean = accountCount >= 2

    /** A segment with no accounts is disabled; All never is. */
    fun enabled(filter: SummaryFilter, tags: List<AccountTag>): Boolean =
        filter.tag == null || tags.any { it == filter.tag }

    /**
     * The filter in force: the remembered value when it names an enabled segment of a shown control, otherwise
     * All (a missing, unknown or emptied value, and any value while there is no Summary).
     */
    fun inForce(stored: String?, tags: List<AccountTag>): SummaryFilter {
        if (!controlShown(tags.size)) return SummaryFilter.ALL
        val f = SummaryFilter.parse(stored) ?: return SummaryFilter.ALL
        return if (enabled(f, tags)) f else SummaryFilter.ALL
    }

    /**
     * The memory rule's one fallback: a stored value that the filter in force does not match — unknown, or its group
     * has no accounts — is replaced by `all`. A missing value stays missing; nothing is evaluated without a Summary
     * (the value is returned as it is). Marking an account later never brings the earlier choice back: only a tap on
     * a segment writes anything else.
     */
    fun settled(stored: String?, tags: List<AccountTag>): String? {
        if (stored == null || !controlShown(tags.size)) return stored
        return if (inForce(stored, tags).key == stored) stored else SummaryFilter.ALL.key
    }

    /** The line a tap on a disabled segment shows under the header for 3 s. */
    fun disabledNote(filter: SummaryFilter): String = "No ${filter.label} accounts yet. Tag one in Settings."

    /** How long [disabledNote] stays (the readout's own timing). */
    const val NOTE_MS = 3_000L
}
