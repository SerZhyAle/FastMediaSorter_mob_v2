package com.sza.fastmediasorter.domain.usecase.feedback

import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ticket.TicketOrigin
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxEntry
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.ticket.TicketAttachmentSource
import com.sza.fastmediasorter.domain.ticket.TicketDraft
import com.sza.fastmediasorter.domain.ticket.TicketInboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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

/** S4573: writable network folders, the exchange-reached ones first, plus the saved choice. */
class ObserveFeedbackFoldersUseCase @Inject constructor(
    private val resources: ResourceRepository,
    private val settings: SettingsRepository,
    private val inbox: TicketInboxRepository,
    private val remoteSources: RemoteSourceAvailabilityGate
) {
    operator fun invoke(): Flow<FeedbackFolderChoice> {
        if (!remoteSources.isNetworkGroupSupported()) return flowOf(FeedbackFolderChoice())
        val selected = settings.getSettings().map { it.feedbackInboxResourceId }.distinctUntilChanged()
        return combine(resources.getAllResources(), selected, inbox.outbox) { all, selectedId, outbox ->
            val folders = all.filter(::isWritableFolder)
                .sortedWith(compareBy<MediaResource>({ it.exchangeAccount == null }, { it.name.lowercase() }))
                .map { FeedbackFolder(it.id, it.name, it.exchangeAccount != null) }
            val chosen = selectedId?.takeIf { id -> folders.any { it.resourceId == id } }
            FeedbackFolderChoice(
                available = true,
                folders = folders,
                selectedId = chosen,
                lastPacket = chosen?.let { id ->
                    outbox.filter { it.inboxResourceId == id && it.origin == TicketOrigin.FEEDBACK }
                        .maxByOrNull { it.createdAtEpochMs }
                }
            )
        }
    }

    private fun isWritableFolder(resource: MediaResource): Boolean =
        resource.type in NETWORK_TYPES && !resource.isReadOnly && !resource.isHidden

    private companion object {
        val NETWORK_TYPES = setOf(ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP)
    }
}

/** S4573: remember the folder the feedback goes to; null = Send to.. only. */
class SelectFeedbackFolderUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(resourceId: Long?) {
        settings.updateSettings { it.withPrograms { copy(feedbackInboxResourceId = resourceId) } }
    }
}

/**
 * S4573: the feedback channel. With a folder chosen the feedback is staged as a TICKET packet for that
 * folder and its delivery scheduled; the Send to.. handoff that follows is the caller's, with or without one.
 * The draft's origin marks the packet as this form's own (S4663).
 */
class SendFeedbackUseCase @Inject constructor(
    private val inbox: TicketInboxRepository,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(text: String, attachments: List<TicketAttachmentSource>): Result<FeedbackSendResult> {
        val current = settings.getSettings().first()
        val folderId = current.feedbackInboxResourceId
            ?: return Result.success(FeedbackSendResult(packetId = null, settings = current))
        // The listener files a packet without a project into its own triage project (INBOX-PACKET item FC).
        val draft = TicketDraft(
            text = text,
            project = null,
            attachments = attachments,
            inboxResourceId = folderId,
            origin = TicketOrigin.FEEDBACK
        )
        return inbox.stage(draft).map { packetId -> FeedbackSendResult(packetId, current) }
    }
}
