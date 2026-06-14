package com.sza.fastmediasorter.ui.settings

import com.sza.fastmediasorter.domain.model.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAllFilesOverrideTest {

    @Test
    fun `manual support toggle off exits all files shortcut`() {
        val updated = AppSettings(allFiles = true).exitAllFilesForManualSupportToggle(isChecked = false)

        assertFalse(updated.allFiles)
    }

    @Test
    fun `manual support toggle on keeps all files shortcut`() {
        val updated = AppSettings(allFiles = true).exitAllFilesForManualSupportToggle(isChecked = true)

        assertTrue(updated.allFiles)
    }

    @Test
    fun `manual support toggle keeps existing non all files state`() {
        val updated = AppSettings(allFiles = false).exitAllFilesForManualSupportToggle(isChecked = false)

        assertFalse(updated.allFiles)
    }
}
