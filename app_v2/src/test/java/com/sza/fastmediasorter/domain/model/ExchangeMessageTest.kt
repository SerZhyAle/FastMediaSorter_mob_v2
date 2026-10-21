package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeMessageTest {

    @Test
    fun `every table row names the step of item CZ`() {
        val expected = mapOf(
            ExchangeMessage.BAD_CREDENTIALS to "credentials",
            ExchangeMessage.BAD_CREDENTIALS_ONE_TIME_CODE to "credentials",
            ExchangeMessage.DEVICE_REVOKED to "device-removed",
            ExchangeMessage.SIGNED_OUT to "credentials",
            ExchangeMessage.VERSION_UNSUPPORTED to "operator",
            ExchangeMessage.TLS_REQUIRED to "operator",
            ExchangeMessage.ID_COLLISION to null,
            ExchangeMessage.CAPACITY to "operator",
            ExchangeMessage.PORT_UNAVAILABLE to "operator",
            ExchangeMessage.UNAVAILABLE to "pc-asleep",
            ExchangeMessage.RATE_LIMITED to "sign-in-rate",
            ExchangeMessage.PROTECTION_UNSUPPORTED to null,
            ExchangeMessage.INVITATION_INVALID to "invitation",
            ExchangeMessage.LOGIN_TAKEN to null,
            ExchangeMessage.PASSWORD_POLICY to null,
            ExchangeMessage.LIMIT_REACHED to "operator",
            ExchangeMessage.LIMIT_REACHED_CLIPBOARD to null,
            ExchangeMessage.NOT_PERMITTED to null,
            ExchangeMessage.DECLINED to null,
            ExchangeMessage.TIMEOUT to null,
            ExchangeMessage.UNSUPPORTED to null,
            ExchangeMessage.UNREACHABLE_SERVER to "pc-asleep",
            ExchangeMessage.UNREACHABLE_DEVICE to "pc-asleep",
            ExchangeMessage.UNANSWERED to "operator",
            ExchangeMessage.TLS_FAILED to "device-clock",
            ExchangeMessage.CERTIFICATE_CHANGED to "certificate",
            ExchangeMessage.EMPTY_CREDENTIAL to null,
            ExchangeMessage.SERVER_OFFLINE to "pc-asleep",
            ExchangeMessage.RESOURCE_LOCKED to null,
            ExchangeMessage.CLIPBOARD_EMPTY to null,
            ExchangeMessage.E2E_MISSING to null,
            ExchangeMessage.KEY_CHANGED to null,
            ExchangeMessage.SERVER_NO_E2E to "operator",
            ExchangeMessage.SEALED_FAILED to null,
            ExchangeMessage.FORMAT_UNSUPPORTED to null,
            ExchangeMessage.REFUSED to "operator",
            ExchangeMessage.ADMIN_DISABLED to null,
            ExchangeMessage.TOKEN_NOT_STORED to null,
        )
        assertEquals(ExchangeMessage.entries.toSet(), expected.keys)
        expected.forEach { (message, stepId) -> assertEquals(message.name, stepId, message.step?.id) }
    }

    @Test
    fun `a wire reason maps to its own row`() {
        assertEquals(ExchangeMessage.BAD_CREDENTIALS, ExchangeMessage.ofWire("bad-credentials"))
        assertEquals(ExchangeMessage.DEVICE_REVOKED, ExchangeMessage.ofWire("device-revoked"))
        assertEquals(ExchangeMessage.SIGNED_OUT, ExchangeMessage.ofWire("signed-out"))
        assertEquals(ExchangeMessage.VERSION_UNSUPPORTED, ExchangeMessage.ofWire("version-unsupported"))
        assertEquals(ExchangeMessage.TLS_REQUIRED, ExchangeMessage.ofWire("tls-required"))
        assertEquals(ExchangeMessage.ID_COLLISION, ExchangeMessage.ofWire("id-collision"))
        assertEquals(ExchangeMessage.CAPACITY, ExchangeMessage.ofWire("capacity"))
        assertEquals(ExchangeMessage.PORT_UNAVAILABLE, ExchangeMessage.ofWire("port-unavailable"))
        assertEquals(ExchangeMessage.UNAVAILABLE, ExchangeMessage.ofWire("unavailable"))
        assertEquals(ExchangeMessage.RATE_LIMITED, ExchangeMessage.ofWire("rate-limited"))
        assertEquals(ExchangeMessage.PROTECTION_UNSUPPORTED, ExchangeMessage.ofWire("protection-unsupported"))
        assertEquals(ExchangeMessage.INVITATION_INVALID, ExchangeMessage.ofWire("invitation-invalid"))
        assertEquals(ExchangeMessage.LOGIN_TAKEN, ExchangeMessage.ofWire("login-taken"))
        assertEquals(ExchangeMessage.PASSWORD_POLICY, ExchangeMessage.ofWire("password-policy"))
        assertEquals(ExchangeMessage.LIMIT_REACHED, ExchangeMessage.ofWire("limit-reached"))
        assertEquals(ExchangeMessage.NOT_PERMITTED, ExchangeMessage.ofWire("not-permitted"))
    }

    @Test
    fun `the two flags pick the variant text`() {
        assertEquals(
            ExchangeMessage.BAD_CREDENTIALS_ONE_TIME_CODE,
            ExchangeMessage.ofWire("bad-credentials", oneTimeCode = true),
        )
        assertEquals(
            ExchangeMessage.LIMIT_REACHED_CLIPBOARD,
            ExchangeMessage.ofWire("limit-reached", clipboardSend = true),
        )
        assertEquals(ExchangeMessage.BAD_CREDENTIALS, ExchangeMessage.ofWire("bad-credentials", clipboardSend = true))
    }

    @Test
    fun `an entry without a token reads as a removed device and the secret store keeps its own row`() {
        assertEquals(ExchangeMessage.DEVICE_REVOKED, ExchangeMessage.ofWire("not-enrolled"))
        assertEquals(ExchangeMessage.TOKEN_NOT_STORED, ExchangeMessage.ofWire("token-not-stored"))
    }

    @Test
    fun `an unknown or empty reason is the generic refusal`() {
        assertEquals(ExchangeMessage.REFUSED, ExchangeMessage.ofWire("something-new"))
        assertEquals(ExchangeMessage.REFUSED, ExchangeMessage.ofWire(""))
        assertEquals(ExchangeMessage.REFUSED, ExchangeMessage.ofWire("declined"))
    }

    @Test
    fun `a cast reason maps to its row and anything else is the generic refusal`() {
        assertEquals(ExchangeMessage.UNAVAILABLE, ExchangeMessage.ofCast("unavailable"))
        assertEquals(ExchangeMessage.DECLINED, ExchangeMessage.ofCast("declined"))
        assertEquals(ExchangeMessage.TIMEOUT, ExchangeMessage.ofCast("timeout"))
        assertEquals(ExchangeMessage.UNSUPPORTED, ExchangeMessage.ofCast("unsupported"))
        assertEquals(ExchangeMessage.REFUSED, ExchangeMessage.ofCast("something-new"))
        assertEquals(ExchangeMessage.REFUSED, ExchangeMessage.ofCast(null))
    }

    @Test
    fun `only the three failures with no reason of their own on the wire are walked`() {
        val walked = ExchangeMessage.entries.filter { it.walked }
        assertEquals(
            setOf(
                ExchangeMessage.UNREACHABLE_SERVER,
                ExchangeMessage.UNREACHABLE_DEVICE,
                ExchangeMessage.UNANSWERED,
                ExchangeMessage.TLS_FAILED,
            ),
            walked.toSet(),
        )
        assertTrue(ExchangeMessage.TLS_FAILED.walked)
        assertFalse(ExchangeMessage.SERVER_OFFLINE.walked)
    }
}
