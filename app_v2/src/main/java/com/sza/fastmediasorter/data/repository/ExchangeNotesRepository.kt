package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.notes.NoteDecode
import com.sza.fastmediasorter.data.notes.NotePayloadCodec
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeNotesClient
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardAnswer
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardKeySource
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardKeys
import com.sza.fastmediasorter.data.remote.exchange.ExchangeNotesListResult
import com.sza.fastmediasorter.data.remote.exchange.ExchangeNotesOutcome
import com.sza.fastmediasorter.data.remote.exchange.ExchangeNotesPutResult
import com.sza.fastmediasorter.data.remote.exchange.NoteContent
import com.sza.fastmediasorter.data.remote.exchange.ReceivedNote
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.repository.NotesListOutcome
import com.sza.fastmediasorter.domain.repository.NotesPutOutcome
import com.sza.fastmediasorter.domain.repository.NotesSyncRemote
import com.sza.fastmediasorter.domain.repository.RemoteNote
import com.sza.fastmediasorter.domain.repository.RemoteNoteContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The account's notes store on the exchange server (DEVICE-EXCHANGE 1.13 items FG to FK) for the one entry the
 * person picked. Every answer's `welcome` is remembered on its entry, so the option knows whether the server still
 * offers `notes` and `sealed`.
 */
@Singleton
class ExchangeNotesRepository @Inject constructor(
    private val client: DeviceExchangeNotesClient,
    private val accounts: DeviceExchangeAccountRepository,
    private val e2e: ExchangeE2eRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NotesSyncRemote {

    override suspend fun entry(): DeviceExchangeAccountEntry? = accounts.notesEntry().first()

    override suspend fun list(entry: DeviceExchangeAccountEntry, since: Long): NotesListOutcome =
        when (val outcome = remembered(entry) { client.list(entry, since, keysOf(entry)) }) {
            ExchangeNotesOutcome.NotOffered -> NotesListOutcome.NotOffered
            ExchangeNotesOutcome.NotSealed -> NotesListOutcome.NotSealed
            ExchangeNotesOutcome.Guest -> NotesListOutcome.Guest
            is ExchangeNotesOutcome.Done -> when (val result = outcome.value) {
                is ExchangeNotesListResult.Refused -> NotesListOutcome.Refused(result.reason)
                is ExchangeNotesListResult.Listed -> NotesListOutcome.Listed(
                    notesRevision = result.listing.notesRevision,
                    full = result.listing.full,
                    notes = result.listing.notes.map(::remoteOf),
                )
            }
        }

    override suspend fun put(
        entry: DeviceExchangeAccountEntry,
        noteId: String,
        baseRevision: Long,
        payload: ByteArray?,
    ): NotesPutOutcome {
        val decoded = payload?.let(NotePayloadCodec::decode)
        if (decoded != null && decoded !is NoteDecode.Decoded) return NotesPutOutcome.Refused(REASON_INVALID_PAYLOAD)
        val clear = (decoded as? NoteDecode.Decoded)?.payload
        val outcome = remembered(entry) { client.put(entry, noteId, baseRevision, clear, keysOf(entry)) }
        return when (outcome) {
            ExchangeNotesOutcome.NotOffered -> NotesPutOutcome.NotOffered
            ExchangeNotesOutcome.NotSealed -> NotesPutOutcome.NotSealed
            ExchangeNotesOutcome.Guest -> NotesPutOutcome.Guest
            is ExchangeNotesOutcome.Done -> putOf(outcome.value)
        }
    }

    private fun putOf(result: ExchangeNotesPutResult): NotesPutOutcome = when (result) {
        is ExchangeNotesPutResult.Stored -> NotesPutOutcome.Stored(result.stored.revision)
        is ExchangeNotesPutResult.Conflict -> NotesPutOutcome.Conflict(remoteOf(result.current))
        is ExchangeNotesPutResult.Refused -> NotesPutOutcome.Refused(result.reason)
        ExchangeNotesPutResult.NoRecipient -> NotesPutOutcome.NoRecipient
        ExchangeNotesPutResult.TooLarge -> NotesPutOutcome.TooLarge
    }

    private fun remoteOf(note: ReceivedNote): RemoteNote = RemoteNote(
        noteId = note.record.noteId,
        revision = note.record.revision,
        content = when (val content = note.content) {
            is NoteContent.Opened -> RemoteNoteContent.Payload(NotePayloadCodec.encode(content.payload))
            NoteContent.Deleted -> RemoteNoteContent.Deleted
            NoteContent.Unsupported -> RemoteNoteContent.Unsupported
            NoteContent.SealedFailed -> RemoteNoteContent.Unopened
        },
    )

    private fun keysOf(entry: DeviceExchangeAccountEntry) = ExchangeClipboardKeySource { devices ->
        val sealing = e2e.sealing(entry, devices)
        sealing.self?.let { self -> ExchangeClipboardKeys(self, sealing.recipients, sealing::trusts) }
    }

    private suspend fun <T> remembered(
        entry: DeviceExchangeAccountEntry,
        call: suspend () -> ExchangeClipboardAnswer<T>,
    ): T {
        val answer = withContext(ioDispatcher) { call() }
        accounts.rememberWelcome(entry, answer.welcome)
        return answer.result
    }

    private companion object {
        const val REASON_INVALID_PAYLOAD = "invalid-payload"
    }
}
