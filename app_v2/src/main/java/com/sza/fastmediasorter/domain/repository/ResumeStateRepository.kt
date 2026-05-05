package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.ResumeState

/**
 * Repository for persisting and retrieving the last active playback state.
 * Used by the "resume last playback" feature on cold app start.
 * Each window stores its state independently, keyed by [windowId].
 */
interface ResumeStateRepository {
    suspend fun saveState(windowId: String, state: ResumeState)
    suspend fun getState(windowId: String): ResumeState?
    suspend fun clearState(windowId: String)

    companion object {
        const val WINDOW_ID_MAIN = "main"
    }
}
