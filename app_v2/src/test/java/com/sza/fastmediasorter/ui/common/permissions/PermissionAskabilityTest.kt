package com.sza.fastmediasorter.ui.common.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure decision behind [canRequestPermission]. The framework reads (manifest declaration, grant, request
 * marker, platform rationale) are not under test here; only how the four answers combine.
 */
class PermissionAskabilityTest {

    @Test
    fun `an undeclared permission is never requestable whatever the other answers say`() {
        val booleans = listOf(false, true)
        for (granted in booleans) {
            for (everAsked in booleans) {
                for (shouldShowRationale in booleans) {
                    assertFalse(
                        "granted=$granted everAsked=$everAsked rationale=$shouldShowRationale",
                        isPermissionRequestable(
                            declared = false,
                            granted = granted,
                            everAsked = everAsked,
                            shouldShowRationale = shouldShowRationale,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `a declared and granted permission is not requestable`() {
        assertFalse(
            isPermissionRequestable(declared = true, granted = true, everAsked = true, shouldShowRationale = false),
        )
    }

    @Test
    fun `a declared permission that was never asked for is requestable`() {
        assertTrue(
            isPermissionRequestable(declared = true, granted = false, everAsked = false, shouldShowRationale = false),
        )
    }

    @Test
    fun `a declared permission asked before is requestable while the platform still offers a rationale`() {
        assertTrue(
            isPermissionRequestable(declared = true, granted = false, everAsked = true, shouldShowRationale = true),
        )
    }

    @Test
    fun `a declared permission asked before with no rationale is permanently denied and not requestable`() {
        assertFalse(
            isPermissionRequestable(declared = true, granted = false, everAsked = true, shouldShowRationale = false),
        )
    }
}
