package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorSerializer
import javax.inject.Inject

/**
 * S3172: encodes the descriptor the broadcast service built, whole. Rebuilding it here from
 * url/title/mode dropped the endpoint list, the live markers and the source id on every channel.
 */
class EncodeBroadcastDescriptorUseCase @Inject constructor(
    private val serializer: BroadcastDescriptorSerializer
) {
    fun compress(descriptor: BroadcastDescriptorDto): String = serializer.serializeCompressed(descriptor)

    fun serializeJson(descriptor: BroadcastDescriptorDto): String = serializer.serialize(descriptor)
}
