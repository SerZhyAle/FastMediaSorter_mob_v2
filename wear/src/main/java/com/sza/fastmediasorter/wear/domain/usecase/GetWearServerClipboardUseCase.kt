package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.files.WearServerClipboardSlot
import com.sza.fastmediasorter.wear.data.files.WearWatchFilePublisher
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFormatPolicy
import com.sza.fastmediasorter.wear.domain.files.WearWatchFileCollection
import com.sza.fastmediasorter.wear.domain.files.WearWatchFileTarget
import com.sza.fastmediasorter.wear.domain.model.WearFileOpenRequest
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardGetResult
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardReceiveReport
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSaved
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSink
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import com.sza.fastmediasorter.wear.domain.repository.WearServerClipboardRepository
import javax.inject.Inject

/**
 * S4227: fetches the latest clipboard value, saves it on the watch and says what to open.
 *
 * Space is checked against the declared size before the first byte (strategic §3.2). A picture, a sound or a
 * video streams straight into a pending row of its media collection, so it needs its size once and becomes
 * visible only after its hash matched; text and every other kind go to [WearServerClipboardSlot] (ADR-4). A
 * value the watch cannot show is saved and named, never opened by itself (strategic §11 criterion 4).
 */
class GetWearServerClipboardUseCase @Inject constructor(
    private val repository: WearServerClipboardRepository,
    private val publisher: WearWatchFilePublisher,
    private val slot: WearServerClipboardSlot,
) {

    private val documentFormats = WearDocumentFormatPolicy()

    suspend operator fun invoke(
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): WearServerClipboardGetResult {
        var refusedForSpace = false
        val report = repository.receive(
            destination = { value ->
                val target = targetOf(value)
                val free = if (target.collection != null) publisher.freeBytes() else slot.freeBytes()
                refusedForSpace = free < value.size
                if (refusedForSpace) null else open(target)
            },
            onProgress = onProgress,
        )
        val openRequest = (report as? WearServerClipboardReceiveReport.Received)
            ?.let { received -> openRequestFor(received.value, received.saved) }
        return WearServerClipboardGetResult(report, openRequest, refusedForSpace)
    }

    private fun targetOf(value: WearServerClipboardValue): Target {
        val mimeType = mimeTypeOf(value)
        val collection = WearWatchFileTarget.collectionOf(mimeType)
            ?.takeIf { value.kind != WearServerClipboardKind.TEXT && publisher.isAvailable() }
        return Target(WearServerClipboardSlot.safeName(value), mimeType, collection)
    }

    private fun open(target: Target): WearServerClipboardSink? = target.collection
        ?.let { publisher.openPending(target.name, target.mimeType, it) }
        ?: slot.open(target.name)

    private fun openRequestFor(value: WearServerClipboardValue, saved: WearServerClipboardSaved): WearFileOpenRequest? {
        val mimeType = mimeTypeOf(value)
        val media = WearWatchFileTarget.collectionOf(mimeType) != null
        val shown = media || documentFormats.isReadable(mimeType, saved.displayName)
        return when {
            !shown -> null
            saved.contentUri != null -> WearFileOpenRequest(
                path = saved.displayName,
                mimeType = mimeType,
                contentUri = saved.contentUri,
                sizeBytes = value.size,
            )
            else -> saved.path?.let { WearFileOpenRequest(path = it, mimeType = mimeType) }
        }
    }

    private fun mimeTypeOf(value: WearServerClipboardValue): String = when (value.kind) {
        WearServerClipboardKind.TEXT -> TEXT_MIME_TYPE
        else -> value.mimeType ?: UNKNOWN_MIME_TYPE
    }

    private class Target(val name: String, val mimeType: String, val collection: WearWatchFileCollection?)

    private companion object {
        const val TEXT_MIME_TYPE = "text/plain"
        const val UNKNOWN_MIME_TYPE = "application/octet-stream"
    }
}
