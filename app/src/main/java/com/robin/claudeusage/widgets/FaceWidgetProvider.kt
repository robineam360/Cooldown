package com.robin.claudeusage.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.notify.Surfaces

/**
 * What the four providers share (CCRM-78 (Widgets Reborn)). Each composes its size map
 * from `WidgetFace.render` through [WidgetHost]; nothing here draws.
 *
 * R7: every callback that changes what a placed widget shows re-arms the one transition
 * alarm through `Surfaces.arm`, which always recomputes over *all* placed widgets of all
 * four providers — so `onDisabled`, which Android sends per provider, re-arms like the
 * rest and the alarm is cancelled only when nothing of any face is left. No periodic
 * work: `updatePeriodMillis` is 0 in every info file.
 *
 * No callback lets an exception out (R10): one widget's failure must not take the
 * launcher's whole update broadcast down.
 */
abstract class FaceWidgetProvider(private val face: Face) : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) = contained(context) {
        val cache = UsageCache(context)
        for (id in appWidgetIds) WidgetHost.update(context, cache, mgr, id, face, UNAVAILABLE.getValue(face))
        Surfaces.arm(context)
    }

    /** Fold, unfold, resize: the launcher picks from the size map, but a redraw is cheap and true. */
    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        contained(context) {
            WidgetHost.update(context, UsageCache(context), mgr, appWidgetId, face, UNAVAILABLE.getValue(face))
        }

    override fun onEnabled(context: Context) = contained(context) { Surfaces.arm(context) }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = contained(context) {
        WidgetPrefs(context).delete(appWidgetIds)
        Surfaces.arm(context)
    }

    override fun onDisabled(context: Context) = contained(context) { Surfaces.arm(context) }

    /** A restore hands new ids for old ones; an old id with no prefs stays unassigned (R5). */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) = contained(context) {
        WidgetPrefs(context).remap(oldWidgetIds, newWidgetIds)
        Surfaces.arm(context)
    }

    companion object {
        /**
         * R9's recovery switch: a face pulled in a later version keeps its provider and
         * draws S14 by flipping its entry here. All false in v1.8.
         */
        val UNAVAILABLE: Map<Face, Boolean> = Face.entries.associateWith { false }

        inline fun contained(context: Context, block: () -> Unit) {
            try {
                block()
            } catch (e: Exception) {
                WidgetHost.log(context, "widget callback failed: ${e.javaClass.simpleName}: ${e.message}")
            } catch (e: OutOfMemoryError) {
                // A bitmap too many: still never out of a provider or receiver (R10).
                WidgetHost.log(context, "widget callback out of memory: ${e.message}")
            }
        }
    }
}
