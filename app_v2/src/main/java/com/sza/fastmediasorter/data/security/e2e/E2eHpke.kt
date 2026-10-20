package com.sza.fastmediasorter.data.security.e2e

import java.math.BigInteger

/**
 * HPKE (RFC 9180) in the Auth mode with the one suite of contract item DH:
 * DHKEM(P-256, HKDF-SHA256) `0x0010`, HKDF-SHA256 `0x0001`, AES-128-GCM `0x0001`.
 *
 * Written from the RFC rather than taken from Bouncy Castle's `HPKE`, whose encapsulation draws its
 * ephemeral key internally: the contract owes reproducible vectors (`vectors/e2e-v1.json`), so the
 * ephemeral key has to come from the caller's [E2eRandom]. Conformance is RFC 9180 Appendix A.3.3,
 * checked by the unit tests before anything is sealed.
 */
object E2eHpke {

    const val ENC_LENGTH = E2eP256.PUBLIC_KEY_LENGTH
    private const val MODE_AUTH: Byte = 0x02
    private const val KEM_ID = 0x0010L
    private const val KDF_ID = 0x0001L
    private const val AEAD_ID = 0x0001L
    private const val SECRET_LENGTH = 32
    private const val KEY_LENGTH = 16
    private const val ID_LENGTH = 2
    private const val MAX_DERIVE_ATTEMPTS = 255

    private val kemSuiteId = E2eBytes.concat(E2eBytes.ascii("KEM"), E2eBytes.i2osp(KEM_ID, ID_LENGTH))
    private val hpkeSuiteId = E2eBytes.concat(
        E2eBytes.ascii("HPKE"),
        E2eBytes.i2osp(KEM_ID, ID_LENGTH),
        E2eBytes.i2osp(KDF_ID, ID_LENGTH),
        E2eBytes.i2osp(AEAD_ID, ID_LENGTH),
    )

    /** The negotiated AEAD of one HPKE context: its key, base nonce and sequence counter. */
    class Context(val key: ByteArray, val baseNonce: ByteArray, val sharedSecret: ByteArray) {
        private var sequence = 0L

        fun seal(aad: ByteArray, plaintext: ByteArray): ByteArray =
            E2eAead.seal(key, nextNonce(), aad, plaintext)

        fun open(aad: ByteArray, ciphertext: ByteArray): ByteArray? =
            E2eAead.open(key, nextNonce(), aad, ciphertext)

        /** Moves the counter, as the RFC vectors skip sequence numbers 3 and 5 to 254. */
        fun skipTo(next: Long) {
            sequence = next
        }

        private fun nextNonce(): ByteArray {
            val counter = E2eBytes.i2osp(sequence++, E2eAead.NONCE_LENGTH)
            return ByteArray(E2eAead.NONCE_LENGTH) { i -> (baseNonce[i].toInt() xor counter[i].toInt()).toByte() }
        }
    }

    /** The result of one single-shot seal: the encapsulated key and the ciphertext. */
    class Sealed(val enc: ByteArray, val ciphertext: ByteArray)

    /** DeriveKeyPair of RFC 9180 section 7.1.3 for P-256 - used by the vectors only. */
    fun deriveKeyPair(ikm: ByteArray): E2eP256.KeyPair {
        val prk = E2eKdf.labeledExtract(kemSuiteId, ByteArray(0), "dkp_prk", ikm)
        var counter = 0
        while (counter <= MAX_DERIVE_ATTEMPTS) {
            val candidate = E2eKdf.labeledExpand(
                kemSuiteId,
                prk,
                "candidate",
                byteArrayOf(counter.toByte()),
                E2eP256.PRIVATE_KEY_LENGTH,
            )
            if (E2eP256.isValidScalar(BigInteger(1, candidate))) {
                return E2eP256.keyPairFromScalar(candidate)
            }
            counter++
        }
        error("DeriveKeyPair found no valid scalar")
    }

    fun setupAuthSender(
        recipientPublic: ByteArray,
        info: ByteArray,
        sender: E2eP256.KeyPair,
        ephemeral: E2eP256.KeyPair,
    ): Pair<ByteArray, Context> {
        val dh = E2eBytes.concat(
            E2eP256.dh(ephemeral.privateKey, recipientPublic),
            E2eP256.dh(sender.privateKey, recipientPublic),
        )
        val enc = ephemeral.publicKey
        val sharedSecret = extractAndExpand(dh, E2eBytes.concat(enc, recipientPublic, sender.publicKey))
        return enc to keySchedule(sharedSecret, info)
    }

    fun setupAuthReceiver(
        enc: ByteArray,
        recipient: E2eP256.KeyPair,
        info: ByteArray,
        senderPublic: ByteArray,
    ): Context {
        val dh = E2eBytes.concat(
            E2eP256.dh(recipient.privateKey, enc),
            E2eP256.dh(recipient.privateKey, senderPublic),
        )
        val sharedSecret = extractAndExpand(dh, E2eBytes.concat(enc, recipient.publicKey, senderPublic))
        return keySchedule(sharedSecret, info)
    }

    fun sealAuth(
        recipientPublic: ByteArray,
        info: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        sender: E2eP256.KeyPair,
        random: E2eRandom,
    ): Sealed {
        val (enc, context) = setupAuthSender(recipientPublic, info, sender, E2eP256.generate(random))
        return Sealed(enc, context.seal(aad, plaintext))
    }

    /** Null for any failure - a bad point, a wrong key or a failed tag all open nothing. */
    fun openAuth(
        enc: ByteArray,
        recipient: E2eP256.KeyPair,
        info: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
        senderPublic: ByteArray,
    ): ByteArray? =
        if (!E2eP256.isValidPublicKey(enc) || !E2eP256.isValidPublicKey(senderPublic)) {
            null
        } else {
            setupAuthReceiver(enc, recipient, info, senderPublic).open(aad, ciphertext)
        }

    private fun extractAndExpand(dh: ByteArray, kemContext: ByteArray): ByteArray {
        val prk = E2eKdf.labeledExtract(kemSuiteId, ByteArray(0), "eae_prk", dh)
        return E2eKdf.labeledExpand(kemSuiteId, prk, "shared_secret", kemContext, SECRET_LENGTH)
    }

    private fun keySchedule(sharedSecret: ByteArray, info: ByteArray): Context {
        val pskIdHash = E2eKdf.labeledExtract(hpkeSuiteId, ByteArray(0), "psk_id_hash", ByteArray(0))
        val infoHash = E2eKdf.labeledExtract(hpkeSuiteId, ByteArray(0), "info_hash", info)
        val scheduleContext = E2eBytes.concat(byteArrayOf(MODE_AUTH), pskIdHash, infoHash)
        val secret = E2eKdf.labeledExtract(hpkeSuiteId, sharedSecret, "secret", ByteArray(0))
        val key = E2eKdf.labeledExpand(hpkeSuiteId, secret, "key", scheduleContext, KEY_LENGTH)
        val nonce = E2eKdf.labeledExpand(hpkeSuiteId, secret, "base_nonce", scheduleContext, E2eAead.NONCE_LENGTH)
        return Context(key, nonce, sharedSecret)
    }
}
