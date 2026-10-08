package com.sza.fastmediasorter.ui.streams.helpers

import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamFacetOptionMapperTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `known category label is localized while unknown category stays raw`() {
        assertEquals(
            context.getString(R.string.streams_category_live_tv),
            StreamCategoryOptionMapper.label(context, "Live TV"),
        )
        assertEquals("Future category", StreamCategoryOptionMapper.label(context, "Future category"))
    }

    @Test
    fun `country uses localized ISO name and raw unknown fallback`() {
        // S2314: a country with no custom image flag carries its emoji in the label itself; RU/BY carry
        // their code in Option.countryFlag instead.
        assertEquals(
            "🇩🇪 Germany",
            StreamCountryOptionMapper.countryOptions(context, listOf("DE")).single().label,
        )
        assertEquals("Atlantis", StreamCountryOptionMapper.countryOptions(context, listOf("Atlantis")).single().label)
    }

    @Test
    fun `language uses localized known label and raw unknown fallback`() {
        assertEquals("English", StreamLanguageOptionMapper.languageOptions(context, listOf("english")).single().label)
        assertEquals(
            "Future language",
            StreamLanguageOptionMapper.languageOptions(context, listOf("future language")).single().label,
        )
    }

    private val ukrainian = Locale.forLanguageTag("uk")
    private val russian = Locale.forLanguageTag("ru")
    private val french = Locale.FRENCH

    @Test
    fun `a vocabulary language outside the translator set resolves and a Russian label carries both names`() {
        val german = StreamLanguageOptionMapper.languageOptions(russian, listOf("german")).single()
        assertTrue(german.label, german.label.startsWith("Немецкий"))
        assertTrue(german.label, german.label.contains("(Deutsch)"))
        assertEquals("german", german.id)
        assertNull(german.countryFlag)

        val papiamento = StreamLanguageOptionMapper.languageOptions(Locale.ENGLISH, listOf("papiamento")).single()
        assertTrue(papiamento.label, papiamento.label.startsWith("Papiamento"))
    }

    @Test
    fun `language options pin english, russian and ukrainian first and sort the rest by label`() {
        val ids = StreamLanguageOptionMapper
            .languageOptions(Locale.ENGLISH, listOf("german", "ukrainian", "afrikaans", "english", "russian"))
            .map { it.id }
        assertEquals(listOf("english", "russian", "ukrainian", "afrikaans", "german"), ids)
    }

    @Test
    fun `country list is alphabetical by name when the interface language is english`() {
        val ids = StreamCountryOptionMapper.countryOptions(Locale.ENGLISH, listOf("UA", "DE", "BE", "FR")).map { it.id }
        assertEquals(listOf("BE", "FR", "DE", "UA"), ids)
    }

    @Test
    fun `ukrainian interface leads with Ukraine and lists it again in the alphabetical list`() {
        val options = StreamCountryOptionMapper.countryOptions(ukrainian, listOf("DE", "UA", "PL"))
        assertEquals(listOf("UA", "DE", "PL", "UA"), options.map { it.id })
        assertNotEquals(options[0].rowKey, options[3].rowKey)
        assertEquals("UA", options[3].rowKey)
    }

    @Test
    fun `french interface leads with the French-speaking countries present in the catalog`() {
        val options = StreamCountryOptionMapper.countryOptions(french, listOf("DE", "FR", "BE", "UA"))
        assertEquals(listOf("BE", "FR", "DE", "BE", "FR", "UA"), options.map { it.id })
    }

    @Test
    fun `a block country absent from the catalog is absent from the block`() {
        assertEquals(listOf("DE"), StreamCountryOptionMapper.countryOptions(ukrainian, listOf("DE")).map { it.id })
    }

    @Test
    fun `RU and BY keep their custom flag`() {
        val options = StreamCountryOptionMapper.countryOptions(Locale.ENGLISH, listOf("RU", "BY"))
        assertEquals(setOf("RU", "BY"), options.mapNotNull { it.countryFlag }.toSet())
    }
}
