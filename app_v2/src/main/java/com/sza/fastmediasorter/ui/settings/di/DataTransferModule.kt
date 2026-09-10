package com.sza.fastmediasorter.ui.settings.di

import com.sza.fastmediasorter.domain.port.StagedFileTransferPort
import com.sza.fastmediasorter.ui.settings.helpers.StagedFileTransferAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** S1565: binds the staging contract the transfer orchestration declares to its UI-layer adapter. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataTransferModule {

    @Binds
    @Singleton
    abstract fun bindStagedFileTransferPort(impl: StagedFileTransferAdapter): StagedFileTransferPort
}
