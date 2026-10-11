package com.sza.fastmediasorter.ui.common.support

import com.sza.fastmediasorter.ui.common.input.InputHelpLinkResolver
import com.sza.fastmediasorter.ui.common.input.UiSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the app's portal links to the published portal sources. Unit tests run with the module as
 * the working directory, so `docs/media-exchange-portal/` is one level up.
 */
class MediaExchangePortalLinksTest {

    private val languages = listOf("en", "ru", "uk")

    @Test
    fun `every page in every portal language has a source page`() {
        for (page in MediaExchangePortalLinks.Page.entries) {
            for (language in languages) {
                val url = MediaExchangePortalLinks.urlFor(page, language)
                val onSite = url.startsWith(InputHelpLinkResolver.SITE_BASE)
                assertTrue("$page/$language: $url is not under the site", onSite)
                val relative = url.removePrefix(InputHelpLinkResolver.SITE_BASE)
                val source = if (relative.endsWith("/")) {
                    relative + "index.md"
                } else {
                    relative.removeSuffix(".html") + ".md"
                }
                assertTrue("$page/$language: no source $source", File("../$source").exists())
            }
        }
    }

    @Test
    fun `a language without a portal edition gets the English address`() {
        for (page in MediaExchangePortalLinks.Page.entries) {
            val english = MediaExchangePortalLinks.urlFor(page, "en")
            assertEquals(english, MediaExchangePortalLinks.urlFor(page, "de"))
            assertEquals(english, MediaExchangePortalLinks.urlFor(page, ""))
            assertNotEquals(english, MediaExchangePortalLinks.urlFor(page, "ru"))
        }
    }

    @Test
    fun `language is matched case-insensitively`() {
        assertEquals(
            MediaExchangePortalLinks.urlFor(MediaExchangePortalLinks.Page.CLOUDS, "uk"),
            MediaExchangePortalLinks.urlFor(MediaExchangePortalLinks.Page.CLOUDS, "UK"),
        )
    }

    @Test
    fun `only the source-choosing surfaces carry a portal page`() {
        val mapped = UiSurface.entries.filter { MediaExchangePortalLinks.pageFor(it) != null }
        assertEquals(listOf(UiSurface.ADD_RESOURCE, UiSurface.CLOUD_PICKER).toSet(), mapped.toSet())
        assertNull(MediaExchangePortalLinks.pageFor(UiSurface.MAIN))
        assertEquals(
            MediaExchangePortalLinks.Page.CLOUDS,
            MediaExchangePortalLinks.pageFor(UiSurface.CLOUD_PICKER),
        )
    }
}
