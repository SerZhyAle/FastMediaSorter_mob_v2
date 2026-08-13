package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.core.launcher.LauncherStarterSets.StarterResources
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        InternalRouteCatalog.KEY_NETWORK_MONITOR to true,
        InternalRouteCatalog.KEY_OCR to true,
    )

    /** The utilities every profile closes with, below the second header. */
    private val commonTail = listOf("fn:favorites", "os:settings", "app:__self__")

    /** S1587: content opens the desktop, so the first item of every set is the everything-else header. */
    private val contentHeader = listOf("sec:everything_else")

    private val columnCounts = listOf(3, 4, 6, 12)

    private data class ProfileGrid(
        val wifi: Boolean = false,
        val bluetooth: Boolean = false,
        val nowPlaying: Boolean = false,
        val blackScreen: Boolean = false,
        val locationTiles: Boolean = false,
        val maps: Boolean = false,
        val fmRadio: Boolean = false,
    )

    private val profileGrid = mapOf(
        DeviceProfileType.CAR_HEAD_UNIT to ProfileGrid(
            wifi = true,
            bluetooth = true,
            nowPlaying = true,
            blackScreen = true,
            locationTiles = true,
            maps = true,
            fmRadio = true,
        ),
        DeviceProfileType.PERSONAL_SMARTPHONE to ProfileGrid(locationTiles = true, maps = true),
        DeviceProfileType.HOME_TABLET to ProfileGrid(),
        DeviceProfileType.AUDIO_PLAYER to ProfileGrid(
            wifi = true,
            bluetooth = true,
            nowPlaying = true,
            blackScreen = true,
        ),
        DeviceProfileType.TV_MEDIA_BOX to ProfileGrid(
            wifi = true,
            bluetooth = true,
            nowPlaying = true,
            blackScreen = true,
        ),
        DeviceProfileType.MEDIA_PLAYER to ProfileGrid(wifi = true, bluetooth = true, nowPlaying = true),
        DeviceProfileType.VIDEO_PLAYER to ProfileGrid(wifi = true, bluetooth = true, nowPlaying = true),
        DeviceProfileType.PHOTO_FRAME to ProfileGrid(wifi = true, blackScreen = true),
        DeviceProfileType.EBOOK_READER to ProfileGrid(),
        DeviceProfileType.VR_HEADSET to ProfileGrid(wifi = true, bluetooth = true),
        DeviceProfileType.OTHER to ProfileGrid(wifi = true, bluetooth = true),
    )

    // ── itemsFor ────────────────────────────────────────────────────────────

    @Test
    fun `every set opens with the content section and closes with the launcher actions`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, StarterResources(), emptyMap(), emptySet())
        assertEquals(
            contentHeader +
                listOf("clock", "search", "weather", "os:wifi", "os:bluetooth") +
                sectionTail(DeviceProfileType.OTHER),
            items.map { it.target },
        )
        assertEquals(LauncherCellKind.SECTION, items.first().kind)
    }

    @Test
    fun `every launcher action sits under the second header and is seeded once`() {
        val targets = LauncherStarterSets
            .itemsFor(
                DeviceProfileType.PERSONAL_SMARTPHONE,
                StarterResources(recentId = 1),
                allPaddingAvailable,
                emptySet(),
            )
            .map { it.target }
        val contentHeaderIndex = targets.indexOf("sec:everything_else")
        val actionsHeaderIndex = targets.indexOf("sec:app_functions")
        val actions = targets.filter { it.startsWith("act:") }
        // Strategic §6.5: they moved out of the tail rather than being duplicated into the section.
        assertEquals(LauncherActionCatalog.all.size - 1, actions.size)
        assertTrue("content header must come first", contentHeaderIndex < actionsHeaderIndex)
        assertEquals(actions, targets.subList(actionsHeaderIndex + 1, targets.size - commonTail.size))
    }

    @Test
    fun `no gadget is seeded inside the app-functions section`() {
        // Strategic §6.11: a gadget may not cover a header row. S1587 moved that section to the end of
        // the set, so the constraint now reads: nothing below the second header is a gadget.
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            mapOf(InternalRouteCatalog.KEY_STREAMS to true),
            emptySet(),
        )
        val actionsHeaderIndex = items.indexOfFirst { it.target == "sec:app_functions" }
        assertFalse(items.drop(actionsHeaderIndex).any { it.kind == LauncherCellKind.GADGET })
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
            emptySet(),
        )
        assertEquals(
            contentHeader + listOf(
                "clock", "search", "weather",
                "res:1:BROWSE", "res:2:BROWSE", "res:3:BROWSE", "res:4:BROWSE", "res:5:BROWSE", "res:6:BROWSE",
                "altitude", "satellites",
                "fn:streams", "fn:quick_camera", "fn:quick_voice", "fn:calculator", "fn:network_monitor", "fn:ocr",
            ) + sectionTail(DeviceProfileType.PERSONAL_SMARTPHONE),
            items.map { it.target },
        )
    }

    @Test
    fun `unavailable padding features are skipped`() {
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(recentId = 1),
            mapOf(InternalRouteCatalog.KEY_CALCULATOR to true), // only calculator compiled in
            emptySet(),
        )
        assertEquals(
            contentHeader +
                listOf("clock", "search", "weather", "res:1:BROWSE", "altitude", "satellites", "fn:calculator") +
                sectionTail(DeviceProfileType.PERSONAL_SMARTPHONE),
            items.map { it.target },
        )
    }

    @Test
    fun `photo frame seeds folder-preview gadget and slideshow shortcut when a resource exists`() {
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.PHOTO_FRAME,
            StarterResources(lastResourceId = 5),
            emptyMap(),
            emptySet(),
        )
        assertEquals(
            contentHeader +
                listOf("clock", "search", "weather", "folder_preview:5", "res:5:SLIDESHOW", "os:wifi") +
                sectionTail(DeviceProfileType.PHOTO_FRAME),
            items.map { it.target },
        )
    }

    @Test
    fun `null id dependencies are skipped, never seeded as dangling cells`() {
        val items = LauncherStarterSets
            .itemsFor(DeviceProfileType.PHOTO_FRAME, StarterResources(), emptyMap(), emptySet())
        assertEquals(
            contentHeader + "clock" + "search" + "weather" + "os:wifi" + sectionTail(DeviceProfileType.PHOTO_FRAME),
            items.map { it.target },
        )
    }

    @Test
    fun `audio profile seeds playlist plus streams only when streams are available`() {
        val withStreams = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            mapOf(InternalRouteCatalog.KEY_STREAMS to true),
            emptySet(),
        )
        assertEquals(
            contentHeader + listOf(
                "clock", "search", "res:7:BROWSE", "playlist:7", "streams", "os:wifi", "os:bluetooth",
                "audio_now_playing", "fn:streams",
            ) + sectionTail(DeviceProfileType.AUDIO_PLAYER),
            withStreams.map { it.target },
        )
        val withoutStreams = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            emptyMap(),
            emptySet(),
        )
        assertFalse(withoutStreams.any { it.target == "streams" })
        assertFalse(withoutStreams.any { it.target == "fn:streams" })
    }

    @Test
    fun `no third-party app cell is seeded when nothing is installed`() {
        DeviceProfileType.entries.forEach { profile ->
            val targets = LauncherStarterSets
                .itemsFor(profile, StarterResources(), allPaddingAvailable, emptySet())
                .map { it.target }
            LauncherStarterSets.candidatePackages.forEach { candidate ->
                assertFalse("$profile seeded $candidate blind", targets.contains("app:$candidate"))
            }
        }
    }

    @Test
    fun `an installed third-party app is seeded and an absent one is not`() {
        val targets = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(),
            allPaddingAvailable,
            setOf(LauncherStarterSets.PACKAGE_YOUTUBE),
        ).map { it.target }
        assertEquals(1, targets.count { it == "app:${LauncherStarterSets.PACKAGE_YOUTUBE}" })
        assertFalse(targets.contains("app:${LauncherStarterSets.PACKAGE_YOUTUBE_MUSIC}"))
    }

    @Test
    fun `car and smartphone starter sets differ`() {
        val installed = setOf(LauncherStarterSets.PACKAGE_MAPS, FM_RADIO_PACKAGE)
        val car = LauncherStarterSets.itemsFor(
            DeviceProfileType.CAR_HEAD_UNIT,
            StarterResources(),
            allPaddingAvailable,
            installed,
        ).map { it.target }.toSet()
        val smartphone = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(),
            allPaddingAvailable,
            installed,
        ).map { it.target }.toSet()

        assertNotEquals(car, smartphone)
        assertTrue("car needs speed", "speed" in car)
        assertFalse("smartphone must not get speed", "speed" in smartphone)
    }

    @Test
    fun `profile starter table matches every approved cross profile assignment`() {
        val installed = setOf(LauncherStarterSets.PACKAGE_MAPS, FM_RADIO_PACKAGE)
        assertEquals(DeviceProfileType.entries.toSet(), profileGrid.keys)
        profileGrid.forEach { (profile, expected) ->
            val targets = LauncherStarterSets
                .itemsFor(profile, StarterResources(), allPaddingAvailable, installed)
                .map { it.target }.toSet()
            assertEquals("$profile weather", profile != DeviceProfileType.AUDIO_PLAYER, "weather" in targets)
            assertEquals("$profile Wi-Fi", expected.wifi, "os:wifi" in targets)
            assertEquals("$profile Bluetooth", expected.bluetooth, "os:bluetooth" in targets)
            assertEquals("$profile now-playing", expected.nowPlaying, "audio_now_playing" in targets)
            assertEquals("$profile black screen", expected.blackScreen, "act:black_screen" in targets)
            assertEquals("$profile altitude", expected.locationTiles, "altitude" in targets)
            assertEquals("$profile satellites", expected.locationTiles, "satellites" in targets)
            assertEquals("$profile speed", profile == DeviceProfileType.CAR_HEAD_UNIT, "speed" in targets)
            assertEquals("$profile maps", expected.maps, "app:${LauncherStarterSets.PACKAGE_MAPS}" in targets)
            assertEquals("$profile FM radio", expected.fmRadio, "app:$FM_RADIO_PACKAGE" in targets)
            assertTrue("$profile all apps", "act:all_apps" in targets)
        }
    }

    // ── place (the overlap invariant) ───────────────────────────────────────

    @Test
    fun `place never overlaps two footprints and keeps every cell inside the grid`() {
        // A full audio set at 4 columns: clock 2x1, playlist 2x2, streams 2x2, plus resource + tail cells.
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            mapOf(InternalRouteCatalog.KEY_STREAMS to true),
            emptySet(),
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
            emptySet(),
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
    fun `imported shortcuts stay overlap-free at every supported column count`() {
        val imported = importedPins()
        assertTrue(imported.all { it.target.startsWith(LauncherCellCommand.PREFIX_PIN) })
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(recentId = 1, allAudioId = 2, allImagesId = 3),
            allPaddingAvailable,
            emptySet(),
            importedShortcuts = imported,
        )
        columnCounts.forEach { columns ->
            val placed = LauncherStarterSets.place(items, columns)
            assertNoOverlap(placed)
            placed.forEach { assertTrue("cell past right edge at $columns", it.colIndex + it.spanW <= columns) }
        }
    }

    @Test
    fun `imported shortcuts sit in the content section, never the app-functions section`() {
        val imported = importedPins()
        val targets = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(recentId = 1),
            allPaddingAvailable,
            emptySet(),
            importedShortcuts = imported,
        ).map { it.target }
        val contentHeaderIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_EVERYTHING_ELSE))
        val actionsHeaderIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_APP_FUNCTIONS))
        imported.forEach { item ->
            val index = targets.indexOf(item.target)
            assertTrue("imported target absent: ${item.target}", index > contentHeaderIndex)
            assertTrue("imported target under the app-functions header: ${item.target}", index < actionsHeaderIndex)
        }
    }

    @Test
    fun `a header persists the widest span while the packed one fits the grid it is seeded on`() {
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, StarterResources(), emptyMap(), emptySet())
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
        val items = LauncherStarterSets.itemsFor(DeviceProfileType.OTHER, StarterResources(), emptyMap(), emptySet())
        val placed = LauncherStarterSets.place(items, columns = 4)
        assertTrue(placed.any { it.item.target == "app:__self__" })
    }

    @Test
    fun `car starter set packs without overlap at the minimum column count`() {
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.CAR_HEAD_UNIT,
            StarterResources(
                recentId = 1,
                allAudioId = 2,
                allImagesId = 3,
                allVideoId = 4,
                allDocsId = 5,
                cameraId = 6,
            ),
            allPaddingAvailable,
            setOf(
                LauncherStarterSets.PACKAGE_YOUTUBE,
                LauncherStarterSets.PACKAGE_YOUTUBE_MUSIC,
                LauncherStarterSets.PACKAGE_MAPS,
                FM_RADIO_PACKAGE,
            ),
        )
        val placed = LauncherStarterSets.place(items, columns = MIN_COLUMNS)
        assertNoOverlap(placed)
        placed.forEach { assertTrue("cell past right edge", it.colIndex + it.spanW <= MIN_COLUMNS) }
    }

    @Test
    fun `no cell is packed above the section floor its header set`() {
        val items = smartphoneFullSet()
        intArrayOf(3, 4, 8).forEach { columns ->
            val placed = LauncherStarterSets.place(items, columns)
            var floor = 0
            placed.forEach { cell ->
                assertTrue("${cell.item.target} above its header at $columns", cell.rowIndex >= floor)
                if (cell.item.kind == LauncherCellKind.SECTION) floor = cell.rowIndex
            }
        }
    }

    @Test
    fun `positional membership matches the section each cell was seeded under`() {
        // S1428 reads membership off the rows, so a cell that packs above its own header belongs to the
        // section before it - the defect S1587 recorded on the device.
        val placed = LauncherStarterSets.place(smartphoneFullSet(), columns = 4)
        var seededUnder: String? = null
        placed.forEach { cell ->
            if (cell.item.kind == LauncherCellKind.SECTION) {
                seededUnder = cell.item.target
                return@forEach
            }
            val ownedBy = placed
                .filter { it.item.kind == LauncherCellKind.SECTION && it.rowIndex <= cell.rowIndex }
                .maxByOrNull { it.rowIndex }
                ?.item?.target
            assertEquals("${cell.item.target} drifted out of its section", seededUnder, ownedBy)
        }
    }

    @Test
    fun `first_screen on a phone carries the resources, not the launcher actions`() {
        val placed = LauncherStarterSets.place(smartphoneFullSet(), columns = PHONE_COLUMNS)
        placed.filter { it.item.target.startsWith("res:") }.forEach {
            assertTrue("${it.item.target} fell below the fold", it.rowIndex < FIRST_SCREEN_ROWS)
        }
        val firstAction = placed.first { it.item.target.startsWith("act:") }
        assertTrue("launcher actions still occupy the first screen", firstAction.rowIndex >= FIRST_SCREEN_ROWS)
    }

    private fun smartphoneFullSet(): List<LauncherStarterSets.StarterItem> = LauncherStarterSets.itemsFor(
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
        setOf(LauncherStarterSets.PACKAGE_YOUTUBE, LauncherStarterSets.PACKAGE_MAPS),
    )

    /** S1587: the launcher's own actions close the set, under the second header, above the common tail. */
    private fun sectionTail(profile: DeviceProfileType): List<String> = buildList {
        add("sec:app_functions")
        addAll(
            LauncherActionCatalog.all
                .filter { it.key != LauncherActionCatalog.KEY_BLACK_SCREEN || profile in BLACK_SCREEN_PROFILES }
                .map { "act:${it.key}" },
        )
        addAll(commonTail)
    }

    /** S1613: pinned-shortcut items shaped exactly as the seed encodes what the platform hands back. */
    private fun importedPins(): List<LauncherStarterSets.StarterItem> =
        listOf("alpha", "beta", "gamma").map { shortcutId ->
            LauncherStarterSets.StarterItem(
                kind = LauncherCellKind.SHORTCUT,
                target = LauncherCellCommand.PinnedShortcut("com.example.publisher", shortcutId, shortcutId).encode(),
            )
        }

    private fun sectionTarget(sectionKey: String): String = LauncherCellCommand.Section(sectionKey).encode()

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

    private companion object {
        const val FM_RADIO_PACKAGE = "com.android.fmradio"
        const val MIN_COLUMNS = 3

        /** The reference phone of S1587: 384dp wide at density factor 1.0, so four 96dp columns. */
        const val PHONE_COLUMNS = 4

        /** Cell rows that fit above the fold on that phone - research `02__first-screen-order.md`. */
        const val FIRST_SCREEN_ROWS = 7

        val BLACK_SCREEN_PROFILES = setOf(
            DeviceProfileType.CAR_HEAD_UNIT,
            DeviceProfileType.AUDIO_PLAYER,
            DeviceProfileType.TV_MEDIA_BOX,
            DeviceProfileType.PHOTO_FRAME,
        )
    }
}
