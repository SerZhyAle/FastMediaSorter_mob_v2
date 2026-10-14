package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.domain.model.ExchangeCheckEvidence
import com.sza.fastmediasorter.domain.model.ExchangeCheckStep
import com.sza.fastmediasorter.domain.model.ExchangeCheckTarget
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnoseExchangeFailureUseCaseTest {

    private fun derive(
        message: ExchangeMessage,
        host: String?,
        evidence: ExchangeCheckEvidence = ExchangeCheckEvidence(),
        target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
    ) = DiagnoseExchangeFailureUseCase.derive(message, host, target, evidence)

    @Test
    fun `a server on the own network is offered the steps of the own network`() {
        val steps = derive(ExchangeMessage.UNREACHABLE_SERVER, "192.168.1.20").steps
        assertTrue(ExchangeCheckStep.ADDRESS_LAN in steps)
        assertFalse(ExchangeCheckStep.PORT_FORWARD in steps)
        assertEquals(ExchangeCheckStep.PC_ASLEEP, steps.first())
    }

    @Test
    fun `a server reached from outside is offered the steps of the internet`() {
        val steps = derive(ExchangeMessage.UNREACHABLE_SERVER, "box.example.com").steps
        assertTrue(ExchangeCheckStep.PORT_FORWARD in steps)
        assertFalse(ExchangeCheckStep.ADDRESS_LAN in steps)
    }

    @Test
    fun `an unknown host is treated as reached from outside`() {
        val steps = derive(ExchangeMessage.UNREACHABLE_SERVER, null).steps
        assertTrue(ExchangeCheckStep.SERVER_INTERNET in steps)
    }

    @Test
    fun `a literal ipv6 address the device cannot reach names the address family first`() {
        val steps = derive(
            ExchangeMessage.UNREACHABLE_SERVER,
            "[2001:db8::7]",
            ExchangeCheckEvidence(deviceHasIpv6 = false),
        ).steps
        assertEquals(ExchangeCheckStep.IP_FAMILY, steps.first())
    }

    @Test
    fun `a refusal with a reason of its own offers its one step and carries its code`() {
        val diagnosis = derive(ExchangeMessage.BAD_CREDENTIALS, "192.168.1.20")
        assertEquals(listOf(ExchangeCheckStep.CREDENTIALS), diagnosis.steps)
        assertEquals("bad-credentials", diagnosis.code)
    }

    @Test
    fun `a row with no step offers nothing`() {
        assertEquals(emptyList<ExchangeCheckStep>(), derive(ExchangeMessage.LOGIN_TAKEN, "x").steps)
    }
}
