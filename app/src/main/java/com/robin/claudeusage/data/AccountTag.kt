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

    /** TalkBack for a segment: "Personal, 3 accounts"; one is "1 account"; none is the bare word (the state says "no accounts"). */
    fun description(label: String, count: Int): String = when (count) {
        0 -> label
        1 -> "$label, 1 account"
        else -> "$label, $count accounts"
    }

    /** The line a tap on a disabled segment shows under the header for 3 s. */
    fun disabledNote(filter: SummaryFilter): String = "No ${filter.label} accounts yet. Tag one in Settings."

    /** How long [disabledNote] stays (the readout's own timing). */
    const val NOTE_MS = 3_000L
}

/**
 * CCRM-118 (Header Glyphs), "Tag marks": Appearance decides, per tag, whether the tag's mark is drawn wherever a tag
 * shows as a mark (tab badge, Summary row glyph, snapshot column). Stored as `tagMarks.personal` / `tagMarks.work`,
 * absent = on (v1.9 ignores them). The column rule is pure so `spec/mobile/fixtures/tags/cases.json` → `marks` pins it.
 */
object TagMarks {
    const val KEY_PERSONAL = "tagMarks.personal"
    const val KEY_WORK = "tagMarks.work"

    fun key(tag: AccountTag): String = if (tag == AccountTag.WORK) KEY_WORK else KEY_PERSONAL

    /** Both on, the default. */
    val ALL_ON: Map<AccountTag, Boolean> = mapOf(AccountTag.PERSONAL to true, AccountTag.WORK to true)

    /** The glyph column exists while any row shown has a tag whose mark is on. */
    fun column(rows: List<AccountTag>, marks: Map<AccountTag, Boolean>): Boolean = rows.any { marks[it] != false }

    /** Per row while the column exists: true = the glyph, false = a blank slot of its width; null = no column. */
    fun slots(rows: List<AccountTag>, marks: Map<AccountTag, Boolean>): List<Boolean>? =
        if (column(rows, marks)) rows.map { marks[it] != false } else null

    /** The snapshot's column: the rows carry both tags and at least one of them is marked. */
    fun snapshotColumn(rows: List<AccountTag>, marks: Map<AccountTag, Boolean>): Boolean =
        rows.toSet().size == 2 && column(rows, marks)
}
