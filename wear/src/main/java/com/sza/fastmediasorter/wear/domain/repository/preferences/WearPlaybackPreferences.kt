package com.sza.fastmediasorter.wear.domain.repository.preferences

import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import kotlinx.coroutines.flow.Flow

/** How the players behave once content is open. */
interface WearPlaybackPreferences {

    val isSlideshowEnabled: Flow<Boolean>
    val slideshowIntervalSeconds: Flow<Int>

    suspend fun setSlideshowEnabled(enabled: Boolean)
    suspend fun setSlideshowIntervalSeconds(seconds: Int)

    /** S2505: player panel auto-hide duration in seconds. */
    val panelAutoHideSeconds: Flow<Int>
    suspend fun setPanelAutoHideSeconds(seconds: Int)

    val downloadAlbumArt: Flow<Boolean>
    suspend fun setDownloadAlbumArt(enabled: Boolean)

    /** S1701: playback order of the browsed set; remembered so it survives a restart. */
    val isShuffleEnabled: Flow<Boolean>
    suspend fun setShuffleEnabled(enabled: Boolean)

    /**
     * S1948: the video player's frame mode, so the choice survives the player closing and covers a
     * stream and a file alike - both open the same player, so one memory serves both.
     */
    val videoScaleMode: Flow<VideoScaleMode>
    suspend fun setVideoScaleMode(mode: VideoScaleMode)

    /**
     * S2006: the image viewer's own fit, over the same vocabulary as the video player's but under its
     * own key - the two screens are chosen for independently and must not overwrite each other.
     */
    val imageScaleMode: Flow<VideoScaleMode>
    suspend fun setImageScaleMode(mode: VideoScaleMode)

    /**
     * S2166: audio and audio streams keep playing after the app is minimized, owned by a foreground
     * service instead of by the player screen. Off by default - the battery cost of holding a
     * session on a watch is not measured yet, so the switch opts in rather than out.
     */
    val backgroundPlaybackEnabled: Flow<Boolean>
    suspend fun setBackgroundPlaybackEnabled(enabled: Boolean)
}
