package com.sza.fastmediasorter.ui.common.support

import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.common.input.UiSurface
import timber.log.Timber

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
    enum class Page {
        START,
        HOME_NETWORK,
        LIVE_BROADCAST,
        CLOUDS,
        EXCHANGE_SERVER,
        ACCOUNT_DELETION,
        FOLDERS_EXCHANGE_SERVER,
        FOLDERS_GOOGLE_DRIVE,
        BROADCAST_START,
        BROADCAST_RECEIVE,
        WATCH_EXCHANGE,
        SCHEDULE_REMOTE_FOLDERS,
        PHONE_SHARING,
        MY_DEVICES,
    }

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

    // S4237: the overview of the exchange server; it links onward to the install, accounts and clipboard
    // pages, which the help buttons of those screens reach through it.
    private val EXCHANGE_SERVER = Localized(
        en = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-server.html",
        ru = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-server-ru.html",
        uk = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-server-uk.html",
    )

    // S4243: the page "how to delete an account"; it is also the address given to Play Console, so it must
    // work without the app and never move.
    private val ACCOUNT_DELETION = Localized(
        en = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/account-deletion.html",
        ru = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/account-deletion-ru.html",
        uk = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/account-deletion-uk.html",
    )

    // S4273: the scenario pages of release 46 (S4242). Each is linked from the screen of its function
    // (SITE-STRUCTURE 0.2 item E); the addresses carry no anchors, so a page edit cannot break them.
    // Positional en, ru, uk: with the argument names the longest literal passes the 120 column limit.
    private val FOLDERS_EXCHANGE_SERVER = Localized(
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/folders-exchange-server.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/folders-exchange-server-ru.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/folders-exchange-server-uk.html",
    )
    private val FOLDERS_GOOGLE_DRIVE = Localized(
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/folders-google-drive.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/folders-google-drive-ru.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/folders-google-drive-uk.html",
    )
    private val BROADCAST_START = Localized(
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-start.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-start-ru.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-start-uk.html",
    )
    private val BROADCAST_RECEIVE = Localized(
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-receive.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-receive-ru.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/broadcast-receive-uk.html",
    )
    private val WATCH_EXCHANGE = Localized(
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/watch-exchange.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/watch-exchange-ru.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/watch-exchange-uk.html",
    )
    private val SCHEDULE_REMOTE_FOLDERS = Localized(
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/schedule-remote-folders.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/schedule-remote-folders-ru.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/schedule-remote-folders-uk.html",
    )
    private val PHONE_SHARING = Localized(
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/phone-sharing.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/phone-sharing-ru.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/phone-sharing-uk.html",
    )
    private val MY_DEVICES = Localized(
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/my-devices.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/my-devices-ru.html",
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/my-devices-uk.html",
    )

    /** Address of [page] in [language], English when the portal has no edition in that language. */
    fun urlFor(page: Page, language: String): String {
        val addresses = when (page) {
            Page.START -> START
            Page.HOME_NETWORK -> HOME_NETWORK
            Page.LIVE_BROADCAST -> LIVE_BROADCAST
            Page.CLOUDS -> CLOUDS
            Page.EXCHANGE_SERVER -> EXCHANGE_SERVER
            Page.ACCOUNT_DELETION -> ACCOUNT_DELETION
            Page.FOLDERS_EXCHANGE_SERVER -> FOLDERS_EXCHANGE_SERVER
            Page.FOLDERS_GOOGLE_DRIVE -> FOLDERS_GOOGLE_DRIVE
            Page.BROADCAST_START -> BROADCAST_START
            Page.BROADCAST_RECEIVE -> BROADCAST_RECEIVE
            Page.WATCH_EXCHANGE -> WATCH_EXCHANGE
            Page.SCHEDULE_REMOTE_FOLDERS -> SCHEDULE_REMOTE_FOLDERS
            Page.PHONE_SHARING -> PHONE_SHARING
            Page.MY_DEVICES -> MY_DEVICES
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
        val language = LocaleHelper.getLanguage(activity)
        Timber.d("S4237: portal page %s opens in %s", page, language)
        DocsPageOpenManager.open(activity, urlFor(page, language))
    }
}
