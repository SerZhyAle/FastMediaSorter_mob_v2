package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.HostKeyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

/** Unit tests for [PinnedHostKeyRepository] - SHA256 pin verification + inert stubs. */
class PinnedHostKeyRepositoryTest {

    /** Canonical fingerprint computed exactly as the production check() does. */
    private fun canonical(key: ByteArray): String {
        val sha = MessageDigest.getInstance("SHA-256").digest(key)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(sha)
    }

    @Test
    fun `constructor rejects non-canonical expected fingerprint`() {
        assertThrows(IllegalArgumentException::class.java) {
            PinnedHostKeyRepository("not-a-fingerprint")
        }
    }

    @Test
    fun `matching key returns OK`() {
        val key = byteArrayOf(1, 2, 3, 4, 5)
        val repo = PinnedHostKeyRepository(canonical(key))
        assertEquals(HostKeyRepository.OK, repo.check("host", key))
    }

    @Test
    fun `mismatched key returns CHANGED`() {
        val pinned = byteArrayOf(1, 2, 3)
        val repo = PinnedHostKeyRepository(canonical(pinned))
        assertEquals(HostKeyRepository.CHANGED, repo.check("host", byteArrayOf(9, 9, 9)))
    }

    @Test
    fun `null key returns CHANGED`() {
        val repo = PinnedHostKeyRepository(canonical(byteArrayOf(1)))
        assertEquals(HostKeyRepository.CHANGED, repo.check("host", null))
    }

    @Test
    fun `empty key returns CHANGED`() {
        val repo = PinnedHostKeyRepository(canonical(byteArrayOf(1)))
        assertEquals(HostKeyRepository.CHANGED, repo.check("host", ByteArray(0)))
    }

    @Test
    fun `read-only stubs return inert defaults`() {
        val repo = PinnedHostKeyRepository(canonical(byteArrayOf(1)))
        assertEquals(0, repo.hostKey.size)
        assertEquals(0, repo.getHostKey("host", "ssh-rsa").size)
        assertEquals("PinnedHostKeyRepository(S0046)", repo.knownHostsRepositoryID)
        // add/remove are no-ops and must not throw.
        repo.add(null, null)
        repo.remove("host", "ssh-rsa")
        repo.remove("host", "ssh-rsa", byteArrayOf(1))
    }
}
