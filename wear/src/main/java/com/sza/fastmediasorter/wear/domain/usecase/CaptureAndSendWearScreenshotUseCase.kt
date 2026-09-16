package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.wear.WearScreenshotRefusalReasons
import com.sza.fastmediasorter.wear.data.wear.WearScreenshotRequestAck
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
import com.sza.fastmediasorter.wear.domain.repository.WearScreenCapture
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3110: photographs this watch's screen for the phone that asked, and says what became of it.
 *
 * The picture rides the file route the watch already uses for everything binary (ADR-2), so the phone
 * receives, stores and announces it with code written long before this ticket; what travels back on
 * the screenshot route is only the outcome.
 */
@Singleton
class CaptureAndSendWearScreenshotUseCase @Inject constructor(
    private val wearScreenCapture: WearScreenCapture,
    private val wearFileSenderRepository: WearFileSenderRepository
) {

    suspend operator fun invoke(requestId: String): WearScreenshotRequestAck {
        val capture = wearScreenCapture.capture()
        val file = capture.file
            ?: return WearScreenshotRequestAck(
                requestId = requestId,
                captured = false,
                reason = capture.reason ?: WearScreenshotRefusalReasons.CAPTURE_FAILED
            )

        return try {
            val outcome = wearFileSenderRepository.sendFile(file).outcome
            val delivered = outcome == WearFileSendOutcome.SENT || outcome == WearFileSendOutcome.QUEUED_ON_PHONE
            Timber.i("Watch screenshot %s left as %s", file.name, outcome)
            WearScreenshotRequestAck(
                requestId = requestId,
                captured = delivered,
                // Named even on a refusal: an unconfirmed send may still land, and this is the only
                // thing tying that arrival to the request the phone made.
                fileName = file.name,
                reason = if (delivered) null else WearScreenshotRefusalReasons.SEND_FAILED
            )
        } finally {
            file.delete()
        }
    }
}
