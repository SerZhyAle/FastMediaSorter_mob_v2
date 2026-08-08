package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership

/**
 * S0404: profile -> starter desktop, as pure data + a pure row-major packer (strategic §5.3: adding a
 * set must not touch the surface). [SeedLauncherDesktopUseCase][com.sza.fastmediasorter.domain.usecase.launcher.SeedLauncherDesktopUseCase]
 * resolves the ids/availability, substitutes the own-app placeholder, and persists the placed cells.
 *
 * Both [itemsFor] and [place] are pure and unit-tested ([LauncherStarterSetsTest]) - [place] is the
 * SOLE guarantor that seeded cells never overlap, because `seedIfEmpty` inserts without the
 * `findOverlapping` guard that the interactive `addCell`/`moveCell` paths run.
 */
object LauncherStarterSets {

    /**
     * Placeholder package in a [LauncherCellKind.SHORTCUT] `app:` target; the use case swaps it for the
     * real package name once (this table has no Context and must stay pure data).
     */
    const val OWN_APP_TOKEN = "__self__"

    // Gadget target keys are duplicated from LauncherGadgetRegistry, which lives in src/launcherEnabled
    // and therefore cannot be imported here. Kept in sync by LauncherStarterSetsParityTest (testStandard),
    // which fails if these drift from the registry's KEY_* consts or the gadgets' default spans.
    private const val GADGET_CLOCK = "clock"
    private const val GADGET_PLAYLIST = "playlist"
    private const val GADGET_STREAMS = "streams"
    private const val GADGET_FOLDER_PREVIEW = "folder_preview"

    private const val SPAN_WIDE = 2

    // S1094: the clock seeds big (its resize floor stays 2x1, declared on the gadget itself).
    private const val CLOCK_SEED_W = 4
    private const val CLOCK_SEED_H = 2

    // Wide stride so packed (row, col) keys never collide across rows for any realistic column count.
    private const val KEY_STRIDE = 100_000L

    data class StarterItem(
        val kind: LauncherCellKind,
        val target: String,
        val spanW: Int = 1,
        val spanH: Int = 1,
    )

    data class PlacedStarterItem(
        val item: StarterItem,
        val rowIndex: Int,
        val colIndex: Int,
        val spanW: Int,
        val spanH: Int,
    ) {
        /**
         * S1428: the span to persist, which for a section is not the packed one. [place] clamps every
         * span to the grid it packs on, and that clamp must not reach the database for a header:
         * `findOverlapping` reads the stored span while the renderer widens a header to the live column
         * count, so a header stored narrow leaves the rest of its row free in the table while covering
         * it on screen.
         */
        val storedSpanW: Int
            get() = if (item.kind == LauncherCellKind.SECTION) item.spanW else spanW
    }

    /** Resolved ids the seed hands in; each null id is skipped so the desktop never gets a dead cell. */
    data class StarterResources(
        val recentId: Long? = null,
        val allAudioId: Long? = null,
        val allImagesId: Long? = null,
        val allVideoId: Long? = null,
        val allDocsId: Long? = null,
        val cameraId: Long? = null,
        val lastResourceId: Long? = null,
    )

    /**
     * The starter set for [profile]. Items whose id-dependency is null (no last resource, no all-audio
     * resource) or whose feature is unavailable (streams) are skipped, so the desktop never seeds a
     * dangling cell. Every set opens with the app-functions section over the four launcher actions,
     * continues past a second header with the clock, and closes with the common tail (favorites, Android
     * settings, this app), so even an unknown profile lands on a useful desktop.
     *
     * S1428: the second header is what ends the first section. Membership is positional and the last
     * section on the desktop has no lower bound, so a single header would own every cell below it.
     */
    fun itemsFor(
        profile: DeviceProfileType,
        resources: StarterResources,
        routeAvailableInBuild: Map<String, Boolean>,
    ): List<StarterItem> {
        val streamsAvailable = routeAvailableInBuild[InternalRouteCatalog.KEY_STREAMS] == true
        val items = mutableListOf(section(LauncherCellCommand.SECTION_APP_FUNCTIONS))
        items += launcherActions()
        items += section(LauncherCellCommand.SECTION_EVERYTHING_ELSE)
        items += clock()
        items += commonResources(resources)
        items += profileItems(profile, resources.lastResourceId, resources.allAudioId, streamsAvailable)
        items += commonFeatures(routeAvailableInBuild)
        items += commonTail()
        return items
    }

    // The unified resource set every profile opens with (owner decision S1091): one BROWSE shortcut per
    // existing virtual resource that resolved to an id. "All files" is the Recent resource (allFiles=true).
    private fun commonResources(resources: StarterResources): List<StarterItem> = buildList {
        resources.recentId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.allAudioId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.allImagesId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.allVideoId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.allDocsId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.cameraId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
    }

    // Padding feature shortcuts that fill the desktop toward the 12-15 target. Gated on build presence
    // only: a compiled-but-runtime-disabled feature keeps its cell, which routes to its own setting.
    private fun commonFeatures(routeAvailableInBuild: Map<String, Boolean>): List<StarterItem> = buildList {
        val paddingKeys = listOf(
            InternalRouteCatalog.KEY_STREAMS,
            InternalRouteCatalog.KEY_QUICK_CAMERA,
            InternalRouteCatalog.KEY_QUICK_VOICE,
            InternalRouteCatalog.KEY_CALCULATOR,
            InternalRouteCatalog.KEY_OCR,
        )
        paddingKeys.forEach { key ->
            if (routeAvailableInBuild[key] == true) add(shortcut(LauncherCellCommand.Feature(key)))
        }
    }

    // Expression `when` (not a statement): a future DeviceProfileType added without a branch is a
    // compile error here, instead of silently falling through to the clock + common tail.
    private fun profileItems(
        profile: DeviceProfileType,
        lastResourceId: Long?,
        allAudioResourceId: Long?,
        streamsAvailable: Boolean,
    ): List<StarterItem> = when (profile) {
        DeviceProfileType.PHOTO_FRAME -> buildList {
            lastResourceId?.let { add(gadget(GADGET_FOLDER_PREVIEW, it)) }
            lastResourceId?.let { add(resourceShortcut(it, LauncherResourceMode.SLIDESHOW)) }
        }
        DeviceProfileType.AUDIO_PLAYER, DeviceProfileType.CAR_HEAD_UNIT -> buildList {
            allAudioResourceId?.let { add(gadget(GADGET_PLAYLIST, it)) }
            if (streamsAvailable) add(streams())
        }
        DeviceProfileType.TV_MEDIA_BOX,
        DeviceProfileType.MEDIA_PLAYER,
        DeviceProfileType.VIDEO_PLAYER -> buildList {
            if (streamsAvailable) add(streams())
            lastResourceId?.let { add(gadget(GADGET_FOLDER_PREVIEW, it)) }
        }
        DeviceProfileType.EBOOK_READER -> buildList {
            lastResourceId?.let { add(resourceShortcut(it, LauncherResourceMode.PLAY)) }
        }
        DeviceProfileType.PERSONAL_SMARTPHONE,
        DeviceProfileType.HOME_TABLET,
        DeviceProfileType.VR_HEADSET,
        DeviceProfileType.OTHER -> emptyList() // clock + the common tail only
    }

    /**
     * Lays [items] row-major over an occupancy grid of [columns] columns: each item takes the first
     * anchor whose whole `spanW x spanH` footprint is free, so no two footprints overlap. Spans are
     * clamped to the grid width first (so `firstFreeAnchor` can never build an empty column range and
     * spin forever). Pure and unit-tested; see the class KDoc for why it is the sole overlap guarantor.
     */
    fun place(items: List<StarterItem>, columns: Int): List<PlacedStarterItem> {
        val cols = columns.coerceAtLeast(1)
        val occupied = mutableSetOf<Long>()
        return items.map { item ->
            val spanW = item.spanW.coerceIn(1, cols)
            val spanH = item.spanH.coerceAtLeast(1)
            val (row, col) = firstFreeAnchor(occupied, cols, spanW, spanH)
            for (r in row until row + spanH) {
                for (c in col until col + spanW) occupied += cellKey(r, c)
            }
            PlacedStarterItem(item, row, col, spanW, spanH)
        }
    }

    private fun clock() =
        StarterItem(LauncherCellKind.GADGET, GADGET_CLOCK, spanW = CLOCK_SEED_W, spanH = CLOCK_SEED_H)

    private fun streams() =
        StarterItem(LauncherCellKind.GADGET, GADGET_STREAMS, spanW = SPAN_WIDE, spanH = SPAN_WIDE)

    // Mirrors LauncherGadgetRegistry.encodeTarget(key, param): "<key>:<param>".
    private fun gadget(key: String, resourceId: Long) =
        StarterItem(LauncherCellKind.GADGET, "$key:$resourceId", spanW = SPAN_WIDE, spanH = SPAN_WIDE)

    private fun resourceShortcut(id: Long, mode: LauncherResourceMode) =
        shortcut(LauncherCellCommand.Resource(id, mode))

    /**
     * S1428: a header is stored at the widest grid it can ever be drawn on rather than at the one being
     * seeded - see [LauncherSectionMembership.HEADER_STORED_SPAN_W]. [place] still packs it at the seeded
     * width, because the occupancy grid is only as wide as the screen; [PlacedStarterItem.storedSpanW]
     * is what carries the full span into the entity.
     */
    private fun section(key: String) = StarterItem(
        LauncherCellKind.SECTION,
        LauncherCellCommand.Section(key).encode(),
        spanW = LauncherSectionMembership.HEADER_STORED_SPAN_W,
    )

    /**
     * S1428: the four launcher actions lead the set under their own header, reversing the ordering S1402
     * chose (strategic §3.1.1, §6.5). They move rather than duplicate, so the user is never asked to tell
     * two identical pairs apart - which is why they are gone from [commonTail].
     */
    private fun launcherActions(): List<StarterItem> =
        LauncherActionCatalog.all.map { shortcut(LauncherCellCommand.LauncherAction(it.key)) }

    /** The utilities every profile closes with, below the second header. */
    private fun commonTail(): List<StarterItem> = listOf(
        shortcut(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_FAVORITES)),
        shortcut(LauncherCellCommand.OsShortcut(OsShortcutCatalog.KEY_SETTINGS)),
        shortcut(LauncherCellCommand.App(OWN_APP_TOKEN)),
    )

    private fun shortcut(command: LauncherCellCommand) =
        StarterItem(LauncherCellKind.SHORTCUT, command.encode())

    private fun firstFreeAnchor(occupied: Set<Long>, cols: Int, spanW: Int, spanH: Int): Pair<Int, Int> {
        var row = 0
        while (true) {
            for (col in 0..(cols - spanW)) {
                if (fits(occupied, row, col, spanW, spanH)) return row to col
            }
            row++
        }
    }

    private fun fits(occupied: Set<Long>, row: Int, col: Int, spanW: Int, spanH: Int): Boolean {
        for (r in row until row + spanH) {
            for (c in col until col + spanW) {
                if (cellKey(r, c) in occupied) return false
            }
        }
        return true
    }

    private fun cellKey(row: Int, col: Int): Long = row.toLong() * KEY_STRIDE + col
}
