package com.sza.fastmediasorter.ui.xr

import java.io.File

/**
 * S1218 (ADR-1): what the immersive session is playing, as a type rather than as a path the callers
 * agree to interpret the same way.
 *
 * The playlist model around the host was file-typed end to end, so a live channel could not be
 * expressed in it at all. Making the distinction a sealed type is the mitigation strategic §7 names
 * against file assumptions surviving in paths a quick read does not reach - the compiler lists every
 * place that has to decide.
 */
sealed class VrPlaybackSource {

    /** What the HUD's title shows: a file has a name, a channel has a title. */
    abstract val displayName: String

    /** True when the source has no duration, no position and no neighbour - see strategic §6 Q2. */
    abstract val isLive: Boolean

    data class LocalFile(val file: File) : VrPlaybackSource() {
        override val displayName: String get() = file.name
        override val isLive: Boolean get() = false
    }

    data class NetworkStream(val uri: String, val title: String) : VrPlaybackSource() {
        // A channel with no title falls back to its address rather than to an empty banner, which
        // would read as a decode failure rather than as a missing name.
        override val displayName: String get() = title.ifBlank { uri }
        override val isLive: Boolean get() = true
    }
}
