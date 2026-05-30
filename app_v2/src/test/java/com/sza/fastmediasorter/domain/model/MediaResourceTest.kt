package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.testing.createMediaResource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the computed capability predicates on [MediaResource].
 */
class MediaResourceTest {

    @Test
    fun `isAudioOnly is true only for a single AUDIO type without allFiles`() {
        assertTrue(createMediaResource(supportedMediaTypes = setOf(MediaType.AUDIO)).isAudioOnly())
        assertFalse(
            createMediaResource(supportedMediaTypes = setOf(MediaType.AUDIO, MediaType.VIDEO)).isAudioOnly(),
        )
        assertFalse(
            createMediaResource(supportedMediaTypes = setOf(MediaType.AUDIO), allFiles = true).isAudioOnly(),
        )
    }

    @Test
    fun `isOnlyImage is true only for a single IMAGE type without allFiles`() {
        assertTrue(createMediaResource(supportedMediaTypes = setOf(MediaType.IMAGE)).isOnlyImage())
        assertFalse(
            createMediaResource(supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.GIF)).isOnlyImage(),
        )
        assertFalse(
            createMediaResource(supportedMediaTypes = setOf(MediaType.IMAGE), allFiles = true).isOnlyImage(),
        )
    }

    @Test
    fun `isVideoOnly is true only for a single VIDEO type without allFiles`() {
        assertTrue(createMediaResource(supportedMediaTypes = setOf(MediaType.VIDEO)).isVideoOnly())
        assertFalse(createMediaResource(supportedMediaTypes = setOf(MediaType.AUDIO)).isVideoOnly())
        assertFalse(
            createMediaResource(supportedMediaTypes = setOf(MediaType.VIDEO), allFiles = true).isVideoOnly(),
        )
    }

    @Test
    fun `supportsDocuments is true when allFiles is on`() {
        assertTrue(
            createMediaResource(supportedMediaTypes = setOf(MediaType.IMAGE), allFiles = true).supportsDocuments(),
        )
    }

    @Test
    fun `supportsDocuments tracks the presence of any document type`() {
        assertTrue(createMediaResource(supportedMediaTypes = setOf(MediaType.PDF)).supportsDocuments())
        assertTrue(createMediaResource(supportedMediaTypes = setOf(MediaType.EPUB)).supportsDocuments())
        assertTrue(createMediaResource(supportedMediaTypes = setOf(MediaType.OFFICE_DOCUMENT)).supportsDocuments())
        assertFalse(
            createMediaResource(supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO)).supportsDocuments(),
        )
    }

    @Test
    fun `supportsImages is true with allFiles or an explicit IMAGE type`() {
        assertTrue(createMediaResource(supportedMediaTypes = setOf(MediaType.IMAGE)).supportsImages())
        assertTrue(
            createMediaResource(supportedMediaTypes = setOf(MediaType.VIDEO), allFiles = true).supportsImages(),
        )
        assertFalse(createMediaResource(supportedMediaTypes = setOf(MediaType.VIDEO)).supportsImages())
    }
}
