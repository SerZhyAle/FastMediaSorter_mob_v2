package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

/**
 * The phone's half of the rung 2 frame vectors of contract DEVICE-EXCHANGE section 7: the exact bytes it
 * writes for every envelope it sends, and the stream-ending inputs of 7.1.
 */
class DeviceExchangeEnvelopeTest {

    private val enroll = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ENROLL) {
        addProperty("login", "serzh")
        addProperty("password", "Passw0rdTest")
        addProperty("deviceId", DEVICE_ID)
        addProperty("deviceName", "Pixel 8")
        addProperty("product", "fms-android")
        addProperty("productVersion", "2.6.1")
        addProperty("platform", "android")
        add("roles", JsonArray().apply { add("resource-producer") })
        addProperty("keepaliveSeconds", KEEPALIVE)
    }

    private val expected = mapOf(
        enroll to """{"schemaVersion":2,"type":"enroll","login":"serzh","password":"Passw0rdTest",""" +
            """"deviceId":"$DEVICE_ID","deviceName":"Pixel 8","product":"fms-android","productVersion":"2.6.1",""" +
            """"platform":"android","roles":["resource-producer"],"keepaliveSeconds":30}""",
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_HELLO) {
            addProperty("deviceId", DEVICE_ID)
            addProperty("deviceToken", TOKEN)
            addProperty("productVersion", "2.6.1")
            addProperty("keepaliveSeconds", KEEPALIVE)
        } to """{"schemaVersion":2,"type":"hello","deviceId":"$DEVICE_ID","deviceToken":"$TOKEN",""" +
            """"productVersion":"2.6.1","keepaliveSeconds":30}""",
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_PUBLISH) {
            add("resource", JsonParser.parseString(RESOURCE))
        } to """{"schemaVersion":2,"type":"publish","resource":$RESOURCE}""",
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECT) {
            addProperty("resourceId", RESOURCE_ID)
        } to """{"schemaVersion":2,"type":"connect","resourceId":"$RESOURCE_ID"}""",
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECT) {
            addProperty("shareId", SHARE_ID)
        } to """{"schemaVersion":2,"type":"connect","shareId":"$SHARE_ID"}""",
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ATTACH) {
            addProperty("tunnelId", TUNNEL_ID)
        } to """{"schemaVersion":2,"type":"attach","tunnelId":"$TUNNEL_ID"}""",
        DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_VERSION_UNSUPPORTED) to
            """{"schemaVersion":2,"type":"refused","reason":"version-unsupported"}""",
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE) to """{"schemaVersion":2,"type":"bye"}""",
    )

    @Test
    fun `every envelope the phone sends is written byte for byte and reads back`() {
        expected.forEach { (envelope, json) ->
            assertEquals(json, envelope.encode())
            val frame = DeviceExchangeFrameCodec.frame(envelope)
            assertEquals(json, frame.copyOfRange(LENGTH_BYTES, frame.size).toString(Charsets.UTF_8))
            assertEquals(json.toByteArray().size, ByteBuffer.wrap(frame, 0, LENGTH_BYTES).int)
            val input = ByteArrayInputStream(frame)
            assertEquals(envelope, DeviceExchangeFrameCodec.read(input))
            assertNull(DeviceExchangeFrameCodec.read(input))
        }
    }

    @Test
    fun `a frame starts with a zero byte and a big-endian length`() {
        val frame = DeviceExchangeFrameCodec.frame(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE))
        assertEquals("00000020", frame.copyOfRange(0, LENGTH_BYTES).joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `server envelopes decode to their members`() {
        val welcome = DeviceExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"welcome","account":"serzh","publicEndpoint":"exchange.example.net:44022",""" +
                """"keepaliveSeconds":30,"features":["directory","tunnel"],"serverVersion":"0.1.0","later":1}"""
        )
        assertTrue(welcome.isKnown)
        assertEquals("exchange.example.net:44022", welcome.string("publicEndpoint"))
        assertEquals(KEEPALIVE, welcome.int("keepaliveSeconds"))
        val open = DeviceExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"open","tunnelId":"$TUNNEL_ID","broadcastId":"$SHARE_ID","scheme":"rtsp"}"""
        )
        assertEquals("rtsp", open.string("scheme"))
        val answer = DeviceExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"cast-result","accepted":false,"reason":"declined"}"""
        )
        assertEquals(false, answer.boolean("accepted"))
        assertEquals(DeviceExchangeEnvelope.REASON_DECLINED, answer.string(DeviceExchangeEnvelope.KEY_REASON))
    }

    @Test
    fun `every known type round-trips and an unknown one is kept but unknown`() {
        DeviceExchangeEnvelope.KNOWN_TYPES.forEach { type ->
            val envelope = DeviceExchangeEnvelope.of(type)
            assertEquals(envelope, DeviceExchangeEnvelope.decode(envelope.encode()))
        }
        assertEquals(32, DeviceExchangeEnvelope.KNOWN_TYPES.size)
        assertFalse(DeviceExchangeEnvelope.decode("""{"schemaVersion":2,"type":"ping"}""").isKnown)
    }

    @Test
    fun `a schemaVersion 1 peer is told apart from every other violation`() {
        assertThrows(DeviceExchangeLegacyPeerException::class.java) {
            DeviceExchangeEnvelope.decode("""{"schemaVersion":1,"type":"register"}""")
        }
        listOf(
            "[]",
            "not json",
            """{"type":"keepalive"}""",
            """{"schemaVersion":3,"type":"keepalive"}""",
            """{"schemaVersion":"2","type":"keepalive"}""",
            """{"schemaVersion":2}""",
            """{"schemaVersion":2,"type":""}""",
        ).forEach { body ->
            val thrown = assertThrows(body, DeviceExchangeWireException::class.java) {
                DeviceExchangeEnvelope.decode(body)
            }
            assertFalse(body, thrown is DeviceExchangeLegacyPeerException)
        }
    }

    @Test
    fun `broken frames end the stream`() {
        listOf(
            byteArrayOf(0, 0, 0, 0),
            ByteBuffer.allocate(LENGTH_BYTES).putInt(DeviceExchangeFrameCodec.MAX_FRAME_BODY + 1).array(),
            byteArrayOf(0, 0, 0, 10, '{'.code.toByte()),
            byteArrayOf(0, 0),
        ).forEach { bytes ->
            assertThrows(DeviceExchangeWireException::class.java) {
                DeviceExchangeFrameCodec.read(ByteArrayInputStream(bytes))
            }
        }
        val oversize = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_PUBLISH) {
            addProperty("name", "x".repeat(DeviceExchangeFrameCodec.MAX_FRAME_BODY))
        }
        assertThrows(DeviceExchangeWireException::class.java) { DeviceExchangeFrameCodec.frame(oversize) }
    }

    @Test
    fun `no secret reaches toString`() {
        assertFalse(enroll.toString().contains("Passw0rdTest"))
        assertEquals("DeviceExchangeEnvelope(type=enroll)", enroll.toString())
    }

    private companion object {
        const val LENGTH_BYTES = 4
        const val KEEPALIVE = 30
        const val DEVICE_ID = "AAECAwQFBgcICQoLDA0ODw"
        const val RESOURCE_ID = "EBESExQVFhcYGRobHB0eHw"
        const val SHARE_ID = "q3Vb7YtK0xP2mN9sLfR4wA"
        const val TOKEN = "dGhpcy1pcy1hLXRlc3QtZGV2aWNlLXRva2VuLTMyYg"
        const val TUNNEL_ID = "UFFSU1RVVldYWVpbXF1eXw"
        const val RESOURCE = """{"resourceId":"EBESExQVFhcYGRobHB0eHw","deviceId":"AAECAwQFBgcICQoLDA0ODw",""" +
            """"kind":"sftp-share","name":"Pixel 8","access":{"descriptor":"FMSSFTP1:h=192.168.1.23&p=2022"}}"""
    }
}
