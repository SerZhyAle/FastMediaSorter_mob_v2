package com.sza.fastmediasorter.domain.model.feedback

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxEntry

/** S4573: one folder the feedback can be delivered into; [viaExchange] = reached through the exchange server. */
data class FeedbackFolder(val resourceId: Long, val name: String, val viaExchange: Boolean)

/**
 * S4573: what the form's folder selector shows. The writable-folder list is also what the ticket
 * composer's destination selector offers (S4663), while [selectedId] and [lastPacket] stay the
 * feedback form's own state. [available] is false where the network file layer is not built in;
 * [lastPacket] is the newest packet this form sent to the selected folder.
 */
data class FeedbackFolderChoice(
    val available: Boolean = false,
    val folders: List<FeedbackFolder> = emptyList(),
    val selectedId: Long? = null,
    val lastPacket: TicketOutboxEntry? = null
)

/** S4573: the outcome of Send - the queued packet, if a folder was chosen, and the settings Send to.. needs. */
data class FeedbackSendResult(val packetId: String?, val settings: AppSettings)
