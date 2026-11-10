package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.remote.sftp.anywhere.CompanionRecoveryChannels
import com.sza.fastmediasorter.domain.model.CompanionRecoveryChannel
import com.sza.fastmediasorter.domain.model.MediaResource
import javax.inject.Inject

/**
 * S4513: the recovery channels that cannot find a companion PC's new address right now, so a browse
 * connection error can name each one instead of leaving the user waiting for a reconnect.
 */
class GetDeadCompanionRecoveryChannelsUseCase @Inject constructor(
    private val channels: CompanionRecoveryChannels,
) {
    operator fun invoke(resource: MediaResource): List<CompanionRecoveryChannel> = channels.deadChannels(resource)
}
