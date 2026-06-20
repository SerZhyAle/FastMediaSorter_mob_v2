package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollapsibleSectionStateMigrationTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `migration remaps legacy keys and sets the guard flag`() {
        context.getSharedPreferences("general_sections_state", Context.MODE_PRIVATE).edit()
            .putBoolean("section_interface_expanded", true).apply()
        context.getSharedPreferences("playback_sections_state", Context.MODE_PRIVATE).edit()
            .putBoolean("section_sorting_expanded", true).apply()
        context.getSharedPreferences("media_sections_state", Context.MODE_PRIVATE).edit()
            .putBoolean("section_vr_expanded", false).apply()

        CollapsibleSectionStateMigration(context).migrateIfNeeded()

        val consolidated = context.getSharedPreferences(CollapsibleSectionStore.NAMESPACE, Context.MODE_PRIVATE)
        assertTrue(consolidated.getBoolean("general__interface", false))
        assertTrue(consolidated.getBoolean("playback__sorting", false))
        // A user-collapsed VR section migrates as collapsed even though its new default is expanded.
        assertFalse(consolidated.getBoolean("media__vr", true))
        assertTrue(consolidated.getBoolean(CollapsibleSectionStateMigration.MIGRATION_DONE_KEY, false))
    }

    @Test
    fun `second migration run is a no-op`() {
        CollapsibleSectionStateMigration(context).migrateIfNeeded()

        // Write a legacy value after the guard is set; a second run must not pick it up.
        context.getSharedPreferences("general_sections_state", Context.MODE_PRIVATE).edit()
            .putBoolean("section_system_expanded", true).apply()
        CollapsibleSectionStateMigration(context).migrateIfNeeded()

        val consolidated = context.getSharedPreferences(CollapsibleSectionStore.NAMESPACE, Context.MODE_PRIVATE)
        assertFalse(consolidated.getBoolean("general__system", false))
    }
}
