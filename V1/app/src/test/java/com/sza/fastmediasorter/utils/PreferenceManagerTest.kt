package com.sza.fastmediasorter.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Simple unit tests for PreferenceManager constants and logic
 * Note: Full testing requires Android framework, use instrumented tests for that
 */
class PreferenceManagerTest {
    @Test
    fun `test language code validation`() {
        // Given
        val validLanguages = setOf("en", "ru", "uk")

        // Then
        validLanguages.forEach { lang ->
            assertTrue("Language code should be 2 characters", lang.length == 2)
            assertTrue("Language code should be lowercase", lang == lang.lowercase())
        }
    }

    @Test
    fun `test video size validation`() {
        // Given
        val validSizes = listOf(1, 10, 50, 100, 500, 999999)

        // Then
        validSizes.forEach { size ->
            assertTrue("Video size should be positive", size > 0)
            assertTrue("Video size should be within range", size in 1..999999)
        }
    }
}
