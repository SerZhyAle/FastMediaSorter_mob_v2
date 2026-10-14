package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearResourceSendFailure
import com.sza.fastmediasorter.wear.domain.model.WearResourceSendReport
import com.sza.fastmediasorter.wear.domain.repository.WearResourceSendRepository
import javax.inject.Inject

/**
 * S4230: "Send to.." -> one of the watch's own network resources for one file of the watch, a dictaphone recording
 * included, with no phone involved (contract DEVICE-EXCHANGE 23 item CH).
 */
class SendWearFileToResourceUseCase @Inject constructor(
    private val repository: WearResourceSendRepository,
) {

    suspend operator fun invoke(sourceId: String, file: WearMediaFile): WearResourceSendReport =
        if (sourceId.isBlank()) {
            WearResourceSendReport(null, null, WearResourceSendFailure.UNREACHABLE)
        } else {
            repository.send(sourceId, file)
        }
}
