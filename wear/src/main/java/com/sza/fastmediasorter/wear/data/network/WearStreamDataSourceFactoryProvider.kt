package com.sza.fastmediasorter.wear.data.network

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Builds the HTTP [DataSource.Factory] and [DefaultMediaSourceFactory] for Wear OS stream playback (S2498).
 *
 * Cross-protocol redirects are mandatory for radio relays because many Icecast/Shoutcast stations
 * 30x-redirect across http<->https.
 *
 * Suppress in-band ICY metadata. Media3 1.2.1 unconditionally requests it for a progressive stream,
 * so the override must be applied at [DataSpec] priority inside the wrapper. The server then sends
 * uninterrupted audio bytes instead of alternating audio and metadata blocks.
 */
@OptIn(UnstableApi::class)
object WearStreamDataSourceFactoryProvider {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    fun createHttpDataSourceFactory(): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("FastMediaSorter/v2 (WearOS)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setKeepPostFor302Redirects(false)
        return DataSource.Factory { WearRadioHttpDataSource(httpFactory.createDataSource()) }
    }

    fun createMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
        val httpFactory = createHttpDataSourceFactory()
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        return DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    }
}

@OptIn(UnstableApi::class)
private class WearRadioHttpDataSource(
    private val delegate: HttpDataSource,
) : DataSource by delegate {

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val userInfo = uri.userInfo
        val host = uri.host
        val authSpec = if (!userInfo.isNullOrEmpty() && host != null) {
            delegate.setRequestProperty(HEADER_AUTHORIZATION, basicAuthHeaderValue(userInfo))
            val authority = if (uri.port != -1) "$host:${uri.port}" else host
            dataSpec.withUri(uri.buildUpon().encodedAuthority(authority).build())
        } else {
            dataSpec
        }
        val effectiveSpec = authSpec.withAdditionalHeaders(mapOf(HEADER_ICY_METADATA to ICY_DISABLED))
        return delegate.open(effectiveSpec)
    }

    private fun basicAuthHeaderValue(userInfo: String): String {
        val separatorIndex = userInfo.indexOf(':')
        val user = if (separatorIndex >= 0) userInfo.substring(0, separatorIndex) else userInfo
        val pass = if (separatorIndex >= 0) userInfo.substring(separatorIndex + 1) else ""
        val credentials = "${Uri.decode(user)}:${Uri.decode(pass)}"
        val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_ICY_METADATA = "Icy-MetaData"
        const val ICY_DISABLED = "0"
    }
}
