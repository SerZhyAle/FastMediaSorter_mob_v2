package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.BroadcastEndpointSecretsRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * S4675: finishes the move of saved broadcasts' relay keys and tunnel pins out of clear text, for the rows saved
 * before the keys were sealed. It runs at startup rather than on first play, so a row nobody plays is protected too,
 * and it is idempotent: a row already sealed, a row a refresh changed meanwhile and a row whose key could not be
 * sealed are all left as they were and offered again by the next run.
 */
class ProtectBroadcastEndpointSecretsUseCase @Inject constructor(
    private val secrets: BroadcastEndpointSecretsRepository,
) {

    suspend operator fun invoke() {
        Timber.d("S4675: startup migration of saved endpoint secrets")
        val report = secrets.protectStored()
        if (report.migrated + report.raced + report.failed == 0) return
        Timber.i(
            "ProtectBroadcastEndpointSecretsUseCase: sealed %d, changed meanwhile %d, not sealed %d",
            report.migrated,
            report.raced,
            report.failed,
        )
    }
}
