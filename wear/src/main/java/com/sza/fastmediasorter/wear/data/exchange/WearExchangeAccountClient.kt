package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** How the watch describes itself in `enroll` (contract DEVICE-EXCHANGE 6.1 and 7.2). */
data class WearExchangeDeviceProfile(val deviceName: String, val productVersion: String)

/**
 * S4158: the two exchanges the watch has with a server in this version - `enroll` with a pairing code (5.3) and
 * the revocation of its own device when an entry is removed (item AB). Neither keeps a stream open: the control
 * streams of the watch come with S4116 phase 08. Every call blocks on the network and belongs on an IO dispatcher.
 */
@Singleton
class WearExchangeAccountClient @Inject constructor(
    private val connector: WearExchangeConnector,
) {

    /**
     * Enrolls [entry] with [pairingCode] and hands the token back unstored. Roles are declared ahead of use, as
     * item AC asks, so receiving and broadcasting audio later needs no second code.
     */
    fun enroll(
        entry: WearExchangeEntry,
        pairingCode: String,
        profile: WearExchangeDeviceProfile,
    ): WearExchangeEnrollResult = try {
        val answer = connector.connect(entry.host, entry.port, entry.pinnedCertificate).use { stream ->
            WearExchangeFrameCodec.write(stream.output, enrollEnvelope(entry, pairingCode, profile))
            WearExchangeFrameCodec.read(stream.input).also { answer ->
                // The enrolled stream is authenticated and would become the control stream; this one only enrolls.
                if (answer?.type == WearExchangeEnvelope.TYPE_ENROLLED) {
                    WearExchangeFrameCodec.write(stream.output, WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_BYE))
                }
            }
        }
        enrollAnswer(answer)
    } catch (e: IOException) {
        connectFailure(e)
    }

    private fun connectFailure(failure: IOException): WearExchangeEnrollResult = when (failure) {
        is WearExchangeCertificateUntrustedException -> WearExchangeEnrollResult.CertificateUntrusted
        is WearExchangeCertificateChangedException -> WearExchangeEnrollResult.CertificateChanged
        else -> WearExchangeEnrollResult.Unreachable(failure)
    }

    /**
     * `hello`, then `revoke` with the entry's own `deviceId`, then the stream closes without `bye`; 7.4 gives
     * `revoke` no answer.
     *
     * @throws IOException when the server cannot be reached, refuses the token or the write fails.
     */
    fun revokeOwnDevice(entry: WearExchangeEntry, token: String, productVersion: String) {
        connector.connect(entry.host, entry.port, entry.pinnedCertificate).use { stream ->
            WearExchangeFrameCodec.write(stream.output, helloEnvelope(entry, token, productVersion))
            val answer = WearExchangeFrameCodec.read(stream.input)
            if (answer?.type != WearExchangeEnvelope.TYPE_WELCOME) {
                throw WearExchangeWireException("hello was not answered with welcome")
            }
            WearExchangeFrameCodec.write(
                stream.output,
                WearExchangeEnvelope.of(
                    WearExchangeEnvelope.TYPE_REVOKE
                ) { addProperty(KEY_DEVICE_ID, entry.deviceId) },
            )
        }
    }

    private fun enrollAnswer(answer: WearExchangeEnvelope?): WearExchangeEnrollResult {
        val token = answer?.takeIf { it.type == WearExchangeEnvelope.TYPE_ENROLLED }?.string(KEY_DEVICE_TOKEN)
        val reason = answer?.takeIf { it.type == WearExchangeEnvelope.TYPE_REFUSED }
            ?.let { it.string(WearExchangeEnvelope.KEY_REASON) ?: WearExchangeEnrollResult.REASON_UNKNOWN }
        return when {
            token != null -> WearExchangeEnrollResult.Enrolled(token)
            reason != null -> WearExchangeEnrollResult.Refused(reason)
            else -> WearExchangeEnrollResult.Unreachable(WearExchangeWireException("enroll was not answered"))
        }
    }

    private fun enrollEnvelope(entry: WearExchangeEntry, pairingCode: String, profile: WearExchangeDeviceProfile) =
        WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_ENROLL) {
            addProperty(KEY_LOGIN, entry.login)
            addProperty(KEY_PAIRING_CODE, pairingCode)
            addProperty(KEY_DEVICE_ID, entry.deviceId)
            addProperty(KEY_DEVICE_NAME, profile.deviceName)
            addProperty(KEY_PRODUCT, PRODUCT_FMS_WEAR)
            addProperty(KEY_PRODUCT_VERSION, profile.productVersion)
            addProperty(KEY_PLATFORM, PLATFORM_ANDROID)
            add(KEY_ROLES, JsonArray().apply { ROLES.forEach(::add) })
            add(KEY_RECEIVER, receiver())
        }

    // Contract section 3: the watch plays AUDIO_ONLY, and S4116 phase 08 receives it from RELAY.
    private fun receiver() = JsonObject().apply {
        add(KEY_MODES, JsonArray().apply { add(MODE_AUDIO_ONLY) })
        add(KEY_TRANSPORTS, JsonArray().apply { add(TRANSPORT_RELAY) })
    }

    private fun helloEnvelope(entry: WearExchangeEntry, token: String, productVersion: String) =
        WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_HELLO) {
            addProperty(KEY_DEVICE_ID, entry.deviceId)
            addProperty(KEY_DEVICE_TOKEN, token)
            addProperty(KEY_PRODUCT_VERSION, productVersion)
        }

    companion object {
        const val PRODUCT_FMS_WEAR = "fms-wear"
        const val ROLE_BROADCASTER = "broadcaster"
        const val ROLE_RECEIVER = "receiver"

        private const val PLATFORM_ANDROID = "android"
        private const val MODE_AUDIO_ONLY = "AUDIO_ONLY"
        private const val TRANSPORT_RELAY = "RELAY"
        private val ROLES = listOf(ROLE_BROADCASTER, ROLE_RECEIVER)

        private const val KEY_LOGIN = "login"
        private const val KEY_PAIRING_CODE = "pairingCode"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_DEVICE_TOKEN = "deviceToken"
        private const val KEY_DEVICE_NAME = "deviceName"
        private const val KEY_PRODUCT = "product"
        private const val KEY_PRODUCT_VERSION = "productVersion"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_ROLES = "roles"
        private const val KEY_RECEIVER = "receiver"
        private const val KEY_MODES = "modes"
        private const val KEY_TRANSPORTS = "transports"
    }
}
