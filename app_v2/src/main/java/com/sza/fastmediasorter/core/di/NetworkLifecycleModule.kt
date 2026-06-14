package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.network.lifecycle.CloudConnectionGate
import com.sza.fastmediasorter.data.network.lifecycle.ConnectionGateRegistry
import com.sza.fastmediasorter.data.network.lifecycle.FtpConnectionGate
import com.sza.fastmediasorter.data.network.lifecycle.SftpConnectionGate
import com.sza.fastmediasorter.data.network.lifecycle.SmbConnectionGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds the [com.sza.fastmediasorter.data.network.lifecycle] subsystem.
 * S0067.
 *
 * S0195: [com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper]
 * is constructor-injected via `@Inject` and therefore needs no explicit `@Provides`
 * binding in this module. Do not add one - duplicate bindings break Hilt graph assembly.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkLifecycleModule {

    @Provides
    @Singleton
    fun provideRegistry(
        smb: SmbConnectionGate,
        sftp: SftpConnectionGate,
        ftp: FtpConnectionGate,
        cloud: CloudConnectionGate,
        mediaCapabilities: MediaCapabilities,
    ): ConnectionGateRegistry = ConnectionGateRegistry().apply {
        register(smb)
        register(sftp)
        register(ftp)
        // S0391: registration is a compile-tier decision (does this flavor ship cloud at all),
        // not the user runtime toggle. The runtime close-on-disable is owned by
        // RemoteSourceDisableCoordinator (Phase 04); the registry stays passive here.
        if (mediaCapabilities.supportsCloud) {
            register(cloud)
        }
    }
}
