package com.robin.claudeusage.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The three cards the CCRM-72 (Main Screen Redesign) layout sheet can show, hide or fold
 * behind More: the 5-hour window, the 7-day window, and the usage-credits card.
 */
enum class CardId(val key: String) {
    SESSION("session"),
    WEEKLY("weekly"),
    CREDITS("credits"),
    ;

    companion object {
        fun fromKey(key: String?): CardId? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Per-account card layout — CCRM-25 (Card Layout) and CCRM-35 (Layout Reset), built under
 * CCRM-72 (Main Screen Redesign): which cards show, in what order, and which are folded
 * behind the "More" disclosure.
 *
 * [order] is every [CardId] in display order. [hidden] cards render nowhere at all;
 * [more] cards render collapsed under the "More" disclosure at the foot of the list. A
 * card is shown above More only when it's in [order] and in neither set.
 *
 * All the interesting logic is in the [Companion] as pure functions over this value —
 * [move]/[hide]/[show]/[toMore]/[toMain] each keep the shape valid by running [normalize]
 * on their result, mirroring the pure-Companion pattern in `ProfileRegistry`. **Every
 * mutator returns the same instance (`===`) on a genuine no-op**, so a caller can skip a
 * write the way `ProfileRegistry.move` does.
 */
data class CardLayout(
    val order: List<CardId>,
    val hidden: Set<CardId>,
    val more: Set<CardId>,
) {
    companion object {

        /** Comfortable's untouched layout: every card shown, in declaration order. */
        val DEFAULT = CardLayout(CardId.entries, emptySet(), emptySet())

        /**
         * Reorders [id] to [toIndex] in [l]'s [CardLayout.order] — the layout sheet's drag,
         * in the `ProfileRegistry.move` idiom. [toIndex] clamps to the list's valid range
         * rather than throwing. Returns [l] itself when [id] is unknown to [l]'s order, or
         * is already at [toIndex].
         */
        fun move(l: CardLayout, id: CardId, toIndex: Int): CardLayout {
            val from = l.order.indexOf(id)
            if (from < 0) return l
            val target = toIndex.coerceIn(0, l.order.lastIndex)
            if (target == from) return l
            val reordered = l.order.toMutableList()
            reordered.removeAt(from)
            reordered.add(target, id)
            return l.copy(order = reordered)
        }

        /** Hides [id] entirely. No-op (same instance) if it's already hidden. */
        fun hide(l: CardLayout, id: CardId): CardLayout {
            if (id in l.hidden) return l
            return normalize(l.copy(hidden = l.hidden + id))
        }

        /** Un-hides [id]. No-op (same instance) if it isn't hidden. */
        fun show(l: CardLayout, id: CardId): CardLayout {
            if (id !in l.hidden) return l
            return normalize(l.copy(hidden = l.hidden - id))
        }

        /** Folds [id] behind More. No-op (same instance) if it's already there. */
        fun toMore(l: CardLayout, id: CardId): CardLayout {
            if (id in l.more) return l
            return normalize(l.copy(more = l.more + id))
        }

        /** Unfolds [id] from behind More, back above it. No-op if it isn't there. */
        fun toMain(l: CardLayout, id: CardId): CardLayout {
            if (id !in l.more) return l
            return normalize(l.copy(more = l.more - id))
        }

        /**
         * Restores every invariant a hand-built or decoded [CardLayout] might not hold:
         * drops ids [order] carries that aren't in [CardId.entries]; appends any missing
         * id at the end of [order]; drops anything from [hidden] and [more] that isn't in
         * [order]; drops anything from [more] that's also [hidden] (hidden wins); and
         * enforces the never-blank invariant — **at least one card stays shown above
         * More** (in [order], not [hidden], not [more]). When that's violated, the first
         * card in [order] is cleared from both sets rather than the whole layout resetting,
         * so a single bad entry doesn't cost the rest of the customisation.
         *
         * Returns [l] itself — the identical instance — when nothing changed, the same
         * `===`-checkable contract as `ProfileRegistry.move`.
         */
        fun normalize(l: CardLayout): CardLayout {
            val order = LinkedHashSet<CardId>().apply {
                addAll(l.order.filter { it in CardId.entries })
                addAll(CardId.entries)
            }.toList()

            val hidden = l.hidden.filterTo(LinkedHashSet()) { it in order }
            val more = l.more.filterTo(LinkedHashSet()) { it in order }
            more.removeAll(hidden)

            val shownAboveMore = order.any { it !in hidden && it !in more }
            if (!shownAboveMore && order.isNotEmpty()) {
                val first = order.first()
                hidden.remove(first)
                more.remove(first)
            }

            val hiddenResult = hidden.toSet()
            val moreResult = more.toSet()
            return if (order == l.order && hiddenResult == l.hidden && moreResult == l.more) l
            else CardLayout(order, hiddenResult, moreResult)
        }

        /**
         * True when hiding [id] would still leave a card shown above More — the layout
         * sheet disables the hide switch otherwise, rather than letting [hide] silently
         * restore a different card via [normalize]'s rescue.
         */
        fun canHide(l: CardLayout, id: CardId): Boolean {
            val n = normalize(l)
            val hidden = n.hidden + id
            return n.order.any { it !in hidden && it !in n.more }
        }

        /**
         * True when folding [id] behind More would still leave a card shown above More —
         * the layout sheet disables the fold switch otherwise.
         */
        fun canFold(l: CardLayout, id: CardId): Boolean {
            val n = normalize(l)
            val more = n.more + id
            return n.order.any { it !in n.hidden && it !in more }
        }

        /** `{"o":["session","weekly","credits"],"h":[],"m":["credits"]}` */
        fun encode(l: CardLayout): String {
            val order = JSONArray()
            for (id in l.order) order.put(id.key)
            val hidden = JSONArray()
            for (id in CardId.entries) if (id in l.hidden) hidden.put(id.key)
            val more = JSONArray()
            for (id in CardId.entries) if (id in l.more) more.put(id.key)
            return JSONObject().put("o", order).put("h", hidden).put("m", more).toString()
        }

        /** Null, blank or garbage decodes to [DEFAULT]; every result is [normalize]d. */
        fun decode(s: String?): CardLayout {
            if (s.isNullOrBlank()) return DEFAULT
            return try {
                val obj = JSONObject(s)
                val order = obj.optJSONArray("o")?.ids() ?: emptyList()
                val hidden = obj.optJSONArray("h")?.ids()?.toSet() ?: emptySet()
                val more = obj.optJSONArray("m")?.ids()?.toSet() ?: emptySet()
                normalize(CardLayout(order, hidden, more))
            } catch (_: Exception) {
                DEFAULT
            }
        }

        private fun JSONArray.ids(): List<CardId> =
            (0 until length()).mapNotNull { i -> CardId.fromKey(optString(i)) }
    }
}
