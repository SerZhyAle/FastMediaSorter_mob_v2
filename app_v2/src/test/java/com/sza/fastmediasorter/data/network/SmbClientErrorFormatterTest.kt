package com.sza.fastmediasorter.data.network

import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [SmbClientErrorFormatter] message classification and diagnostic blob. */
class SmbClientErrorFormatterTest {

    // ── getUserFriendlyMessage ───────────────────────────────────────────────

    @Test
    fun `connection reset produces temporary multi-line message`() {
        val msg = SmbClientErrorFormatter.getUserFriendlyMessage(Exception("Connection reset by peer"))
        assertTrue(msg.contains("temporary"))
        assertTrue(msg.contains("Try:"))
    }

    @Test
    fun `connection reset detected via cause chain`() {
        val msg = SmbClientErrorFormatter.getUserFriendlyMessage(
            Exception("outer", Exception("Connection reset"))
        )
        assertTrue(msg.contains("temporary"))
    }

    @Test
    fun `timeout produces timeout guidance`() {
        val msg = SmbClientErrorFormatter.getUserFriendlyMessage(Exception("TimeoutException occurred"))
        assertTrue(msg.contains("timeout", ignoreCase = true))
    }

    @Test
    fun `bad network name produces share-not-found message`() {
        val msg = SmbClientErrorFormatter.getUserFriendlyMessage(Exception("STATUS_BAD_NETWORK_NAME"))
        assertTrue(msg.contains("Share not found"))
    }

    @Test
    fun `logon failure produces auth message`() {
        val msg = SmbClientErrorFormatter.getUserFriendlyMessage(Exception("STATUS_LOGON_FAILURE"))
        assertEquals("Authentication failed. Check username and password.", msg)
    }

    @Test
    fun `access denied produces permissions message`() {
        val msg = SmbClientErrorFormatter.getUserFriendlyMessage(Exception("STATUS_ACCESS_DENIED"))
        assertEquals("Access denied. Check share permissions.", msg)
    }

    @Test
    fun `connect exception produces reach-server message`() {
        val msg = SmbClientErrorFormatter.getUserFriendlyMessage(Exception("ConnectException"))
        assertEquals("Cannot reach server. Check network connection.", msg)
    }

    @Test
    fun `connect exception with NoRouteToHost still maps to reach-server message`() {
        val msg = SmbClientErrorFormatter.getUserFriendlyMessage(Exception("NoRouteToHostException"))
        assertEquals("Cannot reach server. Check network connection.", msg)
    }

    @Test
    fun `unknown host exception produces address message`() {
        val msg = SmbClientErrorFormatter.getUserFriendlyMessage(Exception("UnknownHostException"))
        assertEquals("Server address not found. Check server name/IP.", msg)
    }

    @Test
    fun `unmatched error produces generic fallback`() {
        val msg = SmbClientErrorFormatter.getUserFriendlyMessage(Exception("nothing recognizable"))
        assertEquals("Resource unavailable. Check connection settings.", msg)
    }

    // ── buildDiagnosticMessage ───────────────────────────────────────────────

    @Test
    fun `diagnostic blob includes server share and error class`() {
        val info = SmbConnectionInfo(server = "192.168.1.10", shareName = "media", username = "bob", port = 445)
        val blob = SmbClientErrorFormatter.buildDiagnosticMessage(IllegalStateException("boom"), info)
        assertTrue(blob.contains("192.168.1.10:445"))
        assertTrue(blob.contains("Share: media"))
        assertTrue(blob.contains("Username: bob"))
        assertTrue(blob.contains("IllegalStateException"))
        assertTrue(blob.contains("Message: boom"))
    }

    @Test
    fun `diagnostic blob renders anonymous for empty username`() {
        val info = SmbConnectionInfo(server = "host", shareName = "s", username = "", port = 139)
        val blob = SmbClientErrorFormatter.buildDiagnosticMessage(Exception("e"), info)
        assertTrue(blob.contains("Username: anonymous"))
        assertTrue(blob.contains("SMB port 139"))
    }
}
