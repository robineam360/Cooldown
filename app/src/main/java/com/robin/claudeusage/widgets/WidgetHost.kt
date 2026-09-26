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
                synthetic = s.synthetic,
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

    /**
     * The size-map key of [bucket] when no frame has been reported yet: a frame at the
     * foot of its class under [Frame.bucketFor]'s thresholds (rev F.1), and the bucket is
     * drawn there too, so no bitmap is wider than the frame that picks it. Rev D keyed each bucket 12 dp under its
     * assumed cover cell, and One UI's narrower cells fell under every key (CCBG-38).
     */
    fun key(bucket: Bucket): SizeF = when (bucket) {
        Bucket.RING_1X1 -> SizeF(84f, 84f)
        Bucket.RING_2X2 -> SizeF(140f, 140f)
        Bucket.NUMBER_2X1, Bucket.COUNTDOWN_2X1 -> SizeF(140f, 84f)
        Bucket.NUMBER_4X1 -> SizeF(240f, 84f)
        Bucket.NUMBER_4X2 -> SizeF(240f, 150f)
        Bucket.COUNTDOWN_2X2 -> SizeF(140f, 150f)
        Bucket.STRIP_4X1 -> SizeF(244f, 84f)
        Bucket.STRIP_4X2 -> SizeF(244f, 150f)
    }

    /** Rev F.1: before any frame is reported, each bucket is drawn at its own key. */
    fun keyFrame(bucket: Bucket): Frame = key(bucket).let { Frame(bucket, it.width, it.height) }

    /** Rev F.1: each reported frame is keyed this far under itself — the host allows +1 dp. */
    const val KEY_SLACK_DP = 2f

    /** The layout a frame of [widthDp] × [heightDp] takes — rev F's class rule. */
    fun pick(face: Face, widthDp: Float, heightDp: Float): Bucket = Frame.bucketFor(face, widthDp, heightDp)

    /**
     * Every frame the launcher reports for the widget, smallest first and at most
     * [MAX_FRAMES]: a Fold reports its cover and its inner frame, most launchers one. When
     * frames must go, the largest go (rev F.1, Fable): the host falls back to its smallest
     * entry when no key fits, and a small face centred in a big frame is whole where the
     * reverse would clip.
     */
    fun reportedSizes(options: Bundle?): List<SizeF> {
        options ?: return emptyList()
        val sizes: List<SizeF>? = if (Build.VERSION.SDK_INT >= 33) {
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        } else {
            @Suppress("DEPRECATION")
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        }
        return sizes.orEmpty().filter { it.width > 0f && it.height > 0f }.distinct()
            .sortedBy { it.width * it.height }.take(MAX_FRAMES)
    }

    /** More than a Fold's two frames would only spend bitmap memory (R10). */
    const val MAX_FRAMES = 4

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
    fun single(context: Context, appWidgetId: Int, bucket: Bucket, state: FaceState): RemoteViews =
        single(context, appWidgetId, bucket.frame, state)

    /** One face drawn at [frame], wired and added fresh (as above). */
    fun single(context: Context, appWidgetId: Int, frame: Frame, state: FaceState): RemoteViews {
        val face = WidgetFace.render(context, frame.bucket.face, frame, state)
            .also { wire(context, it, appWidgetId, frame, state) }
        return RemoteViews(context.packageName, R.layout.widget_fresh).apply {
            removeAllViews(R.id.w_fresh)
            addView(R.id.w_fresh, face)
        }
    }

    /**
     * The composed `RemoteViews(Map<SizeF, RemoteViews>)` (rev F): one face per frame the
     * launcher reports, each drawn at exactly that frame, so the host's best fit is always
     * an exact match. Before any frame is reported (the very first update), the face's
     * buckets at their design sizes under [key] — at most three (R10).
     */
    fun sizeMap(
        context: Context, appWidgetId: Int, face: Face, state: FaceState, sizes: List<SizeF> = emptyList(),
    ): RemoteViews =
        if (sizes.isNotEmpty()) {
            RemoteViews(
                withinBudget(face, sizes, context.resources.displayMetrics.density).associate {
                    SizeF(it.widthDp - KEY_SLACK_DP, it.heightDp - KEY_SLACK_DP) to single(context, appWidgetId, it, state)
                },
            )
        } else {
            RemoteViews(Bucket.of(face).associate { key(it) to single(context, appWidgetId, keyFrame(it), state) })
        }

    /**
     * Rev F.1 (R10): the reported frames, smallest first, for as long as the composed map's
     * bitmaps stay under [WidgetFace.BITMAP_BUDGET_BYTES] — always at least the smallest.
     */
    fun withinBudget(face: Face, sizes: List<SizeF>, density: Float): List<Frame> {
        val out = mutableListOf<Frame>()
        var bytes = 0L
        for (size in sizes) {
            val frame = Frame.at(face, size.width, size.height)
            val more = WidgetFace.frameBytes(frame, density)
            if (out.isNotEmpty() && bytes + more >= WidgetFace.BITMAP_BUDGET_BYTES) break
            out += frame
            bytes += more
        }
        return out
    }

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
            full = { sizeMap(context, appWidgetId, face, state, reportedSizes(mgr.getAppWidgetOptions(appWidgetId))) },
            fallback = {
                val size = currentSize(mgr.getAppWidgetOptions(appWidgetId))
                if (size != null) single(context, appWidgetId, Frame.at(face, size.width, size.height), state)
                else single(context, appWidgetId, keyFrame(Bucket.of(face).first()), state)
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
     * else opens Cooldown on the account it shows. The Number's chips and cycler (4×2, and
     * rev H's 2×2) go to [WidgetActionReceiver] with an identity unique per widget and action.
     */
    private fun wire(context: Context, rv: RemoteViews, id: Int, frame: Frame, state: FaceState) {
        rv.setOnClickPendingIntent(R.id.w_root, faceTap(context, id, state))
        if (WidgetFace.numberControls(frame) && state.message == null) {
            val c = state.cells.single()
            if (c.hasBothWindows) {
                rv.setOnClickPendingIntent(WidgetFace.CHIP_SESSION, WidgetActionReceiver.intent(context, id, WidgetActionReceiver.Action.WINDOW_5H))
                rv.setOnClickPendingIntent(WidgetFace.CHIP_WEEKLY, WidgetActionReceiver.intent(context, id, WidgetActionReceiver.Action.WINDOW_WEEKLY))
            }
            rv.setOnClickPendingIntent(WidgetFace.cycler(frame), WidgetActionReceiver.intent(context, id, WidgetActionReceiver.Action.CYCLE))
        }
    }

    fun uri(id: Int, action: String): Uri = Uri.parse("$SCHEME://$id/$action")

    private fun faceTap(context: Context, id: Int, state: FaceState): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        // R5: a removed account's face opens its config; so does an unassigned one
        // (CCBG-43 (Widget Settings Hidden)), since it is drawing a default, not a choice.
        val unassigned = state.message == null && state.face != Face.STRIP &&
            state.cells.firstOrNull()?.unassigned == true
        if (state.message == FaceMessage.REMOVED || unassigned) {
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
