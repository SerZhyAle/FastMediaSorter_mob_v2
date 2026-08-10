package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.core.launcher.LauncherStarterSets.StarterResources
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S0404: [LauncherStarterSets] (src/main) hardcodes the gadget target keys because it cannot import
 * [LauncherGadgetRegistry] (src/launcherEnabled - Rule 14 forbids a launcherEnabled import in main).
 * The hardcoded keys and the registry consts must stay identical, or the seed produces cells the
 * desktop renders as "unknown gadget". Nothing compile-time ties them, so this test does - it fails
 * the moment a `KEY_*` rename in the registry drifts from the string the starter set emits.
 *
 * S1428 added a second copy under the same constraint: the span a section header is stored at mirrors
 * the renderer's own maximum column count.
 *
 * Lives in `src/testLauncherEnabled`: [LauncherGadgetRegistry] ships only in the launcherEnabled source
 * set, and that set is mounted by two flavors. S1498 moved this out of `src/testStandard`, whose single
 * flavor meant the parity this guards was never checked on the other build that ships the registry.
 */
@Suppress("FunctionNaming") // backtick test names, project convention (cf. LauncherGridGeometryTest)
class LauncherStarterSetsParityTest {

    @Test
    fun `the stored header span mirrors the widest grid the renderer can draw`() {
        // A header stored narrower than the grid it is drawn on leaves the rest of its row free in the
        // database while covering it on screen, so a cell dropped there lands underneath the header.
        assertEquals(LauncherGridGeometry.MAX_COLUMNS, LauncherSectionMembership.HEADER_STORED_SPAN_W)
    }

    @Test
    fun `starter gadget target keys match the registry consts`() {
        // Clock: the one gadget every profile seeds, whose bare target is the key itself.
        val clock = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, StarterResources(), emptyMap())
            .first { it.kind == LauncherCellKind.GADGET }
        assertEquals(LauncherGadgetRegistry.KEY_CLOCK, clock.target)

        val folderPreview = LauncherStarterSets
            .itemsFor(DeviceProfileType.PHOTO_FRAME, StarterResources(lastResourceId = 1L), emptyMap())
            .first { it.target.startsWith(LauncherGadgetRegistry.KEY_FOLDER_PREVIEW) }
        assertEquals("${LauncherGadgetRegistry.KEY_FOLDER_PREVIEW}:1", folderPreview.target)

        val playlist = LauncherStarterSets
            .itemsFor(DeviceProfileType.AUDIO_PLAYER, StarterResources(allAudioId = 2L), emptyMap())
            .first { it.target.startsWith(LauncherGadgetRegistry.KEY_PLAYLIST) }
        assertEquals("${LauncherGadgetRegistry.KEY_PLAYLIST}:2", playlist.target)

        val streams = LauncherStarterSets
            .itemsFor(
                DeviceProfileType.AUDIO_PLAYER,
                StarterResources(allAudioId = 2L),
                mapOf(InternalRouteCatalog.KEY_STREAMS to true),
            )
            .first { it.target == LauncherGadgetRegistry.KEY_STREAMS }
        assertEquals(LauncherGadgetRegistry.KEY_STREAMS, streams.target)
    }
}
