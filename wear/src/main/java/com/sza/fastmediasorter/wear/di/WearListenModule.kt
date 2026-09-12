package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.domain.listen.ListenRequestRegistry
import com.sza.fastmediasorter.wear.domain.listen.ListenSessionStateHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S2550: the listening session's own graph.
 *
 * Its one binding could have joined `WearAppModule`, and briefly did - which is how it was measured
 * that the module had reached its declared ceiling of forty provider functions exactly. A feature
 * that arrived as a module of its own is the answer the ceiling asks for; raising the threshold would
 * spend the one signal that says a god-module is forming.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearListenModule {

    /**
     * Application-scoped for the same reason as `VoiceRecordingStateHolder`, plus one of its own: the
     * Data Layer answer Phase 04 sends to the phone reads the endpoint from here, and a holder scoped
     * to the confirmation screen would lose it the moment the wrist drops and that screen goes dark.
     */
    @Provides
    @Singleton
    fun provideListenSessionStateHolder(): ListenSessionStateHolder = ListenSessionStateHolder()

    /**
     * Application-scoped for [provideListenSessionStateHolder]'s reason and one more: the answer to
     * the phone leaves from the capture service long after the confirmation screen went dark, and the
     * stop that ends the session later still - both need the node that asked.
     */
    @Provides
    @Singleton
    fun provideListenRequestRegistry(): ListenRequestRegistry = ListenRequestRegistry()
}
