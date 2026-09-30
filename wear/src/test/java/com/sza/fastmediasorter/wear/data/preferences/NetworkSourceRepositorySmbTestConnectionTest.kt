package com.sza.fastmediasorter.wear.data.preferences

import android.content.SharedPreferences
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.wear.data.network.WearEndpointResolver
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbLink
import com.sza.fastmediasorter.wear.data.network.smb.SmbLinkOpener
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSourceRepositorySmbTestConnectionTest {

    private val resolver = mockk<WearEndpointResolver> {
        coEvery { resolve(any()) } answers { firstArg() }
    }

    @Test
    fun `testing an SMB source leaves the shared session open`() {
        val sharedOpener = FakeOpener()
        val shared = SmbDataSource(resolver, sharedOpener)
        runBlocking { shared.connect(SOURCE) }
        val probeOpener = FakeOpener()
        val repository = repository { SmbDataSource(resolver, probeOpener) }

        val result = runBlocking { repository.testConnection(SOURCE) }

        assertEquals(Result.success(true), result)
        assertTrue("the shared session survives the test", shared.isConnected())
        assertEquals(0, sharedOpener.links.single().closes)
        assertEquals("the probe's own link is closed", 1, probeOpener.links.single().closes)
    }

    @Test
    fun `every SMB test gets its own probe`() {
        var probes = 0
        val repository = repository {
            probes++
            SmbDataSource(resolver, FakeOpener())
        }

        runBlocking {
            repository.testConnection(SOURCE)
            repository.testConnection(SOURCE)
        }

        assertEquals(2, probes)
    }

    @Test
    fun `a probe that fails to connect reports the failure`() {
        val failing = SmbLinkOpener { error("server refused") }
        val repository = repository { SmbDataSource(resolver, failing) }

        val result = runBlocking { repository.testConnection(SOURCE) }

        assertTrue(result.isFailure)
    }

    private fun repository(newProbe: () -> SmbDataSource) = NetworkSourceRepositoryImpl(
        encryptedPrefs = mockk<SharedPreferences>(relaxed = true),
        newSmbProbe = newProbe,
        ftpConnectionTest = mockk(),
        sftpConnectionTest = mockk()
    )

    private class FakeOpener : SmbLinkOpener {
        val links = mutableListOf<FakeLink>()

        override fun open(source: NetworkSource): SmbLink = FakeLink().also { links += it }
    }

    private class FakeLink : SmbLink {
        var closes = 0
        private var open = true

        override val share: DiskShare = mockk(relaxed = true)

        override val isAlive: Boolean
            get() = open

        override fun close() {
            closes++
            open = false
        }
    }

    companion object {
        private val SOURCE = NetworkSource(
            type = NetworkSourceType.SMB,
            name = "nas",
            server = "192.0.2.10",
            username = "user",
            password = "secret",
            shareName = "media"
        )
    }
}
