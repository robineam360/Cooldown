package com.robin.claudeusage.widgets

import android.os.RemoteException
import android.os.TransactionTooLargeException
import android.widget.RemoteViews

/**
 * The one way a widget face reaches the launcher (CCRM-78 (Widgets Reborn), R10).
 *
 * A composed size map can exceed what the host will take — its bitmap-memory cap
 * (`IllegalArgumentException`), the Binder transaction limit
 * (`TransactionTooLargeException`), or either of those rethrown by `AppWidgetManager` as a
 * `RuntimeException` whose cause chain holds a `RemoteException`. On any of the three the
 * update is tried **once more** with a single-size face at the widget's current size. If
 * that throws too, both failures are logged and the launcher keeps its last good face.
 * Nothing retries in a loop, and nothing escapes: a provider or receiver that threw would
 * take every other placed widget's update down with it.
 */
object SafeUpdate {

    /** `AppWidgetManager.updateAppWidget`, injectable so the tests can make it throw. */
    fun interface Updater {
        fun update(appWidgetId: Int, views: RemoteViews)
    }

    enum class Outcome { UPDATED, FELL_BACK, FAILED }

    /**
     * @param full the composed `RemoteViews(Map<SizeF, RemoteViews>)`.
     * @param fallback one single-size face at the current `OPTION_APPWIDGET_SIZES` size.
     * @param log where each failure is written (`AppLog` at INFO in production).
     */
    fun update(
        updater: Updater,
        appWidgetId: Int,
        full: () -> RemoteViews,
        fallback: () -> RemoteViews,
        log: (String) -> Unit,
    ): Outcome {
        val first = try {
            updater.update(appWidgetId, full())
            return Outcome.UPDATED
        } catch (e: Exception) {
            e
        }
        if (!isHostLimit(first)) {
            // Not a size problem, so a smaller face would not help: contain it.
            log("widget $appWidgetId update failed: ${describe(first)}")
            return Outcome.FAILED
        }
        log("widget $appWidgetId full face refused (${describe(first)}); trying one size")
        return try {
            updater.update(appWidgetId, fallback())
            Outcome.FELL_BACK
        } catch (second: Exception) {
            log("widget $appWidgetId fallback refused too (${describe(second)}); keeping the last face")
            Outcome.FAILED
        }
    }

    /** The three shapes R10 names. */
    fun isHostLimit(t: Throwable): Boolean {
        if (t is IllegalArgumentException || t is TransactionTooLargeException) return true
        if (t !is RuntimeException) return false
        var c: Throwable? = t.cause
        var depth = 0
        while (c != null && depth < 8) {
            if (c is RemoteException || c is TransactionTooLargeException) return true
            c = c.cause
            depth++
        }
        return false
    }

    private fun describe(t: Throwable): String = "${t.javaClass.simpleName}: ${t.message}"
}
