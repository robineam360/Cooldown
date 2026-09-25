package com.robin.claudeusage.widgets

/**
 * CCRM-80 (Number Face): the Huge-number row for one account at 2×1, 4×1 and
 * 4×2, the last with the `[5h | Weekly]` chips and the account cycler.
 *
 * **R9: this class name is permanent** — a rename after release deletes every user's
 * placements. Everything it draws comes from `WidgetFace.render` through [WidgetHost].
 */
class NumberWidgetProvider : FaceWidgetProvider(Face.NUMBER)
