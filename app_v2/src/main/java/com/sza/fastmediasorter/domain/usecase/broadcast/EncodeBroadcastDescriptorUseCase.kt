package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorSerializer
import javax.inject.Inject

class EncodeBroadcastDescriptorUseCase @Inject constructor(
    private val serializer: BroadcastDescriptorSerializer
) {
    fun compress(url: String, title: String? = null, mode: String = "AUDIO_ONLY"): String {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = url,
            title = title,
            mode = mode
        )
        return serializer.serializeCompressed(dto)
    }

    fun serializeJson(url: String, title: String? = null, mode: String = "AUDIO_ONLY"): String {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = url,
            title = title,
            mode = mode
        )
        return serializer.serialize(dto)
    }
}
