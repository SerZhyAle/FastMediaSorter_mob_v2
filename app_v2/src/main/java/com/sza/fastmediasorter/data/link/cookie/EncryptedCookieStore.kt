package com.sza.fastmediasorter.data.link.cookie

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpCookie
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted multi-account cookie storage for link downloads.
 *
 * The store persists one record per `(host, accountId)` pair. Each record keeps
 * metadata plus the cookie array. S0166 also needs permanent-skip records, so the
 * same storage format carries entries with `type=dismissed` and an empty cookie list.
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

    @Volatile
    private var migrated = false

    /**
     * Migrates the old single-host format `domain:<host>` to the account-aware
     * `acct:<host>:__legacy__` format. The operation is idempotent and runs once.
     */
    fun migrateIfNeeded(legacyDisplayName: String) {
        if (migrated) return
        synchronized(this) {
            if (migrated) return
            val legacyKeys = prefs.all.keys.filter { it.startsWith(LEGACY_PREFIX) }
            if (legacyKeys.isNotEmpty()) {
                val editor = prefs.edit()
                legacyKeys.forEach { oldKey ->
                    val host = oldKey.removePrefix(LEGACY_PREFIX)
                    val raw = prefs.getString(oldKey, null) ?: return@forEach
                    val newPayload = runCatching {
                        val root = JSONObject(raw)
                        root.put("accountId", LEGACY_ACCOUNT_ID)
                        root.put("displayName", legacyDisplayName)
                        if (!root.has("lastUsedAtEpochMillis")) {
                            root.put("lastUsedAtEpochMillis", 0L)
                        }
                        if (!root.has("type")) {
                            root.put("type", TYPE_ACTIVE)
                        }
                        root.toString()
                    }.getOrElse { raw }
                    editor.putString(keyForAccount(host, LEGACY_ACCOUNT_ID), newPayload)
                    editor.remove(oldKey)
                }
                editor.apply()
                Timber.i("EncryptedCookieStore: migrated %d legacy session(s)", legacyKeys.size)
            }
            migrated = true
        }
    }

    fun listAccounts(host: String): List<AccountEntry> {
        val prefix = "$ACCT_PREFIX$host:"
        return prefs.all.keys
            .filter { it.startsWith(prefix) }
            .mapNotNull { key ->
                val accountId = key.removePrefix(prefix)
                loadAccountEntry(host, accountId)
            }
            .filter { it.type == TYPE_ACTIVE }
            .sortedByDescending { it.savedAt }
    }

    fun listAllAccounts(): List<Pair<String, AccountEntry>> {
        return prefs.all.keys
            .filter { it.startsWith(ACCT_PREFIX) }
            .mapNotNull { key ->
                val body = key.removePrefix(ACCT_PREFIX)
                val separator = body.indexOf(':')
                if (separator < 0) return@mapNotNull null
                val host = body.substring(0, separator)
                val accountId = body.substring(separator + 1)
                val entry = loadAccountEntry(host, accountId) ?: return@mapNotNull null
                host to entry
            }
    }

    fun loadForAccount(host: String, accountId: String): List<HttpCookie> = try {
        loadCookiesInternal(keyForAccount(host, accountId), host)
    } catch (throwable: Throwable) {
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
        LinkDownloadTrace.verbose("fallback=cookie-store-empty reason=${throwable::class.simpleName}")
        emptyList()
    }

    fun saveForAccount(host: String, accountId: String, displayName: String, cookies: List<HttpCookie>) {
        if (host.isBlank() || accountId.isBlank() || cookies.isEmpty()) return
        val savedAt = System.currentTimeMillis()
        val payload = JSONObject()
            .put("accountId", accountId)
            .put("displayName", displayName.trim())
            .put("savedAtEpochMillis", savedAt)
            .put("lastUsedAtEpochMillis", 0L)
            .put("type", TYPE_ACTIVE)
            .put("cookies", buildCookieArray(cookies, host, savedAt))
        prefs.edit().putString(keyForAccount(host, accountId), payload.toString()).apply()
        LinkDownloadTrace.verbose(
            "encrypted-cookie-store save host=$host accountId=$accountId count=${cookies.size}",
        )
    }

    fun saveAsDismissed(host: String) {
        saveDismissedInternal(host, DISMISSED_ACCOUNT_ID, "")
    }

    fun saveAsDismissedForAccount(host: String, accountId: String, displayName: String) {
        if (host.isBlank() || accountId.isBlank()) return
        saveDismissedInternal(host, dismissedAccountIdFor(accountId), displayName)
    }

    fun hasDismissedRecord(host: String): Boolean =
        loadAccountEntry(host, DISMISSED_ACCOUNT_ID)?.type == TYPE_DISMISSED

    fun hasDismissedRecordForAccount(host: String, accountId: String): Boolean =
        loadAccountEntry(host, dismissedAccountIdFor(accountId))?.type == TYPE_DISMISSED

    fun deleteForAccount(host: String, accountId: String) {
        prefs.edit().remove(keyForAccount(host, accountId)).apply()
        LinkDownloadTrace.verbose("encrypted-cookie-store delete host=$host accountId=$accountId")
    }

    fun updateDisplayName(host: String, accountId: String, newName: String) {
        val key = keyForAccount(host, accountId)
        val raw = prefs.getString(key, null) ?: return
        val updated = runCatching {
            val root = JSONObject(raw)
            root.put("displayName", newName.trim())
            root.toString()
        }.getOrElse { return }
        prefs.edit().putString(key, updated).apply()
    }

    fun markLastUsed(host: String, accountId: String) {
        val key = keyForAccount(host, accountId)
        val raw = prefs.getString(key, null) ?: return
        val updated = runCatching {
            val root = JSONObject(raw)
            root.put("lastUsedAtEpochMillis", System.currentTimeMillis())
            root.toString()
        }.getOrElse { return }
        prefs.edit().putString(key, updated).apply()
    }

    @Deprecated("Use loadForAccount(host, accountId)", level = DeprecationLevel.WARNING)
    fun loadFor(domain: String): List<HttpCookie> = try {
        val best = pickBestAccount(domain)
        if (best != null) loadCookiesInternal(keyForAccount(domain, best.accountId), domain)
        else emptyList()
    } catch (throwable: Throwable) {
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
        LinkDownloadTrace.verbose("fallback=cookie-store-empty reason=${throwable::class.simpleName}")
        emptyList()
    }

    @Deprecated("Use saveForAccount(host, accountId, displayName, cookies)", level = DeprecationLevel.WARNING)
    fun saveFor(domain: String, cookies: List<HttpCookie>) {
        if (domain.isBlank()) return
        saveForAccount(domain, LEGACY_ACCOUNT_ID, "", cookies)
    }

    @Deprecated("Use deleteForAccount(host, accountId)", level = DeprecationLevel.WARNING)
    fun deleteFor(domain: String) {
        val prefix = "$ACCT_PREFIX$domain:"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
        LinkDownloadTrace.verbose("encrypted-cookie-store delete-all domain=$domain count=${keys.size}")
    }

    @Deprecated("Use listAllAccounts()", level = DeprecationLevel.WARNING)
    fun listDomains(): List<String> =
        prefs.all.keys
            .filter { it.startsWith(ACCT_PREFIX) }
            .mapNotNull { key ->
                val body = key.removePrefix(ACCT_PREFIX)
                val separator = body.indexOf(':')
                if (separator < 0) null else body.substring(0, separator)
            }
            .distinct()
            .sorted()

    @Deprecated("Use AccountEntry.savedAt", level = DeprecationLevel.WARNING)
    fun savedAt(domain: String): Instant? = pickBestAccount(domain)?.savedAt

    private fun saveDismissedInternal(host: String, storageAccountId: String, displayName: String) {
        if (host.isBlank()) return
        val payload = JSONObject()
            .put("accountId", storageAccountId)
            .put("displayName", displayName.trim())
            .put("savedAtEpochMillis", System.currentTimeMillis())
            .put("lastUsedAtEpochMillis", 0L)
            .put("type", TYPE_DISMISSED)
            .put("cookies", JSONArray())
        prefs.edit().putString(keyForAccount(host, storageAccountId), payload.toString()).apply()
        LinkDownloadTrace.verbose("encrypted-cookie-store dismissed host=$host accountId=$storageAccountId")
    }

    private fun keyForAccount(host: String, accountId: String): String = "$ACCT_PREFIX$host:$accountId"

    private fun dismissedAccountIdFor(accountId: String): String = "$DISMISSED_ACCOUNT_ID:$accountId"

    private fun pickBestAccount(host: String): AccountEntry? =
        listAccounts(host).maxByOrNull { it.lastUsedAt ?: Instant.MIN }

    private fun loadAccountEntry(host: String, accountId: String): AccountEntry? {
        val key = keyForAccount(host, accountId)
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val now = System.currentTimeMillis()
            val cookieCount = countLiveCookies(root.optJSONArray("cookies"), now)
            val savedMs = root.optLong("savedAtEpochMillis", -1L)
            val lastUsedMs = root.optLong("lastUsedAtEpochMillis", 0L)
            AccountEntry(
                accountId = root.optString("accountId", accountId),
                displayName = root.optString("displayName", accountId),
                savedAt = if (savedMs > 0L) Instant.ofEpochMilli(savedMs) else null,
                lastUsedAt = if (lastUsedMs > 0L) Instant.ofEpochMilli(lastUsedMs) else null,
                cookieCount = cookieCount,
                type = root.optString("type", TYPE_ACTIVE),
            )
        }.getOrNull()
    }

    private fun countLiveCookies(array: JSONArray?, nowEpochMillis: Long): Int {
        if (array == null) return 0
        var count = 0
        for (index in 0 until array.length()) {
            val node = array.optJSONObject(index) ?: continue
            val expires = if (node.has("expiresAtEpochMillis") && !node.isNull("expiresAtEpochMillis")) {
                node.optLong("expiresAtEpochMillis", -1L).takeIf { it > 0L }
            } else {
                null
            }
            if (expires == null || expires >= nowEpochMillis) {
                count += 1
            }
        }
        return count
    }

    private fun loadCookiesInternal(key: String, fallbackDomain: String): List<HttpCookie> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val now = System.currentTimeMillis()
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("cookies") ?: return@runCatching emptyList()
            val out = mutableListOf<HttpCookie>()
            for (index in 0 until array.length()) {
                val node = array.optJSONObject(index) ?: continue
                val name = node.optString("name").orEmpty()
                if (name.isBlank()) continue
                val expires = if (node.has("expiresAtEpochMillis") && !node.isNull("expiresAtEpochMillis")) {
                    node.optLong("expiresAtEpochMillis", -1L).takeIf { it > 0L }
                } else {
                    null
                }
                if (expires != null && expires < now) continue
                val cookie = HttpCookie(name, node.optString("value").orEmpty()).apply {
                    val cookieDomain = node.optString("domain").orEmpty().ifBlank { fallbackDomain }
                    domain = cookieDomain
                    val cookiePath = node.optString("path").orEmpty().ifBlank { "/" }
                    path = cookiePath
                    if (expires != null) {
                        maxAge = ((expires - now) / 1000L).coerceAtLeast(1L)
                    }
                    secure = node.optBoolean("secure", false)
                    isHttpOnly = node.optBoolean("httpOnly", false)
                }
                out += cookie
            }
            LinkDownloadTrace.verbose(
                "encrypted-cookie-store load key=${key.take(40)} count=${out.size} names=[${out.joinToString(",") { it.name }}]",
            )
            out
        }.getOrElse { throwable ->
            LinkDownloadTrace.verbose(
                "encrypted-cookie-store load failed key=${key.take(40)} reason=${throwable::class.simpleName}",
            )
            emptyList()
        }
    }

    private fun buildCookieArray(cookies: List<HttpCookie>, host: String, savedAtEpochMillis: Long): JSONArray {
        val array = JSONArray()
        cookies.forEach { cookie ->
            val node = JSONObject()
                .put("name", cookie.name)
                .put("value", cookie.value)
                .put("domain", cookie.domain ?: host)
                .put("path", cookie.path ?: "/")
                .put("secure", cookie.secure)
                .put("httpOnly", cookie.isHttpOnly)
            val expires = if (cookie.maxAge >= 0L) savedAtEpochMillis + cookie.maxAge * 1000L else null
            if (expires != null) {
                node.put("expiresAtEpochMillis", expires)
            }
            array.put(node)
        }
        return array
    }

    data class AccountEntry(
        val accountId: String,
        val displayName: String,
        val savedAt: Instant?,
        val lastUsedAt: Instant?,
        val cookieCount: Int,
        val type: String = TYPE_ACTIVE,
    )

    companion object {
        const val FILE_NAME = "link_download_cookies"
        const val ACCT_PREFIX = "acct:"
        const val LEGACY_PREFIX = "domain:"
        const val LEGACY_ACCOUNT_ID = "__legacy__"
        const val TYPE_ACTIVE = "active"
        const val TYPE_DISMISSED = "dismissed"
        const val DISMISSED_ACCOUNT_ID = "__dismissed__"
    }
}