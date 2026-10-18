package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateChangedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateUntrustedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTunnelUnavailableException
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeCredential
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollResult
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollment
import com.sza.fastmediasorter.domain.model.DeviceExchangeInvitation
import com.sza.fastmediasorter.domain.model.DeviceExchangePairingCode
import com.sza.fastmediasorter.domain.model.DeviceExchangeWelcome
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
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
    private val e2eKeys: DeviceExchangeE2eKeyStore,
    private val pins: ExchangePinRecorder,
) {

    /**
     * Enrolls [entry] with [credential] and keeps only the device token it gets back. Enrolling a `deviceId`
     * the account already has rotates its token (7.2), which is how a lost token is recovered.
     */
    suspend fun enroll(
        entry: DeviceExchangeAccountEntry,
        credential: DeviceExchangeCredential,
        enrollment: DeviceExchangeEnrollment,
    ): DeviceExchangeEnrollResult {
        Timber.d("Exchange enroll: credential=%s", credential.javaClass.simpleName)
        val key = e2eKeys.keyFor(entry.deviceId)
        return firstExchange(entry, enrollment, enrollEnvelope(entry, credential, enrollment, key))
    }

    /**
     * Creates the account and enrolls [entry] as its first device in one exchange (19 item AQ). The answer is
     * `enrolled` exactly as for [enroll], so the token is kept the same way; a stream closed with no answer is an
     * older server that may not take registration, and it is never retried as an `enroll`.
     */
    suspend fun register(
        entry: DeviceExchangeAccountEntry,
        password: DeviceExchangeCredential.Password,
        invitation: DeviceExchangeInvitation,
        enrollment: DeviceExchangeEnrollment,
    ): DeviceExchangeEnrollResult {
        Timber.d("Exchange register: invitation=%s", invitation.javaClass.simpleName)
        val key = e2eKeys.keyFor(entry.deviceId)
        return firstExchange(entry, enrollment, registerEnvelope(entry, password, invitation, enrollment, key))
    }

    private suspend fun firstExchange(
        entry: DeviceExchangeAccountEntry,
        enrollment: DeviceExchangeEnrollment,
        request: DeviceExchangeEnvelope,
    ): DeviceExchangeEnrollResult = try {
        val trust = entry.trust()
        Timber.d(
            "Exchange %s: %s:%d login=%s deviceId=%s pinned=%s roles=%s product=%s",
            request.type,
            entry.host,
            entry.port,
            entry.login,
            entry.deviceId,
            entry.pinnedCertificate,
            enrollment.roles,
            enrollment.productVersion,
        )
        val connection = connector.connect(entry.host, entry.port, trust)
        val answer = connection.use { stream ->
            // Never the password or the token: the envelope's type and size are enough to place a refusal.
            DeviceExchangeFrameCodec.write(stream.output, request)
            Timber.d("Exchange %s: envelope written, waiting for the answer", request.type)
            DeviceExchangeFrameCodec.read(stream.input).also { answer ->
                Timber.d(
                    "Exchange %s: answer type=%s reason=%s",
                    request.type,
                    answer?.type,
                    answer?.string(DeviceExchangeEnvelope.KEY_REASON),
                )
                // The enrolled stream is authenticated and would become the control stream; this one only enrolls.
                if (answer?.type == DeviceExchangeEnvelope.TYPE_ENROLLED) {
                    DeviceExchangeFrameCodec.write(
                        stream.output,
                        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE)
                    )
                }
            }
        }
        enrollAnswer(entry, answer).withCertificate(connection)
    } catch (e: IOException) {
        Timber.w(e, "Exchange %s failed with %s: %s", request.type, e.javaClass.simpleName, e.message)
        connectFailure(e)
    }

    /**
     * Opens a stream authenticated by `hello`. A `device-revoked` refusal forgets the token: it is dead on
     * the server and only a new enrollment helps. A `signed-out` refusal keeps it (19 item AT): the entry stays
     * and enrolls its own `deviceId` again, which rotates the token anyway. A welcomed stream of a pinned entry
     * writes back a renewed leaf or a missing key (19 item AY).
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
        val hello = helloEnvelope(entry, token, keepaliveSeconds)
        val (connection, answer) = closingOnCancel({ opened -> closeQuietly(opened.first) }) { adopt ->
            val connection = connector.connect(entry.host, entry.port, entry.trust(), adopt)
            connection to ask(connection, hello, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
        }
        if (answer?.type == DeviceExchangeEnvelope.TYPE_WELCOME) {
            return welcomed(entry, DeviceExchangeSession(connection, answer.toWelcome()))
        }
        connection.close()
        throw helloFailure(entry, answer)
    }

    /**
     * Runs the blocking [open] so that a cancelled caller closes the socket [open] adopted at once, whatever stage
     * it blocks in, and a result that completes after the cancellation goes to [release] instead of the caller.
     * The closer is a child of its own: the caller's thread is the one blocked, so only another worker can close.
     */
    private suspend fun <T> closingOnCancel(release: (T) -> Unit, open: ((Closeable) -> Unit) -> T): T {
        val slot = AtomicReference<Any?>(null)
        val opened = ArrayList<T>(1)
        try {
            coroutineScope {
                val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        claim(slot, CANCELLED)?.let(::closeQuietly)
                    }
                }
                val scope = coroutineContext.job
                try {
                    // The job turns inactive the moment it is cancelled, before the closer is dispatched to run.
                    opened += open { socket ->
                        if (!scope.isActive || !slot.compareAndSet(null, socket)) closeQuietly(socket)
                    }
                    claim(slot, HANDED_OVER)
                } finally {
                    closer.cancel()
                }
            }
            // A cancellation that landed while the stream was opening has closed it already.
            currentCoroutineContext().ensureActive()
            return opened.single()
        } catch (e: CancellationException) {
            opened.forEach(release)
            throw e
        } catch (e: IOException) {
            // A socket closed by cancellation fails its blocking call; that is the cancellation, not the server.
            currentCoroutineContext().ensureActive()
            throw e
        }
    }

    // Moves [slot] to [state] unless it is already final, answering the socket it held, if any.
    private fun claim(slot: AtomicReference<Any?>, state: Any): Closeable? {
        while (true) {
            val current = slot.get()
            if (current === CANCELLED || current === HANDED_OVER) return null
            if (slot.compareAndSet(current, state)) return current as? Closeable
        }
    }

    private fun closeQuietly(closeable: Closeable) {
        try {
            closeable.close()
        } catch (e: IOException) {
            Timber.d(e, "Exchange connect: closing an abandoned socket failed")
        }
    }

    private suspend fun welcomed(entry: DeviceExchangeAccountEntry, session: DeviceExchangeSession) = try {
        recordPin(entry, session.connection)
        session
    } catch (e: CancellationException) {
        session.close()
        throw e
    }

    // An entry's own pin carries its key (19 item AY); only it may renew, a descriptor's pin never does.
    private fun DeviceExchangeAccountEntry.trust() = ExchangeTrust.Verified(pinnedCertificate, pinnedKey)

    /**
     * Keeps what a pinned entry learned on [connection]: the leaf of a same-key renewal, which the connector only
     * accepted because the key matched, or the key of a pin saved before this build, which the leaf just matched.
     * A failed write costs one prompt-free renewal at most, so the stream is not dropped for it.
     */
    private suspend fun recordPin(entry: DeviceExchangeAccountEntry, connection: ExchangeConnection) {
        val leaf = connection.certificateFingerprint
        val key = connection.keyFingerprint ?: return
        val learned = entry.pinnedCertificate != null && (leaf != entry.pinnedCertificate || entry.pinnedKey == null)
        if (!learned) return
        Timber.d("S4257: phone keeps the learned pin of a pinned entry")
        try {
            pins.record(entry.identity, leaf, key)
        } catch (e: IOException) {
            Timber.w(e, "Exchange pin: the renewed certificate could not be kept")
        }
    }

    private fun DeviceExchangeEnrollResult.withCertificate(connection: ExchangeConnection) =
        if (this is DeviceExchangeEnrollResult.Enrolled) {
            copy(leafFingerprint = connection.certificateFingerprint, keyFingerprint = connection.keyFingerprint)
        } else {
            this
        }

    private suspend fun helloFailure(entry: DeviceExchangeAccountEntry, answer: DeviceExchangeEnvelope?): IOException {
        val reason = answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_REFUSED }
            ?.string(DeviceExchangeEnvelope.KEY_REASON)
        if (reason == DeviceExchangeEnvelope.REASON_DEVICE_REVOKED) tokens.forget(entry)
        return reason?.let(::DeviceExchangeRefusedException)
            ?: DeviceExchangeWireException("hello was not answered with welcome")
    }

    /**
     * Revokes [entry]'s own device (item AB): `hello`, then `revoke` with its own `deviceId`, then the stream is
     * closed without `bye`. 7.4 gives `revoke` no answer, so nothing is awaited beyond the flushed write; the
     * device's records leave the directory with it, so no `unpublish` is sent first.
     *
     * @throws IOException when the server cannot be reached, refuses the token or the write fails.
     */
    suspend fun revokeOwnDevice(entry: DeviceExchangeAccountEntry) {
        openAuthenticated(entry).use { session ->
            session.send(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_REVOKE) {
                    addProperty(KEY_DEVICE_ID, entry.deviceId)
                }
            )
        }
    }

    /**
     * Revokes another device of [entry]'s account (5.2, 7.4): any enrolled device may, and revoke is the only
     * recovery from a lost one. No answer exists; the device leaves the directory by a `changed` push.
     *
     * @throws IOException when the server cannot be reached, refuses the token or the write fails.
     */
    suspend fun revokeDevice(entry: DeviceExchangeAccountEntry, deviceId: String) {
        openAuthenticated(entry).use { session ->
            session.send(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_REVOKE) { addProperty(KEY_DEVICE_ID, deviceId) }
            )
        }
    }

    /**
     * Changes the account password with the old one (19 item AT) on a stream of its own. The server answers
     * `password-changed` with a fresh token for this device, which stays signed in; every other device of the
     * account is signed out by the same act.
     *
     * @throws DeviceExchangeRefusedException when the server refused it, or with
     * [DeviceExchangeEnrollResult.REASON_TOKEN_NOT_STORED] when the fresh token could not be kept.
     * @throws IOException when the server cannot be reached, refuses the token or answers anything else.
     */
    suspend fun changePassword(
        entry: DeviceExchangeAccountEntry,
        oldPassword: DeviceExchangeCredential.Password,
        newPassword: DeviceExchangeCredential.Password,
    ) {
        val token = openAuthenticated(entry).use { session ->
            session.send(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_PASSWORD_CHANGE) {
                    addProperty(KEY_OLD_PASSWORD, oldPassword.value)
                    addProperty(KEY_NEW_PASSWORD, newPassword.value)
                }
            )
            val answer = awaitAnswer(session, PASSWORD_ANSWERS)
            answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_PASSWORD_CHANGED }?.string(KEY_DEVICE_TOKEN)
                ?: throw answerFailure(answer, DeviceExchangeEnvelope.TYPE_PASSWORD_CHANGED)
        }
        if (!tokens.save(entry, token)) {
            throw DeviceExchangeRefusedException(DeviceExchangeEnrollResult.REASON_TOKEN_NOT_STORED)
        }
    }

    /**
     * Deletes the account with its password (19 item AU). On `account-deleted` the server has erased the account,
     * its devices, records, tokens and clipboard and closes every stream; forgetting the entry is the caller's.
     *
     * @throws DeviceExchangeRefusedException when the server refused it.
     * @throws IOException when the server cannot be reached, refuses the token or answers anything else.
     */
    suspend fun deleteAccount(entry: DeviceExchangeAccountEntry, password: DeviceExchangeCredential.Password) {
        openAuthenticated(entry).use { session ->
            session.send(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ACCOUNT_DELETE) {
                    addProperty(KEY_PASSWORD, password.value)
                }
            )
            val answer = awaitAnswer(session, DELETE_ANSWERS)
            if (answer?.type != DeviceExchangeEnvelope.TYPE_ACCOUNT_DELETED) {
                throw answerFailure(answer, DeviceExchangeEnvelope.TYPE_ACCOUNT_DELETED)
            }
        }
    }

    private fun answerFailure(answer: DeviceExchangeEnvelope?, expected: String): IOException =
        answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_REFUSED }
            ?.let { DeviceExchangeRefusedException(it.string(DeviceExchangeEnvelope.KEY_REASON) ?: REASON_UNKNOWN) }
            ?: DeviceExchangeWireException("the request was not answered with $expected")

    /**
     * The account's whole directory (7.4) on a stream of its own, which replaces a producer's control stream for
     * a moment exactly as [requestPairingCode] does.
     *
     * @throws DeviceExchangeRefusedException when the server refused the request.
     * @throws IOException when the server cannot be reached, refuses the token or answers anything else.
     */
    suspend fun listDirectory(entry: DeviceExchangeAccountEntry): DeviceExchangeDirectory =
        openAuthenticated(entry).use { session ->
            session.send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_LIST))
            val answer = awaitAnswer(session, DIRECTORY_ANSWERS)
            when (answer?.type) {
                DeviceExchangeEnvelope.TYPE_DIRECTORY -> DeviceExchangeDirectory.from(answer)
                DeviceExchangeEnvelope.TYPE_REFUSED -> throw DeviceExchangeRefusedException(
                    answer.string(DeviceExchangeEnvelope.KEY_REASON) ?: REASON_UNKNOWN
                )
                else -> throw DeviceExchangeWireException("list was not answered with directory")
            }
        }

    /**
     * Asks [entry]'s server for a one-time pairing code (5.3, 7.4) on a stream of its own. That stream becomes the
     * device's control stream and so replaces a producer's for a moment (7.3); it closes without `bye`, and the
     * tunnel manager's reconnect loop takes the control stream back with the published resource carried over.
     *
     * @throws DeviceExchangeRefusedException when the server refused the request.
     * @throws IOException when the server cannot be reached, refuses the token or answers anything else.
     */
    suspend fun requestPairingCode(entry: DeviceExchangeAccountEntry): DeviceExchangePairingCode =
        openAuthenticated(entry).use { session ->
            session.send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_PAIRING_REQUEST))
            pairingAnswer(awaitAnswer(session, PAIRING_ANSWERS))
        }

    // The new control stream may carry a push meant for the one it replaced; only the answer counts.
    private fun awaitAnswer(session: DeviceExchangeSession, answers: Set<String>): DeviceExchangeEnvelope? {
        var answer = session.receive()
        var skipped = 0
        while (answer != null && answer.type !in answers && skipped < MAX_UNRELATED_ENVELOPES) {
            answer = session.receive()
            skipped++
        }
        return answer
    }

    private fun pairingAnswer(answer: DeviceExchangeEnvelope?): DeviceExchangePairingCode {
        val code = answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_PAIRING_CODE }?.string(KEY_CODE)
        val reason = answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_REFUSED }
            ?.let { it.string(DeviceExchangeEnvelope.KEY_REASON) ?: REASON_UNKNOWN }
        return when {
            code != null -> DeviceExchangePairingCode(code, answer.string(KEY_EXPIRES_AT))
            reason != null -> throw DeviceExchangeRefusedException(reason)
            else -> throw DeviceExchangeWireException("pairing-request was not answered with pairing-code")
        }
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
                DeviceExchangeEnrollResult.Enrolled(answer.toWelcome())
            } else {
                DeviceExchangeEnrollResult.Refused(DeviceExchangeEnrollResult.REASON_TOKEN_NOT_STORED)
            }
            reason != null -> DeviceExchangeEnrollResult.Refused(reason)
            else -> {
                Timber.w("Exchange enroll: no enrolled or refused answer (answer type=%s)", answer?.type)
                DeviceExchangeEnrollResult.NoAnswer(DeviceExchangeWireException("enroll was not answered"))
            }
        }
    }

    private fun connectFailure(failure: IOException): DeviceExchangeEnrollResult = when (failure) {
        is ExchangeCertificateUntrustedException -> DeviceExchangeEnrollResult.CertificateUntrusted(failure.presented)
        is ExchangeCertificateChangedException ->
            DeviceExchangeEnrollResult.CertificateChanged(failure.pinned, failure.presented)
        // A frame that breaks 7.1 after our own envelope went out is the server answering badly, not a host
        // that could not be reached (18 item AN).
        is DeviceExchangeWireException -> DeviceExchangeEnrollResult.NoAnswer(failure)
        else -> DeviceExchangeEnrollResult.Unreachable(failure)
    }

    private fun enrollEnvelope(
        entry: DeviceExchangeAccountEntry,
        credential: DeviceExchangeCredential,
        enrollment: DeviceExchangeEnrollment,
        key: E2eDeviceKey?,
    ) = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ENROLL) {
        addProperty(KEY_LOGIN, entry.login)
        when (credential) {
            is DeviceExchangeCredential.Password -> addProperty(KEY_PASSWORD, credential.value)
            is DeviceExchangeCredential.PairingCode -> addProperty(KEY_PAIRING_CODE, credential.value)
        }
        addDeviceMembers(entry, enrollment, key)
    }

    // `register` carries every device member of `enroll` beside the account's own (19 item AQ).
    private fun registerEnvelope(
        entry: DeviceExchangeAccountEntry,
        password: DeviceExchangeCredential.Password,
        invitation: DeviceExchangeInvitation,
        enrollment: DeviceExchangeEnrollment,
        key: E2eDeviceKey?,
    ) = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_REGISTER) {
        addProperty(KEY_LOGIN, entry.login)
        addProperty(KEY_PASSWORD, password.value)
        when (invitation) {
            is DeviceExchangeInvitation.OneTime -> addProperty(KEY_INVITATION, invitation.value)
            is DeviceExchangeInvitation.SharedPassword -> addProperty(KEY_INVITATION_PASSWORD, invitation.value)
        }
        addDeviceMembers(entry, enrollment, key)
    }

    // A device without a key declares neither `e2eKey` nor `e2e-v1` (item DF), so it is never a recipient.
    private fun JsonObject.addDeviceMembers(
        entry: DeviceExchangeAccountEntry,
        enrollment: DeviceExchangeEnrollment,
        key: E2eDeviceKey?,
    ) {
        addProperty(KEY_DEVICE_ID, entry.deviceId)
        addProperty(KEY_DEVICE_NAME, enrollment.deviceName)
        addProperty(KEY_PRODUCT, SftpRendezvousDevice.PRODUCT_FMS_ANDROID)
        addProperty(KEY_PRODUCT_VERSION, enrollment.productVersion)
        addProperty(KEY_PLATFORM, SftpRendezvousDevice.PLATFORM_ANDROID)
        add(KEY_ROLES, JsonArray().apply { enrollment.roles.forEach(::add) })
        val capabilities = enrollment.capabilities + listOfNotNull(key?.let { E2eDeviceKey.CAPABILITY })
        if (capabilities.isNotEmpty()) {
            add(KEY_CAPABILITIES, JsonArray().apply { capabilities.distinct().forEach(::add) })
        }
        key?.let { add(E2eDeviceKey.KEY_E2E_KEY, it.toWire()) }
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
        const val KEY_BROADCAST_ID = "broadcastId"
        const val KEY_SCHEME = "scheme"
        const val KEY_KEEPALIVE_SECONDS = "keepaliveSeconds"
        const val REASON_UNKNOWN = "unknown"

        private const val KEY_LOGIN = "login"
        private const val KEY_PASSWORD = "password"
        private const val KEY_OLD_PASSWORD = "oldPassword"
        private const val KEY_NEW_PASSWORD = "newPassword"
        private const val KEY_INVITATION = "invitation"
        private const val KEY_INVITATION_PASSWORD = "invitationPassword"
        private const val KEY_PAIRING_CODE = "pairingCode"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_DEVICE_TOKEN = "deviceToken"
        private const val KEY_DEVICE_NAME = "deviceName"
        private const val KEY_PRODUCT = "product"
        private const val KEY_PRODUCT_VERSION = "productVersion"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_ROLES = "roles"
        private const val KEY_CAPABILITIES = "capabilities"
        private const val KEY_RECEIVER = "receiver"
        private const val KEY_CODE = "code"
        private const val KEY_EXPIRES_AT = "expiresAt"
        private const val MAX_UNRELATED_ENVELOPES = 8
        private const val INITIAL_ANSWER_TIMEOUT_MS = 15_000
        private val CANCELLED = Any()
        private val HANDED_OVER = Any()
        private val PAIRING_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_PAIRING_CODE, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val DIRECTORY_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_DIRECTORY, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val PASSWORD_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_PASSWORD_CHANGED, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val DELETE_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_ACCOUNT_DELETED, DeviceExchangeEnvelope.TYPE_REFUSED)

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

        /**
         * An outsider tunnel to a live broadcast (7.5): `connect {broadcastId, scheme}`, no `hello`, TLS only
         * (item V). Any deployment a descriptor names is reachable this way with no entry for it (item AF);
         * [trust] carries the endpoint's `certFingerprint`, else the platform trust store decides.
         */
        fun connectByBroadcastId(
            connector: ExchangeConnector,
            host: String,
            port: Int,
            broadcastId: String,
            scheme: String,
            trust: ExchangeTrust,
        ): ExchangeConnection {
            val connection = connector.connect(host, port, trust)
            val request = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECT) {
                addProperty(KEY_BROADCAST_ID, broadcastId)
                addProperty(KEY_SCHEME, scheme)
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

        /**
         * Writes [request] and reads the answer; a failed exchange closes [connection] before rethrowing. Only this
         * first answer is bounded: the stream it opens may then stay quiet for as long as its owner keeps it.
         */
        private fun ask(connection: ExchangeConnection, request: DeviceExchangeEnvelope, maxBody: Int) = try {
            val socket = connection.socket
            val longLivedTimeout = socket.soTimeout
            socket.soTimeout = INITIAL_ANSWER_TIMEOUT_MS
            DeviceExchangeFrameCodec.write(connection.output, request, maxBody)
            DeviceExchangeFrameCodec.read(connection.input, maxBody).also { socket.soTimeout = longLivedTimeout }
        } catch (e: IOException) {
            connection.close()
            throw e
        }
    }
}
