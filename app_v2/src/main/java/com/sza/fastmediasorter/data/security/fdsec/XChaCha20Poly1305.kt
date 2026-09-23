package com.sza.fastmediasorter.data.security.fdsec

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.engines.ChaChaEngine
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.util.Pack

/**
 * XChaCha20-Poly1305 - 32-byte key, 24-byte nonce, 16-byte tag appended, associated data.
 *
 * BouncyCastle ships no XChaCha20 construction, so the extended nonce is folded down here: HChaCha20
 * over the key and the first 16 nonce bytes produces a sub-key, and the standard 12-byte-nonce AEAD
 * then runs under it with the nonce `0x00000000 || nonce[16..24)`. This is the only hand-built
 * primitive in the FD-SEC implementation and it is verified against a published XChaCha20 vector of
 * its own, not only against the container vectors: a wrong sub-key would surface as the contract's
 * indistinguishable "wrong credential, not a container, or tampering" outcome and never as a crash.
 */
object XChaCha20Poly1305 {

    private const val ROUNDS = 20
    private const val STATE_WORDS = 16
    private const val KEY_WORDS = 8
    private const val HNONCE_WORDS = 4
    private const val SUBKEY_WORDS = 8
    private const val HNONCE_BYTES = 16
    private const val INNER_NONCE_PREFIX_BYTES = 4
    private const val INNER_NONCE_BYTES = 12
    private const val TAG_BITS = 128

    /** ChaCha's "expand 32-byte k" constants, little-endian. */
    @Suppress("MagicNumber")
    private val SIGMA = intArrayOf(0x6170_7865, 0x3320_646E, 0x7962_2D32, 0x6B20_6574)

    fun seal(key: ByteArray, nonce: ByteArray, associatedData: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = newCipher(forEncryption = true, key = key, nonce = nonce, associatedData = associatedData)
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        var written = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        written += cipher.doFinal(out, written)
        return if (written == out.size) out else out.copyOf(written)
    }

    /** Returns null on a tag failure - the caller decides which outcome class that is. */
    @Suppress("SwallowedException")
    fun open(key: ByteArray, nonce: ByteArray, associatedData: ByteArray, ciphertext: ByteArray): ByteArray? {
        if (ciphertext.size < FdSecFormat.TAG_SIZE) {
            return null
        }
        val cipher = newCipher(forEncryption = false, key = key, nonce = nonce, associatedData = associatedData)
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        return try {
            var written = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
            written += cipher.doFinal(out, written)
            if (written == out.size) out else out.copyOf(written)
        } catch (e: InvalidCipherTextException) {
            // A wrong key and a flipped byte fail the tag identically; that is the security property
            // the contract's outcome taxonomy rests on, so the reason is deliberately discarded here
            // and classified by the caller, which knows which region failed.
            null
        }
    }

    private fun newCipher(
        forEncryption: Boolean,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): ChaCha20Poly1305 {
        require(key.size == FdSecFormat.FILE_KEY_SIZE) { "XChaCha20-Poly1305 key must be 32 bytes" }
        require(nonce.size == FdSecFormat.NONCE_SIZE) { "XChaCha20-Poly1305 nonce must be 24 bytes" }
        val subKey = hChaCha20(key, nonce.copyOf(HNONCE_BYTES))
        val innerNonce = ByteArray(INNER_NONCE_BYTES)
        nonce.copyInto(innerNonce, INNER_NONCE_PREFIX_BYTES, HNONCE_BYTES, FdSecFormat.NONCE_SIZE)
        val cipher = ChaCha20Poly1305()
        cipher.init(forEncryption, AEADParameters(KeyParameter(subKey), TAG_BITS, innerNonce, associatedData))
        return cipher
    }

    /**
     * HChaCha20: the ChaCha20 permutation over the constants, the key and 16 nonce bytes, keeping
     * words 0..3 and 12..15 **without** the feed-forward addition.
     *
     * BouncyCastle's `chachaCore` performs that addition on its way out, so it is subtracted back
     * off here rather than reimplementing the permutation; the arithmetic is exact in Int.
     */
    internal fun hChaCha20(key: ByteArray, nonce16: ByteArray): ByteArray {
        val input = IntArray(STATE_WORDS)
        SIGMA.copyInto(input, 0, 0, SIGMA.size)
        for (i in 0 until KEY_WORDS) {
            input[SIGMA.size + i] = Pack.littleEndianToInt(key, i * Int.SIZE_BYTES)
        }
        for (i in 0 until HNONCE_WORDS) {
            input[SIGMA.size + KEY_WORDS + i] = Pack.littleEndianToInt(nonce16, i * Int.SIZE_BYTES)
        }
        val state = IntArray(STATE_WORDS)
        ChaChaEngine.chachaCore(ROUNDS, input, state)
        val subKeyWords = IntArray(SUBKEY_WORDS)
        for (i in 0 until HNONCE_WORDS) {
            val tail = STATE_WORDS - HNONCE_WORDS + i
            subKeyWords[i] = state[i] - input[i]
            subKeyWords[HNONCE_WORDS + i] = state[tail] - input[tail]
        }
        val subKey = ByteArray(FdSecFormat.FILE_KEY_SIZE)
        Pack.intToLittleEndian(subKeyWords, subKey, 0)
        return subKey
    }
}
