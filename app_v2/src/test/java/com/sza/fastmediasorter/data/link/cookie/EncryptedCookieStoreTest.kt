package com.sza.fastmediasorter.data.link.cookie

import android.content.Context
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
import org.robolectric.RuntimeEnvironment
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
@Config(sdk = [34]) // Robolectric 4.16.1 maxSdkVersion=34; targetSdkVersion=35 would fail without this.
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
        store.saveForAccount("example.com", "acc", "", listOf(cookie))
        val loaded = store.loadForAccount("example.com", "acc")
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
            domain = "example.com"
            path = "/"
            maxAge = 0L
        }
        store.saveForAccount("example.com", "acc", "", listOf(expired, zeroMaxAge))
        val loaded = store.loadForAccount("example.com", "acc")
        // Session cookie (maxAge -1) survives; the zero-maxAge cookie expires at savedAt+0 = now.
        assertFalse("zero-maxAge cookie should be filtered out", loaded.any { it.name == "zero" })
    }

    @Test
    fun `listAllAccounts returns saved hosts`() {
        store.saveForAccount("c.example.com", "acc", "", listOf(HttpCookie("k", "v")))
        store.saveForAccount("a.example.com", "acc", "", listOf(HttpCookie("k", "v")))
        store.saveForAccount("b.example.com", "acc", "", listOf(HttpCookie("k", "v")))
        val hosts = store.listAllAccounts().map { (host, _) -> host }.sorted()
        assertEquals(listOf("a.example.com", "b.example.com", "c.example.com"), hosts)
        assertNotNull(store.listAllAccounts().first { (host, _) -> host == "b.example.com" }.second.savedAt)
    }

    @Test
    fun `deleteForAccount removes only the specified account`() {
        store.saveForAccount("keep.example.com", "acc", "", listOf(HttpCookie("k1", "v1")))
        store.saveForAccount("drop.example.com", "acc", "", listOf(HttpCookie("k2", "v2")))
        store.deleteForAccount("drop.example.com", "acc")
        assertEquals(listOf("keep.example.com"), store.listAllAccounts().map { (host, _) -> host })
        assertTrue(store.loadForAccount("drop.example.com", "acc").isEmpty())
        assertEquals(1, store.loadForAccount("keep.example.com", "acc").size)
    }
}
