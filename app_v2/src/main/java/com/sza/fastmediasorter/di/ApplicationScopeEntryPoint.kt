package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/**
 * S2358: hands the process-lifetime [ApplicationScope] to code that sits outside the Hilt graph.
 *
 * S2360: consolidated canonical entry point for [ApplicationScope] across the codebase, accessed via
 * `Context.applicationScope()`. Incidental scope exposures in domain and widget entry points
 * ([StreamQualityMemoryEntryPoint], `NetworkMonitorWidgetRefreshEntryPoint`) were removed in favor of
 * resolving the application scope solely through this entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ApplicationScopeEntryPoint {
    @ApplicationScope
    fun applicationScope(): CoroutineScope
}
