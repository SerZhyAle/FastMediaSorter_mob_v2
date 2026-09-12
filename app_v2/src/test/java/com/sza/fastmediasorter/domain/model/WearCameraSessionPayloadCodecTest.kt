package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2551: the camera session's wire contract, which crosses a process and a version boundary.
 *
 * These are the cases where a wrong answer is worse than no answer: a refusal read as an invitation,
 * and a payload from a watch on another build taking the process down inside a listener service.
 */
class WearCameraSessionPayloadCodecTest {

    private val codec = WearCameraSessionPayloadCodec(Gson())

    private val lenses = listOf(
        WearCameraLensDto(id = "0", labelKey = "lens_back", facing = "BACK"),
        WearCameraLensDto(id = "1", labelKey = "lens_front", facing = "FRONT")
    )

    @Test
    fun `a start command carries no lens and survives the round trip`() {
        val encoded = codec.encodeCommand(WearCameraCommandPayload(requestId = "req-1"))

        val decoded = codec.decodeCommand(encoded)

        assertEquals("req-1", decoded?.requestId)
        assertNull(decoded?.lensId)
    }

    @Test
    fun `a switch command carries its lens through the round trip`() {
        val encoded =
            codec.encodeCommand(WearCameraCommandPayload(requestId = "req-1", lensId = "1"))

        val decoded = codec.decodeCommand(encoded)

        assertEquals("1", decoded?.lensId)
    }

    @Test
    fun `a served url and its lens list survive the round trip`() {
        val encoded = codec.encodeAck(
            WearCameraAckPayload.serving(
                requestId = "req-1",
                url = "rtsp://192.168.1.42:8554/camera",
                lenses = lenses,
                activeLensId = "0"
            )
        )

        val decoded = codec.decodeAck(encoded)

        assertEquals("rtsp://192.168.1.42:8554/camera", decoded?.url)
        assertEquals(2, decoded?.lenses?.size)
        assertEquals("lens_front", decoded?.lenses?.get(1)?.labelKey)
        assertEquals("0", decoded?.activeLensId)
        assertNull(decoded?.refusal)
    }

    @Test
    fun `a refusal survives the round trip and carries no url`() {
        val encoded = codec.encodeAck(
            WearCameraAckPayload.refused("req-1", WearCameraRefusal.NOT_SUPPORTED)
        )

        val decoded = codec.decodeAck(encoded)

        assertEquals(WearCameraRefusal.NOT_SUPPORTED, decoded?.refusal)
        assertTrue(decoded?.url.isNullOrEmpty())
    }

    /**
     * The case this build meets after a newer watch ships a reason it has no name for: Gson maps the
     * unknown constant to null, and null is how this payload says "serving".
     */
    @Test
    fun `a refusal this build cannot name does not read as an invitation to play`() {
        val fromFutureBuild = """
            {"requestId":"req-1","url":"","lenses":[],"activeLensId":null,"refusal":"THERMAL_LIMIT"}
        """.trimIndent()

        val decoded = codec.decodeAck(fromFutureBuild.toByteArray(Charsets.UTF_8))

        assertEquals(WearCameraRefusal.UNKNOWN, decoded?.refusal)
    }

    @Test
    fun `a blank url with no reason is named rather than opened`() {
        val silent = """{"requestId":"req-1","url":"","lenses":[],"activeLensId":null}"""

        val decoded = codec.decodeAck(silent.toByteArray(Charsets.UTF_8))

        assertEquals(WearCameraRefusal.UNKNOWN, decoded?.refusal)
    }

    @Test
    fun `an undecodable payload is null rather than an exception`() {
        assertNull(codec.decodeCommand("not json at all".toByteArray(Charsets.UTF_8)))
        assertNull(codec.decodeAck("{[".toByteArray(Charsets.UTF_8)))
    }

    /**
     * The literal the watch test decodes with its own codec, so the two wire spellings are compared
     * rather than assumed. Editing it here without editing the watch test is the drift this catches.
     */
    @Test
    fun `the ack spells its fields as the watch test expects`() {
        val encoded = codec.encodeAck(
            WearCameraAckPayload.serving(
                requestId = "req-1",
                url = "rtsp://host/camera",
                lenses = listOf(lenses.first()),
                activeLensId = "0"
            )
        )

        val json = encoded.decodeToString()

        assertTrue(json.contains(""""requestId":"req-1""""))
        assertTrue(json.contains(""""url":"rtsp://host/camera""""))
        assertTrue(json.contains(""""labelKey":"lens_back""""))
        assertTrue(json.contains(""""facing":"BACK""""))
        assertTrue(json.contains(""""activeLensId":"0""""))
    }
}
