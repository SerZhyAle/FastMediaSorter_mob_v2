package com.sza.fastmediasorter.ui.player

import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets

interface PhotoSphereMetadataReader {
    fun read(path: String): ExifPhotoSphereReader.PhotoSphereMetadata?
}

/**
 * Reads Google Photo Sphere / GPano XMP metadata from local image files.
 *
 * We intentionally scan the embedded XMP text directly instead of relying on
 * ExifInterface XMP helpers because support varies across image formats and library versions.
 */
class ExifPhotoSphereReader : PhotoSphereMetadataReader {

    data class PhotoSphereMetadata(
        val isEquirectangular: Boolean,
        val fullPanoWidthPx: Int? = null,
        val fullPanoHeightPx: Int? = null,
        val croppedAreaWidthPx: Int? = null,
        val croppedAreaHeightPx: Int? = null,
    ) {
        fun is180Projection(): Boolean {
            val fullWidth = fullPanoWidthPx ?: return false
            val croppedWidth = croppedAreaWidthPx ?: return false
            return croppedWidth * 2 <= fullWidth + HALF_SPHERE_PIXEL_TOLERANCE
        }

        companion object {
            private const val HALF_SPHERE_PIXEL_TOLERANCE = 8
        }
    }

    override fun read(path: String): PhotoSphereMetadata? {
        if (path.contains("://")) return null

        val file = File(path)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val xmp = readEmbeddedXmp(file) ?: return null
            parseXmp(xmp)
        } catch (e: Exception) {
            Timber.w(e, "ExifPhotoSphereReader: failed to inspect %s", path)
            null
        }
    }

    internal fun parseXmp(xmp: String): PhotoSphereMetadata? {
        if (!PROJECTION_TYPE_REGEX.containsMatchIn(xmp)) return null

        return PhotoSphereMetadata(
            isEquirectangular = true,
            fullPanoWidthPx = extractIntValue(xmp, "FullPanoWidthPixels"),
            fullPanoHeightPx = extractIntValue(xmp, "FullPanoHeightPixels"),
            croppedAreaWidthPx = extractIntValue(xmp, "CroppedAreaImageWidthPixels"),
            croppedAreaHeightPx = extractIntValue(xmp, "CroppedAreaImageHeightPixels"),
        )
    }

    // visible for testing
    internal fun containsProjectionTypeMarker(xmp: String): Boolean = PROJECTION_TYPE_REGEX.containsMatchIn(xmp)

    private fun readEmbeddedXmp(file: File): String? {
        FileInputStream(file).use { input ->
            val bytes = ByteArray(MAX_XMP_SCAN_BYTES)
            // S3790: a single InputStream.read(byte[]) may return fewer bytes than requested - loop
            // until the scan window is full or EOF, or the XMP text is silently truncated.
            var read = 0
            while (read < bytes.size) {
                val chunk = input.read(bytes, read, bytes.size - read)
                if (chunk < 0) break
                read += chunk
            }
            if (read <= 0) return null

            val text = String(bytes, 0, read, StandardCharsets.ISO_8859_1)
            if (!text.contains("GPano") && !text.contains("ProjectionType")) {
                return null
            }
            return text
        }
    }

    private fun extractIntValue(xmp: String, fieldName: String): Int? {
        // Raw strings: single backslash = regex escape. \s = whitespace, \d = digit.
        val attribute = Regex("""GPano:$fieldName\s*=\s*["'](\d+)["']""")
            .find(xmp)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (attribute != null) return attribute

        return Regex("""<GPano:$fieldName>\s*(\d+)\s*</GPano:$fieldName>""")
            .find(xmp)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    companion object {
        private const val MAX_XMP_SCAN_BYTES = 512 * 1024

        // Raw strings: single backslash = regex escape. \s = whitespace.
        private val PROJECTION_TYPE_REGEX = Regex(
            """GPano:ProjectionType\s*=\s*["']equirectangular["']|<GPano:ProjectionType>\s*equirectangular\s*</GPano:ProjectionType>""",
            RegexOption.IGNORE_CASE,
        )
    }
}