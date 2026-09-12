package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2551: the camera session's wire contract, which crosses a process and a version boundary.
 *
 * These are the cases where a wrong answer is worse than no answer: a refusal read as an invitation,
 * and a payload from a phone on another build taking the process down inside a listener service.
 */
class CameraSessionPayloadCodecTest {

    private val codec = CameraSessionPayloadCodec(Gson())

    private val lenses = listOf(
        CameraLensDto(id = "0", labelKey = "lens_back", facing = "BACK"),
        CameraLensDto(id = "1", labelKey = "lens_front", facing = "FRONT")
    )

    @Test
    fun `a start command carries no lens and survives the round trip`() {
        val encoded = codec.encodeCommand(CameraCommandPayload(requestId = "req-1"))

        val decoded = codec.decodeCommand(encoded)

        assertEquals("req-1", decoded?.requestId)
        assertNull(decoded?.lensId)
    }

    @Test
    fun `a switch command carries its lens through the round trip`() {
        val encoded = codec.encodeCommand(CameraCommandPayload(requestId = "req-1", lensId = "1"))

        val decoded = codec.decodeCommand(encoded)

        assertEquals("1", decoded?.lensId)
    }

    @Test
    fun `a served url and its lens list survive the round trip`() {
        val encoded = codec.encodeAck(
            CameraAckPayload.serving(
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
        val encoded = codec.encodeAck(CameraAckPayload.refused("req-1", CameraRefusal.NOT_SUPPORTED))

        val decoded = codec.decodeAck(encoded)

        assertEquals(CameraRefusal.NOT_SUPPORTED, decoded?.refusal)
        assertTrue(decoded?.url.isNullOrEmpty())
    }

    /**
     * The case this build meets after a newer phone ships a reason it has no name for: Gson maps the
     * unknown constant to null, and null is how this payload says "serving".
     */
    @Test
    fun `a refusal this build cannot name does not read as an invitation to play`() {
        val fromFutureBuild = """
            {"requestId":"req-1","url":"","lenses":[],"activeLensId":null,"refusal":"THERMAL_LIMIT"}
        """.trimIndent()

        val decoded = codec.decodeAck(fromFutureBuild.toByteArray(Charsets.UTF_8))

        assertEquals(CameraRefusal.UNKNOWN, decoded?.refusal)
    }

    @Test
    fun `a blank url with no reason is named rather than opened`() {
        val silent = """{"requestId":"req-1","url":"","lenses":[],"activeLensId":null}"""

        val decoded = codec.decodeAck(silent.toByteArray(Charsets.UTF_8))

        assertEquals(CameraRefusal.UNKNOWN, decoded?.refusal)
    }

    @Test
    fun `an undecodable payload is null rather than an exception`() {
        assertNull(codec.decodeCommand("not json at all".toByteArray(Charsets.UTF_8)))
        assertNull(codec.decodeAck("{[".toByteArray(Charsets.UTF_8)))
    }

    /**
     * The phone's own encoded spelling, copied from `WearCameraSessionPayloadCodecTest`, decoded here
     * by the watch's codec - so the two wire spellings are compared rather than assumed. Editing the
     * literal on one side without the other is exactly the silent drift this catches.
     */
    @Test
    fun `the phone's spelling of a served ack decodes on the watch`() {
        val fromPhone = """
            {"requestId":"req-1","url":"rtsp://host/camera","lenses":[{"id":"0","labelKey":"lens_back","facing":"BACK"}],"activeLensId":"0"}
        """.trimIndent()

        val decoded = codec.decodeAck(fromPhone.toByteArray(Charsets.UTF_8))

        assertEquals("req-1", decoded?.requestId)
        assertEquals("rtsp://host/camera", decoded?.url)
        assertEquals("lens_back", decoded?.lenses?.single()?.labelKey)
        assertEquals("BACK", decoded?.lenses?.single()?.facing)
        assertEquals("0", decoded?.activeLensId)
        assertNull(decoded?.refusal)
    }

    /** The phone's spelling of a refusal, likewise copied rather than re-derived. */
    @Test
    fun `the phone's spelling of a refusal decodes on the watch`() {
        val fromPhone = """
            {"requestId":"req-1","url":"","lenses":[],"activeLensId":null,"refusal":"NOT_SUPPORTED"}
        """.trimIndent()

        val decoded = codec.decodeAck(fromPhone.toByteArray(Charsets.UTF_8))

        assertEquals(CameraRefusal.NOT_SUPPORTED, decoded?.refusal)
    }
}
