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
 * set must not touch the surface). [SeedLauncherDesktopUseCase] resolves the ids/availability,
 * substitutes the own-app placeholder, and persists the placed cells.
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

    // S1560: the tiles the per-profile grid adds, under the same duplication contract as the four above.
    private const val GADGET_WEATHER = "weather"
    private const val GADGET_SPEED = "speed"
    private const val GADGET_ALTITUDE = "altitude"
    private const val GADGET_SATELLITES = "satellites"
    private const val GADGET_AUDIO_NOW_PLAYING = "audio_now_playing"

    // S1566: same duplication contract again - the web search field every profile opens with.
    private const val GADGET_SEARCH = "search"

    /**
     * Every gadget key this table can emit. Public because the parity test cannot reach the private
     * consts above, and a hand-written list over there is what let the previous four-key guard fall
     * behind the table it was meant to guard.
     */
    val gadgetKeys: Set<String> = setOf(
        GADGET_CLOCK,
        GADGET_PLAYLIST,
        GADGET_STREAMS,
        GADGET_FOLDER_PREVIEW,
        GADGET_WEATHER,
        GADGET_SPEED,
        GADGET_ALTITUDE,
        GADGET_SATELLITES,
        GADGET_AUDIO_NOW_PLAYING,
        GADGET_SEARCH,
    )

    // S1560: third-party targets this table may seed. A cell is placed only when its package is present,
    // so an absent app leaves no icon behind rather than a dead one (strategic §5.1.3).
    const val PACKAGE_YOUTUBE = "com.google.android.youtube"
    const val PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    const val PACKAGE_MAPS = "com.google.android.apps.maps"

    /**
     * Head-unit FM applications, most common first: there is no single FM package, each vendor ships its
     * own, so the first installed one wins and an unknown vendor simply yields no cell (strategic §6.2).
     */
    private val FM_RADIO_CANDIDATES = listOf(
        "com.android.fmradio",
        "com.caf.fmradio",
        "com.miui.fmradio",
        "com.sec.android.app.fm",
        "com.motorola.fmplayer",
        "com.lge.fmradio",
    )

    /** Everything the seed must ask the package manager about, in one set. */
    val candidatePackages: Set<String> =
        setOf(PACKAGE_YOUTUBE, PACKAGE_YOUTUBE_MUSIC, PACKAGE_MAPS) + FM_RADIO_CANDIDATES

    // S1560: the rows of the approved grid that cut across profiles, one set each. Membership tests
    // rather than eleven near-identical `when` branches: the owner reads the grid row-wise, and a row
    // that lives in one place cannot disagree with itself.
    private val WIFI_PROFILES = setOf(
        DeviceProfileType.CAR_HEAD_UNIT,
        DeviceProfileType.AUDIO_PLAYER,
        DeviceProfileType.TV_MEDIA_BOX,
        DeviceProfileType.MEDIA_PLAYER,
        DeviceProfileType.VIDEO_PLAYER,
        DeviceProfileType.PHOTO_FRAME,
        DeviceProfileType.VR_HEADSET,
        DeviceProfileType.OTHER,
    )

    private val BLUETOOTH_PROFILES = setOf(
        DeviceProfileType.CAR_HEAD_UNIT,
        DeviceProfileType.AUDIO_PLAYER,
        DeviceProfileType.TV_MEDIA_BOX,
        DeviceProfileType.MEDIA_PLAYER,
        DeviceProfileType.VIDEO_PLAYER,
        DeviceProfileType.VR_HEADSET,
        DeviceProfileType.OTHER,
    )

    private val NOW_PLAYING_PROFILES = setOf(
        DeviceProfileType.CAR_HEAD_UNIT,
        DeviceProfileType.AUDIO_PLAYER,
        DeviceProfileType.TV_MEDIA_BOX,
        DeviceProfileType.MEDIA_PLAYER,
        DeviceProfileType.VIDEO_PLAYER,
    )

    /** Strategic §6.4: the always-on devices, where no power button is within reach of the screen. */
    private val BLACK_SCREEN_PROFILES = setOf(
        DeviceProfileType.CAR_HEAD_UNIT,
        DeviceProfileType.AUDIO_PLAYER,
        DeviceProfileType.TV_MEDIA_BOX,
        DeviceProfileType.PHOTO_FRAME,
    )

    /** Altitude and satellites travel together - both read the same location fix. */
    private val LOCATION_TILE_PROFILES = setOf(
        DeviceProfileType.CAR_HEAD_UNIT,
        DeviceProfileType.PERSONAL_SMARTPHONE,
    )

    private val MAPS_PROFILES = setOf(
        DeviceProfileType.CAR_HEAD_UNIT,
        DeviceProfileType.PERSONAL_SMARTPHONE,
    )

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
     * dangling cell. Every set opens with the everything-else section over the clock, the search and the
     * content the profile earns, and closes with the app-functions section over the launcher actions and
     * the common tail (favorites, Android settings, this app), so even an unknown profile lands on a
     * useful desktop.
     *
     * S1587: content leads and the launcher's own actions trail, because the first screen of a phone is
     * otherwise spent on five service shortcuts while the media resources fall below the fold (owner
     * ruling, strategic §3.3). The actions stay reachable from the Start menu, which is what makes the
     * move safe.
     *
     * S1428: the second header is what ends the first section. Membership is positional and the last
     * section on the desktop has no lower bound, so a single header would own every cell below it.
     */
    fun itemsFor(
        profile: DeviceProfileType,
        resources: StarterResources,
        routeAvailableInBuild: Map<String, Boolean>,
        installedPackages: Set<String>,
    ): List<StarterItem> {
        val streamsAvailable = routeAvailableInBuild[InternalRouteCatalog.KEY_STREAMS] == true
        val items = mutableListOf(section(LauncherCellCommand.SECTION_EVERYTHING_ELSE))
        items += clock()
        // S1566: directly after the clock, never before it - LauncherStarterSetsParityTest reads the first
        // GADGET item of an OTHER-profile set and expects the clock.
        items += gadget(GADGET_SEARCH)
        weatherOrNull(profile)?.let { items += it }
        items += commonResources(resources)
        items += profileItems(profile, resources, streamsAvailable, installedPackages)
        items += commonFeatures(routeAvailableInBuild)
        items += commonThirdPartyApps(installedPackages)
        items += section(LauncherCellCommand.SECTION_APP_FUNCTIONS)
        items += launcherActions(profile)
        items += commonTail()
        return items
    }

    /** The two media apps the owner assigned to every profile, each conditional on being installed. */
    private fun commonThirdPartyApps(installedPackages: Set<String>): List<StarterItem> = buildList {
        appIfInstalled(PACKAGE_YOUTUBE, installedPackages)?.let(::add)
        appIfInstalled(PACKAGE_YOUTUBE_MUSIC, installedPackages)?.let(::add)
    }

    private fun appIfInstalled(packageName: String, installedPackages: Set<String>): StarterItem? =
        if (packageName in installedPackages) shortcut(LauncherCellCommand.App(packageName)) else null

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
            InternalRouteCatalog.KEY_NETWORK_MONITOR,
            InternalRouteCatalog.KEY_OCR,
        )
        paddingKeys.forEach { key ->
            if (routeAvailableInBuild[key] == true) add(shortcut(LauncherCellCommand.Feature(key)))
        }
    }

    // Expression `when` (not a statement): a future DeviceProfileType added without a branch is a
    // compile error here, instead of silently falling through to the clock + common tail.
    // S1560 Phase 04: cross-profile rows expressed as membership sets, plus the existing per-profile
    // gadget/resource items. The `when` stays exhaustive so a new profile is a compile error.
    private fun profileItems(
        profile: DeviceProfileType,
        resources: StarterResources,
        streamsAvailable: Boolean,
        installedPackages: Set<String>,
    ): List<StarterItem> = buildList {
        // Per-profile gadgets/resources (the original `when` branches).
        addAll(profileGadgets(profile, resources, streamsAvailable))
        // Cross-profile rows driven by membership sets (S1560 Phase 04 grid).
        if (profile in LOCATION_TILE_PROFILES) {
            add(gadget(GADGET_ALTITUDE))
            add(gadget(GADGET_SATELLITES))
        }
        if (profile == DeviceProfileType.CAR_HEAD_UNIT) {
            add(gadget(GADGET_SPEED))
            appIfInstalled(PACKAGE_MAPS, installedPackages)?.let(::add)
            firstInstalled(FM_RADIO_CANDIDATES, installedPackages)?.let(::add)
        }
        if (profile in MAPS_PROFILES && profile != DeviceProfileType.CAR_HEAD_UNIT) {
            appIfInstalled(PACKAGE_MAPS, installedPackages)?.let(::add)
        }
        if (profile in WIFI_PROFILES) {
            add(shortcut(LauncherCellCommand.OsShortcut(OsShortcutCatalog.KEY_WIFI)))
        }
        if (profile in BLUETOOTH_PROFILES) {
            add(shortcut(LauncherCellCommand.OsShortcut(OsShortcutCatalog.KEY_BLUETOOTH)))
        }
        if (profile in NOW_PLAYING_PROFILES) {
            add(gadget(GADGET_AUDIO_NOW_PLAYING))
        }
    }

    /**
     * The original per-profile gadget/resource items that were already in the table before S1560.
     * Exhaustive over [DeviceProfileType] so a new profile is a compile error.
     */
    private fun profileGadgets(
        profile: DeviceProfileType,
        resources: StarterResources,
        streamsAvailable: Boolean,
    ): List<StarterItem> = when (profile) {
        DeviceProfileType.PHOTO_FRAME -> buildList {
            resources.lastResourceId?.let { add(gadget(GADGET_FOLDER_PREVIEW, it)) }
            resources.lastResourceId?.let { add(resourceShortcut(it, LauncherResourceMode.SLIDESHOW)) }
        }
        DeviceProfileType.AUDIO_PLAYER, DeviceProfileType.CAR_HEAD_UNIT -> buildList {
            resources.allAudioId?.let { add(gadget(GADGET_PLAYLIST, it)) }
            if (streamsAvailable) add(streams())
        }
        DeviceProfileType.TV_MEDIA_BOX,
        DeviceProfileType.MEDIA_PLAYER,
        DeviceProfileType.VIDEO_PLAYER -> buildList {
            if (streamsAvailable) add(streams())
            resources.lastResourceId?.let { add(gadget(GADGET_FOLDER_PREVIEW, it)) }
        }
        DeviceProfileType.EBOOK_READER -> buildList {
            resources.lastResourceId?.let { add(resourceShortcut(it, LauncherResourceMode.PLAY)) }
        }
        DeviceProfileType.PERSONAL_SMARTPHONE,
        DeviceProfileType.HOME_TABLET,
        DeviceProfileType.VR_HEADSET,
        DeviceProfileType.OTHER -> emptyList()
    }

    /**
     * Lays [items] row-major over an occupancy grid of [columns] columns: each item takes the first
     * anchor whose whole `spanW x spanH` footprint is free, so no two footprints overlap. Spans are
     * clamped to the grid width first (so `firstFreeAnchor` can never build an empty column range and
     * spin forever). Pure and unit-tested; see the class KDoc for why it is the sole overlap guarantor.
     *
     * S1587: a section header raises the packing floor to its own row, so nothing placed after it can
     * anchor above it. Without the floor the scan restarts at row 0 for every item and backfills the
     * gap a shorter group left behind - and since membership is positional (see
     * [LauncherSectionMembership][com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership]),
     * an item that lands above its own header belongs to the section before it and collapses with it.
     */
    fun place(items: List<StarterItem>, columns: Int): List<PlacedStarterItem> {
        val cols = columns.coerceAtLeast(1)
        val occupied = mutableSetOf<Long>()
        var sectionFloor = 0
        return items.map { item ->
            val spanW = item.spanW.coerceIn(1, cols)
            val spanH = item.spanH.coerceAtLeast(1)
            val (row, col) = firstFreeAnchor(occupied, cols, spanW, spanH, sectionFloor)
            for (r in row until row + spanH) {
                for (c in col until col + spanW) occupied += cellKey(r, c)
            }
            if (item.kind == LauncherCellKind.SECTION) sectionFloor = row
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

    // S1560: sensor gadgets (speed, altitude, satellites, weather) carry no param - the cell is just
    // the key. Default span is 2x1 to match the existing sensor-tile form factor.
    private fun gadget(key: String) =
        StarterItem(LauncherCellKind.GADGET, key, spanW = SPAN_WIDE)

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
     * S1428: the launcher actions lead the set under their own header, reversing the ordering S1402
     * chose (strategic §3.1.1, §6.5). S1560: black_screen remains in this action section only for
     * [BLACK_SCREEN_PROFILES] (strategic §6.4).
     */
    private fun launcherActions(profile: DeviceProfileType): List<StarterItem> =
        LauncherActionCatalog.all
            .filter { it.key != LauncherActionCatalog.KEY_BLACK_SCREEN || profile in BLACK_SCREEN_PROFILES }
            .map { shortcut(LauncherCellCommand.LauncherAction(it.key)) }

    /** The utilities every profile closes with, below the second header. */
    private fun commonTail(): List<StarterItem> = listOf(
        shortcut(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_FAVORITES)),
        shortcut(LauncherCellCommand.OsShortcut(OsShortcutCatalog.KEY_SETTINGS)),
        shortcut(LauncherCellCommand.App(OWN_APP_TOKEN)),
    )

    private fun shortcut(command: LauncherCellCommand) =
        StarterItem(LauncherCellKind.SHORTCUT, command.encode())

    /**
     * S1560 Phase 04 step 04.2: the weather gadget is seeded to every profile except [AUDIO_PLAYER]
     * (owner ruling strategic §6.1). No param - the cell carries no location until the owner picks one.
     */
    private fun weatherOrNull(profile: DeviceProfileType): StarterItem? =
        if (profile != DeviceProfileType.AUDIO_PLAYER) gadget(GADGET_WEATHER) else null

    /** Returns a shortcut for the first package from [candidates] that is installed, or null. */
    private fun firstInstalled(
        candidates: List<String>,
        installedPackages: Set<String>,
    ): StarterItem? = candidates.firstOrNull { it in installedPackages }
        ?.let { shortcut(LauncherCellCommand.App(it)) }

    private fun firstFreeAnchor(
        occupied: Set<Long>,
        cols: Int,
        spanW: Int,
        spanH: Int,
        floor: Int,
    ): Pair<Int, Int> {
        var row = floor
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
