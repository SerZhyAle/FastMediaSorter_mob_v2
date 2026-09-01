package com.sza.fastmediasorter.ui.streams.helpers

import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

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
        // S2314: a country with no custom image flag carries its emoji in the label itself, unlike a
        // language, which has a LanguageItem to hand to Option.flag.
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
}
