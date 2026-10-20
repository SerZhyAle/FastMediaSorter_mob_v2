package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardFailure
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardOffer
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSendReport
import com.sza.fastmediasorter.wear.domain.repository.WearServerClipboardRepository
import javax.inject.Inject

/**
 * S4227: "Send to.." -> "Clipboard on the server" for one file of the watch, a dictaphone recording included.
 *
 * The watch never reads the system clipboard (contract DEVICE-EXCHANGE 22 item CB), so a value is always a file:
 * a picture goes as `image`, anything else as `file` with its name and type (22 item BU). A value the server
 * would reject as an invalid envelope is refused here instead, so the person reads why.
 */
class SendWearServerClipboardUseCase @Inject constructor(
    private val repository: WearServerClipboardRepository,
) {

    suspend operator fun invoke(
        file: WearMediaFile,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): WearServerClipboardSendReport {
        val offer = offerFor(file)
        return if (offer.validate()) {
            repository.send(offer, file.uri, onProgress)
        } else {
            WearServerClipboardSendReport(
                delivered = emptyList(),
                failed = emptyList(),
                localFailure = WearServerClipboardFailure.SOURCE_UNREADABLE,
            )
        }
    }

    private fun offerFor(file: WearMediaFile): WearServerClipboardOffer {
        val mimeType = file.mimeType?.takeIf(WearServerClipboardOffer::isValidMimeType) ?: UNKNOWN_MIME_TYPE
        val image = mimeType.startsWith(IMAGE_PREFIX)
        return WearServerClipboardOffer(
            kind = if (image) WearServerClipboardKind.IMAGE else WearServerClipboardKind.FILE,
            size = file.size,
            mimeType = mimeType,
            name = file.name.takeIf(WearServerClipboardOffer::isValidName),
        )
    }

    private companion object {
        const val UNKNOWN_MIME_TYPE = "application/octet-stream"
        const val IMAGE_PREFIX = "image/"
    }
}
