package com.sza.fastmediasorter.ui.player

import android.net.Uri

/**
 * Single source of truth for recognizing the "set as default player" probe URI.
 *
 * [com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper] feeds the system chooser a
 * 1-byte stub file under `cache/default_player_probe/` so this app shows up as a candidate handler
 * for a MIME type. That stub is not real media: if a standalone host actually opens it, ExoPlayer /
 * Glide / the document stack fail with an unrecognized-format error and surface a user-visible
 * "playback failed" toast. Every external-intent entry point (per-type standalone hosts + the
 * typeless dispatcher) must short-circuit it before any playback/render starts.
 */
object DefaultPlayerProbe {
    /**
     * Cache subdirectory name the probe file lives in. Shared by the producer
     * ([com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper], which writes the stub) and
     * the consumers (standalone hosts, which short-circuit it) so the path marker can never diverge.
     */
    const val PROBE_DIR_NAME = "default_player_probe"

    fun isProbe(uri: Uri?): Boolean =
        uri?.toString()?.contains(PROBE_DIR_NAME) == true
}
