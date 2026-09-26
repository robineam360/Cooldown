package com.robin.claudeusage.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.notify.Surfaces
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.ProviderMark
import com.robin.claudeusage.ui.resolveDark

/**
 * Placing or reconfiguring a widget (CCRM-78 (Widgets Reborn) §Config; wireframe rev D
 * §6): a live preview, then Account (none on the Strip), Window (hidden when the account
 * has one; none on the Strip) and Background, then "Add widget" — or "Save changes" when
 * reached by long-press → "Widget settings".
 *
 * The result is `RESULT_CANCELED` until Save, so backing out of the add flow removes the
 * widget and backing out of a reconfigure leaves it as it was. Save is the only thing that
 * assigns an account (R5).
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        // An invalid id, or one whose provider is not one of ours, finishes at once.
        val face = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) null else WidgetHost.faceOf(this, id)
        if (face == null) {
            finish()
            return
        }
        val prefs = WidgetPrefs(this)
        val stored = prefs.read(id)
        val cache = UsageCache(this)
        val profiles = cache.registry().all()
        val reconfigure = stored.version > 0

        setContent {
            val dark = resolveDark(remember { cache.themeMode() }, isSystemInDarkTheme())
            val view = LocalView.current
            if (!view.isInEditMode) SideEffect {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            }
            // Saveable, so a fold or unfold mid-config keeps the picks.
            var accountKey by rememberSaveable {
                mutableStateOf((profiles.firstOrNull { it.key == stored.accountKey } ?: profiles.firstOrNull())?.key)
            }
            val account = profiles.firstOrNull { it.key == accountKey }
            var windowPick by rememberSaveable { mutableStateOf(stored.window) }
            var bg by rememberSaveable { mutableStateOf(stored.background) }

            ConfigScreen(
                face = face,
                dark = dark,
                cache = cache,
                profiles = profiles,
                account = account,
                window = windowPick,
                background = bg,
                reconfigure = reconfigure,
                onAccount = { accountKey = it.key },
                onWindow = { windowPick = it },
                onBackground = { bg = it },
                onBack = { finish() },
                preview = {
                    val config = WidgetPrefs.Config(
                        accountKey = if (face == Face.STRIP) null else account?.key,
                        window = windowPick, background = bg, version = 1,
                    )
                    val state = WidgetHost.state(
                        this, cache, id, face, FaceWidgetProvider.UNAVAILABLE.getValue(face), config = config,
                    )
                    val size = WidgetHost.currentSize(AppWidgetManager.getInstance(this).getAppWidgetOptions(id))
                    val frame = size?.let { Frame.at(face, it.width, it.height) } ?: Bucket.of(face).first().frame
                    frame to WidgetFace.render(this, face, frame, state)
                },
                onSave = {
                    prefs.save(
                        id, if (face == Face.STRIP) null else account?.key, windowPick, bg,
                    )
                    WidgetHost.update(
                        this, cache, AppWidgetManager.getInstance(this), id, face,
                        FaceWidgetProvider.UNAVAILABLE.getValue(face),
                    )
                    Surfaces.arm(this)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    finish()
                },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ConfigScreen(
    face: Face,
    dark: Boolean,
    cache: UsageCache,
    profiles: List<Profile>,
    account: Profile?,
    window: FaceWindow,
    background: FaceBackground,
    reconfigure: Boolean,
    onAccount: (Profile) -> Unit,
    onWindow: (FaceWindow) -> Unit,
    onBackground: (FaceBackground) -> Unit,
    onBack: () -> Unit,
    preview: () -> Pair<Frame, android.widget.RemoteViews>?,
    onSave: () -> Unit,
) {
    val surface = Color(if (dark) 0xFF0D0D0D else 0xFFF5EFE8)
    val ink = Color(if (dark) 0xFFEDE8E4 else 0xFF26211E)
    val divider = ink.copy(alpha = 0.08f)
    val accent = account?.let { Palette.color(Palette.accentName(cache, it), dark) }
        ?: Palette.color(Palette.accentName(cache, cache.registry().first()), dark)
    val onAccent = if (dark) Color(0xFF1A1A1A) else Color.White
    val outline = if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f)

    Box(Modifier.fillMaxSize().background(surface)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ink,
                    modifier = Modifier.size(22.dp).clickable(onClick = onBack),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (reconfigure) "Widget settings" else "Add widget",
                    color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                )
            }

            // The live preview: the very render the launcher gets, at the widget's size.
            // Rebuilt only when a pick changes; a preview that fails to draw is left out
            // rather than taking the screen down.
            val built = remember(account, window, background) { runCatching(preview).getOrNull() }
            if (built != null) BoxWithConstraints(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val (frame, views) = built
                val scale = minOf(1f, maxWidth.value / frame.widthDp)
                Box(Modifier.requiredSize((frame.widthDp * scale).dp, (frame.heightDp * scale).dp)) {
                    AndroidView(
                        factory = { FrameLayout(it) },
                        update = { host ->
                            host.removeAllViews()
                            host.addView(views.apply(host.context, host))
                        },
                        modifier = Modifier
                            .requiredSize(frame.widthDp.dp, frame.heightDp.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                }
            }

            ConfigRow(
                if (face == Face.STRIP) "Account — not shown, the Strip carries every account" else "Account",
                ink, divider,
            ) {
                if (face != Face.STRIP) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in profiles) {
                        val sel = p == account
                        val color = Palette.color(Palette.accentName(cache, p), dark)
                        Chip(
                            label = cache.profileLabel(p), selected = sel,
                            fill = color, onFill = onAccent, ink = ink, outline = outline,
                            onClick = { onAccount(p) },
                        ) { ProviderMark(p.provider, 14.dp, if (sel) onAccent else color) }
                    }
                }
            }

            ConfigRow("Window", ink, divider) {
                val data = account?.let { cache.snapshot(it).data }
                val oneWindow = data != null && (data.session == null) != (data.weekly == null)
                when {
                    face == Face.STRIP -> Note("Fixed to each account's headline window — no control here.", ink)
                    oneWindow -> Note("${account?.let { cache.profileLabel(it) }} has one window — no control needed.", ink)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (w in FaceWindow.entries) Chip(
                            w.word, window == w, accent, onAccent, ink, outline, onClick = { onWindow(w) },
                        )
                    }
                }
            }

            ConfigRow("Background", ink, divider) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (b in FaceBackground.entries) Chip(
                        b.name.lowercase().replaceFirstChar { it.uppercase() }, background == b,
                        accent, onAccent, ink, outline, onClick = { onBackground(b) },
                    )
                }
            }

            Box(
                Modifier.padding(16.dp).fillMaxWidth().height(44.dp)
                    .background(accent, RoundedCornerShape(22.dp))
                    .border(1.dp, outline, RoundedCornerShape(22.dp))
                    .clickable(onClick = onSave),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (reconfigure) "Save changes" else "Add widget",
                    color = onAccent, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ConfigRow(label: String, ink: Color, divider: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                label.uppercase(), color = ink, fontSize = 11.sp, letterSpacing = 0.66.sp,
                modifier = Modifier.alpha(0.65f).padding(bottom = 8.dp),
            )
            content()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(divider))
    }
}

@Composable
private fun Note(text: String, ink: Color) =
    Text(text, color = ink.copy(alpha = 0.7f), fontSize = 13.sp)

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    fill: Color,
    onFill: Color,
    ink: Color,
    outline: Color,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(17.dp)
    Row(
        Modifier.height(34.dp)
            .then(if (selected) Modifier.background(fill, shape) else Modifier)
            .border(1.dp, if (selected) Color.Transparent else outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(
            label, color = if (selected) onFill else ink, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
