package com.robin.claudeusage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CCBG-35 (Tab Clip): the fixed row only while every title fits its share, padding included. */
class SettingsTabsTest {

    // "Appearance" at bodyMedium is about 72 dp; the others are narrower. 1 px = 1 dp here.
    private val titles = listOf(60f, 40f, 72f, 36f)
    private val padding = 20f

    @Test
    fun `a 360 dp phone scrolls, because Appearance does not fit 90 dp`() {
        assertFalse(SettingsTabs.fit(titles, 360f / 4, padding))
    }

    @Test
    fun `the Fold cover and inner screens keep the fixed row`() {
        assertTrue(SettingsTabs.fit(titles, 411f / 4, padding))
        assertTrue(SettingsTabs.fit(titles, 750f / 4, padding))
    }

    @Test
    fun `a large font can scroll on the cover and stay fixed inside, as accepted`() {
        val doubled = titles.map { it * 2 }
        assertFalse(SettingsTabs.fit(doubled, 411f / 4, padding))
        assertTrue(SettingsTabs.fit(doubled, 750f / 4, padding))
    }

    @Test
    fun `a title exactly filling its share still fits`() {
        assertTrue(SettingsTabs.fit(listOf(70f), 90f, padding))
    }
}
