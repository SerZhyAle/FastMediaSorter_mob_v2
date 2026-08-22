package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.widget.Toast
import androidx.media3.common.Player
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import timber.log.Timber

/**
 * Owns the player's playback-order mode: where it is persisted and how it reaches whichever
 * engine is actually playing.
 *
 * Responsibilities:
 * - Resolve the mode for the current resource and media type, honouring a one-shot intent override.
 * - Persist a mode change per resource, or globally when no resource is known.
 * - Push the resolved mode to the audio service or to the ExoPlayer repeat mode.
 *
 * Extracted from PlayerActivity (S1963) to bring that file back under the 1500-line limit.
 */
class PlaybackOrderManager(private val activity: PlayerActivity) {

    // The resource plus media-type scope the mode was last resolved for. Without it, every state
    // emission would re-read preferences and re-notify the ViewModel for an unchanged selection.
    private var contextKey: String? = null

    fun applyToActivePlayer(mode: PlaybackOrderMode) {
        when (activity.viewModel.state.value.currentFile?.type) {
            MediaType.AUDIO -> activity.audioServiceController?.applyPlaybackOrderMode(mode)
            MediaType.VIDEO -> {
                val exoRepeatMode = if (mode == PlaybackOrderMode.REPEAT_ONE) {
                    Player.REPEAT_MODE_ONE
                } else {
                    Player.REPEAT_MODE_OFF
                }
                // Deliberately the backing field, not the lazy accessor: touching the accessor here
                // would construct a video player for an audio or image file.
                activity._videoPlayerManager?.getPlayer()?.repeatMode = exoRepeatMode
            }
            else -> Unit
        }
    }

    fun syncForCurrentResource(state: PlayerViewModel.PlayerState): Boolean {
        val resourceId = state.resource?.id
        val mediaType = state.currentFile?.type
        return if (resourceId == null || mediaType == null) {
            false
        } else {
            syncResolved(state, resourceId, mediaType)
        }
    }

    fun onPlaybackOrderClicked() {
        val newMode = activity.viewModel.cyclePlaybackOrderMode()
        val currentState = activity.viewModel.state.value
        val resourceId = currentState.resource?.id
        val mediaType = currentState.currentFile?.type
        val prefs = prefs()
        if (resourceId != null) {
            PlaybackControlPreferences.saveMode(prefs, resourceId, mediaType, newMode)
        } else {
            prefs.edit()
                .putString(PlaybackControlPreferences.globalKeyFor(mediaType), newMode.toPrefsString())
                .apply()
        }
        Timber.d("S1963: playback order clicked, new mode $newMode, resource $resourceId")
        applyToActivePlayer(newMode)
        activity.exoPlayerControlsManager.updatePlaybackOrderButtonState()
        val label = activity.getString(newMode.toPlaybackOrderUiState().labelResId)
        val message = activity.getString(R.string.playback_order_mode_set, label)
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private fun syncResolved(
        state: PlayerViewModel.PlayerState,
        resourceId: Long,
        mediaType: MediaType
    ): Boolean {
        val key = "$resourceId:${PlaybackControlPreferences.modeScopeFor(mediaType)}"
        if (key == contextKey) {
            return false
        }
        contextKey = key

        val resolvedMode = resolveMode(resourceId, mediaType)
        Timber.d("S1963: playback order resolved to $resolvedMode for resource $resourceId")
        applyToActivePlayer(resolvedMode)
        activity.exoPlayerControlsManager.updatePlaybackOrderButtonState()

        val changed = state.playbackOrderMode != resolvedMode
        if (changed) {
            activity.viewModel.setPlaybackOrderMode(resolvedMode)
        }
        return changed
    }

    // The override is one-shot: it is consumed from the Intent so a later re-entry into the same
    // resource resolves from preferences instead of replaying the caller's choice.
    private fun resolveMode(resourceId: Long, mediaType: MediaType): PlaybackOrderMode {
        val prefs = prefs()
        val extraKey = PlaybackControlPreferences.EXTRA_PLAYBACK_ORDER_OVERRIDE
        val overrideMode = activity.intent.getStringExtra(extraKey)?.let(PlaybackOrderMode::fromPrefsString)
        if (overrideMode == null) {
            return PlaybackControlPreferences.loadMode(prefs, resourceId, mediaType)
        }
        PlaybackControlPreferences.saveMode(prefs, resourceId, mediaType, overrideMode)
        activity.intent.removeExtra(extraKey)
        return overrideMode
    }

    private fun prefs() =
        activity.getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)
}
