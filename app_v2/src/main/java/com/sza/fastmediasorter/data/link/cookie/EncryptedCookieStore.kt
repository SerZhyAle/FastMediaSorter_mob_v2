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
 * S0116 §5.1 pillar K: encrypted per-domain cookie store for the URL download
 * pipeline. Backing layer is `EncryptedSharedPreferences` with the project's
 * standard `MasterKey.Builder(...).setKeyScheme(AES256_GCM).build()` pattern
 * (mirrors `DropboxClient.kt:81-87`, `GoogleDriveCredentialsManager.kt:33-39`).
 *
 * S0155: Extended to support multiple accounts per host using key format
 * `acct:<host>:<accountId>`. On first access, legacy `domain:<host>` keys are
 * migrated to `acct:<host>:__legacy__` transparently.
 *
 * Each account entry is persisted as a JSON document containing `accountId`,
 * `displayName`, `savedAtEpochMillis`, `lastUsedAtEpochMillis`, and a `cookies`
 * array preserving `name/value/domain/path/expiresAtEpochMillis/secure/httpOnly`.
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

    @Volatile private var migrated = false

    // ── Migration ────────────────────────────────────────────────────────────

    /**
     * Migrates legacy `domain:<host>` keys to `acct:<host>:__legacy__`.
     * Idempotent — runs at most once per process. The [legacyDisplayName] string
     * must come from the caller (localized); the store itself does not hold a Context.
     */
    fun migrateIfNeeded(legacyDisplayName: String) {
        if (migrated) return
        synchronized(this) {
            if (migrated) return
            // S0157: one-time wipe of all auth records (feature never released; no user data to preserve).
            val metaPrefs = context.getSharedPreferences("link_download_cookies_meta", Context.MODE_PRIVATE)
            if (!metaPrefs.getBoolean("s0157_wiped", false)) {
                val allKeys = prefs.all.keys.toList()
                if (allKeys.isNotEmpty()) {
                    val editor = prefs.edit()
                    allKeys.forEach { editor.remove(it) }
                    editor.apply()
                    Timber.i("EncryptedCookieStore: S0157 one-time wipe removed %d key(s)", allKeys.size)
                }
                metaPrefs.edit().putBoolean("s0157_wiped", true).apply()
            }
            val legacyKeys = prefs.all.keys.filter { it.startsWith(LEGACY_PREFIX) }
            if (legacyKeys.isNotEmpty()) {
                val editor = prefs.edit()
                legacyKeys.forEach { oldKey ->
                    val host = oldKey.removePrefix(LEGACY_PREFIX)
                    val raw = prefs.getString(oldKey, null) ?: return@forEach
                    // Build new payload with account fields added
                    val newPayload = runCatching {
                        val root = JSONObject(raw)
                        root.put("accountId", LEGACY_ACCOUNT_ID)
                        root.put("displayName", legacyDisplayName)
                        if (!root.has("lastUsedAtEpochMillis")) root.put("lastUsedAtEpochMillis", 0L)
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

    // ── Multi-account public API ──────────────────────────────────────────────

    /** All active (type=active) accounts saved for [host]. Ordered by `savedAt` descending. */
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

    // Returns all entries including dismissed (type=dismissed); callers must filter as appropriate.
    /** All `(host, AccountEntry)` pairs across every host. Used by the repository. */
    fun listAllAccounts(): List<Pair<String, AccountEntry>> {
        return prefs.all.keys
            .filter { it.startsWith(ACCT_PREFIX) }
            .mapNotNull { key ->
                val body = key.removePrefix(ACCT_PREFIX) // "<host>:<accountId>"
                val sep = body.indexOf(':')
                if (sep < 0) return@mapNotNull null
                val host = body.substring(0, sep)
                val accountId = body.substring(sep + 1)
                val entry = loadAccountEntry(host, accountId) ?: return@mapNotNull null
                host to entry
            }
    }

    /** Cookies for a specific `(host, accountId)` pair. */
    fun loadForAccount(host: String, accountId: String): List<HttpCookie> = try {
        loadCookiesInternal(keyForAccount(host, accountId), host)
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        LinkDownloadTrace.verbose("fallback=cookie-store-empty reason=${t::class.simpleName}")
        emptyList()
    }

    /** Saves cookies for a specific account. Creates or overwrites the entry. */
    fun saveForAccount(host: String, accountId: String, displayName: String, cookies: List<HttpCookie>) {
        if (host.isBlank() || accountId.isBlank()) return
        val savedAt = System.currentTimeMillis()
        val arr = buildCookieArray(cookies, host, savedAt)
        val payload = JSONObject()
            .put("accountId", accountId)
            .put("displayName", displayName)
            .put("savedAtEpochMillis", savedAt)
            .put("lastUsedAtEpochMillis", 0L)
            .put("type", TYPE_ACTIVE)
            .put("cookies", arr)
        prefs.edit().putString(keyForAccount(host, accountId), payload.toString()).apply()
        LinkDownloadTrace.verbose(
            "encrypted-cookie-store save host=$host accountId=$accountId count=${cookies.size}",
        )
    }

    /**
     * S0157: stores a permanent-skip record for [host] — no cookies, type="dismissed".
     * The offer dialog will not appear for this host until the record is deleted.
     */
    fun saveAsDismissed(host: String) {
        saveDismissedInternal(host, DISMISSED_ACCOUNT_ID, "")
    }

    /**
     * S0157 §6.2: reactive re-auth dismissals are account-scoped. They use a synthetic
     * storage key so the dismissed marker does not overwrite the still-saved active session.
     */
    fun saveAsDismissedForAccount(host: String, accountId: String, displayName: String) {
        if (host.isBlank() || accountId.isBlank()) return
        saveDismissedInternal(host, dismissedAccountIdFor(accountId), displayName)
    }

    /** S0157: true if a host-level dismissed record exists for [host]. */
    fun hasDismissedRecord(host: String): Boolean =
        loadAccountEntry(host, DISMISSED_ACCOUNT_ID)?.type == TYPE_DISMISSED

    /** S0157 §6.2: true if an account-scoped dismissed record exists for [accountId]. */
    fun hasDismissedRecordForAccount(host: String, accountId: String): Boolean =
        loadAccountEntry(host, dismissedAccountIdFor(accountId))?.type == TYPE_DISMISSED

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

    /** Deletes a specific `(host, accountId)` entry. */
    fun deleteForAccount(host: String, accountId: String) {
        prefs.edit().remove(keyForAccount(host, accountId)).apply()
        LinkDownloadTrace.verbose("encrypted-cookie-store delete host=$host accountId=$accountId")
    }

    /** Updates only the `displayName` field of an existing entry. No-op if not found. */
    fun updateDisplayName(host: String, accountId: String, newName: String) {
        val key = keyForAccount(host, accountId)
        val raw = prefs.getString(key, null) ?: return
        val updated = runCatching {
            val root = JSONObject(raw)
            root.put("displayName", newName)
            root.toString()
        }.getOrElse { return }
        prefs.edit().putString(key, updated).apply()
    }

    /** Stamps `lastUsedAtEpochMillis = now` on the given account entry. */
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

    // ── Deprecated single-domain API (kept for backward compat) ─────────────

    /**
     * Loads cookies for [domain] using the account with the highest `lastUsedAt`.
     * Falls back to `__legacy__` then `emptyList()`.
     */
    @Deprecated("Use loadForAccount(host, accountId)", level = DeprecationLevel.WARNING)
    fun loadFor(domain: String): List<HttpCookie> = try {
        val best = pickBestAccount(domain)
        if (best != null) loadCookiesInternal(keyForAccount(domain, best.accountId), domain)
        else emptyList()
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        LinkDownloadTrace.verbose("fallback=cookie-store-empty reason=${t::class.simpleName}")
        emptyList()
    }

    /** Saves cookies as the `__legacy__` account for [domain]. */
    @Deprecated("Use saveForAccount(host, accountId, displayName, cookies)", level = DeprecationLevel.WARNING)
    fun saveFor(domain: String, cookies: List<HttpCookie>) {
        if (domain.isBlank()) return
        saveForAccount(domain, LEGACY_ACCOUNT_ID, "", cookies)
    }

    /** Deletes ALL accounts for [domain]. */
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

    /** Returns distinct hosts across all `acct:` keys. */
    @Deprecated("Use listAllAccounts()", level = DeprecationLevel.WARNING)
    fun listDomains(): List<String> =
        prefs.all.keys
            .filter { it.startsWith(ACCT_PREFIX) }
            .mapNotNull { key ->
                val body = key.removePrefix(ACCT_PREFIX)
                val sep = body.indexOf(':')
                if (sep < 0) null else body.substring(0, sep)
            }
            .distinct()
            .sorted()

    /** Returns `savedAt` of the most-recently-used account for [domain]. */
    @Deprecated("Use AccountEntry.savedAt", level = DeprecationLevel.WARNING)
    fun savedAt(domain: String): Instant? = pickBestAccount(domain)?.savedAt

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun keyForAccount(host: String, accountId: String): String =
        "$ACCT_PREFIX$host:$accountId"

    private fun dismissedAccountIdFor(accountId: String): String =
        "$DISMISSED_ACCOUNT_ID:$accountId"

    private fun pickBestAccount(host: String): AccountEntry? =
        listAccounts(host).maxByOrNull { it.lastUsedAt ?: Instant.MIN }

    private fun loadAccountEntry(host: String, accountId: String): AccountEntry? {
        val key = keyForAccount(host, accountId)
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val cookieCount = run {
                val now = System.currentTimeMillis()
                val arr = root.optJSONArray("cookies") ?: return@run 0
                var count = 0
                for (i in 0 until arr.length()) {
                    val node = arr.optJSONObject(i) ?: continue
                    val expires = if (node.has("expiresAtEpochMillis") && !node.isNull("expiresAtEpochMillis"))
                        node.optLong("expiresAtEpochMillis", -1L).takeIf { it > 0L }
                    else null
                    if (expires == null || expires >= now) count++
                }
                count
            }
            val savedMs = root.optLong("savedAtEpochMillis", -1L)
            val lastUsedMs = root.optLong("lastUsedAtEpochMillis", 0L)
            val type = root.optString("type", TYPE_ACTIVE)
            AccountEntry(
                accountId = root.optString("accountId", accountId),
                displayName = root.optString("displayName", accountId),
                savedAt = if (savedMs > 0L) Instant.ofEpochMilli(savedMs) else null,
                lastUsedAt = if (lastUsedMs > 0L) Instant.ofEpochMilli(lastUsedMs) else null,
                cookieCount = cookieCount,
                type = type,
            )
        }.getOrNull()
    }

    private fun loadCookiesInternal(key: String, fallbackDomain: String): List<HttpCookie> {
        val raw = prefs.getString(key, null) ?: return emptyList()
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
                if (expires != null && expires < now) continue
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
                "encrypted-cookie-store load key=${key.take(40)} count=${out.size} names=[${out.joinToString(",") { it.name }}]",
            )
            out
        }.getOrElse {
            LinkDownloadTrace.verbose("encrypted-cookie-store load failed key=${key.take(40)} reason=${it::class.simpleName}")
            emptyList()
        }
    }

    private fun buildCookieArray(cookies: List<HttpCookie>, host: String, savedAt: Long): JSONArray {
        val arr = JSONArray()
        cookies.forEach { c ->
            val node = JSONObject()
                .put("name", c.name)
                .put("value", c.value)
                .put("domain", c.domain ?: host)
                .put("path", c.path ?: "/")
                .put("secure", c.secure)
                .put("httpOnly", c.isHttpOnly)
            val expires: Long? = if (c.maxAge >= 0L) savedAt + c.maxAge * 1000L else null
            if (expires != null) node.put("expiresAtEpochMillis", expires)
            arr.put(node)
        }
        return arr
    }

    // ── Public types ──────────────────────────────────────────────────────────

    /** Per-account metadata without cookie values (safe to pass to UI layer). */
    data class AccountEntry(
        val accountId: String,
        val displayName: String,
        val savedAt: Instant?,
        val lastUsedAt: Instant?,
        val cookieCount: Int,
        /** S0157: "active" for real sessions, "dismissed" for permanent-skip records. */
        val type: String = TYPE_ACTIVE,
    )

    companion object {
        const val FILE_NAME = "link_download_cookies"
        /** S0155: new key prefix for multi-account entries. Format: `acct:<host>:<accountId>`. */
        const val ACCT_PREFIX = "acct:"
        /** S0155: legacy key prefix, migrated from old single-session format. */
        const val LEGACY_PREFIX = "domain:"
        /** S0155: stable accountId used for migrated (pre-multi-account) sessions. */
        const val LEGACY_ACCOUNT_ID = "__legacy__"
        /** S0157: entry type for a real session with cookies. */
        const val TYPE_ACTIVE = "active"
        /** S0157: entry type for a permanent-skip record (no cookies). */
        const val TYPE_DISMISSED = "dismissed"
        /** S0157: stable accountId used for host-level/system dismissed records. */
        const val DISMISSED_ACCOUNT_ID = "__dismissed__"
    }
}
