package com.sza.fastmediasorter.ui.streams.helpers

import com.sza.fastmediasorter.domain.streams.facets.StreamFacetVocabulary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class InterfaceLanguageCountriesTest {

    @Test
    fun `every code in every block is an assigned country`() {
        val assigned = StreamFacetVocabulary.assignedCountries
        val unknown = InterfaceLanguageCountries.languagesWithBlock
            .flatMap { InterfaceLanguageCountries.forLocale(Locale.forLanguageTag(it)) }
            .filterNot { it in assigned }
        assertEquals(emptyList<String>(), unknown)
    }

    @Test
    fun `english and an unlisted language get no block`() {
        assertTrue(InterfaceLanguageCountries.forLocale(Locale.ENGLISH).isEmpty())
        assertTrue(InterfaceLanguageCountries.forLocale(Locale.forLanguageTag("sw")).isEmpty())
        assertTrue("en" !in InterfaceLanguageCountries.languagesWithBlock)
    }

    @Test
    fun `ukrainian leads with Ukraine alone`() {
        assertEquals(setOf("UA"), InterfaceLanguageCountries.forLocale(Locale.forLanguageTag("uk")))
    }

    @Test
    fun `french holds France, Canada, Belgium, Switzerland and the African states`() {
        val french = InterfaceLanguageCountries.forLocale(Locale.FRENCH)
        assertTrue(french.containsAll(listOf("FR", "CA", "BE", "CH", "SN", "CI", "CD", "MG")))
        assertTrue("UA" !in french)
    }

    @Test
    fun `a script-qualified tag resolves by its language`() {
        assertEquals(
            InterfaceLanguageCountries.forLocale(Locale.forLanguageTag("zh")),
            InterfaceLanguageCountries.forLocale(Locale.forLanguageTag("zh-Hans")),
        )
        assertTrue("CN" in InterfaceLanguageCountries.forLocale(Locale.forLanguageTag("zh-Hans")))
    }

    @Test
    fun `all thirteen declared interface languages are covered or deliberately English`() {
        val declared = listOf("en", "zh", "hi", "es", "fr", "ar", "bn", "pt", "ru", "ur", "uk", "de", "it")
        val withoutBlock = declared.filter { InterfaceLanguageCountries.forLocale(Locale.forLanguageTag(it)).isEmpty() }
        assertEquals(listOf("en"), withoutBlock)
    }
}
