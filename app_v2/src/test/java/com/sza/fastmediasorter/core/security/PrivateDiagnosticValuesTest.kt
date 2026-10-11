package com.sza.fastmediasorter.core.security

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeCredential
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** DIAGNOSTIC-REPORT section 10 (0.13): a login and a server address are redacted by value, not by shape. */
class PrivateDiagnosticValuesTest {

    @Before
    fun setUp() {
        PrivateDiagnosticValues.clear()
    }

    @After
    fun tearDown() {
        PrivateDiagnosticValues.clear()
    }

    @Test
    fun `nothing registered leaves text untouched`() {
        assertTrue(PrivateDiagnosticValues.isEmpty)
        val text = "failed to connect to relay.example.net"
        assertEquals(text, PrivateDiagnosticValues.redact(text))
    }

    @Test
    fun `a registered host and login inside an exception message are redacted by the sink`() {
        PrivateDiagnosticValues.register("relay.example.net", "alice.home", "203.0.113.7")

        val out = SecretMasker.sanitize(
            "failed to connect to relay.example.net/203.0.113.7 (port 44022) as alice.home.",
        )

        assertEquals("failed to connect to [REDACTED]/[REDACTED] (port 44022) as [REDACTED].", out)
    }

    @Test
    fun `a value inside a longer token is left alone`() {
        PrivateDiagnosticValues.register("nas")

        assertEquals("nasty nas.local [REDACTED]:22", PrivateDiagnosticValues.redact("nasty nas.local nas:22"))
    }

    @Test
    fun `a value shorter than three characters is ignored`() {
        PrivateDiagnosticValues.register("ab", "  ", null)

        assertTrue(PrivateDiagnosticValues.isEmpty)
        assertEquals("ab", PrivateDiagnosticValues.redact("ab"))
    }

    @Test
    fun `matching ignores case and prefers the longest value`() {
        PrivateDiagnosticValues.register("relay", "relay.example.net")

        val out = PrivateDiagnosticValues.redact("RELAY.EXAMPLE.NET and relay")

        assertEquals("[REDACTED] and [REDACTED]", out)
        assertFalse(out.contains("example"))
    }

    @Test
    fun `a pairing code credential registers itself`() {
        DeviceExchangeCredential.PairingCode("7KQ2M9XA")

        assertEquals("code [REDACTED] sent", SecretMasker.sanitize("code 7KQ2M9XA sent"))
    }
}
