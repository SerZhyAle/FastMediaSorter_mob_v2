package com.sza.fastmediasorter.data.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCategoryClassifierTest {

    private val table = mapOf(
        MediaCategory.IMAGE to listOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif", "svg", "tiff", "ico", "wmf", "emf",
        ),
        MediaCategory.VIDEO to listOf(
            "mp4", "mkv", "mov", "webm", "3gp", "flv", "wmv", "m4v", "avi", "mpg", "mpeg", "ts", "m2ts", "vob",
            "ogv", "divx", "m2v", "mts", "3g2", "asf",
        ),
        MediaCategory.AUDIO to listOf(
            "mp3", "m4a", "flac", "aac", "ogg", "wma", "opus", "amr", "awb", "ac3", "ec3", "ac4", "adts", "thd",
            "mka", "oga", "caf", "alac", "mia", "mid", "midi", "wav", "wave",
        ),
        MediaCategory.BOOK to listOf("epub", "pdf", "cbz", "cbr", "fb2", "mobi"),
        MediaCategory.STREAM to listOf("m3u", "m3u8", "pls", "xspf", "fmsbcast"),
        MediaCategory.CONTAINER to listOf(
            "fd-sec", "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "cab", "arj", "lzh", "iso", "dmg", "img",
            "vhd", "vdi", "qcow2", "vmdk",
        ),
        MediaCategory.DOCUMENT to listOf(
            "doc", "docx", "rtf", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "txt", "md", "json", "xml",
            "csv", "tsv", "yaml", "yml", "toml", "ini", "conf",
        ),
    )

    @Test
    fun `every extension of the 0_10 table resolves to its category in any case`() {
        table.forEach { (category, extensions) ->
            extensions.forEach { ext ->
                assertEquals(ext, category, MediaCategoryClassifier.classify("file.$ext"))
                assertEquals(ext, category, MediaCategoryClassifier.classify("FILE.${ext.uppercase()}"))
            }
        }
    }

    @Test
    fun `the classifier table holds exactly the contract table`() {
        val expected = table.flatMap { (category, extensions) -> extensions.map { it to category } }.toMap() +
            ("m4b" to MediaCategory.AUDIO)
        assertEquals(expected, MediaCategoryClassifier.canonicalExtensions)
    }

    @Test
    fun `m4b defaults to AUDIO and an unlisted extension is UNKNOWN`() {
        assertEquals(MediaCategory.AUDIO, MediaCategoryClassifier.classify("story.m4b"))
        assertEquals(MediaCategory.UNKNOWN, MediaCategoryClassifier.classify("Main.kt"))
        assertEquals(MediaCategory.UNKNOWN, MediaCategoryClassifier.classify("README"))
    }

    @Test
    fun `trailing dots spaces and temporary suffixes are stripped`() {
        assertEquals(MediaCategory.VIDEO, MediaCategoryClassifier.classify("clip.mp4. "))
        assertEquals(MediaCategory.VIDEO, MediaCategoryClassifier.classify("clip.mp4.download"))
        assertEquals(MediaCategory.IMAGE, MediaCategoryClassifier.classify("photo.JPG.temp_copy"))
        assertEquals("clip.mp4", MediaCategoryClassifier.normalizeName("Clip.MP4.download.temp_copy"))
    }

    @Test
    fun `system junk receives no category`() {
        listOf(
            "Thumbs.db", "DESKTOP.INI", ".DS_Store", ".git", ".hidden.jpg", "draft.tmp", "edit.swp", "doc.~1",
            "folder/Thumbs.db",
        ).forEach {
            assertTrue(it, MediaCategoryClassifier.isSystemJunk(it))
            assertNull(it, MediaCategoryClassifier.classify(it))
            assertNull(it, MediaCategoryClassifier.classify("image/jpeg", it))
        }
    }

    @Test
    fun `dot-files can be left to the caller while named junk stays excluded`() {
        assertFalse(MediaCategoryClassifier.isSystemJunk(".cover.jpg", includeDotFiles = false))
        assertTrue(MediaCategoryClassifier.isSystemJunk(".DS_Store", includeDotFiles = false))
        assertTrue(MediaCategoryClassifier.isSystemJunk("page.tmp", includeDotFiles = false))
    }

    @Test
    fun `a valid MIME takes precedence over the extension`() {
        assertEquals(MediaCategory.AUDIO, MediaCategoryClassifier.classify("audio/ogg", "track.bin"))
        assertEquals(MediaCategory.STREAM, MediaCategoryClassifier.classify("audio/x-mpegurl", "list.txt"))
        assertEquals(MediaCategory.BOOK, MediaCategoryClassifier.classify("application/pdf", "scan.bin"))
        assertEquals(MediaCategory.CONTAINER, MediaCategoryClassifier.classify("application/zip", "pack.bin"))
        assertEquals(MediaCategory.DOCUMENT, MediaCategoryClassifier.classify("text/plain; charset=utf-8", "a.bin"))
    }

    @Test
    fun `a null generic or unmapped MIME falls back to the extension`() {
        assertEquals(MediaCategory.VIDEO, MediaCategoryClassifier.classify(null, "clip.mkv"))
        assertEquals(MediaCategory.CONTAINER, MediaCategoryClassifier.classify("application/octet-stream", "a.fd-sec"))
        assertEquals(MediaCategory.BOOK, MediaCategoryClassifier.classify("application/x-unknown", "comic.cbz"))
    }

    @Test
    fun `every extension the gallery lists keeps its contract category`() {
        val gallery = mapOf(
            MediaCategory.IMAGE to MediaTypeUtils.IMAGE_EXTENSIONS + MediaTypeUtils.GIF_EXTENSIONS,
            MediaCategory.VIDEO to MediaTypeUtils.VIDEO_EXTENSIONS,
            MediaCategory.AUDIO to MediaTypeUtils.AUDIO_EXTENSIONS,
            MediaCategory.BOOK to MediaTypeUtils.PDF_EXTENSIONS + MediaTypeUtils.EPUB_EXTENSIONS,
        )
        gallery.forEach { (category, extensions) ->
            extensions.forEach { ext -> assertEquals(ext, category, MediaCategoryClassifier.classify("f.$ext")) }
        }
    }
}
