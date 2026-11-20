package com.sza.fastmediasorter.ui.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * S4708: the interface-language picker lists languages alphabetically by the name it shows first, and
 * a query matches the English name whatever language the app displays. Both are pure functions, so
 * the test needs no Android runtime.
 */
class UiLanguagePickerItemsTest {

    private val declaredTags = listOf(
        "en", "zh-Hans", "hi", "es", "fr", "ar", "bn", "pt", "ru", "ur", "uk", "de", "it"
    )

    @Test
    fun `languages are sorted by their English name when the app is English`() {
        val codes = UiLanguagePickerItems.sortedLanguageItems(declaredTags, Locale.ENGLISH).map { it.code }

        assertEquals(
            listOf("ar", "bn", "zh-Hans", "en", "fr", "de", "hi", "it", "pt", "ru", "es", "uk", "ur"),
            codes
        )
    }

    @Test
    fun `sorting does not reorder or drop declared languages`() {
        val codes = UiLanguagePickerItems.sortedLanguageItems(declaredTags, Locale.GERMAN).map { it.code }

        assertEquals(declaredTags.sorted(), codes.sorted())
    }

    @Test
    fun `English name matches while the app displays another language`() {
        val russian = UiLanguagePickerItems.item("ru", Locale.GERMAN)

        val keys = languageSearchKeys(russian, Locale.GERMAN)

        assertTrue("russian must be searchable by its English name", keys.any { it.startsWith("rus") })
        assertTrue("the endonym must stay searchable", keys.contains("русский"))
        assertTrue("the code must stay searchable", keys.contains("ru"))
    }
}
