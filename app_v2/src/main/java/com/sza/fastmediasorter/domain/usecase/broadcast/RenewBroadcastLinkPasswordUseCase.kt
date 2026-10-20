package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import javax.inject.Inject

/**
 * Revokes the outward link's password of the live protected broadcast and makes a new one (LIVE-BROADCAST item M);
 * the announcer sends the new verifiers with `broadcast-update` on its own. Null while nothing is protected.
 */
class RenewBroadcastLinkPasswordUseCase @Inject constructor(
    private val credentials: BroadcastCredentialRegistry,
) {
    operator fun invoke(): String? = credentials.renewLink()?.password
}
