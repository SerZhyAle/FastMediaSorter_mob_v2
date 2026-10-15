package com.sza.fastmediasorter.data.broadcast.auth

import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom

/** One entry of `verifiers` (contract DEVICE-EXCHANGE item BF): what the relay checks a listener against. */
data class BroadcastVerifier(val user: String, val salt: String, val hash: String)

/**
 * Makes the relay's verifiers: PBKDF2-HMAC-SHA256, 600000 iterations, 32 bytes, over a 16-byte random salt, both
 * base64url without padding. BouncyCastle rather than `SecretKeyFactory`, whose `PBKDF2WithHmacSHA256` the `legacy`
 * flavor's API 23 does not have.
 */
object BroadcastVerifiers {

    const val ITERATIONS = 600_000
    const val SALT_BYTES = 16
    const val HASH_BYTES = 32
    private const val BITS_PER_BYTE = 8

    private val random = SecureRandom()

    fun create(pair: BroadcastCredentialPair): BroadcastVerifier {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(pair.password, salt, ITERATIONS)
        return BroadcastVerifier(pair.user, E2eBytes.b64url(salt), E2eBytes.b64url(hash))
    }

    fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        generator.init(password.toByteArray(Charsets.UTF_8), salt, iterations)
        return (generator.generateDerivedParameters(HASH_BYTES * BITS_PER_BYTE) as KeyParameter).key
    }
}
