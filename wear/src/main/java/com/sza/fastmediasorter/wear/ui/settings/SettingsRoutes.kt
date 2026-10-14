package com.sza.fastmediasorter.wear.ui.settings

import android.net.Uri

object SettingsRoutes {
    const val MEDIA_TYPES = "settings/media_types"
    const val SLIDESHOW = "settings/slideshow"
    const val SCREEN = "settings/screen"
    const val OTHER = "settings/other"

    /** S2587: the second, permanent way into the tile target picker - see the screen's own comment. */
    const val TILE_TARGETS = "settings/tile_targets"

    /** S3226: the permissions of an install past its first run - shown only where a build asks for any. */
    const val PERMISSIONS = "settings/permissions"

    /** S4158: the exchange servers this watch joined from the phone - view, outgoing mark, removal. */
    const val EXCHANGE_SERVERS = "settings/exchange_servers"

    /** S4229: the broadcasts of the account of every exchange server, grouped by the device that makes them. */
    const val EXCHANGE_BROADCASTS = "settings/exchange_broadcasts"

    /** S4228: the folders of one exchange entry's account; the entry identity rides the route URL-encoded. */
    const val ARG_EXCHANGE_ENTRY = "entry"
    const val EXCHANGE_ACCOUNT_FOLDERS = "settings/exchange_account_folders/{$ARG_EXCHANGE_ENTRY}"

    fun exchangeAccountFolders(entryIdentity: String): String =
        "settings/exchange_account_folders/" + Uri.encode(entryIdentity)

    const val ABOUT = "settings/about"
}
