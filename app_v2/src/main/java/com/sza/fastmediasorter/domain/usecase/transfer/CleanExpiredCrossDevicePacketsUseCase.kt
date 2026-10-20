package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import javax.inject.Inject

/**
 * S3040: drop packets nobody claimed within the TTL.
 *
 * Run on sync and from the manual "clear pending queue" action, since the queue spends the user's
 * own Drive quota and no other device will ever come back for an expired packet.
 *
 * S4231: expiry is judged on Drive's clock per contract item CR, and uploads that never got their
 * manifest are removed once they are 7 days old.
 */
class CleanExpiredCrossDevicePacketsUseCase @Inject constructor(
    private val repository: CrossDeviceTransferRepository
) {

    suspend operator fun invoke(): Result<Int> =
        repository.listQueue().fold(
            onSuccess = { snapshot -> repository.purgeExpired(snapshot) },
            onFailure = { Result.failure(it) }
        )
}
