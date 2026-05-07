package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGroupHeader

interface PermissionRegistryRepository {
    fun getEntries(): List<PermissionEntry>
    fun getGroups(): List<PermissionGroupHeader>
}
