package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.network.IdleDisconnectPolicy
import com.sza.fastmediasorter.data.network.IdleDisconnectPolicyImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IdleDisconnectModule {

    @Binds
    @Singleton
    abstract fun bindIdleDisconnectPolicy(impl: IdleDisconnectPolicyImpl): IdleDisconnectPolicy
}