package com.sza.fastmediasorter.domain.exchange

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.ExchangeNotesEnvelopes
import com.sza.fastmediasorter.data.remote.exchange.ExchangeReceiverControlStreams
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.repository.NotesSyncRemote
import com.sza.fastmediasorter.domain.usecase.notes.NotesSyncOutcome
import com.sza.fastmediasorter.domain.usecase.notes.SyncNotesThroughExchangeUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4572: runs the notes sync at the two moments of DEVICE-EXCHANGE 1.13 item FM that need no screen - the notes
 * entry connecting and an unsolicited `notes_changed` - on the control streams the product already keeps; no timer
 * runs here (item FM). Opening the Notes screen, the return from the editor, a local delete and "Sync now" are the
 * screen's own triggers.
 *
 * Triggers are coalesced: any number of them while a run is in flight leaves exactly one more run, because a run
 * reads the store and the folder afresh. The use case's own lock keeps this and the screen from running together.
 */
@Singleton
class NotesSyncTriggerManager @Inject constructor(
    private val tunnel: SftpServerTunnelManager,
    private val receivers: ExchangeReceiverControlStreams,
    private val remote: NotesSyncRemote,
    private val sync: SyncNotesThroughExchangeUseCase,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val started = AtomicBoolean(false)
    private val pending = Channel<Unit>(Channel.CONFLATED)
    private val mutableSynced = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits after every run that synced the notes, so an open Notes screen reads the folder again. */
    val synced: SharedFlow<Unit> = mutableSynced.asSharedFlow()

    /** Idempotent: a second call adds no second collector. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(ioDispatcher) { tunnel.controlEvents.collect { onControl(null, it) } }
        scope.launch(ioDispatcher) { receivers.events.collect { onControl(it.entry, it.envelope) } }
        scope.launch(ioDispatcher) { pending.receiveAsFlow().collect { runSync() } }
    }

    /** [origin] is the entry of the receive-only stream [envelope] was read on; null for the tunnel. */
    private suspend fun onControl(origin: DeviceExchangeAccountEntry?, envelope: DeviceExchangeEnvelope) {
        if (envelope.type !in TRIGGERS) return
        // Another entry's connect or push says nothing about the one the person picked for the notes.
        val notesEntry = remote.entry() ?: return
        if ((origin ?: tunnel.liveEntry)?.identity == notesEntry.identity) pending.trySend(Unit)
    }

    private suspend fun runSync() {
        try {
            if (sync() is NotesSyncOutcome.Synced) mutableSynced.tryEmit(Unit)
        } catch (e: IOException) {
            Timber.w(e, "NotesSyncTriggerManager: notes sync failed")
        }
    }

    private companion object {
        // A stream's welcome is the connect marker both stream holders emit; the real one never reaches these flows.
        val TRIGGERS = setOf(DeviceExchangeEnvelope.TYPE_WELCOME, ExchangeNotesEnvelopes.TYPE_NOTES_CHANGED)
    }
}
