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
    }

    @Test
    fun `blank and unknown values are preserved without inventing a filter id`() {
        val facets = normalizer.normalize("", "Future topic", "Future language", "Atlantis")

        assertEquals("", facets.category)
        assertEquals("Future topic", facets.topic)
        assertEquals("future language", facets.language)
        assertEquals("Atlantis", facets.country)
    }
}
