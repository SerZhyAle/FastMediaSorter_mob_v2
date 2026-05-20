package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import timber.log.Timber
import java.security.MessageDigest
import java.util.Base64

/**
 * S0046 - JSch [HostKeyRepository] that pins a single SHA256 fingerprint.
 *
 * Returns:
 *  - [HostKeyRepository.OK] when the server key SHA256 matches the expected canonical fingerprint
 *    (constant-time comparison via [MessageDigest.isEqual]).
 *  - [HostKeyRepository.CHANGED] on any mismatch - JSch treats this as a fatal error and aborts
 *    the connection before auth. A `Timber.w` records both expected and actual fingerprints
 *    (no raw key bytes are logged - only their canonical SHA256 form).
 *
 * The other interface members (`add`, `remove`, `getHostKey`, `getKnownHostsRepositoryID`) return
 * inert defaults - this repository is read-only and pin-only; it never accumulates state.
 *
 * @param expectedCanonical canonical fingerprint produced by
 *   [com.sza.fastmediasorter.utils.SshFingerprintNormalizer.canonical] -
 *   format: `SHA256:<base64-no-padding>`. Constructor throws [IllegalArgumentException] if the
 *   value is not in canonical form (defence against accidental mis-wiring upstream).
 */
class PinnedHostKeyRepository(private val expectedCanonical: String) : HostKeyRepository {

    init {
        require(expectedCanonical.startsWith(SHA256_PREFIX)) {
            "expectedCanonical must be in canonical form 'SHA256:<base64>', got '$expectedCanonical'"
        }
    }

    override fun check(host: String?, key: ByteArray?): Int {
        if (key == null || key.isEmpty()) {
            Timber.w("SFTP host-key check: empty server key for host=$host")
            return HostKeyRepository.CHANGED
        }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(key)
        val actualPayload = Base64.getEncoder().withoutPadding().encodeToString(sha256)
        val actualCanonical = SHA256_PREFIX + actualPayload

        val expectedBytes = expectedCanonical.toByteArray(Charsets.US_ASCII)
        val actualBytes = actualCanonical.toByteArray(Charsets.US_ASCII)

        return if (expectedBytes.size == actualBytes.size && MessageDigest.isEqual(expectedBytes, actualBytes)) {
            HostKeyRepository.OK
        } else {
            Timber.w(
                "SFTP host-key mismatch: expected=$expectedCanonical actual=$actualCanonical host=$host"
            )
            HostKeyRepository.CHANGED
        }
    }

    // --- Inert read-only stubs --------------------------------------------------------------

    override fun add(hostkey: HostKey?, ui: UserInfo?) {
        // No-op: pinned repository never accumulates state.
    }

    override fun remove(host: String?, type: String?) {
        // No-op.
    }

    override fun remove(host: String?, type: String?, key: ByteArray?) {
        // No-op.
    }

    override fun getKnownHostsRepositoryID(): String = REPO_ID

    override fun getHostKey(): Array<HostKey> = emptyArray()

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()

    companion object {
        private const val SHA256_PREFIX = "SHA256:"
        private const val REPO_ID = "PinnedHostKeyRepository(S0046)"
    }
}

/**
 * Typed exception for callers that wrap a JSch session and want to surface a pin-mismatch as a
 * UI-distinct error (separate from auth failures). Throw it from the caller layer when JSch's
 * connect path fails with a host-key-related JSchException AND the verifier produced a mismatch.
 */
class HostKeyMismatchException(
    val expected: String,
    val actual: String,
    message: String = "SFTP host-key mismatch: expected=$expected actual=$actual"
) : RuntimeException(message)
