package com.sza.fastmediasorter.data.common

import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaTypeUtilsTest {

    // ──────────────────────────────────────────────────────────────────────
    // getMediaType (extension-based)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `getMediaType returns AUDIO for flac extension`() {
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaType("track.flac"))
    }

    @Test
    fun `getMediaType returns AUDIO for mp3 extension`() {
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaType("song.mp3"))
    }

    @Test
    fun `getMediaType returns AUDIO for ogg extension`() {
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaType("audio.ogg"))
    }

    @Test
    fun `getMediaType is case-insensitive`() {
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaType("track.FLAC"))
        assertEquals(MediaType.VIDEO, MediaTypeUtils.getMediaType("clip.MKV"))
        assertEquals(MediaType.IMAGE, MediaTypeUtils.getMediaType("photo.JPG"))
    }

    @Test
    fun `getMediaType returns null for unknown extension`() {
        assertNull(MediaTypeUtils.getMediaType("file.xyz123"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // getMediaTypeFromMime
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `getMediaTypeFromMime returns AUDIO for audio-flac`() {
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaTypeFromMime("audio/flac"))
    }

    @Test
    fun `getMediaTypeFromMime returns AUDIO for audio-mpeg`() {
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaTypeFromMime("audio/mpeg"))
    }

    @Test
    fun `getMediaTypeFromMime returns VIDEO for video-mp4`() {
        assertEquals(MediaType.VIDEO, MediaTypeUtils.getMediaTypeFromMime("video/mp4"))
    }

    @Test
    fun `getMediaTypeFromMime returns IMAGE for image-jpeg`() {
        assertEquals(MediaType.IMAGE, MediaTypeUtils.getMediaTypeFromMime("image/jpeg"))
    }

    @Test
    fun `getMediaTypeFromMime returns GIF for image-gif`() {
        assertEquals(MediaType.GIF, MediaTypeUtils.getMediaTypeFromMime("image/gif"))
    }

    @Test
    fun `getMediaTypeFromMime returns null for null input`() {
        assertNull(MediaTypeUtils.getMediaTypeFromMime(null))
    }

    @Test
    fun `getMediaTypeFromMime returns null for non-standard flac MIME`() {
        // application/x-flac is a known non-standard MIME that SAF providers may return
        assertNull(MediaTypeUtils.getMediaTypeFromMime("application/x-flac"))
    }

    @Test
    fun `getMediaTypeFromMime has no duplicate video branch - video-webm resolves correctly`() {
        assertEquals(MediaType.VIDEO, MediaTypeUtils.getMediaTypeFromMime("video/webm"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // getMediaTypeFromMimeOrExtension  ← main target of this change
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `getMediaTypeFromMimeOrExtension uses MIME when valid`() {
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaTypeFromMimeOrExtension("audio/flac", "track.flac"))
    }

    @Test
    fun `getMediaTypeFromMimeOrExtension falls back to extension when MIME is null`() {
        // This is the primary bug fix scenario: SAF returns null MIME for FLAC
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaTypeFromMimeOrExtension(null, "track.flac"))
    }

    @Test
    fun `getMediaTypeFromMimeOrExtension falls back to extension when MIME is non-standard`() {
        // application/x-flac is not recognised by getMediaTypeFromMime → extension wins
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaTypeFromMimeOrExtension("application/x-flac", "track.flac"))
    }

    @Test
    fun `getMediaTypeFromMimeOrExtension falls back for mp3 with null MIME`() {
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaTypeFromMimeOrExtension(null, "song.mp3"))
    }

    @Test
    fun `getMediaTypeFromMimeOrExtension falls back for ogg with null MIME`() {
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaTypeFromMimeOrExtension(null, "audio.ogg"))
    }

    @Test
    fun `getMediaTypeFromMimeOrExtension returns null when both MIME and extension are unrecognised`() {
        assertNull(MediaTypeUtils.getMediaTypeFromMimeOrExtension(null, "file.xyz123"))
    }

    @Test
    fun `getMediaTypeFromMimeOrExtension MIME takes priority over extension when both are valid`() {
        // MIME says audio, extension says nothing recognisable → MIME wins
        assertEquals(MediaType.AUDIO, MediaTypeUtils.getMediaTypeFromMimeOrExtension("audio/ogg", "file.unknown"))
    }

    @Test
    fun `getMediaTypeFromMimeOrExtension works for video`() {
        assertEquals(MediaType.VIDEO, MediaTypeUtils.getMediaTypeFromMimeOrExtension(null, "clip.mkv"))
    }

    @Test
    fun `getMediaTypeFromMimeOrExtension works for image`() {
        assertEquals(MediaType.IMAGE, MediaTypeUtils.getMediaTypeFromMimeOrExtension(null, "photo.jpg"))
    }
}
