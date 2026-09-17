package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.domain.usecase.broadcast.EncodeBroadcastDescriptorUseCase
import javax.inject.Inject

/**
 * S3172: every channel shares the descriptor the live session carries. Taking url, title and mode
 * apart here dropped the endpoint list, the live markers and the source id on the wire.
 */
class BroadcastShareManager @Inject constructor(
    private val encodeUseCase: EncodeBroadcastDescriptorUseCase
) {
    fun generateQrPayload(live: BroadcastState.Live): String = encodeUseCase.compress(live.descriptor)

    fun generateJsonPayload(live: BroadcastState.Live): String = encodeUseCase.serializeJson(live.descriptor)

    fun generateShareLink(live: BroadcastState.Live): String =
        BroadcastShareLinkFactory.create(generateQrPayload(live))
}
