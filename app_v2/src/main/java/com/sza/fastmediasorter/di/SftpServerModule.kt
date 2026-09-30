package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.repository.SftpServerRepositoryImpl
import com.sza.fastmediasorter.domain.repository.SftpServerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the embedded SFTP server repository; the server itself is only built when it starts. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SftpServerModule {

    @Binds
    @Singleton
    abstract fun bindSftpServerRepository(impl: SftpServerRepositoryImpl): SftpServerRepository
}
