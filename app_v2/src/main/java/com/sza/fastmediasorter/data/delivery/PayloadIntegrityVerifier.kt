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
            return Result.Verified
        }
        val actual = sha256(file)
        if (!actual.equals(expected.sha256, ignoreCase = true)) {
            return fail(expected.fileName, "SHA-256 mismatch")
        }
        return Result.Verified
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
    }
}
