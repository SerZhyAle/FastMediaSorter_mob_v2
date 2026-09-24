package com.sza.fastmediasorter.data.remote.sftp.server

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.sza.fastmediasorter.data.remote.sftp.PinnedHostKeyRepository
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * The SAF-backed file system served end to end: a real MINA SSHD server over [FileServerNode] roots,
 * driven by the same JSch client FMS uses for its SFTP resources, pinned to the server's host key
 * through the fingerprint format the pairing code carries.
 */
class SftpSafFileSystemServerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var photos: File
    private lateinit var outside: File
    private lateinit var server: SshServer
    private lateinit var session: Session
    private lateinit var sftp: ChannelSftp

    @Before
    fun setUp() {
        photos = temp.newFolder("Photos")
        outside = temp.newFolder("Outside")
        File(outside, "secret.txt").writeText("secret")
        File(photos, "cat.jpg").writeText("meow")

        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        server = SshServer.setUpDefaultServer().apply {
            host = LOOPBACK
            port = 0
            keyPairProvider = KeyPairProvider.wrap(keyPair)
            passwordAuthenticator = PasswordAuthenticator { user, password, _ -> user == USER && password == PASSWORD }
            fileSystemFactory = SftpSafFileSystemFactory { listOf(FileServerNode(photos)) }
            subsystemFactories = listOf(
                SftpSubsystemFactory.Builder().withFileSystemAccessor(SftpSafFileSystemAccessor).build(),
            )
        }
        server.start()

        session = JSch().getSession(USER, LOOPBACK, server.port).apply {
            setPassword(PASSWORD)
            hostKeyRepository = PinnedHostKeyRepository(KeyUtils.getFingerPrint(keyPair.public))
            connect(TIMEOUT_MS)
        }
        sftp = (session.openChannel("sftp") as ChannelSftp).apply { connect(TIMEOUT_MS) }
    }

    @After
    fun tearDown() {
        sftp.disconnect()
        session.disconnect()
        server.stop(true)
    }

    private fun names(path: String): List<String> =
        sftp.ls(path).map { it.filename }.filterNot { it == "." || it == ".." }.sorted()

    @Test
    fun `the root lists the shared folders and starts the session there`() {
        assertEquals("/", sftp.pwd())
        assertEquals(listOf("Photos"), names("/"))
        assertTrue(sftp.stat("/Photos").isDir)
    }

    @Test
    fun `a file round-trips through put and get`() {
        sftp.put(ByteArrayInputStream("hello".toByteArray()), "/Photos/new.txt")
        assertEquals("hello", File(photos, "new.txt").readText())

        val out = ByteArrayOutputStream()
        sftp.get("/Photos/cat.jpg", out)
        assertEquals("meow", out.toString())
        assertEquals(4L, sftp.stat("/Photos/cat.jpg").size)
    }

    @Test
    fun `directories are created listed renamed and removed`() {
        sftp.mkdir("/Photos/album")
        sftp.put(ByteArrayInputStream("x".toByteArray()), "/Photos/album/a.jpg")
        assertEquals(listOf("a.jpg"), names("/Photos/album"))

        sftp.rename("/Photos/album/a.jpg", "/Photos/b.jpg")
        assertTrue(File(photos, "b.jpg").exists())
        sftp.rmdir("/Photos/album")
        assertFalse(File(photos, "album").exists())
        sftp.rm("/Photos/b.jpg")
        assertFalse(File(photos, "b.jpg").exists())
    }

    @Test
    fun `a non-empty directory is not removed recursively`() {
        sftp.mkdir("/Photos/keep")
        sftp.put(ByteArrayInputStream("x".toByteArray()), "/Photos/keep/a.jpg")
        assertThrows(SftpException::class.java) { sftp.rmdir("/Photos/keep") }
        assertTrue(File(photos, "keep/a.jpg").exists())
    }

    @Test
    fun `nothing outside the shared folders is reachable`() {
        assertThrows(SftpException::class.java) { sftp.get("/../Outside/secret.txt", ByteArrayOutputStream()) }
        assertThrows(
            SftpException::class.java
        ) { sftp.get("/Photos/../../Outside/secret.txt", ByteArrayOutputStream()) }
        assertThrows(SftpException::class.java) { sftp.ls("/Outside") }
        sftp.cd("/..")
        assertEquals("/", sftp.pwd())
    }

    @Test
    fun `the root and the shared folders themselves cannot be changed`() {
        assertThrows(SftpException::class.java) { sftp.mkdir("/NewTop") }
        assertThrows(SftpException::class.java) { sftp.rmdir("/Photos") }
        assertThrows(SftpException::class.java) { sftp.rename("/Photos", "/Other") }
        assertThrows(SftpException::class.java) {
            sftp.put(ByteArrayInputStream("x".toByteArray()), "/top.txt")
        }
        assertTrue(photos.exists())
    }

    @Test
    fun `a modification time set after upload is accepted and ignored`() {
        sftp.put(ByteArrayInputStream("x".toByteArray()), "/Photos/t.txt")
        sftp.setMtime("/Photos/t.txt", MTIME_SECONDS)
        assertTrue(File(photos, "t.txt").exists())
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val USER = "fms"
        const val PASSWORD = "secret-pass"
        const val TIMEOUT_MS = 10_000
        const val MTIME_SECONDS = 1_700_000_000
    }
}
