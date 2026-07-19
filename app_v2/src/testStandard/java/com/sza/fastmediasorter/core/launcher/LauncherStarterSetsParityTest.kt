package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S0404: [LauncherStarterSets] (src/main) hardcodes the gadget target keys because it cannot import
 * [LauncherGadgetRegistry] (src/launcherEnabled - Rule 14 forbids a launcherEnabled import in main).
 * The hardcoded keys and the registry consts must stay identical, or the seed produces cells the
 * desktop renders as "unknown gadget". Nothing compile-time ties them, so this test does - it fails
 * the moment a `KEY_*` rename in the registry drifts from the string the starter set emits.
 *
 * Lives in `src/testStandard`: [LauncherGadgetRegistry] ships only in the launcherEnabled source set.
 */
@Suppress("FunctionNaming") // backtick test names, project convention (cf. LauncherGridGeometryTest)
class LauncherStarterSetsParityTest {

    @Test
    fun `starter gadget target keys match the registry consts`() {
        // Clock: every profile opens with the clock gadget, whose bare target is the key itself.
        val clock = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, null, null, streamsAvailable = false).first()
        assertEquals(LauncherGadgetRegistry.KEY_CLOCK, clock.target)

        val folderPreview = LauncherStarterSets
            .itemsFor(DeviceProfileType.PHOTO_FRAME, lastResourceId = 1L, null, false)
            .first { it.target.startsWith(LauncherGadgetRegistry.KEY_FOLDER_PREVIEW) }
        assertEquals("${LauncherGadgetRegistry.KEY_FOLDER_PREVIEW}:1", folderPreview.target)

        val playlist = LauncherStarterSets
            .itemsFor(DeviceProfileType.AUDIO_PLAYER, null, allAudioResourceId = 2L, false)
            .first { it.target.startsWith(LauncherGadgetRegistry.KEY_PLAYLIST) }
        assertEquals("${LauncherGadgetRegistry.KEY_PLAYLIST}:2", playlist.target)

        val streams = LauncherStarterSets
            .itemsFor(DeviceProfileType.AUDIO_PLAYER, null, 2L, streamsAvailable = true)
            .first { it.target == LauncherGadgetRegistry.KEY_STREAMS }
        assertEquals(LauncherGadgetRegistry.KEY_STREAMS, streams.target)
    }
}
