package com.sza.fastmediasorter.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM coverage of the empty/null guard branches of [CryptoHelper].
 *
 * The encrypt/decrypt round-trip itself is backed by the Android Keystore
 * (`AndroidKeyStore` provider + `KeyGenParameterSpec`) which has no JVM/Robolectric
 * implementation, so only the early-return guards - which never touch the keystore -
 * are exercised here.
 */
class CryptoHelperTest {

    @Test
    fun `encrypt returns empty string for empty input`() {
        assertEquals("", CryptoHelper.encrypt(""))
    }

    @Test
    fun `decrypt returns empty string for null input`() {
        assertEquals("", CryptoHelper.decrypt(null))
    }

    @Test
    fun `decrypt returns empty string for empty input`() {
        assertEquals("", CryptoHelper.decrypt(""))
    }
}
