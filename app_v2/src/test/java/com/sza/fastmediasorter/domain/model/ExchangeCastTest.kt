package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeCastTest {

    @Test
    fun `an offer is asked about by default`() {
        val machine = ExchangeCastReceiverMachine()

        val actions = machine.onOffer(offer("b1"), playable = true, playAtOnce = false)

        assertEquals(listOf(ExchangeCastAction.Ask(offer("b1"))), actions)
    }

    @Test
    fun `play at once answers yes and plays without asking`() {
        val machine = ExchangeCastReceiverMachine()

        val actions = machine.onOffer(offer("b1"), playable = true, playAtOnce = true)

        assertEquals(
            listOf(ExchangeCastAction.Answer("b1", true, null), ExchangeCastAction.Play(offer("b1"))),
            actions,
        )
    }

    @Test
    fun `an offer this build cannot play is declined as unsupported without asking`() {
        val actions = ExchangeCastReceiverMachine().onOffer(offer("b1"), playable = false, playAtOnce = false)

        assertEquals(listOf(ExchangeCastAction.Answer("b1", false, ExchangeCastRules.REASON_UNSUPPORTED)), actions)
    }

    @Test
    fun `accepting an asked offer answers once and plays`() {
        val machine = ExchangeCastReceiverMachine()
        machine.onOffer(offer("b1"), playable = true, playAtOnce = false)

        val accepted = machine.onUserAnswer("b1", accepted = true)
        val again = machine.onUserAnswer("b1", accepted = true)

        assertEquals(
            listOf(
                ExchangeCastAction.Dismiss("b1"),
                ExchangeCastAction.Answer("b1", true, null),
                ExchangeCastAction.Play(offer("b1")),
            ),
            accepted,
        )
        assertEquals(listOf(ExchangeCastAction.Dismiss("b1")), again)
    }

    @Test
    fun `declining an asked offer answers declined`() {
        val machine = ExchangeCastReceiverMachine()
        machine.onOffer(offer("b1"), playable = true, playAtOnce = false)

        val actions = machine.onUserAnswer("b1", accepted = false)

        assertEquals(
            listOf(ExchangeCastAction.Dismiss("b1"), ExchangeCastAction.Answer("b1", false, "declined")),
            actions,
        )
    }

    @Test
    fun `a stop withdraws the question and a later answer sends nothing`() {
        val machine = ExchangeCastReceiverMachine()
        machine.onOffer(offer("b1"), playable = true, playAtOnce = false)

        assertEquals(listOf(ExchangeCastAction.Dismiss("b1")), machine.onStopped("b1"))
        assertEquals(listOf(ExchangeCastAction.Dismiss("b1")), machine.onUserAnswer("b1", accepted = true))
        assertEquals(emptyList<ExchangeCastAction>(), machine.onStopped("unknown"))
    }

    @Test
    fun `plays decides coverage when present, else the two lists`() {
        val pairs = SftpRendezvousReceiver(
            modes = listOf("AUDIO_ONLY", "VIDEO_AUDIO"),
            transports = listOf("RELAY", "RTSP"),
            plays = listOf(SftpRendezvousPlayPair("AUDIO_ONLY", "RELAY")),
        )
        val lists = pairs.copy(plays = null)
        val videoOverRelay = listOf(ExchangeEndpointPair("VIDEO_AUDIO", "RELAY"))

        assertFalse(ExchangeCastRules.covers(pairs, videoOverRelay))
        assertTrue(ExchangeCastRules.covers(lists, videoOverRelay))
        assertTrue(ExchangeCastRules.covers(pairs, listOf(ExchangeEndpointPair("audio_only", "relay"))))
        assertFalse(ExchangeCastRules.covers(null, videoOverRelay))
    }

    @Test
    fun `cast targets are other receivers covering an endpoint, online first`() {
        val audioOnly = SftpRendezvousReceiver(listOf("AUDIO_ONLY"), listOf("RELAY"))
        val video = ExchangeCastRules.phoneReceiver(listOf("VIDEO_AUDIO"))
        val devices = listOf(
            device("me", receiver = video),
            device("pc", name = "Zeta PC", online = false, receiver = video),
            device("tv", name = "Alpha TV", receiver = video),
            device("watch", receiver = audioOnly),
            device("nas", receiver = null),
        )
        val endpoints = listOf(
            ExchangeEndpointPair("VIDEO_AUDIO", "RTSP"),
            ExchangeEndpointPair("VIDEO_AUDIO", "RELAY"),
        )

        val targets = ExchangeCastRules.castTargets(devices, ownDeviceId = "me", endpoints = endpoints)

        assertEquals(listOf("tv", "pc"), targets.map { it.deviceId })
        assertEquals(listOf(true, false), targets.map { it.online })
    }

    private fun offer(id: String) = ExchangeCastOffer(
        broadcastId = id,
        fromDeviceId = "pc",
        record = SftpRendezvousBroadcast(
            broadcastId = id,
            deviceId = "pc",
            title = "Screen",
            mode = "VIDEO_AUDIO",
            startedAtMs = null,
            updatedAtMs = 0L,
            descriptorJson = "{}",
            writtenAtMs = 0L,
            ttlSeconds = 0L,
        ),
    )

    private fun device(
        id: String,
        name: String = id,
        online: Boolean = true,
        receiver: SftpRendezvousReceiver?,
    ) = SftpRendezvousDevice(
        deviceId = id,
        deviceName = name,
        product = SftpRendezvousDevice.PRODUCT_FMS_ANDROID,
        productVersion = null,
        platform = null,
        roles = if (receiver != null) listOf(SftpRendezvousDevice.ROLE_RECEIVER) else emptyList(),
        presence = if (online) SftpRendezvousDevice.PRESENCE_ONLINE else SftpRendezvousDevice.PRESENCE_OFFLINE,
        updatedAtMs = 0L,
        writtenAtMs = 0L,
        ttlSeconds = 0L,
        receiver = receiver,
    )
}
