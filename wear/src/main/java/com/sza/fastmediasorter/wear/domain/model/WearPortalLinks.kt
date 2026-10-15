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

    // S4273: the page of the watch's own function in the media exchange portal; SITE-STRUCTURE 0.2 item E
    // asks the About rows for it, not for the portal start. The addresses are full literals on purpose:
    // assert-site-addresses.ps1 reads them from this file.
    private const val WATCH_EXCHANGE_EN =
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/watch-exchange.html"
    private const val WATCH_EXCHANGE_RU =
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/watch-exchange-ru.html"
    private const val WATCH_EXCHANGE_UK =
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/watch-exchange-uk.html"

    /**
     * The watch page of the portal in [language]. The portal publishes en, ru and uk only; any other language
     * gets the English page, never the nearest published one (SITE-STRUCTURE 0.2 item C).
     */
    fun watchExchangeUrl(language: String = Locale.getDefault().language): String {
        Timber.d("S4237: watch portal address asked for language %s", language)
        Timber.d("S4273: About rows open the watch page of the portal")
        return when (language.lowercase()) {
            "ru" -> WATCH_EXCHANGE_RU
            "uk" -> WATCH_EXCHANGE_UK
            else -> WATCH_EXCHANGE_EN
        }
    }

    /**
     * S4011: the phone app's store listing. The watch and the phone share one `applicationId`, so this is
     * the listing of the phone half; it is opened on the phone, never here.
     */
    const val PHONE_APP_STORE_URL = "market://details?id=com.sza.fastmediasorter"
}
