package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGroupHeader

interface PermissionRegistryRepository {
    fun getEntries(): List<PermissionEntry>
    fun getGroups(): List<PermissionGroupHeader>

    /**
     * Adaptive permission set for the welcome onboarding page (S0402). Unlike [getEntries] (the full,
     * non-adaptive Settings list), this narrows the set to what the user actually opted into:
     * - "All files" (MANAGE_EXTERNAL_STORAGE) + MANAGE_MEDIA only when [allFiles] is on.
     * - RECORD_AUDIO only when [audioEnabled] is on.
     * - POST_NOTIFICATIONS (API 33+) is included regardless of the ENABLE_PERSISTENT_AUDIO_PLAYBACK
     *   flavor gate that constrains it in [getEntries] (welcome-only relaxation).
     * All other SDK/flavor registry behaviour is preserved.
     */
    fun getWelcomeEntries(allFiles: Boolean, audioEnabled: Boolean): List<PermissionEntry>
}
