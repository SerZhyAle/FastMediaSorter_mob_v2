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
 *
 * S2012: the all-files rules take the manifest declaration as a second parameter for the same
 * reason, so the four-cell matrix is pinned without a package manager and without a flavor-specific
 * expectation that would only hold on the variant the suite happens to run under.
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
        assertFalse(StoragePermissionRule.requiresAllFilesAccess(29, declaresAllFilesAccess = true))
        assertTrue(StoragePermissionRule.requiresAllFilesAccess(30, declaresAllFilesAccess = true))
    }

    /**
     * S2012: the SDK window alone used to be the whole rule, which made every store flavor - none of
     * which declares MANAGE_EXTERNAL_STORAGE any more - ask forever for a grant it structurally could
     * not receive. A build that does not declare the permission is answered by the runtime
     * permissions, at every API level.
     */
    @Test
    fun `a build that does not declare the permission never requires all files access`() {
        for (sdk in 29..36) {
            assertFalse(
                "API $sdk must not require all-files access when the manifest does not declare it",
                StoragePermissionRule.requiresAllFilesAccess(sdk, declaresAllFilesAccess = false),
            )
        }
    }

    /**
     * The two rules are complements only from API 30 up. Below it the direct `java.io.File` route is
     * still opened by the runtime read permission, so withdrawing the path browser there would take
     * away a route that works.
     */
    @Test
    fun `the direct file route is unobtainable only above API 29 and only without the declaration`() {
        assertTrue(StoragePermissionRule.isDirectFileAccessUnobtainable(30, declaresAllFilesAccess = false))
        assertFalse(StoragePermissionRule.isDirectFileAccessUnobtainable(29, declaresAllFilesAccess = false))
        assertFalse(StoragePermissionRule.isDirectFileAccessUnobtainable(30, declaresAllFilesAccess = true))
    }
}
