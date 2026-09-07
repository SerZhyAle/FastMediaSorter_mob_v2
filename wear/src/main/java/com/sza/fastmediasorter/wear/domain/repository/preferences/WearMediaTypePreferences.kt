package com.sza.fastmediasorter.wear.domain.repository.preferences

import kotlinx.coroutines.flow.Flow

/** Which kinds of content the watch offers at all. */
interface WearMediaTypePreferences {

    val isAudioEnabled: Flow<Boolean>
    val isVideoEnabled: Flow<Boolean>
    val isImagesEnabled: Flow<Boolean>
    val isDocumentsEnabled: Flow<Boolean>

    suspend fun setAudioEnabled(enabled: Boolean)
    suspend fun setVideoEnabled(enabled: Boolean)
    suspend fun setImagesEnabled(enabled: Boolean)
    suspend fun setDocumentsEnabled(enabled: Boolean)

    /** S1781: the Streams section ships on and can be switched off from the Media types settings. */
    val streamsSectionEnabled: Flow<Boolean>
    suspend fun setStreamsSectionEnabled(enabled: Boolean)
}
