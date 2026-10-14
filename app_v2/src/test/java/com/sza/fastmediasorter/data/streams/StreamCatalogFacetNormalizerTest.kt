package com.sza.fastmediasorter.data.streams

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCatalogFacetNormalizerTest {

    private val normalizer = StreamCatalogFacetNormalizer()

    @Test
    fun `known aliases normalize to stable identifiers`() {
        val facets = normalizer.normalize(
            category = "Radio (SomaFM)",
            topic = "Adult Contemporary",
            language = "American English, Gernan",
            country = "Germany",
        )

        assertEquals("Radio", facets.category)
        assertEquals("Pop", facets.topic)
        assertEquals("english,german", facets.language)
        assertEquals("DE", facets.country)

        val webcamFacets = normalizer.normalize(
            category = "webcams",
            topic = "Webcam",
            language = "Portuguese Brazil",
            country = "USA",
        )
        assertEquals("Webcam", webcamFacets.category)
        assertEquals("Webcam", webcamFacets.topic)
        assertEquals("portuguese", webcamFacets.language)
        assertEquals("US", webcamFacets.country)
    }

    @Test
    fun `blank stays blank, unknown category and topic stay visible, language and country close`() {
        val facets = normalizer.normalize("", "Future topic", "Future language", "Atlantis")

        assertEquals("", facets.category)
        assertEquals("Future topic", facets.topic)
        // STREAM-BANK 2.3: nothing recognized reads english, and a non-code country is blank.
        assertEquals("english", facets.language)
        assertEquals("", facets.country)

        val blank = normalizer.normalize("", "", "", "")
        assertEquals("", blank.language)
        assertEquals("", blank.country)

        val unknownCategory = normalizer.normalize("Future Category", "", "", "")
        assertEquals("Future Category", unknownCategory.category)
    }

    @Test
    fun `a space-joined list splits and a variety folds into its language`() {
        val facets = normalizer.normalize("Radio", "", "english french german russian slovak spain", "Czech Republic")

        assertEquals("english,french,german,russian,slovak", facets.language)
        assertEquals("CZ", facets.country)
        assertEquals("english", normalizer.normalize("", "", "caribbean english", "").language)
    }
}
