package com.sza.fastmediasorter.wear.data.network.sftp

import android.net.Uri
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.sza.fastmediasorter.wear.data.network.WearEndpointResolver
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.util.MediaMimeTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Vector
import javax.inject.Inject

private const val CONNECT_TIMEOUT_MS = 30_000

class SftpDataSource @Inject constructor(
    private val endpointResolver: WearEndpointResolver
) {

    suspend fun listDirectory(sourceIn: NetworkSource, path: String): List<WearMediaFile> =
        withContext(Dispatchers.IO) {
            // S2488: the address group is narrowed to the one answering now before anything is opened,
            // and the resolved copy is what the URI below is built from.
            val source = endpointResolver.resolve(sourceIn)
            var session: Session? = null
            var channel: ChannelSftp? = null
            try {
                session = openSession(source)
                channel = openChannel(session)

                @Suppress("UNCHECKED_CAST")
                val entries = channel.ls(path) as? Vector<*>
                    ?: error("SFTP ls returned null for path=$path")

                entries.filterIsInstance<ChannelSftp.LsEntry>()
                    .filterNot { it.filename == "." || it.filename == ".." }
                    .mapIndexed { index, entry ->
                        val filePath = "${path.trimEnd('/')}/${entry.filename}"
                        WearMediaFile(
                            id = index.toLong(),
                            name = entry.filename,
                            uri = Uri.parse("sftp://${source.server}:${source.port}$filePath"),
                            mimeType = MediaMimeTypes.fromFileName(entry.filename),
                            size = entry.attrs.size,
                            dateModified = entry.attrs.mTime.toLong() * 1000L
                        )
                    }
            } finally {
                runCatching { channel?.disconnect() }
                runCatching { session?.disconnect() }
            }
        }

    /**
     * Open a file for reading. The caller owns the returned stream and closes it; the channel and
     * the session are torn down with it.
     */
    suspend fun getFileStream(sourceIn: NetworkSource, path: String): Result<InputStream> =
        withContext(Dispatchers.IO) {
            val source = endpointResolver.resolve(sourceIn)
            var session: Session? = null
            var channel: ChannelSftp? = null
            try {
                session = openSession(source)
                channel = openChannel(session)
                Timber.d("Opening SFTP file: $path")

                val stream = channel.get(path)
                    ?: error("SFTP get returned null for path=$path")

                Result.success(streamClosingSession(stream, channel, session))
            } catch (e: CancellationException) {
                throw e
            } catch (e: JSchException) {
                failStream(e, channel, session, path)
            } catch (e: SftpException) {
                failStream(e, channel, session, path)
            } catch (e: IllegalStateException) {
                failStream(e, channel, session, path)
            }
        }

    private fun failStream(
        cause: Exception,
        channel: ChannelSftp?,
        session: Session?,
        path: String
    ): Result<InputStream> {
        Timber.e(cause, "Failed to open SFTP file stream for path=$path")
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        return Result.failure(cause)
    }

    private fun openSession(source: NetworkSource): Session {
        val jsch = JSch()
        if (!source.sshPrivateKey.isNullOrBlank()) {
            jsch.addIdentity(
                "wear_identity",
                source.sshPrivateKey.toByteArray(),
                null,
                null
            )
        }
        val session = jsch.getSession(source.username, source.server, source.port)
        session.setPassword(source.password)
        WearHostKeyPolicy.apply(session, source)
        session.connect(CONNECT_TIMEOUT_MS)
        return session
    }

    private fun openChannel(session: Session): ChannelSftp =
        (session.openChannel("sftp") as ChannelSftp).apply { connect() }

    /**
     * JSch reads lazily through the channel, so a channel or session disconnected before the caller
     * closes the stream truncates the download.
     */
    private fun streamClosingSession(
        stream: InputStream,
        channel: ChannelSftp,
        session: Session
    ): InputStream = object : FilterInputStream(stream) {
        override fun close() {
            try {
                super.close()
            } finally {
                runCatching { channel.disconnect() }
                runCatching { session.disconnect() }
            }
        }
    }
}
