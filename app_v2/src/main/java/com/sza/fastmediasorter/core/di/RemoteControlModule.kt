package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.core.notification.RemoteCommandConsentNotifier
import com.sza.fastmediasorter.data.remotecontrol.RemoteControlRepositoryImpl
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentPrompter
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControlRepository
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.domain.remotecontrol.verbs.CommandsRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.verbs.PlaybackStopRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.verbs.QuitRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.verbs.StatusRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.verbs.VersionRemoteVerb
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/** Flavor source sets contribute their own verbs into the same set; an empty contribution is legal. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteControlModule {
    @Binds
    @Singleton
    abstract fun bindRemoteControlRepository(impl: RemoteControlRepositoryImpl): RemoteControlRepository

    @Binds
    abstract fun bindRemoteConsentPrompter(impl: RemoteCommandConsentNotifier): RemoteConsentPrompter

    @Multibinds
    abstract fun remoteVerbHandlers(): Set<RemoteVerbHandler>

    @Binds
    @IntoSet
    abstract fun bindCommandsVerb(verb: CommandsRemoteVerb): RemoteVerbHandler

    @Binds
    @IntoSet
    abstract fun bindVersionVerb(verb: VersionRemoteVerb): RemoteVerbHandler

    @Binds
    @IntoSet
    abstract fun bindStatusVerb(verb: StatusRemoteVerb): RemoteVerbHandler

    @Binds
    @IntoSet
    abstract fun bindQuitVerb(verb: QuitRemoteVerb): RemoteVerbHandler

    @Binds
    @IntoSet
    abstract fun bindPlaybackStopVerb(verb: PlaybackStopRemoteVerb): RemoteVerbHandler
}
