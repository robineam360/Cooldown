package com.robin.claudeusage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robin.claudeusage.data.PlanFit
import com.robin.claudeusage.ui.PlanFitCopy

/**
 * CCRM-70 (Plan Fit): the tonal block above the weekly bars in the History screen's 7-day
 * pane — a reading of whether the plan an account is on fits its last eight closed weekly
 * windows. Inserted directly above `WeeklyView` in `HistoryScreen.kt`; it displaces
 * nothing, the bars simply move down.
 *
 * Always `surfaceVariant`, never an accent or a warning colour — this is a reading, not an
 * alarm, and "This plan runs out on you" in red would read as an accusation. The weekly
 * bars below keep their own severity colours unchanged; this block never touches them. See
 * `design/2026-09-16-plan-fit-and-account-order.html` §1 for the anatomy and the full
 * rationale.
 *
 * [PlanFitCopy] owns every word this draws; this owns only the layout and colour.
 *
 * @param fallbackPlan see [PlanFitCopy.build] — the account's already-known live plan
 *   label, used only when nothing has closed yet to carry a `pl` tag of its own.
 * @param compact true on the Fold 7 cover screen (COMPACT width, §3), where the header
 *   drops its period token; false on the two-pane inner screen (§4) and the wide 7-day
 *   pane, where the block sits over the 7-day side only.
 */
@Composable
fun PlanFitBlock(reading: PlanFit.Reading, fallbackPlan: String?, compact: Boolean) {
    val lines = PlanFitCopy.build(reading, fallbackPlan, compact)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            lines.header,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            lines.reading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            lines.evidence,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        lines.fiveHourLine?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
