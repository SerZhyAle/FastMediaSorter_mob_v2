package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [applyProfile] extension - the only transformation logic around [ResourceFormData].
 */
class ResourceFormDataTest {

    @Test
    fun `NONE profile returns the same instance unchanged`() {
        val data = ResourceFormData(name = "x", allFiles = true)
        assertSame(data, data.applyProfile(ResourceProfile.NONE))
    }

    @Test
    fun `AUDIO_LIBRARY sets audio-only media types and remembers the list`() {
        val result = ResourceFormData().applyProfile(ResourceProfile.AUDIO_LIBRARY)
        assertEquals(ResourceProfile.AUDIO_LIBRARY, result.profile)
        assertEquals(setOf(MediaType.AUDIO), result.supportedMediaTypes)
        assertFalse(result.allFiles)
        assertTrue(result.rememberFileList)
    }

    @Test
    fun `VIDEO_LIBRARY enables video and audio`() {
        val result = ResourceFormData().applyProfile(ResourceProfile.VIDEO_LIBRARY)
        assertEquals(ResourceProfile.VIDEO_LIBRARY, result.profile)
        assertEquals(setOf(MediaType.VIDEO, MediaType.AUDIO), result.supportedMediaTypes)
        assertFalse(result.allFiles)
    }

    @Test
    fun `PHOTO_STORAGE enables image and gif`() {
        val result = ResourceFormData().applyProfile(ResourceProfile.PHOTO_STORAGE)
        assertEquals(setOf(MediaType.IMAGE, MediaType.GIF), result.supportedMediaTypes)
        assertFalse(result.allFiles)
    }

    @Test
    fun `DOCUMENTS enables the full document family`() {
        val result = ResourceFormData().applyProfile(ResourceProfile.DOCUMENTS)
        assertEquals(
            setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT),
            result.supportedMediaTypes,
        )
        assertFalse(result.allFiles)
    }

    @Test
    fun `ALL_FILES flips allFiles on and records the profile`() {
        val result = ResourceFormData(allFiles = false).applyProfile(ResourceProfile.ALL_FILES)
        assertTrue(result.allFiles)
        assertEquals(ResourceProfile.ALL_FILES, result.profile)
    }

    @Test
    fun `applying a profile preserves unrelated fields`() {
        val result = ResourceFormData(name = "Library", host = "10.0.0.1")
            .applyProfile(ResourceProfile.AUDIO_LIBRARY)
        assertEquals("Library", result.name)
        assertEquals("10.0.0.1", result.host)
    }
}
