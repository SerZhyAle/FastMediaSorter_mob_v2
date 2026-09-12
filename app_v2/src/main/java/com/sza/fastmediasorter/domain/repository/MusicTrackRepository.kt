package com.sza.fastmediasorter.domain.repository

import android.net.Uri
import com.sza.fastmediasorter.domain.model.stopwatch.MusicTrackOption

/**
 * The device's audio library as a chooser source (S2792).
 *
 * Exists because the system document picker cannot guarantee a music-only list on every OEM file
 * provider - the owner's device shows the whole tree - so the app reads MediaStore audio itself.
 * The slideshow music picker is the named second consumer of this seam (strategic S2792 5.3).
 */
interface MusicTrackRepository {

    /** Every audio track of the external media store, ordered by title. */
    suspend fun loadTracks(): List<MusicTrackOption>

    /**
     * The display title of a previously chosen track uri, or null when the uri is not a MediaStore
     * audio track - a legacy SAF uri from before the in-app picker keeps its old label fallback.
     */
    suspend fun titleOf(uri: Uri): String?
}
