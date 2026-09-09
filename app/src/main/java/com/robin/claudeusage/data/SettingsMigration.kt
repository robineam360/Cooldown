package com.robin.claudeusage.data

/**
 * Pure migration decisions for settings whose shape changed under CCRM-61
 * (Settings Diet). Kept free of [android.content.SharedPreferences] so the
 * decision itself is unit-testable without Robolectric; [UsageCache] does the
 * reading and writing and hands the raw (nullable) values in here.
 */
object SettingsMigration {
    /**
     * The merged "Show red past the pace mark" toggle: the app and the
     * notification each had their own switch (`paceOverInApp` /
     * `paceOverOnNotification`); CCRM-61 collapses them to one `showOverPace`
     * key. [stored] is that new key's value, or null if nothing has written it
     * yet. [legacyInApp] is the retired `paceOverInApp` key's value, or null if
     * that was never written either — an upgrading install carries its in-app
     * choice forward; a fresh install has neither and lands on the shared
     * default, true.
     */
    fun showOverPace(stored: Boolean?, legacyInApp: Boolean?): Boolean =
        stored ?: legacyInApp ?: true
}
