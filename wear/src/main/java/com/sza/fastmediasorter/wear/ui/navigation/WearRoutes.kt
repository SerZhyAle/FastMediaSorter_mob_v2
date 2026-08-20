package com.sza.fastmediasorter.wear.ui.navigation

/**
 * Every navigation address of the watch app, declared once.
 *
 * A route spelled out at the call site is not checked by anything: the declaration and the jump are two
 * separate string literals, so a typo in either is a silent no-op at runtime - the tap does nothing and no
 * test catches it, because the watch module has no instrumented tests. Naming each route here makes the two
 * ends the same symbol, so a mismatch stops being possible.
 *
 * A route that carries arguments has two forms: the `*_PATTERN` the graph declares, and a builder that fills
 * the arguments in. They must never be confused - navigating to a pattern lands nowhere.
 */
object WearRoutes {

    const val HOME = "home"
    const val NETWORK_SOURCES = "network_sources"
    const val PHONE_RESOURCE = "phone_resource"
    const val ADD_NETWORK_SOURCE = "add_network_source"

    /** Kept reachable because an older build could have put this name on the back stack. */
    const val ADD_SMB_ALIAS = "add_smb"

    const val SYNC_TRANSFER = "sync_transfer"
    const val SETTINGS = "settings"

    /**
     * Home-section entrances (S1781). LOCAL_HOME and PHONE_HOME list the categories of one origin -
     * media on the watch itself and the phone's virtual resources - so they are separate addresses,
     * not two media types of one screen.
     *
     * PHONE_RESOURCE above stays untouched: it is S1697's raw phone-folder browser reached through a
     * request/response token protocol, a different feature that happens to mention the same word.
     */
    const val LOCAL_HOME = "local_home"
    const val PHONE_HOME = "phone_home"
    const val FAVOURITES = "favourites"

    /** Entrances owned by other tickets: the screens behind them belong to S1708 and S1710. */
    const val STREAMS = "streams"
    const val APPS = "apps"

    /**
     * Mini-programs of the Apps section (S1710). Each value equals its program's `canonicalKey` in
     * `WearAppId`, so a key saved by the phone's program registry resolves straight to a destination.
     */
    const val CALCULATOR = "calculator"
    const val NETWORK_MONITOR = "network_monitor"
    const val GAME = "game"

    const val ARG_MEDIA_TYPE = "mediaType"
    const val ARG_SOURCE_ID = "sourceId"
    const val ARG_SOURCE_NAME = "sourceName"
    const val ARG_ADDED = "added"
    const val ARG_UPDATED = "updated"
    const val ARG_FILE_ID = "fileId"

    const val BROWSE_PATTERN = "browse/{$ARG_MEDIA_TYPE}"
    const val PHONE_BROWSE_PATTERN = "browse_phone/{$ARG_MEDIA_TYPE}"
    const val BROWSE_SOURCE_PATTERN =
        "browse/{$ARG_MEDIA_TYPE}?$ARG_SOURCE_ID={$ARG_SOURCE_ID}&$ARG_SOURCE_NAME={$ARG_SOURCE_NAME}"

    /**
     * S1829: the media-type step a network source gets between its list and browse.
     *
     * The other two origins - the watch itself and the phone - pick the type one level up, because each
     * of them has exactly one container. A network origin has many, so the type belongs to the source
     * that was chosen rather than to the origin: picking Photos first would leave sources holding no
     * image at all in the list, as dead ends with nothing to mark them at the entrance.
     */
    const val SOURCE_MEDIA_TYPE_PATTERN =
        "source_media_type?$ARG_SOURCE_ID={$ARG_SOURCE_ID}&$ARG_SOURCE_NAME={$ARG_SOURCE_NAME}"
    const val SYNC_RESULT_PATTERN = "sync_result/{$ARG_ADDED}/{$ARG_UPDATED}"
    const val AUDIO_PLAYER_PATTERN = "audio_player/{$ARG_FILE_ID}"
    const val VIDEO_PLAYER_PATTERN = "video_player/{$ARG_FILE_ID}"
    const val IMAGE_VIEWER_PATTERN = "image_viewer/{$ARG_FILE_ID}"

    fun browse(mediaType: String): String = "browse/$mediaType"

    fun browsePhone(mediaType: String): String = "browse_phone/$mediaType"

    fun browseSource(mediaType: String, sourceId: String, sourceName: String): String =
        "browse/$mediaType?$ARG_SOURCE_ID=$sourceId&$ARG_SOURCE_NAME=$sourceName"

    fun sourceMediaType(sourceId: String, sourceName: String): String =
        "source_media_type?$ARG_SOURCE_ID=$sourceId&$ARG_SOURCE_NAME=$sourceName"

    fun syncResult(added: Int, updated: Int): String = "sync_result/$added/$updated"

    fun audioPlayer(fileId: Long): String = "audio_player/$fileId"

    fun videoPlayer(fileId: Long): String = "video_player/$fileId"

    fun imageViewer(fileId: Long): String = "image_viewer/$fileId"
}
