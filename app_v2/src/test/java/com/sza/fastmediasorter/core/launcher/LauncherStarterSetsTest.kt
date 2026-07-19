package com.sza.fastmediasorter.core.launcher

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

    // ── itemsFor ────────────────────────────────────────────────────────────

    @Test
    fun `every set opens with a clock and closes with the common tail`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, null, null, streamsAvailable = false)
        assertEquals(listOf("clock", "fn:favorites", "os:settings", "app:__self__"), items.map { it.target })
        assertEquals(LauncherCellKind.GADGET, items.first().kind)
    }

    @Test
    fun `photo frame seeds folder-preview gadget and slideshow shortcut when a resource exists`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.PHOTO_FRAME, lastResourceId = 5, null, false)
        assertEquals(
            listOf("clock", "folder_preview:5", "res:5:SLIDESHOW", "fn:favorites", "os:settings", "app:__self__"),
            items.map { it.target },
        )
    }

    @Test
    fun `null id dependencies are skipped, never seeded as dangling cells`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.PHOTO_FRAME, lastResourceId = null, null, false)
        assertEquals(listOf("clock", "fn:favorites", "os:settings", "app:__self__"), items.map { it.target })
    }

    @Test
    fun `audio profile seeds playlist plus streams only when streams are available`() {
        val withStreams = LauncherStarterSets.itemsFor(DeviceProfileType.AUDIO_PLAYER, null, 7, streamsAvailable = true)
        assertEquals(
            listOf("clock", "playlist:7", "streams", "fn:favorites", "os:settings", "app:__self__"),
            withStreams.map { it.target },
        )
        val withoutStreams =
            LauncherStarterSets.itemsFor(DeviceProfileType.AUDIO_PLAYER, null, 7, streamsAvailable = false)
        assertFalse(withoutStreams.any { it.target == "streams" })
    }

    // ── place (the overlap invariant) ───────────────────────────────────────

    @Test
    fun `place never overlaps two footprints and keeps every cell inside the grid`() {
        // A full audio set at 4 columns: clock 2x1, playlist 2x2, streams 2x2, plus 3 tail shortcuts.
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.AUDIO_PLAYER, null, 7, streamsAvailable = true)
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
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, null, null, false)
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
