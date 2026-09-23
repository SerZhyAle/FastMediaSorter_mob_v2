package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.FdSecResult
import com.sza.fastmediasorter.domain.transfer.SiblingFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

/**
 * S3408: the secure-container write discipline for a folder that is not a local path.
 *
 * The contract asks for a temporary file in the destination folder, a full re-read of what was
 * actually written, and only then the rename. [proven] has already passed that proof locally - a
 * container read back to its payload digest, or an original checked against the sealed digest - so
 * the re-read here hashes every byte the location returns and compares it with [proven]. Identity
 * with a proven file is the stronger statement, and it spares a second key derivation.
 *
 * Nothing here logs a name or a path: a restored file carries the true name the container exists to
 * hide.
 */
class PlaceVerifiedFileBesideUseCase @Inject constructor() {

    /**
     * Writes [proven] into [folder] as [desiredName], suffixed on a clash and never overwriting, and
     * reports the name it got. [scratch] is a caller-owned directory for the read-back copy.
     */
    suspend operator fun invoke(
        proven: File,
        folder: SiblingFolder,
        desiredName: String,
        scratch: File,
    ): FdSecResult = withContext(Dispatchers.IO) {
        val readBack = File(scratch, READ_BACK_NAME)
        var temporary: String? = null
        var landed = false
        try {
            val written = folder.write(proven, TEMPORARY_PREFIX + System.nanoTime() + TEMPORARY_SUFFIX)
            temporary = written
            folder.read(written, readBack)
            if (sameBytes(proven, readBack)) {
                val finalName = freeName(folder, desiredName)
                folder.rename(written, finalName)
                landed = true
                Timber.d("S3408: read back identical, renamed into place")
                FdSecResult.Placed(finalName)
            } else {
                FdSecResult.Failed("the written copy did not read back identical")
            }
        } catch (e: IOException) {
            Timber.w("fdsec: writing beside the original failed (%s)", e.javaClass.simpleName)
            FdSecResult.Failed("the file could not be written beside the original")
        } finally {
            readBack.delete()
            val leftover = temporary
            if (leftover != null && !landed) {
                withContext(NonCancellable) { discard(folder, leftover) }
            }
        }
    }

    private suspend fun discard(folder: SiblingFolder, address: String) {
        try {
            folder.delete(address)
        } catch (e: IOException) {
            // The neutral temporary name is what the contract accepts an interrupted write to leave.
            Timber.w("fdsec: a temporary file beside the original could not be removed (%s)", e.javaClass.simpleName)
        }
    }

    /** A collision is suffixed, never overwritten - the same rule as the local pair. */
    private suspend fun freeName(folder: SiblingFolder, name: String): String {
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val tail = if (dot > 0) name.substring(dot) else ""
        var candidate = name
        var ordinal = 1
        while (folder.contains(candidate)) {
            candidate = "$stem-$ordinal$tail"
            ordinal++
        }
        return candidate
    }

    private fun sameBytes(expected: File, actual: File): Boolean =
        expected.length() == actual.length() && MessageDigest.isEqual(digestOf(expected), digestOf(actual))

    private fun digestOf(file: File): ByteArray {
        val digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
        val buffer = ByteArray(BUFFER_BYTES)
        file.inputStream().use { input ->
            var read = input.read(buffer)
            while (read != END_OF_STREAM) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest()
    }

    private companion object {
        const val READ_BACK_NAME = "read-back"
        const val TEMPORARY_PREFIX = ".fdsec-"
        const val TEMPORARY_SUFFIX = ".fdsec-part"
        const val DIGEST_ALGORITHM = "SHA-256"
        const val BUFFER_BYTES = 64 * 1024
        const val END_OF_STREAM = -1
    }
}
