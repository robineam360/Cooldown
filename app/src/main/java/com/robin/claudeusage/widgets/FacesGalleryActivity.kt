package com.robin.claudeusage.widgets

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * CCRM-84 (Faces Gallery), wireframe rev D §9d (`design/2026-09-25-widgets-reborn.html`):
 * every face × bucket × [StateId], inflated through the very same [WidgetFace.render] the
 * providers call, captioned with its bucket, state and [WidgetFace.bitmapBytes] estimate.
 * Debug-only; the Settings → Debug entry that launches it is wired by the seat.
 *
 * A tile's render+apply is wrapped so one bad state shows its exception instead of taking
 * the screen down (CCBG-19 (Fixture Unreachable) is exactly the failure mode this avoids).
 */
class FacesGalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GalleryScreen(onBack = { finish() }) }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, FacesGalleryActivity::class.java)
    }
}

private val FACE_FILTERS: List<Pair<String, Face?>> = listOf(
    "All" to null,
    "Ring" to Face.RING,
    "Number" to Face.NUMBER,
    "Countdown" to Face.COUNTDOWN,
    "Accounts Strip" to Face.STRIP,
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GalleryScreen(onBack: () -> Unit) {
    var dark by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf<Face?>(null) }

    val surface = Color(if (dark) 0xFF0D0D0D else 0xFFF5EFE8)
    val ink = Color(if (dark) 0xFFEDE8E4 else 0xFF26211E)
    val accent = Color(0xFFE59980)
    val outline = if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f)

    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val faces = filter?.let { listOf(it) } ?: Face.entries
    val tiles = remember(dark, filter) { GalleryTiles.build(dark, density, faces) }

    Box(Modifier.fillMaxSize().background(surface)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ink,
                    modifier = Modifier.size(22.dp).clickable(onClick = onBack),
                )
                Spacer(Modifier.width(10.dp))
                Text("Faces gallery", color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GalleryChip("Dark", dark, accent, ink, outline) { dark = true }
                GalleryChip("Light", !dark, accent, ink, outline) { dark = false }
            }

            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((label, face) in FACE_FILTERS) {
                    GalleryChip(label, filter == face, accent, ink, outline) { filter = face }
                }
            }

            Text(
                "${tiles.size} tiles",
                color = ink.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            FlowRow(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (tile in tiles) {
                    GalleryTileView(tile, dark, ink, outline)
                }
            }
        }
    }
}

@Composable
private fun GalleryChip(
    label: String, selected: Boolean, accent: Color, ink: Color, outline: Color, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    val onAccent = Color.White
    Row(
        Modifier.height(30.dp)
            .then(if (selected) Modifier.background(accent, shape) else Modifier)
            .border(1.dp, if (selected) Color.Transparent else outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, color = if (selected) onAccent else ink, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * One tile: [WidgetFace.render] and `RemoteViews.apply` both run inside the `try` — a bad
 * state shows its caption plus the exception message instead of crashing the gallery.
 */
@Composable
private fun GalleryTileView(tile: GalleryTile, dark: Boolean, ink: Color, outline: Color) {
    val context = LocalContext.current
    var error by remember(tile, dark) { mutableStateOf<String?>(null) }

    Column(Modifier.width(tile.bucket.widthDp.dp).padding(bottom = 12.dp)) {
        Box(
            Modifier.width(tile.bucket.widthDp.dp).height(tile.bucket.heightDp.dp)
                .border(1.dp, outline),
        ) {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { host ->
                    host.removeAllViews()
                    try {
                        val state = FaceStates.of(tile.input)
                        val views = WidgetFace.render(context, tile.face, tile.bucket, state)
                        host.addView(views.apply(context, host))
                        error = null
                    } catch (t: Throwable) {
                        error = t.message ?: t.toString()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            tile.caption, color = ink, fontSize = 10.5.sp,
            modifier = Modifier.width(tile.bucket.widthDp.dp).padding(top = 4.dp),
        )
        error?.let {
            Text(
                it, color = Color(0xFFFF6B6B), fontSize = 9.5.sp,
                modifier = Modifier.width(tile.bucket.widthDp.dp).padding(top = 2.dp),
            )
        }
    }
}
