package com.robin.claudeusage.widgets

/**
 * CCRM-79 (Ring Face): the status-bar ring at home-screen size, 1×1 and 2×2 —
 * one account's 5h window, or its headline window when it has no 5h.
 *
 * **R9: this class name is permanent** — a rename after release deletes every user's
 * placements. Everything it draws comes from `WidgetFace.render` through [WidgetHost].
 */
class RingWidgetProvider : FaceWidgetProvider(Face.RING)
