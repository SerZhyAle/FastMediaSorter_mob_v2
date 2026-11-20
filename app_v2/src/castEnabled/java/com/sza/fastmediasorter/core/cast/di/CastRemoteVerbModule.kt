package com.sza.fastmediasorter.core.cast.di

import com.sza.fastmediasorter.core.cast.remote.CastStopRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** The cast verb exists only where the cast source set is mounted; elsewhere the verb is simply unknown. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CastRemoteVerbModule {
    @Binds
    @IntoSet
    abstract fun bindCastStopVerb(verb: CastStopRemoteVerb): RemoteVerbHandler
}
