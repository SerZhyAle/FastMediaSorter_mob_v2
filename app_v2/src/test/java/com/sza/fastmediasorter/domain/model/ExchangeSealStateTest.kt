package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeSealStateTest {

    private val entry = DeviceExchangeAccountEntry("exchange.example.net", PORT, "owner", "dev-self", null)

    private fun notice(state: ExchangeSealState, own: Boolean, features: List<String>?) =
        ExchangeSealState.noticeOf(state, own, entry.copy(features = features))

    @Test
    fun `only a server that lists sealed carries content`() {
        assertFalse(entry.serverSeals)
        assertFalse(entry.copy(features = listOf("clipboard")).serverSeals)
        assertTrue(entry.copy(features = listOf("clipboard", "sealed")).serverSeals)
    }

    @Test
    fun `the own line says server-no-e2e once the server answered without sealed`() {
        val answered = listOf("clipboard")

        assertEquals(ExchangeMessage.SERVER_NO_E2E, notice(ExchangeSealState.TRUSTED, true, answered))
        assertNull(notice(ExchangeSealState.TRUSTED, true, null))
        assertNull(notice(ExchangeSealState.TRUSTED, false, answered))
    }

    @Test
    fun `a device line shows e2e-missing and key-changed`() {
        val sealed = listOf("sealed")

        assertEquals(ExchangeMessage.E2E_MISSING, notice(ExchangeSealState.E2E_MISSING, false, sealed))
        assertEquals(ExchangeMessage.KEY_CHANGED, notice(ExchangeSealState.KEY_CHANGED, false, sealed))
        assertNull(notice(ExchangeSealState.TRUSTED, false, sealed))
    }

    private companion object {
        const val PORT = 44022
    }
}
