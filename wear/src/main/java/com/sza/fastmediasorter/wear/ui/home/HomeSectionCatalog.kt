package com.sza.fastmediasorter.wear.ui.home

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HomeSection
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes

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
 */
object HomeSectionCatalog {

    fun sectionsFor(visibility: HomeSectionVisibility): List<HomeSection> = buildList {
        add(
            HomeSection(
                id = HomeSectionId.RESOURCES,
                labelRes = R.string.wear_section_resources,
                route = WearRoutes.NETWORK_SOURCES
            )
        )
        add(
            HomeSection(
                id = HomeSectionId.PHONE,
                labelRes = R.string.wear_section_phone,
                route = WearRoutes.PHONE_HOME
            )
        )
        add(
            HomeSection(
                id = HomeSectionId.LOCAL,
                labelRes = R.string.wear_section_local,
                route = WearRoutes.LOCAL_HOME
            )
        )
        if (visibility.streamsEnabled) {
            add(
                HomeSection(
                    id = HomeSectionId.STREAMS,
                    labelRes = R.string.wear_section_streams,
                    route = WearRoutes.STREAMS
                )
            )
        }
        add(
            HomeSection(
                id = HomeSectionId.APPS,
                labelRes = R.string.wear_section_apps,
                route = WearRoutes.APPS
            )
        )
        add(
            HomeSection(
                id = HomeSectionId.FAVOURITES,
                labelRes = R.string.wear_section_favourites,
                route = WearRoutes.FAVOURITES
            )
        )
    }
}
