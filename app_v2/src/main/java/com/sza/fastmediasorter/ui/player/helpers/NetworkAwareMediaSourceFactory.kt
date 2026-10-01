package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.sza.fastmediasorter.core.playback.ListenRecordingSinkHolder
import com.sza.fastmediasorter.data.cloud.datasource.StreamCloudClients
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.datasource.FtpDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.SftpDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.SmbDataSourceFactory
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpEndpointResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media3 [MediaSource.Factory] that lets the background audio service play SMB/SFTP/FTP/cloud URIs
 * directly (streaming), the same way the in-app player does, instead of requiring a full pre-cache
 * to a local `file://` copy. Local/HTTP URIs fall through to the default factory.
 *
 * Credentials cannot be resolved here without blocking the player thread on a DB read, so the caller
 * resolves them on a coroutine and hands them over in-process through [StreamCredentialHolder], keyed
 * by the media URI. Host/share/path come from the URI itself.
 */
@UnstableApi
@Singleton
class NetworkAwareMediaSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val endpointResolver: SftpEndpointResolver,
    private val ftpClient: FtpClient,
    private val cloudClients: StreamCloudClients,
    private val listenRecordingSinkHolder: ListenRecordingSinkHolder,
    private val streamCredentialHolder: StreamCredentialHolder,
) : MediaSource.Factory {

    private val defaultFactory = DefaultMediaSourceFactory(context)
    private var drmProvider: DrmSessionManagerProvider? = null
    private var loadErrorPolicy: LoadErrorHandlingPolicy? = null

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        drmProvider = provider
        defaultFactory.setDrmSessionManagerProvider(provider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        loadErrorPolicy = policy
        defaultFactory.setLoadErrorHandlingPolicy(policy)
        return this
    }

    override fun getSupportedTypes(): IntArray = defaultFactory.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        val protocolFactory = uri?.let { dataSourceFactoryFor(it) }
            ?: return defaultFactory.createMediaSource(mediaItem)

        val factory = DefaultMediaSourceFactory(protocolFactory)
        drmProvider?.let { factory.setDrmSessionManagerProvider(it) }
        loadErrorPolicy?.let { factory.setLoadErrorHandlingPolicy(it) }
        return factory.createMediaSource(mediaItem)
    }

    /** Build the protocol [DataSource.Factory] for a network/cloud URI, or null for local/HTTP. */
    private fun dataSourceFactoryFor(uri: Uri): DataSource.Factory? {
        val creds = streamCredentialHolder.get(uri.toString())
        val user = creds?.username.orEmpty()
        val pass = creds?.password.orEmpty()
        val domain = creds?.domain
        val extraPort = creds?.port ?: 0
        // Defense-in-depth: a URI rebuilt via Uri.Builder.authority("host:port") percent-encodes the
        // ':' so uri.host can come back as the whole "host:port" with uri.port == -1. Split a numeric
        // trailing port back out so JSch/FTP never receive a host that still carries the port.
        val (host, embeddedPort) = splitHostPort(uri.host.orEmpty())

        return when (uri.scheme?.lowercase()) {
            "sftp" -> {
                // S1006: use the reachable endpoint for this resource (LAN at home, WAN in transit).
                // Cache-only on the player thread - browse warmed it; falls back to the URI host otherwise.
                val ep = endpointResolver.resolveCached(host, port(uri, embeddedPort ?: extraPort, DEFAULT_SFTP_PORT))
                SftpDataSourceFactory(sftpClient, ep.host, ep.port, user, pass, context)
            }
            "ftp" -> FtpDataSourceFactory(
                ftpClient,
                host,
                port(uri, embeddedPort ?: extraPort, DEFAULT_FTP_PORT),
                user,
                pass,
                context
            )
            "smb" -> {
                val share = uri.pathSegments.firstOrNull().orEmpty()
                val connectionInfo = SmbConnectionInfo(
                    server = host,
                    shareName = share,
                    username = user,
                    password = pass,
                    domain = domain.orEmpty(),
                    port = port(uri, extraPort, DEFAULT_SMB_PORT)
                )
                SmbDataSourceFactory(smbClient, connectionInfo, context)
            }
            "cloud" -> cloudClients.dataSourceFactory()
            // Internet radio/HLS played through the background service must use the same HTTP factory
            // as the in-app player, for cross-protocol redirects: without it an Icecast/Shoutcast 30x
            // across http<->https surfaces as a fatal "Response code: 301" source error. Note the
            // shared factory also forces `Icy-MetaData: 0` - in-band track metadata is deliberately
            // OFF on every consumer, not enabled here (see StreamDataSourceFactoryProvider, S1142).
            // S2881: the watch's listening address is an ordinary http one, so a recording session
            // hands its sink in here. Asked only while a session is armed, and answered per address,
            // so radio played from this same branch is never written to a file.
            "http", "https" -> if (listenRecordingSinkHolder.isArmed()) {
                StreamDataSourceFactoryProvider.create(context, listenRecordingSinkHolder::openSinkFor)
            } else {
                StreamDataSourceFactoryProvider.create(context)
            }
            else -> null
        }
    }

    private fun port(uri: Uri, extraPort: Int, default: Int): Int =
        uri.port.takeIf { it > 0 } ?: extraPort.takeIf { it > 0 } ?: default

    /**
     * Split a possibly "host:port" string into (host, port). Only splits on a single trailing
     * numeric port (IPv4/hostname); a multi-colon value (IPv6 literal) is returned untouched so it
     * is not mis-parsed. Guards against a Uri.Builder.authority() round-trip that leaves the port
     * fused into [Uri.getHost].
     */
    private fun splitHostPort(rawHost: String): Pair<String, Int?> {
        val colon = rawHost.indexOf(':')
        if (colon <= 0 || colon != rawHost.lastIndexOf(':')) return rawHost to null
        val portPart = rawHost.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..65535 }
            ?: return rawHost to null
        return rawHost.substring(0, colon) to portPart
    }

    companion object {
        private const val DEFAULT_SFTP_PORT = 22
        private const val DEFAULT_FTP_PORT = 21
        private const val DEFAULT_SMB_PORT = 445
    }
}
