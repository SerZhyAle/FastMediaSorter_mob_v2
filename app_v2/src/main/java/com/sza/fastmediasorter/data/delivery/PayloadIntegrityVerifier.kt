package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.PayloadFile
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies a downloaded payload file against its app-pinned integrity anchors before it is attached
 * or `System.load`ed (S0386 strategic §6.1 B2, ADR-3): the SHA-256 must equal the compiled
 * [PayloadFile.sha256] and the size must be at least [PayloadFile.minSize]. The mirror cannot
 * substitute a payload because the hash would not match.
 *
 * A blank pinned SHA-256 marks a pure-resource file (Set C audio videos): the hash is skipped and
 * only the size lower bound is enforced (no native code is loaded from it, so there is no
 * code-execution attack surface to anchor).
 */
@Singleton
class PayloadIntegrityVerifier @Inject constructor() {

    sealed interface Result {
        data object Verified : Result
        data class Failed(val reason: String) : Result
    }

    fun verify(file: File, expected: PayloadFile): Result {
        if (!file.isFile) {
            return fail(expected.fileName, "payload file missing")
        }
        val size = file.length()
        if (size < expected.minSize) {
            return fail(expected.fileName, "size $size < minSize ${expected.minSize}")
        }
        if (expected.sha256.isBlank()) {
            // Pure-resource files (blank pinned hash) are verified by size bounds only; add an upper
            // bound so a substituted/corrupt oversized file cannot flood filesDir (the lower bound
            // alone would accept an arbitrarily large payload).
            if (size > MAX_UNVERIFIED_RESOURCE_BYTES) {
                return fail(expected.fileName, "unverified resource too large: $size > $MAX_UNVERIFIED_RESOURCE_BYTES")
            }
            // S1483: an unpinned ZIP is a tile pack, and without a hash the size bounds alone would
            // accept a truncated download - which would then overwrite working artwork with a file
            // that decodes to nothing. Check the container contract instead: the archive opens, it
            // holds entries, and every entry name is the decimal slot index StreamTilePackReader
            // looks up. That is the same class of structural check the catalog import applies to
            // streams.csv, and it is what replaces the pin rather than nothing replacing it.
            if (expected.fileName.endsWith(".zip", ignoreCase = true)) {
                return verifyTilePack(file, expected.fileName)
            }
            return Result.Verified
        }
        val actual = sha256(file)
        if (!actual.equals(expected.sha256, ignoreCase = true)) {
            return fail(expected.fileName, "SHA-256 mismatch")
        }
        return Result.Verified
    }

    /**
     * S1483: structural verification of a tile pack. Every entry name must be a plain decimal slot
     * index - the container contract the reader and the offline packer share - so a zip of ordinary
     * files, an HTML error page saved as `.zip`, or a truncated download is rejected here rather than
     * discovered later as blank cells in the grid.
     */
    private fun verifyTilePack(file: File, fileName: String): Result = try {
        java.util.zip.ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            when {
                entries.isEmpty() -> fail(fileName, "tile pack has no entries")
                entries.any { it.name.toIntOrNull() == null } ->
                    fail(fileName, "tile pack entry name is not a slot index")
                else -> Result.Verified
            }
        }
    } catch (e: java.io.IOException) {
        // A truncated or non-zip payload throws ZipException/IOException here; that is precisely the
        // case this check exists for, so it is a verdict rather than an error to propagate.
        fail(fileName, "tile pack unreadable: ${e.message}")
    }

    private fun fail(fileName: String, reason: String): Result.Failed {
        Timber.w("Payload integrity check failed for %s: %s", fileName, reason)
        return Result.Failed("$fileName: $reason")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte.toInt() and 0xff)
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8192
        // Sanity ceiling for hash-less pure-resource files (e.g. audio-viz videos are ~1-2 MB each);
        // far above any real delivered resource, only guards against an oversized substitution.
        const val MAX_UNVERIFIED_RESOURCE_BYTES = 64L * 1024 * 1024
    }
}
