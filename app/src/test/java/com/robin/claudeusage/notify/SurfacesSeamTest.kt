package com.robin.claudeusage.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CCRM-78 (Widgets Reborn), R7: every surface redraw outside `notify/` goes through
 * [Surfaces.refresh], never straight to `PinnedNotification.update` — a direct call would
 * redraw the notification and silently leave every widget behind.
 */
class SurfacesSeamTest {

    private val root: File =
        listOf(File("app/src/main/java"), File("src/main/java")).firstOrNull { it.isDirectory }
            ?: error("couldn't find the sources from ${File(".").absolutePath}")

    private val direct = Regex("""PinnedNotification\s*\.\s*update\s*\(""")

    @Test
    fun onlyTheSeamCallsPinnedNotificationUpdate() {
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "Surfaces.kt" || it.name == "PinnedNotification.kt" }
            .filter { f -> f.readLines().any { line -> direct.containsMatchIn(line.substringBefore("//")) } }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun theThirteenCallSitesAreOnTheSeam() {
        val calls = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { f -> Regex("""Surfaces\.refresh\(""").findAll(f.readText()).count() }
        assertTrue("expected at least the 13 replaced call sites, found $calls", calls >= 13)
    }
}
