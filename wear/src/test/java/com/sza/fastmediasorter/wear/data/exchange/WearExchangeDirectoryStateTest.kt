package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The directory a subscribed watch holds (contract DEVICE-EXCHANGE 7.4): `directory`, then `changed` by revision. */
class WearExchangeDirectoryStateTest {

    @Test
    fun `a directory answer holds its devices and broadcasts`() {
        val state = WearExchangeDirectoryState.of(directory())

        assertEquals(5L, state.revision)
        assertEquals("Phone", state.devices.getValue("d1").name)
        assertTrue(state.devices.getValue("d1").isOnline)
        assertFalse(state.devices.getValue("d2").isOnline)
        assertEquals("Mic", state.broadcasts.getValue("b1").title)
        assertEquals("AUDIO_ONLY", state.broadcasts.getValue("b1").mode)
    }

    @Test
    fun `a device record's key, capability and time are read for the trust rules`() {
        val key = E2eDeviceKey.generate()
        val record = """{"schemaVersion":2,"type":"directory","revision":1,"devices":[{"deviceId":"d9",""" +
            """"deviceName":"Tab","product":"fms-android","presence":"online","capabilities":["e2e-v1"],""" +
            """"e2eKey":{"v":1,"kem":"${key.kem}"},"updatedAt":"2026-10-15T08:00:00Z"}]}"""

        val device = WearExchangeDirectoryState.of(WearExchangeEnvelope.decode(record)).devices.getValue("d9")

        assertEquals(key.kem, device.kem)
        assertTrue(device.sealsCapable)
        assertEquals(Instant.parse("2026-10-15T08:00:00Z").toEpochMilli(), device.updatedAtMs)
    }

    @Test
    fun `the next revision is applied`() {
        val state = WearExchangeDirectoryState.of(directory())

        val outcome = state.apply(
            changed(
                6,
                upserts = """"broadcasts":[{"broadcastId":"b2","deviceId":"d2",""" +
                    """"title":"Screen","mode":"VIDEO_AUDIO"}]""",
            ),
        )

        val applied = outcome as WearExchangeDirectoryState.Outcome.Applied
        assertEquals(6L, applied.state.revision)
        assertEquals(setOf("b1", "b2"), applied.state.broadcasts.keys)
    }

    @Test
    fun `a skipped revision asks for a new list`() {
        val outcome = WearExchangeDirectoryState.of(directory()).apply(changed(7))

        assertEquals(WearExchangeDirectoryState.Outcome.Gap, outcome)
    }

    @Test
    fun `a revision not newer than the held one is stale`() {
        val state = WearExchangeDirectoryState.of(directory())

        assertEquals(WearExchangeDirectoryState.Outcome.Stale, state.apply(changed(5)))
        assertEquals(WearExchangeDirectoryState.Outcome.Stale, state.apply(changed(3)))
    }

    @Test
    fun `a push without a revision is stale`() {
        val noRevision = WearExchangeEnvelope.decode("""{"schemaVersion":2,"type":"changed"}""")

        assertEquals(
            WearExchangeDirectoryState.Outcome.Stale,
            WearExchangeDirectoryState.of(directory()).apply(noRevision),
        )
    }

    @Test
    fun `a removed broadcast leaves and the rest stays`() {
        val state = WearExchangeDirectoryState.of(directory())

        val applied = state.apply(changed(6, removals = """"broadcastIds":["b1"]""")) as
            WearExchangeDirectoryState.Outcome.Applied

        assertNull(applied.state.broadcasts["b1"])
        assertNotNull(applied.state.devices["d1"])
    }

    @Test
    fun `a removed device takes its broadcasts with it`() {
        val state = WearExchangeDirectoryState.of(directory())

        val applied = state.apply(changed(6, removals = """"deviceIds":["d1"]""")) as
            WearExchangeDirectoryState.Outcome.Applied

        assertTrue(applied.state.broadcasts.isEmpty())
        assertNull(applied.state.devices["d1"])
    }

    @Test
    fun `a malformed record is skipped alone`() {
        val state = WearExchangeDirectoryState.of(
            WearExchangeEnvelope.decode(
                """{"schemaVersion":2,"type":"directory","revision":1,"devices":[7,{"deviceName":"no id"}],""" +
                    """"broadcasts":["x",{"broadcastId":"ok","deviceId":"d1"},{"deviceId":"d1"}]}""",
            ),
        )

        assertEquals(setOf("ok"), state.broadcasts.keys)
        assertTrue(state.devices.isEmpty())
    }

    @Test
    fun `credentials are read and never printed`() {
        val state = WearExchangeDirectoryState.of(
            WearExchangeEnvelope.decode(
                """{"schemaVersion":2,"type":"directory","revision":1,"broadcasts":[{"broadcastId":"p",""" +
                    """"deviceId":"d1","descriptor":{"auth":{"scheme":"password-v1","user":"u"}},""" +
                    """"credentials":{"user":"alice","password":"s3cret"}}]}""",
            ),
        )

        val record = state.broadcasts.getValue("p")

        assertEquals("s3cret", record.credentials?.password)
        assertFalse(record.toString().contains("s3cret"))
        assertFalse(record.credentials.toString().contains("s3cret"))
        assertFalse(record.credentials.toString().contains("alice"))
    }

    @Test
    fun `a descriptor with a higher schemaVersion is never partially used`() {
        val state = WearExchangeDirectoryState.of(
            WearExchangeEnvelope.decode(
                """{"schemaVersion":2,"type":"directory","revision":1,"broadcasts":[{"broadcastId":"b_new",""" +
                    """"deviceId":"d1","descriptor":{""" +
                    """"schemaVersion":2,"title":"FutureTitle","mode":"FUTURE_MODE"}}]}""",
            ),
        )

        val record = state.broadcasts.getValue("b_new")

        assertNull(record.title)
        assertNull(record.mode)
        assertNotNull(record.descriptor)
    }

    private fun directory(): WearExchangeEnvelope = WearExchangeEnvelope.decode(
        """{"schemaVersion":2,"type":"directory","revision":5,"devices":[""" +
            """{"deviceId":"d1","deviceName":"Phone","product":"fms-android","presence":"online"},""" +
            """{"deviceId":"d2","deviceName":"PC","product":"fms-windows","presence":"offline"}],""" +
            """"resources":[],"broadcasts":[{"broadcastId":"b1","deviceId":"d1","title":"Mic","mode":"AUDIO_ONLY"}]}""",
    )

    private fun changed(revision: Long, upserts: String = "", removals: String = ""): WearExchangeEnvelope =
        WearExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"changed","revision":$revision,"upserts":{$upserts},"removals":{$removals}}""",
        )
}
