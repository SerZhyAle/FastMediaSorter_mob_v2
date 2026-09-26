package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.repository.WearStopwatchSessionRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.WearStopwatchSessionRepository
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchOngoingIndicator
import com.sza.fastmediasorter.wear.ui.apps.stopwatch.WearStopwatchOngoingNotificationManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S3555: the stopwatch's process-wide bindings. Kept out of [WearAppModule], which sits at detekt's
 * TooManyFunctions ceiling; the singleton scope is what lets the measurement outlive every screen.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearStopwatchModule {

    @Binds
    @Singleton
    abstract fun bindStopwatchSession(impl: WearStopwatchSessionRepositoryImpl): WearStopwatchSessionRepository

    @Binds
    @Singleton
    abstract fun bindStopwatchOngoingIndicator(
        impl: WearStopwatchOngoingNotificationManager
    ): WearStopwatchOngoingIndicator
}
