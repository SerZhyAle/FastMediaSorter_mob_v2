package com.sza.fastmediasorter.data.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [PermissionRequestMarkerRepositoryImpl]: per-permission request-fired flag
 * persistence via SharedPreferences (default false, true after markRequested, independent keys).
 * Robolectric supplies the real SharedPreferences implementation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionRequestMarkerRepositoryImplTest {

    private val repo = PermissionRequestMarkerRepositoryImpl(RuntimeEnvironment.getApplication())

    @Test
    fun `wasRequested defaults to false`() {
        assertFalse(repo.wasRequested("read_media_images"))
    }

    @Test
    fun `markRequested then wasRequested returns true`() {
        repo.markRequested("record_audio")
        assertTrue(repo.wasRequested("record_audio"))
    }

    @Test
    fun `markers are independent per permission id`() {
        repo.markRequested("post_notifications")
        assertTrue(repo.wasRequested("post_notifications"))
        assertFalse(repo.wasRequested("battery_optimization"))
    }
}
