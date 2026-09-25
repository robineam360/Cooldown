package com.robin.claudeusage.widgets

/**
 * CCRM-82 (Accounts Strip): every account as its own ring in registry order, each
 * on its headline window — never a sum, an average or a combined figure.
 *
 * **R9: this class name is permanent** — a rename after release deletes every user's
 * placements. Everything it draws comes from `WidgetFace.render` through [WidgetHost].
 */
class StripWidgetProvider : FaceWidgetProvider(Face.STRIP)
