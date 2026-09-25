package com.robin.claudeusage.widgets

import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The wireframe's fixtures (design/2026-09-25-widgets-reborn.html, §2's ST table): one
 * input per state S1–S14, for every face. "Now" is a Thursday, 6:29 PM; the 5h window
 * resets at 9:10 PM the same day and the weekly one on Saturday at 9:10 PM.
 */
object WidgetFixtures {

    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    val NOW: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 18, 29, 0, 0, ZONE) // a Thursday
    val NOW_MS: Long = NOW.toInstant().toEpochMilli()
    val RESET_5H: Instant = NOW.withHour(21).withMinute(10).toInstant()
    val RESET_WEEKLY: Instant = NOW.plusDays(2).withHour(21).withMinute(10).toInstant()
    const val MIN = 60_000L

    const val PRO_ACCENT = 0xFFE59980.toInt()
    const val TEAMS_ACCENT = 0xFFE59980.toInt()
    const val PRODUCT_ACCENT = 0xFFCE93D8.toInt()
    const val GPT_ACCENT = 0xFF19C39A.toInt()

    fun both(session: Double?, weekly: Double = 20.0, sessionReset: Instant? = RESET_5H) =
        UsageData(
            session = UsageWindow(session, sessionReset, null),
            weekly = UsageWindow(weekly, RESET_WEEKLY, null),
            modelCaps = emptyList(),
        )

    fun weeklyOnly(pct: Double) = UsageData(
        session = null, weekly = UsageWindow(pct, RESET_WEEKLY, null), modelCaps = emptyList(),
    )

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
    ) = AccountInput(
        key = key, label = label, provider = provider, accentArgb = accent, data = data,
        fetchedAt = fetchedAt, authState = auth, planUnsupported = free,
        sessionRunsOutAtMs = runsOut,
    )

    fun pro(data: UsageData? = both(38.0), runsOut: Long? = null) =
        account("pro", "Pro", data, runsOut = runsOut)

    val TEAMS = account("teams", "Teams", both(24.0), accent = TEAMS_ACCENT)
    val PRODUCT = account("product", "Product", both(8.0), accent = PRODUCT_ACCENT)
    val CHATGPT = account("chatgpt", "ChatGPT", weeklyOnly(31.0), Provider.CHATGPT, GPT_ACCENT)
    val RESEARCH = account("research", "Research", both(15.0), accent = 0xFF4DD0E1.toInt())

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
    ) = FaceInput(
        face = face, accountKey = accountKey, window = window, background = background,
        dark = dark, accounts = accounts, usageLeft = left, showOverPace = true,
        synthetic = synthetic, unavailable = unavailable, nowMs = now, zone = ZONE,
    )

    /**
     * The input that puts [face] into [state]. On the Strip a state lands on one ring —
     * Teams, the second — and every other account keeps its own reading (rev B).
     */
    fun forState(face: Face, state: StateId, dark: Boolean = true, bg: FaceBackground = FaceBackground.SOLID): FaceInput {
        if (face == Face.STRIP) return stripForState(state, dark, bg)
        val base = listOf(pro(), TEAMS, PRODUCT, CHATGPT)
        fun with(a: AccountInput, key: String? = a.key) =
            input(face, listOf(a) + base.drop(1), key, dark = dark, background = bg)
        return when (state) {
            StateId.S1 -> with(pro())
            StateId.S2 -> with(pro(both(62.0), runsOut = NOW_MS + 85 * MIN))
            StateId.S3 -> with(pro(both(100.0), runsOut = NOW_MS + 5 * MIN))
            StateId.S4 -> input(face, base, "chatgpt", dark = dark, background = bg)
            StateId.S5 -> with(pro(both(0.0, sessionReset = null)))
            StateId.S6 -> with(
                pro(both(71.0, sessionReset = Instant.ofEpochMilli(NOW_MS - 10 * MIN)))
                    .copy(fetchedAt = NOW_MS - 30 * MIN),
            )
            StateId.S7 -> with(pro().copy(authState = AuthState.REAUTH_NEEDED))
            StateId.S8 -> with(pro().copy(fetchedAt = NOW_MS - 7 * 60 * MIN))
            StateId.S9 -> input(face, base, "removed-account", dark = dark, background = bg)
            StateId.S10 -> input(face, base, null, dark = dark, background = bg)
            StateId.S11 -> with(pro()).copy(usageLeft = true)
            StateId.S12 -> with(pro().copy(planUnsupported = true))
            StateId.S13 -> with(pro()).copy(synthetic = true)
            StateId.S14 -> with(pro()).copy(unavailable = true)
        }
    }

    private fun stripForState(state: StateId, dark: Boolean, bg: FaceBackground): FaceInput {
        val t = when (state) {
            StateId.S2 -> TEAMS.copy(data = both(55.0))
            StateId.S3 -> TEAMS.copy(data = both(100.0))
            StateId.S5 -> TEAMS.copy(data = both(0.0, sessionReset = null))
            StateId.S6 -> TEAMS.copy(
                data = both(40.0, sessionReset = Instant.ofEpochMilli(NOW_MS - 10 * MIN)),
                fetchedAt = NOW_MS - 30 * MIN,
            )
            StateId.S7 -> TEAMS.copy(authState = AuthState.REAUTH_NEEDED)
            StateId.S8 -> TEAMS.copy(fetchedAt = NOW_MS - 7 * 60 * MIN)
            StateId.S12 -> TEAMS.copy(planUnsupported = true)
            else -> TEAMS
        }
        val accounts = when (state) {
            StateId.S9 -> listOf(pro(), PRODUCT, CHATGPT) // a removed account simply leaves
            StateId.S10 -> emptyList()
            else -> listOf(pro(), t, PRODUCT, CHATGPT)
        }
        return input(
            Face.STRIP, accounts, null, dark = dark, background = bg,
            left = state == StateId.S11, synthetic = state == StateId.S13,
            unavailable = state == StateId.S14,
        )
    }
}
