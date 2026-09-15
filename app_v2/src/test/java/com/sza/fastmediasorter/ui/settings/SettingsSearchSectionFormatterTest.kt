package com.sza.fastmediasorter.ui.settings

import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSearchSectionFormatterTest {

    @Test
    fun `broadcast section uses its own label resource`() {
        assertEquals(R.string.settings_broadcast_section, settingsSearchSectionResId("broadcast"))
    }

    @Test
    fun `unknown section uses other label resource`() {
        assertEquals(R.string.settings_category_other, settingsSearchSectionResId("unknown"))
    }
}
