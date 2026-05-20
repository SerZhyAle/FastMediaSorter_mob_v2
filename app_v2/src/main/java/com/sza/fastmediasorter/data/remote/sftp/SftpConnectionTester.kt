package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Stateless SFTP test-connection helpers and recursive `mkdir -p` for SftpClient.
 *
 * Both test methods open a fresh JSch session, connect, and tear down - never touching the
 * client's pool. Used during AddResource flow to validate credentials before persisting.
 *
 * Extracted to keep SftpClient below the 1000-line cap.
 */
object SftpConnectionTester {

    private const val CONNECTION_TIMEOUT = 10_000

    /** Test password-based SFTP connection. Returns [Result.failure] on any error. */
    suspend fun testConnection(
        host: String,
        port: Int = 22,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var testSession: Session? = null
        var testChannel: ChannelSftp? = null
        try {
            val testJsch = JSch()
            testSession = testJsch.getSession(username, host, port)
            testSession.setPassword(password)

            testSession.userInfo = object : UserInfo {
                override fun getPassphrase(): String? = null
                override fun getPassword(): String = password
                override fun promptPassword(message: String?): Boolean = true
                override fun promptPassphrase(message: String?): Boolean = false
                override fun promptYesNo(message: String?): Boolean = true
                override fun showMessage(message: String?) {}
            }

            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            config["PreferredAuthentications"] = "keyboard-interactive,password"
            testSession.setConfig(config)

            testSession.timeout = CONNECTION_TIMEOUT
            testSession.connect(CONNECTION_TIMEOUT)

            testChannel = testSession.openChannel("sftp") as ChannelSftp
            testChannel.connect(CONNECTION_TIMEOUT)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                testChannel?.disconnect()
                testSession?.disconnect()
            } catch (_: Exception) {
                // ignore teardown errors - the connect attempt already determined success/failure
            }
        }
    }

    /** Test public-key SFTP connection. [passphrase] is required only for encrypted keys. */
    suspend fun testConnectionWithPrivateKey(
        host: String,
        port: Int = 22,
        username: String,
        privateKey: String,
        passphrase: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var testSession: Session? = null
        var testChannel: ChannelSftp? = null
        try {
            val testJsch = JSch()
            if (passphrase != null) {
                testJsch.addIdentity("test_key", privateKey.toByteArray(), null, passphrase.toByteArray())
            } else {
                testJsch.addIdentity("test_key", privateKey.toByteArray(), null, null)
            }

            testSession = testJsch.getSession(username, host, port)

            if (passphrase != null) {
                testSession.userInfo = object : UserInfo {
                    override fun getPassphrase(): String = passphrase
                    override fun getPassword(): String? = null
                    override fun promptPassword(message: String?): Boolean = false
                    override fun promptPassphrase(message: String?): Boolean = true
                    override fun promptYesNo(message: String?): Boolean = true
                    override fun showMessage(message: String?) {}
                }
            }

            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            config["PreferredAuthentications"] = "publickey"
            testSession.setConfig(config)

            testSession.timeout = CONNECTION_TIMEOUT
            testSession.connect(CONNECTION_TIMEOUT)

            testChannel = testSession.openChannel("sftp") as ChannelSftp
            testChannel.connect(CONNECTION_TIMEOUT)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                testChannel?.disconnect()
                testSession?.disconnect()
            } catch (_: Exception) {
                // ignore teardown errors
            }
        }
    }

    /**
     * Recursive `mkdir -p` over an SFTP channel. `cd` is used for existence probing because
     * JSch lacks a direct `stat` for missing paths; race-tolerant against concurrent creators.
     */
    fun ensureDirectoryExists(channel: ChannelSftp, remotePath: String) {
        try {
            channel.cd(remotePath)
            return
        } catch (_: Exception) {
            // Directory doesn't exist - recurse into parent first
            val parent = remotePath.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) {
                ensureDirectoryExists(channel, parent)
            }

            try {
                channel.mkdir(remotePath)
                Timber.d("Created SFTP directory: $remotePath")
            } catch (mkdirEx: Exception) {
                // Another process may have created it between our check and mkdir - re-probe.
                try {
                    channel.cd(remotePath)
                } catch (_: Exception) {
                    throw mkdirEx
                }
            }
        }
    }
}
