package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.domain.model.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bottom bar's Copy/Move must obey the same two switches as the player panel and the per-file menu. */
class BrowseOperationsBarVisibilityTest {

    @Test
    fun `copy shows for a selection when copying is allowed`() {
        assertTrue(BrowseStateUiUpdater.isCopyActionVisible(hasSelection = true, settings = AppSettings()))
    }

    @Test
    fun `copy is hidden when copying is disabled`() {
        val settings = AppSettings(enableCopying = false)
        assertFalse(BrowseStateUiUpdater.isCopyActionVisible(hasSelection = true, settings = settings))
    }

    @Test
    fun `copy is hidden without a selection`() {
        assertFalse(BrowseStateUiUpdater.isCopyActionVisible(hasSelection = false, settings = AppSettings()))
    }

    @Test
    fun `move shows for a writable selection when moving is allowed`() {
        assertTrue(
            BrowseStateUiUpdater.isMoveActionVisible(hasSelection = true, canWrite = true, settings = AppSettings())
        )
    }

    @Test
    fun `move is hidden when moving is disabled`() {
        val settings = AppSettings(enableMoving = false)
        assertFalse(BrowseStateUiUpdater.isMoveActionVisible(hasSelection = true, canWrite = true, settings = settings))
    }

    @Test
    fun `move is hidden on a read-only resource`() {
        assertFalse(
            BrowseStateUiUpdater.isMoveActionVisible(hasSelection = true, canWrite = false, settings = AppSettings())
        )
    }

    @Test
    fun `disabling copying leaves move untouched`() {
        val settings = AppSettings(enableCopying = false)
        assertTrue(BrowseStateUiUpdater.isMoveActionVisible(hasSelection = true, canWrite = true, settings = settings))
    }
}
