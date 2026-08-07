package com.sza.fastmediasorter.core.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1424: composition is a pure function, so what a resource offers is settled here rather than only
 * on a device (strategic 11.6). Assertions name the whole list, not its size - a wrong item in the
 * right count is exactly the regression this guards.
 */
class ResourceActionCatalogTest {

    private val plainLocalFolder = ResourceActionCatalog.Facts()

    @Test
    fun `plain local folder on the main window offers everything unconditional`() {
        val actions = ResourceActionCatalog.actionsFor(MenuActionSurface.MAIN_WINDOW, plainLocalFolder)

        assertEquals(
            listOf(
                ResourceMenuAction.OPEN,
                ResourceMenuAction.ADD_TO_HOME_SCREEN,
                ResourceMenuAction.EDIT,
                ResourceMenuAction.COPY,
                ResourceMenuAction.EXPORT,
                ResourceMenuAction.SCAN,
                ResourceMenuAction.MOVE_UP,
                ResourceMenuAction.MOVE_DOWN,
                ResourceMenuAction.MOVE_TO_TOP,
                ResourceMenuAction.MOVE_TO_BOTTOM,
                ResourceMenuAction.DELETE,
            ),
            actions,
        )
    }

    @Test
    fun `predefined virtual resource cannot be copied or exported`() {
        val actions = ResourceActionCatalog.actionsFor(
            MenuActionSurface.MAIN_WINDOW,
            plainLocalFolder.copy(isPredefinedVirtual = true),
        )

        assertFalse(actions.contains(ResourceMenuAction.COPY))
        assertFalse(actions.contains(ResourceMenuAction.EXPORT))
        assertTrue(actions.contains(ResourceMenuAction.DELETE))
    }

    @Test
    fun `sftp resource offers access sharing`() {
        val actions = ResourceActionCatalog.actionsFor(
            MenuActionSurface.MAIN_WINDOW,
            plainLocalFolder.copy(isSftp = true),
        )

        assertTrue(actions.contains(ResourceMenuAction.SHARE_SFTP_ACCESS))
    }

    @Test
    fun `slideshow eligibility and device capabilities each add exactly their own row`() {
        val actions = ResourceActionCatalog.actionsFor(
            MenuActionSurface.MAIN_WINDOW,
            plainLocalFolder.copy(
                isQuickSlideshowEligible = true,
                isNewWindowAvailable = true,
                isVrCinemaAvailable = true,
            ),
        )

        assertTrue(actions.contains(ResourceMenuAction.LAUNCH_PLAYER))
        assertTrue(actions.contains(ResourceMenuAction.OPEN_IN_SEPARATE_WINDOW))
        assertTrue(actions.contains(ResourceMenuAction.OPEN_IN_VR_CINEMA))
    }

    @Test
    fun `desktop never offers a reorder row or a separate window whatever the resource is`() {
        allFactCombinations().forEach { facts ->
            val actions = ResourceActionCatalog.actionsFor(MenuActionSurface.LAUNCHER_DESKTOP, facts)

            assertFalse("MOVE_UP leaked for $facts", actions.contains(ResourceMenuAction.MOVE_UP))
            assertFalse("MOVE_DOWN leaked for $facts", actions.contains(ResourceMenuAction.MOVE_DOWN))
            assertFalse("MOVE_TO_TOP leaked for $facts", actions.contains(ResourceMenuAction.MOVE_TO_TOP))
            assertFalse(
                "MOVE_TO_BOTTOM leaked for $facts",
                actions.contains(ResourceMenuAction.MOVE_TO_BOTTOM),
            )
            assertFalse(
                "OPEN_IN_SEPARATE_WINDOW leaked for $facts",
                actions.contains(ResourceMenuAction.OPEN_IN_SEPARATE_WINDOW),
            )
        }
    }

    @Test
    fun `desktop list is the main window list minus the excluded five, order preserved`() {
        allFactCombinations().forEach { facts ->
            val mainWindow = ResourceActionCatalog.actionsFor(MenuActionSurface.MAIN_WINDOW, facts)
            val desktop = ResourceActionCatalog.actionsFor(MenuActionSurface.LAUNCHER_DESKTOP, facts)

            assertEquals("mismatch for $facts", mainWindow.filter { it !in DESKTOP_EXCLUDED }, desktop)
        }
    }

    @Test
    fun `a host that cannot run an action leaves it out rather than showing it dead`() {
        val actions = ResourceActionCatalog.actionsFor(
            MenuActionSurface.LAUNCHER_DESKTOP,
            plainLocalFolder,
        ) { it != ResourceMenuAction.EXPORT }

        assertFalse(actions.contains(ResourceMenuAction.EXPORT))
        assertTrue(actions.contains(ResourceMenuAction.OPEN))
    }

    /** Every combination of the five booleans, so a leak cannot hide behind one untested flag. */
    private fun allFactCombinations(): List<ResourceActionCatalog.Facts> =
        (0 until COMBINATIONS).map { mask ->
            ResourceActionCatalog.Facts(
                isPredefinedVirtual = mask and FLAG_VIRTUAL != 0,
                isSftp = mask and FLAG_SFTP != 0,
                isQuickSlideshowEligible = mask and FLAG_SLIDESHOW != 0,
                isNewWindowAvailable = mask and FLAG_WINDOW != 0,
                isVrCinemaAvailable = mask and FLAG_VR != 0,
            )
        }

    private companion object {
        const val FLAG_VIRTUAL = 1
        const val FLAG_SFTP = 2
        const val FLAG_SLIDESHOW = 4
        const val FLAG_WINDOW = 8
        const val FLAG_VR = 16

        /** Two states for each of the five flags. */
        const val COMBINATIONS = 32

        val DESKTOP_EXCLUDED = setOf(
            ResourceMenuAction.MOVE_UP,
            ResourceMenuAction.MOVE_DOWN,
            ResourceMenuAction.MOVE_TO_TOP,
            ResourceMenuAction.MOVE_TO_BOTTOM,
            ResourceMenuAction.OPEN_IN_SEPARATE_WINDOW,
        )
    }
}
