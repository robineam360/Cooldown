package com.robin.claudeusage.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The widget layouts come in threes — base, `_sd` and `_sl` — because RemoteViews cannot
 * set a text shadow at runtime, and a Transparent face needs one in the opposite tone
 * (rev D). The three must differ in the text style and nothing else.
 */
class WidgetLayoutsTest {

    private val dir: File =
        listOf(File("app/src/main/res/layout"), File("src/main/res/layout")).firstOrNull { it.isDirectory }
            ?: error("couldn't find res/layout from ${File(".").absolutePath}")

    @Test
    fun theShadowVariantsDifferOnlyInStyle() {
        for (face in listOf("widget_ring", "widget_number", "widget_countdown", "widget_strip")) {
            val base = File(dir, "$face.xml").readText()
            assertEquals(
                face, base.replace("@style/WFace\"", "@style/WFace.ShadowDark\""),
                File(dir, "${face}_sd.xml").readText(),
            )
            assertEquals(
                face, base.replace("@style/WFace\"", "@style/WFace.ShadowLight\""),
                File(dir, "${face}_sl.xml").readText(),
            )
        }
    }

    /**
     * Covers the notification layouts too: CCBG-31 (Alert Crash) was a plain `<View>`
     * spacer in `notif_panel_single.xml`, which the shade refuses to inflate — and for a
     * foreground service's notification that refusal kills the app, in a restart loop.
     * `include` is resolved by the inflater itself, so it never reaches the filter.
     */
    @Test
    fun onlyViewsRemoteViewsAllows() {
        val allowed = setOf(
            "FrameLayout", "LinearLayout", "TextView", "ImageView", "Chronometer", "include",
        )
        val remote = dir.listFiles()!!.filter { it.name.startsWith("widget_") || it.name.startsWith("notif_") }
        assertTrue("no notification layouts found", remote.any { it.name.startsWith("notif_") })
        for (f in remote) {
            val xml = f.readText().replace(Regex("(?s)<!--.*?-->"), "")
            val tags = Regex("""<([A-Za-z.]+)[\s>/]""").findAll(xml).map { it.groupValues[1] }
                .filterNot { it == "?xml" }.toSet()
            assertFalse("${f.name}: ${tags - allowed}", (tags - allowed).isNotEmpty())
        }
    }
}
