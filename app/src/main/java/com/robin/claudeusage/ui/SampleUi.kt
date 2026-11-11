package com.robin.claudeusage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robin.claudeusage.data.SampleMode

/**
 * CCRM-113 (Sample Port): the sample's three visible pieces, drawn from rev B §9 and its 2026-11-11 addendum — the
 * banner, the first screen's entry and the Add account sheet's last row. The rule they serve is in
 * [com.robin.claudeusage.data.SampleMode].
 */

/** The sample's violet: #7C4DFF on light, #B388FF where it is text or a glyph on dark. */
@Composable
fun sampleViolet(): Color = Color(if (appDark()) SampleMode.VIOLET_ON_DARK else SampleMode.VIOLET)

/**
 * The words the banner prints at [availableDp] of width and [fontScale]: the whole line, or — where it does not fit —
 * without " — made-up accounts". "● Sample data" and "Exit" never drop or wrap. Pure, for the tests.
 */
object SampleBannerFit {
    /** 14 dp of start padding, the 8 dp gap, and the 12 dp a side that make the "Exit" target 48 dp wide. */
    const val CHROME_DP = 14f + 8f + 24f

    /** Text widths in the wireframe's em model at 13 sp: 206 dp for the whole line, "Exit" 23 dp (§9 b). */
    fun showsTail(availableDp: Float, fullTextDp: Float, exitDp: Float, chromeDp: Float): Boolean =
        fullTextDp + exitDp + chromeDp <= availableDp
}

/**
 * The violet band at the top of every tab, Settings and History while the sample is showing (iOS's banner in Android's
 * notice slot): "● Sample data" in violet, " — made-up accounts" in the ink, and a trailing "Exit" with a 48 dp target.
 * The band itself is the wireframe's 36 dp (9 dp padding, a 13 sp line); the row lays out 48 dp tall so the target is whole.
 */
@Composable
fun SampleBanner(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val violet = sampleViolet()
    val ink = MaterialTheme.colorScheme.onSurface
    val band = Color(SampleMode.VIOLET).copy(alpha = 0.16f)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val light = TextStyle(fontSize = 13.sp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fits = with(density) {
            val head = measurer.measure("● ${SampleMode.BANNER_HEAD}", style).size.width.toDp().value
            val tail = measurer.measure(SampleMode.BANNER_TAIL, light).size.width.toDp().value
            val exit = measurer.measure("Exit", style).size.width.toDp().value
            // The 14 dp start padding, the 8 dp gap, and the Exit target's own 12 dp a side.
            SampleBannerFit.showsTail(maxWidth.value, head + tail, exit, SampleBannerFit.CHROME_DP)
        }
        val text = buildAnnotatedString {
            withStyle(androidx.compose.ui.text.SpanStyle(color = violet, fontWeight = FontWeight.SemiBold)) {
                append("● ${SampleMode.BANNER_HEAD}")
            }
            if (fits) withStyle(androidx.compose.ui.text.SpanStyle(color = ink)) { append(SampleMode.BANNER_TAIL) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                // The band is 36 dp inside the 48 dp row: the target is whole, the look is the drawing's.
                .drawBehind {
                    val inset = 6.dp.toPx()
                    drawRoundRect(
                        band,
                        topLeft = Offset(0f, inset),
                        size = Size(size.width, size.height - 2 * inset),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                }
                .padding(start = 14.dp)
                .testTag("sample-banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onExit)
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = "Exit sample data" }
                    .testTag("sample-exit"),
                contentAlignment = Alignment.Center,
            ) {
                Text("Exit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * The first screen's entry (no account): a text button 12 dp under the "No Claude account yet" card, and the caption under
 * it. The card itself is unchanged.
 */
@Composable
fun SampleEntry(onEnter: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        TextButton(
            onClick = onEnter,
            modifier = Modifier.heightIn(min = 48.dp).testTag("sample-entry"),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Text(SampleMode.ENTRY_TITLE, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            SampleMode.ENTRY_DETAIL,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * The Add account sheet's last row, under a divider: a 28 dp violet ring-and-dot glyph, "Try with sample data" and the
 * same caption. It ignores the Personal | Work control (the sample's tags are fixed at entry).
 */
@Composable
fun SampleAddRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val violet = sampleViolet()
    Column(modifier.fillMaxWidth().padding(top = 14.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(top = 10.dp, bottom = 4.dp)
                .testTag("sample-add-row"),
            verticalAlignment = Alignment.Top,
        ) {
            Canvas(Modifier.size(28.dp)) {
                val u = size.width / 28f
                drawCircle(violet, radius = 11f * u, center = center, style = Stroke(width = 2.4f * u))
                drawCircle(violet, radius = 4.5f * u, center = center)
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(SampleMode.ENTRY_TITLE, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    SampleMode.ENTRY_DETAIL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The banner placed in a notice slot or a page's first row: 16 dp a side, capped at 720 dp and centred (the inner screen),
 * 12 dp of band above it (6 dp of row, 6 dp of the row's own inset) and 4 dp below.
 */
@Composable
fun SampleBannerSlot(onExit: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp), contentAlignment = Alignment.TopCenter) {
        SampleBanner(onExit, Modifier.widthIn(max = 720.dp))
    }
}
