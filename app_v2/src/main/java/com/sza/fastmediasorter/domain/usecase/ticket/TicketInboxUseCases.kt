package com.sza.fastmediasorter.domain.usecase.ticket

import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxEntry
import com.sza.fastmediasorter.domain.model.ticket.TicketProject
import com.sza.fastmediasorter.domain.ticket.TicketDeliveryReport
import com.sza.fastmediasorter.domain.ticket.TicketDraft
import com.sza.fastmediasorter.domain.ticket.TicketInboxRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** S4554: stage a composed ticket in the outbox; delivery is the worker's. */
class FileTicketUseCase @Inject constructor(private val repository: TicketInboxRepository) {
    suspend operator fun invoke(draft: TicketDraft): Result<String> = repository.stage(draft)
}

/** S4554: one delivery pass, then a read-back of the PC's verdicts. */
class DeliverQueuedTicketsUseCase @Inject constructor(private val repository: TicketInboxRepository) {
    suspend operator fun invoke(): TicketDeliveryReport {
        val report = repository.deliverQueued()
        repository.syncStatuses()
        return report
    }
}

/** S4554: read `done/` and `failed/` for the sent packets. */
class SyncTicketInboxStatusUseCase @Inject constructor(private val repository: TicketInboxRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.syncStatuses()
}

/** S4554: the published picker list; null means "free text, project null". */
class GetTicketProjectsUseCase @Inject constructor(private val repository: TicketInboxRepository) {
    suspend operator fun invoke(): List<TicketProject>? = repository.loadProjects()
}

/** S4554: the outbox as the composer lists it. */
class ObserveTicketOutboxUseCase @Inject constructor(private val repository: TicketInboxRepository) {
    operator fun invoke(): StateFlow<List<TicketOutboxEntry>> = repository.outbox

    /** Re-read the outbox and ask for a delivery pass, which also reads the PC's verdicts. */
    suspend fun refresh() {
        repository.refreshOutbox()
        repository.scheduleDelivery()
    }

    suspend fun discard(packetId: String) = repository.discard(packetId)
}
