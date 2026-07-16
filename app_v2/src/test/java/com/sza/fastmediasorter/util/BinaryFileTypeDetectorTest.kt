package com.sza.fastmediasorter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1058 - the archive/disk MIME table must never emit a coined `application/<ext>` type.
 *
 * Plain JUnit on purpose: [BinaryFileTypeDetector] carries no `android.*` dependency, so the table
 * is assertable without Robolectric. The platform-map rung that [BinaryFileTypeDetector] feeds into
 * is deliberately NOT covered here - `MimeTypeMap` answers per firmware, and Robolectric's
 * `ShadowMimeTypeMap` starts empty and is filled by the test itself, so it would only ever confirm
 * what the test just told it.
 */
class BinaryFileTypeDetectorTest {

    @Test
    fun `registry mime types are pinned exactly`() {
        val expected = mapOf(
            "zip" to "application/zip",
            "rar" to "application/vnd.rar",
            "7z" to "application/x-7z-compressed",
            "tar" to "application/x-tar",
            "gz" to "application/gzip",
            "bz2" to "application/x-bzip2",
            "xz" to "application/x-xz",
            "cab" to "application/vnd.ms-cab-compressed",
            "iso" to "application/x-iso9660-image",
            "dmg" to "application/x-apple-diskimage",
        )

        expected.forEach { (extension, mime) ->
            assertEquals("mime for .$extension", mime, BinaryFileTypeDetector.mimeTypeForExtension(extension))
        }
    }

    @Test
    fun `lookup is case insensitive`() {
        assertEquals("application/x-7z-compressed", BinaryFileTypeDetector.mimeTypeForExtension("7Z"))
        assertEquals("application/x-iso9660-image", BinaryFileTypeDetector.mimeTypeForExtension("ISO"))
    }

    @Test
    fun `extensions with no registry type resolve to null rather than a coined type`() {
        BinaryFileTypeDetector.NO_REGISTRY_MIME.forEach { extension ->
            assertNull(
                "'$extension' has no registry type and must fall through to the caller",
                BinaryFileTypeDetector.mimeTypeForExtension(extension)
            )
        }
    }

    @Test
    fun `unknown extension resolves to null`() {
        assertNull(BinaryFileTypeDetector.mimeTypeForExtension("definitely-not-an-archive"))
        assertNull(BinaryFileTypeDetector.mimeTypeForExtension(""))
    }

    /**
     * The regression net. Adding an extension to ARCHIVES/DISK_IMAGES without deciding its MIME is
     * exactly how S1058 shipped: the type was derived from the extension by template. This forces
     * the decision to be made in one of the two sets rather than defaulted silently.
     */
    @Test
    fun `every archive and disk extension has a decided mime outcome`() {
        val classified = BinaryFileTypeDetector.ARCHIVES + BinaryFileTypeDetector.DISK_IMAGES
        val decided = BinaryFileTypeDetector.MIME_BY_EXTENSION.keys + BinaryFileTypeDetector.NO_REGISTRY_MIME

        assertEquals(
            "extension classified as archive/disk but with no MIME decision - add it to " +
                "MIME_BY_EXTENSION (registry type exists) or NO_REGISTRY_MIME (none does)",
            emptySet<String>(),
            classified - decided
        )
        assertEquals(
            "MIME decision for an extension that is no longer classified as archive/disk",
            emptySet<String>(),
            decided - classified
        )
    }

    // Deliberately no "entry must not equal application/<ext>" test: `zip` -> `application/zip` is
    // the real IANA type and matches that shape anyway, so the check cannot separate "coined by
    // template" from "template happens to be right". Whether a type is coined is decided against a
    // registry, which is what the pinning test above does - the sources are cited in S1058 section 2.3.

    @Test
    fun `table only covers extensions the detector routes through it`() {
        BinaryFileTypeDetector.MIME_BY_EXTENSION.keys.forEach { extension ->
            val type = BinaryFileTypeDetector.detectType(extension)
            assertTrue(
                "'$extension' maps to $type, which does not use the archive/disk MIME table",
                type == com.sza.fastmediasorter.domain.model.MediaType.BINARY_ARCHIVE ||
                    type == com.sza.fastmediasorter.domain.model.MediaType.BINARY_DISK
            )
        }
    }
}
