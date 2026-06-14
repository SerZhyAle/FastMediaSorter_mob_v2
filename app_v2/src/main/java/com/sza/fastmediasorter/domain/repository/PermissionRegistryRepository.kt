package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGroupHeader

interface PermissionRegistryRepository {
    fun getEntries(): List<PermissionEntry>
    fun getGroups(): List<PermissionGroupHeader>

    /**
     * Permission set for the welcome onboarding page (S0402). Shows every permission this build can
     * request (the full [getEntries] set, SDK- and flavor-filtered) so the user sees and decides on all
     * of them; the user opts out by leaving individual permissions ungranted rather than the set being
     * pre-narrowed by their functionality toggles. POST_NOTIFICATIONS (API 33+) is additionally included
     * regardless of the ENABLE_PERSISTENT_AUDIO_PLAYBACK flavor gate that constrains it in [getEntries]
     * (welcome-only relaxation).
     */
    fun getWelcomeEntries(): List<PermissionEntry>
}
