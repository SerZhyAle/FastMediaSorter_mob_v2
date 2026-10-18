package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One administration stream of contract DEVICE-EXCHANGE 1.4 `REMOTE-ADMIN.md` `native-administration-stream`. The
 * token lives here and nowhere else; closing the session ends it. Every call blocks on the network and belongs on
 * an IO dispatcher, and a cancelled call closes the socket at once.
 */
class DeviceExchangeAdminSession internal constructor(
    private val connection: ExchangeConnection,
    private val token: String,
) : Closeable {

    private val nextId = AtomicLong(1)
    private val closed = AtomicBoolean(false)

    /**
     * Sends one `admin-request` and returns its `data`.
     *
     * @throws DeviceExchangeAdminReplyException on `ok:false`.
     * @throws DeviceExchangeAdminSessionLapsedException when the server refused the token or ended the stream.
     * @throws IOException when the stream failed.
     */
    suspend fun call(op: String, members: JsonObject.() -> Unit = {}): JsonElement? {
        val id = nextId.getAndIncrement()
        val request = DeviceExchangeAdminCodec.request(id, token, op, members)
        // The op and the size are enough to place a failure; a request may carry a password.
        Timber.d("Exchange admin %s: request id=%d", op, id)
        val reply = closingOnCancel {
            DeviceExchangeFrameCodec.write(connection.output, request, MAX_BODY)
            awaitReply(id)
        }
        Timber.d("Exchange admin %s: reply ok=%s", op, reply is DeviceExchangeAdminReply.Ok)
        return when (reply) {
            is DeviceExchangeAdminReply.Ok -> reply.data
            is DeviceExchangeAdminReply.Failed -> throw DeviceExchangeAdminReplyException(reply.code)
        }
    }

    private fun awaitReply(id: Long): DeviceExchangeAdminReply {
        repeat(MAX_UNRELATED_ENVELOPES) {
            val envelope = DeviceExchangeFrameCodec.read(connection.input, MAX_BODY)
            if (envelope == null || envelope.type == DeviceExchangeEnvelope.TYPE_REFUSED) {
                throw DeviceExchangeAdminSessionLapsedException()
            }
            if (envelope.type == DeviceExchangeEnvelope.TYPE_ADMIN_REPLY) {
                val reply = DeviceExchangeAdminCodec.readReply(envelope)
                if (reply.id == id) return reply
            }
        }
        throw DeviceExchangeWireException("admin-request was not answered")
    }

    private suspend fun <T> closingOnCancel(block: () -> T): T = coroutineScope {
        val done = AtomicBoolean(false)
        val closer = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!done.get()) close()
            }
        }
        try {
            block()
        } finally {
            done.set(true)
            closer.cancel()
        }
    }

    /** Ends the stream with `bye` when it still can; the socket closes either way. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            DeviceExchangeFrameCodec.write(
                connection.output,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE),
                DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
            )
        } catch (e: IOException) {
            Timber.d(e, "Exchange admin: bye was not written")
        }
        ExchangeStreamOpening.closeQuietly(connection)
    }

    private companion object {
        const val MAX_UNRELATED_ENVELOPES = 8
        const val MAX_BODY = DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED
    }
}

/**
 * The administration client of contract DEVICE-EXCHANGE 1.4 (strategic ADR-1 of S4356): it takes only the address
 * and the pin of an entry, never its device token, and keeps nothing of the sign-in.
 */
@Singleton
class DeviceExchangeAdminClient @Inject constructor(
    private val connector: ExchangeConnector,
) {

    /**
     * Signs in with the operator [password], which is cleared once written. The password goes out only on a stream
     * whose leaf passed the entry's trust, and a refused sign-in is never repeated here.
     *
     * @throws DeviceExchangeRefusedException when the server refused the sign-in.
     * @throws DeviceExchangeWireException when the server closed without an answer (`unanswered`).
     * @throws IOException when the server cannot be reached or its certificate is refused.
     */
    suspend fun signIn(entry: DeviceExchangeAccountEntry, password: CharArray): DeviceExchangeAdminSession {
        val auth = DeviceExchangeAdminCodec.authEnvelope(password)
        password.fill(CLEARED)
        val trust = ExchangeTrust.Verified(entry.pinnedCertificate, entry.pinnedKey)
        Timber.d("Exchange admin sign-in: %s:%d pinned=%s", entry.host, entry.port, entry.pinnedCertificate != null)
        val release: (Pair<ExchangeConnection, DeviceExchangeEnvelope?>) -> Unit = { opened ->
            ExchangeStreamOpening.closeQuietly(opened.first)
        }
        val (connection, answer) = ExchangeStreamOpening.closingOnCancel(release) { adopt ->
            val connection = connector.connect(entry.host, entry.port, trust, adopt)
            connection to firstAnswer(connection, auth)
        }
        Timber.d("Exchange admin sign-in: answer type=%s", answer?.type)
        if (answer?.type == DeviceExchangeEnvelope.TYPE_ADMIN_AUTH_OK) {
            return try {
                connection.socket.soTimeout = REPLY_TIMEOUT_MS
                DeviceExchangeAdminSession(connection, DeviceExchangeAdminCodec.readToken(answer))
            } catch (e: IOException) {
                connection.close()
                throw e
            }
        }
        connection.close()
        throw answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_REFUSED }
            ?.let { DeviceExchangeRefusedException(it.string(DeviceExchangeEnvelope.KEY_REASON) ?: REASON_UNKNOWN) }
            ?: DeviceExchangeWireException("admin-auth was not answered")
    }

    private fun firstAnswer(connection: ExchangeConnection, auth: DeviceExchangeEnvelope) = try {
        connection.socket.soTimeout = REPLY_TIMEOUT_MS
        DeviceExchangeFrameCodec.write(connection.output, auth, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
        DeviceExchangeFrameCodec.read(connection.input, DeviceExchangeFrameCodec.MAX_FRAME_BODY)
    } catch (e: IOException) {
        connection.close()
        throw e
    }

    private companion object {
        const val REPLY_TIMEOUT_MS = 15_000
        const val REASON_UNKNOWN = "unknown"
        const val CLEARED = '\u0000'
    }
}
