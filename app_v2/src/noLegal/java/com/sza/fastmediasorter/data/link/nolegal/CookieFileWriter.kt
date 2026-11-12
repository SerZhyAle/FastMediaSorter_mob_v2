package com.sza.fastmediasorter.data.link.nolegal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.net.HttpCookie
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serialises the cookies of one link-download session to a Netscape-format temp file in
 * [Context.filesDir] for use by yt-dlp via Chaquopy.
 *
 * The cookie list is the caller's, never read from the store here: the session context already
 * holds exactly the account the run selected (plus the in-memory response-cookie overlay), and
 * merging every stored account of the host mixed one account's sessionid with another's csrftoken.
 *
 * Lifecycle: [writeCookieFile] writes the file; the caller must call [deleteCookieFile]
 * in a `try/finally` block. [deleteOnExit] is registered at write time as a backup.
 *
 * Security: cookie values are never written to Timber logs.
 */
@Singleton
class CookieFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Writes [cookies] to a Netscape HTTP Cookie File in [Context.filesDir]. [targetHost] is the
     * domain of a cookie that carries none.
     *
     * Returns the temp [File], or `null` when [cookies] is empty (no file is written, so yt-dlp
     * runs anonymously). The caller must delete the file via [deleteCookieFile] in a
     * `try/finally` block.
     */
    fun writeCookieFile(targetHost: String, cookies: List<HttpCookie>): File? {
        Timber.d("S4511: cookie file requested host=%s sessionCookies=%d", targetHost, cookies.size)
        if (cookies.isEmpty()) return null

        val file = File(context.filesDir, "ytdlp_cookies_${System.currentTimeMillis()}.txt")
        file.deleteOnExit() // belt-and-suspenders if deleteCookieFile() is not called
        // S0822: Netscape cookie-file expiry is an ABSOLUTE Unix timestamp (epoch seconds), but
        // HttpCookie.maxAge is a RELATIVE remaining lifetime in seconds (EncryptedCookieStore
        // reconstructs it as (expiresAtEpochMillis - now) / 1000). Emitting maxAge verbatim made
        // yt-dlp read e.g. a one-year sessionid as expiring in 1971, drop it as stale, and fail
        // authenticated Instagram fetches with "You need to log in" despite cookies=9.
        val nowEpochSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        file.bufferedWriter().use { writer ->
            writer.write("# Netscape HTTP Cookie File\n")
            writer.write("# https://curl.se/docs/http-cookies.html\n")
            for (cookie in cookies) {
                val domain = cookie.domain?.trimStart('.') ?: targetHost
                val includeSubdomain = if (cookie.domain?.startsWith('.') == true) "TRUE" else "FALSE"
                val path = cookie.path ?: "/"
                val secure = if (cookie.secure) "TRUE" else "FALSE"
                // maxAge < 0 → session cookie; emit 0 (Netscape session marker). Otherwise turn
                // the remaining lifetime into an absolute epoch-seconds expiry yt-dlp accepts.
                val expiry = if (cookie.maxAge >= 0L) nowEpochSeconds + cookie.maxAge else 0L
                val value = cookie.value ?: ""
                writer.write("$domain\t$includeSubdomain\t$path\t$secure\t$expiry\t${cookie.name}\t$value\n")
            }
        }

        Timber.d("CookieFileWriter: wrote %d cookies for host=%s", cookies.size, targetHost)
        return file
    }

    /**
     * Deletes the cookie file. Call in `finally` after yt-dlp has read it.
     * Swallows exceptions - the file will also be deleted by the JVM on exit via [deleteOnExit].
     */
    fun deleteCookieFile(file: File) {
        runCatching { file.delete() }
    }
}
