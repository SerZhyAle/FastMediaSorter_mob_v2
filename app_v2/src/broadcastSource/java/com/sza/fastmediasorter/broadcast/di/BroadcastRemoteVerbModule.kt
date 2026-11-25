package com.sza.fastmediasorter.broadcast.di

import com.sza.fastmediasorter.broadcast.remote.BroadcastStartRemoteVerb
import com.sza.fastmediasorter.broadcast.remote.BroadcastStopRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** The broadcast verbs exist only where the broadcast source set is mounted; elsewhere they are unknown. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BroadcastRemoteVerbModule {
    @Binds
    @IntoSet
    abstract fun bindBroadcastStopVerb(verb: BroadcastStopRemoteVerb): RemoteVerbHandler

    @Binds
    @IntoSet
    abstract fun bindBroadcastStartVerb(verb: BroadcastStartRemoteVerb): RemoteVerbHandler
}
