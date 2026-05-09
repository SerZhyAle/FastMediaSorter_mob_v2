package com.sza.fastmediasorter.data.link.cookie

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpCookie
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar K: encrypted per-domain cookie store for the URL download
 * pipeline. Backing layer is `EncryptedSharedPreferences` with the project's
 * standard `MasterKey.Builder(...).setKeyScheme(AES256_GCM).build()` pattern
 * (mirrors `DropboxClient.kt:81-87`, `GoogleDriveCredentialsManager.kt:33-39`).
 *
 * Each domain is persisted as a single JSON document containing `savedAtEpochMillis`
 * and a `cookies` array preserving `name/value/domain/path/expiresAtEpochMillis/secure/httpOnly`.
 * On load, expired cookies are dropped silently.
 *
 * Privacy: tracing emits domain + cookie count + names only; cookie values never
 * appear in logs.
 */
@Singleton
class EncryptedCookieStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun loadFor(domain: String): List<HttpCookie> = try {
        loadForInternal(domain)
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        LinkDownloadTrace.verbose("fallback=cookie-store-empty reason=${t::class.simpleName}")
        emptyList()
    }

    private fun loadForInternal(domain: String): List<HttpCookie> {
        val raw = prefs.getString(keyForDomain(domain), null) ?: return emptyList()
        val now = System.currentTimeMillis()
        return runCatching {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("cookies") ?: return@runCatching emptyList()
            val out = mutableListOf<HttpCookie>()
            for (i in 0 until arr.length()) {
                val node = arr.optJSONObject(i) ?: continue
                val name = node.optString("name").orEmpty()
                if (name.isBlank()) continue
                val value = node.optString("value").orEmpty()
                val expires = if (node.has("expiresAtEpochMillis") && !node.isNull("expiresAtEpochMillis")) {
                    node.optLong("expiresAtEpochMillis", -1L).takeIf { it > 0L }
                } else null
                if (expires != null && expires < now) continue // dropped expired
                val cookie = HttpCookie(name, value).apply {
                    val cookieDomain = node.optString("domain").orEmpty()
                    if (cookieDomain.isNotBlank()) this.domain = cookieDomain
                    val path = node.optString("path").orEmpty()
                    if (path.isNotBlank()) this.path = path
                    if (expires != null) maxAge = ((expires - now) / 1000L).coerceAtLeast(1L)
                    secure = node.optBoolean("secure", false)
                    isHttpOnly = node.optBoolean("httpOnly", false)
                }
                out.add(cookie)
            }
            LinkDownloadTrace.verbose(
                "encrypted-cookie-store load domain=$domain count=${out.size} names=[${out.joinToString(",") { it.name }}]",
            )
            out
        }.getOrElse {
            LinkDownloadTrace.verbose("encrypted-cookie-store load failed domain=$domain reason=${it::class.simpleName}")
            emptyList()
        }
    }

    fun saveFor(domain: String, cookies: List<HttpCookie>) {
        if (domain.isBlank()) return
        val savedAt = System.currentTimeMillis()
        val arr = JSONArray()
        cookies.forEach { c ->
            val node = JSONObject()
                .put("name", c.name)
                .put("value", c.value)
                .put("domain", c.domain ?: domain)
                .put("path", c.path ?: "/")
                .put("secure", c.secure)
                .put("httpOnly", c.isHttpOnly)
            val expires: Long? = if (c.maxAge >= 0L) savedAt + c.maxAge * 1000L else null
            if (expires != null) node.put("expiresAtEpochMillis", expires)
            arr.put(node)
        }
        val payload = JSONObject()
            .put("savedAtEpochMillis", savedAt)
            .put("cookies", arr)
        prefs.edit().putString(keyForDomain(domain), payload.toString()).apply()
        LinkDownloadTrace.verbose(
            "encrypted-cookie-store save domain=$domain count=${cookies.size} names=[${cookies.joinToString(",") { it.name }}]",
        )
    }

    fun listDomains(): List<String> =
        prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .map { it.removePrefix(KEY_PREFIX) }
            .sorted()

    fun deleteFor(domain: String) {
        prefs.edit().remove(keyForDomain(domain)).apply()
        LinkDownloadTrace.verbose("encrypted-cookie-store delete domain=$domain")
    }

    fun savedAt(domain: String): Instant? {
        val raw = prefs.getString(keyForDomain(domain), null) ?: return null
        return runCatching {
            JSONObject(raw).optLong("savedAtEpochMillis", -1L)
                .takeIf { it > 0L }
                ?.let { Instant.ofEpochMilli(it) }
        }.getOrNull()
    }

    private fun keyForDomain(domain: String): String = KEY_PREFIX + domain

    private companion object {
        const val FILE_NAME = "link_download_cookies"
        const val KEY_PREFIX = "domain:"
    }
}
