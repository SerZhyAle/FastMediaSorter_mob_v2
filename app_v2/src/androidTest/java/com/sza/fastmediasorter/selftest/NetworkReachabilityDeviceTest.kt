package com.sza.fastmediasorter.selftest

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.sza.fastmediasorter.selftest.SelfTestNetworkConfig.Protocol
import org.apache.commons.net.ftp.FTPClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * S3741 / S3757: every provisioned network endpoint answers on its port and supports directory listing
 * from this device. A protocol with no endpoint in the pushed credentials file is skipped as
 * "network not provisioned", never passed.
 */
@RunWith(JUnit4::class)
class NetworkReachabilityDeviceTest {

    @get:Rule
    val baselineRule = SelfTestBaselineRule()

    @Test
    fun smbAnswers() = assertReachable(Protocol.SMB, SMB_PORT)

    @Test
    fun smbListDirectory() {
        val endpoint = SelfTestNetworkConfig.assumeProtocol(Protocol.SMB)
        val smbConfig = SmbConfig.builder()
            .withTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .withSoTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val client = SMBClient(smbConfig)
        client.connect(endpoint.host, endpoint.port ?: SMB_PORT).use { connection ->
            val authContext = if (endpoint.user.isNotBlank()) {
                AuthenticationContext(endpoint.user, endpoint.password.toCharArray(), "")
            } else {
                AuthenticationContext.anonymous()
            }
            val session = connection.authenticate(authContext)
            val segments = endpoint.path.trim('/', '\\').split('/', '\\').filter { it.isNotBlank() }
            val shareName = segments.firstOrNull() ?: "public"
            val subPath = if (segments.size > 1) segments.drop(1).joinToString("/") else ""
            (session.connectShare(shareName) as? DiskShare)?.use { diskShare ->
                val list = diskShare.list(subPath)
                assertNotNull("SMB directory listing should not be null", list)
            } ?: org.junit.Assert.fail("Failed to connect to SMB share: $shareName")
        }
    }

    @Test
    fun sftpAnswers() = assertReachable(Protocol.SFTP, SFTP_PORT)

    @Test
    fun sftpListDirectory() {
        val endpoint = SelfTestNetworkConfig.assumeProtocol(Protocol.SFTP)
        val jsch = JSch()
        val user = endpoint.user.ifBlank { "anonymous" }
        val session = jsch.getSession(user, endpoint.host, endpoint.port ?: SFTP_PORT)
        session.setPassword(endpoint.password)
        session.setConfig("StrictHostKeyChecking", "no")
        session.timeout = CONNECT_TIMEOUT_MS
        session.connect(CONNECT_TIMEOUT_MS)
        try {
            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(CONNECT_TIMEOUT_MS)
            try {
                val path = endpoint.path.ifBlank { "." }
                val entries = channel.ls(path)
                assertNotNull("SFTP directory listing should not be null", entries)
            } finally {
                channel.disconnect()
            }
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun ftpAnswers() = assertReachable(Protocol.FTP, FTP_PORT)

    @Test
    fun ftpListDirectory() {
        val endpoint = SelfTestNetworkConfig.assumeProtocol(Protocol.FTP)
        val ftp = FTPClient()
        ftp.connectTimeout = CONNECT_TIMEOUT_MS
        ftp.defaultTimeout = CONNECT_TIMEOUT_MS
        ftp.connect(endpoint.host, endpoint.port ?: FTP_PORT)
        try {
            val user = endpoint.user.ifBlank { "anonymous" }
            val loginSuccess = ftp.login(user, endpoint.password)
            assertTrue("FTP login failed for user $user", loginSuccess)
            ftp.enterLocalPassiveMode()
            val path = endpoint.path.ifBlank { "." }
            val files = ftp.listFiles(path)
            assertNotNull("FTP directory listing should not be null", files)
        } finally {
            if (ftp.isConnected) {
                try {
                    ftp.disconnect()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun assertReachable(protocol: Protocol, defaultPort: Int) {
        val endpoint = SelfTestNetworkConfig.assumeProtocol(protocol)
        Socket().use { it.connect(InetSocketAddress(endpoint.host, endpoint.port ?: defaultPort), CONNECT_TIMEOUT_MS) }
    }

    private companion object {
        const val SMB_PORT = 445
        const val SFTP_PORT = 22
        const val FTP_PORT = 21
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
