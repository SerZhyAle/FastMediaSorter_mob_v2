package com.sza.fastmediasorter.wear.data.network.sftp

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import timber.log.Timber
import java.security.MessageDigest
import java.util.Base64

/**
 * S1555 - applies the host-key pin the phone set for a source.
 *
 * The watch never decides which key to trust: it has no screen for it and no way to learn one. It
 * only enforces a decision already made on the phone (S0046) and carried in the sync payload, in
 * canonical `SHA256:<base64-no-padding>` form - the phone canonicalises before sending, so no
 * fingerprint parsing exists here.
 *
 * A source with no pin keeps the permissive behaviour it has always had; tightening that would
 * break every source saved before this ticket, which the spec's risk table forbids.
 */
object WearHostKeyPolicy {

    private const val SHA256_PREFIX = "SHA256:"

    /** Returns the pin actually applied, or null when the session stays permissive. */
    fun apply(session: Session, source: NetworkSource): String? {
        val pin = source.hostKeyFingerprint
        return if (pin.isNullOrBlank() || !pin.startsWith(SHA256_PREFIX)) {
            session.setConfig("StrictHostKeyChecking", "no")
            null
        } else {
            session.setHostKeyRepository(PinnedHostKeyRepository(pin))
            session.setConfig("StrictHostKeyChecking", "yes")
            pin
        }
    }
}

/**
 * S1555 - JSch repository that accepts exactly one SHA256 fingerprint.
 *
 * CHANGED is fatal to JSch, so a substituted key aborts the connection before authentication and
 * the credentials are never offered to the impostor. Only canonical fingerprints are logged, never
 * raw key bytes.
 */
class PinnedHostKeyRepository(private val expectedCanonical: String) : HostKeyRepository {

    override fun check(host: String?, key: ByteArray?): Int {
        if (key == null || key.isEmpty()) {
            Timber.w("SFTP host-key check: empty server key for host=$host")
            return HostKeyRepository.CHANGED
        }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(key)
        val actualCanonical = SHA256_PREFIX + Base64.getEncoder().withoutPadding().encodeToString(sha256)

        val expectedBytes = expectedCanonical.toByteArray(Charsets.US_ASCII)
        val actualBytes = actualCanonical.toByteArray(Charsets.US_ASCII)
        val matches = expectedBytes.size == actualBytes.size &&
            MessageDigest.isEqual(expectedBytes, actualBytes)

        return if (matches) {
            HostKeyRepository.OK
        } else {
            Timber.w("SFTP host-key mismatch on watch: expected=$expectedCanonical actual=$actualCanonical host=$host")
            HostKeyRepository.CHANGED
        }
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) {
        // Pin-only repository: it never learns a key.
    }

    override fun remove(host: String?, type: String?) {
        // Pin-only repository: nothing to forget.
    }

    override fun remove(host: String?, type: String?, key: ByteArray?) {
        // Pin-only repository: nothing to forget.
    }

    override fun getKnownHostsRepositoryID(): String = REPO_ID

    override fun getHostKey(): Array<HostKey> = emptyArray()

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()

    private companion object {
        const val SHA256_PREFIX = "SHA256:"
        const val REPO_ID = "WearPinnedHostKeyRepository(S1555)"
    }
}
