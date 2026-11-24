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

    @Test
    fun `dialog width keeps narrow portrait screen and caps wide landscape`() {
        assertEquals(720, computeLanguagePickerDialogWidthPx(screenWidthPx = 720, density = 2f))
        assertEquals(1160, computeLanguagePickerDialogWidthPx(screenWidthPx = 1280, density = 2f))
    }

    @Test
    fun `dialog height cap shrinks to available height above keyboard in both orientations`() {
        val portraitWithIme = computeLanguagePickerMaxHeightPx(
            visibleHeightPx = 612,
            verticalWindowInsetsPx = 96,
            maxScrollCapPx = 1000,
        )
        val landscapeWithIme = computeLanguagePickerMaxHeightPx(
            visibleHeightPx = 268,
            verticalWindowInsetsPx = 40,
            maxScrollCapPx = 1000,
        )
        val portraitNoIme = computeLanguagePickerMaxHeightPx(
            visibleHeightPx = 1136,
            verticalWindowInsetsPx = 96,
            maxScrollCapPx = 1000,
        )

        assertEquals(516, portraitWithIme)
        assertEquals(228, landscapeWithIme)
        assertEquals(1000, portraitNoIme)
    }
}
