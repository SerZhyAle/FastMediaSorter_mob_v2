package com.sza.fastmediasorter.data.link.nolegal

import android.content.Context
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.net.HttpCookie
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serialises cookies from [EncryptedCookieStore] to a Netscape-format temp file in
 * [Context.filesDir] for use by yt-dlp via Chaquopy. Matches cookies by eTLD+1 (same
 * naive split used in `LinkDownloadCookieJar.registrableDomain()`).
 *
 * Lifecycle: [writeCookieFile] writes the file; the caller must call [deleteCookieFile]
 * in a `try/finally` block. [deleteOnExit] is registered at write time as a backup.
 *
 * Security: cookie values are never written to Timber logs.
 */
@Singleton
class CookieFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: EncryptedCookieStore,
) {

    /**
     * Writes cookies matching [targetHost] by eTLD+1 from [EncryptedCookieStore]
     * to a Netscape HTTP Cookie File in [Context.filesDir].
     *
     * Returns the temp [File], or `null` if no matching cookies were found (no file is written).
     * The caller must delete the file via [deleteCookieFile] in a `try/finally` block.
     */
    fun writeCookieFile(targetHost: String): File? {
        val targetDomain = registrableDomain(targetHost)

        // Collect all cookies from accounts whose host matches the eTLD+1.
        val merged = mutableListOf<HttpCookie>()
        val seenNames = mutableSetOf<String>()
        store.listAllAccounts()
            .filter { (storedHost, entry) ->
                entry.type == EncryptedCookieStore.TYPE_ACTIVE &&
                    registrableDomain(storedHost) == targetDomain
            }
            .flatMap { (storedHost, entry) ->
                store.loadForAccount(storedHost, entry.accountId)
            }
            .forEach { cookie ->
                // Deduplicate by name - first occurrence wins.
                if (seenNames.add(cookie.name)) {
                    merged += cookie
                }
            }

        if (merged.isEmpty()) return null

        val file = File(context.filesDir, "ytdlp_cookies_${System.currentTimeMillis()}.txt")
        file.deleteOnExit() // belt-and-suspenders if deleteCookieFile() is not called
        file.bufferedWriter().use { writer ->
            writer.write("# Netscape HTTP Cookie File\n")
            writer.write("# https://curl.se/docs/http-cookies.html\n")
            for (cookie in merged) {
                val domain = cookie.domain?.trimStart('.') ?: targetHost
                val includeSubdomain = if (cookie.domain?.startsWith('.') == true) "TRUE" else "FALSE"
                val path = cookie.path ?: "/"
                val secure = if (cookie.secure) "TRUE" else "FALSE"
                // maxAge < 0 → session cookie; yt-dlp accepts 0 as session
                val expiry = if (cookie.maxAge >= 0L) cookie.maxAge else 0L
                val value = cookie.value ?: ""
                writer.write("$domain\t$includeSubdomain\t$path\t$secure\t$expiry\t${cookie.name}\t$value\n")
            }
        }

        Timber.d("CookieFileWriter: wrote %d cookies for host=%s", merged.size, targetHost)
        return file
    }

    /**
     * Deletes the cookie file. Call in `finally` after yt-dlp has read it.
     * Swallows exceptions - the file will also be deleted by the JVM on exit via [deleteOnExit].
     */
    fun deleteCookieFile(file: File) {
        runCatching { file.delete() }
    }

    /**
     * Naive eTLD+1 extraction - same algorithm as `LinkDownloadCookieJar.registrableDomain()`.
     * For `www.instagram.com` → `instagram.com`. Single-label or IP addresses → returned as-is.
     */
    private fun registrableDomain(host: String): String {
        val parts = host.trimStart('.').split('.')
        return if (parts.size >= 2) "${parts[parts.size - 2]}.${parts.last()}" else host
    }
}
