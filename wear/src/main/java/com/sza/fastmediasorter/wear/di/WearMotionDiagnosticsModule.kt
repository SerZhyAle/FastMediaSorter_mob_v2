package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.motion.AndroidWearMotionDiagnosticsRepository
import com.sza.fastmediasorter.wear.domain.repository.WearMotionDiagnosticsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The component is only where the binding lives, not a lifetime: the session's lifetime is the flow
 * collection itself, so reopening the Motion Monitor starts counting events afresh rather than replaying
 * figures from the previous visit.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearMotionDiagnosticsModule {

    @Binds
    abstract fun bindWearMotionDiagnosticsRepository(
        impl: AndroidWearMotionDiagnosticsRepository
    ): WearMotionDiagnosticsRepository
}
