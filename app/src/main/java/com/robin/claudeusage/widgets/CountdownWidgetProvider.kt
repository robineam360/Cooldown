package com.robin.claudeusage.widgets

/**
 * CCRM-81 (Countdown Face): when one account's window comes back, as a
 * launcher-ticked chronometer or an absolute clock time — never a static relative one (R4).
 *
 * **R9: this class name is permanent** — a rename after release deletes every user's
 * placements. Everything it draws comes from `WidgetFace.render` through [WidgetHost].
 */
class CountdownWidgetProvider : FaceWidgetProvider(Face.COUNTDOWN)
