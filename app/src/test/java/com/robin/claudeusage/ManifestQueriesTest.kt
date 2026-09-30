package com.robin.claudeusage

import com.robin.claudeusage.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * CCRM-88 (Scoped Queries): the main manifest asks for no broad package visibility, and
 * its `<queries>` names exactly the apps the code opens — the browsers the sign-in
 * picker probes for, and every provider's own app. A fourth provider that forgets its
 * `<package>` line fails here, not silently on a phone.
 */
class ManifestQueriesTest {

    private val android = "http://schemas.android.com/apk/res/android"

    // Gradle runs unit tests from the module directory.
    private val manifest = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))
        .documentElement

    private fun Element.all(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun `QUERY_ALL_PACKAGES is not requested`() {
        val permissions = manifest.all("uses-permission").map { it.getAttributeNS(android, "name") }
        assertFalse(permissions.contains("android.permission.QUERY_ALL_PACKAGES"))
    }

    @Test
    fun `every provider app is visible, and nothing else by name`() {
        val queries = manifest.all("queries").single()
        val packages = queries.all("package").map { it.getAttributeNS(android, "name") }.toSet()
        assertEquals(Provider.entries.map { it.appPackage }.toSet(), packages)
    }

    @Test
    fun `the browser query matches the picker's probe`() {
        val intent = manifest.all("queries").single().all("intent").single()
        assertEquals(
            listOf("android.intent.action.VIEW"),
            intent.all("action").map { it.getAttributeNS(android, "name") },
        )
        assertEquals(
            listOf("android.intent.category.BROWSABLE"),
            intent.all("category").map { it.getAttributeNS(android, "name") },
        )
        assertEquals(listOf("https"), intent.all("data").map { it.getAttributeNS(android, "scheme") })
    }
}
