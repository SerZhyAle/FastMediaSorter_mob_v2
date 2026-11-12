package com.sza.fastmediasorter.domain.ticket

import com.sza.fastmediasorter.domain.model.ticket.TicketAttachmentRole
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxEntry
import com.sza.fastmediasorter.domain.model.ticket.TicketProject
import kotlinx.coroutines.flow.StateFlow

/** S4554: one attachment the composer picked; [uri] is opened by the data layer, never parsed here. */
data class TicketAttachmentSource(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val role: TicketAttachmentRole
)

/** S4554: what the owner composed - text, attachments and the target project, null for triage. */
data class TicketDraft(
    val text: String,
    val project: String?,
    val attachments: List<TicketAttachmentSource>
)

/** S4554: one delivery pass - how many packets reached Drive and how many still wait. */
data class TicketDeliveryReport(val delivered: Int, val waiting: Int)

/**
 * S4554: the phone half of the SZA ticket inbox (INBOX-PACKET v1).
 *
 * A packet is staged locally whole before any network call, so filing a ticket never depends on
 * connectivity; delivery and the read-back of the PC's verdict are separate passes the worker runs.
 */
interface TicketInboxRepository {

    val outbox: StateFlow<List<TicketOutboxEntry>>

    /** Stage [draft] as a complete packet in the local outbox; returns its packetId. */
    suspend fun stage(draft: TicketDraft): Result<String>

    /** Upload every queued or waiting packet into the inbox's `new/` folder. */
    suspend fun deliverQueued(): TicketDeliveryReport

    /** Read `done/` and `failed/` for every sent packet. */
    suspend fun syncStatuses(): Result<Unit>

    /** The published project list, or null when `projects.json` is missing, stale or unreadable. */
    suspend fun loadProjects(): List<TicketProject>?

    /** Drop one packet from the local outbox; the Drive copy, if any, stays the PC's audit trail. */
    suspend fun discard(packetId: String)

    /** Ask for a delivery pass on the next connectivity; a no-op when one is already scheduled. */
    fun scheduleDelivery()

    /** Re-read the outbox folder into [outbox]. */
    suspend fun refreshOutbox()
}
