package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.StreamResumeState

/**
 * S1152: persists and retrieves the last active stream for "resume on cold start".
 * Single-slot - there is only one last-active stream (no windowId).
 */
interface StreamResumeStateRepository {
    suspend fun save(state: StreamResumeState)
    suspend fun get(): StreamResumeState?
    suspend fun clear()

    companion object {
        /** 48 hours TTL, mirroring the media resume validity window. */
        const val RESUME_TTL_MS = 48L * 60 * 60 * 1000
    }
}
