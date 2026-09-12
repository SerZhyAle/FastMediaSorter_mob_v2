package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.domain.usecase.broadcast.EncodeBroadcastDescriptorUseCase
import javax.inject.Inject

class BroadcastShareManager @Inject constructor(
    private val encodeUseCase: EncodeBroadcastDescriptorUseCase
) {
    fun generateQrPayload(url: String, title: String?, mode: String): String {
        return encodeUseCase.compress(url, title, mode)
    }

    fun generateJsonPayload(url: String, title: String?, mode: String): String {
        return encodeUseCase.serializeJson(url, title, mode)
    }
}
