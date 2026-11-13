package com.robin.claudeusage.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.robin.claudeusage.data.AccountTag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.robin.claudeusage.R
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.UsageCache

/**
 * The provider's own mark, not a dot (CCRM-56 (Provider Identity), decision 4) —
 * single-colour hand-traced vector paths of each company's public mark, unmodified
 * in shape. Sizes per the approved wireframe (rev B1): 20dp before the label on
 * cards and tabs, 14dp in chips and on the pinned label line, 28dp in the
 * Add-account sheet. [tint] is the account's resolved accent, or ink on a
 * monochrome surface (decision 7); pass null to draw the drawable's own colour.
 */
@Composable
fun ProviderMark(provider: Provider, size: Dp = 16.dp, tint: Color? = null) {
    Image(
        painter = painterResource(providerMarkRes(provider)),
        contentDescription = provider.displayName,
        modifier = Modifier.size(size),
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

/** The mark's drawable resource, for surfaces that build RemoteViews directly. */
@DrawableRes
fun providerMarkRes(provider: Provider): Int = when (provider) {
    Provider.CLAUDE -> R.drawable.ic_provider_claude
    Provider.CHATGPT -> R.drawable.ic_provider_chatgpt
    Provider.ANTIGRAVITY -> R.drawable.ic_provider_gemini
}

/**
 * CCRM-118 (Header Glyphs): the account icon — a 24 dp box, the provider [mark] at its top-left in the account's
 * [accent], and, when [marked], the tag badge: a circle of radius 8 dp cleared out of the mark's lower right (centre
 * 17.5, 17.5) and the tag's filled glyph, 12 dp, in [ink] centred there. Unmarked, the mark stays whole at the same
 * place, so a badged and a plain tab align. The tag is never a colour.
 */
@Composable
fun BadgedMark(
    provider: Provider,
    tag: AccountTag,
    accent: Color,
    ink: Color,
    mark: Dp = 20.dp,
    marked: Boolean,
) {
    Box(Modifier.size(BADGE_BOX_DP.dp)) {
        Box(
            Modifier
                .size(BADGE_BOX_DP.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (marked) {
                        drawCircle(
                            Color.Black,
                            radius = 8.dp.toPx(),
                            center = Offset(BADGE_CENTRE_DP.dp.toPx(), BADGE_CENTRE_DP.dp.toPx()),
                            blendMode = BlendMode.Clear,
                        )
                    }
                },
        ) {
            ProviderMark(provider, size = mark, tint = accent)
        }
        if (marked) {
            Image(
                painter = painterResource(tagGlyphFilledRes(tag)),
                contentDescription = null,
                modifier = Modifier
                    .offset((BADGE_CENTRE_DP - 6f).dp, (BADGE_CENTRE_DP - 6f).dp)
                    .size(12.dp),
                colorFilter = ColorFilter.tint(ink),
            )
        }
    }
}

private const val BADGE_BOX_DP = 24f
private const val BADGE_CENTRE_DP = 17.5f

/** The icon's box and the gap before the name: 24 + 2 = the 20 + 6 the plain label measured, so TabStripFit does not move. */
object TabLabelMetrics {
    const val BOX_DP = BADGE_BOX_DP
    const val GAP_DP = 2f
    const val PLAIN_MARK_DP = 20f
    const val PLAIN_GAP_DP = 6f
}

@DrawableRes
fun tagGlyphFilledRes(tag: AccountTag): Int = when (tag) {
    AccountTag.PERSONAL -> R.drawable.ic_tag_personal_filled
    AccountTag.WORK -> R.drawable.ic_tag_work_filled
}

/**
 * A tab strip's label: the [BadgedMark] (the mark always in the account's own resolved accent regardless of
 * selection, the badge in the tab's ink so an unselected tab's figure is 60 %), 2 dp, then the account's own label
 * text. TalkBack: the mark keeps the provider's name, the text says "Teams, Work account". Shared by the Main and
 * History tab strips.
 */
@Composable
fun ProviderTabLabel(cache: UsageCache, profile: Profile, dark: Boolean = appDark()) {
    val tag = cache.tag(profile)
    val name = cache.profileLabel(profile)
    Row(verticalAlignment = Alignment.CenterVertically) {
        BadgedMark(
            profile.provider,
            tag,
            accent = Palette.color(Palette.accentName(cache, profile), dark),
            ink = LocalContentColor.current,
            marked = cache.tagMarks()[tag] != false,
        )
        Spacer(Modifier.width(TabLabelMetrics.GAP_DP.dp))
        Text(name, modifier = Modifier.semantics { contentDescription = "$name, ${tag.label} account" })
    }
}
