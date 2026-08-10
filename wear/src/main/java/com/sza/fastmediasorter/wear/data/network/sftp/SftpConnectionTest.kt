package com.sza.fastmediasorter.wear.data.network.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * SFTP connection test for Wear OS network sources.
 *
 * Mirrors [SftpDataSource]'s connect path deliberately: a test that is stricter than browsing would
 * reject sources that open fine, which is worse than no test at all. Two differences from browsing -
 * the base path is probed with stat instead of a directory listing, so nothing is pulled over the
 * watch radio, and the connect timeout is shorter because a human is waiting on the result.
 *
 * Host-key checking is left permissive to match the browse path; tightening both together, plus a
 * place to store the expected key, is S1555.
 */
class SftpConnectionTest @Inject constructor() {

    suspend fun testSftp(source: NetworkSource): Result<Boolean> = withContext(Dispatchers.IO) {
        val jsch = JSch()
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            if (!source.sshPrivateKey.isNullOrBlank()) {
                jsch.addIdentity(
                    "wear_identity",
                    source.sshPrivateKey.toByteArray(),
                    null,
                    null
                )
            }
            session = jsch.getSession(source.username, source.server, source.port)
            session.setPassword(source.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(CONNECT_TIMEOUT_MS)

            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            channel.stat(source.basePath)

            Result.success(true)
        } catch (e: JSchException) {
            failure(source, e)
        } catch (e: SftpException) {
            failure(source, e)
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { session?.disconnect() }
        }
    }

    /**
     * Only the two JSch exception families are caught: everything else is a programming error rather
     * than a server answer, and the repository's own wrapper already turns it into a failed Result.
     */
    private fun failure(source: NetworkSource, e: Exception): Result<Boolean> {
        Timber.w(e, "SftpConnectionTest: test failed for source '${source.name}'")
        return Result.failure(e)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
    }
}
