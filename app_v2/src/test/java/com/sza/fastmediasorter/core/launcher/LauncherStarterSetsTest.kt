package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.core.launcher.LauncherStarterSets.StarterResources
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0404: [LauncherStarterSets] is pure data + a pure row-major packer. [LauncherStarterSets.place] is
 * the SOLE guarantor that seeded cells never overlap - the seed path inserts through `seedIfEmpty`,
 * which (unlike the interactive `addCell`/`moveCell`) does not run `findOverlapping`. That invariant is
 * exactly what is worth testing here. Pure Kotlin, no Android imports (not launcher UI, so in scope
 * despite the iteration-1 no-UI-tests boundary - same reasoning as the Phase 07 repository tests).
 */
@Suppress("FunctionNaming") // backtick test names, project convention (cf. LauncherGridGeometryTest)
class LauncherStarterSetsTest {

    private val allPaddingAvailable = mapOf(
        InternalRouteCatalog.KEY_STREAMS to true,
        InternalRouteCatalog.KEY_QUICK_CAMERA to true,
        InternalRouteCatalog.KEY_QUICK_VOICE to true,
        InternalRouteCatalog.KEY_CALCULATOR to true,
        InternalRouteCatalog.KEY_OCR to true,
    )

    // S1402: the tail every profile ends with - the utilities, then the four launcher actions. Named
    // once so a fifth action is one edit here, not five.
    private val commonTail = listOf(
        "fn:favorites",
        "os:settings",
        "app:__self__",
        "act:app_settings",
        "act:launcher_settings",
        "act:edit_desktop",
        "act:exit_launcher_mode",
    )

    // ── itemsFor ────────────────────────────────────────────────────────────

    @Test
    fun `every set opens with a clock and closes with the common tail`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, StarterResources(), emptyMap())
        assertEquals(listOf("clock") + commonTail, items.map { it.target })
        assertEquals(LauncherCellKind.GADGET, items.first().kind)
    }

    @Test
    fun `mainstream profile seeds the full resource and padding set`() {
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(
                recentId = 1,
                allAudioId = 2,
                allImagesId = 3,
                allVideoId = 4,
                allDocsId = 5,
                cameraId = 6,
            ),
            allPaddingAvailable,
        )
        assertEquals(
            listOf(
                "clock",
                "res:1:BROWSE", "res:2:BROWSE", "res:3:BROWSE", "res:4:BROWSE", "res:5:BROWSE", "res:6:BROWSE",
                "fn:streams", "fn:quick_camera", "fn:quick_voice", "fn:calculator", "fn:ocr",
            ) + commonTail,
            items.map { it.target },
        )
    }

    @Test
    fun `unavailable padding features are skipped`() {
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(recentId = 1),
            mapOf(InternalRouteCatalog.KEY_CALCULATOR to true), // only calculator compiled in
        )
        assertEquals(
            listOf("clock", "res:1:BROWSE", "fn:calculator") + commonTail,
            items.map { it.target },
        )
    }

    @Test
    fun `photo frame seeds folder-preview gadget and slideshow shortcut when a resource exists`() {
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.PHOTO_FRAME,
            StarterResources(lastResourceId = 5),
            emptyMap(),
        )
        assertEquals(
            listOf("clock", "folder_preview:5", "res:5:SLIDESHOW") + commonTail,
            items.map { it.target },
        )
    }

    @Test
    fun `null id dependencies are skipped, never seeded as dangling cells`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.PHOTO_FRAME, StarterResources(), emptyMap())
        assertEquals(listOf("clock") + commonTail, items.map { it.target })
    }

    @Test
    fun `audio profile seeds playlist plus streams only when streams are available`() {
        val withStreams = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            mapOf(InternalRouteCatalog.KEY_STREAMS to true),
        )
        assertEquals(
            listOf("clock", "res:7:BROWSE", "playlist:7", "streams", "fn:streams") + commonTail,
            withStreams.map { it.target },
        )
        val withoutStreams = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            emptyMap(),
        )
        assertFalse(withoutStreams.any { it.target == "streams" })
        assertFalse(withoutStreams.any { it.target == "fn:streams" })
    }

    // ── place (the overlap invariant) ───────────────────────────────────────

    @Test
    fun `place never overlaps two footprints and keeps every cell inside the grid`() {
        // A full audio set at 4 columns: clock 2x1, playlist 2x2, streams 2x2, plus resource + tail cells.
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            mapOf(InternalRouteCatalog.KEY_STREAMS to true),
        )
        val placed = LauncherStarterSets.place(items, columns = 4)
        assertNoOverlap(placed)
        placed.forEach { assertTrue("cell past right edge", it.colIndex + it.spanW <= 4) }
    }

    @Test
    fun `a wide gadget reserves its full height so the next cell does not land under it`() {
        val gadget = LauncherStarterSets.StarterItem(LauncherCellKind.GADGET, "streams", spanW = 2, spanH = 2)
        val shortcut = LauncherStarterSets.StarterItem(LauncherCellKind.SHORTCUT, "os:settings")
        val placed = LauncherStarterSets.place(listOf(gadget, shortcut), columns = 2)
        // The 2x2 fills rows 0-1 across both columns; the shortcut cannot fit until row 2.
        assertEquals(0, placed[0].rowIndex)
        assertEquals(2, placed[1].rowIndex)
        assertNoOverlap(placed)
    }

    @Test
    fun `place clamps a span wider than the grid instead of spinning forever`() {
        val wide = LauncherStarterSets.StarterItem(LauncherCellKind.GADGET, "streams", spanW = 2, spanH = 2)
        val placed = LauncherStarterSets.place(listOf(wide), columns = 1)
        assertEquals(1, placed.single().spanW)
        assertEquals(0, placed.single().colIndex)
    }

    @Test
    fun `place preserves the own-app placeholder target for the use case to substitute`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, StarterResources(), emptyMap())
        val placed = LauncherStarterSets.place(items, columns = 4)
        assertTrue(placed.any { it.item.target == "app:__self__" })
    }

    private fun assertNoOverlap(placed: List<LauncherStarterSets.PlacedStarterItem>) {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        for (p in placed) {
            for (r in p.rowIndex until p.rowIndex + p.spanH) {
                for (c in p.colIndex until p.colIndex + p.spanW) {
                    assertTrue("overlap at ($r, $c)", occupied.add(r to c))
                }
            }
        }
    }
}
