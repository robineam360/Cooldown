package com.robin.claudeusage.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * CCBG-81 (History Tab Wrap), Robin 2026-11-11 (option B): a profile tab strip — Main's and History's — is a fixed,
 * equal-width [TabRow] while every tab's label fits its share on one line, and a [ScrollableTabRow] as soon as one
 * would not. It replaces CCRM-6 (Multi-Account)'s fixed-up-to-three count, which let History (no All tab) wrap
 * "Personal" to "Person / al" at 360 dp with three accounts. Pure, so `TabStripFitTest` pins it.
 */
internal object TabStripFit {

    /** Material 3's `Tab` pads its text 16 dp each side. */
    const val TAB_PADDING_DP = 32

    /**
     * True when a label of [labelWidths] (one line, unconstrained) plus [tabPadding] is wider than its equal share of
     * [stripWidth]; all in the same unit (px at the call site).
     */
    fun scrolls(labelWidths: List<Int>, stripWidth: Int, tabPadding: Int): Boolean {
        if (labelWidths.isEmpty()) return false
        val share = stripWidth / labelWidths.size
        return labelWidths.any { it + tabPadding > share }
    }
}

/**
 * The strip itself: [labels] are each tab's label content (the same composables [tabs] passes to its `Tab`s), measured
 * once at their one-line width under the tab label's text style, so the decision follows the font scale and the names.
 */
@Composable
internal fun ProfileTabStrip(
    selected: Int,
    contentColor: Color,
    indicator: @Composable (List<TabPosition>) -> Unit,
    labels: List<@Composable () -> Unit>,
    tabs: @Composable () -> Unit,
) {
    // Material 3's Tab sets its text in titleSmall (PrimaryNavigationTabTokens.ActiveLabelTextFont).
    val labelStyle = MaterialTheme.typography.titleSmall
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val widths = subcompose("labels") {
            labels.forEach { label -> Box { ProvideTextStyle(labelStyle) { label() } } }
        }.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val scroll = TabStripFit.scrolls(widths, constraints.maxWidth, TabStripFit.TAB_PADDING_DP.dp.roundToPx())
        val row = subcompose(if (scroll) "scrolling" else "fixed") {
            if (scroll) {
                ScrollableTabRow(
                    selectedTabIndex = selected,
                    edgePadding = 0.dp,
                    contentColor = contentColor,
                    indicator = indicator,
                ) { tabs() }
            } else {
                TabRow(
                    selectedTabIndex = selected,
                    contentColor = contentColor,
                    indicator = indicator,
                ) { tabs() }
            }
        }.map { it.measure(constraints.copy(minHeight = 0)) }
        layout(constraints.maxWidth, row.maxOfOrNull { it.height } ?: 0) { row.forEach { it.place(0, 0) } }
    }
}
