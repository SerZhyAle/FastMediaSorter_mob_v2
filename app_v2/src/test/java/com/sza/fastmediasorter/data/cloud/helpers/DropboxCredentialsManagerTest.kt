package com.sza.fastmediasorter.data.cloud.helpers

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S2115: covers the purge of credential entries left by the removed plaintext fallback.
 *
 * AndroidKeyStore is unavailable in Robolectric's JVM, so `EncryptedSharedPreferences.create(..)`
 * throws here - which is the very condition the purge must survive. The accessors below therefore
 * exercise the real broken-Keystore path: the purge runs, and the encrypted access that follows is
 * caught and degrades to "no credentials".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.16.1 maxSdkVersion=34; targetSdkVersion=36 would fail without this.
class DropboxCredentialsManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        plainPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        plainPrefs().edit().clear().commit()
    }

    private fun plainPrefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun `purge removes plaintext credential entries left by the fallback`() {
        plainPrefs().edit()
            .putString(LEGACY_KEY, LEAKED_JSON)
            .putString("${PER_ACCOUNT_PREFIX}user@example.com", LEAKED_JSON)
            .commit()

        DropboxCredentialsManager(context).loadStoredCredentials()

        val keys = plainPrefs().all.keys
        assertFalse("legacy plaintext key must be erased", keys.contains(LEGACY_KEY))
        assertFalse(
            "per-account plaintext key must be erased",
            keys.contains("${PER_ACCOUNT_PREFIX}user@example.com")
        )
    }

    @Test
    fun `purge keeps entries it does not own`() {
        plainPrefs().edit()
            .putString(LEGACY_KEY, LEAKED_JSON)
            .putString(UNRELATED_KEY, "keep me")
            .commit()

        DropboxCredentialsManager(context).loadStoredCredentials()

        assertTrue("unrelated key must survive", plainPrefs().all.keys.contains(UNRELATED_KEY))
        assertEquals("keep me", plainPrefs().getString(UNRELATED_KEY, null))
    }

    @Test
    fun `purge runs once per manager instance`() {
        plainPrefs().edit().putString(LEGACY_KEY, LEAKED_JSON).commit()
        val manager = DropboxCredentialsManager(context)
        manager.loadStoredCredentials()

        // A second plaintext entry appearing after the one-shot purge is not re-scanned.
        plainPrefs().edit().putString(LEGACY_KEY, LEAKED_JSON).commit()
        manager.clearStoredCredentials()

        assertTrue(plainPrefs().all.keys.contains(LEGACY_KEY))
    }

    private companion object {
        const val PREFS_NAME = "dropbox_credentials"
        const val LEGACY_KEY = "credentials_json"
        const val PER_ACCOUNT_PREFIX = "credentials_json_"
        const val UNRELATED_KEY = "some_other_setting"
        const val LEAKED_JSON = """{"refresh_token":"leaked"}"""
    }
}
