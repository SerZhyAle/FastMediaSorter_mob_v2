package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.sza.fastmediasorter.BuildConfig

/**
 * Builds the streaming HTTP [DataSource.Factory] for internet stream playback.
 *
 * Cross-protocol redirects and the `Icy-MetaData:1` request header are mandatory for radio relays:
 * many Icecast/Shoutcast stations 30x-redirect across http<->https, and a station only emits ICY
 * now-playing metadata when the client opts in via `Icy-MetaData:1`.
 */
@UnstableApi
internal object StreamDataSourceFactoryProvider {

    fun create(context: Context): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("FastMediaSorter/${BuildConfig.VERSION_NAME} (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setKeepPostFor302Redirects(false)
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
        return DefaultDataSource.Factory(context, httpFactory)
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000
}
