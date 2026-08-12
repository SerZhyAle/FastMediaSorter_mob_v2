package com.sza.fastmediasorter.core.util

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins every SDK window [StoragePermissionRule] owns against its registry row in
 * `PermissionRegistryRepositoryImpl`: `read_external_storage` 23..32, `write_external_storage`
 * 23..28, `read_media_*` from 33, `manage_external_storage` from 30. The rule takes `sdkInt` as a
 * parameter for exactly this reason - a window that drifts from its row fails here rather than on a
 * device nobody has.
 */
class StoragePermissionRuleTest {

    @Test
    fun `write covers the registry window 23 to 28`() {
        for (sdk in 23..28) {
            assertTrue(
                "API $sdk must still request WRITE_EXTERNAL_STORAGE",
                Manifest.permission.WRITE_EXTERNAL_STORAGE in
                    StoragePermissionRule.requiredPermissions(sdk),
            )
        }
        for (sdk in 29..36) {
            assertFalse(
                "API $sdk must not request WRITE_EXTERNAL_STORAGE - the platform stopped granting it",
                Manifest.permission.WRITE_EXTERNAL_STORAGE in
                    StoragePermissionRule.requiredPermissions(sdk),
            )
        }
    }

    @Test
    fun `read external storage covers the registry window 23 to 32`() {
        for (sdk in 23..32) {
            assertTrue(
                "API $sdk is inside the read_external_storage registry window",
                Manifest.permission.READ_EXTERNAL_STORAGE in
                    StoragePermissionRule.requiredPermissions(sdk),
            )
        }
        assertFalse(
            Manifest.permission.READ_EXTERNAL_STORAGE in
                StoragePermissionRule.requiredPermissions(33),
        )
    }

    @Test
    fun `the three media permissions replace storage from the registry minSdk of 33`() {
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            ),
            StoragePermissionRule.requiredPermissions(33).toList(),
        )
    }

    @Test
    fun `below the runtime permission model nothing is requested`() {
        assertEquals(0, StoragePermissionRule.requiredPermissions(22).size)
    }

    @Test
    fun `all files access starts at the manage external storage registry minSdk of 30`() {
        assertFalse(StoragePermissionRule.requiresAllFilesAccess(29))
        assertTrue(StoragePermissionRule.requiresAllFilesAccess(30))
    }
}
