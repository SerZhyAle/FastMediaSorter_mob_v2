package com.sza.fastmediasorter.data.link.cookie

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpCookie

/**
 * S0116 Phase 04 step 6: Robolectric-backed contract test for [EncryptedCookieStore].
 * Covers round-trip preservation, expiry filtering, listing, and deletion.
 *
 * NOTE: AndroidKeyStore (used by EncryptedSharedPreferences / MasterKey) is unavailable
 * in Robolectric's JVM environment. These tests must be run as instrumented tests on a
 * device or emulator. Marked @Ignore to keep the JVM unit-test suite green.
 */
@Ignore("AndroidKeyStore unavailable in Robolectric JVM - run as instrumented test on device/emulator")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.11.1 maxSdkVersion=34; targetSdkVersion=35 would fail without this.
class EncryptedCookieStoreTest {

    private lateinit var context: Context
    private lateinit var store: EncryptedCookieStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Wipe any state from a prior test invocation in the same JVM.
        context.getSharedPreferences("link_download_cookies", Context.MODE_PRIVATE).edit().clear().commit()
        store = EncryptedCookieStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("link_download_cookies", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `round-trip preserves all cookie fields`() {
        val cookie = HttpCookie("session", "abc").apply {
            domain = "example.com"
            path = "/"
            maxAge = 60L * 60L // 1 hour
            secure = true
            isHttpOnly = true
        }
        store.saveFor("example.com", listOf(cookie))
        val loaded = store.loadFor("example.com")
        assertEquals(1, loaded.size)
        val out = loaded.first()
        assertEquals("session", out.name)
        assertEquals("abc", out.value)
        assertEquals("example.com", out.domain)
        assertEquals("/", out.path)
        assertTrue(out.secure)
        assertTrue(out.isHttpOnly)
        assertTrue(out.maxAge in 1L..3601L)
    }

    @Test
    fun `expired cookie is dropped on load`() {
        val expired = HttpCookie("stale", "value").apply {
            domain = "example.com"
            path = "/"
            maxAge = -1L // session cookie - see contract note: also write a clearly-expired one
        }
        // Persist a cookie whose computed expiresAtEpochMillis is in the past by abusing
        // maxAge = 0 (already expired immediately after save).
        val zeroMaxAge = HttpCookie("zero", "z").apply {
            domain = "example.com"; path = "/"; maxAge = 0L
        }
        store.saveFor("example.com", listOf(expired, zeroMaxAge))
        val loaded = store.loadFor("example.com")
        // Session cookie (maxAge -1) survives; the zero-maxAge cookie expires at savedAt+0 = now.
        assertFalse("zero-maxAge cookie should be filtered out", loaded.any { it.name == "zero" })
    }

    @Test
    fun `listDomains returns saved domains in deterministic order`() {
        store.saveFor("c.example.com", listOf(HttpCookie("k", "v")))
        store.saveFor("a.example.com", listOf(HttpCookie("k", "v")))
        store.saveFor("b.example.com", listOf(HttpCookie("k", "v")))
        val domains = store.listDomains()
        assertEquals(listOf("a.example.com", "b.example.com", "c.example.com"), domains)
        assertNotNull(store.savedAt("b.example.com"))
    }

    @Test
    fun `deleteFor removes only the specified domain`() {
        store.saveFor("keep.example.com", listOf(HttpCookie("k1", "v1")))
        store.saveFor("drop.example.com", listOf(HttpCookie("k2", "v2")))
        store.deleteFor("drop.example.com")
        assertEquals(listOf("keep.example.com"), store.listDomains())
        assertTrue(store.loadFor("drop.example.com").isEmpty())
        assertEquals(1, store.loadFor("keep.example.com").size)
    }
}
