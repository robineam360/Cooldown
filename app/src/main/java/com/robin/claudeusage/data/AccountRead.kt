package com.robin.claudeusage.data

/**
 * CCRM-96 (Weekly First): one account's v2.0 reading, resolved the same way for every
 * surface — whether the 5h binds (spec/core §6.2) and which window a surface leads with.
 */
object AccountRead {

    /** §3's projected 100% instant for the live 5h window, or null. */
    fun sessionHitsLimitAtMs(data: UsageData?, points: List<HistoryPoint>?): Long? {
        val reset = data?.session?.resetsAt?.toEpochMilli() ?: return null
        if (points == null) return null
        return Projection.forecast(Projection.sessionSamples(points, reset, Projection.SESSION_MS), reset, data.session?.percent, Projection.SESSION_MS)
            ?.hitsLimitAtMs
    }

    fun sessionBinding(data: UsageData?, sessionHitsLimitAtMs: Long?): Boolean =
        Pace.sessionBinding(data?.session?.percent, data?.weekly?.percent, sessionHitsLimitAtMs != null)

    fun lead(setting: Headline, surface: HeadlineSurface, data: UsageData?, binding: Boolean): WindowKind =
        HeadlineRule.lead(setting, surface, hasSession = data?.session != null, sessionBinding = binding)
}
