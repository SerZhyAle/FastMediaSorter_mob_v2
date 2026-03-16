package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.ResumeState

/**
 * Repository for persisting and retrieving the last active playback state.
 * Used by the "resume last playback" feature on cold app start.
 */
interface ResumeStateRepository {
    suspend fun saveState(state: ResumeState)
    suspend fun getState(): ResumeState?
    suspend fun clearState()
}
