package com.sza.fastmediasorter.data.repository.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1703: the engine was withdrawn, but a device that once chose it still carries the word in its settings
 * and an old backup can carry it for years. This is the half of the withdrawal that touches data a user
 * already has, so it is the half that has to hold without a device.
 */
class RetiredOcrEngineValueTest {

    @Test
    fun `the retired engine reads back as the default`() {
        assertEquals(
            TextRecognitionSettingsStore.DEFAULT_ENGINE,
            TextRecognitionSettingsStore.normaliseEngine("PADDLE")
        )
    }

    @Test
    fun `the shipped engine is left alone`() {
        assertEquals(
            TextRecognitionSettingsStore.DEFAULT_ENGINE,
            TextRecognitionSettingsStore.normaliseEngine(TextRecognitionSettingsStore.DEFAULT_ENGINE)
        )
    }

    @Test
    fun `an unknown value reads back as the default`() {
        assertEquals(
            TextRecognitionSettingsStore.DEFAULT_ENGINE,
            TextRecognitionSettingsStore.normaliseEngine("SOMETHING_ELSE")
        )
    }

    @Test
    fun `an absent value reads back as the default`() {
        assertEquals(
            TextRecognitionSettingsStore.DEFAULT_ENGINE,
            TextRecognitionSettingsStore.normaliseEngine(null)
        )
    }

    @Test
    fun `normalising twice is the same as normalising once`() {
        val once = TextRecognitionSettingsStore.normaliseEngine("PADDLE")

        assertEquals(once, TextRecognitionSettingsStore.normaliseEngine(once))
    }
}
