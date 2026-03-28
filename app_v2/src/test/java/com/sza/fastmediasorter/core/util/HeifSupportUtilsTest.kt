package com.sza.fastmediasorter.core.util

import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field

/**
 * Tests for [HeifSupportUtils].
 *
 * API-level checks are exercised by reflectively writing [Build.VERSION.SDK_INT].
 */
class HeifSupportUtilsTest {

    private var originalSdkInt: Int = Build.VERSION.SDK_INT

    /** Restore SDK_INT after each test so mutations don't bleed across tests. */
    @After
    fun restoreSdkInt() {
        setSdkInt(originalSdkInt)
    }

    // ── isHeicSupported ──────────────────────────────────────────────────────

    @Test
    fun `isHeicSupported returns false on API 27`() {
        setSdkInt(27)
        assertFalse(HeifSupportUtils.isHeicSupported())
    }

    @Test
    fun `isHeicSupported returns true on API 28`() {
        setSdkInt(28)
        assertTrue(HeifSupportUtils.isHeicSupported())
    }

    @Test
    fun `isHeicSupported returns true on API 33`() {
        setSdkInt(33)
        assertTrue(HeifSupportUtils.isHeicSupported())
    }

    // ── isAvifSupported ──────────────────────────────────────────────────────

    @Test
    fun `isAvifSupported returns false on API 30`() {
        setSdkInt(30)
        assertFalse(HeifSupportUtils.isAvifSupported())
    }

    @Test
    fun `isAvifSupported returns true on API 31`() {
        setSdkInt(31)
        assertTrue(HeifSupportUtils.isAvifSupported())
    }

    // ── isSupported ──────────────────────────────────────────────────────────

    @Test
    fun `isSupported heic returns false on API 27`() {
        setSdkInt(27)
        assertFalse(HeifSupportUtils.isSupported("heic"))
    }

    @Test
    fun `isSupported HEIC uppercase returns false on API 27`() {
        setSdkInt(27)
        assertFalse(HeifSupportUtils.isSupported("HEIC"))
    }

    @Test
    fun `isSupported heif returns false on API 27`() {
        setSdkInt(27)
        assertFalse(HeifSupportUtils.isSupported("heif"))
    }

    @Test
    fun `isSupported heic returns true on API 28`() {
        setSdkInt(28)
        assertTrue(HeifSupportUtils.isSupported("heic"))
    }

    @Test
    fun `isSupported avif returns false on API 30`() {
        setSdkInt(30)
        assertFalse(HeifSupportUtils.isSupported("avif"))
    }

    @Test
    fun `isSupported avif returns true on API 31`() {
        setSdkInt(31)
        assertTrue(HeifSupportUtils.isSupported("avif"))
    }

    @Test
    fun `isSupported jpg always returns true`() {
        setSdkInt(26)
        assertTrue(HeifSupportUtils.isSupported("jpg"))
    }

    @Test
    fun `isSupported png always returns true`() {
        setSdkInt(26)
        assertTrue(HeifSupportUtils.isSupported("png"))
    }

    @Test
    fun `isSupported mp4 always returns true`() {
        setSdkInt(26)
        assertTrue(HeifSupportUtils.isSupported("mp4"))
    }

    // ── minimumAndroidVersion ─────────────────────────────────────────────────

    @Test
    fun `minimumAndroidVersion returns correct string for heic`() {
        assertEquals("Android 9 (API 28)", HeifSupportUtils.minimumAndroidVersion("heic"))
    }

    @Test
    fun `minimumAndroidVersion returns correct string for HEIF uppercase`() {
        assertEquals("Android 9 (API 28)", HeifSupportUtils.minimumAndroidVersion("HEIF"))
    }

    @Test
    fun `minimumAndroidVersion returns correct string for avif`() {
        assertEquals("Android 12 (API 31)", HeifSupportUtils.minimumAndroidVersion("avif"))
    }

    @Test
    fun `minimumAndroidVersion returns null for jpg`() {
        assertNull(HeifSupportUtils.minimumAndroidVersion("jpg"))
    }

    @Test
    fun `minimumAndroidVersion returns null for unknown extension`() {
        assertNull(HeifSupportUtils.minimumAndroidVersion("xyz"))
    }

    @Test
    fun `minimumAndroidVersion is non-null for unsupported heic on API 27`() {
        setSdkInt(27)
        assertFalse(HeifSupportUtils.isSupported("heic"))
        assertNotNull(HeifSupportUtils.minimumAndroidVersion("heic"))
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Writes [Build.VERSION.SDK_INT] via sun.misc.Unsafe accessed through Class.forName,
     * avoiding a direct import that Kotlin may not resolve under the Android test JVM config.
     */
    private fun setSdkInt(version: Int) {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField: Field = unsafeClass.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val sdkField: Field = Build.VERSION::class.java.getField("SDK_INT")
        val staticBase = unsafeClass.getMethod("staticFieldBase", Field::class.java).invoke(unsafe, sdkField)
        val staticOffset = unsafeClass.getMethod("staticFieldOffset", Field::class.java).invoke(unsafe, sdkField) as Long
        unsafeClass.getMethod("putInt", Any::class.java, Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(unsafe, staticBase, staticOffset, version)
    }
}
