package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.core.launcher.LauncherStarterSets.StarterResources
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
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

    // S1428: every set opens with the app-functions header over the four launcher actions, and a second
    // header ends that section. Named once so a fifth action is one edit here, not six.
    private val sectionHead = listOf(
        "sec:app_functions",
        "act:app_settings",
        "act:launcher_settings",
        "act:edit_desktop",
        "act:exit_launcher_mode",
        "sec:everything_else",
    )

    /** The utilities every profile closes with, below the second header. */
    private val commonTail = listOf("fn:favorites", "os:settings", "app:__self__")

    private val columnCounts = listOf(3, 4, 6, 12)

    // ── itemsFor ────────────────────────────────────────────────────────────

    @Test
    fun `every set opens with the app-functions section and closes with the common tail`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, StarterResources(), emptyMap())
        assertEquals(sectionHead + "clock" + commonTail, items.map { it.target })
        assertEquals(LauncherCellKind.SECTION, items.first().kind)
    }

    @Test
    fun `the four launcher actions sit between the two headers and are seeded once`() {
        val targets = LauncherStarterSets
            .itemsFor(DeviceProfileType.PERSONAL_SMARTPHONE, StarterResources(recentId = 1), allPaddingAvailable)
            .map { it.target }
        val firstHeader = targets.indexOf("sec:app_functions")
        val secondHeader = targets.indexOf("sec:everything_else")
        val actions = targets.filter { it.startsWith("act:") }
        // Strategic §6.5: they moved out of the tail rather than being duplicated into the section.
        assertEquals(4, actions.size)
        assertEquals(actions, targets.subList(firstHeader + 1, secondHeader))
    }

    @Test
    fun `no gadget is seeded inside the app-functions section`() {
        // Strategic §6.11: a gadget may not cover a header row. The clock is the one gadget every
        // profile gets, so the seeded order has to keep it below the second header by construction.
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            mapOf(InternalRouteCatalog.KEY_STREAMS to true),
        )
        val secondHeader = items.indexOfFirst { it.target == "sec:everything_else" }
        assertFalse(items.take(secondHeader).any { it.kind == LauncherCellKind.GADGET })
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
            sectionHead + listOf(
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
            sectionHead + listOf("clock", "res:1:BROWSE", "fn:calculator") + commonTail,
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
            sectionHead + listOf("clock", "folder_preview:5", "res:5:SLIDESHOW") + commonTail,
            items.map { it.target },
        )
    }

    @Test
    fun `null id dependencies are skipped, never seeded as dangling cells`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.PHOTO_FRAME, StarterResources(), emptyMap())
        assertEquals(sectionHead + "clock" + commonTail, items.map { it.target })
    }

    @Test
    fun `audio profile seeds playlist plus streams only when streams are available`() {
        val withStreams = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            mapOf(InternalRouteCatalog.KEY_STREAMS to true),
        )
        assertEquals(
            sectionHead + listOf("clock", "res:7:BROWSE", "playlist:7", "streams", "fn:streams") + commonTail,
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
    fun `a full-width header stays overlap-free at every supported column count`() {
        // S1428 step 05.3: a header is the first starter item as wide as the grid itself, so it is the
        // case most likely to collide with whatever the packer places next - the narrow grid worst of all.
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(recentId = 1, allAudioId = 2, allImagesId = 3),
            allPaddingAvailable,
        )
        columnCounts.forEach { columns ->
            val placed = LauncherStarterSets.place(items, columns)
            assertNoOverlap(placed)
            placed.forEach { assertTrue("cell past right edge at $columns", it.colIndex + it.spanW <= columns) }
            placed.filter { it.item.kind == LauncherCellKind.SECTION }.forEach {
                assertEquals("header not full width at $columns", columns, it.spanW)
                assertEquals("header not at column 0 at $columns", 0, it.colIndex)
            }
        }
    }

    @Test
    fun `a header persists the widest span while the packed one fits the grid it is seeded on`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, StarterResources(), emptyMap())
        val placed = LauncherStarterSets.place(items, columns = 4)
        val header = placed.first { it.item.kind == LauncherCellKind.SECTION }
        assertEquals(4, header.spanW)
        assertEquals(LauncherSectionMembership.HEADER_STORED_SPAN_W, header.storedSpanW)
        // Every other kind persists exactly what it was packed at, edge clamp included.
        val shortcut = placed.first { it.item.kind == LauncherCellKind.SHORTCUT }
        assertEquals(shortcut.spanW, shortcut.storedSpanW)
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
