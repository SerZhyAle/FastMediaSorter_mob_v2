package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorParser
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorSerializer
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3172: the share chain used to rebuild the descriptor from url/title/mode, so every consumer of a
 * live broadcast lost the endpoint list, the live markers and the source id. The round-trip test one
 * layer below could not see it, because it starts at the serializer.
 */
class EncodeBroadcastDescriptorUseCaseTest {

    private val parser = BroadcastDescriptorParser()
    private val useCase = EncodeBroadcastDescriptorUseCase(BroadcastDescriptorSerializer())

    private val descriptor = BroadcastDescriptorDto(
        schemaVersion = 1,
        url = "rtsp://192.168.1.84:8554/",
        title = "Phone Audio Stream",
        mode = "VIDEO_AUDIO",
        sourceId = "6f1a8f0e-0f4e-4a2b-9d1c-2b7f1a8f0e00",
        endpoints = listOf(
            BroadcastEndpointDto(
                url = "rtsp://192.168.1.84:8554/",
                transport = "RTSP",
                mode = "VIDEO_AUDIO",
                videoCodec = "H264",
                audioCodec = "AAC",
                isLive = true,
                targetLatencyMs = 1000
            ),
            BroadcastEndpointDto(
                url = "http://192.168.1.84:8768/live-audio.aac",
                transport = "HTTP",
                mode = "AUDIO_ONLY",
                audioCodec = "AAC",
                isLive = true,
                targetLatencyMs = 1000
            )
        ),
        isLive = true,
        targetLatencyMs = 1000
    )

    @Test
    fun testSerializeJsonKeepsEveryDescriptorField() {
        assertEquals(descriptor, parser.parse(useCase.serializeJson(descriptor)))
    }

    @Test
    fun testCompressKeepsEveryDescriptorField() {
        assertEquals(descriptor, parser.parse(useCase.compress(descriptor)))
    }
}
