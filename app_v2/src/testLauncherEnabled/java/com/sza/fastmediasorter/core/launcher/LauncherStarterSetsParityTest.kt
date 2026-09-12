package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.core.launcher.LauncherStarterSets.StarterResources
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * S0404: [LauncherStarterSets] (src/main) hardcodes the gadget target keys because it cannot import
 * [LauncherGadgetRegistry] (src/launcherEnabled - Rule 14 forbids a launcherEnabled import in main).
 * The hardcoded keys and the registry consts must stay identical, or the seed produces cells the
 * desktop renders as "unknown gadget". Nothing compile-time ties them, so this test does - it fails
 * the moment a `KEY_*` rename in the registry drifts from the string the starter set emits.
 *
 * S1642 keeps a second copy under the same constraint: the span a section header is stored and drawn at
 * has to fit the narrowest grid the renderer resolves.
 *
 * Lives in `src/testLauncherEnabled`: [LauncherGadgetRegistry] ships only in the launcherEnabled source
 * set, and that set is mounted by two flavors. S1498 moved this out of `src/testStandard`, whose single
 * flavor meant the parity this guards was never checked on the other build that ships the registry.
 */
@Suppress("FunctionNaming") // backtick test names, project convention (cf. LauncherGridGeometryTest)
class LauncherStarterSetsParityTest {

    /**
     * S2309: the screen class these cases compose for unless they are about the class itself.
     *
     * Medium and wide is the pair the pre-S2309 hardcoded layout was written against, so a case
     * that predates the second axis keeps asserting the desktop it was written to assert.
     */
    private val mediumWide =
        LauncherScreenClass(LauncherScreenClass.Size.MEDIUM, LauncherScreenClass.Shape.WIDE)

    @Test
    fun `the header span fits the narrowest grid the renderer can resolve`() {
        // S1642: the header is stored and drawn at one span, so the only way it can fail to fit is by
        // exceeding the smallest column count the desktop ever resolves - where it would be clamped on
        // screen while the table still held the wider rectangle.
        assertEquals(2, LauncherSectionMembership.HEADER_SPAN_W)
        assertTrue(LauncherSectionMembership.HEADER_SPAN_W <= LauncherGridGeometry.MIN_COLUMNS)
    }

    @Test
    fun `starter gadget target keys match the registry consts`() {
        // Clock: the one gadget every profile seeds, whose bare target is the key itself.
        val clock = LauncherStarterSets.itemsFor(
            DeviceProfileType.OTHER,
            StarterResources(),
            emptyMap(),
            emptySet(),
            screenClass = mediumWide,
        )
            .first { it.kind == LauncherCellKind.GADGET }
        assertEquals(LauncherGadgetRegistry.KEY_CLOCK, clock.target)

        val folderPreview = LauncherStarterSets
            .itemsFor(
                DeviceProfileType.PHOTO_FRAME,
                StarterResources(lastResourceId = 1L),
                emptyMap(),
                emptySet(),
                screenClass = mediumWide,
            )
            .first { it.target.startsWith(LauncherGadgetRegistry.KEY_FOLDER_PREVIEW) }
        assertEquals("${LauncherGadgetRegistry.KEY_FOLDER_PREVIEW}:1", folderPreview.target)

        val playlist = LauncherStarterSets
            .itemsFor(
                DeviceProfileType.AUDIO_PLAYER,
                StarterResources(allAudioId = 2L),
                emptyMap(),
                emptySet(),
                screenClass = mediumWide,
            )
            .first { it.target.startsWith(LauncherGadgetRegistry.KEY_PLAYLIST) }
        assertEquals("${LauncherGadgetRegistry.KEY_PLAYLIST}:2", playlist.target)

        val streams = LauncherStarterSets
            .itemsFor(
                DeviceProfileType.AUDIO_PLAYER,
                StarterResources(allAudioId = 2L),
                mapOf(InternalRouteCatalog.KEY_STREAMS to true),
                emptySet(),
                screenClass = mediumWide,
            )
            .first { it.target == LauncherGadgetRegistry.KEY_STREAMS }
        assertEquals(LauncherGadgetRegistry.KEY_STREAMS, streams.target)

        val sensors = LauncherStarterSets.itemsFor(
            DeviceProfileType.CAR_HEAD_UNIT,
            StarterResources(),
            emptyMap(),
            emptySet(),
            screenClass = mediumWide,
        ).map { it.target }.toSet()
        assertEquals(true, LauncherGadgetRegistry.KEY_WEATHER in sensors)
        assertEquals(true, LauncherGadgetRegistry.KEY_SPEED in sensors)
        // S1747: the compass replaced the altitude + satellites pair in the seed. Both remain in the
        // registry and stay addable by hand, so their absence here is the assertion, not an omission.
        assertEquals(true, LauncherGadgetRegistry.KEY_COMPASS in sensors)
        assertEquals(false, LauncherGadgetRegistry.KEY_ALTITUDE in sensors)
        assertEquals(false, LauncherGadgetRegistry.KEY_SATELLITES in sensors)
        assertEquals(true, LauncherGadgetRegistry.KEY_AUDIO_NOW_PLAYING in sensors)

        // S1886: the two profiles whose widgets group was empty before this ticket, so a regression
        // that drops the seed shows up here as an empty group rather than as a silently plainer desktop.
        // The image window carries its resource id, so the assertion is on the `key:id` prefix.
        val tabletWindow = LauncherStarterSets
            .itemsFor(
                DeviceProfileType.HOME_TABLET,
                StarterResources(allImagesId = 3L),
                emptyMap(),
                emptySet(),
                screenClass = mediumWide,
            )
            .first { it.target.startsWith(LauncherGadgetRegistry.KEY_MEDIA_IMAGE_WINDOW) }
        assertEquals("${LauncherGadgetRegistry.KEY_MEDIA_IMAGE_WINDOW}:3", tabletWindow.target)

        val headsetTargets = LauncherStarterSets
            .itemsFor(
                DeviceProfileType.VR_HEADSET,
                StarterResources(),
                emptyMap(),
                emptySet(),
                screenClass = mediumWide,
            )
            .map { it.target }
            .toSet()
        assertEquals(true, LauncherGadgetRegistry.KEY_BATTERY in headsetTargets)
    }

    /**
     * S2385: the head unit's signature key, declared since S2241 and emitted by nothing until that
     * ticket - so it is the one key whose spelling was never exercised against the registry.
     *
     * Its own case rather than a block inside the sweep above, which is at the length ceiling: a
     * fourteenth assertion there would be paid for by deleting one of the thirteen.
     */
    @Test
    fun `the head units live map key matches the registry const`() {
        val liveMap = LauncherStarterSets
            .itemsFor(
                DeviceProfileType.CAR_HEAD_UNIT,
                StarterResources(),
                emptyMap(),
                emptySet(),
                screenClass = mediumWide,
            )
            .first { it.target == LauncherGadgetRegistry.KEY_GOOGLE_MAPS_LIVE }
        assertEquals(LauncherGadgetRegistry.KEY_GOOGLE_MAPS_LIVE, liveMap.target)
    }

    @Test
    fun `every starter gadget key resolves through the registry`() {
        val registryKeys = setOf(
            LauncherGadgetRegistry.KEY_CLOCK,
            LauncherGadgetRegistry.KEY_WEATHER,
            LauncherGadgetRegistry.KEY_PLAYLIST,
            LauncherGadgetRegistry.KEY_STREAMS,
            LauncherGadgetRegistry.KEY_FOLDER_PREVIEW,
            LauncherGadgetRegistry.KEY_SPEED,
            LauncherGadgetRegistry.KEY_COMPASS,
            LauncherGadgetRegistry.KEY_AUDIO_NOW_PLAYING,
            LauncherGadgetRegistry.KEY_SEARCH,
            LauncherGadgetRegistry.KEY_MEDIA_IMAGE_WINDOW,
            LauncherGadgetRegistry.KEY_MEDIA_AUDIO_WINDOW,
            LauncherGadgetRegistry.KEY_MEDIA_VIDEO_WINDOW,
            LauncherGadgetRegistry.KEY_MEDIA_DOCUMENT_WINDOW,
            LauncherGadgetRegistry.KEY_BATTERY,
            LauncherGadgetRegistry.KEY_GOOGLE_MAPS_LIVE,
            LauncherGadgetRegistry.KEY_TRANSLATOR,
            LauncherGadgetRegistry.KEY_STORAGE,
        )
        assertEquals(registryKeys, LauncherStarterSets.gadgetKeys)
    }

    /**
     * S2682: the two keys the ticket ruled seedable, and the profiles it ruled them onto.
     *
     * The coverage case below proves only that a seeded key reaches SOME profile, so it would pass on a
     * membership set that had drifted onto the wrong ones - and which profiles get the tile is the whole
     * content of the ruling.
     */
    @Test
    fun `the translator and storage tiles land on the profiles S2682 named`() {
        fun targetsFor(profile: DeviceProfileType) = LauncherStarterSets
            .itemsFor(profile, StarterResources(), emptyMap(), emptySet(), screenClass = mediumWide)
            .map { it.target }
            .toSet()

        val phone = targetsFor(DeviceProfileType.PERSONAL_SMARTPHONE)
        assertTrue(LauncherGadgetRegistry.KEY_TRANSLATOR in phone)
        assertTrue(LauncherGadgetRegistry.KEY_STORAGE in phone)

        // The reader translates a word but does no sorting, so it earns one of the two, not both.
        val reader = targetsFor(DeviceProfileType.EBOOK_READER)
        assertTrue(LauncherGadgetRegistry.KEY_TRANSLATOR in reader)
        assertEquals(false, LauncherGadgetRegistry.KEY_STORAGE in reader)

        val headUnit = targetsFor(DeviceProfileType.CAR_HEAD_UNIT)
        assertEquals(false, LauncherGadgetRegistry.KEY_TRANSLATOR in headUnit)
        assertEquals(false, LauncherGadgetRegistry.KEY_STORAGE in headUnit)
    }

    /**
     * S2672: the coverage assertion - every key the registry declares carries a seed decision.
     *
     * The registry's side is read by reflection rather than retyped here, because a hand-written list in
     * a test guards nothing: a thirty-sixth `KEY_*` const would be absent from both the policy and the
     * list, and the test would pass while the new gadget joined the twenty this ticket found. Kotlin
     * compiles a companion `const val` to a public static final field on the outer class, so the fields
     * below are the registry's keys exactly.
     */
    @Test
    fun `every registry gadget key carries a seed decision`() {
        val registryKeys = LauncherGadgetRegistry::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java && it.name.startsWith("KEY_") }
            .onEach { it.isAccessible = true }
            .map { it.get(null) as String }
            .toSet()

        assertEquals(registryKeys.size, LauncherGadgetSeedPolicy.decisions.size)
        assertEquals(registryKeys, LauncherGadgetSeedPolicy.decisions.keys)
    }

    /** S2672: the seed table emits exactly the keys the policy marks Seeded, and nothing else. */
    @Test
    fun `the seeded decisions match the keys the starter table emits`() {
        assertEquals(LauncherGadgetSeedPolicy.seededKeys, LauncherStarterSets.gadgetKeys)
    }

    /**
     * S2672: "Seeded" has to be earned, not declared. Without this case the policy could call a key
     * seeded while no profile emitted it - which is the exact failure the ticket opened on, one level up.
     *
     * Every input is maximally generous (every resource id present, every route launchable, every
     * candidate package installed), so a key missing here is missing from the table on every profile,
     * not merely absent from this fixture.
     */
    @Test
    fun `every key declared seeded is emitted for at least one profile`() {
        val resources = StarterResources(
            recentId = 1L,
            allAudioId = 2L,
            allImagesId = 3L,
            allVideoId = 4L,
            allDocsId = 5L,
            cameraId = 6L,
            allFilesId = 7L,
            lastResourceId = 8L,
        )
        val routes = mapOf(
            InternalRouteCatalog.KEY_STREAMS to true,
            InternalRouteCatalog.KEY_GAME to true,
            InternalRouteCatalog.KEY_NETWORK_MONITOR to true,
        )

        val emitted = DeviceProfileType.entries.flatMap { profile ->
            LauncherStarterSets.itemsFor(
                profile,
                resources,
                routes,
                LauncherStarterSets.candidatePackages,
                googleServicesAvailable = true,
                screenClass = mediumWide,
            )
        }
            .filter { it.kind == LauncherCellKind.GADGET }
            .map { it.target.substringBefore(':') }
            .toSet()

        assertEquals(emptySet<String>(), LauncherGadgetSeedPolicy.seededKeys - emitted)
    }
}
