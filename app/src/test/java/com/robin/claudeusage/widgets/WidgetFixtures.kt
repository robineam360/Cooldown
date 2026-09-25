package com.robin.claudeusage.widgets

import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.UsageData
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Delegates to [GalleryFixtures] (main source, CCRM-84 (Faces Gallery)) so a test and the
 * on-phone gallery build every state S1–S14 exactly the same way. Kept as a thin forwarding
 * object rather than removed so every existing test call site (`WidgetFixtures.forState`,
 * `.pro()`, `.TEAMS`, …) is unchanged.
 */
object WidgetFixtures {

    val ZONE: ZoneId get() = GalleryFixtures.ZONE
    val NOW: ZonedDateTime get() = GalleryFixtures.NOW
    val NOW_MS: Long get() = GalleryFixtures.NOW_MS
    val RESET_5H: Instant get() = GalleryFixtures.RESET_5H
    val RESET_WEEKLY: Instant get() = GalleryFixtures.RESET_WEEKLY
    const val MIN = GalleryFixtures.MIN

    const val PRO_ACCENT = GalleryFixtures.PRO_ACCENT
    const val TEAMS_ACCENT = GalleryFixtures.TEAMS_ACCENT
    const val PRODUCT_ACCENT = GalleryFixtures.PRODUCT_ACCENT
    const val GPT_ACCENT = GalleryFixtures.GPT_ACCENT

    fun both(session: Double?, weekly: Double = 20.0, sessionReset: Instant? = RESET_5H) =
        GalleryFixtures.both(session, weekly, sessionReset)

    fun weeklyOnly(pct: Double) = GalleryFixtures.weeklyOnly(pct)

    fun account(
        key: String,
        label: String,
        data: UsageData?,
        provider: Provider = Provider.CLAUDE,
        accent: Int = PRO_ACCENT,
        fetchedAt: Long = NOW_MS - 3 * MIN,
        auth: AuthState = AuthState.OK,
        free: Boolean = false,
        runsOut: Long? = null,
    ) = GalleryFixtures.account(key, label, data, provider, accent, fetchedAt, auth, free, runsOut)

    fun pro(data: UsageData? = both(38.0), runsOut: Long? = null) = GalleryFixtures.pro(data, runsOut)

    val TEAMS get() = GalleryFixtures.TEAMS
    val PRODUCT get() = GalleryFixtures.PRODUCT
    val CHATGPT get() = GalleryFixtures.CHATGPT
    val RESEARCH get() = GalleryFixtures.RESEARCH

    fun input(
        face: Face,
        accounts: List<AccountInput>,
        accountKey: String? = accounts.firstOrNull()?.key,
        window: FaceWindow = FaceWindow.SESSION,
        background: FaceBackground = FaceBackground.SOLID,
        dark: Boolean = true,
        left: Boolean = false,
        synthetic: Boolean = false,
        unavailable: Boolean = false,
        now: Long = NOW_MS,
    ) = GalleryFixtures.input(face, accounts, accountKey, window, background, dark, left, synthetic, unavailable, now)

    /**
     * The input that puts [face] into [state]. On the Strip a state lands on one ring —
     * Teams, the second — and every other account keeps its own reading (rev B).
     */
    fun forState(face: Face, state: StateId, dark: Boolean = true, bg: FaceBackground = FaceBackground.SOLID): FaceInput =
        GalleryFixtures.forState(face, state, dark, bg)
}
