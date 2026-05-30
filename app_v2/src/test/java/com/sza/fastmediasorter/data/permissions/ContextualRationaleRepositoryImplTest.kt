package com.sza.fastmediasorter.data.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [ContextualRationaleRepositoryImpl]: per-permission shown-flag persistence via
 * SharedPreferences (default false, true after markShown, independent keys). Robolectric supplies
 * the real SharedPreferences implementation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextualRationaleRepositoryImplTest {

    private val repo = ContextualRationaleRepositoryImpl(RuntimeEnvironment.getApplication())

    @Test
    fun `isShown defaults to false`() {
        assertFalse(repo.isShown("read_media_images"))
    }

    @Test
    fun `markShown then isShown returns true`() {
        repo.markShown("record_audio")
        assertTrue(repo.isShown("record_audio"))
    }

    @Test
    fun `flags are independent per permission id`() {
        repo.markShown("post_notifications")
        assertTrue(repo.isShown("post_notifications"))
        assertFalse(repo.isShown("battery_optimization"))
    }
}
