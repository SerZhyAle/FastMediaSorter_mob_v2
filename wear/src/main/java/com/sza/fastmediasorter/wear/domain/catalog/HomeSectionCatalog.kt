package com.sza.fastmediasorter.wear.domain.catalog

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HomeSection
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.WearApp
import com.sza.fastmediasorter.wear.domain.model.WearAppId

/**
 * The home screen renders what this catalog returns; it never decides for itself which sections exist.
 *
 * Order is fixed here rather than at the call site so adding a section stays a one-line change and
 * cannot silently reorder the screen. STREAMS and APPS are entrances owned by other tickets - S1708
 * and S1710 respectively - which is why they are ordinary rows here rather than screen-specific code.
 *
 * S1940: FAVOURITES is last and unconditional. It is the section the owner asked to sit directly above
 * the Settings command bar, and the position is stated as that relation rather than as a tile number
 * because STREAMS is conditional, so the count of drawn rows varies.
 *
 * S1974: this catalog holds predefined sections only. The last-used shortcuts travel in their own
 * HomeUiState field and are drawn as a row of their own, which is what makes every position below
 * them independent of how many resources the owner has opened.
 *
 * S2751: moved here from the home screen's own package, and its rows no longer carry a route. A section
 * of this list is addressed by its id, which the navigation branch turns into a screen address; the
 * route field is left to the dynamic last-used row, which is built up in the screen's own layer.
 */
object HomeSectionCatalog {

    fun sectionsFor(visibility: HomeSectionVisibility): List<HomeSection> = buildList {
        add(
            HomeSection(
                id = HomeSectionId.RESOURCES,
                labelRes = R.string.wear_section_resources
            )
        )
        add(
            HomeSection(
                id = HomeSectionId.PHONE,
                labelRes = R.string.wear_section_phone
            )
        )
        add(
            HomeSection(
                id = HomeSectionId.LOCAL,
                labelRes = R.string.wear_section_local
            )
        )
        if (visibility.streamsEnabled) {
            add(
                HomeSection(
                    id = HomeSectionId.STREAMS,
                    labelRes = R.string.wear_section_streams
                )
            )
        }
        add(
            HomeSection(
                id = HomeSectionId.APPS,
                labelRes = R.string.wear_section_apps
            )
        )
        add(lastUsedAppSection(visibility.lastUsedApp))
        // S2551: the opposite direction of the row above - the phone's camera watched here, rather
        // than this watch's microphone heard there. Unconditional for the same reason: both Wear
        // flavors carry it, and the phone half answers NOT_SUPPORTED where its own build cannot.
        add(
            HomeSection(
                id = HomeSectionId.PHONE_CAMERA,
                labelRes = R.string.wear_section_phone_camera
            )
        )
        add(
            HomeSection(
                id = HomeSectionId.FAVOURITES,
                labelRes = R.string.wear_section_favourites
            )
        )
    }

    /**
     * S3116: the slot after Apps - the program opened last, or the broadcast entrance.
     *
     * S2509 made this slot the first of the broadcast's two equal paths, and it stays exactly that
     * until a program has been opened, and again whenever the broadcast itself was the last one: the
     * broadcast row is returned unchanged in both cases rather than dressed up as a recent program,
     * so every entrance already pointed at it - the tile among them - keeps addressing the same row.
     *
     * Unconditional in either shape, because strategic §3.2 forbids hiding this entrance behind the
     * restricted-capability gate - the capability ships in both Wear flavors.
     */
    private fun lastUsedAppSection(lastUsedApp: WearApp?): HomeSection {
        if (lastUsedApp == null || lastUsedApp.id == WearAppId.BROADCAST) {
            return HomeSection(
                id = HomeSectionId.BROADCAST,
                labelRes = R.string.wear_section_broadcast
            )
        }
        return HomeSection(
            id = HomeSectionId.LAST_USED_APP,
            labelRes = lastUsedApp.labelRes,
            appId = lastUsedApp.id
        )
    }

    /**
     * S2511: the same sections, in the order the shortcut tile offers them.
     *
     * The screen scrolls and the tile does not: its grid holds seven cells, one of which goes to the way
     * out when there are more sections than that. So the tile needs an answer to "which ones come first"
     * that the screen never has to give, and it is declared here beside the screen's order rather than in
     * the tile layer - a second list of sections living somewhere else is how the two come to disagree
     * about what a section even is.
     *
     * A section absent from [TILE_ORDER] sorts last, which is deliberate: a future row lands behind the
     * ones the owner named in the request and is reached through the overflow cell, rather than pushing
     * one of those off the tile the moment it is added.
     */
    fun tileSectionsFor(visibility: HomeSectionVisibility): List<HomeSection> =
        sectionsFor(visibility).sortedBy { section ->
            TILE_ORDER.indexOf(section.id).takeIf { it >= 0 } ?: TILE_ORDER.size
        }

    /**
     * The sections the owner named when asking for this tile, ahead of the rows added by later tickets.
     *
     * FAVOURITES sits among them rather than last as on the screen: it is one of the six the request lists,
     * and leaving it in screen position is exactly what dropped it off the grid once the catalog reached
     * eight rows.
     */
    private val TILE_ORDER = listOf(
        HomeSectionId.RESOURCES,
        HomeSectionId.PHONE,
        HomeSectionId.LOCAL,
        HomeSectionId.STREAMS,
        HomeSectionId.APPS,
        HomeSectionId.FAVOURITES
    )
}
