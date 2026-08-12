package com.sza.fastmediasorter.ui.common.permissions

import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import com.sza.fastmediasorter.domain.repository.PermissionRequestMarkerRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S1206: how a hand-built helper reaches the two things that decide whether asking is worth it.
 *
 * Same escape hatch, and the same reason, as [PermissionRationaleEntryPoint] beside it: the call
 * sites in [canRequestPermission] are helpers constructed by hand with an Activity, not members of
 * the graph, and threading the repositories down from each host Activity is what CLAUDE.md Rule 3
 * forbids.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PermissionAskabilityEntryPoint {
    fun permissionRegistry(): PermissionRegistryRepository
    fun permissionRequestMarker(): PermissionRequestMarkerRepository
}
