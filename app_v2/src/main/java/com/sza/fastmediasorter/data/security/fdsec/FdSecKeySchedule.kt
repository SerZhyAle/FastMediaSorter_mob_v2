package com.sza.fastmediasorter.data.security.fdsec

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.text.Normalizer

/**
 * The credential-to-keys schedule of FD-SEC format version 1.
 *
 * Nothing here reads a parameter out of the container: the mask key has to exist before a single
 * byte of the head can be interpreted, so the work factor is carried by [FdSecKdfProfile] as a
 * property of the format version instead.
 */
class FdSecKeySchedule(private val profile: FdSecKdfProfile = FdSecKdfProfile.V1) {

    /**
     * NFC, UTF-8, no trimming - a leading or trailing space is part of the credential.
     *
     * The intermediate [String] cannot be wiped on a garbage-collected runtime; overwriting key
     * material is best effort here, exactly as the contract describes it.
     */
    fun normalizeCredential(credential: CharArray): ByteArray =
        Normalizer.normalize(String(credential), Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)

    /**
     * The root key. A credential shorter than the profile threshold takes the deliberately expensive
     * Argon2id branch; one at or above it is trusted to carry its own entropy. Which branch ran is
     * stored nowhere - the reader recomputes it from the length of the credential it holds.
     */
    fun deriveRoot(credentialUtf8: ByteArray, salt: ByteArray): ByteArray =
        if (credentialUtf8.size < profile.thresholdBytes) {
            argon2id(credentialUtf8, salt)
        } else {
            blake2b512(salt + credentialUtf8).copyOf(FdSecFormat.ROOT_KEY_SIZE)
        }

    fun maskKey(root: ByteArray): ByteArray = blake2b256Keyed(root, FdSecFormat.CTX_MASK.toByteArray(Charsets.UTF_8))

    fun kek(root: ByteArray): ByteArray = blake2b256Keyed(root, FdSecFormat.CTX_KEK.toByteArray(Charsets.UTF_8))

    /** `BLAKE2b-256(key = fileKey, msg = "FDSEC1/nonce/" || ctx)[0:24]`. */
    fun nonceFor(fileKey: ByteArray, context: ByteArray): ByteArray {
        val message = FdSecFormat.CTX_NONCE_PREFIX.toByteArray(Charsets.UTF_8) + context
        return blake2b256Keyed(fileKey, message).copyOf(FdSecFormat.NONCE_SIZE)
    }

    fun metadataNonce(fileKey: ByteArray): ByteArray =
        nonceFor(fileKey, FdSecFormat.NONCE_CTX_META.toByteArray(Charsets.UTF_8))

    fun chunkNonce(fileKey: ByteArray, index: Long): ByteArray {
        val context = FdSecFormat.NONCE_CTX_CHUNK_PREFIX.toByteArray(Charsets.UTF_8) + FdSecBytes.u64le(index)
        return nonceFor(fileKey, context)
    }

    /**
     * The head mask: raw ChaCha20 (RFC 8439) under a twelve-byte zero nonce with the block counter
     * at zero. The salt is fresh per container, so the key and the keystream are single-use and the
     * nonce need not vary.
     */
    fun headKeystream(maskKey: ByteArray, size: Int): ByteArray {
        val engine = ChaCha7539Engine()
        engine.init(true, ParametersWithIV(KeyParameter(maskKey), ByteArray(IETF_NONCE_SIZE)))
        val out = ByteArray(size)
        engine.processBytes(ByteArray(size), 0, size, out, 0)
        return out
    }

    private fun argon2id(credentialUtf8: ByteArray, salt: ByteArray): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(profile.memoryKib)
            .withIterations(profile.iterations)
            .withParallelism(profile.parallelism)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(parameters)
        val out = ByteArray(FdSecFormat.ROOT_KEY_SIZE)
        generator.generateBytes(credentialUtf8, out, 0, out.size)
        return out
    }

    companion object {
        private const val IETF_NONCE_SIZE = 12
        private const val BITS_PER_BYTE = 8

        private const val BLAKE2B_256_BITS = FdSecFormat.DIGEST_SIZE * BITS_PER_BYTE

        fun blake2b256(message: ByteArray): ByteArray = digest(Blake2bDigest(BLAKE2B_256_BITS), message)

        fun blake2b512(message: ByteArray): ByteArray = digest(Blake2bDigest(BLAKE2B_512_BITS), message)

        /**
         * Keyed BLAKE2b through the primitive's own key input, never an HMAC construction over it.
         * The 4-argument constructor takes the digest length in BYTES, unlike the 1-argument one.
         */
        fun blake2b256Keyed(key: ByteArray, message: ByteArray): ByteArray =
            digest(Blake2bDigest(key, FdSecFormat.DIGEST_SIZE, null, null), message)

        private const val BLAKE2B_512_BITS = 512

        private fun digest(digest: Blake2bDigest, message: ByteArray): ByteArray {
            digest.update(message, 0, message.size)
            val out = ByteArray(digest.digestSize)
            digest.doFinal(out, 0)
            return out
        }
    }
}
