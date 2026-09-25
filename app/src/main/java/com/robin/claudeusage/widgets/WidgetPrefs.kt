package com.robin.claudeusage.widgets

import android.content.Context
import android.content.SharedPreferences

/**
 * Per-widget settings (CCRM-78 (Widgets Reborn) §Config), in their own `widget_prefs`
 * file. **The keys are a permanent contract (R9)** — `w<id>.account`, `w<id>.window`,
 * `w<id>.bg`, `w<id>.v` — and so are the stored values below: a rename after release
 * would silently repoint or reset every user's placed widgets.
 *
 * An empty or missing account is the **unassigned** state (R5), never a guess stored on
 * the widget's behalf: a launcher that skips config, an unknown id after a launcher reset
 * or a restored id with no prefs all read as unassigned, and the face draws the first
 * account at draw time. Saving the config is what assigns.
 */
class WidgetPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** One widget's stored settings; [version] 0 means config was never saved. */
    data class Config(
        val accountKey: String?,
        val window: FaceWindow,
        val background: FaceBackground,
        val version: Int,
    ) {
        val assigned: Boolean get() = accountKey != null
    }

    fun read(id: Int): Config = Config(
        accountKey = prefs.getString(key(id, ACCOUNT), null)?.takeIf { it.isNotEmpty() },
        window = decodeWindow(prefs.getString(key(id, WINDOW), null)),
        background = decodeBackground(prefs.getString(key(id, BG), null)),
        version = prefs.getInt(key(id, VERSION), 0),
    )

    /** The config activity's Save: writes all four keys, `w<id>.v = 1` included. */
    fun save(id: Int, accountKey: String?, window: FaceWindow, background: FaceBackground) {
        prefs.edit()
            .putString(key(id, ACCOUNT), accountKey ?: "")
            .putString(key(id, WINDOW), encode(window))
            .putString(key(id, BG), encode(background))
            .putInt(key(id, VERSION), CURRENT_VERSION)
            .apply()
    }

    /** Number 4×2's `[5h | Weekly]` chips. */
    fun setWindow(id: Int, window: FaceWindow) {
        prefs.edit().putString(key(id, WINDOW), encode(window)).apply()
    }

    /** Number 4×2's account cycler: it stores the account *key*, so a reorder never repoints it. */
    fun setAccount(id: Int, accountKey: String) {
        prefs.edit().putString(key(id, ACCOUNT), accountKey).apply()
    }

    /** `onDeleted`: prune every key of each id. */
    fun delete(ids: IntArray) {
        val e = prefs.edit()
        for (id in ids) for (name in NAMES) e.remove(key(id, name))
        e.apply()
    }

    /**
     * `onRestored`: move each old id's keys to its new id. Everything is read before
     * anything is written, so an old id that is also someone's new id cannot be clobbered
     * mid-move. An old id with no prefs leaves its new id unassigned (R5).
     */
    fun remap(oldIds: IntArray, newIds: IntArray) {
        require(oldIds.size == newIds.size) { "oldIds and newIds pair up" }
        val moved = oldIds.map { old -> NAMES.associateWith { prefs.all[key(old, it)] } }
        val e = prefs.edit()
        for (old in oldIds) for (name in NAMES) e.remove(key(old, name))
        for ((i, new) in newIds.withIndex()) {
            for ((name, value) in moved[i]) when (value) {
                is String -> e.putString(key(new, name), value)
                is Int -> e.putInt(key(new, name), value)
                null -> e.remove(key(new, name))
            }
        }
        e.apply()
    }

    companion object {
        const val FILE = "widget_prefs"

        // R9: never renamed after release.
        const val ACCOUNT = "account"
        const val WINDOW = "window"
        const val BG = "bg"
        const val VERSION = "v"
        private val NAMES = listOf(ACCOUNT, WINDOW, BG, VERSION)

        const val CURRENT_VERSION = 1

        fun key(id: Int, name: String): String = "w$id.$name"

        // Stored values — as permanent as the keys.
        fun encode(w: FaceWindow): String = when (w) {
            FaceWindow.SESSION -> "5h"
            FaceWindow.WEEKLY -> "weekly"
        }

        fun encode(b: FaceBackground): String = when (b) {
            FaceBackground.SOLID -> "solid"
            FaceBackground.GRADIENT -> "gradient"
            FaceBackground.TRANSPARENT -> "transparent"
        }

        /** Anything unknown reads as the default: 5h, and Solid (Q4). */
        fun decodeWindow(s: String?): FaceWindow =
            if (s == "weekly") FaceWindow.WEEKLY else FaceWindow.SESSION

        fun decodeBackground(s: String?): FaceBackground = when (s) {
            "gradient" -> FaceBackground.GRADIENT
            "transparent" -> FaceBackground.TRANSPARENT
            else -> FaceBackground.SOLID
        }
    }
}
