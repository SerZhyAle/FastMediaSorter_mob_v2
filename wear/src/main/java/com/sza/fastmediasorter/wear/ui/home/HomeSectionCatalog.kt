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
 */
object HomeSectionCatalog {

    fun sectionsFor(visibility: HomeSectionVisibility): List<HomeSection> = buildList {
        // S1836: the same route builder a tap in the sources list uses, so the shortcut and the list
        // cannot drift into two entrances to one resource.
        visibility.lastUsedResource?.let { target ->
            add(
                HomeSection(
                    id = HomeSectionId.LAST_USED_RESOURCE,
                    labelRes = R.string.wear_section_last_used,
                    route = WearRoutes.sourceMediaType(target.id, target.name),
                    dynamicLabel = target.name
                )
            )
        }
        if (visibility.favouritesEnabled) {
            add(
                HomeSection(
                    id = HomeSectionId.FAVOURITES,
                    labelRes = R.string.wear_section_favourites,
                    route = WearRoutes.FAVOURITES
                )
            )
        }
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
    }
}
