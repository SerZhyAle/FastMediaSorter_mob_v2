package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import javax.inject.Inject

/**
 * S3040: drop packets nobody claimed within the TTL.
 *
 * Run on sync and from the manual "clear pending queue" action, since the queue spends the user's
 * own Drive quota and no other device will ever come back for an expired packet.
 */
class CleanExpiredCrossDevicePacketsUseCase @Inject constructor(
    private val repository: CrossDeviceTransferRepository
) {

    suspend operator fun invoke(ttlDays: Int = CrossDevicePacketManifest.DEFAULT_TTL_DAYS): Result<Int> =
        repository.purgeExpiredPackets(ttlDays * MILLIS_PER_DAY)

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
