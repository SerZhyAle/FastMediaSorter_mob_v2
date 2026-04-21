package com.sza.fastmediasorter.ui.player.helpers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AudioMetadata
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.ui.player.PlayerActivity
import timber.log.Timber

/**
 * Manages online audio metadata display and audio-related external actions.
 *
 * Responsibilities:
 * - Cache the last fetched iTunes AudioMetadata (artist, album, track, year)
 * - Update audioMetadata / audioFileName views when metadata arrives
 * - Trigger audio slideshow photo mode label update on metadata change
 * - Open YouTube Music with artist+title search query
 *
 * Cache is keyed by file path so stale metadata from a previous track is never shown
 * for the current one.
 */
class PlayerAudioMetadataManager(
    private val activity: PlayerActivity
) {
    // Cached AudioMetadata from online cover search (iTunes), keyed by file path
    private var cachedPath: String? = null
    private var cachedMetadata: AudioMetadata? = null

    /**
     * Return cached metadata for [filePath], or null if the cache is for a different file.
     */
    fun getCachedMetadataFor(filePath: String?): AudioMetadata? =
        if (filePath != null && cachedPath == filePath) cachedMetadata else null

    /**
     * Called by ImageLoadingManager when metadata is fetched from the online source.
     * Caches the result and updates the UI metadata line.
     */
    fun onMetadataLoaded(metadata: AudioMetadata, currentFile: MediaFile?) {
        if (currentFile != null) {
            cachedPath = currentFile.path
            cachedMetadata = metadata
            Timber.d("PlayerAudioMetadataManager: cached metadata for ${currentFile.name}")
        }

        activity.runOnUiThread {
            val safeViews = activity.safeViews
            val albumPart = when {
                metadata.albumName != null && metadata.releaseYear != null ->
                    "${metadata.albumName} (${metadata.releaseYear})"
                metadata.albumName != null -> metadata.albumName
                else -> null
            }
            val parts = listOfNotNull(
                metadata.artistName?.takeIf { it.isNotBlank() },
                albumPart,
                metadata.trackName?.takeIf { it.isNotBlank() }
            )
            val metadataLine = parts.joinToString(" \u2013 ")

            if (metadataLine.isNotBlank()) {
                safeViews.audioMetadata.text = metadataLine
                safeViews.audioMetadata.visibility = View.VISIBLE
                safeViews.audioFileName.visibility = View.GONE
                Timber.d("PlayerAudioMetadataManager: displayed $metadataLine")

                if (activity.audioSlideshowPhotoModeManager.isActive) {
                    activity.audioSlideshowPhotoModeManager.updateCurrentSongLabel()
                }
            }
        }
    }

    /**
     * Open YouTube Music with a search query for the current audio track.
     * Uses cached iTunes metadata (artist + title) when available; falls back to filename.
     * Opens in the YouTube Music app if installed, otherwise in the browser.
     */
    fun searchInYoutubeMusic() {
        val currentFile = activity.viewModel.state.value.currentFile ?: return
        val metadata = getCachedMetadataFor(currentFile.path)
        val artist = metadata?.artistName?.takeIf { it.isNotBlank() }
        val title = metadata?.trackName?.takeIf { it.isNotBlank() }
        val query = when {
            artist != null && title != null -> "$artist $title"
            title != null -> title
            artist != null -> artist
            else -> currentFile.name.substringBeforeLast(".")
        }
        Timber.d("PlayerAudioMetadataManager: YouTube Music query='$query'")
        val uri = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "PlayerAudioMetadataManager: no app can handle YouTube Music search")
            Toast.makeText(
                activity,
                activity.getString(R.string.search_in_youtube_music_no_app),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
