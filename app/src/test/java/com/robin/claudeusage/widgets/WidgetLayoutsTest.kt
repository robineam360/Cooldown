package com.robin.claudeusage.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun onlyViewsRemoteViewsAllows() {
        val allowed = setOf(
            "FrameLayout", "LinearLayout", "TextView", "ImageView", "Chronometer",
        )
        for (f in dir.listFiles()!!.filter { it.name.startsWith("widget_") }) {
            val xml = f.readText().replace(Regex("(?s)<!--.*?-->"), "")
            val tags = Regex("""<([A-Za-z.]+)[\s>/]""").findAll(xml).map { it.groupValues[1] }
                .filterNot { it == "?xml" }.toSet()
            assertFalse("${f.name}: ${tags - allowed}", (tags - allowed).isNotEmpty())
        }
    }
}
