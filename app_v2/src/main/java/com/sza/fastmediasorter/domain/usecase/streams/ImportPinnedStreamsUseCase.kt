package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.model.transfer.PinnedStreamsTransferPayload
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.model.transfer.TransferReport
import java.util.UUID
import javax.inject.Inject

/**
 * S1565: applies a transferred pinned-stream list, keyed on the channel address.
 *
 * A payload of another kind, or of a format version this build does not understand, is refused
 * before the first write, so an incompatible file leaves the local list exactly as it was.
 */
class ImportPinnedStreamsUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {

    suspend operator fun invoke(payload: PinnedStreamsTransferPayload): Result<TransferReport> {
        val refusal = refusalFor(payload)
        return if (refusal != null) Result.failure(refusal) else runCatching { apply(payload) }
    }

    private fun refusalFor(payload: PinnedStreamsTransferPayload): IncompatibleTransferPayload? {
        val kind = TransferDataKind.PINNED_STREAMS
        return when {
            payload.kind != kind.name -> IncompatibleTransferPayload.WrongKind(payload.kind)
            payload.version > kind.formatVersion ->
                IncompatibleTransferPayload.NewerVersion(payload.version)

            else -> null
        }
    }

    private suspend fun apply(payload: PinnedStreamsTransferPayload): TransferReport {
        var created = 0
        var updated = 0
        var skipped = 0
        val orderedIds = mutableListOf<String>()
        val alreadyPinned = repository.pinnedSnapshot().sortedBy { it.sortIndex }.map { it.id }

        for (entry in payload.entries.sortedBy { it.sortIndex }) {
            val url = entry.url.trim()
            val existing = if (url.isEmpty()) null else repository.getByUrl(url)
            when {
                url.isEmpty() -> skipped++

                existing == null -> {
                    val entity = newEntity(url, entry)
                    repository.add(entity)
                    repository.pinToTop(entity.id)
                    orderedIds += entity.id
                    created++
                }

                !existing.pinned -> {
                    repository.pinToTop(existing.id)
                    orderedIds += existing.id
                    updated++
                }

                else -> {
                    orderedIds += existing.id
                    // Already pinned - this import only moves it when its place differs.
                    val movedInPlace =
                        alreadyPinned.indexOf(existing.id) == orderedIds.lastIndex
                    if (movedInPlace) skipped++ else updated++
                }
            }
        }

        // Channels the file did not mention keep their pins, below the transferred ones.
        val tail = alreadyPinned.filterNot { it in orderedIds }
        repository.reorderPinned(orderedIds + tail)

        return TransferReport(
            kind = TransferDataKind.PINNED_STREAMS,
            created = created,
            updated = updated,
            skipped = skipped
        )
    }

    private fun newEntity(url: String, entry: PinnedStreamsTransferPayload.PinnedStreamEntry) =
        StreamSourceEntity(
            id = UUID.randomUUID().toString(),
            url = url,
            title = entry.title.trim().ifEmpty { url },
            mediaKind = entry.mediaKind,
            sourceOrigin = SOURCE_ORIGIN_IMPORTED,
            sortIndex = entry.sortIndex,
            addedAt = System.currentTimeMillis()
        )

    /** Why the import refused a payload, so the UI can name the reason to the user. */
    sealed class IncompatibleTransferPayload(message: String) : Exception(message) {
        class WrongKind(val declaredKind: String) :
            IncompatibleTransferPayload("payload declares kind '$declaredKind'")

        class NewerVersion(val declaredVersion: Int) :
            IncompatibleTransferPayload("payload declares format version $declaredVersion")
    }

    private companion object {
        const val SOURCE_ORIGIN_IMPORTED = "IMPORTED"
    }
}
