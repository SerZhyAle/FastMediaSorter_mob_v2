package com.sza.fastmediasorter.ui.common.permissions

import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S1436: how a hand-constructed helper reaches the permission registry.
 *
 * The storage explanation call sites are helpers built by hand with an Activity, not members of the
 * graph, so they use the escape hatch this project already uses in the same situation -
 * `UriPathResolver` and the Glide model loaders. Threading the repository down from each host
 * Activity was tried first and is what CLAUDE.md Rule 3 forbids: `assert-source-gates` refused it as
 * new domain-layer field injection in an Activity.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PermissionRationaleEntryPoint {
    fun permissionRegistry(): PermissionRegistryRepository
}
