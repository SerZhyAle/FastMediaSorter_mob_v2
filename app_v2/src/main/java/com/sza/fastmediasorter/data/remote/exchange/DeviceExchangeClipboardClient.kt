package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonObject
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeWelcome
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import com.sza.fastmediasorter.domain.model.ExchangeClipboardMeta
import com.sza.fastmediasorter.domain.model.ExchangeClipboardValue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
}

/** How one `clipboard-put` ended (22 item BW); only [Stored] means the value is on the server. */
sealed interface ExchangeClipboardPutResult {
    data class Stored(val clipId: String?, val sha256: String?, val storedAt: String?) : ExchangeClipboardPutResult {
        override fun toString(): String = "Stored(storedAt=$storedAt)"
    }

    data class Refused(val reason: String) : ExchangeClipboardPutResult
    data object NotOffered : ExchangeClipboardPutResult
    data object Guest : ExchangeClipboardPutResult
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
}

/**
 * The client side of the clipboard on the exchange server (contract DEVICE-EXCHANGE 22 items BW and BY). Every
 * call opens its own authenticated stream for one entry and hands back that stream's `welcome`, so the caller can
 * remember what the server announced; which entries take part is the caller's decision, not this class's.
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

    /** `clipboard-info` (22 item BY): the value the account's clipboard holds now, or none. */
    suspend fun state(entry: DeviceExchangeAccountEntry): ExchangeClipboardAnswer<ExchangeClipboardState> =
        withClipboardSession(entry, ExchangeClipboardState.NotOffered, ExchangeClipboardState.Guest) { session ->
            session.send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_INFO))
            val answer = awaitAnswer(session, STATE_ANSWERS)
            when (answer?.type) {
                DeviceExchangeEnvelope.TYPE_CLIPBOARD_STATE -> stateOf(answer)
                DeviceExchangeEnvelope.TYPE_REFUSED -> ExchangeClipboardState.Refused(reasonOf(answer))
                else -> throw DeviceExchangeWireException("clipboard-info was not answered with clipboard-state")
            }
        }

    /**
     * `clipboard-put` (22 item BW): declares [meta], waits for `clipboard-ready`, writes exactly `meta.size` bytes
     * of [source] and reports success only on `clipboard-stored`. A [source] that ends early throws before the
     * stream carries a full value, so the server keeps its previous one (22 item BX).
     */
    suspend fun put(
        entry: DeviceExchangeAccountEntry,
        meta: ExchangeClipboardMeta,
        source: InputStream,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardAnswer<ExchangeClipboardPutResult> =
        withClipboardSession(entry, ExchangeClipboardPutResult.NotOffered, ExchangeClipboardPutResult.Guest) {
            Timber.i("Exchange clipboard put: kind=%s size=%d", meta.kind, meta.size)
            it.send(putEnvelope(meta))
            val ready = awaitAnswer(it, PUT_ANSWERS)
            when (ready?.type) {
                DeviceExchangeEnvelope.TYPE_CLIPBOARD_READY -> {
                    copyExactly(source, it.connection.output, meta.size, onProgress)
                    storedOf(awaitAnswer(it, STORED_ANSWERS))
                }
                DeviceExchangeEnvelope.TYPE_REFUSED -> ExchangeClipboardPutResult.Refused(reasonOf(ready))
                else -> throw DeviceExchangeWireException("clipboard-put was not answered with clipboard-ready")
            }
        }

    /**
     * `clipboard-get` (22 item BY) into [target]. The bytes are hashed as they arrive; a short stream, a longer
     * count or another SHA-256 deletes [target] and answers [ExchangeClipboardGetResult.Damaged], so a partial value
     * is never handed on. [clipId] asks for the value the caller was shown; a replaced one is refused `unavailable`.
     */
    suspend fun get(
        entry: DeviceExchangeAccountEntry,
        clipId: String?,
        target: File,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardAnswer<ExchangeClipboardGetResult> =
        withClipboardSession(entry, ExchangeClipboardGetResult.NotOffered, ExchangeClipboardGetResult.Guest) {
            it.send(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_GET) {
                    clipId?.let { id -> addProperty(KEY_CLIP_ID, id) }
                }
            )
            val answer = awaitAnswer(it, GET_ANSWERS)
            when (answer?.type) {
                DeviceExchangeEnvelope.TYPE_CLIPBOARD_VALUE -> receive(it, valueOf(answer.members), target, onProgress)
                DeviceExchangeEnvelope.TYPE_CLIPBOARD_EMPTY -> ExchangeClipboardGetResult.Empty
                DeviceExchangeEnvelope.TYPE_REFUSED -> ExchangeClipboardGetResult.Refused(reasonOf(answer))
                else -> throw DeviceExchangeWireException("clipboard-get was not answered with clipboard-value")
            }
        }

    // 22 item BV: a server whose `welcome` lacks `clipboard` never sees a clipboard envelope, nor does a guest.
    private suspend fun <T> withClipboardSession(
        entry: DeviceExchangeAccountEntry,
        notOffered: T,
        guest: T,
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
                    DeviceExchangeAccountEntry.FEATURE_CLIPBOARD !in welcome.features -> notOffered
                    welcome.accountKind == DeviceExchangeAccountEntry.ACCOUNT_KIND_GUEST -> guest
                    else -> block(session)
                }
                closeOnCancel.cancel()
                ExchangeClipboardAnswer(welcome, result)
            }
        }
    }

    private suspend fun receive(
        session: DeviceExchangeSession,
        value: ExchangeClipboardValue,
        target: File,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardGetResult {
        Timber.i("Exchange clipboard get: kind=%s size=%d", value.kind, value.size)
        val digest = MessageDigest.getInstance("SHA-256")
        var kept = false
        try {
            val received = target.outputStream().use { out ->
                copyUpTo(session.connection.input, out, value.size, digest, onProgress)
            }
            kept = received == value.size && digest.digest().toHex().equals(value.sha256, ignoreCase = true)
        } finally {
            if (!kept) target.delete()
        }
        return if (kept) ExchangeClipboardGetResult.Received(value) else ExchangeClipboardGetResult.Damaged
    }

    private suspend fun copyExactly(source: InputStream, sink: OutputStream, size: Long, onProgress: (Long) -> Unit) {
        val buffer = ByteArray(CHUNK_BYTES)
        var done = 0L
        while (done < size) {
            currentCoroutineContext().ensureActive()
            val read = source.read(buffer, 0, minOf(buffer.size.toLong(), size - done).toInt())
            if (read < 0) throw IOException("the clipboard source ended before its declared size")
            sink.write(buffer, 0, read)
            done += read
            onProgress(done)
        }
        sink.flush()
    }

    // Reads at most [size] bytes; one more byte would break the value, so the count decides (22 item BY).
    private suspend fun copyUpTo(
        input: InputStream,
        sink: OutputStream,
        size: Long,
        digest: MessageDigest,
        onProgress: (Long) -> Unit,
    ): Long {
        val buffer = ByteArray(CHUNK_BYTES)
        var done = 0L
        var read = 0
        while (done < size && read >= 0) {
            currentCoroutineContext().ensureActive()
            read = input.read(buffer, 0, minOf(buffer.size.toLong(), size - done).toInt())
            if (read > 0) {
                sink.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                done += read
                onProgress(done)
            }
        }
        return done
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

    private fun stateOf(answer: DeviceExchangeEnvelope): ExchangeClipboardState =
        answer.obj(KEY_VALUE)?.let { ExchangeClipboardState.Value(valueOf(it)) } ?: ExchangeClipboardState.Empty

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

    private fun putEnvelope(meta: ExchangeClipboardMeta) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_PUT) {
            addProperty(DeviceExchangeEnvelope.KEY_KIND, meta.kind.wire)
            addProperty(KEY_SIZE, meta.size)
            meta.mimeType?.let { addProperty(KEY_MIME_TYPE, it) }
            meta.name?.let { addProperty(KEY_NAME, it) }
        }

    private fun ByteArray.toHex(): String = joinToString("") { HEX_BYTE.format(it) }

    companion object {
        private const val KEY_VALUE = "value"
        private const val KEY_CLIP_ID = "clipId"
        private const val KEY_SIZE = "size"
        private const val KEY_SHA256 = "sha256"
        private const val KEY_STORED_AT = "storedAt"
        private const val KEY_SOURCE_DEVICE_ID = "sourceDeviceId"
        private const val KEY_SOURCE_DEVICE_NAME = "sourceDeviceName"
        private const val KEY_MIME_TYPE = "mimeType"
        private const val KEY_NAME = "name"
        private const val HEX_BYTE = "%02x"
        private const val CHUNK_BYTES = 64 * 1024
        private const val MAX_UNRELATED_ENVELOPES = 8
        private val STATE_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STATE, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val PUT_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_CLIPBOARD_READY, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val STORED_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STORED, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val GET_ANSWERS = setOf(
            DeviceExchangeEnvelope.TYPE_CLIPBOARD_VALUE,
            DeviceExchangeEnvelope.TYPE_CLIPBOARD_EMPTY,
            DeviceExchangeEnvelope.TYPE_REFUSED,
        )

        /** The members of a value (22 items BU and BY); one missing or malformed member breaks the wire. */
        fun valueOf(json: JsonObject): ExchangeClipboardValue {
            fun text(key: String): String? =
                json.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            val size = json.get(KEY_SIZE)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
                ?.takeIf { it >= 1 }
            return ExchangeClipboardValue(
                clipId = text(KEY_CLIP_ID).required(),
                kind = ExchangeClipboardKind.fromWire(text(DeviceExchangeEnvelope.KEY_KIND)).required(),
                size = size.required(),
                sha256 = text(KEY_SHA256).required(),
                storedAt = text(KEY_STORED_AT).required(),
                sourceDeviceId = text(KEY_SOURCE_DEVICE_ID),
                sourceDeviceName = text(KEY_SOURCE_DEVICE_NAME),
                mimeType = text(KEY_MIME_TYPE),
                name = text(KEY_NAME),
            )
        }

        private fun <T : Any> T?.required(): T =
            this ?: throw DeviceExchangeWireException("a clipboard value lacks a required member")
    }
}
