package com.sza.fastmediasorter.util

import android.graphics.Bitmap
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Replaces a file that may be the user's only copy of a photo or GIF without ever truncating it: the
 * new content is written into a hidden sibling file and swapped in by rename, so a failed encode, a
 * full disk, an out-of-memory error or a kill mid-write leaves the original untouched.
 *
 * `FileOutputStream(original)` truncates the original before the first byte of the encode exists, and
 * `delete()` followed by `renameTo()` loses it when the rename is refused - both shapes are banned for
 * a file the user already owns.
 */
object InPlaceFileReplacer {

    private const val PNG_QUALITY = 100
    private const val LOSSY_QUALITY = 95

    /**
     * Writes [target] from [write], which returns false when its encoder reports a failure.
     * @param tag distinguishes concurrent editors in the temporary file name.
     * @throws IOException when the write or the swap fails; [target] then still holds its old bytes,
     *   except in the one case described on [swapIn].
     */
    fun replace(target: File, tag: String, write: (OutputStream) -> Boolean) {
        // Same directory so the rename stays on one filesystem; the leading dot keeps scanners off it.
        val tempFile = File(target.absoluteFile.parentFile, ".${target.name}.$tag.tmp")
        var keepTempFile = false
        try {
            val written = FileOutputStream(tempFile).use { out ->
                write(out).also { ok -> if (ok) out.fd.sync() }
            }
            if (!written) throw IOException("Failed to write the replacement of ${target.path}")
            keepTempFile = !swapIn(tempFile, target)
            if (keepTempFile) throw IOException("Replacement of ${target.path} left at ${tempFile.path}")
        } finally {
            if (!keepTempFile && tempFile.exists() && !tempFile.delete()) {
                Timber.w("Failed to delete temporary file: ${tempFile.path}")
            }
        }
    }

    /** Encodes [bitmap] into [target] in the format its extension names. */
    fun replaceWithBitmap(target: File, bitmap: Bitmap, tag: String) {
        val format = compressFormatFor(target)
        val quality = if (format == Bitmap.CompressFormat.PNG) PNG_QUALITY else LOSSY_QUALITY
        replace(target, tag) { out -> bitmap.compress(format, quality, out) }
    }

    /** Re-encodes as JPEG regardless of the extension, for callers whose source is always a JPEG. */
    fun replaceWithJpeg(target: File, bitmap: Bitmap, quality: Int, tag: String) {
        replace(target, tag) { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
    }

    private fun compressFormatFor(file: File): Bitmap.CompressFormat = when (file.extension.lowercase()) {
        "png" -> Bitmap.CompressFormat.PNG
        "webp" -> Bitmap.CompressFormat.WEBP
        else -> Bitmap.CompressFormat.JPEG
    }

    /**
     * Some filesystems refuse to rename over an existing file; there the original is deleted first.
     * @return false only when that fallback deleted the original and then failed, leaving [source]
     *   as the one full copy that must not be cleaned up.
     */
    private fun swapIn(source: File, target: File): Boolean {
        if (source.renameTo(target)) return true
        if (target.exists() && !target.delete()) throw IOException("Failed to replace ${target.path}")
        return source.renameTo(target)
    }
}
