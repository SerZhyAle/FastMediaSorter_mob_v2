package com.sza.fastmediasorter.wear.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2054: guards the mapping from an incoming phone language tag to a tag the watch declares.
 *
 * The declared list is passed in rather than parsed, because the wear module has no Robolectric and the
 * contract worth guarding is the resolution, not the XML reader. That the reader reports what the file
 * declares is proven on the phone by UiLanguageCatalogTest against the same parser shape.
 */
class WearLocaleManagerTest {

    /** Mirrors wear/src/main/res/xml/locales_config.xml, in declaration order. */
    private val declared = listOf(
        "en", "zh-Hans", "hi", "es", "fr", "ar", "bn", "pt", "ru", "ur", "uk", "de", "it",
    )

    private fun resolve(tag: String?): String? = WearLanguageCatalog.resolveTagIn(declared, tag)

    @Test
    fun `resolve exact declared language tags`() {
        assertEquals("en", resolve("en"))
        assertEquals("ru", resolve("ru"))
        assertEquals("uk", resolve("uk"))
    }

    @Test
    fun `resolve tags that were refused before S2054`() {
        assertEquals("de", resolve("de"))
        assertEquals("fr", resolve("fr"))
        assertEquals("es", resolve("es"))
        assertEquals("it", resolve("it"))
        assertEquals("ar", resolve("ar"))
        assertEquals("ur", resolve("ur"))
        assertEquals("hi", resolve("hi"))
        assertEquals("bn", resolve("bn"))
        assertEquals("pt", resolve("pt"))
    }

    @Test
    fun `resolve strips region to the declared tag`() {
        assertEquals("en", resolve("en-US"))
        assertEquals("en", resolve("en-GB"))
        assertEquals("ru", resolve("ru-RU"))
        assertEquals("uk", resolve("uk-UA"))
        assertEquals("de", resolve("de-DE"))
        assertEquals("pt", resolve("pt-BR"))
    }

    @Test
    fun `script qualified chinese resolves to the declared script`() {
        assertEquals("zh-Hans", resolve("zh"))
        assertEquals("zh-Hans", resolve("zh-Hans"))
        assertEquals("zh-Hans", resolve("zh-CN"))
    }

    @Test
    fun `resolve is case and whitespace tolerant`() {
        assertEquals("ru", resolve(" RU "))
        assertEquals("uk", resolve("UK"))
        assertEquals("en", resolve("EN-us"))
        assertEquals("de", resolve("De"))
    }

    @Test
    fun `undeclared and blank input is refused`() {
        assertNull(resolve(null))
        assertNull(resolve(""))
        assertNull(resolve("   "))
        assertNull(resolve("ja"))
        assertNull(resolve("ko"))
    }

    @Test
    fun `an empty declaration refuses every tag`() {
        assertNull(WearLanguageCatalog.resolveTagIn(emptyList(), "en"))
    }
}
