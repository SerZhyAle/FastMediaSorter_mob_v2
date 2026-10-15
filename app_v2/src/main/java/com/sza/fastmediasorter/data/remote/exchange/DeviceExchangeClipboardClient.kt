package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eClipboard
import com.sza.fastmediasorter.data.security.e2e.E2eClipboardClear
import com.sza.fastmediasorter.data.security.e2e.E2eClipboardRead
import com.sza.fastmediasorter.data.security.e2e.E2eDigestInputStream
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSegmentException
import com.sza.fastmediasorter.data.security.e2e.E2eSegmentStream
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeWelcome
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import com.sza.fastmediasorter.domain.model.ExchangeClipboardMeta
import com.sza.fastmediasorter.domain.model.ExchangeClipboardValue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** What one entry's server answered, with the `welcome` of the stream the answer came on. */
data class ExchangeClipboardAnswer<out T>(val welcome: DeviceExchangeWelcome, val result: T)

/** The clipboard of one entry's account (22 item BY), or why it was not asked. */
sealed interface ExchangeClipboardState {
    data class Value(val value: ExchangeClipboardValue) : ExchangeClipboardState
    data object Empty : ExchangeClipboardState
    data class Refused(val reason: String) : ExchangeClipboardState

    /** The server's `welcome` lists no `clipboard`; nothing was asked (22 item BV). */
    data object NotOffered : ExchangeClipboardState

    /** A `guest` account has no clipboard; nothing was asked (22 item CA). */
    data object Guest : ExchangeClipboardState

    /** The server's `welcome` lists no `sealed`; nothing was asked (`server-no-e2e`, 27 item DP). */
    data object NotSealed : ExchangeClipboardState

    /** The value is sealed, but not for this device or not by a trusted sender (`sealed-failed`, item DN). */
    data object SealedFailed : ExchangeClipboardState

    /** The value came in clear from a sender without the capability and is never placed (`e2e-missing`). */
    data object Unsealed : ExchangeClipboardState
}

/** How one `clipboard-put` ended (22 item BW); only [Stored] means the value is on the server. */
sealed interface ExchangeClipboardPutResult {
    data class Stored(val clipId: String?, val sha256: String?, val storedAt: String?) : ExchangeClipboardPutResult {
        override fun toString(): String = "Stored(storedAt=$storedAt)"
    }

    data class Refused(val reason: String) : ExchangeClipboardPutResult
    data object NotOffered : ExchangeClipboardPutResult
    data object Guest : ExchangeClipboardPutResult

    /** `server-no-e2e`: the server cannot carry a sealed value, and no clear one is sent (27 item DP). */
    data object NotSealed : ExchangeClipboardPutResult

    /** No other device of the account can open a box yet (`e2e-missing`); nothing was sent. */
    data object NoRecipient : ExchangeClipboardPutResult
}

/** How one `clipboard-get` ended (22 item BY); only [Received] left a file behind. */
sealed interface ExchangeClipboardGetResult {
    data class Received(val value: ExchangeClipboardValue) : ExchangeClipboardGetResult
    data object Empty : ExchangeClipboardGetResult
    data class Refused(val reason: String) : ExchangeClipboardGetResult

    /** The byte count or the SHA-256 differed from the announced value; the partial file is gone. */
    data object Damaged : ExchangeClipboardGetResult
    data object NotOffered : ExchangeClipboardGetResult
    data object Guest : ExchangeClipboardGetResult
    data object NotSealed : ExchangeClipboardGetResult

    /** A box or a segment did not open, or the clear size or SHA-256 differed; nothing was placed (item DN). */
    data object SealedFailed : ExchangeClipboardGetResult
    data object Unsealed : ExchangeClipboardGetResult
}

/**
 * The client side of the clipboard on the exchange server (contract DEVICE-EXCHANGE 22 items BW and BY), every value
 * sealed (27 item DN): the kind, type, name, clear size and SHA-256 travel in a box, the bytes as 64 KiB segments.
 * Every call opens its own authenticated stream for one entry, lists the account's devices on it to know whom to
 * seal for and whom to trust, and hands back that stream's `welcome`.
 *
 * The content never rides an envelope: after the metadata answer exactly `size` raw bytes follow on the same
 * stream, so a value of gigabytes passes through a fixed buffer and never sits in memory.
 *
 * Every call blocks on the network and belongs on an IO dispatcher with more than one thread: cancelling the
 * caller closes the stream from a second thread, which is what unblocks a read in progress.
 */
@Singleton
class DeviceExchangeClipboardClient @Inject constructor(
    private val accounts: DeviceExchangeAccountClient,
) {

    /** `clipboard-info` (22 item BY): the value the account's clipboard holds now, opened, or none. */
    suspend fun state(
        entry: DeviceExchangeAccountEntry,
        keySource: ExchangeClipboardKeySource,
    ): ExchangeClipboardAnswer<ExchangeClipboardState> = withClipboardSession(
        entry,
        Refusals(ExchangeClipboardState.NotOffered, ExchangeClipboardState.Guest, ExchangeClipboardState.NotSealed),
    ) { session ->
        val keys = keysOf(session, keySource)
        session.send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_INFO))
        val answer = awaitAnswer(session, STATE_ANSWERS)
        when (answer?.type) {
            DeviceExchangeEnvelope.TYPE_CLIPBOARD_STATE -> stateOf(answer, keys)
            DeviceExchangeEnvelope.TYPE_REFUSED -> ExchangeClipboardState.Refused(reasonOf(answer))
            else -> throw DeviceExchangeWireException("clipboard-info was not answered with clipboard-state")
        }
    }

    /**
     * `clipboard-put` of kind `sealed` (22 item BW, 27 item DN): the box over [meta] and [clearSha256], then exactly
     * the sealed size of [source]'s first `meta.size` bytes. A [source] that ends early throws before the stream
     * carries a full value, so the server keeps its previous one (22 item BX).
     */
    suspend fun put(
        entry: DeviceExchangeAccountEntry,
        meta: ExchangeClipboardMeta,
        clearSha256: String,
        source: InputStream,
        keySource: ExchangeClipboardKeySource,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardAnswer<ExchangeClipboardPutResult> = withClipboardSession(
        entry,
        Refusals(
            ExchangeClipboardPutResult.NotOffered,
            ExchangeClipboardPutResult.Guest,
            ExchangeClipboardPutResult.NotSealed,
        ),
    ) { session ->
        val keys = keysOf(session, keySource)?.takeIf { it.recipients.isNotEmpty() }
        if (keys == null) {
            ExchangeClipboardPutResult.NoRecipient
        } else {
            Timber.i("Exchange clipboard put: kind=%s size=%d sealed", meta.kind, meta.size)
            val (box, contentKey) = E2eSealedBox.sealWithKey(
                payloadOf(meta, clearSha256),
                E2eSealedBox.CLIPBOARD_CONTEXT,
                keys.self,
                keys.recipients,
            )
            session.send(putEnvelope(E2eSegmentStream.sealedSize(meta.size), box))
            val ready = awaitAnswer(session, PUT_ANSWERS)
            when (ready?.type) {
                DeviceExchangeEnvelope.TYPE_CLIPBOARD_READY -> {
                    sealInto(session, source, meta.size, E2eSegmentStream.valueKey(contentKey), onProgress)
                    storedOf(awaitAnswer(session, STORED_ANSWERS))
                }
                DeviceExchangeEnvelope.TYPE_REFUSED -> ExchangeClipboardPutResult.Refused(reasonOf(ready))
                else -> throw DeviceExchangeWireException("clipboard-put was not answered with clipboard-ready")
            }
        }
    }

    /**
     * `clipboard-get` (22 item BY) into [target]: the sealed bytes are counted and hashed against the server's
     * members while their segments open into [target], then the clear size and SHA-256 from the box are checked.
     * Any difference deletes [target], so a partial value is never handed on. [clipId] asks for the value the caller
     * was shown; a replaced one is refused `unavailable`.
     */
    suspend fun get(
        entry: DeviceExchangeAccountEntry,
        clipId: String?,
        target: File,
        keySource: ExchangeClipboardKeySource,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardAnswer<ExchangeClipboardGetResult> = withClipboardSession(
        entry,
        Refusals(
            ExchangeClipboardGetResult.NotOffered,
            ExchangeClipboardGetResult.Guest,
            ExchangeClipboardGetResult.NotSealed,
        ),
    ) { session ->
        val keys = keysOf(session, keySource)
        session.send(
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_GET) {
                clipId?.let { id -> addProperty(KEY_CLIP_ID, id) }
            }
        )
        val answer = awaitAnswer(session, GET_ANSWERS)
        when (answer?.type) {
            DeviceExchangeEnvelope.TYPE_CLIPBOARD_VALUE -> when (val read = readValue(answer.members, keys)) {
                is ValueRead.Opened -> receive(session, read, target, onProgress)
                ValueRead.Failed -> ExchangeClipboardGetResult.SealedFailed
                ValueRead.Clear -> ExchangeClipboardGetResult.Unsealed
            }
            DeviceExchangeEnvelope.TYPE_CLIPBOARD_EMPTY -> ExchangeClipboardGetResult.Empty
            DeviceExchangeEnvelope.TYPE_REFUSED -> ExchangeClipboardGetResult.Refused(reasonOf(answer))
            else -> throw DeviceExchangeWireException("clipboard-get was not answered with clipboard-value")
        }
    }

    private class Refusals<T>(val notOffered: T, val guest: T, val notSealed: T)

    // 22 item BV: a server whose `welcome` lacks `clipboard` never sees a clipboard envelope, nor does a guest; and
    // 27 item DP: a server without `sealed` is sent no value and asked for none.
    private suspend fun <T> withClipboardSession(
        entry: DeviceExchangeAccountEntry,
        refusals: Refusals<T>,
        block: suspend (DeviceExchangeSession) -> T,
    ): ExchangeClipboardAnswer<T> {
        val session = accounts.openAuthenticated(entry)
        return session.use {
            coroutineScope {
                val closeOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        session.close()
                    }
                }
                val welcome = session.welcome
                val result = when {
                    DeviceExchangeAccountEntry.FEATURE_CLIPBOARD !in welcome.features -> refusals.notOffered
                    welcome.accountKind == DeviceExchangeAccountEntry.ACCOUNT_KIND_GUEST -> refusals.guest
                    DeviceExchangeAccountEntry.FEATURE_SEALED !in welcome.features -> refusals.notSealed
                    else -> block(session)
                }
                closeOnCancel.cancel()
                ExchangeClipboardAnswer(welcome, result)
            }
        }
    }

    // The stream's own `list` names the devices a box goes to and whose key opens one, as they stand right now.
    private suspend fun keysOf(
        session: DeviceExchangeSession,
        source: ExchangeClipboardKeySource,
    ): ExchangeClipboardKeys? {
        session.send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_LIST))
        val answer = awaitAnswer(session, LIST_ANSWERS)
        return answer?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_DIRECTORY }
            ?.let { source.keysFor(DeviceExchangeDirectory.from(it).devices) }
    }

    private fun sealInto(
        session: DeviceExchangeSession,
        source: InputStream,
        size: Long,
        valueKey: ByteArray,
        onProgress: (Long) -> Unit,
    ) {
        val clear = E2eDigestInputStream(source, size, MessageDigest.getInstance(SHA_256), onProgress)
        val sealed = E2eSegmentStream.seal(clear, session.connection.output, valueKey)
        if (sealed != size) throw IOException("the clipboard source ended before its declared size")
        session.connection.output.flush()
    }

    private fun receive(
        session: DeviceExchangeSession,
        read: ValueRead.Opened,
        target: File,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardGetResult {
        val value = read.value
        Timber.i("Exchange clipboard get: kind=%s size=%d sealed", value.kind, value.size)
        val sealedDigest = MessageDigest.getInstance(SHA_256)
        val clearDigest = MessageDigest.getInstance(SHA_256)
        val input = E2eDigestInputStream(session.connection.input, read.sealedSize, sealedDigest, onProgress)
        var kept = false
        try {
            val clearCount = target.outputStream().use { out ->
                E2eSegmentStream.open(input, DigestOutputStream(out, clearDigest), E2eSegmentStream.valueKey(read.key))
            }
            kept = input.count == read.sealedSize &&
                E2eBytes.hex(sealedDigest.digest()).equals(read.sealedSha256, ignoreCase = true) &&
                clearCount == value.size &&
                E2eBytes.hex(clearDigest.digest()).equals(value.sha256, ignoreCase = true)
        } catch (e: E2eSegmentException) {
            Timber.w("Exchange clipboard get: a segment did not open (%s)", e.message)
        } finally {
            if (!kept) target.delete()
        }
        return if (kept) ExchangeClipboardGetResult.Received(value) else ExchangeClipboardGetResult.SealedFailed
    }

    private fun awaitAnswer(session: DeviceExchangeSession, answers: Set<String>): DeviceExchangeEnvelope? {
        var answer = session.receive()
        var skipped = 0
        while (answer != null && answer.type !in answers && skipped < MAX_UNRELATED_ENVELOPES) {
            answer = session.receive()
            skipped++
        }
        return answer
    }

    private fun stateOf(answer: DeviceExchangeEnvelope, keys: ExchangeClipboardKeys?): ExchangeClipboardState {
        val held = answer.obj(KEY_VALUE)
        return when (val read = held?.let { readValue(it, keys) }) {
            null -> ExchangeClipboardState.Empty
            is ValueRead.Opened -> ExchangeClipboardState.Value(read.value)
            ValueRead.Failed -> ExchangeClipboardState.SealedFailed
            ValueRead.Clear -> ExchangeClipboardState.Unsealed
        }
    }

    private fun storedOf(answer: DeviceExchangeEnvelope?): ExchangeClipboardPutResult = when (answer?.type) {
        DeviceExchangeEnvelope.TYPE_CLIPBOARD_STORED -> ExchangeClipboardPutResult.Stored(
            clipId = answer.string(KEY_CLIP_ID),
            sha256 = answer.string(KEY_SHA256),
            storedAt = answer.string(KEY_STORED_AT),
        )
        DeviceExchangeEnvelope.TYPE_REFUSED -> ExchangeClipboardPutResult.Refused(reasonOf(answer))
        else -> throw DeviceExchangeWireException("the value was not answered with clipboard-stored")
    }

    private fun reasonOf(answer: DeviceExchangeEnvelope): String =
        answer.string(DeviceExchangeEnvelope.KEY_REASON) ?: DeviceExchangeAccountClient.REASON_UNKNOWN

    // A sealed put carries no `mimeType` and no `name` in clear (27 item DN).
    private fun putEnvelope(sealedSize: Long, box: JsonObject) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_PUT) {
            addProperty(DeviceExchangeEnvelope.KEY_KIND, KIND_SEALED)
            addProperty(KEY_SIZE, sealedSize)
            add(KEY_SEALED, box)
        }

    /** A value of the server as this device can use it, or why it cannot. */
    sealed interface ValueRead {
        class Opened(
            val value: ExchangeClipboardValue,
            val sealedSize: Long,
            val sealedSha256: String,
            val key: ByteArray,
        ) : ValueRead

        data object Failed : ValueRead

        /** A clear kind: the sender has no capability, and the value is never placed (27 item DN). */
        data object Clear : ValueRead
    }

    companion object {
        const val KIND_SEALED = "sealed"
        private const val KEY_VALUE = "value"
        private const val KEY_CLIP_ID = "clipId"
        private const val KEY_SIZE = "size"
        private const val KEY_SHA256 = "sha256"
        private const val KEY_STORED_AT = "storedAt"
        private const val KEY_SOURCE_DEVICE_ID = "sourceDeviceId"
        private const val KEY_SOURCE_DEVICE_NAME = "sourceDeviceName"
        private const val KEY_SEALED = "sealed"
        private const val SHA_256 = "SHA-256"
        private const val MAX_UNRELATED_ENVELOPES = 8
        private val STATE_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STATE, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val PUT_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_CLIPBOARD_READY, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val STORED_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STORED, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val LIST_ANSWERS = setOf(DeviceExchangeEnvelope.TYPE_DIRECTORY, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val GET_ANSWERS = setOf(
            DeviceExchangeEnvelope.TYPE_CLIPBOARD_VALUE,
            DeviceExchangeEnvelope.TYPE_CLIPBOARD_EMPTY,
            DeviceExchangeEnvelope.TYPE_REFUSED,
        )

        /** The box's payload: the members of 22 item BW for the clear value, with its size and SHA-256. */
        fun payloadOf(meta: ExchangeClipboardMeta, clearSha256: String): JsonObject =
            E2eClipboard.payload(meta.kind.wire, meta.mimeType, meta.name, meta.size, clearSha256)

        /**
         * A `clipboard-value` or the `value` of `clipboard-state`: a sealed one opened with [keys], a clear kind
         * refused, anything malformed failed. The clear members come from the box, the server's from outside it.
         */
        fun readValue(json: JsonObject, keys: ExchangeClipboardKeys?): ValueRead =
            when (val read = E2eClipboard.read(json, keys?.self) { from, key -> keys?.trusts(from, key) == true }) {
                E2eClipboardRead.Clear -> ValueRead.Clear
                E2eClipboardRead.Failed -> ValueRead.Failed
                is E2eClipboardRead.Opened -> valueOf(json, read.clear)
                    ?.let { ValueRead.Opened(it, read.sealedSize, read.sealedSha256, read.contentKey) }
                    ?: ValueRead.Failed
            }

        private fun valueOf(json: JsonObject, clear: E2eClipboardClear): ExchangeClipboardValue? {
            val clipId = text(json, KEY_CLIP_ID)
            val storedAt = text(json, KEY_STORED_AT)
            val kind = ExchangeClipboardKind.fromWire(clear.kind)
            return if (clipId == null || storedAt == null || kind == null) {
                null
            } else {
                ExchangeClipboardValue(
                    clipId = clipId,
                    kind = kind,
                    size = clear.size,
                    sha256 = clear.sha256,
                    storedAt = storedAt,
                    sourceDeviceId = text(json, KEY_SOURCE_DEVICE_ID),
                    sourceDeviceName = text(json, KEY_SOURCE_DEVICE_NAME),
                    mimeType = clear.mimeType,
                    name = clear.name,
                )
            }
        }
        private fun text(json: JsonObject, key: String): String? =
            json.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    }
}
