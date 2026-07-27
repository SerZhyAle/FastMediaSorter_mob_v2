package com.sza.fastmediasorter.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val ROBOLECTRIC_MAX_SDK = 34

/**
 * S1190: guards the contract the whole language mechanism rests on - the catalog reports exactly what
 * `res/xml/locales_config.xml` declares. The test reads the catalog, never a second copy of the list,
 * so adding a language keeps it passing while a code-side list would break it.
 */
// Robolectric 4.11 ships no image for API 36, so the test runs on the newest one it has. The parser
// under test uses no API-level-specific behaviour, so the older image proves the same contract.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK])
class UiLanguageCatalogTest {

    @Before
    fun setUp() {
        UiLanguageCatalog.ensureInitialized(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `declared locales are reported in declaration order`() {
        val tags = UiLanguageCatalog.supportedTags
        assertEquals("en", tags.first())
        assertTrue("zh-Hans must be declared with its script", tags.contains("zh-Hans"))
        assertTrue(tags.containsAll(listOf("ru", "uk", "de", "it", "ar", "ur")))
    }

    @Test
    fun `undeclared language is not supported`() {
        assertFalse(UiLanguageCatalog.isSupported("ja"))
        assertNull(UiLanguageCatalog.resolveTag("ja"))
    }

    @Test
    fun `language subtag resolves to the declared tag`() {
        assertEquals("zh-Hans", UiLanguageCatalog.resolveTag("zh"))
        assertEquals("ru", UiLanguageCatalog.resolveTag("RU"))
    }

    @Test
    fun `every declared language has a non-blank name in its own language`() {
        UiLanguageCatalog.supportedTags.forEach { tag ->
            assertTrue("blank display name for $tag", UiLanguageCatalog.displayName(tag).isNotBlank())
        }
    }

}
