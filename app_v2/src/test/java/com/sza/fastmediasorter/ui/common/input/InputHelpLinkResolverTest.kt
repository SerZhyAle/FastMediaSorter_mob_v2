package com.sza.fastmediasorter.ui.common.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the F1 page registry to the documentation corpus. Unit tests run with the module as the
 * working directory, so the page manifest and the published portal tree are one level up.
 */
class InputHelpLinkResolverTest {

    private val manifest: Map<String, String> by lazy {
        val file = File("../docs/docs-pages-manifest.jsonl")
        assertTrue("page manifest not found at ${file.absolutePath}", file.exists())
        file.readLines().filter { it.isNotBlank() }.associate { line ->
            field(line, "page_id") to field(line, "canonical_path")
        }
    }

    @Test
    fun `every surface points at a manifest page with the same path`() {
        for (surface in UiSurface.entries) {
            val page = InputHelpLinkResolver.pageFor(surface)
            val manifestPath = manifest[page.pageId]
            assertNotNull("$surface: page id ${page.pageId} is not in the page manifest", manifestPath)
            assertEquals("$surface: path differs from the manifest", manifestPath, page.path)
        }
    }

    @Test
    fun `every page and every declared translation is published`() {
        for (surface in UiSurface.entries) {
            val page = InputHelpLinkResolver.pageFor(surface)
            assertTrue("$surface: ${page.path} missing", File("../${page.path}").exists())
            for (lang in page.translatedLanguages) {
                val translated = InputHelpLinkResolver.urlFor(page, lang)
                    .removePrefix(InputHelpLinkResolver.SITE_BASE)
                assertTrue("$surface: $lang translation $translated missing", File("../$translated").exists())
            }
        }
    }

    @Test
    fun `untranslated page opens the English original`() {
        val url = InputHelpLinkResolver.urlFor(UiSurface.MAIN, "ru")
        val expected = "https://serzhyale.github.io/FastMediaSorter_mob_v2/" +
            "documentation/getting-started/main-screen-overview.html"
        assertEquals(expected, url)
    }

    @Test
    fun `declared translation uses the site locale suffix`() {
        val page = InputHelpLinkResolver.DocsHelpPage(
            pageId = "x.y",
            path = "documentation/x/y.html",
            translatedLanguages = setOf("uk"),
        )
        val base = InputHelpLinkResolver.SITE_BASE
        assertEquals(base + "documentation/x/y-uk.html", InputHelpLinkResolver.urlFor(page, "UK"))
        assertEquals(base + "documentation/x/y.html", InputHelpLinkResolver.urlFor(page, "en"))
    }

    private fun field(line: String, name: String): String {
        val match = Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(line)
        assertNotNull("field $name missing in: $line", match)
        return match!!.groupValues[1]
    }
}
