package com.sza.fastmediasorter.ui.common.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HelpTargetResolverTest {

    private val map = HelpMap.parse(
        """
        {
          "schemaVersion": 1,
          "pageTemplate": "{locale}/{help}.html",
          "fallbackLocale": "en",
          "futureMember": { "ignored": true },
          "surfaces": [
            { "id": "Main", "name": "Main", "form": "page", "help": "main" },
            { "id": "Settings", "name": "Settings", "form": "page", "help": "settings-general",
              "pages": [ { "id": "settings.media", "name": "Media", "help": "settings-media" } ] },
            { "id": "Filter", "name": "Filter", "form": "inherit", "parent": "Main", "reason": "r" },
            { "id": "Chain", "name": "Chain", "form": "inherit", "parent": "Filter", "reason": "r" },
            { "id": "LoopA", "name": "A", "form": "inherit", "parent": "LoopB", "reason": "r" },
            { "id": "LoopB", "name": "B", "form": "inherit", "parent": "LoopA", "reason": "r" },
            { "id": "Confirm", "name": "Confirm", "form": "none", "reason": "r" },
            { "id": "Future", "name": "Future", "form": "hologram" }
          ]
        }
        """.trimIndent(),
    )

    private val resolver = HelpTargetResolver(
        map,
        setOf("en/main.html", "ru/main.html", "en/settings-general.html", "en/settings-media.html"),
    )

    @Test
    fun `page row resolves to the bundled page`() {
        val target = resolver.resolve("Main", "en")
        assertEquals("file:///android_asset/help/en/main.html", target?.assetUrl)
        assertEquals("main", target?.helpId)
    }

    @Test
    fun `translated page is used when present`() {
        val target = resolver.resolve("Main", "ru")
        assertEquals("file:///android_asset/help/ru/main.html", target?.assetUrl)
        assertTrue(target!!.siteUrl.endsWith("documentation/help/main-ru.html"))
    }

    @Test
    fun `missing translation falls back to english`() {
        val target = resolver.resolve("Main", "de")
        assertEquals("file:///android_asset/help/en/main.html", target?.assetUrl)
        assertTrue(target!!.siteUrl.endsWith("documentation/help/main.html"))
    }

    @Test
    fun `inherit follows the parent`() {
        assertEquals("main", resolver.resolve("Filter", "en")?.helpId)
    }

    @Test
    fun `inherit cycle resolves to null`() {
        assertNull(resolver.resolve("LoopA", "en"))
    }

    @Test
    fun `none and unknown surfaces resolve to null`() {
        assertNull(resolver.resolve("Confirm", "en"))
        assertNull(resolver.resolve("Nowhere", "en"))
    }

    @Test
    fun `row with an unknown form is skipped`() {
        assertNull(map.surface("Future"))
    }

    @Test
    fun `hasPage is true for a page row and for inherit rows that reach one`() {
        assertTrue(resolver.hasPage("Main"))
        assertTrue(resolver.hasPage("Filter"))
        assertTrue(resolver.hasPage("Chain"))
        assertTrue(resolver.hasPage("settings.media"))
    }

    @Test
    fun `hasPage is false for none, a cycle, an unknown id and a page with no file`() {
        assertFalse(resolver.hasPage("Confirm"))
        assertFalse(resolver.hasPage("LoopA"))
        assertFalse(resolver.hasPage("Nowhere"))
        assertFalse(HelpTargetResolver(map, emptySet()).hasPage("Main"))
    }

    @Test
    fun `settings sub-page resolves through pages`() {
        assertEquals("settings-media", resolver.resolve("settings.media", "en")?.helpId)
    }

    @Test
    fun `element anchor is appended`() {
        val target = resolver.resolve("Main", "en", elementAnchor = "btnFilter")
        assertEquals("file:///android_asset/help/en/main.html#btnFilter", target?.assetUrl)
        assertTrue(target!!.siteUrl.endsWith("#btnFilter"))
    }

    @Test
    fun `page without any bundled file resolves to null`() {
        val empty = HelpTargetResolver(map, emptySet())
        assertNull(empty.resolve("Main", "en"))
    }

    @Test
    fun `shipped help map parses`() {
        val file = listOf("src/main/assets/help/help-map.json", "app_v2/src/main/assets/help/help-map.json")
            .map(::File)
            .first { it.exists() }
        val shipped = HelpMap.parse(file.readText())
        assertEquals(1, shipped.schemaVersion)
        assertEquals(HelpForm.PAGE, shipped.surface("MainActivity")?.form)
        assertTrue(shipped.surfaces.filter { it.form == HelpForm.NONE }.all { !it.reason.isNullOrBlank() })
    }
}
