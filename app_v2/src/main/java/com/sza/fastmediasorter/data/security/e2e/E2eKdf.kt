package com.sza.fastmediasorter.data.security.e2e

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter

/**
 * HKDF-SHA256 (RFC 5869) and the labeled forms of RFC 9180 section 4.
 */
object E2eKdf {

    const val HASH_LENGTH = 32
    private const val MAX_BLOCKS = 255
    private val HPKE_LABEL_PREFIX = E2eBytes.ascii("HPKE-v1")

    fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt
        return hmac(effectiveSalt, ikm)
    }

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 0..MAX_BLOCKS * HASH_LENGTH) { "HKDF output too long" }
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, E2eBytes.concat(previous, info, byteArrayOf(counter.toByte())))
            val take = minOf(previous.size, length - offset)
            previous.copyInto(out, offset, 0, take)
            offset += take
            counter++
        }
        return out
    }

    /** HKDF with an empty salt in one call - the value key of a sealed clipboard value (item DN). */
    fun derive(ikm: ByteArray, info: ByteArray, length: Int): ByteArray =
        expand(extract(ByteArray(0), ikm), info, length)

    fun labeledExtract(suiteId: ByteArray, salt: ByteArray, label: String, ikm: ByteArray): ByteArray =
        extract(salt, E2eBytes.concat(HPKE_LABEL_PREFIX, suiteId, E2eBytes.ascii(label), ikm))

    fun labeledExpand(suiteId: ByteArray, prk: ByteArray, label: String, info: ByteArray, length: Int): ByteArray {
        val labeledInfo = E2eBytes.concat(
            E2eBytes.i2osp(length.toLong(), 2),
            HPKE_LABEL_PREFIX,
            suiteId,
            E2eBytes.ascii(label),
            info,
        )
        return expand(prk, labeledInfo, length)
    }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        val out = ByteArray(mac.macSize)
        mac.doFinal(out, 0)
        return out
    }
}
