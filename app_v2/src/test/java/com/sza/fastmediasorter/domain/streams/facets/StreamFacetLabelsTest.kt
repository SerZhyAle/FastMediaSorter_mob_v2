package com.sza.fastmediasorter.domain.streams.facets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StreamFacetLabelsTest {

    private val russian = Locale.forLanguageTag("ru")

    @Test
    fun `equal interface and own names are written once`() {
        assertEquals("English", StreamFacetLabels.languageLabel("english", Locale.ENGLISH))
    }

    @Test
    fun `different names carry the own name in parentheses`() {
        assertEquals("German (Deutsch)", StreamFacetLabels.languageLabel("german", Locale.ENGLISH))
        val label = StreamFacetLabels.languageLabel("german", russian)
        assertTrue(label, label.startsWith("Немецкий"))
        assertTrue(label, label.endsWith("(Deutsch)"))
    }

    @Test
    fun `an id the vocabulary does not know keeps its English text`() {
        assertEquals("Brazilian portuguese", StreamFacetLabels.languageLabel("brazilian portuguese", russian))
    }

    @Test
    fun `an extension language the platform may not name still has a label`() {
        // The platform may know it under its own name (CLDR says "Morisyen") or not at all; either way the
        // reader never sees the bare code.
        val label = StreamFacetLabels.languageLabel("mauritian creole", Locale.ENGLISH)
        assertTrue(label, label.isNotBlank())
        assertTrue(label, !label.equals("mfe", ignoreCase = true))
    }
}
