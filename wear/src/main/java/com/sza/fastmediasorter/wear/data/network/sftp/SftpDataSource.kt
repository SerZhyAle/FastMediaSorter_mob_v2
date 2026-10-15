package com.sza.fastmediasorter.wear.data.network.sftp

import android.net.Network
import android.net.Uri
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeConnection
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeTunnelClient
import com.sza.fastmediasorter.wear.data.exchange.WearFolderIdentity
import com.sza.fastmediasorter.wear.data.exchange.WearResourcePinStore
import com.sza.fastmediasorter.wear.data.network.WearEndpointResolver
import com.sza.fastmediasorter.wear.data.network.WearSftpRoute
import com.sza.fastmediasorter.wear.domain.files.WearFileNameConflictResolver
import com.sza.fastmediasorter.wear.domain.files.WearRemoteFileNames
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearNetworkEntry
import com.sza.fastmediasorter.wear.domain.model.WearNetworkEntry.Companion.PARENT_ENTRY
import com.sza.fastmediasorter.wear.domain.model.WearNetworkEntry.Companion.SELF_ENTRY
import com.sza.fastmediasorter.wear.util.MediaMimeTypes
import com.sza.fastmediasorter.wear.util.handingOffCloseable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Vector
import javax.inject.Inject

private const val CONNECT_TIMEOUT_MS = 30_000

/** SFTP reports modification time in whole seconds; the rest of the module works in millis. */
private const val MILLIS_PER_SECOND = 1000L

/** S4228: the root proof is a keyboard-interactive exchange; the other two stay as the fallback order. */
private const val ROOT_PROOF_AUTHENTICATIONS = "keyboard-interactive,password,publickey"

/**
 * S4228: a protected exchange folder whose PIN has not come from the phone yet. Raised before any connection, so the
 * browse error can say the PIN is entered on the phone rather than report a generic connection failure.
 */
class WearResourcePinMissingException : IllegalStateException("The folder's PIN has not been received from the phone")

class SftpDataSource @Inject constructor(
    private val endpointResolver: WearEndpointResolver,
    private val pinStore: WearResourcePinStore,
    private val tunnelClient: WearExchangeTunnelClient
) {

    suspend fun listDirectory(sourceIn: NetworkSource, path: String): List<WearMediaFile> {
        // S2488: the address group is narrowed to the one answering now before anything is opened,
        // and the resolved copy is what the URI below is built from. S2694: resolved once here and
        // handed down, so the listing and the uri cannot name different endpoints of one group.
        val route = endpointResolver.route(sourceIn)
        val source = route.source
        return listEntriesResolved(route, path).mapIndexed { index, entry ->
            WearMediaFile(
                id = index.toLong(),
                name = entry.name,
                uri = Uri.parse("sftp://${source.server}:${source.port}${entry.path}"),
                mimeType = MediaMimeTypes.fromFileName(entry.name),
                size = entry.sizeBytes,
                dateModified = entry.dateModifiedEpochMillis
            )
        }
    }

    /**
     * S2694: the directory listing with the directory flag kept, which [listDirectory] then flattens.
     *
     * @param path Absolute server path
     */
    suspend fun listEntries(sourceIn: NetworkSource, path: String): List<WearNetworkEntry> =
        listEntriesResolved(endpointResolver.route(sourceIn), path)

    /**
     * The pure half of the SFTP listing: the path join and the seconds-to-millis conversion.
     *
     * The flag itself is read from a JSch entry at the call site, and JSch's entry type cannot be
     * constructed outside the library, so that one read stays uncovered by unit test - the walk on a
     * real server is what proves it. Everything the mapping decides on its own is here.
     */
    internal fun toNetworkEntry(
        path: String,
        name: String,
        isDirectory: Boolean,
        sizeBytes: Long,
        modifiedEpochSeconds: Long
    ): WearNetworkEntry = WearNetworkEntry(
        name = name,
        path = "${path.trimEnd('/')}/$name",
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        dateModifiedEpochMillis = modifiedEpochSeconds * MILLIS_PER_SECOND
    )

    private suspend fun listEntriesResolved(route: WearSftpRoute, path: String): List<WearNetworkEntry> =
        withContext(Dispatchers.IO) {
            var session: Session? = null
            var channel: ChannelSftp? = null
            try {
                session = openSession(route)
                channel = openChannel(session)

                val entries = channel.ls(path) as? Vector<*>
                    ?: error("SFTP ls returned null for path=$path")

                entries.filterIsInstance<ChannelSftp.LsEntry>()
                    .filterNot { it.filename == SELF_ENTRY || it.filename == PARENT_ENTRY }
                    .map { entry ->
                        toNetworkEntry(
                            path = path,
                            name = entry.filename,
                            isDirectory = entry.attrs.isDir,
                            sizeBytes = entry.attrs.size,
                            modifiedEpochSeconds = entry.attrs.mTime.toLong()
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
        handingOffCloseable { handOff ->
            withContext(Dispatchers.IO) {
                val route = endpointResolver.route(sourceIn)
                var session: Session? = null
                var channel: ChannelSftp? = null
                try {
                    session = openSession(route)
                    channel = openChannel(session)
                    Timber.d("Opening SFTP file: $path")

                    val stream = channel.get(path)
                        ?: error("SFTP get returned null for path=$path")

                    Result.success(handOff.track(streamClosingSession(stream, channel, session)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JSchException) {
                    failStream(e, channel, session, path)
                } catch (e: SftpException) {
                    failStream(e, channel, session, path)
                } catch (e: IllegalStateException) {
                    failStream(e, channel, session, path)
                } catch (e: IOException) {
                    failStream(e, channel, session, path)
                }
            }
        }

    /**
     * Remove a file from the server.
     *
     * S3359: channel and session are opened and closed inside this call, because unlike a download
     * nothing survives it that the caller could close. A server that refuses - a read-only account, a
     * missing file - answers with a failure, so a removal that did not happen is never reported as one.
     */
    suspend fun deleteFile(sourceIn: NetworkSource, path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val route = endpointResolver.route(sourceIn)
            var session: Session? = null
            var channel: ChannelSftp? = null
            try {
                session = openSession(route)
                channel = openChannel(session)
                Timber.d("Deleting SFTP file: $path")

                channel.rm(path)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: JSchException) {
                failDelete(e, path)
            } catch (e: SftpException) {
                failDelete(e, path)
            } catch (e: IOException) {
                failDelete(e, path)
            } finally {
                runCatching { channel?.disconnect() }
                runCatching { session?.disconnect() }
            }
        }

    private fun failDelete(cause: Exception, path: String): Result<Unit> {
        Timber.w(cause, "Failed to delete SFTP file for path=$path")
        return Result.failure(cause)
    }

    /**
     * S4230: store [input] in [directory] and return the name it ended up with.
     *
     * The bytes go under a partial name and are renamed only after the last one landed, so a broken transfer
     * never leaves a file under a real name; [intendedName] is replaced by a free one when the folder already
     * holds it, so nothing the watch did not create is overwritten. With a [network] the session's sockets are
     * created through it, which is what keeps the data off the Bluetooth proxy.
     */
    suspend fun storeFile(
        sourceIn: NetworkSource,
        directory: String,
        intendedName: String,
        input: InputStream,
        network: Network? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = WearRemoteFileNames.newToken()
        val partial = WearRemoteFileNames.joinRemote(directory, WearRemoteFileNames.partialName(token))
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            // S4230: routed inside the guard - an exchange folder with no way in raises its own exception here, and
            // a store reports every refusal as a result, never as a throw for the writer to miss.
            val route = endpointResolver.route(sourceIn, network)
            session = openSession(route, network)
            channel = openChannel(session)
            val finalName = freeNameIn(channel, directory, intendedName)
            channel.put(input, partial, ChannelSftp.OVERWRITE)
            channel.rename(partial, WearRemoteFileNames.joinRemote(directory, finalName))
            Result.success(finalName)
        } catch (e: CancellationException) {
            discardPartial(channel, partial)
            throw e
        } catch (e: JSchException) {
            failStore(e, channel, partial)
        } catch (e: SftpException) {
            failStore(e, channel, partial)
        } catch (e: IOException) {
            failStore(e, channel, partial)
        } catch (e: IllegalStateException) {
            failStore(e, channel, partial)
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { session?.disconnect() }
        }
    }

    private fun freeNameIn(channel: ChannelSftp, directory: String, intendedName: String): String {
        val listing = channel.ls(directory) as? Vector<*>
            ?: error("SFTP ls returned null for path=$directory")
        val existing = listing.filterIsInstance<ChannelSftp.LsEntry>().map { it.filename }.toSet()
        return WearFileNameConflictResolver.resolveAmong(existing, intendedName).first
    }

    private fun failStore(cause: Exception, channel: ChannelSftp?, partial: String): Result<String> {
        Timber.w(cause, "Failed to store SFTP file")
        discardPartial(channel, partial)
        return Result.failure(cause)
    }

    private fun discardPartial(channel: ChannelSftp?, partial: String) {
        runCatching { channel?.rm(partial) }
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

    /**
     * S4228: a tunnel route opens its stream first, in this coroutine, and hands it to JSch as the proxy; a failed
     * session closes it and forgets the route, so the next open probes the direct addresses again.
     */
    private suspend fun openSession(route: WearSftpRoute, network: Network? = null): Session {
        val tunnel = route as? WearSftpRoute.Tunnel
        Timber.d("S4228: sftp session opens, tunnel=${tunnel != null}, pinProtected=${route.source.pinProtected}")
        val pin = rootProofPin(route.source)
        val connection = try {
            tunnel?.let { tunnelClient.open(it.entry, it.resourceId, network) }
        } catch (e: IOException) {
            endpointResolver.forgetRoute(route.source.id)
            throw e
        }
        return try {
            connectSession(route.source, network, connection, pin)
        } catch (e: JSchException) {
            connection?.close()
            if (tunnel != null) endpointResolver.forgetRoute(route.source.id)
            throw e
        }
    }

    private fun connectSession(
        source: NetworkSource,
        network: Network?,
        tunnel: WearExchangeConnection?,
        pin: String?
    ): Session {
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
        pin?.let {
            session.setConfig("PreferredAuthentications", ROOT_PROOF_AUTHENTICATIONS)
            session.userInfo = WearSftpRootProofUserInfo(source.identityRoot, source.password, it)
        }
        if (tunnel != null) {
            session.setProxy(WearExchangeTunnelProxy(tunnel))
            session.connect(WearExchangeTunnelClient.ANSWER_TIMEOUT_MS)
        } else {
            network?.let { session.setSocketFactory(NetworkSocketFactory(it)) }
            session.connect(CONNECT_TIMEOUT_MS)
        }
        return session
    }

    /**
     * S4228: the PIN a protected root demands on every connection (contract DEVICE-EXCHANGE 17 item AH), or null for an
     * unprotected source. A protected source without one fails here, before a connection that could only be refused.
     */
    private suspend fun rootProofPin(source: NetworkSource): String? {
        if (!source.pinProtected) return null
        val identity = WearFolderIdentity.of(source) ?: throw WearResourcePinMissingException()
        return pinStore.pin(identity) ?: throw WearResourcePinMissingException()
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

/**
 * S4230: JSch's socket seam over one granted [Network], so the connection and its data leave through the watch's own
 * Wi-Fi or LTE; a request for that network alone does not move an already-routed socket off the Bluetooth proxy.
 */
private class NetworkSocketFactory(private val network: Network) : com.jcraft.jsch.SocketFactory {

    /**
     * JSch applies its connect timeout only to sockets it creates itself, so the bound socket is connected here with
     * the same bound - an unreachable host would otherwise hold the send until the platform gives up.
     */
    override fun createSocket(host: String, port: Int): Socket {
        val socket = network.socketFactory.createSocket()
        try {
            socket.connect(InetSocketAddress(network.getByName(host), port), CONNECT_TIMEOUT_MS)
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw e
        }
        return socket
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()

    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
}
