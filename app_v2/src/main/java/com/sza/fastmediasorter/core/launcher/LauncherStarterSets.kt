package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode

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
    )

    /**
     * The starter set for [profile]. Items whose id-dependency is null (no last resource, no all-audio
     * resource) or whose feature is unavailable (streams) are skipped, so the desktop never seeds a
     * dangling cell. Every set opens with a clock and closes with the common tail (favorites, Android
     * settings, this app), so even an unknown profile lands on a useful desktop.
     */
    fun itemsFor(
        profile: DeviceProfileType,
        lastResourceId: Long?,
        allAudioResourceId: Long?,
        streamsAvailable: Boolean,
    ): List<StarterItem> {
        val items = mutableListOf(clock())
        items += profileItems(profile, lastResourceId, allAudioResourceId, streamsAvailable)
        items += commonTail()
        return items
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

    private fun clock() = StarterItem(LauncherCellKind.GADGET, GADGET_CLOCK, spanW = SPAN_WIDE, spanH = 1)

    private fun streams() =
        StarterItem(LauncherCellKind.GADGET, GADGET_STREAMS, spanW = SPAN_WIDE, spanH = SPAN_WIDE)

    // Mirrors LauncherGadgetRegistry.encodeTarget(key, param): "<key>:<param>".
    private fun gadget(key: String, resourceId: Long) =
        StarterItem(LauncherCellKind.GADGET, "$key:$resourceId", spanW = SPAN_WIDE, spanH = SPAN_WIDE)

    private fun resourceShortcut(id: Long, mode: LauncherResourceMode) =
        shortcut(LauncherCellCommand.Resource(id, mode))

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
