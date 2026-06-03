package com.sza.fastmediasorter.domain.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationLanguageCodeMapperTest {

    @Test
    fun languageCodeToMLKit_mapsAutoToEnglishPivot() {
        assertEquals(
            TranslateLanguage.ENGLISH,
            TranslationLanguageCodeMapper.languageCodeToMLKit("auto")
        )
    }

    @Test
    fun languageCodeToMLKit_keepsSupportedTargetCode() {
        assertEquals("cs", TranslationLanguageCodeMapper.languageCodeToMLKit("CS"))
    }

    @Test
    fun languageCodeToMLKit_fallsBackToEnglishForUnknownCode() {
        assertEquals(
            TranslateLanguage.ENGLISH,
            TranslationLanguageCodeMapper.languageCodeToMLKit("zz")
        )
    }

    @Test
    fun supportedCodes_includeFullMlKitTranslationCatalog() {
        assertEquals(59, TranslationLanguageCodeMapper.supportedCodes.size)
        assertTrue("cs" in TranslationLanguageCodeMapper.supportedCodes)
        assertTrue("mt" in TranslationLanguageCodeMapper.supportedCodes)
    }
}
