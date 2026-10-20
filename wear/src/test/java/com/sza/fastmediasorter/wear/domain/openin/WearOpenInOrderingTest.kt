package com.sza.fastmediasorter.wear.domain.openin

import com.sza.fastmediasorter.wear.domain.model.WearOpenInAction
import com.sza.fastmediasorter.wear.domain.model.WearOpenInCandidate
import com.sza.fastmediasorter.wear.domain.model.WearOpenInRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4381: the order of the list - last used, default browser, default maps, the rest by name (ADR-4). */
class WearOpenInOrderingTest {

    private fun app(packageName: String, label: String) =
        WearOpenInCandidate(packageName, "$packageName.Main", label, WearOpenInAction.SEND_TEXT)

    private val browser = app("pkg.browser", "Browser")
    private val maps = app("pkg.maps", "Maps")
    private val chat = app("pkg.chat", "chat")
    private val notes = app("pkg.notes", "Notes")
    private val zebra = app("pkg.zebra", "Zebra")

    private fun packagesOf(
        candidates: List<WearOpenInCandidate>,
        defaultBrowser: String? = null,
        defaultMaps: String? = null,
        lastUsed: String? = null,
    ) = WearOpenInOrdering.order(candidates, defaultBrowser, defaultMaps, lastUsed).map { it.packageName }

    @Test
    fun `all four positions are filled in order`() {
        val result = WearOpenInOrdering.order(
            candidates = listOf(zebra, maps, notes, browser, chat),
            defaultBrowser = "pkg.browser",
            defaultMaps = "pkg.maps",
            lastUsed = "pkg.zebra",
        )

        assertEquals(
            listOf("pkg.zebra", "pkg.browser", "pkg.maps", "pkg.chat", "pkg.notes"),
            result.map { it.packageName },
        )
        assertEquals(
            listOf(
                WearOpenInRole.LAST_USED,
                WearOpenInRole.BROWSER,
                WearOpenInRole.MAPS,
                WearOpenInRole.OTHER,
                WearOpenInRole.OTHER,
            ),
            result.map { it.role },
        )
    }

    @Test
    fun `without a last choice the browser and maps lead`() {
        assertEquals(
            listOf("pkg.browser", "pkg.maps", "pkg.chat", "pkg.zebra"),
            packagesOf(listOf(zebra, chat, maps, browser), "pkg.browser", "pkg.maps"),
        )
    }

    @Test
    fun `the rest is sorted by name ignoring case`() {
        assertEquals(
            listOf("pkg.chat", "pkg.notes", "pkg.zebra"),
            packagesOf(listOf(zebra, notes, chat)),
        )
    }

    @Test
    fun `a last choice equal to a default is pinned once with the last used role`() {
        val result = WearOpenInOrdering.order(
            candidates = listOf(browser, chat),
            defaultBrowser = "pkg.browser",
            defaultMaps = null,
            lastUsed = "pkg.browser",
        )

        assertEquals(listOf("pkg.browser", "pkg.chat"), result.map { it.packageName })
        assertEquals(WearOpenInRole.LAST_USED, result.first().role)
    }

    @Test
    fun `a pinned package that cannot take the object pins nothing`() {
        assertEquals(
            listOf("pkg.chat", "pkg.notes"),
            packagesOf(listOf(notes, chat), "pkg.browser", "pkg.maps", "pkg.gone"),
        )
    }

    @Test
    fun `a package listed twice appears once`() {
        val twice = listOf(chat, chat.copy(activityName = "pkg.chat.Other"), notes)

        assertEquals(listOf("pkg.chat", "pkg.notes"), packagesOf(twice))
    }

    @Test
    fun `no candidates gives an empty list`() {
        assertTrue(WearOpenInOrdering.order(emptyList(), "pkg.browser", "pkg.maps", "pkg.chat").isEmpty())
    }
}
