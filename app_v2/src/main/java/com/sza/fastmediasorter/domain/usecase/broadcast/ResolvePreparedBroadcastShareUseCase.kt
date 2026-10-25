package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastAuthDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastDescriptors
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.model.PreparedLinkChoice
import javax.inject.Inject

/**
 * S4247, LIVE-BROADCAST item S: the outward pending descriptor of a prepared broadcast - the same descriptor, the
 * same three channels, the same password and the same keys as a broadcast started now (items BF, DK, DL). It
 * opens the broadcast by itself once it starts, so it travels only by the person's hand and is never logged.
 */
class ResolvePreparedBroadcastShareUseCase @Inject constructor() {

    operator fun invoke(link: PreparedBroadcastLink): BroadcastDescriptorDto {
        val pending = ExchangeBroadcastDescriptors.pending(
            broadcastId = link.broadcastId,
            publicEndpoint = link.publicEndpoint,
            certFingerprint = link.certFingerprint,
            mode = link.mode,
            sealed = link.tunnelAlias != null,
            untilMs = link.untilMs,
        ).copy(title = link.title, sourceId = link.sourceId)
        val outward = ExchangeBroadcastDescriptors.withOutwardKeys(pending, link.relayLinkKey, link.tunnelPin)
        val user = link.linkUser?.takeIf { link.linkChoice != PreparedLinkChoice.OPEN }
        return outward.copy(auth = user?.let { BroadcastAuthDto(user = it) })
    }

    /** The password handed beside the link; null for an open link. */
    fun linkPassword(link: PreparedBroadcastLink): String? =
        link.linkPassword?.takeIf { link.linkChoice != PreparedLinkChoice.OPEN }
}
