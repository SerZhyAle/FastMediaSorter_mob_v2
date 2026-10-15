package com.sza.fastmediasorter.wear.domain.model

import timber.log.Timber
import java.util.Locale

/**
 * S2496: the external addresses the watch offers to open.
 *
 * Held here rather than inside the About screen because two readers need the same address - the
 * on-watch browser intent and the request that hands it to the paired phone - and two copies of a
 * literal would sooner or later point at two different pages.
 */
object WearPortalLinks {

    // S4237: the media exchange portal replaces the watch section as the page the "Web Portal" row opens.
    // The addresses are full literals on purpose: assert-site-addresses.ps1 reads them from this file.
    private const val PORTAL_START_EN =
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/"
    private const val PORTAL_START_RU =
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/index-ru.html"
    private const val PORTAL_START_UK =
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/index-uk.html"

    /**
     * The portal start page in [language]. The portal publishes en, ru and uk only; any other language gets
     * the English page, never the nearest published one (SITE-STRUCTURE 0.2 item C).
     */
    fun portalUrl(language: String = Locale.getDefault().language): String {
        Timber.d("S4237: watch portal address asked for language %s", language)
        return when (language.lowercase()) {
            "ru" -> PORTAL_START_RU
            "uk" -> PORTAL_START_UK
            else -> PORTAL_START_EN
        }
    }

    /**
     * S4011: the phone app's store listing. The watch and the phone share one `applicationId`, so this is
     * the listing of the phone half; it is opened on the phone, never here.
     */
    const val PHONE_APP_STORE_URL = "market://details?id=com.sza.fastmediasorter"
}
