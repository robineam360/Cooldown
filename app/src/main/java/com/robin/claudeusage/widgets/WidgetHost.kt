package com.robin.claudeusage.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.robin.claudeusage.MainActivity
import com.robin.claudeusage.R
import com.robin.claudeusage.data.ErrorKind
import com.robin.claudeusage.data.HistoryStore
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.diag.AppLog
import com.robin.claudeusage.ui.Palette

/**
 * Everything between the cache and a placed widget (CCRM-78 (Widgets Reborn)): which
 * widgets are placed, what each is asked to draw, the size map each provider hands the
 * launcher, and the tap targets on it. The providers, both receivers, the config activity
 * and `Surfaces` all come through here, so a face drawn by any trigger is the same face.
 */
object WidgetHost {

    /**
     * R9: the four provider class names — a permanent contract, spelled out here so no
     * refactor can move one by accident. A rename after release deletes every placement.
     */
    val PROVIDERS: Map<Face, String> = mapOf(
        Face.RING to "com.robin.claudeusage.widgets.RingWidgetProvider",
        Face.NUMBER to "com.robin.claudeusage.widgets.NumberWidgetProvider",
        Face.COUNTDOWN to "com.robin.claudeusage.widgets.CountdownWidgetProvider",
        Face.STRIP to "com.robin.claudeusage.widgets.StripWidgetProvider",
    )

    /** A widget whose frame is a little under a bucket's cover size still gets it. */
    const val FIT_TOLERANCE_DP = 12f

    /** Intent data scheme for every widget PendingIntent (CCRM-78 §On-face controls). */
    const val SCHEME = "cooldown-widget"

    fun faceOf(context: Context, appWidgetId: Int): Face? {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider
            ?: return null
        if (provider.packageName != context.packageName) return null
        return PROVIDERS.entries.firstOrNull { it.value == provider.className }?.key
    }

    /** Every placed widget of all four providers, with its face. */
    fun placedIds(context: Context): List<Pair<Int, Face>> {
        val mgr = AppWidgetManager.getInstance(context)
        return PROVIDERS.flatMap { (face, cls) ->
            mgr.getAppWidgetIds(ComponentName(context, cls)).map { it to face }
        }
    }

    /** What [Transitions.nextTransitionAt] needs about every placed widget. */
    fun placed(context: Context): List<Transitions.Placed> {
        val prefs = WidgetPrefs(context)
        return placedIds(context).map { (id, face) ->
            val c = prefs.read(id)
            Transitions.Placed(face, c.accountKey, c.window)
        }
    }

    fun snapshots(cache: UsageCache): List<Transitions.Snapshot> =
        cache.registry().all().map { p ->
            val s = cache.snapshot(p)
            Transitions.Snapshot(p.key, s.data, s.fetchedAt)
        }

    /** Widgets follow the system's night mode, not the app's override. */
    fun systemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * Every account in registry order (CCRM-71 (Account Order)), as the faces need it.
     * [withEstimates] reads each account's history for the Countdown 2×2's "~ runs out"
     * line; the other faces never show it, so they skip the file reads.
     */
    fun accounts(context: Context, cache: UsageCache, dark: Boolean, withEstimates: Boolean): List<AccountInput> {
        val history = if (withEstimates) HistoryStore(context) else null
        return cache.registry().all().map { p ->
            val s = cache.snapshot(p)
            val data = s.data
            var sessionOut: Long? = null
            var weeklyOut: Long? = null
            if (history != null && data != null) {
                val points = history.points(p)
                data.session?.resetsAt?.toEpochMilli()?.let { r ->
                    sessionOut = Projection.estimate(Projection.sessionSamples(points, r, Projection.SESSION_MS), r)?.hitsLimitAtMs
                }
                data.weekly?.resetsAt?.toEpochMilli()?.let { r ->
                    weeklyOut = Projection.estimate(Projection.weeklySamples(points, r, Projection.WEEKLY_MS), r)?.hitsLimitAtMs
                }
            }
            AccountInput(
                key = p.key,
                label = cache.profileLabel(p),
                provider = p.provider,
                accentArgb = Palette.color(Palette.accentName(cache, p), dark).toArgb(),
                data = data,
                fetchedAt = s.fetchedAt,
                authState = s.authState,
                planUnsupported = s.lastStatusKind == ErrorKind.PLAN.key,
                sessionRunsOutAtMs = sessionOut,
                weeklyRunsOutAtMs = weeklyOut,
            )
        }
    }

    /** One widget's [FaceState], from its prefs and the cache. */
    fun state(
        context: Context,
        cache: UsageCache,
        appWidgetId: Int,
        face: Face,
        unavailable: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        config: WidgetPrefs.Config = WidgetPrefs(context).read(appWidgetId),
    ): FaceState {
        val dark = systemDark(context)
        return FaceStates.of(
            FaceInput(
                face = face,
                accountKey = config.accountKey,
                window = config.window,
                background = config.background,
                dark = dark,
                accounts = accounts(context, cache, dark, withEstimates = face == Face.COUNTDOWN),
                usageLeft = cache.usageLeft(),
                showOverPace = cache.showOverPace(),
                // R8 (CCRM-15 (Above-Pace Verification)): the marker follows the one
                // process-wide switch the cache's snapshots already honour.
                synthetic = com.robin.claudeusage.data.SyntheticSeries.isOn,
                unavailable = unavailable,
                nowMs = nowMs,
            ),
        )
    }

    // ---- sizes --------------------------------------------------------------------

    /** The size-map key of [bucket]: its cover size less [FIT_TOLERANCE_DP]. */
    fun key(bucket: Bucket): SizeF =
        SizeF(bucket.widthDp - FIT_TOLERANCE_DP, bucket.heightDp - FIT_TOLERANCE_DP)

    /**
     * The bucket the launcher would pick for a frame of [widthDp] × [heightDp]: the largest
     * whose key fits, else the smallest — `RemoteViews(Map)`'s own rule, so the one-size
     * fallback (R10) draws what the size map would have shown.
     */
    fun pick(face: Face, widthDp: Float, heightDp: Float): Bucket {
        val buckets = Bucket.of(face)
        return buckets
            .filter { key(it).width <= widthDp && key(it).height <= heightDp }
            .maxByOrNull { it.widthDp * it.heightDp }
            ?: buckets.minBy { it.widthDp * it.heightDp }
    }

    /** The widget's current frame in dp, from the launcher's options; null if unknown. */
    fun currentSize(options: Bundle?): SizeF? {
        options ?: return null
        val sizes: List<SizeF>? = if (Build.VERSION.SDK_INT >= 33) {
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        } else {
            @Suppress("DEPRECATION")
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        }
        // A Fold lists the cover and the inner size; the smallest always fits whichever
        // screen is showing, so the one-size fallback is never clipped.
        sizes?.minByOrNull { it.width * it.height }?.let { return it }
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return if (w > 0 && h > 0) SizeF(w.toFloat(), h.toFloat()) else null
    }

    // ---- composing --------------------------------------------------------------------

    /**
     * One bucket, rendered, wired and added into [R.layout.widget_fresh], so the launcher
     * inflates it fresh rather than reapplying it over the last state's views (CCBG-36
     * (Widget Reapply Residue)).
     */
    fun single(context: Context, appWidgetId: Int, bucket: Bucket, state: FaceState): RemoteViews {
        val face = WidgetFace.render(context, bucket.face, bucket, state)
            .also { wire(context, it, appWidgetId, bucket, state) }
        return RemoteViews(context.packageName, R.layout.widget_fresh).apply {
            removeAllViews(R.id.w_fresh)
            addView(R.id.w_fresh, face)
        }
    }

    /** The composed `RemoteViews(Map<SizeF, RemoteViews>)` — at most three buckets (R10). */
    fun sizeMap(context: Context, appWidgetId: Int, face: Face, state: FaceState): RemoteViews =
        RemoteViews(Bucket.of(face).associate { key(it) to single(context, appWidgetId, it, state) })

    /** Draws one widget through [SafeUpdate]. */
    fun update(
        context: Context,
        cache: UsageCache,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        face: Face,
        unavailable: Boolean,
    ): SafeUpdate.Outcome {
        val state = try {
            state(context, cache, appWidgetId, face, unavailable)
        } catch (e: Exception) {
            log(context, "widget $appWidgetId state failed: ${e.javaClass.simpleName}: ${e.message}")
            return SafeUpdate.Outcome.FAILED
        }
        return SafeUpdate.update(
            updater = { id, views -> mgr.updateAppWidget(id, views) },
            appWidgetId = appWidgetId,
            full = { sizeMap(context, appWidgetId, face, state) },
            fallback = {
                val size = currentSize(mgr.getAppWidgetOptions(appWidgetId))
                val bucket = size?.let { pick(face, it.width, it.height) } ?: Bucket.of(face).first()
                single(context, appWidgetId, bucket, state)
            },
            log = { log(context, it) },
        )
    }

    /** Every placed widget of every face, from the cache. */
    fun updateAll(context: Context, cache: UsageCache) {
        val mgr = AppWidgetManager.getInstance(context)
        for ((id, face) in placedIds(context)) {
            update(context, cache, mgr, id, face, unavailable = FaceWidgetProvider.UNAVAILABLE.getValue(face))
        }
    }

    fun log(context: Context, event: String) =
        AppLog.log(context, AppLog.Level.INFO, "widget", event = event)

    // ---- taps ---------------------------------------------------------------------------

    /**
     * The face's own tap: S9 opens this widget's config (R5, "tap to choose"); anything
     * else opens Cooldown on the account it shows. Number 4×2's chips and cycler go to
     * [WidgetActionReceiver] with an identity unique per widget and action.
     */
    private fun wire(context: Context, rv: RemoteViews, id: Int, bucket: Bucket, state: FaceState) {
        rv.setOnClickPendingIntent(R.id.w_root, faceTap(context, id, state))
        if (bucket == Bucket.NUMBER_4X2 && state.message == null) {
            val c = state.cells.single()
            if (c.hasBothWindows) {
                rv.setOnClickPendingIntent(WidgetFace.CHIP_SESSION, WidgetActionReceiver.intent(context, id, WidgetActionReceiver.Action.WINDOW_5H))
                rv.setOnClickPendingIntent(WidgetFace.CHIP_WEEKLY, WidgetActionReceiver.intent(context, id, WidgetActionReceiver.Action.WINDOW_WEEKLY))
            }
            rv.setOnClickPendingIntent(WidgetFace.CYCLER, WidgetActionReceiver.intent(context, id, WidgetActionReceiver.Action.CYCLE))
        }
    }

    fun uri(id: Int, action: String): Uri = Uri.parse("$SCHEME://$id/$action")

    private fun faceTap(context: Context, id: Int, state: FaceState): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        if (state.message == FaceMessage.REMOVED) {
            val configure = Intent(context, WidgetConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setData(uri(id, "configure"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(context, requestCode(id, TAP_CONFIGURE), configure, flags)
        }
        val open = Intent(context, MainActivity::class.java)
            .setData(uri(id, "open"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // The Strip shows every account, so it opens the app as it was.
        if (state.face != Face.STRIP) state.cells.firstOrNull()?.let { open.putExtra("profile", it.key) }
        return PendingIntent.getActivity(context, requestCode(id, TAP_OPEN), open, flags)
    }

    private const val TAP_OPEN = 6
    private const val TAP_CONFIGURE = 7

    /** Per widget and action (CCRM-78 §On-face controls): the id times eight, plus the action. */
    fun requestCode(id: Int, action: Int): Int = id * 8 + action
}
