package com.sza.fastmediasorter.data.link.auth

import java.net.HttpCookie

/**
 * S0211: extracts a stable, server-issued identity from harvested cookies for known
 * social platforms. The returned string is the dedup key for a (host, identity) pair —
 * stable across sessions because the platform's user-id cookie does not rotate on
 * re-login.
 *
 * Returns `null` when the host is unknown, or when the expected identity cookie is
 * absent / blank. Callers must treat `null` as "do not deduplicate" and fall back to
 * inserting a fresh `accountId`.
 *
 * Pure function — no Android imports, no logging, no IO.
 */
object AccountIdentityExtractor {

    private const val PLATFORM_INSTAGRAM = "instagram.com"
    private const val PLATFORM_THREADS = "threads.net"
    private const val PLATFORM_FACEBOOK = "facebook.com"
    private const val PLATFORM_X = "x.com"
    private const val PLATFORM_TWITTER = "twitter.com"

    private const val COOKIE_DS_USER_ID = "ds_user_id"
    private const val COOKIE_C_USER = "c_user"
    private const val COOKIE_TWID = "twid"

    private const val TWID_PREFIX = "u="

    fun extract(host: String, cookies: List<HttpCookie>): String? {
        if (host.isBlank() || cookies.isEmpty()) return null
        val normalizedHost = host.lowercase()
        val cookieName = cookieNameForHost(normalizedHost) ?: return null
        val byName = cookies.associateBy { it.name.lowercase() }
        val raw = byName[cookieName]?.value?.takeIf { it.isNotBlank() } ?: return null
        return when (cookieName) {
            COOKIE_TWID -> raw.removePrefix(TWID_PREFIX).takeIf { it.isNotBlank() }
            else -> raw
        }
    }

    private fun cookieNameForHost(normalizedHost: String): String? = when {
        matchesPlatform(normalizedHost, PLATFORM_INSTAGRAM) -> COOKIE_DS_USER_ID
        matchesPlatform(normalizedHost, PLATFORM_THREADS) -> COOKIE_DS_USER_ID
        matchesPlatform(normalizedHost, PLATFORM_FACEBOOK) -> COOKIE_C_USER
        matchesPlatform(normalizedHost, PLATFORM_X) -> COOKIE_TWID
        matchesPlatform(normalizedHost, PLATFORM_TWITTER) -> COOKIE_TWID
        else -> null
    }

    private fun matchesPlatform(host: String, platform: String): Boolean =
        host == platform || host.endsWith(".$platform")
}
