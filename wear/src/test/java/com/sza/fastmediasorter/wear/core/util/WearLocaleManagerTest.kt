package com.sza.fastmediasorter.wear.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearLocaleManagerTest {

    @Test
    fun `resolve exact supported language tags`() {
        assertEquals("en", WearLocaleManager.resolveSupportedTag("en"))
        assertEquals("ru", WearLocaleManager.resolveSupportedTag("ru"))
        assertEquals("uk", WearLocaleManager.resolveSupportedTag("uk"))
    }

    @Test
    fun `resolve regional language tags`() {
        assertEquals("en", WearLocaleManager.resolveSupportedTag("en-US"))
        assertEquals("en", WearLocaleManager.resolveSupportedTag("en-GB"))
        assertEquals("ru", WearLocaleManager.resolveSupportedTag("ru-RU"))
        assertEquals("uk", WearLocaleManager.resolveSupportedTag("uk-UA"))
    }

    @Test
    fun `resolve uppercase and whitespace codes`() {
        assertEquals("ru", WearLocaleManager.resolveSupportedTag(" RU "))
        assertEquals("uk", WearLocaleManager.resolveSupportedTag("UK"))
        assertEquals("en", WearLocaleManager.resolveSupportedTag("EN-us"))
    }

    @Test
    fun `reject unsupported languages and blank strings`() {
        assertNull(WearLocaleManager.resolveSupportedTag(null))
        assertNull(WearLocaleManager.resolveSupportedTag(""))
        assertNull(WearLocaleManager.resolveSupportedTag("   "))
        assertNull(WearLocaleManager.resolveSupportedTag("de"))
        assertNull(WearLocaleManager.resolveSupportedTag("fr"))
        assertNull(WearLocaleManager.resolveSupportedTag("es"))
        assertNull(WearLocaleManager.resolveSupportedTag("zh-Hans"))
        assertNull(WearLocaleManager.resolveSupportedTag("it"))
    }
}
