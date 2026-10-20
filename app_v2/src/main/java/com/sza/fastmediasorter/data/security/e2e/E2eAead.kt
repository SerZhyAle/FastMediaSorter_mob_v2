package com.sza.fastmediasorter.data.security.e2e

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * AES-GCM with a 16-byte tag. The key length picks the variant: 16 bytes for the HPKE suite's
 * AES-128-GCM, 32 bytes for every payload, segment and relay record of the contract.
 */
object E2eAead {

    const val NONCE_LENGTH = 12
    const val TAG_LENGTH = 16
    private const val TAG_BITS = 128

    fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray =
        run(true, key, nonce, aad, plaintext)

    /** Returns null on a failed tag, so a caller can never use partial plaintext. */
    fun open(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray? =
        if (ciphertext.size < TAG_LENGTH) {
            null
        } else {
            try {
                run(false, key, nonce, aad, ciphertext)
            } catch (_: InvalidCipherTextException) {
                null
            }
        }

    private fun run(encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        require(nonce.size == NONCE_LENGTH) { "AES-GCM nonce must be 12 bytes" }
        @Suppress("DEPRECATION") // AESEngine.newInstance() arrived after the pinned 1.75.
        val cipher = GCMBlockCipher(AESEngine())
        cipher.init(encrypt, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(input.size))
        val written = cipher.processBytes(input, 0, input.size, out, 0)
        val total = written + cipher.doFinal(out, written)
        return if (total == out.size) out else out.copyOf(total)
    }
}
