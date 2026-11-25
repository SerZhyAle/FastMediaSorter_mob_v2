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
        assertEquals("Czech (Čeština)", TranslationLanguageCatalog.formatLanguage(czech!!))
    }

    @Test
    fun languageItems_carryNoFlag_onlyAutoDetectHasGlyph() {
        val languages = TranslationLanguageCatalog.buildSourceLanguageList("en") +
            TranslationLanguageCatalog.buildTargetLanguageList("ru")

        val marked = languages.filter { it.glyph.isNotEmpty() }.map { it.code }.distinct()
        assertEquals(listOf("auto"), marked)
    }

    @Test
    fun languageCapabilities_markTranslationAndOcrLevels() {
        val czech = TranslationLanguageCatalog.findLanguage("cs", Locale.ENGLISH)
        val russian = TranslationLanguageCatalog.findLanguage("ru", Locale.ENGLISH)

        assertNotNull(czech)
        assertNotNull(russian)
        assertTrue(czech!!.capabilities.contains(LanguageCapability.TRANSLATION))
        assertTrue(czech.capabilities.contains(LanguageCapability.BASIC_OCR))
        assertTrue(!czech.capabilities.contains(LanguageCapability.QUALITY_OCR))
        assertTrue(russian!!.capabilities.contains(LanguageCapability.QUALITY_OCR))
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
    fun formatLanguage_printsOneNameWhenBothSpellingsCoincide() {
        val auto = TranslationLanguageCatalog.findLanguage("auto", Locale.GERMAN)

        assertNotNull(auto)
        assertEquals("$GLOBE Auto-detect", TranslationLanguageCatalog.formatLanguage(auto!!))
    }

    @Test
    fun withAutoDetectLabel_replacesOnlyTheAutoRow() {
        val auto = TranslationLanguageCatalog.findLanguage("auto", Locale.ENGLISH)!!
        val czech = TranslationLanguageCatalog.findLanguage("cs", Locale.ENGLISH)!!

        assertEquals(
            "$GLOBE Automatisch",
            TranslationLanguageCatalog.formatLanguage(auto.withAutoDetectLabel("Automatisch")),
        )
        assertEquals(czech, czech.withAutoDetectLabel("Automatisch"))
    }

    @Test
    fun languageCodeToMLKit_keepsCzechCode() {
        assertEquals("cs", TranslationManager.languageCodeToMLKit("cs"))
    }

    private companion object {
        /** The marker of the auto-detect row (ICON-EXTERNAL rule 6 keeps flags off languages, not this glyph). */
        const val GLOBE = "🌐"
    }
}
