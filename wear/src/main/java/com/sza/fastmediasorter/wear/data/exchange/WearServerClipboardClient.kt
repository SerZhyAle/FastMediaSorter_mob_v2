package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eClipboard
import com.sza.fastmediasorter.data.security.e2e.E2eClipboardClear
import com.sza.fastmediasorter.data.security.e2e.E2eClipboardRead
import com.sza.fastmediasorter.data.security.e2e.E2eDigestInputStream
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSegmentException
import com.sza.fastmediasorter.data.security.e2e.E2eSegmentStream
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
import java.security.DigestOutputStream
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
    private val e2e: WearExchangeE2e,
) {

    suspend fun info(entry: WearExchangeEntry, network: Network?): WearServerClipboardInfo =
        withContext(Dispatchers.IO) {
            attempt({ WearServerClipboardInfo.Failed(it) }) {
                opener.open(entry, WearExchangeKeepalive.KEEPALIVE_SECONDS, INFO_READ_TIMEOUT_MS, network)
                    .use { session -> session.closingOnCancel { askInfo(session, entry) } }
            }
        }

    /** [clearSha256] is the SHA-256 of the clear bytes, which the box carries ahead of them (27 item DN). */
    suspend fun put(
        entry: WearExchangeEntry,
        offer: WearServerClipboardOffer,
        clearSha256: String,
        source: InputStream,
        network: Network?,
        onProgress: (Long) -> Unit,
    ): WearServerClipboardPut = withContext(Dispatchers.IO) {
        attempt({ WearServerClipboardPut.Failed(it) }) {
            opener.open(entry, null, DATA_READ_TIMEOUT_MS, network).use { session ->
                val sealed = SealedOffer(offer, clearSha256)
                session.closingOnCancel { sendValue(session, entry, sealed, source, onProgress) }
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
                session.closingOnCancel { receiveValue(session, entry, clipId, prepareSink, onProgress) }
            }
        }
    }

    private suspend fun askInfo(session: WearExchangeSession, entry: WearExchangeEntry): WearServerClipboardInfo {
        val refusal = featureRefusal(session.welcome, write = false)
        if (refusal != null) return WearServerClipboardInfo.Failed(refusal)
        val sealing = sealingOf(session, entry)
        session.send(WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_INFO))
        val state = awaitAnswer(session, WearExchangeEnvelope.TYPE_CLIPBOARD_STATE)
        sendByeQuietly(session)
        if (state.type != WearExchangeEnvelope.TYPE_CLIPBOARD_STATE) {
            return WearServerClipboardInfo.Failed(refusalOf(state))
        }
        val held = state.obj(KEY_VALUE)
        return when (val read = held?.let { E2eClipboard.read(it, sealing?.self, trustOf(sealing)) }) {
            null -> WearServerClipboardInfo.Answered(null)
            is E2eClipboardRead.Opened -> valueOf(held, read.clear)
                ?.let { WearServerClipboardInfo.Answered(it) }
                ?: WearServerClipboardInfo.Failed(WearServerClipboardFailure.SEALED_FAILED)
            E2eClipboardRead.Failed -> WearServerClipboardInfo.Failed(WearServerClipboardFailure.SEALED_FAILED)
            E2eClipboardRead.Clear -> WearServerClipboardInfo.Failed(WearServerClipboardFailure.E2E_MISSING)
        }
    }

    /** What is about to be sealed: the offer and the SHA-256 of its clear bytes. */
    private class SealedOffer(val offer: WearServerClipboardOffer, val clearSha256: String)

    private suspend fun sendValue(
        session: WearExchangeSession,
        entry: WearExchangeEntry,
        sealed: SealedOffer,
        source: InputStream,
        onProgress: (Long) -> Unit,
    ): WearServerClipboardPut {
        val offer = sealed.offer
        val local = featureRefusal(session.welcome, write = true)
            ?: WearServerClipboardFailure.SOURCE_UNREADABLE.takeUnless { offer.validate() }
        val sealing = if (local == null) sealingOf(session, entry) else null
        val self = sealing?.self
        val failure = when {
            local != null -> local
            self == null || sealing.recipients.isEmpty() -> WearServerClipboardFailure.E2E_MISSING
            else -> {
                val payload = E2eClipboard.payload(
                    offer.kind.wire,
                    offer.mimeType,
                    offer.name,
                    offer.size,
                    sealed.clearSha256,
                )
                val (box, contentKey) = E2eSealedBox.sealWithKey(
                    payload,
                    E2eSealedBox.CLIPBOARD_CONTEXT,
                    self,
                    sealing.recipients,
                )
                session.send(putEnvelope(E2eSegmentStream.sealedSize(offer.size), box))
                transfer(session, source, offer.size, E2eSegmentStream.valueKey(contentKey), onProgress)
            }
        }
        return failure?.let { WearServerClipboardPut.Failed(it) } ?: WearServerClipboardPut.Stored
    }

    // Null when the server answered `clipboard-stored`.
    private fun transfer(
        session: WearExchangeSession,
        source: InputStream,
        size: Long,
        valueKey: ByteArray,
        onProgress: (Long) -> Unit,
    ): WearServerClipboardFailure? {
        val ready = awaitAnswer(session, WearExchangeEnvelope.TYPE_CLIPBOARD_READY)
        return if (ready.type == WearExchangeEnvelope.TYPE_CLIPBOARD_READY) {
            val clear = E2eDigestInputStream(source, size, MessageDigest.getInstance(SHA_256), onProgress)
            val sent = E2eSegmentStream.seal(clear, session.connection.output, valueKey)
            if (sent != size) throw SourceUnreadableException()
            session.connection.output.flush()
            val stored = awaitAnswer(session, WearExchangeEnvelope.TYPE_CLIPBOARD_STORED)
            if (stored.type == WearExchangeEnvelope.TYPE_CLIPBOARD_STORED) null else refusalOf(stored)
        } else {
            refusalOf(ready)
        }
    }

    private suspend fun receiveValue(
        session: WearExchangeSession,
        entry: WearExchangeEntry,
        clipId: String?,
        prepareSink: suspend (WearServerClipboardValue) -> OutputStream?,
        onProgress: (Long) -> Unit,
    ): WearServerClipboardGet {
        val refusal = featureRefusal(session.welcome, write = false)
        if (refusal != null) return WearServerClipboardGet.Failed(refusal)
        val sealing = sealingOf(session, entry)
        session.send(
            WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_GET) {
                clipId?.let { addProperty(KEY_CLIP_ID, it) }
            }
        )
        val answer = session.receive()
        val members = answer?.takeIf { it.type == WearExchangeEnvelope.TYPE_CLIPBOARD_VALUE }?.members
        val read = members?.let { E2eClipboard.read(it, sealing?.self, trustOf(sealing)) }
        return when {
            answer?.type == WearExchangeEnvelope.TYPE_CLIPBOARD_EMPTY -> WearServerClipboardGet.Empty
            read == null -> WearServerClipboardGet.Failed(answer?.let(::refusalOf) ?: BROKEN)
            read is E2eClipboardRead.Opened ->
                openedValue(session, requireNotNull(members), read, prepareSink, onProgress)
            read == E2eClipboardRead.Clear -> WearServerClipboardGet.Failed(WearServerClipboardFailure.E2E_MISSING)
            else -> WearServerClipboardGet.Failed(WearServerClipboardFailure.SEALED_FAILED)
        }
    }

    private suspend fun openedValue(
        session: WearExchangeSession,
        members: JsonObject,
        read: E2eClipboardRead.Opened,
        prepareSink: suspend (WearServerClipboardValue) -> OutputStream?,
        onProgress: (Long) -> Unit,
    ): WearServerClipboardGet {
        val value = valueOf(members, read.clear)
            ?: return WearServerClipboardGet.Failed(WearServerClipboardFailure.SEALED_FAILED)
        val sink = prepareSink(value) ?: return WearServerClipboardGet.Declined(value)
        val sealedDigest = MessageDigest.getInstance(SHA_256)
        val input = E2eDigestInputStream(session.connection.input, read.sealedSize, sealedDigest, onProgress)
        val clearDigest = MessageDigest.getInstance(SHA_256)
        val target = SinkStream(DigestOutputStream(sink, clearDigest))
        val opened = try {
            E2eSegmentStream.open(input, target, E2eSegmentStream.valueKey(read.contentKey))
        } catch (e: E2eSegmentException) {
            Timber.w("Clipboard get: a segment did not open (%s)", e.javaClass.simpleName)
            null
        }
        sink.flush()
        val whole = opened == value.size &&
            input.count == read.sealedSize &&
            input.hexDigest().equals(read.sealedSha256, ignoreCase = true) &&
            E2eBytes.hex(clearDigest.digest()).equals(value.sha256, ignoreCase = true)
        return if (whole) {
            WearServerClipboardGet.Received(value)
        } else {
            WearServerClipboardGet.Failed(WearServerClipboardFailure.SEALED_FAILED)
        }
    }

    // A full or failing disk on the watch is not the server's fault and must not read as one.
    private class SinkStream(private val target: OutputStream) : OutputStream() {
        override fun write(b: Int) = guarded { target.write(b) }

        override fun write(b: ByteArray, off: Int, len: Int) = guarded { target.write(b, off, len) }

        override fun flush() = guarded { target.flush() }

        private fun guarded(write: () -> Unit) {
            try {
                write()
            } catch (e: IOException) {
                throw SinkWriteException(e)
            }
        }
    }

    // The stream's own `list` names whom a box goes to and whose key opens one, as the account stands right now.
    private suspend fun sealingOf(session: WearExchangeSession, entry: WearExchangeEntry): WearSealing? {
        session.send(WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_LIST))
        val answer = awaitAnswer(session, WearExchangeEnvelope.TYPE_DIRECTORY)
        return answer.takeIf { it.type == WearExchangeEnvelope.TYPE_DIRECTORY }
            ?.let { e2e.sealing(entry, WearExchangeDirectoryState.of(it).devices.values) }
    }

    private fun trustOf(sealing: WearSealing?): (String, ByteArray) -> Boolean =
        { from, key -> sealing?.trusts(from, key) == true }
    /** Reads past keepalives and change notices until [expected] or anything that ends the exchange. */
    private fun awaitAnswer(session: WearExchangeSession, expected: String): WearExchangeEnvelope {
        while (true) {
            val answer = session.receive() ?: throw WearExchangeWireException("stream closed before $expected")
            if (answer.type !in PASSING_NOTICES) return answer
        }
    }

    // S4240: a server without `sealed` carries no value either way, so it is never sent one (27 item DP).
    private fun featureRefusal(welcome: WearExchangeWelcome, write: Boolean): WearServerClipboardFailure? = when {
        WearExchangeEntry.FEATURE_CLIPBOARD !in welcome.features -> WearServerClipboardFailure.NO_CLIPBOARD_FEATURE
        write && welcome.accountKind == WearExchangeEntry.ACCOUNT_KIND_GUEST -> WearServerClipboardFailure.NOT_PERMITTED
        WearExchangeEntry.FEATURE_SEALED !in welcome.features -> WearServerClipboardFailure.NO_SEALED_FEATURE
        else -> null
    }

    private fun refusalOf(answer: WearExchangeEnvelope): WearServerClipboardFailure =
        if (answer.type == WearExchangeEnvelope.TYPE_REFUSED) {
            WearServerClipboardFailure.fromReason(answer.string(WearExchangeEnvelope.KEY_REASON))
        } else {
            BROKEN
        }

    // A sealed put carries no `mimeType` and no `name` in clear (27 item DN).
    private fun putEnvelope(sealedSize: Long, box: JsonObject) =
        WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_PUT) {
            addProperty(KEY_KIND, E2eClipboard.KIND_SEALED)
            addProperty(KEY_SIZE, sealedSize)
            add(E2eClipboard.KEY_SEALED, box)
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
        private const val KEY_CLIP_ID = "clipId"
        private const val SHA_256 = "SHA-256"
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

        /**
         * A value from the members of `clipboard-value` or of `clipboard-state`'s `value` and the clear members of
         * its opened box (27 item DN); null when one required member is missing.
         */
        fun valueOf(members: JsonObject, clear: E2eClipboardClear): WearServerClipboardValue? {
            val envelope = WearExchangeEnvelope(WearExchangeEnvelope.TYPE_CLIPBOARD_VALUE, members)
            val kind = WearServerClipboardKind.fromWire(clear.kind)
            val clipId = envelope.string(KEY_CLIP_ID)
            return if (kind == null || clipId == null) {
                null
            } else {
                WearServerClipboardValue(
                    clipId = clipId,
                    kind = kind,
                    size = clear.size,
                    sha256 = clear.sha256,
                    storedAt = envelope.string(KEY_STORED_AT),
                    sourceDeviceId = envelope.string(KEY_SOURCE_DEVICE_ID),
                    sourceDeviceName = envelope.string(KEY_SOURCE_DEVICE_NAME),
                    mimeType = clear.mimeType,
                    name = clear.name,
                )
            }
        }
    }
}
