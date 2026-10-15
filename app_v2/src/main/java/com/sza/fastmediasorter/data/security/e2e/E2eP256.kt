package com.sza.fastmediasorter.data.security.e2e

import org.bouncycastle.asn1.x9.ECNamedCurveTable
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

/**
 * The curve P-256: key encoding, validation and the Diffie-Hellman of DHKEM(P-256, HKDF-SHA256).
 *
 * Every public key on the wire is the 65-byte uncompressed SEC1 point (contract item DF); a point
 * that does not decode to a valid point of the curve is refused before any arithmetic touches it.
 */
object E2eP256 {

    const val PUBLIC_KEY_LENGTH = 65
    const val PRIVATE_KEY_LENGTH = 32
    private const val UNCOMPRESSED_PREFIX: Byte = 0x04

    private val params = ECNamedCurveTable.getByName("secp256r1")
    val order: BigInteger = params.n

    /** A private scalar with its public point, both in wire form. */
    class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

    fun isValidScalar(scalar: BigInteger): Boolean = scalar.signum() > 0 && scalar < order

    fun keyPairFromScalar(privateKey: ByteArray): KeyPair {
        require(privateKey.size == PRIVATE_KEY_LENGTH) { "P-256 private key must be 32 bytes" }
        val scalar = BigInteger(1, privateKey)
        require(isValidScalar(scalar)) { "P-256 private key out of range" }
        return KeyPair(privateKey.copyOf(), encode(params.g.multiply(scalar)))
    }

    /** Draws a private key from [random] by rejection sampling, so the scalar is uniform below the order. */
    fun generate(random: E2eRandom): KeyPair {
        var candidate = random.next(PRIVATE_KEY_LENGTH)
        while (!isValidScalar(BigInteger(1, candidate))) {
            candidate = random.next(PRIVATE_KEY_LENGTH)
        }
        return keyPairFromScalar(candidate)
    }

    fun isValidPublicKey(publicKey: ByteArray): Boolean =
        publicKey.size == PUBLIC_KEY_LENGTH &&
            publicKey[0] == UNCOMPRESSED_PREFIX &&
            runCatching { decode(publicKey) }.isSuccess

    /** The x-coordinate of `privateKey * publicKey`, 32 bytes - DH() of RFC 9180 section 4.1. */
    fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val shared = decode(publicKey).multiply(BigInteger(1, privateKey)).normalize()
        require(!shared.isInfinity) { "DH produced the point at infinity" }
        return shared.affineXCoord.getEncoded()
    }

    private fun decode(publicKey: ByteArray): ECPoint {
        require(publicKey.size == PUBLIC_KEY_LENGTH && publicKey[0] == UNCOMPRESSED_PREFIX) {
            "not an uncompressed P-256 point"
        }
        val point = params.curve.decodePoint(publicKey)
        require(point.isValid && !point.isInfinity) { "not a point of P-256" }
        return point
    }

    private fun encode(point: ECPoint): ByteArray = point.normalize().getEncoded(false)
}
