package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.drive.DriveBroadcastAnnouncer
import com.sza.fastmediasorter.domain.model.DriveBroadcastPublishState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Where the live broadcast stands on Google Drive (contract DEVICE-EXCHANGE item BC), for the screen. */
class ObserveDriveBroadcastPublishStateUseCase @Inject constructor(
    private val announcer: DriveBroadcastAnnouncer,
) {
    operator fun invoke(): StateFlow<DriveBroadcastPublishState> = announcer.publishState
}
