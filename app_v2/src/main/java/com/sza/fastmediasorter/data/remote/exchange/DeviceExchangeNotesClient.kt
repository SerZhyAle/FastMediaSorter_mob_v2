package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.notes.NoteDecode
import com.sza.fastmediasorter.data.notes.NotePayload
import com.sza.fastmediasorter.data.notes.NotePayloadCodec
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollment
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A note record's content as this device can use it (DEVICE-EXCHANGE 1.13 items FG and FH). */
sealed interface NoteContent {
    data class Opened(val payload: NotePayload) : NoteContent
    data object Deleted : NoteContent

    /** `format-unsupported` (item FH): the local note is neither changed nor deleted. */
    data object Unsupported : NoteContent

    /** The box is not for this device, not from a trusted sender, or holds no item FH payload (`sealed-failed`). */
    data object SealedFailed : NoteContent
}

data class ReceivedNote(val record: NoteRecord, val content: NoteContent)

/** Every record above `since`, all pages joined (item FI); [full] means a record absent here is deleted. */
data class NotesListing(
    val notesRevision: Long,
    val notesHorizon: Long,
    val full: Boolean,
    val notes: List<ReceivedNote>,
)

sealed interface ExchangeNotesListResult {
    data class Listed(val listing: NotesListing) : ExchangeNotesListResult
    data class Refused(val reason: String) : ExchangeNotesListResult
}

/** How one `notes_put` ended; only [Stored] changed the server. */
sealed interface ExchangeNotesPutResult {
    data class Stored(val stored: NoteStored) : ExchangeNotesPutResult

    /** `note-conflict` (item FJ): [current] is the record the server holds; nothing was stored. */
    data class Conflict(val current: ReceivedNote) : ExchangeNotesPutResult
    data class Refused(val reason: String) : ExchangeNotesPutResult

    /** This entry has no device key, so no box can be sealed; nothing was sent. */
    data object NoRecipient : ExchangeNotesPutResult

    /** The payload is above item FH's limit; the note stays on this device and nothing was sent. */
    data object TooLarge : ExchangeNotesPutResult
}

/** Why a notes session ran nothing, or the value its block returned. */
sealed interface ExchangeNotesOutcome<out T> {
    data class Done<T>(val value: T) : ExchangeNotesOutcome<T>

    /** The server's `welcome` lists no `notes` (item FI). */
    data object NotOffered : ExchangeNotesOutcome<Nothing>

    /** A `guest` account has no notes store (item FG). */
    data object Guest : ExchangeNotesOutcome<Nothing>

    /** `server-no-e2e`: no `sealed`, and notes never travel in clear (item FK). */
    data object NotSealed : ExchangeNotesOutcome<Nothing>
}

/** One authenticated stream of one entry, with the keys its own `list` produced. */
class ExchangeNotesSession internal constructor(
    private val session: DeviceExchangeSession,
    private val keys: ExchangeClipboardKeys?,
    private val recipients: List<E2eRecipient>,
) {

    /** `notes_list` from [since], following `more` until the server has sent every record (item FI). */
    fun list(since: Long): ExchangeNotesListResult {
        val notes = mutableListOf<ReceivedNote>()
        var cursor = since
        var page: NotesChanges
        do {
            session.send(ExchangeNotesEnvelopes.listEnvelope(cursor))
            val answer = DeviceExchangeNotesClient.awaitAnswer(session, DeviceExchangeNotesClient.LIST_ANSWERS)
            page = when (answer?.type) {
                ExchangeNotesEnvelopes.TYPE_NOTES_CHANGES -> ExchangeNotesEnvelopes.parseChanges(answer)
                DeviceExchangeEnvelope.TYPE_REFUSED -> return ExchangeNotesListResult.Refused(reasonOf(answer))
                else -> null
            } ?: throw DeviceExchangeWireException("notes_list was not answered with notes_changes")
            notes += page.records.map { ReceivedNote(it, contentOf(it)) }
            val last = page.records.lastOrNull()?.revision ?: cursor
            // A page that does not advance the cursor would repeat forever; the next sync resumes from here.
            val advanced = last > cursor
            cursor = last
        } while (page.more && advanced)
        return ExchangeNotesListResult.Listed(NotesListing(page.notesRevision, page.notesHorizon, page.full, notes))
    }

    /** `notes_put` of [payload] over [baseRevision]; a null [payload] deletes the note (items FI and FJ). */
    fun put(noteId: String, baseRevision: Long, payload: NotePayload?): ExchangeNotesPutResult {
        require(ExchangeNotesEnvelopes.isValidNoteId(noteId)) { "noteId is not a lower-case UUID" }
        val self = keys?.self
        val refusal = when {
            payload != null && !NotePayloadCodec.fits(payload) -> ExchangeNotesPutResult.TooLarge
            payload != null && self == null -> ExchangeNotesPutResult.NoRecipient
            else -> null
        }
        if (refusal != null) return refusal
        val box = if (payload == null || self == null) {
            null
        } else {
            E2eSealedBox.seal(NotePayloadCodec.toJson(payload), E2eSealedBox.noteContext(noteId), self, recipients)
        }
        Timber.i("Exchange notes put: base=%d deleted=%b recipients=%d", baseRevision, payload == null, recipients.size)
        session.send(ExchangeNotesEnvelopes.putEnvelope(noteId, baseRevision, payload == null, box))
        val answer = DeviceExchangeNotesClient.awaitAnswer(session, DeviceExchangeNotesClient.PUT_ANSWERS)
        val stored = answer?.let(ExchangeNotesEnvelopes::parseStored)
        val conflict = answer?.let(ExchangeNotesEnvelopes::parseConflict)
        return when {
            stored != null -> ExchangeNotesPutResult.Stored(stored)
            conflict != null -> ExchangeNotesPutResult.Conflict(ReceivedNote(conflict, contentOf(conflict)))
            answer?.type == DeviceExchangeEnvelope.TYPE_REFUSED -> ExchangeNotesPutResult.Refused(reasonOf(answer))
            else -> throw DeviceExchangeWireException("notes_put was not answered with notes_stored")
        }
    }

    private fun contentOf(record: NoteRecord): NoteContent {
        val self = keys?.self
        val box = record.sealed
        return when {
            record.deleted -> NoteContent.Deleted
            self == null || box == null -> NoteContent.SealedFailed
            else -> when (
                val opened = E2eSealedBox.open(box, E2eSealedBox.noteContext(record.noteId), self, ::trusts)
            ) {
                is E2eOpenResult.Opened -> when (val decoded = NotePayloadCodec.decode(opened.payload)) {
                    is NoteDecode.Decoded -> NoteContent.Opened(decoded.payload)
                    NoteDecode.Unsupported -> NoteContent.Unsupported
                    NoteDecode.Malformed -> NoteContent.SealedFailed
                }
                is E2eOpenResult.Failed -> NoteContent.SealedFailed.also {
                    Timber.w("Exchange notes: a record did not open (%s)", opened.reason)
                }
            }
        }
    }

    // This device is a recipient of its own boxes, and its own key is never in the peer trust map.
    private fun trusts(from: String, fromKey: ByteArray): Boolean {
        val self = keys?.self ?: return false
        return if (from == self.deviceId) {
            E2eBytes.constantTimeEquals(fromKey, self.key.pair.publicKey)
        } else {
            keys.trusts(from, fromKey)
        }
    }

    private fun reasonOf(answer: DeviceExchangeEnvelope): String =
        answer.string(DeviceExchangeEnvelope.KEY_REASON) ?: DeviceExchangeAccountClient.REASON_UNKNOWN
}

/**
 * The client side of the account's notes store (DEVICE-EXCHANGE 1.13 items FG to FJ). Every call opens its own
 * authenticated stream for one entry, lists the account's devices on it to know whom to seal for and whom to trust,
 * and runs a block of `notes_list` / `notes_put` exchanges on that one stream.
 *
 * Every call blocks on the network and belongs on an IO dispatcher with more than one thread: cancelling the caller
 * closes the stream from a second thread, which is what unblocks a read in progress.
 */
@Singleton
class DeviceExchangeNotesClient @Inject constructor(
    private val accounts: DeviceExchangeAccountClient,
) {

    /**
     * Runs [block] on one stream of [entry]. A server without `notes`, a guest account and a server without
     * `sealed` are sent no notes frame at all (items FI and FK).
     */
    suspend fun <T> withNotesSession(
        entry: DeviceExchangeAccountEntry,
        keySource: ExchangeClipboardKeySource,
        block: suspend (ExchangeNotesSession) -> T,
    ): ExchangeClipboardAnswer<ExchangeNotesOutcome<T>> {
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
                val outcome = when {
                    DeviceExchangeAccountEntry.FEATURE_NOTES !in welcome.features -> ExchangeNotesOutcome.NotOffered
                    welcome.accountKind == DeviceExchangeAccountEntry.ACCOUNT_KIND_GUEST -> ExchangeNotesOutcome.Guest
                    DeviceExchangeAccountEntry.FEATURE_SEALED !in welcome.features -> ExchangeNotesOutcome.NotSealed
                    else -> ExchangeNotesOutcome.Done(block(notesSession(session, keySource)))
                }
                closeOnCancel.cancel()
                ExchangeClipboardAnswer(welcome, outcome)
            }
        }
    }

    suspend fun list(
        entry: DeviceExchangeAccountEntry,
        since: Long,
        keySource: ExchangeClipboardKeySource,
    ): ExchangeClipboardAnswer<ExchangeNotesOutcome<ExchangeNotesListResult>> =
        withNotesSession(entry, keySource) { it.list(since) }

    suspend fun put(
        entry: DeviceExchangeAccountEntry,
        noteId: String,
        baseRevision: Long,
        payload: NotePayload?,
        keySource: ExchangeClipboardKeySource,
    ): ExchangeClipboardAnswer<ExchangeNotesOutcome<ExchangeNotesPutResult>> =
        withNotesSession(entry, keySource) { it.put(noteId, baseRevision, payload) }

    // Every listed device goes to the key source, so its trust bookkeeping sees the whole account; only the
    // devices holding the role `notes` become recipients (item FI), with this device itself first.
    private suspend fun notesSession(
        session: DeviceExchangeSession,
        source: ExchangeClipboardKeySource,
    ): ExchangeNotesSession {
        session.send(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_LIST))
        val directory = awaitAnswer(session, DIRECTORY_ANSWERS)
            ?.takeIf { it.type == DeviceExchangeEnvelope.TYPE_DIRECTORY }
            ?.let(DeviceExchangeDirectory::from)
        val keys = directory?.let { source.keysFor(it.devices) }
        val notesDevices = directory?.devices.orEmpty()
            .filter { DeviceExchangeEnrollment.ROLE_NOTES in it.roles }
            .map { it.deviceId }
            .toSet()
        val self = keys?.self
        val recipients = if (self == null) {
            emptyList()
        } else {
            val peers = keys.recipients.filter { it.deviceId in notesDevices && it.deviceId != self.deviceId }
            listOf(E2eRecipient(self.deviceId, self.key.pair.publicKey)) + peers.take(E2eSealedBox.MAX_RECIPIENTS - 1)
        }
        return ExchangeNotesSession(session, keys, recipients)
    }

    internal companion object {
        private const val MAX_UNRELATED_ENVELOPES = 8
        val LIST_ANSWERS = setOf(ExchangeNotesEnvelopes.TYPE_NOTES_CHANGES, DeviceExchangeEnvelope.TYPE_REFUSED)
        val PUT_ANSWERS = setOf(ExchangeNotesEnvelopes.TYPE_NOTES_STORED, DeviceExchangeEnvelope.TYPE_REFUSED)
        private val DIRECTORY_ANSWERS =
            setOf(DeviceExchangeEnvelope.TYPE_DIRECTORY, DeviceExchangeEnvelope.TYPE_REFUSED)

        // An unsolicited `notes_changed` or `keepalive` may arrive between a request and its answer (item EF).
        fun awaitAnswer(session: DeviceExchangeSession, answers: Set<String>): DeviceExchangeEnvelope? {
            var answer = session.receive()
            var skipped = 0
            while (answer != null && answer.type !in answers && skipped < MAX_UNRELATED_ENVELOPES) {
                answer = session.receive()
                skipped++
            }
            return answer
        }
    }
}
