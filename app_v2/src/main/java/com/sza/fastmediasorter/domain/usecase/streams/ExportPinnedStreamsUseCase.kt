package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.model.transfer.PinnedStreamsTransferPayload
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import javax.inject.Inject

/**
 * S1565: reads the user's pinned-stream list into a device-independent payload.
 *
 * Serialization and the medium belong to the caller - this use case never writes a file.
 */
class ExportPinnedStreamsUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {

    suspend operator fun invoke(): Result<PinnedStreamsTransferPayload> = runCatching {
        val pinned = repository.pinnedSnapshot().sortedBy { it.sortIndex }
        PinnedStreamsTransferPayload(
            version = TransferDataKind.PINNED_STREAMS.formatVersion,
            kind = TransferDataKind.PINNED_STREAMS.name,
            exportedAt = System.currentTimeMillis(),
            entries = pinned.mapIndexed { index, entity ->
                PinnedStreamsTransferPayload.PinnedStreamEntry(
                    url = entity.url,
                    title = entity.title,
                    mediaKind = entity.mediaKind,
                    sortIndex = index
                )
            }
        )
    }
}
