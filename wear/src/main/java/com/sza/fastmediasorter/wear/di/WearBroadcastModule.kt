package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastSessionStateHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S2509: the owner-started broadcast's own graph, kept beside `WearListenModule` rather than inside it.
 *
 * The two features share the microphone and nothing else. A single module would suggest a shared
 * lifecycle they deliberately do not have - one is answered to a paired phone, the other is started on
 * the wrist and outlives every screen.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearBroadcastModule {

    /**
     * Application-scoped because the session outlives its screen by design: strategic goal 3 lets the
     * owner return Home while the broadcast runs, and a holder the control screen owned would be gone
     * the moment that happens - taking the stop action's only view of the session with it.
     */
    @Provides
    @Singleton
    fun provideWearBroadcastSessionStateHolder(): WearBroadcastSessionStateHolder =
        WearBroadcastSessionStateHolder()
}
