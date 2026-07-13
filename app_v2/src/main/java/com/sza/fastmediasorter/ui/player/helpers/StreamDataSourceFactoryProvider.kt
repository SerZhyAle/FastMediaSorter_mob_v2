package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
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
        val authAwareFactory = DataSource.Factory { UserInfoBasicAuthDataSource(httpFactory.createDataSource()) }
        return DefaultDataSource.Factory(context, authAwareFactory)
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000
}

/**
 * S1015: many IP-camera / DVR HTTP endpoints (mjpg-streamer and similar) are shared as
 * `http://user:pass@host/path` and expect HTTP Basic Auth. [HttpURLConnection] - what
 * [DefaultHttpDataSource] wraps - never reads credentials out of a URI's userinfo the way a
 * browser or curl would, so those streams silently 401 and the manually-added source looks
 * "unreachable" right after being added. This wrapper lifts a present userinfo into an explicit
 * `Authorization: Basic` header and strips it from the URI actually requested.
 */
private class UserInfoBasicAuthDataSource(
    private val delegate: HttpDataSource,
) : DataSource by delegate {

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val userInfo = uri.userInfo
        val host = uri.host
        val effectiveSpec = if (!userInfo.isNullOrEmpty() && host != null) {
            delegate.setRequestProperty(HEADER_AUTHORIZATION, basicAuthHeaderValue(userInfo))
            val authority = if (uri.port != -1) "$host:${uri.port}" else host
            dataSpec.withUri(uri.buildUpon().encodedAuthority(authority).build())
        } else {
            dataSpec
        }
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
    }
}
