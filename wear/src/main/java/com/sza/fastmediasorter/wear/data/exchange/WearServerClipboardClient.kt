package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardFailure
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardOffer
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/** S4227: what `clipboard-info` answered for one entry (contract DEVICE-EXCHANGE 22 item BY). */
sealed interface WearServerClipboardInfo {
    /** [value] is null when the clipboard on that server is empty. */
    data class Answered(val value: WearServerClipboardValue?) : WearServerClipboardInfo

    data class Failed(val failure: WearServerClipboardFailure) : WearServerClipboardInfo
}

/** S4227: how one `clipboard-put` ended (22 item BW); only [Stored] means the server holds the value. */
sealed interface WearServerClipboardPut {
    data object Stored : WearServerClipboardPut

    data class Failed(val failure: WearServerClipboardFailure) : WearServerClipboardPut
}

/** S4227: how one `clipboard-get` ended (22 item BY). */
sealed interface WearServerClipboardGet {
    /** Every byte arrived and matched `size` and `sha256`; the sink may now be committed. */
    data class Received(val value: WearServerClipboardValue) : WearServerClipboardGet

    data object Empty : WearServerClipboardGet

    /** The caller gave no sink for this value (no space on the watch); no byte was read. */
    data class Declined(val value: WearServerClipboardValue) : WearServerClipboardGet

    data class Failed(val failure: WearServerClipboardFailure) : WearServerClipboardGet
}

/**
 * S4227: the clipboard wire of contract DEVICE-EXCHANGE 22 for one entry - a `clipboard-info` question on a
 * short control stream, and `clipboard-put` / `clipboard-get` on data streams that switch to raw bytes after one
 * envelope. A value reaches gigabytes, so it streams through in pieces and never sits in memory.
 *
 * Every call checks for cancellation between pieces, and a cancel closes the socket under a blocked read, so
 * a cancelled send leaves the server's previous value (22 item BX) and a cancelled get leaves a sink the caller
 * discards. Nothing here logs a name, a hash or a clip id (22 item CC).
 */
@Singleton
class WearServerClipboardClient @Inject constructor(
    private val opener: WearExchangeSessionOpener,
) {

    suspend fun info(entry: WearExchangeEntry, network: Network?): WearServerClipboardInfo =
        withContext(Dispatchers.IO) {
            attempt({ WearServerClipboardInfo.Failed(it) }) {
                opener.open(entry, WearExchangeKeepalive.KEEPALIVE_SECONDS, INFO_READ_TIMEOUT_MS, network)
                    .use { session -> session.closingOnCancel { askInfo(session) } }
            }
        }

    suspend fun put(
        entry: WearExchangeEntry,
        offer: WearServerClipboardOffer,
        source: InputStream,
        network: Network?,
        onProgress: (Long) -> Unit,
    ): WearServerClipboardPut = withContext(Dispatchers.IO) {
        attempt({ WearServerClipboardPut.Failed(it) }) {
            opener.open(entry, null, DATA_READ_TIMEOUT_MS, network).use { session ->
                session.closingOnCancel { sendValue(session, offer, source, onProgress) }
            }
        }
    }

    /**
     * [prepareSink] is asked once the server described the value and before a single byte is read; null
     * declines it. The sink is never committed or discarded here - the caller decides from the result.
     */
    suspend fun get(
        entry: WearExchangeEntry,
        clipId: String?,
        network: Network?,
        prepareSink: suspend (WearServerClipboardValue) -> OutputStream?,
        onProgress: (Long) -> Unit,
    ): WearServerClipboardGet = withContext(Dispatchers.IO) {
        attempt({ WearServerClipboardGet.Failed(it) }) {
            opener.open(entry, null, DATA_READ_TIMEOUT_MS, network).use { session ->
                session.closingOnCancel { receiveValue(session, clipId, prepareSink, onProgress) }
            }
        }
    }

    private fun askInfo(session: WearExchangeSession): WearServerClipboardInfo {
        val refusal = featureRefusal(session.welcome, write = false)
        if (refusal != null) return WearServerClipboardInfo.Failed(refusal)
        session.send(WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_INFO))
        val state = awaitAnswer(session, WearExchangeEnvelope.TYPE_CLIPBOARD_STATE)
        sendByeQuietly(session)
        return when (state.type) {
            WearExchangeEnvelope.TYPE_CLIPBOARD_STATE -> WearServerClipboardInfo.Answered(
                state.obj(KEY_VALUE)?.let { valueOf(WearExchangeEnvelope(state.type, it)) }
            )
            else -> WearServerClipboardInfo.Failed(refusalOf(state))
        }
    }

    private suspend fun sendValue(
        session: WearExchangeSession,
        offer: WearServerClipboardOffer,
        source: InputStream,
        onProgress: (Long) -> Unit,
    ): WearServerClipboardPut {
        val refusal = featureRefusal(session.welcome, write = true)
            ?: WearServerClipboardFailure.SOURCE_UNREADABLE.takeUnless { offer.validate() }
        if (refusal != null) return WearServerClipboardPut.Failed(refusal)
        session.send(putEnvelope(offer))
        val ready = awaitAnswer(session, WearExchangeEnvelope.TYPE_CLIPBOARD_READY)
        val failure = when (ready.type) {
            WearExchangeEnvelope.TYPE_CLIPBOARD_READY -> {
                copyExactly(source, session.connection.output, offer.size, null, onProgress)
                val stored = awaitAnswer(session, WearExchangeEnvelope.TYPE_CLIPBOARD_STORED)
                if (stored.type == WearExchangeEnvelope.TYPE_CLIPBOARD_STORED) null else refusalOf(stored)
            }
            else -> refusalOf(ready)
        }
        return failure?.let { WearServerClipboardPut.Failed(it) } ?: WearServerClipboardPut.Stored
    }

    private suspend fun receiveValue(
        session: WearExchangeSession,
        clipId: String?,
        prepareSink: suspend (WearServerClipboardValue) -> OutputStream?,
        onProgress: (Long) -> Unit,
    ): WearServerClipboardGet {
        val refusal = featureRefusal(session.welcome, write = false)
        if (refusal != null) return WearServerClipboardGet.Failed(refusal)
        session.send(
            WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_GET) {
                clipId?.let { addProperty(KEY_CLIP_ID, it) }
            }
        )
        val answer = session.receive()
        val value = answer?.takeIf { it.type == WearExchangeEnvelope.TYPE_CLIPBOARD_VALUE }?.let(::valueOf)
        return when {
            answer?.type == WearExchangeEnvelope.TYPE_CLIPBOARD_EMPTY -> WearServerClipboardGet.Empty
            value == null -> WearServerClipboardGet.Failed(answer?.let(::refusalOf) ?: BROKEN)
            else -> readValue(session, value, prepareSink, onProgress)
        }
    }

    private suspend fun readValue(
        session: WearExchangeSession,
        value: WearServerClipboardValue,
        prepareSink: suspend (WearServerClipboardValue) -> OutputStream?,
        onProgress: (Long) -> Unit,
    ): WearServerClipboardGet {
        val sink = prepareSink(value) ?: return WearServerClipboardGet.Declined(value)
        val digest = MessageDigest.getInstance("SHA-256")
        copyExactly(session.connection.input, sink, value.size, digest, onProgress)
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return if (actual == value.sha256.lowercase()) {
            WearServerClipboardGet.Received(value)
        } else {
            WearServerClipboardGet.Failed(WearServerClipboardFailure.HASH_MISMATCH)
        }
    }

    /**
     * Copies exactly [size] bytes, hashing them on the way when [digest] is given. A source that ends early
     * on the watch is [SourceUnreadableException]; a socket that ends early is the transfer breaking.
     */
    private suspend fun copyExactly(
        from: InputStream,
        to: OutputStream,
        size: Long,
        digest: MessageDigest?,
        onProgress: (Long) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_BYTES)
        var copied = 0L
        while (copied < size) {
            currentCoroutineContext().ensureActive()
            val wanted = minOf(BUFFER_BYTES.toLong(), size - copied).toInt()
            val read = from.read(buffer, 0, wanted)
            if (read < 0) throw endedEarly(isSink = digest != null)
            writeOrFail(to, buffer, read, isSink = digest != null)
            digest?.update(buffer, 0, read)
            copied += read
            onProgress(copied)
        }
        to.flush()
    }

    private fun endedEarly(isSink: Boolean): IOException =
        if (isSink) WearExchangeWireException("value ended early") else SourceUnreadableException()

    private fun writeOrFail(to: OutputStream, buffer: ByteArray, count: Int, isSink: Boolean) {
        try {
            to.write(buffer, 0, count)
        } catch (e: IOException) {
            // A full or failing disk on the watch is not the server's fault and must not read as one.
            throw if (isSink) SinkWriteException(e) else e
        }
    }

    /** Reads past keepalives and change notices until [expected] or anything that ends the exchange. */
    private fun awaitAnswer(session: WearExchangeSession, expected: String): WearExchangeEnvelope {
        while (true) {
            val answer = session.receive() ?: throw WearExchangeWireException("stream closed before $expected")
            if (answer.type !in PASSING_NOTICES) return answer
        }
    }

    private fun featureRefusal(welcome: WearExchangeWelcome, write: Boolean): WearServerClipboardFailure? = when {
        WearExchangeEntry.FEATURE_CLIPBOARD !in welcome.features -> WearServerClipboardFailure.NO_CLIPBOARD_FEATURE
        write && welcome.accountKind == WearExchangeEntry.ACCOUNT_KIND_GUEST -> WearServerClipboardFailure.NOT_PERMITTED
        else -> null
    }

    private fun refusalOf(answer: WearExchangeEnvelope): WearServerClipboardFailure =
        if (answer.type == WearExchangeEnvelope.TYPE_REFUSED) {
            WearServerClipboardFailure.fromReason(answer.string(WearExchangeEnvelope.KEY_REASON))
        } else {
            BROKEN
        }

    private fun putEnvelope(offer: WearServerClipboardOffer) =
        WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_PUT) {
            addProperty(KEY_KIND, offer.kind.wire)
            addProperty(KEY_SIZE, offer.size)
            offer.mimeType?.let { addProperty(KEY_MIME_TYPE, it) }
            offer.name?.let { addProperty(KEY_NAME, it) }
        }

    private fun sendByeQuietly(session: WearExchangeSession) {
        try {
            session.send(WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_BYE))
        } catch (e: IOException) {
            // The answer is already in hand; a server that closed first leaves nothing to say goodbye to.
            Timber.i("Clipboard info: bye not sent (%s)", e.javaClass.simpleName)
        }
    }

    // Closes the socket the moment the caller is cancelled, which is what unblocks a read in progress.
    private suspend fun <T> WearExchangeSession.closingOnCancel(block: suspend () -> T): T = coroutineScope {
        val closer = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                close()
            }
        }
        try {
            block()
        } finally {
            closer.cancel()
        }
    }

    private suspend fun <R> attempt(fail: (WearServerClipboardFailure) -> R, block: suspend () -> R): R = try {
        block()
    } catch (e: IOException) {
        // A cancel surfaces as a closed socket; it must stay a cancellation, never become a named failure.
        currentCoroutineContext().ensureActive()
        Timber.w("Clipboard on the server: %s", e.javaClass.simpleName)
        fail(failureOf(e))
    }

    class SourceUnreadableException : IOException("the file on the watch ended before its declared size")

    class SinkWriteException(cause: IOException) : IOException("the value could not be written on the watch", cause)

    companion object {
        private const val BUFFER_BYTES = 64 * 1024
        private const val INFO_READ_TIMEOUT_MS = WearExchangeConnector.ENROLL_READ_TIMEOUT_MS

        // Above the server's own 60-second silence bound (22 item BW), so the server is the one that gives up.
        private const val DATA_READ_TIMEOUT_MS = 75_000

        private val BROKEN = WearServerClipboardFailure.TRANSFER_BROKEN
        private val PASSING_NOTICES = setOf(WearExchangeEnvelope.TYPE_KEEPALIVE, WearExchangeEnvelope.TYPE_CHANGED)

        private const val KEY_VALUE = "value"
        private const val KEY_KIND = "kind"
        private const val KEY_SIZE = "size"
        private const val KEY_MIME_TYPE = "mimeType"
        private const val KEY_NAME = "name"
        private const val KEY_CLIP_ID = "clipId"
        private const val KEY_SHA256 = "sha256"
        private const val KEY_STORED_AT = "storedAt"
        private const val KEY_SOURCE_DEVICE_ID = "sourceDeviceId"
        private const val KEY_SOURCE_DEVICE_NAME = "sourceDeviceName"

        fun failureOf(e: IOException): WearServerClipboardFailure = when (e) {
            is WearExchangeNotEnrolledException -> WearServerClipboardFailure.NOT_ENROLLED
            is WearExchangeRefusedException -> WearServerClipboardFailure.fromReason(e.reason)
            is WearExchangeCertificateChangedException -> WearServerClipboardFailure.CERTIFICATE_CHANGED
            is WearExchangeCertificateUntrustedException, is SSLException -> WearServerClipboardFailure.TLS_FAILED
            is SocketTimeoutException -> WearServerClipboardFailure.UNANSWERED
            is ConnectException, is NoRouteToHostException, is UnknownHostException ->
                WearServerClipboardFailure.UNREACHABLE
            is SourceUnreadableException -> WearServerClipboardFailure.SOURCE_UNREADABLE
            is SinkWriteException -> WearServerClipboardFailure.SAVE_FAILED
            else -> BROKEN
        }

        /** The members of `clipboard-value` or of `clipboard-state`'s `value`; null when one required is missing. */
        fun valueOf(members: WearExchangeEnvelope): WearServerClipboardValue? {
            val kind = WearServerClipboardKind.fromWire(members.string(KEY_KIND))
            val size = members.long(KEY_SIZE)?.takeIf { it >= 1 }
            return members.string(KEY_CLIP_ID)?.let { clipId ->
                members.string(KEY_SHA256)?.let { sha256 ->
                    if (kind == null || size == null) {
                        null
                    } else {
                        WearServerClipboardValue(
                            clipId = clipId,
                            kind = kind,
                            size = size,
                            sha256 = sha256,
                            storedAt = members.string(KEY_STORED_AT),
                            sourceDeviceId = members.string(KEY_SOURCE_DEVICE_ID),
                            sourceDeviceName = members.string(KEY_SOURCE_DEVICE_NAME),
                            mimeType = members.string(KEY_MIME_TYPE),
                            name = members.string(KEY_NAME),
                        )
                    }
                }
            }
        }
    }
}
