package com.sza.fastmediasorter.data.ticket

import android.content.Context
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxEntry
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxState
import com.sza.fastmediasorter.domain.model.ticket.TicketProject
import com.sza.fastmediasorter.domain.ticket.TicketDeliveryReport
import com.sza.fastmediasorter.domain.ticket.TicketDraft
import com.sza.fastmediasorter.domain.ticket.TicketInboxRepository
import com.sza.fastmediasorter.worker.TicketInboxDeliveryWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4554: the outbox on disk, delivered into the Drive inbox.
 *
 * One mutex serializes staging, delivery and the read-back: the worker and the composer's refresh can
 * run at the same moment, and two uploads of one packet would race on the same Drive folder.
 */
@Singleton
class TicketInboxRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: TicketOutboxStore,
    private val inbox: GoogleDriveTicketInboxDataSource,
    @IoDispatcher private val io: CoroutineDispatcher
) : TicketInboxRepository {

    private val mutex = Mutex()
    private val entries = MutableStateFlow<List<TicketOutboxEntry>>(emptyList())

    override val outbox: StateFlow<List<TicketOutboxEntry>> = entries.asStateFlow()

    override suspend fun stage(draft: TicketDraft): Result<String> = locked {
        Timber.d("S4554: staging ticket packet attachments=${draft.attachments.size} project=${draft.project}")
        runCatching { store.stage(draft).manifest.packetId }
            .onFailure { it.rethrowIfCancellation() }
            .onSuccess { TicketInboxDeliveryWorker.enqueue(context) }
            .also { publish() }
    }

    override fun scheduleDelivery() = TicketInboxDeliveryWorker.enqueue(context)

    // The Drive auth restore surfaces auth, IO and state failures alike; any of them only means
    // "not signed in now", so packets stay queued for the next worker run.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun deliverQueued(): TicketDeliveryReport = locked {
        Timber.d("S4554: delivering queued ticket packets to Drive inbox")
        val pending = store.list().filter {
            it.entry.state == TicketOutboxState.QUEUED || it.entry.state == TicketOutboxState.WAITING_ON_DRIVE
        }
        var delivered = 0
        if (pending.isNotEmpty()) {
            val signedIn = try {
                inbox.ensureSignedIn()
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.w(e, "Ticket inbox sign-in check failed, packets stay queued")
                false
            }
            pending.forEach { packet ->
                val failure = if (signedIn) uploadFailure(packet) else NOT_SIGNED_IN
                val next = if (failure == null) {
                    delivered++
                    packet.entry.copy(state = TicketOutboxState.SENT, failure = null)
                } else {
                    packet.entry.copy(state = TicketOutboxState.WAITING_ON_DRIVE, failure = failure)
                }
                store.update(packet, next)
            }
        }
        publish()
        TicketDeliveryReport(delivered = delivered, waiting = pending.size - delivered)
    }

    override suspend fun syncStatuses(): Result<Unit> = locked {
        Timber.d("S4554: reading done/failed verdicts of sent ticket packets")
        val sent = store.list().filter { it.entry.state == TicketOutboxState.SENT }
        val outcome = runCatching {
            if (sent.isNotEmpty() && !inbox.ensureSignedIn()) throw IOException(NOT_SIGNED_IN)
            sent.forEach { packet ->
                when (val verdict = inbox.readVerdict(packet.entry.packetId)) {
                    is TicketInboxVerdict.Done -> store.update(
                        packet,
                        packet.entry.copy(state = TicketOutboxState.PROCESSED, tickets = verdict.result.tickets)
                    )
                    is TicketInboxVerdict.Failed -> store.update(
                        packet,
                        packet.entry.copy(state = TicketOutboxState.FAILED, failure = refusalLine(verdict))
                    )
                    TicketInboxVerdict.Pending -> Unit
                }
            }
        }.onFailure {
            it.rethrowIfCancellation()
            Timber.w(it, "Ticket inbox status read failed")
        }
        publish()
        outcome
    }

    override suspend fun loadProjects(): List<TicketProject>? = withContext(io) {
        runCatching { if (inbox.ensureSignedIn()) inbox.readProjects() else null }
            .onFailure {
                it.rethrowIfCancellation()
                Timber.w(it, "Ticket inbox projects.json unreadable")
            }
            .getOrNull()
    }

    override suspend fun discard(packetId: String) = locked {
        store.delete(packetId)
        publish()
    }

    override suspend fun refreshOutbox() = locked { publish() }

    private suspend fun uploadFailure(packet: StagedTicketPacket): String? = try {
        inbox.upload(packet.folder, packet.manifest)
        null
    } catch (error: IOException) {
        Timber.w(error, "Ticket packet upload failed")
        error.message ?: error.javaClass.simpleName
    }

    private fun refusalLine(verdict: TicketInboxVerdict.Failed): String =
        listOfNotNull(verdict.refusal.error, verdict.refusal.detail).joinToString(": ")

    private fun publish() {
        entries.value = store.list().map { it.entry }
    }

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(io) { mutex.withLock { block() } }

    private companion object {
        const val NOT_SIGNED_IN = "Google Drive is not signed in"
    }
}
