package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.repository.RawAuthSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpCookie

/**
 * S0406: pure-JVM round-trip checks for the new backup payload sections.
 * Network-credential mapping is excluded here because it relies on the Android Keystore
 * (CryptoHelper) which is unavailable in a plain JVM unit test.
 */
class BackupMapperRoundTripTest {

    @Test
    fun webAuthSession_roundTrip_preservesCookieFields() {
        val cookie = HttpCookie("sid", "abc123").apply {
            domain = "example.com"
            path = "/app"
            secure = true
            isHttpOnly = true
            maxAge = 3600
        }
        val raw = RawAuthSession(
            host = "example.com",
            accountId = "acc-1",
            displayName = "Alice",
            userAgent = "UA/1.0",
            savedAtEpochMillis = 1_000L,
            lastUsedAtEpochMillis = 2_000L,
            cookies = listOf(cookie),
        )

        val backup = BackupMapper.toBackupWebAuthSession(raw)
        assertEquals(1, backup.cookies.size)
        val backupCookie = backup.cookies.first()
        assertEquals("sid", backupCookie.name)
        assertEquals("abc123", backupCookie.value)
        assertEquals("example.com", backupCookie.domain)
        assertEquals("/app", backupCookie.path)
        assertTrue(backupCookie.secure)
        assertTrue(backupCookie.httpOnly)
        assertNotNull(backupCookie.expiresAtEpochMillis)
    }

    @Test
    fun webAuthSession_roundTrip_preservesSessionMetadata() {
        val raw = RawAuthSession(
            host = "site.org",
            accountId = "uuid-42",
            displayName = "Bob",
            userAgent = "Mozilla/5.0",
            savedAtEpochMillis = 111L,
            lastUsedAtEpochMillis = 222L,
            cookies = listOf(HttpCookie("c", "v").apply { domain = "site.org"; path = "/" }),
        )

        val restored = BackupMapper.toRawAuthSession(BackupMapper.toBackupWebAuthSession(raw))

        assertEquals("site.org", restored.host)
        assertEquals("uuid-42", restored.accountId)
        assertEquals("Bob", restored.displayName)
        assertEquals("Mozilla/5.0", restored.userAgent)
        assertEquals(1, restored.cookies.size)
        assertEquals("c", restored.cookies.first().name)
        assertEquals("v", restored.cookies.first().value)
    }

    @Test
    fun webAuthSession_persistentCookie_keepsLiveExpiryAfterRoundTrip() {
        val raw = RawAuthSession(
            host = "h.com",
            accountId = "a",
            displayName = "",
            userAgent = null,
            savedAtEpochMillis = 0L,
            lastUsedAtEpochMillis = 0L,
            cookies = listOf(HttpCookie("k", "x").apply { domain = "h.com"; path = "/"; maxAge = 7200 }),
        )

        val restored = BackupMapper.toRawAuthSession(BackupMapper.toBackupWebAuthSession(raw))

        // A persistent cookie must survive as persistent (maxAge >= 0), not collapse to a session cookie.
        assertTrue(restored.cookies.first().maxAge >= 0L)
    }

    @Test
    fun payload_v4Json_withoutSecretSections_deserializesWithNullSections() {
        val v4Json = """
            {
              "version": 4,
              "appVersionName": "2.0",
              "settings": { "language": "ru" },
              "resources": [],
              "favorites": []
            }
        """.trimIndent()

        val payload = Gson().fromJson(v4Json, BackupPayload::class.java)

        assertEquals(4, payload.version)
        assertNotNull(payload.settings)
        assertEquals("ru", payload.settings?.language)
        // New v5 sections are absent in a v4 file - must stay null, never crash.
        assertNull(payload.networkCredentials)
        assertNull(payload.webAuthSessions)
    }

    @Test
    fun settings_roundTrip_preservesDefaultNetworkCredentials() {
        val backupSettings = BackupSettings(defaultUser = "domain\\user", defaultPassword = "p@ss")

        val roundTripped = Gson().fromJson(Gson().toJson(backupSettings), BackupSettings::class.java)

        assertEquals("domain\\user", roundTripped.defaultUser)
        assertEquals("p@ss", roundTripped.defaultPassword)
    }
}
