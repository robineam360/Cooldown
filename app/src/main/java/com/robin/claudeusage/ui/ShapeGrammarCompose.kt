package com.robin.claudeusage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * CCRM-103 (Hybrid Notification): Appearance › Usage end mark, provided once at the app's
 * root so every in-app bar and ring reads it without threading it through each card.
 */
val LocalUsageEndMark = staticCompositionLocalOf { UsageEndMark.DEFAULT }

/**
 * A figure in the grammar's type (rev C §1c, rev B §1b): Roboto with tabular, lining
 * digits, its "%" at 58% of the digits on the baseline.
 */
fun grammarFigure(text: String): AnnotatedString = buildAnnotatedString {
    if (text.length >= 2 && text.last() == '%' && text[text.length - 2].isDigit()) {
        append(text.dropLast(1))
        withStyle(SpanStyle(fontSize = Grammar.PCT_SCALE.em)) { append("%") }
    } else append(text)
}

/** The figure style: the system sans, tabular and lining. */
fun TextStyle.grammarDigits(): TextStyle =
    copy(fontFamily = FontFamily.Default, fontFeatureSettings = Grammar.FIGURE_FEATURES)

/**
 * The grammar's ring with its figure in the bore (rev D §1): [GrammarRing.paint] on a
 * Canvas, the figure a real `Text` fitted to the bore. [figure] null draws no text
 * (a plan with no 5h, or the bare 14 dp ring). [figureColor] null = the ink, or the
 * severity colour above 90. Screen readers hear [description] ("5-hour window, 34 percent").
 */
@Composable
fun GrammarRingBox(
    size: Dp,
    pct: Double?,
    elapsed: Double?,
    accent: Color,
    figure: String?,
    description: String,
    modifier: Modifier = Modifier,
    state: GrammarRing.State = GrammarRing.State.NORMAL,
    figureColor: Color? = null,
    projectionPct: Double? = null,
    showOverPace: Boolean = true,
    surface: Color = CardDefaults.cardColors().containerColor,
) {
    val dark = appDark()
    val endMark = LocalUsageEndMark.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    Box(
        modifier.size(size).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            drawIntoCanvas {
                GrammarRing.paint(
                    it.nativeCanvas, 0f, 0f, this.size.width, density.density, pct, elapsed, accent, dark,
                    state, endMark, showOverPace, surface, projectionPct,
                )
            }
        }
        if (figure != null && state != GrammarRing.State.NOT_APPLICABLE) {
            val sDp = size.value
            val base = GrammarRing.figureDp(sDp, figure)
            val loud = state == GrammarRing.State.NORMAL && GrammarRing.loud(pct)
            val style = TextStyle(
                fontSize = (base / density.fontScale).sp,
                fontWeight = if (loud && figureColor == null) FontWeight.ExtraBold else FontWeight.Bold,
                letterSpacing = (-0.02).em,
            ).grammarDigits()
            val text = grammarFigure(figure)
            val widthDp = with(density) { measurer.measure(text, style).size.width.toDp().value }
            val fitted = GrammarRing.fitFigure(sDp, base, widthDp)
            val ink = Grammar.ink(dark)
            val color = when {
                state == GrammarRing.State.GREY || pct == null -> ink.copy(alpha = if (state == GrammarRing.State.GREY) 0.45f else 0.5f)
                figureColor != null -> figureColor
                loud -> Palette.barColor(pct, accent, dark)
                else -> ink
            }
            Text(text, style = style.copy(fontSize = (fitted / density.fontScale).sp), color = color, maxLines = 1, softWrap = false)
        }
    }
}
