package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.ChannelSftp
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SftpConnectionTester]. The recursive `mkdir -p` over a mocked JSch [ChannelSftp]
 * is fully covered; `testConnectionWithPrivateKey` is covered only on its local key-parse failure
 * branch (a malformed key throws before any socket attempt). Live connect paths are out of scope.
 */
class SftpConnectionTesterTest {

    // ── ensureDirectoryExists ────────────────────────────────────────────────────

    @Test
    fun `ensureDirectoryExists returns immediately when directory exists`() {
        val channel = mockk<ChannelSftp>()
        every { channel.cd("/data/media") } just Runs
        SftpConnectionTester.ensureDirectoryExists(channel, "/data/media")
        verify(exactly = 1) { channel.cd("/data/media") }
        verify(exactly = 0) { channel.mkdir(any()) }
    }

    @Test
    fun `ensureDirectoryExists creates missing parent chain then leaf`() {
        val channel = mockk<ChannelSftp>()
        // Leaf cd fails, parent cd fails, grandparent cd succeeds.
        every { channel.cd("/a/b/c") } throws RuntimeException("no dir")
        every { channel.cd("/a/b") } throws RuntimeException("no dir")
        every { channel.cd("/a") } just Runs
        every { channel.mkdir(any()) } just Runs
        SftpConnectionTester.ensureDirectoryExists(channel, "/a/b/c")
        verifyOrder {
            channel.mkdir("/a/b")
            channel.mkdir("/a/b/c")
        }
    }

    @Test
    fun `ensureDirectoryExists tolerates concurrent creation via re-probe`() {
        val channel = mockk<ChannelSftp>()
        every { channel.cd("/a") } throws RuntimeException("missing") andThen Unit
        // mkdir fails (another process created it) but the follow-up cd succeeds → swallowed.
        every { channel.mkdir("/a") } throws RuntimeException("already exists")
        SftpConnectionTester.ensureDirectoryExists(channel, "/a")
        verify { channel.mkdir("/a") }
    }

    @Test
    fun `ensureDirectoryExists rethrows when mkdir fails and re-probe fails`() {
        val channel = mockk<ChannelSftp>()
        every { channel.cd("/a") } throws RuntimeException("missing")
        every { channel.mkdir("/a") } throws IllegalStateException("permission denied")
        var thrown: Throwable? = null
        try {
            SftpConnectionTester.ensureDirectoryExists(channel, "/a")
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is IllegalStateException)
    }

    // ── testConnectionWithPrivateKey local-failure branch ────────────────────────

    @Test
    fun `testConnectionWithPrivateKey fails on malformed key without opening a socket`() = runTest {
        val result = SftpConnectionTester.testConnectionWithPrivateKey(
            host = "203.0.113.1", // TEST-NET-3 reserved - never reachable, but parse fails first
            port = 22,
            username = "user",
            privateKey = "not-a-real-private-key",
            passphrase = null
        )
        assertTrue(result.isFailure)
    }
}
