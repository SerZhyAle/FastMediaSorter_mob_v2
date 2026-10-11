package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateChangedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateUntrustedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTunnelUnavailableException
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import java.io.Closeable
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Where the device token of each account entry lives: Keystore-backed, never the password (5.2). */
interface DeviceExchangeTokenStore {

    suspend fun token(entry: DeviceExchangeAccountEntry): String?

    /** False when the secret store refused to keep it, so the caller never believes a lost write. */
    suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean

    suspend fun forget(entry: DeviceExchangeAccountEntry)
}

/**
 * An authenticated stream: `hello` was answered with [welcome], so the larger frame limit of 7.1 holds in
 * both directions. Writes are serialized because the keepalive and the owner of the stream share it.
 */
class DeviceExchangeSession(
    val connection: ExchangeConnection,
    val welcome: DeviceExchangeWelcome,
) : Closeable {

    @Synchronized
    fun send(envelope: DeviceExchangeEnvelope) =
        DeviceExchangeFrameCodec.write(
            connection.output,
            envelope,
            DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED
        )

    /** The next envelope, or null at a clean end of the stream. */
    fun receive(): DeviceExchangeEnvelope? =
        DeviceExchangeFrameCodec.read(connection.input, DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)

    override fun close() = connection.close()
}

/**
 * The account client of contract DEVICE-EXCHANGE sections 5 and 7.2-7.5: enrollment, `hello`, and the
 * tunnel requests. It holds no state of its own - every call names the account entry it acts for, so the
 * entries of S4156 share one client and never one stream.
 *
 * Every call blocks on the network and belongs on an IO dispatcher.
 */
@Singleton
class DeviceExchangeAccountClient @Inject constructor(
    private val connector: ExchangeConnector,
    private val tokens: DeviceExchangeTokenStore,
) {

    /**
     * Enrolls [entry] with [credential] and keeps only the device token it gets back. Enrolling a `deviceId`
     * the account already has rotates its token (7.2), which is how a lost token is recovered.
     */
    suspend fun enroll(
        entry: DeviceExchangeAccountEntry,
        credential: DeviceExchangeCredential,
        enrollment: DeviceExchangeEnrollment,
    ): DeviceExchangeEnrollResult = try {
        val trust = ExchangeTrust.Verified(entry.pinnedCertificate)
        val answer = connector.connect(entry.host, entry.port, trust).use { stream ->
            DeviceExchangeFrameCodec.write(stream.output, enrollEnvelope(entry, credential, enrollment))
            DeviceExchangeFrameCodec.read(stream.input).also { answer ->
                // The enrolled stream is authenticated and would become the control stream; this one only enrolls.
                if (answer?.type == DeviceExchangeEnvelope.TYPE_ENROLLED) {
                    DeviceExchangeFrameCodec.write(
                        stream.output,
                        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE)
                    )
                }
            }
        }
        enrollAnswer(entry, answer)
    } catch (e: IOException) {
        connectFailure(e)
    }

    /**
     * Opens a stream authenticated by `hello`. A `device-revoked` refusal forgets the token: it is dead on
     * the server and only a new enrollment helps.
     *
     * @throws DeviceExchangeNotEnrolledException when [entry] holds no token.
     * @throws DeviceExchangeRefusedException when the server refused the token.
     * @throws IOException when the server cannot be reached or its certificate is refused.
     */
    suspend fun openAuthenticated(
        entry: DeviceExchangeAccountEntry,
        keepaliveSeconds: Int? = null
    ): DeviceExchangeSession {
        val token = tokens.token(entry) ?: throw DeviceExchangeNotEnrolledException()
        val connection = connector.connect(entry.host, entry.port, ExchangeTrust.Verified(entry.pinnedCertificate))
        val hello = helloEnvelope(entry, token, keepaliveSeconds)
        val answer = ask(connection, hello, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
        if (answer?.type == DeviceExchangeEnvelope.TYPE_WELCOME) {
            return DeviceExchangeSession(connection, DeviceExchangeWelcome.from(answer))
        }
        connection.close()
        throw helloFailure(entry, answer)
    }

    private suspend fun helloFailure(entry: DeviceExchangeAccountEntry, answer: DeviceExchangeEnvelope?): IOException {
        val reason = answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_REFUSED }
            ?.string(DeviceExchangeEnvelope.KEY_REASON)
        if (reason == DeviceExchangeEnvelope.REASON_DEVICE_REVOKED) tokens.forget(entry)
        return reason?.let(::DeviceExchangeRefusedException)
            ?: DeviceExchangeWireException("hello was not answered with welcome")
    }

    /** The producer's data stream for one `open` (7.5): `hello`, then `attach {tunnelId}`, then raw bytes. */
    suspend fun attach(entry: DeviceExchangeAccountEntry, tunnelId: String): ExchangeConnection {
        val session = openAuthenticated(entry)
        try {
            session.send(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ATTACH) { addProperty(KEY_TUNNEL_ID, tunnelId) }
            )
        } catch (e: IOException) {
            session.close()
            throw e
        }
        return session.connection
    }

    /** A tunnel to a resource of [entry]'s own account by its `resourceId` (7.5); raw bytes once it returns. */
    suspend fun connectByResourceId(entry: DeviceExchangeAccountEntry, resourceId: String): ExchangeConnection {
        val session = openAuthenticated(entry)
        val request = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECT) {
            addProperty(KEY_RESOURCE_ID, resourceId)
        }
        return requestTunnel(session.connection, request, DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
    }

    private suspend fun enrollAnswer(
        entry: DeviceExchangeAccountEntry,
        answer: DeviceExchangeEnvelope?,
    ): DeviceExchangeEnrollResult {
        val token = answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_ENROLLED }?.string(KEY_DEVICE_TOKEN)
        val reason = answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_REFUSED }
            ?.let { it.string(DeviceExchangeEnvelope.KEY_REASON) ?: REASON_UNKNOWN }
        return when {
            token != null -> if (tokens.save(entry, token)) {
                DeviceExchangeEnrollResult.Enrolled(DeviceExchangeWelcome.from(answer))
            } else {
                DeviceExchangeEnrollResult.Refused(DeviceExchangeEnrollResult.REASON_TOKEN_NOT_STORED)
            }
            reason != null -> DeviceExchangeEnrollResult.Refused(reason)
            else -> DeviceExchangeEnrollResult.Unreachable(DeviceExchangeWireException("enroll was not answered"))
        }
    }

    private fun connectFailure(failure: IOException): DeviceExchangeEnrollResult = when (failure) {
        is ExchangeCertificateUntrustedException -> DeviceExchangeEnrollResult.CertificateUntrusted(failure.presented)
        is ExchangeCertificateChangedException ->
            DeviceExchangeEnrollResult.CertificateChanged(failure.pinned, failure.presented)
        else -> DeviceExchangeEnrollResult.Unreachable(failure)
    }

    private fun enrollEnvelope(
        entry: DeviceExchangeAccountEntry,
        credential: DeviceExchangeCredential,
        enrollment: DeviceExchangeEnrollment,
    ) = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ENROLL) {
        addProperty(KEY_LOGIN, entry.login)
        when (credential) {
            is DeviceExchangeCredential.Password -> addProperty(KEY_PASSWORD, credential.value)
            is DeviceExchangeCredential.PairingCode -> addProperty(KEY_PAIRING_CODE, credential.value)
        }
        addProperty(KEY_DEVICE_ID, entry.deviceId)
        addProperty(KEY_DEVICE_NAME, enrollment.deviceName)
        addProperty(KEY_PRODUCT, SftpRendezvousDevice.PRODUCT_FMS_ANDROID)
        addProperty(KEY_PRODUCT_VERSION, enrollment.productVersion)
        addProperty(KEY_PLATFORM, SftpRendezvousDevice.PLATFORM_ANDROID)
        add(KEY_ROLES, JsonArray().apply { enrollment.roles.forEach(::add) })
        enrollment.receiver?.let { add(KEY_RECEIVER, it) }
    }

    private fun helloEnvelope(entry: DeviceExchangeAccountEntry, token: String, keepaliveSeconds: Int?) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_HELLO) {
            addProperty(KEY_DEVICE_ID, entry.deviceId)
            addProperty(KEY_DEVICE_TOKEN, token)
            addProperty(KEY_PRODUCT_VERSION, BuildConfig.VERSION_NAME)
            keepaliveSeconds?.let { addProperty(KEY_KEEPALIVE_SECONDS, it) }
        }

    companion object {
        const val KEY_TUNNEL_ID = "tunnelId"
        const val KEY_RESOURCE_ID = "resourceId"
        const val KEY_SHARE_ID = "shareId"
        const val KEY_KEEPALIVE_SECONDS = "keepaliveSeconds"
        const val REASON_UNKNOWN = "unknown"

        private const val KEY_LOGIN = "login"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PAIRING_CODE = "pairingCode"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_DEVICE_TOKEN = "deviceToken"
        private const val KEY_DEVICE_NAME = "deviceName"
        private const val KEY_PRODUCT = "product"
        private const val KEY_PRODUCT_VERSION = "productVersion"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_ROLES = "roles"
        private const val KEY_RECEIVER = "receiver"

        /**
         * An outsider tunnel by share id (5.4, 7.5): no `hello`, TLS only since 0.17 (item V). [trust] pins the
         * descriptor's `certFingerprint` when it states one, else the platform trust store decides.
         */
        fun connectByShareId(
            connector: ExchangeConnector,
            host: String,
            port: Int,
            shareId: String,
            trust: ExchangeTrust,
        ): ExchangeConnection {
            val connection = connector.connect(host, port, trust)
            val request = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECT) {
                addProperty(KEY_SHARE_ID, shareId)
            }
            return requestTunnel(connection, request, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
        }

        /** Sends `connect` on [connection] and hands it back as raw bytes once the server says `connected`. */
        private fun requestTunnel(
            connection: ExchangeConnection,
            request: DeviceExchangeEnvelope,
            maxBody: Int,
        ): ExchangeConnection {
            val answer = ask(connection, request, maxBody)
            if (answer?.type != DeviceExchangeEnvelope.TYPE_CONNECTED) {
                connection.close()
                throw ExchangeTunnelUnavailableException(answer?.string(DeviceExchangeEnvelope.KEY_REASON))
            }
            return connection
        }

        /** Writes [request] and reads the answer; a failed exchange closes [connection] before rethrowing. */
        private fun ask(connection: ExchangeConnection, request: DeviceExchangeEnvelope, maxBody: Int) = try {
            DeviceExchangeFrameCodec.write(connection.output, request, maxBody)
            DeviceExchangeFrameCodec.read(connection.input, maxBody)
        } catch (e: IOException) {
            connection.close()
            throw e
        }
    }
}
