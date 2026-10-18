package com.sza.fastmediasorter.data.streams

import com.sza.fastmediasorter.domain.streams.facets.StreamTopicFolding
import com.sza.fastmediasorter.ui.streams.helpers.StreamTopicRubricCatalog
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
    fun `blank stays blank, unknown category stays visible, topic language and country close`() {
        val facets = normalizer.normalize("", "Future topic", "Future language", "Atlantis")

        assertEquals("", facets.category)
        // STREAM-BANK 2.4 topic law: an unknown non-blank topic reads General.
        assertEquals("General", facets.topic)
        // STREAM-BANK 2.3: nothing recognized reads english, and a non-code country is blank.
        assertEquals("english", facets.language)
        assertEquals("", facets.country)

        val blank = normalizer.normalize("", "", "", "")
        assertEquals("", blank.topic)
        assertEquals("", blank.language)
        assertEquals("", blank.country)

        val unknownCategory = normalizer.normalize("Future Category", "", "", "")
        assertEquals("Future Category", unknownCategory.category)
    }

    @Test
    fun `a rubric id in any case folds to the canonical id`() {
        assertEquals("News", normalizer.normalize("", "news", "", "").topic)
        assertEquals("Test", normalizer.normalize("", "test", "", "").topic)
        assertEquals("Jazz & Blues", normalizer.normalize("", "jazz & blues", "", "").topic)
    }

    @Test
    fun `every rubric the fold produces has a localized label`() {
        val unlabelled = StreamTopicFolding.rubricIds.filter { StreamTopicRubricCatalog.labelRes(it) == null }
        assertEquals(emptyList<String>(), unlabelled)
    }

    @Test
    fun `a space-joined list splits and a variety folds into its language`() {
        val facets = normalizer.normalize("Radio", "", "english french german russian slovak spain", "Czech Republic")

        assertEquals("english,french,german,russian,slovak", facets.language)
        assertEquals("CZ", facets.country)
        assertEquals("english", normalizer.normalize("", "", "caribbean english", "").language)
    }
}
