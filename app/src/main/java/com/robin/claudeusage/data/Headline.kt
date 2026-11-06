package com.robin.claudeusage.data

/**
 * CCRM-96 (Weekly First): the per-account Headline — which window a surface leads
 * with (spec/mobile BEHAVIOR-MOBILE §5). Cases: `spec/mobile/fixtures/headline/cases.json`.
 */
enum class Headline(val key: String) {
    WEEKLY("weekly"),
    FIVE_HOUR("fiveHour"),
    AUTO("auto");

    companion object {
        val DEFAULT = WEEKLY
        fun fromKey(key: String?): Headline = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

enum class HeadlineSurface {
    /** The pinned notification (each Duet half follows its own account). */
    NOTIFICATION,
    /** A widget's window choice when it is placed; stored per widget afterwards. */
    NEW_WIDGET,
    /** The app's default card order (a layout reordered in the sheet wins). */
    CARD_ORDER,
}

object HeadlineRule {

    /**
     * The window [surface] leads with: [WindowKind.WEEKLY] or [WindowKind.SESSION].
     * An account without a session window always leads with Weekly.
     */
    fun lead(
        setting: Headline,
        surface: HeadlineSurface,
        hasSession: Boolean,
        sessionBinding: Boolean,
    ): WindowKind {
        if (!hasSession) return WindowKind.WEEKLY
        val auto = if (sessionBinding) WindowKind.SESSION else WindowKind.WEEKLY
        return when (surface) {
            HeadlineSurface.NOTIFICATION -> when (setting) {
                Headline.WEEKLY -> WindowKind.WEEKLY
                Headline.FIVE_HOUR -> WindowKind.SESSION
                Headline.AUTO -> auto
            }
            HeadlineSurface.NEW_WIDGET, HeadlineSurface.CARD_ORDER ->
                if (setting == Headline.FIVE_HOUR) WindowKind.SESSION else WindowKind.WEEKLY
        }
    }
}
