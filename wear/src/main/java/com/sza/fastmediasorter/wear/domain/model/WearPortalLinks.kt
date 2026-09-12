package com.sza.fastmediasorter.wear.domain.model

/**
 * S2496: the external addresses the watch offers to open.
 *
 * Held here rather than inside the About screen because two readers need the same address - the
 * on-watch browser intent and the request that hands it to the paired phone - and two copies of a
 * literal would sooner or later point at two different pages.
 */
object WearPortalLinks {

    const val WEB_PORTAL_URL = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/wear/"
}
