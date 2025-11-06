package com.sza.fastmediasorter.utils

import org.junit.Assert.*
import org.junit.Test

class MediaUtilsTest {
    @Test
    fun `test isImageFile recognizes common image formats`() {
        // Given
        val imageFiles =
            listOf(
                "photo.jpg",
                "image.jpeg",
                "picture.png",
                "graphic.gif",
                "bitmap.bmp",
                "vector.webp",
                // uppercase
                "PHOTO.JPG",
                // mixed case
                "Image.JPEG",
            )

        // When & Then
        imageFiles.forEach { filename ->
            assertTrue("$filename should be recognized as image", MediaUtils.isImageFile(filename))
        }
    }

    @Test
    fun `test isImageFile rejects non-image formats`() {
        // Given
        val nonImageFiles =
            listOf(
                "video.mp4",
                "audio.mp3",
                "document.pdf",
                "text.txt",
                "app.apk",
                // no extension
                "photo",
                "image.doc",
            )

        // When & Then
        nonImageFiles.forEach { filename ->
            assertFalse("$filename should not be recognized as image", MediaUtils.isImageFile(filename))
        }
    }

    @Test
    fun `test isVideoFile recognizes common video formats`() {
        // Given
        val videoFiles =
            listOf(
                "movie.mp4",
                "video.mkv",
                "stream.mov",
                "web.webm",
                "mobile.3gp",
                // uppercase
                "MOVIE.MP4",
                // mixed case
                "Video.MKV",
            )

        // When & Then
        videoFiles.forEach { filename ->
            assertTrue("$filename should be recognized as video", MediaUtils.isVideoFile(filename))
        }
    }

    @Test
    fun `test isVideoFile rejects non-video formats`() {
        // Given
        val nonVideoFiles =
            listOf(
                "photo.jpg",
                "audio.mp3",
                "document.pdf",
                "text.txt",
                // no extension
                "movie",
                "video.doc",
            )

        // When & Then
        nonVideoFiles.forEach { filename ->
            assertFalse("$filename should not be recognized as video", MediaUtils.isVideoFile(filename))
        }
    }

    @Test
    fun `test formatFileSize with different sizes`() {
        // Given & When & Then
        assertEquals("0 B", MediaUtils.formatFileSize(0))
        assertEquals("512 B", MediaUtils.formatFileSize(512))

        // 1 KB = 1024 bytes
        val oneKB = MediaUtils.formatFileSize(1024)
        assertTrue("Should be 1 KB or 1.0 KB", oneKB == "1 KB" || oneKB == "1.0 KB")

        // 1.5 KB
        val oneAndHalfKB = MediaUtils.formatFileSize(1536)
        assertTrue("Should contain KB", oneAndHalfKB.contains("KB"))

        // 1 MB
        val oneMB = MediaUtils.formatFileSize(1024 * 1024)
        assertTrue("Should be 1 MB or 1.0 MB", oneMB == "1 MB" || oneMB == "1.0 MB")

        // 2.5 MB
        val twoAndHalfMB = MediaUtils.formatFileSize((2.5 * 1024 * 1024).toLong())
        assertTrue("Should contain MB", twoAndHalfMB.contains("MB"))

        // 1 GB
        val oneGB = MediaUtils.formatFileSize(1024L * 1024L * 1024L)
        assertTrue("Should contain GB", oneGB.contains("GB"))
    }

    @Test
    fun `test getFileExtension returns correct extension`() {
        // Given & When & Then
        assertEquals("jpg", MediaUtils.getFileExtension("photo.jpg"))
        assertEquals("jpeg", MediaUtils.getFileExtension("image.jpeg"))
        assertEquals("", MediaUtils.getFileExtension("noextension"))
        assertEquals("", MediaUtils.getFileExtension(""))
        assertEquals("ext", MediaUtils.getFileExtension(".ext"))
        assertEquals("final", MediaUtils.getFileExtension("file.name.final"))
    }

    @Test
    fun `test getFileExtension handles case sensitivity`() {
        // Given & When & Then
        assertEquals("JPG", MediaUtils.getFileExtension("PHOTO.JPG"))
        assertEquals("Mp4", MediaUtils.getFileExtension("video.Mp4"))
        assertEquals("PNG", MediaUtils.getFileExtension("Image.PNG"))
    }
}
