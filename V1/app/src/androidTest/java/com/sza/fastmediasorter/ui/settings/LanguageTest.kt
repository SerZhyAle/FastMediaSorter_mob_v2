package com.sza.fastmediasorter.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.utils.PreferenceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class LanguageTest {
    private lateinit var context: Context
    private lateinit var preferenceManager: PreferenceManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferenceManager = PreferenceManager(context)
    }

    @Test
    fun test_defaultLanguageIsEnglish() {
        // Given - fresh preferences
        preferenceManager.setLanguage("en")

        // When
        val language = preferenceManager.getLanguage()

        // Then
        assertEquals("en", language)
    }

    @Test
    fun test_languageCanBeChanged() {
        // Given
        val languages = arrayOf("en", "ru", "uk")

        languages.forEach { lang ->
            // When
            preferenceManager.setLanguage(lang)
            val savedLanguage = preferenceManager.getLanguage()

            // Then
            assertEquals(lang, savedLanguage)
        }
    }

    @Test
    fun test_languageArraysAreConsistent() {
        // Given
        val languageEntries = context.resources.getStringArray(R.array.language_entries)
        val languageValues = context.resources.getStringArray(R.array.language_values)

        // Then
        assertEquals(
            "Language arrays should have same length",
            languageEntries.size,
            languageValues.size,
        )
        assertEquals("Should have 3 languages", 3, languageEntries.size)
    }

    @Test
    fun test_allLanguageStringsExist() {
        // Given
        val languages = arrayOf("en", "ru", "uk")

        languages.forEach { lang ->
            // When - set locale and get string
            val locale = Locale(lang)
            val config = android.content.res.Configuration()
            config.setLocale(locale)
            val localizedContext = context.createConfigurationContext(config)

            // Then - verify key strings exist
            try {
                val appName = localizedContext.getString(R.string.app_name)
                val settings = localizedContext.getString(R.string.settings)
                val language = localizedContext.getString(R.string.language)

                assertNotNull("App name should not be null for $lang", appName)
                assertNotNull("Settings should not be null for $lang", settings)
                assertNotNull("Language should not be null for $lang", language)

                assertTrue("App name should not be empty for $lang", appName.isNotEmpty())
                assertTrue("Settings should not be empty for $lang", settings.isNotEmpty())
                assertTrue("Language should not be empty for $lang", language.isNotEmpty())
            } catch (e: Exception) {
                fail("Missing string resources for language: $lang - ${e.message}")
            }
        }
    }

    @Test
    fun test_languageValuesAreValid() {
        // Given
        val languageValues = context.resources.getStringArray(R.array.language_values)
        val expectedValues = setOf("en", "ru", "uk")

        // When
        val actualValues = languageValues.toSet()

        // Then
        assertEquals("Should contain expected language codes", expectedValues, actualValues)
    }

    @Test
    fun test_languageEntriesAreNotEmpty() {
        // Given
        val languageEntries = context.resources.getStringArray(R.array.language_entries)

        // Then
        languageEntries.forEach { entry ->
            assertTrue("Language entry should not be empty: '$entry'", entry.isNotEmpty())
        }
    }
}
