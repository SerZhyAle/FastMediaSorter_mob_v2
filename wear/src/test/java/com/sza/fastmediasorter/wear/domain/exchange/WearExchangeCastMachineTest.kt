package com.sza.fastmediasorter.wear.domain.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract 7.8 on the watch: ask by default, decline what cannot play, withdraw on stop or lapse. */
class WearExchangeCastMachineTest {

    private val machine = WearExchangeCastMachine()
    private val offer = WearExchangeCastOffer(identity = "ex:7443/owner", broadcastId = "b1", title = "Phone")

    @Test
    fun `an offer is asked about by default`() {
        val actions = machine.onOffer(offer, playable = true, playAtOnce = false)

        assertEquals(listOf(WearExchangeCastAction.Ask(offer)), actions)
    }

    @Test
    fun `an offer the watch cannot play is declined as unsupported`() {
        val actions = machine.onOffer(offer, playable = false, playAtOnce = false)

        assertEquals(listOf(WearExchangeCastAction.Answer(offer, false, "unsupported")), actions)
    }

    @Test
    fun `accepting answers and plays once`() {
        machine.onOffer(offer, playable = true, playAtOnce = false)

        val actions = machine.onUserAnswer("b1", accepted = true)

        // S4339: the acceptance dismiss keeps the endpoint - the Play that follows it still needs it.
        assertEquals(
            listOf(
                WearExchangeCastAction.Dismiss("b1", keepsEndpoint = true),
                WearExchangeCastAction.Answer(offer, true, null),
                WearExchangeCastAction.Play(offer),
            ),
            actions,
        )
        assertTrue(machine.isPlaying)
        assertEquals(listOf(WearExchangeCastAction.Answer(offer, true, null)), machine.onOffer(offer, true, false))
    }

    @Test
    fun `declining answers declined and plays nothing`() {
        machine.onOffer(offer, playable = true, playAtOnce = false)

        val actions = machine.onUserAnswer("b1", accepted = false)

        assertEquals(
            listOf(WearExchangeCastAction.Dismiss("b1"), WearExchangeCastAction.Answer(offer, false, "declined")),
            actions,
        )
        assertFalse(machine.isPlaying)
    }

    @Test
    fun `a stop withdraws the question or the playback`() {
        machine.onOffer(offer, playable = true, playAtOnce = false)
        assertEquals(listOf(WearExchangeCastAction.Dismiss("b1")), machine.onStopped("b1"))

        machine.onOffer(offer, playable = true, playAtOnce = false)
        machine.onUserAnswer("b1", accepted = true)
        assertEquals(listOf(WearExchangeCastAction.StopPlaying("b1")), machine.onStopped("b1"))
        assertFalse(machine.isPlaying)
    }

    @Test
    fun `a lapsed question is dismissed and a late answer changes nothing`() {
        machine.onOffer(offer, playable = true, playAtOnce = false)

        assertEquals(listOf(WearExchangeCastAction.Dismiss("b1")), machine.onExpired("b1"))
        assertEquals(listOf(WearExchangeCastAction.Dismiss("b1")), machine.onUserAnswer("b1", accepted = true))
        assertFalse(machine.isPlaying)
    }

    @Test
    fun `play at once skips the question`() {
        val actions = machine.onOffer(offer, playable = true, playAtOnce = true)

        val expected = listOf(WearExchangeCastAction.Answer(offer, true, null), WearExchangeCastAction.Play(offer))
        assertEquals(expected, actions)
    }
}
