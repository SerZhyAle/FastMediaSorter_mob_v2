package com.sza.fastmediasorter.ui.common.support

import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.common.input.UiSurface

/**
 * S4143: the pages of the media exchange portal (`docs/media-exchange-portal/`) the app links to.
 *
 * The addresses are full literals on purpose: `assert-site-addresses.ps1` (dimension
 * `held-addresses`) reads them from this file, so a portal page cannot be renamed while the app
 * still opens it. The portal publishes en, ru and uk only; every other UI language gets English.
 * Opening goes through [DocsPageOpenManager], which explains an offline phone or a TV box without
 * a browser instead of failing silently.
 */
object MediaExchangePortalLinks {

    /** The portal pages a screen of the app can point at. */
    enum class Page { START, HOME_NETWORK, LIVE_BROADCAST, CLOUDS, ACCOUNT_DELETION }

    private class Localized(val en: String, val ru: String, val uk: String)

    private val START = Localized(
        en = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/",
        ru = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/index-ru.html",
        uk = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/index-uk.html",
    )
    private val HOME_NETWORK = Localized(
        en = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/home-network.html",
        ru = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/home-network-ru.html",
        uk = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/home-network-uk.html",
    )
    private val LIVE_BROADCAST = Localized(
        en = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/live-broadcast.html",
        ru = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/live-broadcast-ru.html",
        uk = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/live-broadcast-uk.html",
    )
    private val CLOUDS = Localized(
        en = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/clouds.html",
        ru = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/clouds-ru.html",
        uk = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/clouds-uk.html",
    )

    // S4223: the exchange server page until S4243 publishes the account page "how to delete an account".
    private val ACCOUNT_DELETION = Localized(
        en = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-server.html",
        ru = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-server-ru.html",
        uk = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-server-uk.html",
    )

    /** Address of [page] in [language], English when the portal has no edition in that language. */
    fun urlFor(page: Page, language: String): String {
        val addresses = when (page) {
            Page.START -> START
            Page.HOME_NETWORK -> HOME_NETWORK
            Page.LIVE_BROADCAST -> LIVE_BROADCAST
            Page.CLOUDS -> CLOUDS
            Page.ACCOUNT_DELETION -> ACCOUNT_DELETION
        }
        return when (language.lowercase()) {
            "ru" -> addresses.ru
            "uk" -> addresses.uk
            else -> addresses.en
        }
    }

    /** Portal page that explains the choice [surface] asks of the user, or null when it asks none. */
    fun pageFor(surface: UiSurface): Page? = when (surface) {
        UiSurface.CLOUD_PICKER -> Page.CLOUDS
        UiSurface.ADD_RESOURCE -> Page.HOME_NETWORK
        else -> null
    }

    /** Opens [page] in the device language; an offline or browserless device gets the fallback dialog. */
    fun open(activity: FragmentActivity, page: Page) {
        DocsPageOpenManager.open(activity, urlFor(page, LocaleHelper.getLanguage(activity)))
    }
}
