package com.sza.fastmediasorter.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import javax.inject.Inject

/**
 * S2000: hands the prepared background frame to the queue that already carries files to the watch.
 *
 * The picture rides the byte channel rather than the settings contract because that channel
 * announces its size in advance, refuses before a byte flows and reports its outcome, while an
 * oversized settings item simply never arrives and reads on the watch as an unreachable phone
 * (strategic ADR-1). Nothing here opens a channel or names a path: the queue owns both.
 */
class SendWearBackgroundImageUseCase @Inject constructor(
    private val prepareWearBackgroundImage: PrepareWearBackgroundImageUseCase,
    private val wearFileTransferRepository: WearFileTransferRepository
) {

    /**
     * Answers the queue's own id for the transfer, which is how the caller finds this one entry in
     * the queue snapshot and shows whether the frame arrived (strategic section 2.8).
     */
    suspend operator fun invoke(source: Uri): Result<String> =
        prepareWearBackgroundImage(source).map { frame ->
            wearFileTransferRepository.enqueue(
                sourcePath = frame.path,
                displayName = WearDataLayerPaths.BACKGROUND_IMAGE_FILE_NAME
            )
        }
}
