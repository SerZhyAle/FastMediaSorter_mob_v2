package com.sza.fastmediasorter.wear.domain.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the playback service is playing while no screen is watching it.
 *
 * @param fileId the file the screen handed over, so a screen reopened on the same file recognises its
 *   own session and one reopened on another file knows it is looking at somebody else's.
 */
data class WearBackgroundSession(
    val fileId: Long,
    val mediaUri: String,
    val streamMediaKind: String?,
    val positionMs: Long,
    val isPlaying: Boolean
)

/**
 * S2166: the one place the service says what it is playing, so a returning screen can resume that
 * session instead of preparing a second player on the same track (strategic goal 5).
 *
 * It carries a description, never the player: strategic §7 names two owners of one player as the
 * defect to design against, and a handle passed between them is exactly how that happens.
 */
@Singleton
class WearBackgroundSessionState @Inject constructor() {

    private val _session = MutableStateFlow<WearBackgroundSession?>(null)
    val session: StateFlow<WearBackgroundSession?> = _session.asStateFlow()

    fun start(session: WearBackgroundSession) {
        _session.value = session
    }

    fun updateProgress(positionMs: Long, isPlaying: Boolean) {
        _session.update { it?.copy(positionMs = positionMs, isPlaying = isPlaying) }
    }

    fun clear() {
        _session.value = null
    }
}
