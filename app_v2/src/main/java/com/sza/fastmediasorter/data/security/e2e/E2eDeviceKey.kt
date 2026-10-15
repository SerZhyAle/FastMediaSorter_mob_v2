package com.sza.fastmediasorter.data.security.e2e

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.math.BigInteger

/**
 * The device key of contract item DF: one P-256 pair per `deviceId`, its public half published as
 * `e2eKey` `{v, kem}`, its private half never leaving the device.
 */
class E2eDeviceKey private constructor(val pair: E2eP256.KeyPair) {

    /** `kem` of `e2eKey`: the uncompressed point, base64url without padding. */
    val kem: String get() = E2eBytes.b64url(pair.publicKey)

    val publicKey: ByteArray get() = pair.publicKey.copyOf()

    /** The private scalar, for the platform secret store only - never a backup, export or log. */
    fun exportPrivateForSecretStore(): ByteArray = pair.privateKey.copyOf()

    /** `e2eKey` as `enroll`, `register` and the device record carry it. */
    fun toWire(): JsonObject = JsonObject().apply {
        addProperty(KEY_VERSION, VERSION)
        addProperty(KEY_KEM, kem)
    }

    companion object {
        const val VERSION = 1
        const val CAPABILITY = "e2e-v1"
        const val SERVER_FEATURE = "sealed"
        const val KEY_E2E_KEY = "e2eKey"
        private const val KEY_VERSION = "v"
        private const val KEY_KEM = "kem"

        /**
         * The `kem` of a wire `e2eKey` when it is version 1 and a valid point; null for anything else, so an
         * unknown version is never read as a key.
         */
        fun kemOf(element: JsonElement?): String? = runCatching {
            val obj = element?.takeIf { it.isJsonObject }?.asJsonObject
            val kem = obj?.takeIf { it.get(KEY_VERSION)?.asInt == VERSION }?.get(KEY_KEM)?.asString
            kem?.takeIf { decodeKem(it) != null }
        }.getOrNull()
        private const val CHECK_CODE_PREFIX_BYTES = 8
        private const val CHECK_CODE_DIGITS = 12
        private const val CHECK_CODE_GROUP = 4
        private val CHECK_CODE_MODULUS = BigInteger.TEN.pow(CHECK_CODE_DIGITS)
        private val CHECK_CODE_LABEL = E2eBytes.ascii("fmsx-e2e-v1 check")

        fun generate(random: E2eRandom = E2eRandom.secure): E2eDeviceKey = E2eDeviceKey(E2eP256.generate(random))

        fun fromPrivate(privateKey: ByteArray): E2eDeviceKey = E2eDeviceKey(E2eP256.keyPairFromScalar(privateKey))

        /** Decodes a published `kem`; null when it is not a valid uncompressed P-256 point. */
        fun decodeKem(kem: String): ByteArray? =
            runCatching { E2eBytes.fromB64url(kem) }.getOrNull()?.takeIf(E2eP256::isValidPublicKey)

        /**
         * The twelve-digit check code of item DG: the first 8 bytes of SHA-256 over the label and the
         * 65 key bytes, unsigned big-endian, modulo 10^12, in three groups of four.
         */
        fun checkCode(publicKey: ByteArray): String {
            val digest = E2eBytes.sha256(CHECK_CODE_LABEL, publicKey)
            val value = BigInteger(1, digest.copyOf(CHECK_CODE_PREFIX_BYTES)).mod(CHECK_CODE_MODULUS)
            val digits = value.toString().padStart(CHECK_CODE_DIGITS, '0')
            return digits.chunked(CHECK_CODE_GROUP).joinToString(" ")
        }
    }
}
