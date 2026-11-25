package com.sza.fastmediasorter.domain.usecase.feedback

import android.content.Context
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.core.logging.LoggingHelper
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.feedback.FeedbackFolder
import com.sza.fastmediasorter.domain.model.feedback.FeedbackFolderChoice
import com.sza.fastmediasorter.domain.model.feedback.FeedbackSendResult
import com.sza.fastmediasorter.domain.model.ticket.TicketAttachmentRole
import com.sza.fastmediasorter.domain.model.ticket.TicketDestination
import com.sza.fastmediasorter.domain.model.ticket.TicketOrigin
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.ticket.TicketAttachmentSource
import com.sza.fastmediasorter.domain.ticket.TicketDraft
import com.sza.fastmediasorter.domain.ticket.TicketInboxRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
            destination = TicketDestination.Folder(folderId),
            origin = TicketOrigin.FEEDBACK
        )
        return inbox.stage(draft).map { packetId -> FeedbackSendResult(packetId, current) }
    }
}

/**
 * S4685: the diagnostic archive as a LOG-role attachment, built by the same packaging the log export
 * ships - its redaction, size bound and environment summary are inherited, not reimplemented (ADR-1).
 * Null means the log set is empty, so the form reports that instead of attaching nothing; a build
 * failure throws and is reported as its own notice, keeping the two cases apart.
 */
class AttachFeedbackLogsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(): TicketAttachmentSource? = withContext(io) {
        if (LoggingHelper.getExportableLogFiles(context).isEmpty()) return@withContext null
        val uri = LogExportHelper.buildLogsZipUri(context) ?: throw ArchiveBuildFailed()
        val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifEmpty { FALLBACK_NAME }
        TicketAttachmentSource(uri.toString(), name, ARCHIVE_MIME, TicketAttachmentRole.LOG)
    }

    /** The builder failed or produced no archive for a non-empty log set; never carries user data. */
    class ArchiveBuildFailed : IllegalStateException("diagnostic archive build failed")

    private companion object {
        const val ARCHIVE_MIME = "application/zip"
        const val FALLBACK_NAME = "logs.zip"
    }
}
