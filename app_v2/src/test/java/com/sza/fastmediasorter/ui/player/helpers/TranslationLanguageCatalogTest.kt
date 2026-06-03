package com.sza.fastmediasorter.ui.player.helpers

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationLanguageCatalogTest {

    @Test
    fun getFlagEmoji_returnsCzechFlag() {
        assertEquals("🇨🇿", TranslationLanguageCatalog.getFlagEmoji("CZ"))
    }

    @Test
    fun buildTargetLanguageList_containsFullMlKitWhitelistAndFormattedCzech() {
        val languages = TranslationLanguageCatalog.buildTargetLanguageList("en")
        val czech = TranslationLanguageCatalog.findLanguage("cs", Locale.ENGLISH)

        assertEquals(59, TranslationLanguageCatalog.supportedCodes.size)
        assertEquals(59, languages.size)
        assertNotNull(czech)
        assertEquals("🇨🇿 Czech (Čeština)", TranslationLanguageCatalog.formatLanguage(czech!!))
    }

    @Test
    fun languageCapabilities_markTranslationOcrAndNoLegalLevels() {
        val czech = TranslationLanguageCatalog.findLanguage("cs", Locale.ENGLISH)
        val russian = TranslationLanguageCatalog.findLanguage("ru", Locale.ENGLISH)

        assertNotNull(czech)
        assertNotNull(russian)
        assertTrue(czech!!.capabilities.contains(LanguageCapability.TRANSLATION))
        assertTrue(czech.capabilities.contains(LanguageCapability.BASIC_OCR))
        assertTrue(!czech.capabilities.contains(LanguageCapability.NO_LEGAL_OCR))
        assertTrue(russian!!.capabilities.contains(LanguageCapability.QUALITY_OCR))
        assertTrue(russian.capabilities.contains(LanguageCapability.NO_LEGAL_OCR))
    }

    @Test
    fun buildSourceLanguageList_ordersAutoInterfaceAndEnglishFirst() {
        val languages = TranslationLanguageCatalog.buildSourceLanguageList("uk")

        assertEquals("auto", languages[0].code)
        assertEquals("uk", languages[1].code)
        assertEquals("en", languages[2].code)
    }

    @Test
    fun buildTargetLanguageList_ordersInterfaceAndEnglishFirst() {
        val languages = TranslationLanguageCatalog.buildTargetLanguageList("ru")

        assertEquals("ru", languages[0].code)
        assertEquals("en", languages[1].code)
        assertTrue(languages.drop(2).any { it.code == "cs" })
    }

    @Test
    fun languageCodeToMLKit_keepsCzechCode() {
        assertEquals("cs", TranslationManager.languageCodeToMLKit("cs"))
    }
}
