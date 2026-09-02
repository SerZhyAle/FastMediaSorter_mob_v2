package com.sza.fastmediasorter.wear.ui.streams.helpers

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * S2146: the language label resolves through the JVM's own locale table, which a unit test has, so
 * this needs no Android runtime - unlike the rubric catalogue, which needs a `Context` to read a
 * string resource and is therefore left to the device pass.
 *
 * The default locale is pinned to English for the duration: `getDisplayLanguage()` answers in the
 * ambient locale, so a machine set to another language would otherwise fail these on a correct
 * implementation.
 */
class WearStreamLanguageLabelsTest {

    private lateinit var original: Locale

    @Before
    fun pinLocale() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun `a known catalogue name resolves through the locale table`() {
        assertEquals("German", WearStreamLanguageLabels.label("german"))
        assertEquals("Ukrainian", WearStreamLanguageLabels.label("ukrainian"))
    }

    @Test
    fun `resolution ignores the catalogue's casing and padding`() {
        assertEquals("French", WearStreamLanguageLabels.label("  FrEnCh  "))
    }

    @Test
    fun `a composite name that no ISO code carries stays its own text, title-cased`() {
        // Strategic §7: `brazilian portuguese` is in no ISO table and must remain selectable rather
        // than vanish from the picker - the phone behaves the same way with the same value.
        assertEquals("Brazilian portuguese", WearStreamLanguageLabels.label("brazilian portuguese"))
    }

    @Test
    fun `an empty value comes back empty rather than throwing`() {
        assertEquals("", WearStreamLanguageLabels.label(""))
        assertEquals("", WearStreamLanguageLabels.label("   "))
    }
}
