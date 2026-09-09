package com.robin.claudeusage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the merged "Show red past the pace mark" toggle's migration (CCRM-61,
 * Settings Diet): once `showOverPace` has been written explicitly it always
 * wins; before that, the retired `paceOverInApp` in-app toggle's value carries
 * over; with neither ever written, the shared default is true.
 */
class SettingsMigrationTest {

    @Test
    fun `an explicit showOverPace value always wins`() {
        assertTrue(SettingsMigration.showOverPace(stored = true, legacyInApp = false))
        assertFalse(SettingsMigration.showOverPace(stored = false, legacyInApp = true))
    }

    @Test
    fun `an upgrading install carries the legacy in-app toggle's true`() {
        assertTrue(SettingsMigration.showOverPace(stored = null, legacyInApp = true))
    }

    @Test
    fun `an upgrading install carries the legacy in-app toggle's false`() {
        assertFalse(SettingsMigration.showOverPace(stored = null, legacyInApp = false))
    }

    @Test
    fun `a fresh install with neither key written defaults true`() {
        assertEquals(true, SettingsMigration.showOverPace(stored = null, legacyInApp = null))
    }
}
