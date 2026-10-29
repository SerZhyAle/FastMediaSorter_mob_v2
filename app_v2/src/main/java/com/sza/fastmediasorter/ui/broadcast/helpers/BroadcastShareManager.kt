package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.domain.usecase.broadcast.EncodeBroadcastDescriptorUseCase
import com.sza.fastmediasorter.domain.usecase.broadcast.ResolveBroadcastShareDescriptorUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S3172: every channel shares the descriptor the live session carries. Taking url, title and mode
 * apart here dropped the endpoint list, the live markers and the source id on the wire.
 */
class BroadcastShareManager @Inject constructor(
    private val encodeUseCase: EncodeBroadcastDescriptorUseCase,
    private val resolveShareDescriptor: ResolveBroadcastShareDescriptorUseCase,
) {
    val shareChanges: Flow<*> get() = resolveShareDescriptor.changes

    fun generateQrPayload(live: BroadcastState.Live): String =
        encodeUseCase.compress(resolveShareDescriptor(live.descriptor))

    fun generateJsonPayload(live: BroadcastState.Live): String =
        encodeUseCase.serializeJson(resolveShareDescriptor(live.descriptor))

    fun generateShareLink(live: BroadcastState.Live): String =
        BroadcastShareLinkFactory.create(generateQrPayload(live))

    fun linkPassword(live: BroadcastState.Live): String? = resolveShareDescriptor.linkPassword(live.descriptor)

    fun isAnnounced(live: BroadcastState.Live): Boolean = resolveShareDescriptor.isAnnounced(live.descriptor)
}
