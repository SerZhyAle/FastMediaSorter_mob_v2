package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.core.launcher.LauncherStarterLayoutRules.StarterSectionGroup
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

    // S1747: the compass replaced altitude and satellites in the seed. Both gadgets still exist and stay
    // addable by hand - the owner's objection was that a bare satellite count says nothing to a user and
    // that altitude is a thing one adds deliberately, not something a fresh desktop should decide for them.
    private const val GADGET_COMPASS = "compass"
    private const val GADGET_AUDIO_NOW_PLAYING = "audio_now_playing"

    // S1566: same duplication contract again - the web search field every profile opens with.
    private const val GADGET_SEARCH = "search"

    // S1886: the S1754 media-window family, one window per profile. These are the closest keys the
    // launcher can actually render to the system-desktop widgets the request named - the launcher hosts
    // gadgets, not AppWidgets, so RandomPhotoFrame and its siblings are unreachable from a seed.
    private const val GADGET_MEDIA_IMAGE_WINDOW = "media_image_window"
    private const val GADGET_MEDIA_AUDIO_WINDOW = "media_audio_window"
    private const val GADGET_MEDIA_VIDEO_WINDOW = "media_video_window"
    private const val GADGET_MEDIA_DOCUMENT_WINDOW = "media_document_window"

    // S1886: the headset seeds charge rather than a media window - a window decides nothing there.
    private const val GADGET_BATTERY = "battery"

    // S2241: Google Maps interactive live frame gadget
    private const val GADGET_GOOGLE_MAPS_LIVE = "google_maps_live"

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
        GADGET_COMPASS,
        GADGET_AUDIO_NOW_PLAYING,
        GADGET_SEARCH,
        GADGET_MEDIA_IMAGE_WINDOW,
        GADGET_MEDIA_AUDIO_WINDOW,
        GADGET_MEDIA_VIDEO_WINDOW,
        GADGET_MEDIA_DOCUMENT_WINDOW,
        GADGET_BATTERY,
        GADGET_GOOGLE_MAPS_LIVE,
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

    // S1644: the Google applications the conditional GOOGLE section may hold, in the owner's order
    // (strategic §6.1). Every name was read off a GMS device rather than recalled, because a wrong
    // package name is indistinguishable from an uninstalled app and silently yields no cell.
    private const val PACKAGE_PLAY_STORE = "com.android.vending"
    private const val PACKAGE_CHROME = "com.android.chrome"
    private const val PACKAGE_GMAIL = "com.google.android.gm"
    private const val PACKAGE_GOOGLE_SEARCH = "com.google.android.googlequicksearchbox"
    private const val PACKAGE_PHOTOS = "com.google.android.apps.photos"
    private const val PACKAGE_DRIVE = "com.google.android.apps.docs"
    private const val PACKAGE_CALENDAR = "com.google.android.calendar"

    /**
     * S1644: the closed, ordered catalogue of strategic ADR-3 - membership is decided by this list and
     * the package being installed, never by a popularity heuristic, a store rating or a category.
     */
    val GOOGLE_APP_PACKAGES: List<String> = listOf(
        PACKAGE_PLAY_STORE,
        PACKAGE_CHROME,
        PACKAGE_GMAIL,
        PACKAGE_GOOGLE_SEARCH,
        PACKAGE_PHOTOS,
        PACKAGE_DRIVE,
        PACKAGE_MAPS,
        PACKAGE_YOUTUBE,
        PACKAGE_YOUTUBE_MUSIC,
        PACKAGE_CALENDAR,
    )

    private val DIALER_CANDIDATES = listOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
    )

    /** Everything the seed must ask the package manager about, in one set. */
    val candidatePackages: Set<String> =
        setOf(PACKAGE_YOUTUBE, PACKAGE_YOUTUBE_MUSIC, PACKAGE_MAPS, PACKAGE_CHROME) +
            FM_RADIO_CANDIDATES +
            DIALER_CANDIDATES +
            GOOGLE_APP_PACKAGES

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

    /** S1747: the profiles a fresh desktop seeds the compass on - the one location tile that survived. */
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
        val screenIndex: Int = 0,
    )

    data class PlacedStarterItem(
        val item: StarterItem,
        val rowIndex: Int,
        val colIndex: Int,
        val spanW: Int,
        val spanH: Int,
        val screenIndex: Int = 0,
    )

    /** Resolved ids the seed hands in; each null id is skipped so the desktop never gets a dead cell. */
    data class StarterResources(
        val recentId: Long? = null,
        val allAudioId: Long? = null,
        val allImagesId: Long? = null,
        val allVideoId: Long? = null,
        val allDocsId: Long? = null,
        val cameraId: Long? = null,
        // S2321: the predefined "All files" resource, named separately from [userResourceIds] because it
        // sits at a real storage path rather than a virtual one and so is indistinguishable from a user
        // resource by path alone - which is how it ended up in the budgeted tail and off every phone.
        val allFilesId: Long? = null,
        val lastResourceId: Long? = null,
        val userResourceIds: List<Long> = emptyList(),
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
     *
     * S1613: [importedShortcuts] land at the tail of the content section, never inside the app-functions
     * section.
     *
     * S1644: a repeated target is placed, not dropped. This used to discard an imported shortcut whose
     * target the starter set had already placed, which was the only target-uniqueness check anywhere in
     * the seed; the owner ruled that uniqueness belongs to the grid rectangle alone, so one application
     * may hold as many cells as it has free positions. [place] is what still keeps two cells apart.
     *
     * S2015: that allowance is for the *user's* hand and for an imported shortcut, not for the seed's own
     * two app sections. The Google section resolves first and owns every package it takes; the
     * Android-apps section is then built from candidates with that set subtracted, so a fresh desktop
     * never shows one icon twice in two adjacent sections (strategic ADR-1). Subtracting the actually
     * seeded set rather than the whole catalogue is what keeps Chrome, YouTube and Maps on a device with
     * no Play Services, where the Google section is not seeded at all.
     *
     * S2385: the profile's signature cell is a third owner under that same contract, and it resolves
     * ahead of both app sections - see [profileSignatureOrNull].
     *
     * S2015: [thirdPartyApps] are the device's own installed applications, resolved by the caller
     * (`QueryThirdPartyAppsUseCase`) because this table has no Context and must stay pure data. They
     * close the Android-apps section, which otherwise holds only Wi-Fi, Bluetooth and a dialer once the
     * Google catalogue is subtracted out of it.
     */
    @Suppress("LongParameterList") // one data table, and every argument is a distinct seed input
    fun itemsFor(
        profile: DeviceProfileType,
        resources: StarterResources,
        routeLaunchable: Map<String, Boolean>,
        installedPackages: Set<String>,
        googleServicesAvailable: Boolean = false,
        importedShortcuts: List<StarterItem> = emptyList(),
        thirdPartyApps: List<String> = emptyList(),
        screenClass: LauncherScreenClass,
    ): List<StarterItem> {
        val rule = LauncherStarterLayoutRules.ruleFor(screenClass)
        val groups = contentGroups(
            profile = profile,
            resources = resources,
            routeLaunchable = routeLaunchable,
            installedPackages = installedPackages,
            googleServicesAvailable = googleServicesAvailable,
            importedShortcuts = importedShortcuts,
            thirdPartyApps = thirdPartyApps,
        )
        return unsectionedTop(profile) + emitGroups(groups, rule)
    }

    /**
     * The unsectioned head of every desktop, ahead of the first header and always on screen 0.
     *
     * It is not a group and so never reorders: section membership is positional, so anything placed
     * above the first header belongs to no section and stays where the eye lands first.
     */
    private fun unsectionedTop(profile: DeviceProfileType): List<StarterItem> = buildList {
        add(clock())
        add(gadget(GADGET_SEARCH))
        weatherOrNull(profile)?.let(::add)
    }

    /**
     * Every content group the profile earns, unordered and unbudgeted - [emitGroups] applies the rule.
     *
     * Splitting composition from ordering is what lets S2309 vary the order per device without a second
     * copy of the table: a group is built once here and placed once there.
     */
    @Suppress("LongParameterList") // the same seed inputs itemsFor takes, forwarded whole
    private fun contentGroups(
        profile: DeviceProfileType,
        resources: StarterResources,
        routeLaunchable: Map<String, Boolean>,
        installedPackages: Set<String>,
        googleServicesAvailable: Boolean,
        importedShortcuts: List<StarterItem>,
        thirdPartyApps: List<String>,
    ): Map<StarterSectionGroup, List<StarterItem>> {
        val streamsAvailable = routeLaunchable[InternalRouteCatalog.KEY_STREAMS] == true

        // S2385: the profile group is resolved first because its signature cell may be an application,
        // and every later owner of that package is defined by what the earlier ones already took.
        val profileGroup = profileGadgetGroup(profile, resources, streamsAvailable, installedPackages)
        val profileOwned = profileGroup.mapNotNullTo(mutableSetOf()) { appPackageOrNull(it.target) }

        // S2015: the Google membership is resolved before the Android-apps group is built, never after,
        // because that group subtracts it (see the itemsFor KDoc and strategic ADR-1).
        val googleOwned = googleSectionPackages(googleServicesAvailable, installedPackages)

        return mapOf(
            StarterSectionGroup.PROFILE_GADGETS to profileGroup,
            StarterSectionGroup.CORE_RESOURCES to coreResources(resources),
            StarterSectionGroup.RESOURCES to userResources(resources) + importedShortcuts,
            StarterSectionGroup.APP_FUNCTIONS to commonFeatures(routeLaunchable),
            StarterSectionGroup.LAUNCHER_ACTIONS to launcherActionGroup(profile),
            StarterSectionGroup.ANDROID_APPS to
                androidAppsSection(profile, installedPackages, profileOwned + googleOwned, thirdPartyApps),
            // S2385: the exclusion the Apps section gets is the UNION, while this group loses only what
            // the profile took. Handing the Apps section the shrunk set instead would drop the profile's
            // package out of the Google section and straight back into commonThirdPartyApps below it.
            StarterSectionGroup.GOOGLE_APPS to
                (googleOwned - profileOwned).map { shortcut(LauncherCellCommand.App(it)) },
            StarterSectionGroup.UTILITY_WIDGETS to utilityWidgetGroup(routeLaunchable),
            StarterSectionGroup.MEDIA_WINDOWS to mediaWindowGroup(resources),
            StarterSectionGroup.STREAMS to streamGroup(streamsAvailable),
        )
    }

    /**
     * Turns the built groups into a flat item list: the rule's order, its per-group budget and its
     * screen assignment, in that sequence.
     *
     * An empty group is dropped before anything else is decided, header included - membership is
     * positional (see [LauncherSectionMembership]), so a header with nothing under it would not merely
     * look wrong, it would swallow every cell of the section below it.
     *
     * Groups that share a screen AND a section key are emitted under a single header. A section is
     * addressed by its target and nothing else - foldedness is stored per (orientation, target) and the
     * packing pass keys its positions by target - so two headers carrying one key on one screen would
     * fold as one and overwrite each other's packed position. Two groups may legitimately map to one
     * key (widgets, resources); which screen they land on is the rule's decision, so the collision has
     * to be resolved here rather than by forbidding the mapping.
     */
    private fun emitGroups(
        groups: Map<StarterSectionGroup, List<StarterItem>>,
        rule: LauncherStarterLayoutRules.Rule,
    ): List<StarterItem> {
        val present = rule.sectionOrder.filter { groups[it]?.isNotEmpty() == true }
        val screenOf = assignScreens(present, rule)
        return present
            .groupBy { screenOf.getValue(it) to it.sectionKey }
            .flatMap { (slot, members) ->
                val (screenIndex, sectionKey) = slot
                listOf(section(sectionKey, screenIndex)) + members.flatMap { group ->
                    val budget = rule.itemBudget[group] ?: Int.MAX_VALUE
                    groups.getValue(group).take(budget).map { it.copy(screenIndex = screenIndex) }
                }
            }
    }

    /**
     * Which screen each present group lands on: the rule's leading entries stay on screen 0 and the rest
     * spread as evenly as they divide over the screens the rule allows.
     *
     * The cut is a position in [LauncherStarterLayoutRules.Rule.sectionOrder], never a position among
     * the groups that happen to be non-empty. Counting the present ones instead would let an absent
     * group promote a later one: a device with no resources and no media windows would seed the utility
     * widgets onto screen 0, which is the layout this ticket exists to stop happening by accident.
     *
     * The remainder is spread rather than piled onto screen 1, because a rule that earned a third screen
     * did so to hold whole sections - leaving them all on the second one would make the extra screen a
     * setting the user sees and a screen they never reach.
     */
    private fun assignScreens(
        present: List<StarterSectionGroup>,
        rule: LauncherStarterLayoutRules.Rule,
    ): Map<StarterSectionGroup, Int> {
        val later = present.filter { rule.sectionOrder.indexOf(it) >= rule.firstScreenSections }
        val laterScreens = (rule.screenCount - 1).coerceAtLeast(0)
        val perScreen = if (laterScreens > 0) {
            ((later.size + laterScreens - 1) / laterScreens).coerceAtLeast(1)
        } else {
            1
        }
        return present.associateWith { group ->
            val laterIndex = later.indexOf(group)
            if (laterIndex < 0 || laterScreens == 0) {
                0
            } else {
                1 + (laterIndex / perScreen).coerceAtMost(laterScreens - 1)
            }
        }
    }

    private fun profileGadgetGroup(
        profile: DeviceProfileType,
        resources: StarterResources,
        streamsAvailable: Boolean,
        installedPackages: Set<String>,
    ): List<StarterItem> = buildList {
        profileSignatureOrNull(profile, installedPackages)?.let(::add)
        addAll(profileGadgets(profile, resources, streamsAvailable))
        if (profile in LOCATION_TILE_PROFILES) {
            add(gadget(GADGET_COMPASS))
        }
        if (profile == DeviceProfileType.CAR_HEAD_UNIT) {
            add(gadget(GADGET_SPEED))
        }
        if (profile in NOW_PLAYING_PROFILES) {
            add(gadget(GADGET_AUDIO_NOW_PLAYING))
        }
        mediaWindowOrNull(profile, resources)?.let(::add)
    }

    /**
     * The launcher's own actions plus the tail every desktop closes with.
     *
     * A separate group from the feature tiles it shares a section with, so a budget can shorten those
     * without ever reaching these: a subset of "open the settings, leave launcher mode" is not a smaller
     * app-functions section, it is a desktop with no way out of the launcher.
     */
    private fun launcherActionGroup(profile: DeviceProfileType): List<StarterItem> = buildList {
        addAll(launcherActions(profile))
        addAll(commonTail())
    }

    /** S2251: the utility tiles - a game, the speed readout and the network monitor. */
    private fun utilityWidgetGroup(routeLaunchable: Map<String, Boolean>): List<StarterItem> = buildList {
        if (routeLaunchable[InternalRouteCatalog.KEY_GAME] == true) {
            add(shortcut(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_GAME)))
        }
        add(gadget(GADGET_SPEED))
        if (routeLaunchable[InternalRouteCatalog.KEY_NETWORK_MONITOR] == true) {
            add(shortcut(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_NETWORK_MONITOR)))
        }
    }

    /** S1886: the media windows, each falling back to the last resource when its own is absent. */
    private fun mediaWindowGroup(resources: StarterResources): List<StarterItem> = buildList {
        (resources.allImagesId ?: resources.lastResourceId)?.let { add(gadget(GADGET_MEDIA_IMAGE_WINDOW, it)) }
        (resources.allVideoId ?: resources.lastResourceId)?.let { add(gadget(GADGET_MEDIA_VIDEO_WINDOW, it)) }
        resources.allAudioId?.let { add(gadget(GADGET_MEDIA_AUDIO_WINDOW, it)) }
    }

    private fun streamGroup(streamsAvailable: Boolean): List<StarterItem> = if (streamsAvailable) {
        listOf(streams(), streams())
    } else {
        emptyList()
    }

    /**
     * S2015: the Android-apps section, with every package an earlier section already owns filtered out.
     * [thirdPartyApps] close it, minus anything the fixed rules above already placed - the caller's list
     * is resolved against the whole device and the starter table's own candidates may appear in it.
     *
     * S2385: [alreadySeeded] is the union of the Google membership and the profile's signature cell, so
     * the name no longer says "google" - two sections ahead of this one can own a package now.
     */
    private fun androidAppsSection(
        profile: DeviceProfileType,
        installedPackages: Set<String>,
        alreadySeeded: Set<String>,
        thirdPartyApps: List<String>,
    ): List<StarterItem> {
        val fixed = buildList {
            if (profile == DeviceProfileType.CAR_HEAD_UNIT) {
                appIfInstalled(PACKAGE_MAPS, installedPackages, alreadySeeded)?.let(::add)
                firstInstalled(FM_RADIO_CANDIDATES, installedPackages)?.let(::add)
            } else if (profile in MAPS_PROFILES) {
                appIfInstalled(PACKAGE_MAPS, installedPackages, alreadySeeded)?.let(::add)
            }
            if (profile in WIFI_PROFILES) {
                add(shortcut(LauncherCellCommand.OsShortcut(OsShortcutCatalog.KEY_WIFI)))
            }
            if (profile in BLUETOOTH_PROFILES) {
                add(shortcut(LauncherCellCommand.OsShortcut(OsShortcutCatalog.KEY_BLUETOOTH)))
            }
            addAll(commonThirdPartyApps(installedPackages, alreadySeeded))
        }
        val alreadyPlaced = fixed.mapNotNullTo(mutableSetOf()) { appPackageOrNull(it.target) }
        return fixed + thirdPartyApps
            .filterNot { it in alreadyPlaced || it in alreadySeeded }
            .map { shortcut(LauncherCellCommand.App(it)) }
    }

    /** The package inside an `app:` target, or null for any other cell kind. */
    private fun appPackageOrNull(target: String): String? =
        target.takeIf { it.startsWith(LauncherCellCommand.PREFIX_APP) }
            ?.removePrefix(LauncherCellCommand.PREFIX_APP)

    /**
     * S2015: which packages the Google section will actually seed - the catalogue narrowed to what is
     * installed, and empty when the device has no Play Services. This is the set the Android-apps section
     * subtracts, and it is deliberately the *seeded* set rather than [GOOGLE_APP_PACKAGES]: on a device
     * without services the section is not emitted, so subtracting the catalogue there would strip Chrome,
     * YouTube and Maps off the desktop entirely instead of moving them (strategic ADR-1).
     */
    private fun googleSectionPackages(
        googleServicesAvailable: Boolean,
        installedPackages: Set<String>,
    ): Set<String> = if (googleServicesAvailable) {
        GOOGLE_APP_PACKAGES.filterTo(linkedSetOf()) { it in installedPackages }
    } else {
        emptySet()
    }

    /**
     * The third party apps assigned to profiles, conditional on being installed. S2015: [excluded] names
     * the packages the Google section already took, so the same icon is never seeded into both sections.
     * When that section is not seeded the set is empty and every candidate here lands as it always did.
     */
    private fun commonThirdPartyApps(
        installedPackages: Set<String>,
        excluded: Set<String>,
    ): List<StarterItem> = buildList {
        appIfInstalled(PACKAGE_YOUTUBE, installedPackages, excluded)?.let(::add)
        appIfInstalled(PACKAGE_YOUTUBE_MUSIC, installedPackages, excluded)?.let(::add)
        appIfInstalled(PACKAGE_CHROME, installedPackages, excluded)?.let(::add)
        firstInstalled(DIALER_CANDIDATES, installedPackages)?.let(::add)
    }

    private fun appIfInstalled(
        packageName: String,
        installedPackages: Set<String>,
        excluded: Set<String> = emptySet(),
    ): StarterItem? = if (packageName in installedPackages && packageName !in excluded) {
        shortcut(LauncherCellCommand.App(packageName))
    } else {
        null
    }

    // The unified resource set every profile opens with (owner decision S1091): one BROWSE shortcut per
    // existing aggregate resource that resolved to an id. S2321: a closed set, seeded unbudgeted, because
    // each entry is the desktop's only way into a whole content type.
    private fun coreResources(resources: StarterResources): List<StarterItem> = buildList {
        resources.recentId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.allAudioId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.allImagesId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.allVideoId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.allDocsId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.cameraId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
        resources.allFilesId?.let { add(resourceShortcut(it, LauncherResourceMode.BROWSE)) }
    }

    // S2321: the open tail - whatever the user made or imported. Budgeted, because it can grow without
    // limit and a shortened list of one's own folders is a shorter list, not a missing capability.
    private fun userResources(resources: StarterResources): List<StarterItem> =
        resources.userResourceIds.map { resourceShortcut(it, LauncherResourceMode.BROWSE) }

    // S2019: every toggleable program the "Programs and scenarios" strip (MainProgramsMenuCoordinator)
    // also offers, in that strip's order - App Launch Panel (no on/off state) and VR Cinema (no static
    // InternalRouteCatalog route, needs a resource-picker dialog) are the strip's only two exclusions.
    // S2382: gated on launchability, not on build presence. A compiled-but-disabled feature used to hold
    // a cell that routed to its own setting; the reactive shortcut sync (S2330) now adds the cell the
    // moment the feature becomes launchable, so a first seed no longer has to carry it in advance.
    private fun commonFeatures(routeLaunchable: Map<String, Boolean>): List<StarterItem> = buildList {
        val paddingKeys = listOf(
            InternalRouteCatalog.KEY_STREAMS,
            InternalRouteCatalog.KEY_QUICK_CAMERA,
            InternalRouteCatalog.KEY_QUICK_VOICE,
            InternalRouteCatalog.KEY_CALCULATOR,
            InternalRouteCatalog.KEY_NETWORK_MONITOR,
            InternalRouteCatalog.KEY_OCR,
            InternalRouteCatalog.KEY_SCREEN_RECORDING,
            InternalRouteCatalog.KEY_LINK_DOWNLOAD,
            InternalRouteCatalog.KEY_GAME,
            InternalRouteCatalog.KEY_SYSTEM_INFO,
            InternalRouteCatalog.KEY_WEAR_COMPANION,
        )
        paddingKeys.forEach { key ->
            if (routeLaunchable[key] == true) add(shortcut(LauncherCellCommand.Feature(key)))
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
     * S1886: the media window a profile opens its widgets group with, or null when the device has no
     * resource to point it at. Kept beside the compass/speed/now-playing rules in [itemsFor] rather than
     * inside [profileGadgets], because this is a cross-profile row and folding nine more branches into
     * that `when` pushed it past the complexity ceiling.
     *
     * The id is not optional: every window declares `requiresResourceParam`, so a bare key seeds a cell
     * that can only ever render "unavailable". Each window also filters its files by type, which is why
     * the matching all-of-type resource is preferred over the merely last-used one.
     */
    private fun mediaWindowOrNull(profile: DeviceProfileType, resources: StarterResources): StarterItem? =
        when (profile) {
            DeviceProfileType.PHOTO_FRAME, DeviceProfileType.HOME_TABLET ->
                (resources.allImagesId ?: resources.lastResourceId)?.let { gadget(GADGET_MEDIA_IMAGE_WINDOW, it) }
            DeviceProfileType.AUDIO_PLAYER, DeviceProfileType.MEDIA_PLAYER ->
                resources.allAudioId?.let { gadget(GADGET_MEDIA_AUDIO_WINDOW, it) }
            DeviceProfileType.VIDEO_PLAYER, DeviceProfileType.TV_MEDIA_BOX ->
                (resources.allVideoId ?: resources.lastResourceId)?.let { gadget(GADGET_MEDIA_VIDEO_WINDOW, it) }
            DeviceProfileType.EBOOK_READER ->
                (resources.allDocsId ?: resources.lastResourceId)?.let { gadget(GADGET_MEDIA_DOCUMENT_WINDOW, it) }
            // The headset seeds charge instead: a media window decides nothing there, the battery does.
            DeviceProfileType.VR_HEADSET -> gadget(GADGET_BATTERY)
            DeviceProfileType.PERSONAL_SMARTPHONE,
            DeviceProfileType.CAR_HEAD_UNIT,
            DeviceProfileType.OTHER -> null
        }

    /**
     * S2385: the one cell that makes a fresh desktop recognisable as the device it belongs to, seeded at
     * the HEAD of the profile's widget group (strategic ADR-2). The group is budgeted and scales down to
     * three items on a compact screen, so a signature added to the tail would be the first thing cut.
     *
     * Exhaustive over [DeviceProfileType] like [mediaWindowOrNull] beside it: a new profile must name its
     * signature or an explicit null rather than inherit a silent default. Nine of the eleven return null
     * because [profileGadgets] already gives them a group nothing else has - the frame its photos, the
     * box its streams, the reader its documents.
     *
     * The head unit is the exception the ticket opened on: it carried a Maps *icon* in the apps section
     * while the live frame gadget shipped in the same build and was seeded nowhere. The tablet is the
     * other: its group held one image window and nothing said "tablet", so YouTube moves out of the row
     * every profile shares and into the group only this profile has.
     */
    private fun profileSignatureOrNull(
        profile: DeviceProfileType,
        installedPackages: Set<String>,
    ): StarterItem? = when (profile) {
        DeviceProfileType.CAR_HEAD_UNIT -> StarterItem(
            LauncherCellKind.GADGET,
            GADGET_GOOGLE_MAPS_LIVE,
            spanW = SPAN_WIDE,
            spanH = SPAN_WIDE,
        )
        DeviceProfileType.HOME_TABLET -> appIfInstalled(PACKAGE_YOUTUBE, installedPackages)
        DeviceProfileType.PERSONAL_SMARTPHONE,
        DeviceProfileType.AUDIO_PLAYER,
        DeviceProfileType.MEDIA_PLAYER,
        DeviceProfileType.VIDEO_PLAYER,
        DeviceProfileType.TV_MEDIA_BOX,
        DeviceProfileType.PHOTO_FRAME,
        DeviceProfileType.EBOOK_READER,
        DeviceProfileType.VR_HEADSET,
        DeviceProfileType.OTHER -> null
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
     *
     * S1642: a header opens a row nothing else reaches into - see [firstEmptyRow]. The items that follow it
     * still pack into the rest of that row, which is what puts the first shortcuts beside the header
     * instead of a line below it.
     */
    fun place(items: List<StarterItem>, columns: Int): List<PlacedStarterItem> {
        val cols = columns.coerceAtLeast(1)
        val result = mutableListOf<PlacedStarterItem>()
        val itemsByScreen = items.groupBy { it.screenIndex }
        for ((screenIndex, screenItems) in itemsByScreen) {
            val occupied = mutableSetOf<Long>()
            var sectionFloor = 0
            for (item in screenItems) {
                val spanW = item.spanW.coerceIn(1, cols)
                val spanH = item.spanH.coerceAtLeast(1)
                val isSection = item.kind == LauncherCellKind.SECTION
                val (row, col) = if (isSection) {
                    firstEmptyRow(occupied, cols, sectionFloor) to 0
                } else {
                    firstFreeAnchor(occupied, cols, spanW, spanH, sectionFloor)
                }
                for (r in row until row + spanH) {
                    for (c in col until col + spanW) occupied += cellKey(r, c)
                }
                if (isSection) sectionFloor = row
                result += PlacedStarterItem(item, row, col, spanW, spanH, screenIndex)
            }
        }
        return result
    }

    private fun clock(screenIndex: Int = 0) =
        StarterItem(
            LauncherCellKind.GADGET,
            GADGET_CLOCK,
            spanW = CLOCK_SEED_W,
            spanH = CLOCK_SEED_H,
            screenIndex = screenIndex
        )

    private fun streams(screenIndex: Int = 0) =
        StarterItem(
            LauncherCellKind.GADGET,
            GADGET_STREAMS,
            spanW = SPAN_WIDE,
            spanH = SPAN_WIDE,
            screenIndex = screenIndex
        )

    // Mirrors LauncherGadgetRegistry.encodeTarget(key, param): "<key>:<param>".
    private fun gadget(key: String, resourceId: Long, screenIndex: Int = 0) =
        StarterItem(
            LauncherCellKind.GADGET,
            "$key:$resourceId",
            spanW = SPAN_WIDE,
            spanH = SPAN_WIDE,
            screenIndex = screenIndex
        )

    // S1560: sensor gadgets (speed, altitude, satellites, weather) carry no param - the cell is just
    // the key. Default span is 2x1 to match the existing sensor-tile form factor.
    private fun gadget(key: String, screenIndex: Int = 0) =
        StarterItem(LauncherCellKind.GADGET, key, spanW = SPAN_WIDE, screenIndex = screenIndex)

    private fun resourceShortcut(id: Long, mode: LauncherResourceMode, screenIndex: Int = 0) =
        shortcut(LauncherCellCommand.Resource(id, mode), screenIndex = screenIndex)

    /**
     * S1642: a header is seeded at [LauncherSectionMembership.HEADER_SPAN_W], the one span it is stored and
     * drawn at. [place] packs it at that width unchanged - the value is below the narrowest grid the
     * desktop resolves, so the packer's clamp to the seeded width can never narrow it.
     */
    private fun section(key: String, screenIndex: Int = 0) = StarterItem(
        LauncherCellKind.SECTION,
        LauncherCellCommand.Section(key).encode(),
        spanW = LauncherSectionMembership.HEADER_SPAN_W,
        screenIndex = screenIndex,
    )

    /**
     * S1428: the launcher actions lead the set under their own header, reversing the ordering S1402
     * chose (strategic §3.1.1, §6.5). S1560: black_screen remains in this action section only for
     * [BLACK_SCREEN_PROFILES] (strategic §6.4).
     */
    private fun launcherActions(profile: DeviceProfileType, screenIndex: Int = 0): List<StarterItem> =
        LauncherActionCatalog.all
            .filter { it.key != LauncherActionCatalog.KEY_BLACK_SCREEN || profile in BLACK_SCREEN_PROFILES }
            .map { shortcut(LauncherCellCommand.LauncherAction(it.key), screenIndex = screenIndex) }

    /** The utilities every profile closes with, below the second header. */
    private fun commonTail(screenIndex: Int = 0): List<StarterItem> = listOf(
        shortcut(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_FAVORITES), screenIndex = screenIndex),
        shortcut(LauncherCellCommand.OsShortcut(OsShortcutCatalog.KEY_SETTINGS), screenIndex = screenIndex),
        shortcut(LauncherCellCommand.App(OWN_APP_TOKEN), screenIndex = screenIndex),
    )

    private fun shortcut(command: LauncherCellCommand, screenIndex: Int = 0) =
        StarterItem(LauncherCellKind.SHORTCUT, command.encode(), screenIndex = screenIndex)

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

    /**
     * The first row at or below [floor] that no cell reaches into.
     *
     * S1642: a section header takes a whole row rather than the first free rectangle in one. Narrowed to
     * two columns it would otherwise be seated beside a cell already standing there, and when that cell is
     * several rows tall it would end up straddling the boundary the header just drew - the placement
     * strategic §6.11 refuses everywhere else.
     */
    private fun firstEmptyRow(occupied: Set<Long>, cols: Int, floor: Int): Int {
        var row = floor
        while (!fits(occupied, row, 0, cols, 1)) {
            row++
        }
        return row
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
