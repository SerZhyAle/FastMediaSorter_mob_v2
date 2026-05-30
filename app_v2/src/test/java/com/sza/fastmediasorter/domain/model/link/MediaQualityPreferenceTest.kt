package com.sza.fastmediasorter.domain.model.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MediaQualityPreference.fromSettings] resolution-string mapping.
 */
class MediaQualityPreferenceTest {

    @Test
    fun `known resolution strings map to their pixel ceiling`() {
        assertEquals(480, MediaQualityPreference.fromSettings("480p", audioOnly = false).maxResolutionPx)
        assertEquals(720, MediaQualityPreference.fromSettings("720p", audioOnly = false).maxResolutionPx)
        assertEquals(1080, MediaQualityPreference.fromSettings("1080p", audioOnly = false).maxResolutionPx)
    }

    @Test
    fun `best maps to Int MAX_VALUE`() {
        assertEquals(Int.MAX_VALUE, MediaQualityPreference.fromSettings("best", audioOnly = false).maxResolutionPx)
    }

    @Test
    fun `unknown resolution falls back to 1080`() {
        assertEquals(1080, MediaQualityPreference.fromSettings("4k", audioOnly = false).maxResolutionPx)
        assertEquals(1080, MediaQualityPreference.fromSettings("", audioOnly = false).maxResolutionPx)
    }

    @Test
    fun `audioOnly flag is carried through unchanged`() {
        assertTrue(MediaQualityPreference.fromSettings("720p", audioOnly = true).audioOnly)
        assertFalse(MediaQualityPreference.fromSettings("720p", audioOnly = false).audioOnly)
    }
}
