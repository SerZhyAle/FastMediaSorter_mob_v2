package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Wear OS application preferences.
 * Manages settings for media types, slideshow, and album art.
 */
interface WearPreferencesRepository {

    // Media type toggles
    val isAudioEnabled: Flow<Boolean>
    val isVideoEnabled: Flow<Boolean>
    val isImagesEnabled: Flow<Boolean>

    suspend fun setAudioEnabled(enabled: Boolean)
    suspend fun setVideoEnabled(enabled: Boolean)
    suspend fun setImagesEnabled(enabled: Boolean)

    // Slideshow settings
    val isSlideshowEnabled: Flow<Boolean>
    val slideshowIntervalSeconds: Flow<Int>

    suspend fun setSlideshowEnabled(enabled: Boolean)
    suspend fun setSlideshowIntervalSeconds(seconds: Int)

    // Album art settings
    val downloadAlbumArt: Flow<Boolean>
    suspend fun setDownloadAlbumArt(enabled: Boolean)

    /** S1701: playback order of the browsed set; remembered so it survives a restart. */
    val isShuffleEnabled: Flow<Boolean>
    suspend fun setShuffleEnabled(enabled: Boolean)

    /** S1781: one view shared by the navigation screens - the home screen and the Resources page. */
    val viewMode: Flow<WearViewMode>
    suspend fun setViewMode(mode: WearViewMode)

    /** S1730: the view of a file list inside a resource, deliberately separate from [viewMode]. */
    val fileListViewMode: Flow<WearViewMode>
    suspend fun setFileListViewMode(mode: WearViewMode)

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
     * S2000: what is drawn behind the app's screens. Read from here rather than from the last
     * message received, so the watch draws the right background after a restart out of BT range.
     */
    val backgroundMode: Flow<WearBackgroundMode>
    suspend fun setBackgroundMode(mode: WearBackgroundMode)

    /** S1781: the players hold the screen unconditionally, so this covers only the rest of the app. */
    val keepScreenAwakeOutsidePlayers: Flow<Boolean>
    suspend fun setKeepScreenAwakeOutsidePlayers(enabled: Boolean)

    /**
     * S1781: the resources opened last, newest first. S1836: an entry that predates the identifier
     * cannot address a source and never reaches this list. S1974: a list rather than a single value,
     * because the home screen fills its first row with as many shortcuts as it has columns; an empty
     * list is the whole of "there is no shortcut".
     *
     * [setLastUsedResource] pushes onto that history - an id already in it moves to the front rather
     * than appearing twice - and [clearLastUsedResource] empties it.
     */
    val lastUsedResources: Flow<List<LastUsedResource>>
    suspend fun setLastUsedResource(id: String, name: String)
    suspend fun clearLastUsedResource()

    /** S1781: the Streams section ships on and can be switched off from the Media types settings. */
    val streamsSectionEnabled: Flow<Boolean>
    suspend fun setStreamsSectionEnabled(enabled: Boolean)

    /**
     * S1710: the calculator's own state, kept on the watch and never reconciled with the phone.
     *
     * An empty history and a null memory are a first run, not a broken store.
     */
    val calculatorHistory: Flow<List<String>>
    suspend fun setCalculatorHistory(entries: List<String>)

    val calculatorMemory: Flow<String?>
    suspend fun setCalculatorMemory(value: String?)

    /**
     * S1710: the game started on the watch, serialized by GameStateSnapshot.
     *
     * Null means no game has been started - an absent key is a first run, not a broken save, and a
     * stored string the snapshot cannot read is discarded the same way rather than reported.
     */
    val gameState: Flow<String?>
    suspend fun setGameState(value: String?)

    /** S1718: watch screen auto-rotation setting. Default: false (forbidden). */
    val isAutoRotationEnabled: Flow<Boolean>
    suspend fun setAutoRotationEnabled(enabled: Boolean)

    /** S1814: active app language inherited from the phone companion; null means system locale default. */
    val appLanguage: Flow<String?>
    suspend fun setAppLanguage(languageCode: String?)

    /**
     * S1862: whether a finished voice note leaves the watch on its own. Absent reads as
     * [VoiceNoteSendPolicy.AUTOMATIC] - a watch recording is meant for the phone by default.
     */
    val voiceNoteSendPolicy: Flow<VoiceNoteSendPolicy>
    suspend fun setVoiceNoteSendPolicy(policy: VoiceNoteSendPolicy)

    /**
     * S1961: whether POST_NOTIFICATIONS has already been asked for once.
     *
     * Set after the first ask regardless of the answer, because a refusal simply returns the
     * behaviour the watch had before this ticket - it is a valid choice, not an error to retry. It is
     * kept here rather than derived from `shouldShowRequestPermissionRationale`, which cannot tell a
     * first run apart from a permanent refusal.
     */
    val notificationPermissionAsked: Flow<Boolean>
    suspend fun setNotificationPermissionAsked(asked: Boolean)
}
