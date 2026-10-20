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
    fun `the exchange server page has an edition for each portal language`() {
        val page = MediaExchangePortalLinks.Page.EXCHANGE_SERVER
        assertTrue(MediaExchangePortalLinks.urlFor(page, "en").endsWith("/exchange-server.html"))
        assertTrue(MediaExchangePortalLinks.urlFor(page, "ru").endsWith("/exchange-server-ru.html"))
        assertTrue(MediaExchangePortalLinks.urlFor(page, "uk").endsWith("/exchange-server-uk.html"))
    }

    @Test
    fun `the account deletion page is a page of its own in each portal language`() {
        val page = MediaExchangePortalLinks.Page.ACCOUNT_DELETION
        assertTrue(MediaExchangePortalLinks.urlFor(page, "en").endsWith("/account-deletion.html"))
        assertTrue(MediaExchangePortalLinks.urlFor(page, "ru").endsWith("/account-deletion-ru.html"))
        assertTrue(MediaExchangePortalLinks.urlFor(page, "uk").endsWith("/account-deletion-uk.html"))
        for (language in languages) {
            assertNotEquals(
                MediaExchangePortalLinks.urlFor(MediaExchangePortalLinks.Page.EXCHANGE_SERVER, language),
                MediaExchangePortalLinks.urlFor(page, language),
            )
        }
    }

    @Test
    fun `each scenario page of release 46 has the address of its own page`() {
        val stems = mapOf(
            MediaExchangePortalLinks.Page.FOLDERS_EXCHANGE_SERVER to "folders-exchange-server",
            MediaExchangePortalLinks.Page.FOLDERS_GOOGLE_DRIVE to "folders-google-drive",
            MediaExchangePortalLinks.Page.BROADCAST_START to "broadcast-start",
            MediaExchangePortalLinks.Page.BROADCAST_RECEIVE to "broadcast-receive",
            MediaExchangePortalLinks.Page.WATCH_EXCHANGE to "watch-exchange",
            MediaExchangePortalLinks.Page.SCHEDULE_REMOTE_FOLDERS to "schedule-remote-folders",
            MediaExchangePortalLinks.Page.PHONE_SHARING to "phone-sharing",
            MediaExchangePortalLinks.Page.MY_DEVICES to "my-devices",
        )
        for ((page, stem) in stems) {
            assertTrue(MediaExchangePortalLinks.urlFor(page, "en").endsWith("/$stem.html"))
            assertTrue(MediaExchangePortalLinks.urlFor(page, "ru").endsWith("/$stem-ru.html"))
            assertTrue(MediaExchangePortalLinks.urlFor(page, "uk").endsWith("/$stem-uk.html"))
        }
    }

    @Test
    fun `no two pages share an address`() {
        val addresses = MediaExchangePortalLinks.Page.entries.flatMap { page ->
            languages.map { MediaExchangePortalLinks.urlFor(page, it) }
        }
        assertEquals(addresses.size, addresses.toSet().size)
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
