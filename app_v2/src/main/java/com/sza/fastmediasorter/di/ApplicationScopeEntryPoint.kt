package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/**
 * S2358: hands the process-lifetime [ApplicationScope] to code that sits outside the Hilt graph.
 *
 * `ScrollableTextDialog` is an object and takes no constructor injection, yet the file write it starts
 * has to finish whether or not the dialog that started it is still open. [StreamQualityMemoryEntryPoint]
 * already exposes the same scope, but only beside its own use cases, so borrowing it here would attach
 * an unrelated subject to that entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ApplicationScopeEntryPoint {
    @ApplicationScope
    fun applicationScope(): CoroutineScope
}
