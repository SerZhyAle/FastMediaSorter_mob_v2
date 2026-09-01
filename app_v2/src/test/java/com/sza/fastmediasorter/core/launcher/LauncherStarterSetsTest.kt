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
@Suppress("FunctionNaming", "LargeClass") // backtick test names, project convention (cf. LauncherGridGeometryTest)
class LauncherStarterSetsTest {

    /**
     * S2309: the screen class these cases compose for unless they are about the class itself.
     *
     * Medium and wide is the pair the pre-S2309 hardcoded layout was written against, so a case
     * that predates the second axis keeps asserting the desktop it was written to assert.
     */
    private val mediumWide =
        LauncherScreenClass(LauncherScreenClass.Size.MEDIUM, LauncherScreenClass.Shape.WIDE)

    private val allPaddingAvailable = mapOf(
        InternalRouteCatalog.KEY_STREAMS to true,
        InternalRouteCatalog.KEY_QUICK_CAMERA to true,
        InternalRouteCatalog.KEY_QUICK_VOICE to true,
        InternalRouteCatalog.KEY_CALCULATOR to true,
        InternalRouteCatalog.KEY_NETWORK_MONITOR to true,
        InternalRouteCatalog.KEY_OCR to true,
        InternalRouteCatalog.KEY_SCREEN_RECORDING to true,
        InternalRouteCatalog.KEY_LINK_DOWNLOAD to true,
        InternalRouteCatalog.KEY_GAME to true,
        InternalRouteCatalog.KEY_SYSTEM_INFO to true,
        InternalRouteCatalog.KEY_WEAR_COMPANION to true,
    )

    /** The utilities every profile closes with, below the second header. */
    private val commonTail = listOf("fn:favorites", "os:settings", "app:__self__")

    /** The launcher actions a profile seeds, in catalogue order. */
    private fun actionTargets(profile: DeviceProfileType): List<String> =
        LauncherActionCatalog.all
            .filter { it.key != LauncherActionCatalog.KEY_BLACK_SCREEN || profile in BLACK_SCREEN_PROFILES }
            .map { "act:${it.key}" }

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

    // ── itemsFor ────────

    @Test
    fun `every set opens with top unsectioned items and closes with the launcher actions`() {
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.OTHER,
            StarterResources(),
            emptyMap(),
            emptySet(),
            screenClass = mediumWide,
        )
            .filter { it.screenIndex == 0 }
        assertEquals(
            listOf("clock", "search", "weather", "sec:app_functions") +
                actionTargets(DeviceProfileType.OTHER) +
                commonTail +
                listOf("sec:android_apps", "os:wifi", "os:bluetooth"),
            items.map { it.target },
        )
        assertEquals(LauncherCellKind.GADGET, items.first().kind)
    }

    @Test
    fun `every launcher action sits under the app-functions header and is seeded once`() {
        val targets = LauncherStarterSets
            .itemsFor(
                DeviceProfileType.PERSONAL_SMARTPHONE,
                StarterResources(recentId = 1),
                allPaddingAvailable,
                emptySet(),
                screenClass = mediumWide,
            )
            .filter { it.screenIndex == 0 }
            .map { it.target }
        val widgetsHeaderIndex = targets.indexOf("sec:widgets")
        val actionsHeaderIndex = targets.indexOf("sec:app_functions")
        val actions = targets.filter { it.startsWith("act:") }
        assertEquals(LauncherActionCatalog.all.size - 1, actions.size)
        assertTrue("widgets header must come first", widgetsHeaderIndex < actionsHeaderIndex)
        val actionsStart = actionsHeaderIndex + allPaddingAvailable.size + 1
        assertEquals(actions, targets.subList(actionsStart, actionsStart + actions.size))
    }

    @Test
    fun `no gadget is seeded inside the app-functions section`() {
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            mapOf(InternalRouteCatalog.KEY_STREAMS to true),
            emptySet(),
            screenClass = mediumWide,
        ).filter { it.screenIndex == 0 }
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
            screenClass = mediumWide,
        ).filter { it.screenIndex == 0 }
        assertEquals(
            listOf(
                "clock", "search", "weather",
                "sec:widgets", "compass",
                "sec:resources",
                "res:1:BROWSE", "res:2:BROWSE", "res:3:BROWSE",
                "res:4:BROWSE", "res:5:BROWSE", "res:6:BROWSE",
                // S1913: listed rather than folded into sectionTail(), which has no padding cells by
                // construction. This assertion is named "and padding set" and is called with
                // allPaddingAvailable, so commonFeatures emits every key - the helper silently dropped
                // them when the flat tail was refactored away, which is what made this test red.
                "sec:app_functions",
                "fn:streams", "fn:quick_camera", "fn:quick_voice",
                "fn:calculator", "fn:network_monitor", "fn:ocr",
                // S2019: the five programs the seed used to leave out of the App-functions section.
                "fn:screen_recording", "fn:link_download", "fn:game", "fn:system_info", "fn:wear_companion",
            ) + actionTargets(DeviceProfileType.PERSONAL_SMARTPHONE) + commonTail,
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
            screenClass = mediumWide,
        ).filter { it.screenIndex == 0 }
        assertEquals(
            listOf(
                "clock", "search", "weather",
                "sec:widgets", "compass",
                "sec:resources", "res:1:BROWSE",
                "sec:app_functions", "fn:calculator",
            ) +
                actionTargets(DeviceProfileType.PERSONAL_SMARTPHONE) +
                commonTail,
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
            screenClass = mediumWide,
        ).filter { it.screenIndex == 0 }
        assertEquals(
            listOf(
                "clock", "search", "weather",
                // S1913: no sec:resources header here. profileGadgets seeds the PHOTO_FRAME
                // slideshow shortcut into the widgets bucket beside its folder-preview gadget, the
                // way EBOOK_READER seeds its PLAY shortcut, while commonResources is scoped to one
                // BROWSE shortcut per virtual resource and never reads lastResourceId. resItems is
                // therefore empty, and an empty section must not print a header - a header with
                // nothing under it swallows the section below it, section membership being positional.
                "sec:widgets", "folder_preview:5", "res:5:SLIDESHOW", "media_image_window:5",
                "sec:app_functions",
            ) +
                actionTargets(DeviceProfileType.PHOTO_FRAME) +
                commonTail +
                listOf("sec:android_apps", "os:wifi"),
            items.map { it.target },
        )
    }

    @Test
    fun `null id dependencies are skipped, never seeded as dangling cells`() {
        val items = LauncherStarterSets
            .itemsFor(
                DeviceProfileType.PHOTO_FRAME,
                StarterResources(),
                emptyMap(),
                emptySet(),
                screenClass = mediumWide,
            )
            .filter { it.screenIndex == 0 }
        assertEquals(
            listOf(
                "clock", "search", "weather",
                "sec:app_functions",
            ) +
                actionTargets(DeviceProfileType.PHOTO_FRAME) +
                commonTail +
                listOf("sec:android_apps", "os:wifi"),
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
            screenClass = mediumWide,
        ).filter { it.screenIndex == 0 }
        assertEquals(
            listOf(
                "clock", "search",
                "sec:widgets", "playlist:7", "streams", "audio_now_playing", "media_audio_window:7",
                "sec:resources", "res:7:BROWSE",
                "sec:app_functions", "fn:streams",
            ) +
                actionTargets(DeviceProfileType.AUDIO_PLAYER) +
                commonTail +
                listOf("sec:android_apps", "os:wifi", "os:bluetooth"),
            withStreams.map { it.target },
        )
        val withoutStreams = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            emptyMap(),
            emptySet(),
            screenClass = mediumWide,
        )
        assertFalse(withoutStreams.any { it.target == "streams" })
        assertFalse(withoutStreams.any { it.target == "fn:streams" })
    }

    @Test
    fun `no third-party app cell is seeded when nothing is installed`() {
        DeviceProfileType.entries.forEach { profile ->
            val targets = LauncherStarterSets
                .itemsFor(profile, StarterResources(), allPaddingAvailable, emptySet(), screenClass = mediumWide)
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
            screenClass = mediumWide,
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
            screenClass = mediumWide,
        ).filter { it.screenIndex == 0 }.map { it.target }.toSet()
        val smartphone = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(),
            allPaddingAvailable,
            installed,
            screenClass = mediumWide,
        ).filter { it.screenIndex == 0 }.map { it.target }.toSet()

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
                .itemsFor(profile, StarterResources(), allPaddingAvailable, installed, screenClass = mediumWide)
                .filter { it.screenIndex == 0 }
                .map { it.target }.toSet()
            assertEquals("$profile weather", profile != DeviceProfileType.AUDIO_PLAYER, "weather" in targets)
            assertEquals("$profile Wi-Fi", expected.wifi, "os:wifi" in targets)
            assertEquals("$profile Bluetooth", expected.bluetooth, "os:bluetooth" in targets)
            assertEquals("$profile now-playing", expected.nowPlaying, "audio_now_playing" in targets)
            assertEquals("$profile black screen", expected.blackScreen, "act:black_screen" in targets)
            // S1747: the compass is the only location tile a fresh desktop seeds; altitude and
            // satellites stay addable by hand and must never come back on their own.
            assertEquals("$profile compass", expected.locationTiles, "compass" in targets)
            assertFalse("$profile altitude", "altitude" in targets)
            assertFalse("$profile satellites", "satellites" in targets)
            assertEquals("$profile speed", profile == DeviceProfileType.CAR_HEAD_UNIT, "speed" in targets)
            assertEquals("$profile maps", expected.maps, "app:${LauncherStarterSets.PACKAGE_MAPS}" in targets)
            assertEquals("$profile FM radio", expected.fmRadio, "app:$FM_RADIO_PACKAGE" in targets)
            assertTrue("$profile all apps", "act:all_apps" in targets)
        }
    }

    @Test
    fun `every profile opens a widgets section and the tablet and reader fill it differently`() {
        // S1886: HOME_TABLET and VR_HEADSET seeded no widgets section at all before this ticket, so the
        // group the device profile is supposed to describe never reached the desktop.
        assertTrue("tablet widgets", widgetTargets(DeviceProfileType.HOME_TABLET).isNotEmpty())
        assertTrue("headset widgets", widgetTargets(DeviceProfileType.VR_HEADSET).isNotEmpty())
        assertNotEquals(
            widgetTargets(DeviceProfileType.HOME_TABLET),
            widgetTargets(DeviceProfileType.EBOOK_READER),
        )
    }

    /** Targets between the widgets header and the next section header; empty when the group is absent. */
    private fun widgetTargets(profile: DeviceProfileType): List<String> {
        val res = StarterResources(allImagesId = 9, allDocsId = 9, lastResourceId = 9)
        val targets = LauncherStarterSets.itemsFor(
            profile,
            res,
            emptyMap(),
            emptySet(),
            screenClass = mediumWide,
        ).map { it.target }
        val start = targets.indexOf("sec:widgets")
        if (start < 0) return emptyList()
        val rest = targets.drop(start + 1)
        return rest.take(rest.indexOfFirst { it.startsWith("sec:") }.takeIf { it >= 0 } ?: rest.size)
    }

    // ── place (the overlap invariant) ────────

    @Test
    fun `place never overlaps two footprints and keeps every cell inside the grid`() {
        // A full audio set at 4 columns: clock 2x1, playlist 2x2, streams 2x2, plus resource + tail cells.
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.AUDIO_PLAYER,
            StarterResources(allAudioId = 7),
            mapOf(InternalRouteCatalog.KEY_STREAMS to true),
            emptySet(),
            screenClass = mediumWide,
        )
        val placed = LauncherStarterSets.place(items, columns = 4)
        assertNoOverlap(placed)
        placed.forEach { assertTrue("cell past right edge", it.colIndex + it.spanW <= 4) }
    }

    @Test
    fun `a compact header stays overlap-free at every supported column count`() {
        // S1642: the header is the first starter item and the one the packer immediately fills the rest of
        // the row beside, so it is the case most likely to collide - the narrow grid worst of all.
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(recentId = 1, allAudioId = 2, allImagesId = 3),
            allPaddingAvailable,
            emptySet(),
            screenClass = mediumWide,
        )
        columnCounts.forEach { columns ->
            val placed = LauncherStarterSets.place(items, columns)
            assertNoOverlap(placed)
            placed.forEach { assertTrue("cell past right edge at $columns", it.colIndex + it.spanW <= columns) }
            placed.filter { it.item.kind == LauncherCellKind.SECTION }.forEach {
                assertEquals(
                    "header not at the compact span at $columns",
                    LauncherSectionMembership.HEADER_SPAN_W,
                    it.spanW,
                )
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
            screenClass = mediumWide,
        )
        columnCounts.forEach { columns ->
            val placed = LauncherStarterSets.place(items, columns)
            assertNoOverlap(placed)
            placed.forEach { assertTrue("cell past right edge at $columns", it.colIndex + it.spanW <= columns) }
        }
    }

    @Test
    fun `every profile places its starter set intact on the dense-grid column count`() {
        // S2320: the dense grid is the shipped density, and on a typical phone it resolves to five
        // columns where the previous default resolved to four. Every profile is checked because the
        // grid width applies to all of them at once, and a profile added later must not slip through.
        val denseGridColumns = 5
        DeviceProfileType.entries.forEach { profile ->
            val items = LauncherStarterSets.itemsFor(
                profile,
                StarterResources(recentId = 1, allAudioId = 2, allImagesId = 3, allVideoId = 4, allDocsId = 5),
                allPaddingAvailable,
                emptySet(),
                screenClass = mediumWide,
            )
            val placed = LauncherStarterSets.place(items, denseGridColumns)

            assertEquals("item dropped for $profile", items.size, placed.size)
            assertNoOverlap(placed)
            placed.forEach {
                assertTrue("cell past right edge for $profile", it.colIndex + it.spanW <= denseGridColumns)
            }
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
            screenClass = mediumWide,
        ).map { it.target }
        val contentHeaderIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_RESOURCES))
        val actionsHeaderIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_APP_FUNCTIONS))
        imported.forEach { item ->
            val index = targets.indexOf(item.target)
            assertTrue("imported target absent: ${item.target}", index > contentHeaderIndex)
            assertTrue("imported target under the app-functions header: ${item.target}", index < actionsHeaderIndex)
        }
    }

    // ── S1644: the conditional GOOGLE section ────────

    private fun googleItems(
        googleServicesAvailable: Boolean,
        installed: Set<String>,
    ): List<LauncherStarterSets.StarterItem> = LauncherStarterSets.itemsFor(
        DeviceProfileType.PERSONAL_SMARTPHONE,
        StarterResources(recentId = 1),
        allPaddingAvailable,
        installed,
        googleServicesAvailable = googleServicesAvailable,
        screenClass = mediumWide,
    ).filter { it.screenIndex == 0 }

    @Test
    fun `google section holds the installed candidates in catalogue order`() {
        val installed = setOf(
            "com.google.android.youtube",
            "com.android.chrome",
            "com.android.vending",
        )
        val targets = googleItems(googleServicesAvailable = true, installed = installed).map { it.target }
        val headerIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_GOOGLE))
        assertTrue("google header absent", headerIndex >= 0)
        assertEquals(
            listOf("app:com.android.vending", "app:com.android.chrome", "app:com.google.android.youtube"),
            targets.subList(headerIndex + 1, targets.size),
        )
    }

    @Test
    fun `google section is absent when no candidate is installed`() {
        val targets = googleItems(googleServicesAvailable = true, installed = emptySet()).map { it.target }
        assertFalse(
            "an empty section would swallow every cell below it",
            targets.contains(sectionTarget(LauncherCellCommand.SECTION_GOOGLE)),
        )
    }

    @Test
    fun `google section is absent without google services even when the apps are installed`() {
        val installed = LauncherStarterSets.GOOGLE_APP_PACKAGES.toSet()
        val targets = googleItems(googleServicesAvailable = false, installed = installed).map { it.target }
        assertFalse(targets.contains(sectionTarget(LauncherCellCommand.SECTION_GOOGLE)))
        // S2015: these four are exactly the packages that must survive the deduplication on a device
        // with no Play Services. The Google section is not seeded here, so it owns nothing, and the
        // Apps section subtracts an empty set - YouTube, YouTube Music and Chrome still arrive through
        // commonThirdPartyApps, Maps through the MAPS_PROFILES rule. Subtracting the whole catalogue
        // instead of the seeded set would strip them off the desktop altogether (strategic ADR-1).
        // Chrome's literal rather than the constant because PACKAGE_CHROME is private, as with
        // FM_RADIO_PACKAGE elsewhere in this file.
        val seededOutsideGoogleSection = setOf(
            LauncherStarterSets.PACKAGE_YOUTUBE,
            LauncherStarterSets.PACKAGE_YOUTUBE_MUSIC,
            LauncherStarterSets.PACKAGE_MAPS,
            "com.android.chrome",
        )
        seededOutsideGoogleSection.forEach {
            assertTrue("lost without services: $it", targets.contains("app:$it"))
        }
        LauncherStarterSets.GOOGLE_APP_PACKAGES
            .filterNot { it in seededOutsideGoogleSection }
            .forEach { assertFalse("seeded without services: $it", targets.contains("app:$it")) }
    }

    // ── S2015: one section owns a package, and the Apps section holds the user's own ────────

    @Test
    fun `with google services those four sit in the google section and nowhere else`() {
        // S1913 asserted the opposite: that YouTube, YouTube Music, Maps and Chrome legitimately stood
        // outside the Google section while it also held them. That is the duplication S2015 removes.
        val installed = LauncherStarterSets.GOOGLE_APP_PACKAGES.toSet()
        val targets = googleItems(googleServicesAvailable = true, installed = installed).map { it.target }
        val headerIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_GOOGLE))
        assertTrue("google header absent", headerIndex >= 0)
        LauncherStarterSets.GOOGLE_APP_PACKAGES.forEach { packageName ->
            val target = "app:$packageName"
            assertEquals("$packageName seeded twice", 1, targets.count { it == target })
            assertTrue("$packageName sits outside the google section", targets.indexOf(target) > headerIndex)
        }
    }

    @Test
    fun `no app target repeats on a device carrying the whole google catalogue`() {
        val installed = LauncherStarterSets.candidatePackages
        val targets = googleItems(googleServicesAvailable = true, installed = installed).map { it.target }
        val repeated = targets.filter { it.startsWith("app:") }
            .groupingBy { it }.eachCount()
            .filterValues { it > 1 }
        assertEquals("a fresh desktop shows the same icon twice: $repeated", emptyMap<String, Int>(), repeated)
    }

    @Test
    fun `supplied third-party apps are seeded under the android-apps header, never above it`() {
        val thirdParty = listOf("com.whatsapp", "org.telegram.messenger")
        val targets = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(recentId = 1),
            allPaddingAvailable,
            LauncherStarterSets.candidatePackages,
            googleServicesAvailable = true,
            thirdPartyApps = thirdParty,
            screenClass = mediumWide,
        ).map { it.target }
        val appsHeaderIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_ANDROID_APPS))
        val googleHeaderIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_GOOGLE))
        assertTrue("android-apps header absent", appsHeaderIndex >= 0)
        thirdParty.forEach { packageName ->
            val index = targets.indexOf("app:$packageName")
            assertTrue("$packageName absent", index >= 0)
            assertTrue("$packageName landed above its header", index > appsHeaderIndex)
            assertTrue("$packageName fell into the google section", index < googleHeaderIndex)
        }
    }

    @Test
    fun `a supplied third-party app the section already placed is not seeded twice`() {
        // The caller resolves its list against the whole device, so the starter table's own candidates
        // can appear in it; the section drops them rather than placing a second cell.
        val targets = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(recentId = 1),
            allPaddingAvailable,
            setOf(LauncherStarterSets.PACKAGE_YOUTUBE),
            thirdPartyApps = listOf(LauncherStarterSets.PACKAGE_YOUTUBE, "com.whatsapp"),
            screenClass = mediumWide,
        ).map { it.target }
        assertEquals(1, targets.count { it == "app:${LauncherStarterSets.PACKAGE_YOUTUBE}" })
        assertEquals(1, targets.count { it == "app:com.whatsapp" })
    }

    @Test
    fun `google section sits after all content and after the app-functions header`() {
        val installed = setOf("com.android.vending")
        val targets = googleItems(googleServicesAvailable = true, installed = installed).map { it.target }
        val contentHeaderIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_RESOURCES))
        val googleHeaderIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_GOOGLE))
        val actionsHeaderIndex = targets.indexOf(sectionTarget(LauncherCellCommand.SECTION_APP_FUNCTIONS))
        assertTrue("google header must follow the content header", contentHeaderIndex < googleHeaderIndex)
        assertTrue("google header must follow the app-functions header", actionsHeaderIndex < googleHeaderIndex)
        assertTrue(targets.indexOf("fn:ocr") < googleHeaderIndex)
        assertTrue(targets.indexOf("res:1:BROWSE") < googleHeaderIndex)
    }

    // ── S1644: a repeated target is not what makes a cell a duplicate ───────

    @Test
    fun `an imported shortcut is kept even when the starter set already placed its target`() {
        val collidingTarget = LauncherCellCommand.App("com.android.vending").encode()
        val imported = listOf(
            LauncherStarterSets.StarterItem(LauncherCellKind.SHORTCUT, collidingTarget),
        )
        val targets = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            StarterResources(recentId = 1),
            allPaddingAvailable,
            setOf("com.android.vending"),
            googleServicesAvailable = true,
            importedShortcuts = imported,
            screenClass = mediumWide,
        ).map { it.target }
        // Once from the import, once from the GOOGLE section: the owner allows the same application to
        // hold as many cells as it has free positions.
        assertEquals(2, targets.count { it == collidingTarget })
    }

    @Test
    fun `two cells with the same target occupy different rectangles`() {
        val target = LauncherCellCommand.App("com.android.vending").encode()
        val items = List(2) { LauncherStarterSets.StarterItem(LauncherCellKind.SHORTCUT, target) }
        columnCounts.forEach { columns ->
            val placed = LauncherStarterSets.place(items, columns)
            assertNoOverlap(placed)
            assertNotEquals(
                "same target packed onto the same anchor at $columns",
                placed[0].rowIndex to placed[0].colIndex,
                placed[1].rowIndex to placed[1].colIndex,
            )
        }
    }

    @Test
    fun `a header is packed at the one span it is stored at`() {
        // S1642: the packer's clamp to the seeded width used to narrow a header, which is why the seed
        // carried a second span. The compact span fits every grid, so the clamp is now a no-op on it.
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.OTHER,
            StarterResources(),
            emptyMap(),
            emptySet(),
            screenClass = mediumWide,
        )
        val placed = LauncherStarterSets.place(items, columns = 4)
        val header = placed.first { it.item.kind == LauncherCellKind.SECTION }
        assertEquals(LauncherSectionMembership.HEADER_SPAN_W, header.spanW)
    }

    @Test
    fun `a seeded header lands on a row nothing already reaches into`() {
        // S1642: once a header stops filling its row, the packer must still give it a row of its own -
        // otherwise the two-row gadget packed after the first header runs straight through the row the
        // second header lands on, and that gadget belongs to neither section.
        val items = listOf(
            header(LauncherCellCommand.SECTION_MAIN),
            LauncherStarterSets.StarterItem(LauncherCellKind.GADGET, "clock", spanW = 4, spanH = 2),
            header(LauncherCellCommand.SECTION_APP_FUNCTIONS),
            LauncherStarterSets.StarterItem(LauncherCellKind.SHORTCUT, "fn:favorites"),
        )
        val placed = LauncherStarterSets.place(items, columns = 8)
        assertNoOverlap(placed)
        placed.forEachIndexed { index, header ->
            if (header.item.kind != LauncherCellKind.SECTION) return@forEachIndexed
            assertEquals("header not at column 0", 0, header.colIndex)
            placed.forEach { other ->
                val straddles = other.rowIndex < header.rowIndex &&
                    header.rowIndex < other.rowIndex + other.spanH
                assertFalse("cell straddles the header row ${header.rowIndex}", straddles)
            }
            placed.take(index).forEach { earlier ->
                val endsAbove = earlier.rowIndex + earlier.spanH <= header.rowIndex
                assertTrue("cell placed before the header still occupies its row", endsAbove)
            }
        }
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
        val items = LauncherStarterSets.itemsFor(
            DeviceProfileType.OTHER,
            StarterResources(),
            emptyMap(),
            emptySet(),
            screenClass = mediumWide,
        )
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
            screenClass = mediumWide,
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
            val placedByScreen = placed.groupBy { it.screenIndex }
            for ((_, screenPlaced) in placedByScreen) {
                var floor = 0
                screenPlaced.forEach { cell ->
                    assertTrue("${cell.item.target} above its header at $columns", cell.rowIndex >= floor)
                    if (cell.item.kind == LauncherCellKind.SECTION) floor = cell.rowIndex
                }
            }
        }
    }

    @Test
    fun `positional membership matches the section each cell was seeded under`() {
        // S1428 reads membership off the rows, so a cell that packs above its own header belongs to the
        // section before it - the defect S1587 recorded on the device.
        val placed = LauncherStarterSets.place(smartphoneFullSet(), columns = 4)
        val placedByScreen = placed.groupBy { it.screenIndex }
        for ((_, screenPlaced) in placedByScreen) {
            var seededUnder: String? = null
            screenPlaced.forEach { cell ->
                if (cell.item.kind == LauncherCellKind.SECTION) {
                    seededUnder = cell.item.target
                    return@forEach
                }
                val ownedBy = screenPlaced
                    .filter { it.item.kind == LauncherCellKind.SECTION && it.rowIndex <= cell.rowIndex }
                    .maxByOrNull { it.rowIndex }
                    ?.item?.target
                assertEquals("${cell.item.target} drifted out of its section", seededUnder, ownedBy)
            }
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
        screenClass = mediumWide,
    )

    /** S1613: pinned-shortcut items shaped exactly as the seed encodes what the platform hands back. */
    private fun importedPins(): List<LauncherStarterSets.StarterItem> =
        listOf("alpha", "beta", "gamma").map { shortcutId ->
            LauncherStarterSets.StarterItem(
                kind = LauncherCellKind.SHORTCUT,
                target = LauncherCellCommand.PinnedShortcut("com.example.publisher", shortcutId, shortcutId).encode(),
            )
        }

    private fun sectionTarget(sectionKey: String): String = LauncherCellCommand.Section(sectionKey).encode()

    private fun header(sectionKey: String) = LauncherStarterSets.StarterItem(
        LauncherCellKind.SECTION,
        sectionTarget(sectionKey),
        spanW = LauncherSectionMembership.HEADER_SPAN_W,
    )

    private fun assertNoOverlap(placed: List<LauncherStarterSets.PlacedStarterItem>) {
        val occupied = mutableSetOf<Triple<Int, Int, Int>>()
        for (p in placed) {
            for (r in p.rowIndex until p.rowIndex + p.spanH) {
                for (c in p.colIndex until p.colIndex + p.spanW) {
                    assertTrue("overlap at screen ${p.screenIndex} ($r, $c)", occupied.add(Triple(p.screenIndex, r, c)))
                }
            }
        }
    }

    // -- S2309 screen class ----------------------------------------------------------------------

    @Test
    fun `a compact elongated screen seeds fewer first-screen items than an expanded balanced one`() {
        val compact = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            fullResources(),
            allPaddingAvailable,
            setOf(LauncherStarterSets.PACKAGE_YOUTUBE, LauncherStarterSets.PACKAGE_MAPS),
            screenClass = LauncherScreenClass(LauncherScreenClass.Size.COMPACT, LauncherScreenClass.Shape.ELONGATED),
        ).count { it.screenIndex == 0 }
        val expanded = LauncherStarterSets.itemsFor(
            DeviceProfileType.PERSONAL_SMARTPHONE,
            fullResources(),
            allPaddingAvailable,
            setOf(LauncherStarterSets.PACKAGE_YOUTUBE, LauncherStarterSets.PACKAGE_MAPS),
            screenClass = LauncherScreenClass(LauncherScreenClass.Size.EXPANDED, LauncherScreenClass.Shape.BALANCED),
        ).count { it.screenIndex == 0 }

        assertTrue("compact seeded $compact first-screen items, expanded $expanded", compact < expanded)
    }

    @Test
    fun `the seeded header order follows the rule section order`() {
        val screenClass = LauncherScreenClass(LauncherScreenClass.Size.MEDIUM, LauncherScreenClass.Shape.WIDE)
        val profile = DeviceProfileType.CAR_HEAD_UNIT
        val rule = LauncherStarterLayoutRules.ruleFor(screenClass)

        val headers = LauncherStarterSets.itemsFor(
            profile,
            fullResources(),
            allPaddingAvailable,
            setOf(LauncherStarterSets.PACKAGE_MAPS),
            screenClass = screenClass,
        ).filter { it.target.startsWith(LauncherCellCommand.PREFIX_SECTION) }

        // Judged per screen, not over the flat list: a key recurring on ANOTHER screen is the layout the
        // desktop has always had (widgets and resources lead screen 0 and open screen 1 again), while a
        // key recurring on ONE screen is the collision ADR-7 exists to prevent. Comparing the flat list
        // against a de-duplicated rule order would have called the first case a failure.
        val ruleOrder = rule.sectionOrder.map { "${LauncherCellCommand.PREFIX_SECTION}${it.sectionKey}" }
        for ((screen, onScreen) in headers.groupBy { it.screenIndex }) {
            val targets = onScreen.map { it.target }

            assertEquals("screen $screen seeded one section key twice: $targets", targets.size, targets.distinct().size)
            val positions = targets.map { ruleOrder.indexOf(it) }
            assertEquals("screen $screen ordered $targets against the rule", positions.sorted(), positions)
        }
    }

    @Test
    fun `no seeded item lands past the last screen the rule allows`() {
        for (profile in DeviceProfileType.entries) {
            for (size in LauncherScreenClass.Size.entries) {
                for (shape in LauncherScreenClass.Shape.entries) {
                    val screenClass = LauncherScreenClass(size, shape)
                    val rule = LauncherStarterLayoutRules.ruleFor(screenClass)
                    val items = LauncherStarterSets.itemsFor(
                        profile,
                        fullResources(),
                        allPaddingAvailable,
                        setOf(LauncherStarterSets.PACKAGE_MAPS),
                        screenClass = screenClass,
                    )

                    val worst = items.maxOf { it.screenIndex }
                    assertTrue(
                        "$profile on $screenClass seeded screen $worst of ${rule.screenCount}",
                        worst <= rule.screenCount - 1,
                    )
                }
            }
        }
    }

    @Test
    fun `a bare device still gets at least one section`() {
        for (profile in DeviceProfileType.entries) {
            val items = LauncherStarterSets.itemsFor(
                profile,
                StarterResources(),
                emptyMap(),
                emptySet(),
                screenClass = LauncherScreenClass(
                    LauncherScreenClass.Size.COMPACT,
                    LauncherScreenClass.Shape.ELONGATED,
                ),
            )

            assertTrue(
                "$profile seeded no section on a bare device",
                items.any { it.target.startsWith(LauncherCellCommand.PREFIX_SECTION) },
            )
        }
    }

    @Test
    fun `no section header is seeded without an item under it`() {
        val screenClass = LauncherScreenClass(LauncherScreenClass.Size.COMPACT, LauncherScreenClass.Shape.ELONGATED)

        for (profile in DeviceProfileType.entries) {
            val targets = LauncherStarterSets
                .itemsFor(profile, StarterResources(), emptyMap(), emptySet(), screenClass = screenClass)
                .map { it.target }

            targets.forEachIndexed { index, target ->
                if (target.startsWith(LauncherCellCommand.PREFIX_SECTION)) {
                    val next = targets.getOrNull(index + 1)
                    assertTrue(
                        "$profile seeded empty header $target",
                        next != null && !next.startsWith(LauncherCellCommand.PREFIX_SECTION),
                    )
                }
            }
        }
    }

    @Test
    fun `no two section headers share a screen and a target`() {
        for (profile in DeviceProfileType.entries) {
            for (size in LauncherScreenClass.Size.entries) {
                for (shape in LauncherScreenClass.Shape.entries) {
                    val headers = LauncherStarterSets.itemsFor(
                        profile,
                        fullResources(),
                        allPaddingAvailable,
                        setOf(LauncherStarterSets.PACKAGE_YOUTUBE, LauncherStarterSets.PACKAGE_MAPS),
                        screenClass = LauncherScreenClass(size, shape),
                    ).filter { it.target.startsWith(LauncherCellCommand.PREFIX_SECTION) }
                        .map { it.screenIndex to it.target }

                    assertEquals(
                        "$profile on $size/$shape emitted a duplicate section header",
                        headers.size,
                        headers.distinct().size,
                    )
                }
            }
        }
    }

    /** A device with something in every media bucket, so a budget has something to cut. */
    private fun fullResources() = StarterResources(
        recentId = 1L,
        allAudioId = 2L,
        allImagesId = 3L,
        allVideoId = 4L,
        allDocsId = 5L,
        cameraId = 6L,
        lastResourceId = 7L,
    )

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
