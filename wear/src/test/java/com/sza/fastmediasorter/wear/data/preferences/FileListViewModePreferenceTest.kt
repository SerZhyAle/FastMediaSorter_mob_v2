package com.sza.fastmediasorter.wear.data.preferences

import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The file list and the navigation screens must never share a stored value (S1730 ADR-3): coupling
 * them would still compile and would still look right on whichever screen was checked first.
 *
 * DataStore needs an Android context and the watch module has no Robolectric harness, so the
 * property is asserted where it actually lives - in the two key names and in the default the
 * mapping falls back to.
 */
class FileListViewModePreferenceTest {

    @Test
    fun `the file list and the navigation screens address different stored keys`() {
        assertNotEquals(
            WearPreferencesRepositoryImpl.PreferencesKeys.VIEW_MODE.name,
            WearPreferencesRepositoryImpl.PreferencesKeys.FILE_LIST_VIEW_MODE.name
        )
    }

    @Test
    fun `an absent stored value keeps the previous list behaviour`() {
        assertEquals(WearViewMode.LIST, WearViewMode.fromNameOrDefault(null))
        assertEquals(WearViewMode.LIST, WearViewMode.fromNameOrDefault("GRID_9"))
    }

    @Test
    fun `every mode round-trips through its stored name`() {
        WearViewMode.entries.forEach { mode ->
            assertEquals(mode, WearViewMode.fromNameOrDefault(mode.name))
        }
    }
}
