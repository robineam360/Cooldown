package com.robin.claudeusage.ui

import androidx.compose.ui.text.TextStyle
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robin.claudeusage.R
import com.robin.claudeusage.data.AccountTag
import com.robin.claudeusage.data.SummaryFilter
import com.robin.claudeusage.data.SummaryFilters

/**
 * CCRM-110 (Account Tags) and CCRM-115 (Row Tag Glyph): the glyphs and controls of the Personal / Work tag, drawn from
 * the approved addendum `design/2026-11-11-v20-narrow-duet-and-row-glyph-wireframes.html` (sections "Row glyph" and
 * "Adding and tagging"). A person for Personal, a briefcase for Work — Material outlined, in the muted ink, never a
 * colour and (on rows) never the word.
 */
@DrawableRes
fun tagGlyphRes(tag: AccountTag): Int = when (tag) {
    AccountTag.PERSONAL -> R.drawable.ic_tag_personal
    AccountTag.WORK -> R.drawable.ic_tag_work
}

/** The glyph alone; hidden from accessibility (the tag is spoken once, on the row's name or the chip). */
@Composable
fun TagGlyph(tag: AccountTag, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(tagGlyphRes(tag)),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** What the Settings chip's name-first fit decides (addendum §4): a pure rule so it is unit-tested. */
object TagChipFit {
    /** The chip's glyph and its word's chip: 12 dp glyph, 4 dp, the word, 8 dp of padding and the 1 dp outline. */
    const val CHIP_FIXED_DP = 12f + 4f + 8f + 2f

    /**
     * The name gives first, down to 64 dp at its own text scale (or its own width, if shorter); when that is still
     * not enough for the chip with its word, the chip drops the word and keeps its glyph.
     */
    fun showsWord(availableDp: Float, nameDp: Float, nameScale: Float, othersDp: Float, wordDp: Float): Boolean {
        val floor = minOf(SummaryRows.NAME_FLOOR_DP * nameScale, nameDp)
        return floor + othersDp + CHIP_FIXED_DP + wordDp <= availableDp
    }
}

/**
 * The neutral chip on every account card in Settings › Accounts: outline, 12 dp glyph, [showWord]'s word in
 * `labelSmall`; a tap opens the Tag dialog. TalkBack: "Tag: Personal", with the action "change".
 */
@Composable
fun TagChip(tag: AccountTag, showWord: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .clip(shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(onClickLabel = "change", role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Tag: ${tag.label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagGlyph(tag, 12.dp, ink)
        if (showWord) {
            Spacer(Modifier.width(4.dp))
            Text(tag.label, style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1, softWrap = false)
        }
    }
}

/** "Tag · Teams": two radio rows (glyph + word), the help line, Close. A tap applies at once. */
@Composable
fun TagDialog(label: String, current: AccountTag, onPick: (AccountTag) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag · $label") },
        text = {
            Column(Modifier.selectableGroup()) {
                for (tag in AccountTag.entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = tag == current, role = Role.RadioButton) { onPick(tag) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The row is the control; the radio only draws.
                        RadioButton(selected = tag == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        TagGlyph(tag, 18.dp, MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(10.dp))
                        Text(tag.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tags group accounts on the Summary: All · Personal · Work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * The Add account sheet's "This account is" control (addendum §Adding and tagging): label, a full-width two-segment
 * row (40 dp; glyph and word, the chosen one tonal and SemiBold, the glyph in place of Material's check), the caption.
 * The glyphs leave **both** segments together when "Personal" with its glyph no longer fits half the row (font 2.0 on
 * a 360 dp phone). TalkBack: the group is "This account is"; the segments read "Personal, selected, 1 of 2".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheetTagControl(selected: AccountTag, onSelect: (AccountTag) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val label = MaterialTheme.typography.labelLarge
    Column(Modifier.fillMaxWidth()) {
        Text("This account is", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A segment is half the row; the glyph, its 8 dp gap and the button's 12 dp padding both sides beside the word.
            val word = with(density) { measurer.measure("Personal", label.copy(fontWeight = FontWeight.SemiBold)).size.width.toDp().value }
            val glyphs = AddSheetFit.keepsGlyphs(maxWidth.value, word)
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    // 40 dp at scale 1.0; it grows with the text rather than clipping it.
                    .heightIn(min = 40.dp)
                    .semantics { contentDescription = "This account is" },
            ) {
                AccountTag.entries.forEachIndexed { i, tag ->
                    val on = tag == selected
                    SegmentedButton(
                        selected = on,
                        onClick = { onSelect(tag) },
                        shape = SegmentedButtonDefaults.itemShape(i, AccountTag.entries.size),
                        icon = {
                            if (glyphs) TagGlyph(tag, 18.dp, MaterialTheme.colorScheme.onSurface)
                        },
                        label = {
                            Text(tag.label, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Groups it on the Summary. You can change it later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The Add sheet control's fit rule, pure. */
object AddSheetFit {
    /** Material's segmented button: 12 dp padding each side, an 18 dp glyph and its 8 dp gap, beside the word. */
    fun keepsGlyphs(rowDp: Float, personalWordDp: Float): Boolean = (rowDp / 2f) >= personalWordDp + 24f + 18f + 8f
}

/**
 * The Summary header's control (CCRM-118 (Header Glyphs)): "All" (a word), then the tag's glyph (16 dp × font scale)
 * with the group's count (12 sp, tabular) for Personal and Work; 28 dp high; the chosen segment tonal and SemiBold. A
 * segment with no accounts keeps full ink with its 0 and, tapped, calls [onDisabledTap] (the one-line note); All never
 * is. TalkBack: "Personal, 3 accounts".
 */
@Composable
fun SummaryFilterControl(
    current: SummaryFilter,
    enabled: (SummaryFilter) -> Boolean,
    /** The group's size among all accounts (All: every account), never the rows shown. */
    count: (SummaryFilter) -> Int,
    onChoose: (SummaryFilter) -> Unit,
    onDisabledTap: (SummaryFilter) -> Unit,
    modifier: Modifier = Modifier,
    /** Large text: the control runs under the title across the whole width, its segments sharing it. */
    fill: Boolean = false,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val fontScale = LocalDensity.current.fontScale
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .selectableGroup()
            .heightIn(min = 28.dp)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clip(shape),
    ) {
        SummaryFilter.entries.forEachIndexed { i, f ->
            val on = f == current
            val live = enabled(f)
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
            Box(
                Modifier
                    // Across the whole width the segments share it equally (the glyph and count are the same size).
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .background(if (on) ink.copy(alpha = 0.13f) else Color.Transparent)
                    .clickable { if (live) onChoose(f) else onDisabledTap(f) }
                    .padding(horizontal = 10.dp)
                    .heightIn(min = 28.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = SummaryFilters.description(f.label, count(f))
                        role = Role.RadioButton
                        selected = on
                        // Not disabled(): a disabled node has no click action, and a tap on this segment answers with the
                        // note (addendum T3), TalkBack included.
                        if (!live) stateDescription = "no accounts"

                    },
                contentAlignment = Alignment.Center,
            ) {
                val weight = if (on) FontWeight.SemiBold else FontWeight.Normal
                if (f.tag == null) {
                    Text(f.label, fontSize = 12.sp, fontWeight = weight, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp).offset(y = 1.dp))
                } else {
                    // The glyph and count sat about 1.5 dp above the bar's middle (the 24 dp glyph box and the text line
                    // both carry their air below); 1.5 dp down centres them on the 28 dp bar.
                    Row(Modifier.padding(vertical = 4.dp).offset(y = 1.5.dp), verticalAlignment = Alignment.CenterVertically) {
                        TagGlyph(f.tag, (16f * fontScale).dp, ink)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            count(f).toString(), fontSize = 12.sp, fontWeight = weight, maxLines = 1, softWrap = false,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                    }
                }
            }
        }
    }
}
