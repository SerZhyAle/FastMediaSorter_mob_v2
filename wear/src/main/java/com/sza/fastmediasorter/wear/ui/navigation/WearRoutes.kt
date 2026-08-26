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

    /**
     * S1862: the voice recorder is a mini-program, so [VOICE_RECORDER] carries its `canonicalKey`
     * like the three above. [VOICE_NOTES] is not one - it is the recorder's own note list, reached
     * only from the recorder, so it stays out of the Apps catalog and has no key to match.
     */
    const val VOICE_RECORDER = "voice_recorder"
    const val VOICE_NOTES = "voice_notes"

    /**
     * S2008: the watch's own report, relocated from `settings/system_info`. Its value is the program's
     * `canonicalKey` like the four above, which is what the Apps catalog's route-equals-key test reads;
     * the old settings-namespaced address could not satisfy that and is gone.
     */
    const val SYSTEM_INFO = "system_info"

    /** S2006: where a file the watch cannot play lands, instead of the audio player. */
    const val UNSUPPORTED_FILE = "unsupported_file"

    const val ARG_MEDIA_TYPE = "mediaType"
    const val ARG_SOURCE_ID = "sourceId"
    const val ARG_SOURCE_NAME = "sourceName"
    const val ARG_ADDED = "added"
    const val ARG_UPDATED = "updated"
    const val ARG_FILE_ID = "fileId"
    const val ARG_TILE_KIND = "tileKind"

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

    /**
     * S1955: where a tile of the given kind is pointed at its target.
     *
     * The only address the tiles add. Everything else a tile opens is an existing route reused as it is -
     * a resource goes to [SOURCE_MEDIA_TYPE_PATTERN], favourites to [FAVOURITES], a channel to the players -
     * because the assignment is the one action the feature adds and the rest it only shortens.
     */
    const val TILE_TARGET_PICKER_PATTERN = "tile_target_picker/{$ARG_TILE_KIND}"

    fun browse(mediaType: String): String = "browse/$mediaType"

    fun browsePhone(mediaType: String): String = "browse_phone/$mediaType"

    fun browseSource(mediaType: String, sourceId: String, sourceName: String): String {
        val route = "browse/$mediaType?$ARG_SOURCE_ID=${encodeArg(sourceId)}&$ARG_SOURCE_NAME=${encodeArg(sourceName)}"
        return route
    }

    fun sourceMediaType(sourceId: String, sourceName: String): String {
        val route = "source_media_type?$ARG_SOURCE_ID=${encodeArg(sourceId)}&$ARG_SOURCE_NAME=${encodeArg(sourceName)}"
        return route
    }

    fun syncResult(added: Int, updated: Int): String = "sync_result/$added/$updated"

    fun audioPlayer(fileId: Long): String = "audio_player/$fileId"

    fun videoPlayer(fileId: Long): String = "video_player/$fileId"

    fun imageViewer(fileId: Long): String = "image_viewer/$fileId"

    fun tileTargetPicker(kind: String): String = "tile_target_picker/${encodeArg(kind)}"

    /**
     * S1848: percent-encodes one argument value before it is concatenated into a route.
     *
     * The builders below assemble a URI by hand and the graph parses it back as a URI, so any character
     * that means something to a URI has to be escaped or the two ends disagree. A source named
     * `Home & NAS` used to produce a second query pair - `sourceName=Home ` plus a stray ` NAS` - so the
     * screen opened with a truncated name; a `/` or a `?` could miss the pattern entirely and the tap did
     * nothing at all, with no error and no log. Names arrive from the phone (S1556), where `&` in a
     * resource name is perfectly legal, so this is not a typo the user could avoid.
     *
     * Hand-rolled rather than android.net.Uri.encode because this module's unit tests run on the plain
     * JVM with no Robolectric, where every android.net.Uri call returns a "not mocked" stub - the rule
     * would ship untested. Not java.net.URLEncoder either: it encodes a space as `+`, which URI decoding
     * on the receiving side does NOT turn back into a space, so a name with a space would arrive wrong.
     *
     * Escapes everything outside the RFC 3986 unreserved set, which is always safe to leave alone.
     */
    fun encodeArg(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && c.code < CHAR_LIMIT_ASCII || c in UNRESERVED_PUNCTUATION) {
                append(c)
            } else {
                append('%')
                append(HEX[(byte.toInt() shr NIBBLE_BITS) and LOW_NIBBLE_MASK])
                append(HEX[byte.toInt() and LOW_NIBBLE_MASK])
            }
        }
    }

    private const val CHAR_LIMIT_ASCII = 128
    private const val UNRESERVED_PUNCTUATION = "-_.~"
    private const val HEX = "0123456789ABCDEF"
    private const val NIBBLE_BITS = 4
    private const val LOW_NIBBLE_MASK = 0x0F
}
